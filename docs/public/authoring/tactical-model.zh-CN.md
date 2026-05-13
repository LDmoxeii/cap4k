# 公开战术模型

本页说明业务项目作者如何理解 cap4k 的分层责任和运行时战术对象。它描述默认使用方式，不要求作者理解框架内部实现。

## 分层责任

| 层 / 模块 | 默认责任 | 常见内容 |
| --- | --- | --- |
| `domain` | 业务模型与领域决策 | 聚合、实体、值对象、领域事件、领域服务、行为、工厂、规约 |
| `application` | 用例边界与流程编排 | 命令、命令处理器、查询/client 请求契约、校验器、订阅器、job、流程编排 |
| `adapter` | 技术适配和传输边界 | controller、API payload、JPA repository、查询处理器、client/cli 处理器、外部系统桥接 |
| `start` | 运行时装配 | Spring Boot 应用、运行时配置、本地 DB/script wiring |

责任与物理包位置相关，但不完全等同。query/client/cli handler 默认物理生成在 adapter 侧，因为它们经常触碰读模型、外部协议或技术适配；它们仍然实现 application 请求契约。

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
3. 调用聚合行为或 `Mediator.services` 中的领域服务。
4. 必要时登记领域事件或集成事件。
5. 调用 `Mediator.uow.save()` 作为写入事务边界。

命令处理器可以使用 repository、factory、domain service 和 UoW。只有当命令结果依赖外部能力返回时，才应在命令处理器中调用 client/cli 请求；普通写入决策不要随意穿过外部边界。

## 查询和 client/cli 处理器

查询处理器默认物理落在 adapter 侧，通常包装读模型、JPA 读取或投影组装。它适合 controller、job 或编排代码读取信息，而不是承载写入。

client/cli 处理器默认物理落在 adapter 侧，用来包装外部系统或本地模拟外部能力。它们通过 request supervisor 被发送，与命令、查询共享底层 request 机制。

当前运行时核心是 `RequestParam` 与 `RequestHandler`。design tag 支持 `command`、`query`、`client`、`api_payload`、`domain_event`、`validator`；其中 `client` 可以代表外部 client/cli 请求，并在生成器布局中形成对应 handler 形态。不要把 client/cli 命名约定误解成独立的 `cli` design tag。

## 流程编排

流程编排属于 application-facing 代码，例如：

- `application.subscribers.domain` 下的领域事件订阅器；
- `application.subscribers.integration` 下的集成事件订阅器；
- job；
- 必要时显式编写的流程 / application service。

编排代码可以使用 `Mediator.cmd`、`Mediator.qry` 和 `Mediator.requests` 组合步骤。job 或订阅器需要读取信息时，优先考虑查询边界；需要推进状态时，发送命令，而不是直接把仓储写入散落在入口代码中。

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

领域事件表达领域内部已经发生的事实，通常由聚合行为登记。领域事件订阅器默认位于 `application.subscribers.domain`，负责后续应用层推进。

集成事件表达跨边界消息，可通过 `Mediator.events.attach` 或 publish 进入 integration event supervisor，再由 HTTP、RabbitMQ、RocketMQ 等 adapter 传输。集成事件订阅器默认位于 `application.subscribers.integration`，收到外部事实后应转换成内部命令、查询或 client 请求。

不要把外部回调伪装成领域事件。外部输入应先作为集成事件或 adapter 输入进入系统，再由 application 层翻译成内部状态推进。

## 不要误用

- 不要让 controller、job 或 message listener 直接成为写入主面。
- 不要在查询处理器里偷偷修改状态。
- 不要把 query/client/cli handler 说成默认物理位于 application module；当前默认物理落点是 adapter 侧。
- 不要让命令处理器随意依赖外部 client/cli，除非命令结果确实依赖外部返回。
- 不要让聚合直接了解 HTTP payload、第三方 DTO、轮询响应或消息协议。
- 不要给非聚合根生成工厂。
- 不要把 `src-generated/main/kotlin` 当成活跃生成输出。
- 不要把 design 暂不支持的 `value_object`、`domain_service` 当成已经可生成能力；`integration_event` 只支持契约和 inbound `@EventListener` subscriber 骨架，不包含 MQ 专用代码生成。
