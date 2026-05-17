# 公开战术模型

本页说明业务项目作者如何理解 cap4k 的分层责任和运行时战术对象。它描述默认使用方式，不要求作者理解框架内部实现。

## 分层责任

| 层 / 模块 | 默认责任 | 常见内容 |
| --- | --- | --- |
| `domain` | 业务模型与领域决策 | 聚合、实体、值对象、领域事件、领域服务、行为、工厂、规约 |
| `application` | 用例边界与流程编排 | 命令、命令处理器、查询/client 请求契约、校验器、订阅器、内部流程编排 |
| `adapter` | 技术适配和边界入口 | 开放服务入口实现、外部事实入口实现、API payload、JPA repository、查询处理器、client handler、外部协议桥接 |
| `start` | 运行时装配 | Spring Boot 应用、运行时配置、本地 DB/script wiring |

责任与物理包位置相关，但不完全等同。query/client handler 默认物理生成在 adapter 侧，因为它们经常触碰读模型、外部协议或技术适配；它们仍然实现 application 请求契约。

## 边界入口与对外交互

cap4k authoring 不以 controller、RPC endpoint、callback handler、message listener 这些实现形态作为架构中心。它们首先属于边界入口。

- 开放服务入口（Open Host Service）表示外部系统或前端以同步方式消费本系统能力。HTTP controller、RPC endpoint、gRPC service 都只是实现形态。开放服务入口使用发布语言（Published Language）表达稳定请求、响应、错误与状态语义。
- 外部能力端口 / client 防腐层表示内部主动消费外部能力，例如对象存储、支付、短信、媒体处理、权限资料或兄弟服务查询。client contract 应使用内部业务语言，外部协议、SDK、bucket、objectKey、第三方状态码留在 adapter handler 内。
- 外部事实入口表示外部已经发生的事实进入内部。callback controller、message listener、inbound integration event subscriber 都只是实现形态。外部事实入口不直接修改聚合；会推进状态的事实必须翻译成内部 command，纯观察类事实可以翻译成 query 或明确的 application entry point。
- 内部事实发布表示本系统把内部已经发生的跨边界事实发布给外部。outbound integration event 是常见实现形态，通常由领域事实或 application process 派生，不建议聚合根直接承担跨服务发布协议。

前端-facing HTTP API 也按开放服务入口规则审计。严格 DDD 语义中的 Open Host Service 更偏跨系统公开契约，但 cap4k 默认规则要求所有同步入口都遵守同一条边界：入口只翻译请求，写操作进入 command，读操作进入 query。

## Mediator

业务代码优先使用静态 `Mediator` 表面，不需要为了调用这些 supervisor 而注入 mediator 对象。

| 表面 | 别名 | 用途 |
| --- | --- | --- |
| `Mediator.cmd` / `Mediator.commands` | 无 | 发送命令请求 |
| `Mediator.qry` / `Mediator.queries` | 无 | 发送查询请求 |
| `Mediator.requests` | `Mediator.req` | 发送通用 request param |
| `Mediator.repositories` | `Mediator.repo` | 通过内置仓储加载聚合根 |
| `Mediator.factories` | `Mediator.fac` | 通过工厂 payload 创建聚合根 |
| `Mediator.services` | `Mediator.svc` | 获取领域服务 |
| `Mediator.uow` | 无 | 工作单元持久化、删除、保存 |

`Mediator.events` 可用于领域事件和集成事件的 attach / publish。`Mediator.aggregates` 可用于聚合 supervisor 操作，但日常业务代码更常使用仓储、工厂、服务和工作单元。

## 命令处理器

命令处理器是默认写用例边界。常见步骤是：

1. 读取命令参数。
2. 通过 `Mediator.repositories` 加载聚合，或通过 `Mediator.factories` 创建聚合根。
3. 在写用例需要外部能力返回时，通过 client 请求调用外部能力端口。
4. 调用聚合行为或 `Mediator.services` 中的领域服务。
5. 必要时登记领域事件，或把内部事实交给 application process 转换为集成事件。
6. 调用 `Mediator.uow.save()` 作为写入持久化边界。

命令处理器可以使用 repository、factory、domain service、client 请求和 UoW。只有当写用例确实依赖外部能力返回时，才应在命令处理器中调用 client；普通写入决策不要随意穿过外部边界。入口层不编排“先调 client，再补 command”的写流程。

## 查询处理器与外部能力端口

查询处理器默认物理落在 adapter 侧，通常包装读模型、JPA 读取或投影组装。它适合 controller、job 或编排代码读取信息，而不是承载写入。

client handler 默认物理落在 adapter 侧，用来包装外部系统或本地模拟外部能力。它们通过 request supervisor 被发送，与命令、查询共享底层 request 机制。

当前运行时核心是 `RequestParam` 与 `RequestHandler`。`client` 表达的是外部能力端口，不等同于 RPC，也不只服务于兄弟服务调用。对象存储、支付、短信、媒体处理、权限资料查询都可以是 client 背后的外部能力。

## 流程编排

流程编排属于 application-facing 代码，不属于开放服务入口或外部事实入口本身。常见形态包括：

- `application.subscribers.domain` 下的领域事件订阅器；
- `application.subscribers.integration` 下的集成事件订阅器；
- 内部 job / scheduler 触发后的 application 推进点；
- 必要时显式编写的流程 / application service。

编排代码可以使用 `Mediator.cmd`、`Mediator.qry` 和 `Mediator.requests` 组合步骤。开放服务入口、外部事实入口、内部触发入口需要读取信息时，优先考虑查询边界；需要推进状态时，发送命令，而不是直接把仓储写入散落在入口代码中。

## 内置仓储

生成仓储通过 adapter 侧 Spring Data JPA 实现，并暴露 cap4k `Repository<Entity>`。

常见用法：

- 命令处理器通过 `Mediator.repositories.findOne`、`findFirst`、`find` 等加载聚合根；
- 查询处理器可以封装 repository / JPA 读取；
- job 做读编排时优先调用查询，而不是直接访问仓储。

内置仓储是战术模型的一部分，不是普通 CRUD 细节。写入仍应通过聚合行为和工作单元完成。

## 工厂

`AggregateFactory<Payload, Entity>` 负责创建聚合根。默认工厂 supervisor 按 payload 类型解析工厂，调用 `create(payload)`，并把新实体纳入 `JpaUnitOfWork.persist`。

作者规则：

- 只有聚合根应该有工厂；
- 工厂骨架是重要的作者维护面，生成后先看 `conflictPolicy`；
- 创建逻辑放在工厂中，不要散落在命令处理器的临时构造代码里；
- 通过工厂创建的聚合会自动进入工作单元。

## 工作单元

`Mediator.uow` 是默认写入边界，支持：

- `persist`;
- `persistIfNotExist`;
- `remove`;
- `save(propagation = REQUIRED)`.

`JpaUnitOfWork` 会记录 persist / remove 意图，处理聚合 wrapper、分配 ID、flush 实体、运行规约、触发生命周期监听、发布已 attach 的领域事件和集成事件，并在事务前后执行拦截器。

命令处理器应调用 `Mediator.uow.save()` 完成持久化。不要把手写事务注解当成默认主写入模型。

UoW 只保存聚合根。子实体、值对象、inline value、JSON-backed value 都通过聚合根持久化，不作为独立 UoW 保存目标。

## 生命周期

聚合行为可以定义：

```kotlin
fun Entity.onCreate()
fun Entity.onUpdate()
fun Entity.onDelete()
```

当 `cap4k.ddd.application.jpa-uow.supportEntityInlinePersistListener` 启用时，`JpaUnitOfWork` 会在持久化过程中调用对应生命周期监听。生成的 behavior 模板已经为 `onCreate`、`onUpdate`、`onDelete` 保留扩展点，作者可以在行为文件中实现它们。

已知限制：生命周期识别仍有缺陷或不足，应作为框架缺口跟踪。作者可以使用 intended usage，但不要声称所有子类或子实体场景都已完整支持。

## 规约

`Specification<Entity>` 用于在事务前或事务内校验聚合状态，通常通过 `SpecificationUnitOfWorkInterceptor` 执行。

作者规则：

- 只有项目确实要展示或执行该战术概念时才生成规约；
- 规约一般是 checked-in `SKIP` 骨架；
- 不要为了显得完整而生成没有业务含义的规约文件。

## 领域服务

领域服务是 Spring bean，通过 `DefaultDomainServiceSupervisor` 发现，并经由 `Mediator.services` 访问。

当一个业务规则跨聚合边界，或不自然归属于单个聚合根时，可以使用领域服务。应用层负责加载聚合与编排流程，领域服务负责领域判断，最终仍应通过拥有状态的聚合和工作单元写入。

不要把领域服务当成“什么都能放”的 application service。它应承载领域决策，而不是外部协议转换、controller 流程或数据库查询拼装。

## 领域事件与集成事件

领域事件表达领域内部已经发生的有意义事实，通常由聚合行为登记。领域事件可以同步参与当前事务，也可以异步发布；同步或异步是运行时策略，不改变它的领域事实身份。领域事件订阅器默认位于 `application.subscribers.domain`，负责后续应用层推进。

集成事件表达跨边界事实，可通过 `Mediator.events.attach` 或 publish 进入 integration event supervisor，再由 HTTP、RabbitMQ、RocketMQ 等 adapter 传输。集成事件订阅器默认位于 `application.subscribers.integration`，收到会推进状态的外部事实后应转换成内部命令；纯观察类输入才进入查询，需要调用外部能力时也要经过明确的 client request 边界。

运行时会扫描集成事件类，并由 `EventSubscriberManager` 把消费到的事件 payload 桥接到 Spring `ApplicationEventPublisher`。因此生成的 inbound subscriber 使用 `@EventListener` 即可接收事件；如果项目需要更底层的运行时合同，也可以手写 `EventSubscriber<Event>`。

不要把外部事实入口伪装成领域事件。外部输入应先作为外部事实或 adapter 输入进入系统，再由 application 层翻译成内部状态推进。对外发布集成事件时，优先从领域事实或 application process 派生，不建议由聚合根直接承担跨服务协议。

## 不要误用

- 不要让开放服务入口、外部事实入口或内部触发入口直接成为写入主面。
- 不要在查询处理器里偷偷修改状态。
- 不要把 query/client handler 说成默认物理位于 application module；当前默认物理落点是 adapter 侧。
- 不要让命令处理器随意依赖外部 client，除非写用例确实依赖外部返回。
- 不要让入口层先调用外部能力，再调用 command 补内部状态。
- 不要让聚合直接了解 HTTP payload、第三方 DTO、轮询响应或消息协议。
- 不要给非聚合根生成工厂。
