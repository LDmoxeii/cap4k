# Cap4k 官方默认项目与运行时能力边界设计

Date: 2026-07-25

Status: Approved product decisions; pending implementation plan

Scope: 固化 cap4k 官方默认项目、运行时能力、starter 拆分、配置瘦身、事件订阅发现、Bootstrap 和数据库初始化的已确认边界。

## 1. 文档权威与使用方式

本文是本轮“官方默认项目与运行时拆分”工作的单一决策事实来源。

- 本文记录的是已经确认的产品和架构决策，不是待讨论备忘录。
- 后续源码审计只用于定位修改点、识别遗漏和制定实施顺序，不能把已确认决策重新降级为开放问题。
- 如果历史 spec、plan、对话摘要或当前实现与本文在本议题上冲突，以本文为准。
- 历史文档仍可作为现状、问题来源和局部实现背景；本文不会自动否定与本议题无冲突的既有设计。
- 若源码证明某项决策无法实现，必须带着具体证据重新请求产品决策，不能自行回退到旧方案。

本文明确取代本轮更早出现的以下方向：

- 以一个全能力聚合 starter 作为普通用户入口。
- 默认项目携带可靠 Request、可靠 Event、Saga、Snowflake 或 Integration Event。
- 用自动推导的 Spring Boot 根包替代 `event-scan-package`。
- 在本轮为所有框架表建立自动迁移体系。
- 由 Bootstrap 安装、覆盖、删除、打包或管理 cap4k Skill。

本文不取代 Saga 内部补偿语义等局部设计；本轮只改变 Saga 的默认地位和模块边界。

## 2. 决策背景

GitHub Template、IDEA 项目初始化器和类似 Spring Initializr 的入口都依赖一个稳定的官方默认项目契约。如果先建设多个传播入口，再反复调整默认模块、依赖、配置和数据库要求，所有入口都会同时漂移。

因此当前顺序固定为：

1. 先稳定官方默认项目结构和能力边界。
2. 拆分当前错误聚合的 starter 和运行时依赖。
3. 瘦身生成器配置和运行时配置。
4. 调整现有多模块 `cap4kBootstrapPlan` / `cap4kBootstrap`。
5. 在真实项目中使用并稳定。
6. 最后再评估 GitHub Template、IDEA 初始化器、Initializr 服务和单模块项目。

cap4k 当前没有外部用户。本轮允许破坏性调整，不为尚不存在的兼容需求保留错误边界。

## 3. 总体产品原则

### 3.1 默认项目优先降低学习成本

用户通过 Bootstrap 创建第一个项目，是为了快速理解 cap4k 的建模和调用手感，不是为了第一次启动就接受完整的分布式可靠性设施。

官方默认项目采用最小同步模式。高级可靠性能力通过显式依赖加入。

### 3.2 能力由依赖决定

具备明确运行时依赖关系的能力，应由 starter 组合形成合法配置，而不是由一个全能力依赖再配合大量 `enabled=false` 开关裁剪。

配置用于表达真实策略选择，不用于掩盖错误的模块聚合。

### 3.3 默认组合与版本管理分离

- Bootstrap 提供官方默认依赖组合。
- BOM 统一 cap4k 组件版本。
- starter 提供单一、可理解的能力。
- 不保留把所有高级能力重新聚合给普通用户的公共入口。

### 3.4 默认项目不制造删除任务

官方项目不生成示例业务、占位业务或需要用户理解后删除的演示代码。框架只生成正式分层、运行入口、测试结构和必要基础骨架。

## 4. 官方默认项目契约

### 4.1 项目形态

当前官方项目只支持既有的多模块 Bootstrap 形态：

```text
project
├── domain
├── application
├── adapter
└── start
```

模块名仍可通过 Bootstrap 配置调整，但四种角色保持固定。

本轮不增加单模块 Bootstrap。单模块支持会影响所有初始化入口、模块假设、生成路径和测试契约，必须作为未来独立设计处理。

### 4.2 默认能力

官方默认项目包含：

- Spring Boot 本地运行入口。
- MVC。
- JPA Repository 与 Unit of Work。
- Aggregate Factory。
- UUIDv7 默认 ID 策略。
- 强类型 ID 所需运行时能力。
- 基础同步 Command / Query 路由。
- 本地 Domain Event 表达和进程内分发。
- 正式的分层和测试结构。

这些能力不得要求 cap4k 框架表才能首次启动。

### 4.3 默认不包含

官方默认项目不包含：

- 可靠异步 Request 持久化。
- 可靠 Event 持久化。
- JDBC Locker 实现。
- Integration Event。
- HTTP、RabbitMQ、RocketMQ 等 Integration Event Transport。
- Saga。
- Snowflake。
- 部署设施。
- 示例业务。
- cap4k Skill。

### 4.4 默认数据库要求

默认项目可以使用业务 JPA 表，但不要求以下 cap4k 框架表：

- Request 与 Archived Request 表。
- Event 与 Archived Event 表。
- Locker 表。
- Saga 与 Archived Saga 表。
- Snowflake worker-id 表。
- Integration Event HTTP subscriber 表。

因此，类似 `content-studio-runtime-init-h2.sql` 的框架运行时 SQL 不能成为默认项目首次启动的前置步骤。

## 5. 本轮目标：审计并拆除运行时硬耦合

本轮不是机械地“拆几个 starter”，而是：

> 审计并拆除运行时模块之间不合理的硬耦合，让依赖树真实表达能力边界。

数据库自动初始化仍然后置；但任何会让最小同步项目被动引入框架表、调度器、消息适配器或高级能力的依赖，都必须在本轮处理。

### 5.1 已确认的耦合问题

| 当前问题 | 本轮处理方向 |
| --- | --- |
| `ddd-domain-repo-jpa` 依赖 `ddd-domain-event-jpa` | 删除依赖；业务 JPA 仓储不能自动获得可靠事件持久化 |
| `cap4k-ddd-starter` 聚合 Core、JPA、Locker、Snowflake、Request、Event、Saga | 拆除并删除聚合入口，各 starter 独立发布，由 BOM 统一版本 |
| `IdPolicyAutoConfiguration` 同时认识 UUID7 和 Snowflake | UUID7 留在 Core starter；Snowflake Strategy 移入 Snowflake starter |
| `RequestAutoConfiguration` 同时负责同步路由、JPA Record、调度、补偿和归档 | 拆成 Core 的基础同步 Request 装配与可靠 Request JPA 装配 |
| `DomainEventAutoConfiguration` 同时负责本地事件、Integration Event、JPA Event、Locker 和定时任务 | 拆成本地事件运行时、可靠 Event JPA、Integration Event 三个边界 |
| `IntegrationEventAutoConfiguration` 同时装配 HTTP、HTTP JPA、RabbitMQ 和 RocketMQ | 每个 Transport starter 管理自己的自动装配 |
| `JpaRepositoryAutoConfiguration` 仍装配旧 Specification 机制 | 删除 Specification 运行时实现与装配 |
| 可选能力通过 `@ConditionalOnClass` 塞在大 starter 中 | 改为引入对应 starter 才存在对应自动装配 |

### 5.2 本轮不拆分 `ddd-core` API

`ddd-core` 当前可以继续共置 Request、Event、Integration Event、Saga 的接口及部分无数据库实现。

理由：

- `ddd-core` 本身不引入框架表、JDBC/JPA 持久化实现或调度任务。
- API 共置不会让最小项目创建数据库表或启动高级运行时。
- 继续拆成 `request-api`、`event-api`、`saga-api` 会波及大量包名、生成模板、文档和测试。
- 当前真实问题是实现与自动装配硬耦合，不是 classpath 中能看到未使用的接口。

因此本轮边界固定为：

> 拆实现、自动装配和 starter，不拆 `ddd-core` 的 API 包；等 API 共置产生真实维护问题后再讨论 Core 分裂。

### 5.3 目标 starter 矩阵

底层 `ddd-*` 实现模块可以继续保留，但自动装配分别迁入对应 starter。

| Starter | 直接拥有的能力 | 依赖 | 运行时前提 | 框架表 | 默认模板 |
| --- | --- | --- | --- | --- | --- |
| `cap4k-ddd-core-starter` | Mediator、同步 Command/Query、本地同步 Domain Event、Domain Service、UUID7 | `ddd-core` | Spring Context，Validator 可选 | 无 | 是 |
| `cap4k-ddd-jpa-starter` | Repository、Unit of Work、Aggregate Factory、JPA ID 注入 | Core starter、`ddd-domain-repo-jpa` | Spring Data JPA | 无框架表，只有业务表 | 是 |
| `cap4k-ddd-request-starter` | 可靠异步 Request、schedule、retry、result、补偿和归档 | Core starter、`ddd-application-request-jpa`、`Locker` 接口 | JPA、一个 `Locker` Bean、非空应用名 | `__request`、`__archived_request` | 否 |
| `cap4k-ddd-event-starter` | 可靠 Event、Outbox、重试、补偿和归档 | Core starter、`ddd-domain-event-jpa`、`Locker` 接口 | JPA、一个 `Locker` Bean、非空应用名 | `__event`、`__archived_event` | 否 |
| `cap4k-ddd-locker-jdbc-starter` | JDBC `Locker` 实现 | `ddd-core` 中的 `Locker` 接口、`ddd-distributed-locker-jdbc` | `JdbcTemplate` | `__locker` | 否 |
| `cap4k-ddd-saga-starter` | Saga 持久化、执行、补偿和归档 | Core starter、`ddd-distributed-saga-jpa`、`Locker` 接口 | JPA、一个 `Locker` Bean、非空应用名 | `__saga`、`__saga_process` | 否 |
| `cap4k-ddd-snowflake-starter` | Snowflake Generator 和对应 ID Strategy | Core starter、`ddd-distributed-snowflake` | `JdbcTemplate` | `__worker_id` | 否 |
| `cap4k-ddd-integration-event-starter` | Integration Event 公共运行时和 best-effort/可靠模式桥接 | Core starter | 至少一个 Transport Publisher | 无 | 否，仅供 Transport 传递依赖 |
| `cap4k-ddd-integration-event-http-starter` | HTTP Publisher、Subscriber 和端点 | Integration Event starter、`ddd-integration-event-http` | Spring Web | 无 | 否 |
| `cap4k-ddd-integration-event-http-jpa-starter` | HTTP Subscriber 注册信息持久化 | HTTP starter、`ddd-integration-event-http-jpa` | JPA | `__event_http_subscriber` | 否 |
| `cap4k-ddd-integration-event-rabbitmq-starter` | RabbitMQ Transport | Integration Event starter、`ddd-integration-event-rabbitmq` | RabbitMQ 客户端配置 | 无 | 否 |
| `cap4k-ddd-integration-event-rocketmq-starter` | RocketMQ Transport | Integration Event starter、`ddd-integration-event-rocketmq` | RocketMQ 客户端配置 | 无 | 否 |

名称表达用户选择的能力，而不是当前持久化技术。即使当前 Request、Event、Saga 官方实现是 JPA，也继续使用通用 starter 名称；JPA 前提和 SQL 必须在各 starter 文档中明确。

### 5.4 模块硬规则

- `cap4k-ddd-bom` 只管理版本，没有运行时依赖。
- 当前大而全的 `cap4k-ddd-starter` 删除，不保留兼容聚合入口。
- 默认 Bootstrap 只引入 `cap4k-ddd-core-starter` 和 `cap4k-ddd-jpa-starter`。
- 一个可选 starter 只能带入自身能力的实现。
- 可选 starter 可以依赖 `ddd-core` 中的接口，但不能依赖另一个可选能力的具体 starter 或持久化实现。
- Request/Event/Saga starter 不依赖 JDBC Locker starter。
- Request/Event/Saga starter 之间不相互依赖。
- Saga 可以依赖 Request 的接口或同步路由能力，但不自动引入 Request JPA。
- JPA Repository 可以参与本地领域事件生命周期，但不自动引入可靠 Event JPA。
- Transport starter 不依赖可靠 Event starter。
- HTTP JPA 不反向污染普通 HTTP Transport。
- Snowflake starter 注册 Snowflake ID Strategy；默认 UUID7 装配不认识 Snowflake 实现。
- Spring Boot 的 `AutoConfiguration.imports` 分散到各 starter，不再集中注册所有能力。
- `@ConditionalOnClass` 只用于 starter 自身第三方库条件，不再用于在大 starter 中猜测用户需要哪项 cap4k 能力。

## 6. starter 依赖规则

### 6.1 拆除跨能力实现依赖

当前 `ddd-domain-repo-jpa` 以 `implementation` 依赖 `ddd-domain-event-jpa`。这会让选择 JPA Repository 的项目被动获得 Event JPA，实现边界错误，本轮必须拆除。

源码审计还必须检查所有类似耦合：

- Repository 实现传递引入 Event 实现。
- Request/Event/Saga 传递引入 JDBC Locker。
- 基础 starter 传递引入 Snowflake。
- Domain Event 传递引入 Integration Event。
- Transport 反向成为基础事件分发的必需依赖。
- 测试 fixture 依赖误进入生产 API 或 implementation 图。

发现同类耦合时直接按本文能力边界拆分，不为当前模块结构保留兼容层。

### 6.2 可靠能力只依赖 Locker 抽象

可靠 Request、可靠 Event 和 Saga 可以依赖 Locker 接口，但不能依赖 `cap4k-ddd-locker-jdbc-starter`。

最终应用负责选择 Locker 实现。对 Request/Event/Saga starter，`Locker` 是已声明的运行时前提；引入 starter 却没有 `Locker` Bean 时，Spring Context 必须以明确的缺失依赖错误启动失败，不能静默退化或等待后台任务运行后才暴露问题。

### 6.3 Request 的默认边界

Request 的语义固定为：

- Core starter 提供同步 `send`。
- 同步 `send` 不依赖 Request Record、JPA、Locker、调度器或框架表。
- `async`、`schedule`、`retry`、`result` 属于可靠 Request 能力。
- 缺少 `cap4k-ddd-request-starter` 时调用可靠异步 API，必须立即抛出明确错误。
- 不提供与可靠 API 同名的静默内存降级。

也就是说，默认项目可以完整体验同步 Command / Query，但不能误以为没有 Request starter 时仍获得可靠异步语义。

### 6.4 Integration Event 的两级语义

Integration Event 允许在没有可靠 Event starter 时运行，但必须显式区分 best-effort 与可靠模式。

#### 仅引入 Transport starter

例如只引入 HTTP、RabbitMQ 或 RocketMQ starter：

- 启用 Integration Event 公共运行时和对应 Transport。
- 事务提交后直接尝试发布。
- 不写入 `__event`。
- 不提供 Outbox、重试、补偿、归档或至少一次投递保证。
- 发送期间发生进程崩溃、网络失败或 Broker 不可用时，事件可能丢失。
- 该模式明确命名和记录为 best-effort。

#### 同时引入可靠 Event starter

- Integration Event 先进入可靠 Event Record。
- Event JPA 提供 Outbox、重试、补偿和归档。
- 获得至少一次发布语义。
- 需要 `__event`、`__archived_event` 以及用户选择的 `Locker` Bean。

可靠增强通过公共接口接入。Transport starter 不直接依赖 `cap4k-ddd-event-starter` 或 `ddd-domain-event-jpa`。

### 6.5 Integration Event 模块边界

- 默认 Core 不装配 Integration Event。
- HTTP、RabbitMQ、RocketMQ 各自拥有独立 starter 和自动装配。
- 多个 Transport 共享的 Integration Event 基础运行时位于公共实现层，不把具体 Transport 逻辑放回 `ddd-core`。
- Event JPA 出现时通过接口增强可靠性，不让 Transport 认识其具体实现。
- HTTP Subscriber JPA 保持独立，不随 HTTP Transport 自动引入。
- 不再由一个巨大的 `IntegrationEventAutoConfiguration` 使用 `@ConditionalOnClass` 同时管理所有适配器。

### 6.6 Domain Event、可靠 Event 与 Integration Event 分离

- Domain Event 是领域语言和本地进程内协作能力。
- 可靠 Event 是持久化、Outbox、重试、补偿和归档能力。
- Integration Event 是跨边界发布语言。
- Transport 是 Integration Event 的传输实现。

这四者不能继续由一个默认 starter 或一个扫描配置隐式绑定。

## 7. 删除 `event-scan-package` 与 classpath 事件扫描

### 7.1 已确认结论

`event-scan-package` 直接删除。不替换为“自动推导 Spring Boot 根包”，而是删除事件类型的 classpath 预扫描机制本身。

当前扫描承担两类职责：

1. 枚举 Domain Event 和 Integration Event 类型，为每种类型注册 Spring Event 桥接订阅者。
2. 为 HTTP、RabbitMQ、RocketMQ 枚举入站 Integration Event 类型，建立消费者和反序列化映射。

两类职责都改为由运行时真实信息驱动。

### 7.2 本地事件桥接

`EventSubscriberManager.dispatch(payload)` 收到 payload 后：

1. 分发给 cap4k 自身的 EventSubscriber。
2. 调用 `ApplicationEventPublisher.publishEvent(payload)`。

Spring 根据实际 payload 类型将事件路由到匹配的 `@EventListener`。本地桥接不需要提前枚举事件类。

### 7.3 Integration Event 订阅注册表

新增 `IntegrationEventSubscriptionRegistry`，以真实订阅入口作为入站 Integration Event 类型来源。

`Cap4kEventListenerFactory` 创建 `@EventListener` Listener 时：

1. 读取 Listener 方法的有效事件参数类型。
2. 若该类型标记为 `@IntegrationEvent`，登记到 Registry。
3. Transport 在 Listener 注册完成后读取 Registry。
4. HTTP、RabbitMQ、RocketMQ 只为本服务真实订阅的 Integration Event 建立消费者和反序列化映射。

没有对应 `@EventListener` Handler 的 Integration Event 不建立消费者。消费者发现由真实订阅关系决定，不由包结构决定。

实施时必须验证 Registry 与 Transport 的生命周期顺序，确保 Transport 读取时 Listener 类型已登记；这属于实现约束，不改变注册表方案。

### 7.4 删除范围

本轮至少删除：

- `EventProperties.eventScanPackage`。
- 所有配置文件中的 `cap4k.ddd.domain.event.event-scan-package`。
- Transport 构造函数和装配代码中的 `scanPath`。
- `DefaultEventSubscriberManager` 中的 Domain/Integration Event 类扫描。
- `findDomainEventClasses`。
- `findIntegrationEventClasses`。
- 只为事件扫描服务的 `ScanUtils.kt` 和对应测试；若审计发现其他真实用途，则只保留非事件扫描部分。
- Bootstrap 模板、测试 fixture、示例和文档中的扫描包配置。

删除后的结果必须满足：

- 用户不配置事件扫描包。
- 空字符串不再导致启动失败。
- 项目包结构和公共父包不再影响事件发现。
- 多模块项目不需要计算共同扫描根。
- 不扫描没有 Handler 的事件类型。
- 本地 Domain Event 与 Spring `@EventListener` 桥接继续工作。

## 8. 运行时配置瘦身

本轮采用配置瘦身方案 A：

> 删除能力启停和结构配置，只保留真实的运行策略与运维调优项。

能力边界由 starter 依赖决定。高级用户需要替换底层实现时提供 SPI Bean，而不是通过字符串配置重写框架内部结构。

### 8.1 删除的配置

#### 能力启停开关

删除：

- `snowflake.enable`。
- `supportEntityInlinePersistListener`。
- `addPartitionEnable`。
- 其他与 starter 存在性重复的能力开关。

如果用户不希望某项可选能力存在，应删除对应 starter，而不是设置 `enable=false`。

#### 框架表名和字段名

删除用户自定义：

- Locker 表名和字段名。
- Snowflake 表名和字段名。
- Request/Event/Saga 表名和字段名。

Request、Event、Saga、Locker 和 Snowflake 使用固定框架表契约。高级用户如果需要不同结构，应替换对应 Repository、Locker 或存储 SPI，而不是配置每个表名和字段名。

#### 扫描、线程工厂和分区配置

删除：

- `eventScanPackage`。
- 所有 `*ThreadFactoryClassName`。
- `addPartitionCron`。
- 旧 camelCase/kebab-case 双重兼容键。
- 当前不统一的 `application/domain/distributed` 历史层级前缀。

线程执行器需要深度定制时，用户通过特定类型或名称的 Spring Bean 覆盖，不再填写线程工厂实现类名字符串。

### 8.2 引入 starter 后固定开启的行为

#### Core starter

- Mediator。
- 同步 Command / Query。
- 本地同步 Domain Event。
- UUID7。
- Domain Service。

#### JPA starter

- Repository。
- Unit of Work。
- Aggregate Factory。
- Entity inline lifecycle listener。

#### Request/Event/Saga starter

- 对应可靠持久化。
- 补偿任务。
- 归档任务。

#### Snowflake starter

- 注册 Snowflake Generator。
- 注册 Snowflake ID Strategy。

默认 UUID7 装配不包含任何 Snowflake 类型或条件判断。

#### Transport starter

- 引入对应 starter 即装配对应 Transport。
- 不通过大 starter 中的 `@ConditionalOnClass` 猜测能力。

### 8.3 保留的配置

只保留具有真实生产调优价值的配置。

#### JPA

- `retrieve-count-warn-threshold`。

#### Request

- 执行线程池大小。
- 补偿 cron。
- 补偿批次大小。
- 最大并发数。
- 补偿间隔。
- 最大锁时长。
- 归档 cron。
- 归档批次大小。
- 归档保留天数。
- 归档最大锁时长。

#### Event

- Publisher 线程池大小。
- 与 Request 对应的补偿和归档调优项。

#### Saga

- 异步线程池大小。
- 与 Request 对应的补偿和归档调优项。

#### Snowflake

- 可选固定 `worker-id`。
- 可选固定 `datacenter-id`。
- Worker 租约时间。
- 主机标识。
- 最大连续心跳失败次数。

#### Transport

- HTTP/RabbitMQ 发布线程池大小。
- RabbitMQ 是否自动声明 Exchange/Queue。
- RabbitMQ 默认 Exchange 类型。
- RocketMQ、RabbitMQ 连接信息继续使用官方 Spring 配置，不在 cap4k 中重复包装。

### 8.4 配置前缀统一

配置前缀与 starter 能力对齐：

```text
cap4k.ddd.jpa
cap4k.ddd.request
cap4k.ddd.event
cap4k.ddd.saga
cap4k.ddd.locker.jdbc
cap4k.ddd.snowflake
cap4k.ddd.integration-event.http
cap4k.ddd.integration-event.rabbitmq
cap4k.ddd.integration-event.rocketmq
```

同时把历史拼写 `compense` 统一修正为 `compensation`。当前没有外部用户，不保留旧前缀、旧拼写或旧键兼容。

### 8.5 默认项目配置效果

官方最小同步项目的 `application.yml` 不出现任何 `cap4k.ddd.*` 配置，只保留正常 Spring 配置，例如：

```yaml
spring:
  application:
    name: example
  datasource:
    # ...
```

引入可靠 Request/Event/Saga starter 后，所有调优项均有默认值，用户无需先复制一组 cap4k 配置；但必须提供：

- 对应框架表。
- 一个 `Locker` Bean。
- 非空的 `spring.application.name`。

## 9. 生成器能力调整

### 9.1 Unique 自动生成迁移到 B 类 addon

根据 Schema 唯一约束自动生成 Unique Query、Handler 和 Validator 属于实验性附加能力：

- 不进入默认生成流程。
- 不作为核心 generator 的普通开关。
- 迁移到 B 类、高级或实验 addon。

这一决策不否定数据库唯一约束，也不禁止用户手写唯一性相关用例；只是不把当前自动生成形态当作默认产品能力。

### 9.2 删除当前 Aggregate Specification 实现

删除当前围绕 Aggregate 保存前 Specification 的框架实现和生成能力。

理由：

- 只能在数据库落库前进行校验。
- 无法自然依赖 Repository、基础设施或外部能力。
- 当前示例项目没有形成实际使用。
- 当前实现没有为用户提供足够的独立价值。

否定的是 cap4k 当前实现，不是否定 Specification 概念本身。未来若出现不同的、能力完整的设计，应作为新能力重新评审，不能恢复当前实现。

删除审计至少覆盖：

- core 接口、Manager、Interceptor 和自动装配。
- aggregate specification planner、template、option 和默认配置。
- renderer、functional fixture 和相关测试。
- 文档、Skill 和 capability map 中对当前实现的承诺。

### 9.3 删除 `Reentrant`

删除：

- `Reentrant` 注解。
- `ReentrantAspect`。
- JDBC Locker 自动装配中的 Aspect bean。
- 对应测试、文档和配置引用。

当前框架内部没有形成必须保留该用户注解的实际依赖。cap4k 没有外部用户，不保留兼容壳。

### 9.4 不扩张为通用“旧生成物删除”机制

删除 generator 能力时，只处理仓库内当前模板、配置、测试和受控 fixture。本轮不建设通用的旧生成文件识别与删除协议。

生成器如何安全删除历史生成物是所有生成器共同面对的未来议题，不能为了本轮 Specification 或 Skill 单独扩大范围。

## 10. Bootstrap 边界

### 10.1 Bootstrap 只负责项目骨架

`cap4kBootstrapPlan` / `cap4kBootstrap` 本轮继续生成多模块项目，并调整为本文定义的最小同步默认组合。

Bootstrap 负责：

- `domain/application/adapter/start` 四种模块角色。
- 根构建和模块构建结构。
- 最小可运行的 Spring Boot host。
- 官方默认依赖组合。
- 正式测试结构。

Bootstrap 不负责：

- 示例业务。
- 可靠运行时能力的默认引入。
- 单模块项目。
- 消息中间件或部署设施。
- 框架数据库迁移。
- Skill 安装。

### 10.2 Bootstrap 不管理 Skill

cap4k Skill 完全位于 Bootstrap 契约之外。

Bootstrap 不执行：

- 生成 Skill。
- 从制品复制 Skill。
- 写入或覆盖 `.agents/skills`。
- 更新、合并或删除旧 Skill。
- Skill 版本管理。
- Skill 冲突处理。
- Skill 离线打包。
- 运行时从 GitHub 下载 Skill。

用户需要 cap4k Skill 时，自行从 GitHub 仓库复制。未来可以在文档中提供获取说明，但 Bootstrap 本身不参与。

因此，本轮不存在“Bootstrap 管理的官方 Skill 命名空间”“直接覆盖策略”或“旧 Skill 删除策略”。

## 11. 框架数据库表与初始化

### 11.1 本轮不建设自动迁移体系

框架表自动初始化可以后置，因为默认项目不启用需要框架表的能力。

本轮规则：

- 默认项目不携带框架表 SQL。
- 默认启动不检查 Request、Event、Locker、Saga、Snowflake 或 subscriber 表。
- 用户显式引入高级 starter 后，按照该 starter 的文档准备对应表。
- SQL 和表结构说明归属于具体能力，不归属于默认 Bootstrap。
- 现阶段允许用户显式执行 starter 提供或文档引用的 SQL。

### 11.2 未来独立迭代

以下内容以后作为单独设计处理：

- starter 自带版本化迁移。
- 开发环境自动初始化。
- 测试环境可重复重建。
- 生产环境禁止隐式改表。
- 多数据库方言和迁移升级路径。

未来自动迁移方案不得反向让最小同步项目依赖框架表。

## 12. Saga 的本轮定位

Saga 是极少使用、当前能力偏弱的高级功能。

本轮固定决策：

- Saga 不进入官方默认项目。
- Saga 与基础 Request、Event、JPA、Locker 和 Snowflake starter 隔离。
- Saga 可以依赖所需抽象和显式能力，但不能迫使普通项目引入 Saga。
- 先完成模块隔离，不以 Saga 理论讨论阻塞本轮。

本轮不决定：

- 删除 Saga。
- 将 Saga 降级为实验模块。
- 重建为 durable Process Manager。
- 事件 choreography 是否可以覆盖所有 Saga 场景。

这些问题只有在 Saga 隔离完成后、出现真实高级用户场景时才需要重新评审。

## 13. 明确后置和排除范围

本轮不实施：

- GitHub Template Repository 按钮。
- IDEA 项目初始化向导。
- 类似 Spring Initializr 的服务。
- 单模块 Bootstrap。
- 框架表自动迁移体系。
- Saga 重建或理论定位重审。
- HTTP Integration Event 的替代方案。
- 通用旧生成文件删除机制。
- Bootstrap Skill 管理。
- Skill 离线制品、版本或冲突协议。
- 示例业务。
- 为不存在的外部用户提供兼容层。

这些项目不能在实施过程中以“顺便处理”的方式重新进入范围。

## 14. 本轮实施范围

后续实施计划应围绕以下工作拆分：

1. 建立当前 Gradle 模块和 Spring AutoConfiguration 依赖图。
2. 按第 5.3 节矩阵建立 BOM 和全部独立 starter。
3. 拆除 `ddd-domain-repo-jpa -> ddd-domain-event-jpa` 及同类错误实现依赖。
4. 删除当前 `cap4k-ddd-starter`，把 `AutoConfiguration.imports` 分散到各 starter。
5. 将 Request、Event、Locker、Saga、Snowflake、Integration Event、HTTP JPA 和各 Transport 隔离。
6. 保持 `ddd-core` API 共置，同时确保 Core starter 的同步 Command / Query 与本地 Domain Event 在无框架表环境运行。
7. 固化 Request 语义：同步 `send` 默认可用，缺少 Request starter 时可靠异步 API 调用即报错。
8. 实现 Integration Event 的 Transport-only best-effort 模式和 Event starter 可靠增强模式。
9. 删除 `event-scan-package` 和事件 classpath 扫描。
10. 实现真实 `@EventListener` 驱动的 `IntegrationEventSubscriptionRegistry`。
11. 按第 8 节精确清单删除、固定和保留运行时配置，并统一前缀。
12. 瘦身 generator 配置，将 Unique 自动生成迁移到 B 类 addon。
13. 删除当前 Aggregate Specification 实现。
14. 删除 `Reentrant`。
15. 调整多模块 Bootstrap，使默认依赖只有 Core starter 和 JPA starter，且不包含示例业务、框架 SQL 和 Skill。
16. 按新模块边界重组测试 fixture 和验证命令。

在进入代码修改前，必须先形成独立实施计划；本文不规定文件移动顺序和提交拆分。

## 15. 当前源码事实索引

以下路径用于帮助后续实施者快速恢复现状，不代表当前实现是目标设计：

- [当前全能力聚合 starter](../../../cap4k-ddd-starter/build.gradle.kts)
  - API 暴露 core、Snowflake、JDBC Locker 和 JPA Repository。
  - implementation 引入 Request JPA、Saga JPA 和 Event JPA。
- [当前 JPA Repository 模块](../../../ddd-domain-repo-jpa/build.gradle.kts)
  - 当前以 implementation 依赖 Event JPA，是已确认的错误耦合。
- [IdPolicyAutoConfiguration](../../../cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfiguration.kt)
  - 当前同时装配 UUID7 和 Snowflake Strategy。
- [RequestAutoConfiguration](../../../cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/application/request/RequestAutoConfiguration.kt)
  - 当前同时装配 JPA Record Repository、Request Supervisor、调度服务、补偿任务和归档任务。
- [DomainEventAutoConfiguration](../../../cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/event/DomainEventAutoConfiguration.kt)
  - 当前同时装配本地事件、Event JPA、Integration Event 协作、Locker 调度服务和定时任务。
- [IntegrationEventAutoConfiguration](../../../cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/application/event/IntegrationEventAutoConfiguration.kt)
  - 当前在一个自动装配中通过条件判断管理 HTTP、HTTP JPA、RabbitMQ 和 RocketMQ。
- [JpaRepositoryAutoConfiguration](../../../cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt)
  - 当前仍装配 Specification Manager、Specification UoW Interceptor 和可开关的 Entity inline listener。
- [当前集中式 AutoConfiguration imports](../../../cap4k-ddd-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports)
  - 当前统一登记 Core、JPA、Request、Event、Saga、Locker、Snowflake 和 Integration Event 配置。
- [EventProperties](../../../cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/event/configure/EventProperties.kt)
  - 当前定义空字符串默认值的 `eventScanPackage`。
- [DefaultEventSubscriberManager](../../../ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultEventSubscriberManager.kt)
  - 当前使用扫描结果为事件类型建立桥接订阅。
- [Cap4kEventListenerFactory](../../../ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/Cap4kEventListenerFactory.kt)
  - 作为真实 `@EventListener` 类型登记的现有接入点。
- [ScanUtils](../../../ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/share/misc/ScanUtils.kt)
  - 当前包含事件 classpath 扫描函数。
- [Reentrant](../../../ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/distributed/annotation/Reentrant.kt)
- [ReentrantAspect](../../../ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/distributed/impl/ReentrantAspect.kt)
- [JDBC Locker 自动装配](../../../cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/application/distributed/JdbcLockerAutoConfiguration.kt)
  - 当前创建 `ReentrantAspect` bean。

Specification 的实现跨 core、pipeline API、aggregate generator、Pebble renderer、functional fixture、文档和 Skill。实施计划必须通过全仓搜索建立删除清单，不能只删除模板。

## 16. 实施问题与产品决策的边界

以下内容允许在实施计划中确定：

- 现有底层 `ddd-*` 模块中的代码是移动、拆分还是由新 starter 组合。
- 已固定 starter 内部的 AutoConfiguration 类名、包名和文件布局。
- Registry 的 Spring 生命周期和并发数据结构。
- Integration Event 可靠增强接口的具体 SPI 形态。
- 线程执行器覆盖 Bean 的类型和名称。
- 测试 fixture 如何按 starter 隔离。
- 各可选 starter 的 SQL 文件位置和文档格式。

这些实现选择必须同时满足：

- starter artifact 名称和第 5.3 节矩阵不变。
- 本轮不拆 `ddd-core` API。
- 默认项目保持最小同步。
- 删除当前全能力 `cap4k-ddd-starter`。
- Request 同步 `send` 默认可用，可靠异步 API 缺少 Request starter 时调用即报错。
- Integration Event 同时支持 Transport-only best-effort 和 Event starter 可靠增强。
- 不恢复事件包扫描。
- 不让 Request/Event/Saga 固定依赖 JDBC Locker。
- 不让 JPA Repository 固定依赖 Event JPA。
- 不让 Transport 固定依赖 Event JPA。
- 不改变第 8 节已确认的配置删除、保留和前缀清单。
- 不让 Bootstrap 管理 Skill。
- 不把数据库自动迁移带回本轮。

## 17. 验收标准

设计实施完成后必须满足：

1. Bootstrap 生成的默认多模块项目不含示例业务。
2. 默认 Bootstrap 只引入 `cap4k-ddd-core-starter` 和 `cap4k-ddd-jpa-starter`。
3. 默认项目只使用最小同步、MVC、JPA、UUID7/Strong ID、Factory 和本地 Domain Event 所需能力。
4. 默认项目无需任何 cap4k 框架表即可启动和执行基础同步用例。
5. 第 5.3 节定义的 starter 均形成独立发布和自动装配边界。
6. `cap4k-ddd-bom` 只统一版本，不携带运行时依赖。
7. 当前全能力 `cap4k-ddd-starter` 被删除，不保留兼容聚合入口。
8. 本轮没有为了 starter 拆分继续拆分 `ddd-core` API。
9. JPA Repository 不依赖 Event JPA。
10. Request/Event/Saga 只依赖 Locker 抽象，不固定依赖 JDBC Locker starter，且三者不相互依赖。
11. Transport 不依赖可靠 Event starter，HTTP JPA 不污染普通 HTTP Transport。
12. Snowflake Strategy 只由 Snowflake starter 注册，Core 的 UUID7 装配不认识 Snowflake 实现。
13. 同步 Request `send` 在 Core starter 中可用；缺少 Request starter 时调用 `async/schedule/retry/result` 明确报错。
14. 仅引入 Transport 时，Integration Event 以无 `__event` 表的 best-effort 模式运行。
15. 同时引入 Event starter 时，Integration Event 获得 Event Record、重试、补偿、归档和至少一次发布语义。
16. 各 starter 拥有自己的 `AutoConfiguration.imports`；不再由大 starter 通过 `@ConditionalOnClass` 管理所有 cap4k 能力。
17. `event-scan-package`、事件 classpath 扫描及其模板配置全部消失。
18. Transport 只为真实 `@EventListener` Integration Event Handler 建立消费者。
19. 第 8.1 节能力开关、表字段、扫描、线程工厂类名、分区 cron、旧键和旧前缀全部删除。
20. 只保留第 8.3 节运维调优配置，并使用第 8.4 节统一前缀和 `compensation` 拼写。
21. 官方默认项目的 `application.yml` 不出现 `cap4k.ddd.*` 配置。
22. Unique 自动生成不再属于默认核心。
23. 当前 Aggregate Specification 实现和 `Reentrant` 完整删除。
24. Bootstrap 不读取、生成、复制、覆盖或删除 Skill。
25. 本轮没有引入框架表自动迁移、单模块初始化或公开项目模板。

## 18. 上下文恢复摘要

如果后续会话发生上下文压缩，只需恢复以下事实：

- 先稳定官方项目契约，再做传播入口。
- 官方默认项目是无示例业务的多模块最小同步项目。
- 默认能力不需要 cap4k 框架表。
- BOM 只管版本；默认 Bootstrap 只引入 Core starter 和 JPA starter。
- starter 使用第 5.3 节固定矩阵；删除全能力 `cap4k-ddd-starter`。
- 本轮拆实现、自动装配和 starter，不拆 `ddd-core` API。
- JPA Repository 与 Event JPA 解耦。
- Request/Event/Saga 依赖 Locker 抽象，不依赖 JDBC 实现，也不相互依赖。
- Request 默认只有同步 `send`；可靠异步 API 缺少 Request starter 时调用即报错。
- Integration Event 支持 Transport-only best-effort 与 Event starter 至少一次发布两级语义。
- Integration Event、Saga、Snowflake、可靠 Request/Event、Transport 和 HTTP JPA 全部 opt-in。
- Snowflake Strategy 只存在于 Snowflake starter。
- 每个 starter 自己拥有 AutoConfiguration，不能用大包加 `@ConditionalOnClass` 模拟能力拆分。
- 删除 `event-scan-package` 和 classpath 扫描，改用真实 `@EventListener` Registry。
- 配置采用方案 A：删除能力与结构配置，只保留运维调优项，统一 starter 对齐前缀和 `compensation` 拼写。
- 默认项目 `application.yml` 不含 `cap4k.ddd.*`。
- Unique 自动生成迁移到 B 类 addon。
- 删除当前 Aggregate Specification 和 `Reentrant`。
- 框架表自动迁移后置。
- Bootstrap 不管理 Skill；用户需要时自行从 GitHub 复制。
- 单模块、GitHub Template、IDEA/Initializr 和 Saga 重建全部后置。
