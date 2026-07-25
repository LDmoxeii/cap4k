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
- Console。
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

## 5. 运行时能力分层

下表定义产品能力边界。具体 Gradle artifact 名称在实施计划中结合现有发布坐标最终落定，但不得改变这里的依赖方向。

| 能力 | 默认项目 | 数据库要求 | 边界 |
| --- | --- | --- | --- |
| Core 同步路由 | 包含 | 无框架表 | Command / Query 同步路由及基础运行时契约 |
| JPA Repository / UoW | 包含 | 仅业务 JPA 结构 | 不得传递引入 Event、Request、Saga、Locker 或 Snowflake 实现 |
| UUIDv7 / Strong ID / Factory | 包含 | 无框架表 | 官方基础能力，不再用普通布尔开关拼装 |
| 本地 Domain Event | 包含 | 无框架表 | 进程内分发，不等于可靠事件持久化 |
| 可靠 Request | 可选 | Request / Archived Request 表 | 为 async、schedule、retry、result 等可靠语义提供持久化实现 |
| 可靠 Event | 可选 | Event / Archived Event 表 | 提供至少一次投递、补偿或归档所需实现 |
| Locker | 抽象按需使用 | 取决于实现 | Request/Event/Saga 依赖 Locker 抽象，不依赖 JDBC starter |
| JDBC Locker | 可选 | Locker 表 | Locker 的一种实现，不是可靠能力模块的固定传递依赖 |
| Integration Event | 可选 | 取决于可靠实现和 Transport | 单体项目不需要默认引入 |
| HTTP/RabbitMQ/RocketMQ Transport | 可选 | 取决于适配器 | 各 Transport 独立装配，只消费真实订阅类型 |
| Saga | 高级可选 | Saga 相关表 | 先隔离，不进入默认项目 |
| Snowflake | 可选 | worker-id 存储 | UUIDv7 默认项目不出现 Snowflake |

### 5.1 starter 目标

目标架构至少形成以下独立能力入口：

- 最小基础同步入口。
- `cap4k-ddd-jpa-starter`。
- `cap4k-ddd-request-starter`。
- `cap4k-ddd-event-starter`。
- `cap4k-ddd-locker-jdbc-starter`。
- 独立 Saga starter。
- 独立 Snowflake starter。
- 独立 Integration Event 能力及各 Transport starter。
- cap4k BOM。

当前 `cap4k-ddd-starter` 同时聚合 core、Snowflake、JDBC Locker、JPA Repository、Request JPA、Saga JPA、Event JPA 和 Integration Event 适配器。该“全能力入口”不再是目标公共契约。

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

最终应用负责选择 Locker 实现。若某个已启用的可靠能力必须使用 Locker，而应用没有提供任何实现，应在启动或首次建立该能力时给出明确错误，不能静默退化。

### 6.3 同步路径不依赖可靠持久化

基础同步 Command / Query 必须在没有 Request JPA、Event JPA、Locker、Saga 和 Snowflake 的情况下正常工作。

可靠 API 的处理规则为：

- `async`、`schedule`、`retry`、`result` 等操作如果承诺持久化、重试或可恢复语义，则缺少对应实现时必须明确失败。
- 不允许表面调用成功、实际悄悄失去可靠性保证。
- 若框架保留某种进程内、非可靠实现，其 API 和文档必须明确表达非可靠语义；不能与可靠 API 共用含糊契约。
- 用户可以显式选择并承担非可靠模式的代价，框架不能替用户隐式选择。

### 6.4 Domain Event、可靠 Event 与 Integration Event 分离

- Domain Event 是领域语言和本地进程内协作能力。
- 可靠 Event 是持久化、重试、补偿、归档等运行时能力。
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

### 8.1 基础能力不开放普通开关

以下属于官方基础组合，不应继续以普通用户布尔开关呈现：

- Aggregate Factory。
- UUIDv7 默认策略。
- 强类型 ID 所需能力。
- 基础同步 Command / Query 路由。
- 默认项目选择的 JPA Repository / Unit of Work。

用户通过依赖选择能力，通过少量策略配置调整行为；不能通过任意开关制造框架不支持的半装配状态。

### 8.2 配置审计规则

实施计划必须逐项盘点现有配置，并按下列类型处理：

| 配置类型 | 处理 |
| --- | --- |
| 与依赖存在性重复 | 删除，由 starter 是否存在决定 |
| 官方基础能力开关 | 删除或固定为推荐默认 |
| 高级能力启用开关 | 优先改为引入对应 starter |
| 真正策略选择 | 保留，但缩小取值和说明 |
| 历史实现遗留 | 删除 |
| 实验性生成能力 | 移出默认核心，进入 addon |

本轮不接受“为了灵活”而保留没有明确用户场景的配置。

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
2. 定义 BOM、最小基础入口和各独立 starter 的最终 artifact 映射。
3. 拆除 `ddd-domain-repo-jpa -> ddd-domain-event-jpa` 及同类错误实现依赖。
4. 将 Request、Event、Locker、Saga、Snowflake 和 Integration Event/Transport 隔离。
5. 确保同步 Command / Query 与本地 Domain Event 在无框架表环境运行。
6. 为缺少可靠实现的高级 API 建立明确失败语义。
7. 删除 `event-scan-package` 和事件 classpath 扫描。
8. 实现真实 `@EventListener` 驱动的 `IntegrationEventSubscriptionRegistry`。
9. 瘦身运行时配置和 generator 配置。
10. 将 Unique 自动生成迁移到 B 类 addon。
11. 删除当前 Aggregate Specification 实现。
12. 删除 `Reentrant`。
13. 调整多模块 Bootstrap 的默认依赖，确认不包含示例业务、框架 SQL和 Skill。
14. 按新模块边界重组测试 fixture 和验证命令。

在进入代码修改前，必须先形成独立实施计划；本文不规定文件移动顺序和提交拆分。

## 15. 当前源码事实索引

以下路径用于帮助后续实施者快速恢复现状，不代表当前实现是目标设计：

- [当前全能力聚合 starter](../../../cap4k-ddd-starter/build.gradle.kts)
  - API 暴露 core、Snowflake、JDBC Locker 和 JPA Repository。
  - implementation 引入 Request JPA、Saga JPA 和 Event JPA。
- [当前 JPA Repository 模块](../../../ddd-domain-repo-jpa/build.gradle.kts)
  - 当前以 implementation 依赖 Event JPA，是已确认的错误耦合。
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

- 最小基础 starter 的最终 artifact 名称。
- 现有模块是改名、拆分还是新建后删除旧模块。
- AutoConfiguration 类的最终归属。
- Registry 的 Spring 生命周期和并发数据结构。
- 缺失能力时使用启动校验还是调用时异常。
- 测试 fixture 如何按 starter 隔离。
- 各可选 starter 的 SQL 文件位置和文档格式。

这些实现选择必须同时满足：

- 默认项目保持最小同步。
- 不恢复全能力聚合入口。
- 不恢复事件包扫描。
- 不让 Request/Event 固定依赖 JDBC Locker。
- 不让 JPA Repository 固定依赖 Event JPA。
- 不让 Bootstrap 管理 Skill。
- 不把数据库自动迁移带回本轮。

## 17. 验收标准

设计实施完成后必须满足：

1. Bootstrap 生成的默认多模块项目不含示例业务。
2. 默认项目只使用最小同步、MVC、JPA、UUIDv7/Strong ID、Factory 和本地 Domain Event 所需能力。
3. 默认项目无需任何 cap4k 框架表即可启动和执行基础同步用例。
4. BOM 可以统一所有 cap4k 组件版本。
5. 没有公共全能力 starter 把 Saga、Snowflake、Request/Event 可靠实现和 Transport 一次性传递给用户。
6. JPA Repository 不依赖 Event JPA。
7. Request/Event/Saga 只依赖 Locker 抽象，不固定依赖 JDBC Locker starter。
8. 缺少可靠实现时，高级 API 不会静默丢失语义。
9. `event-scan-package`、事件 classpath 扫描及其模板配置全部消失。
10. Transport 只为真实 `@EventListener` Integration Event Handler 建立消费者。
11. Unique 自动生成不再属于默认核心。
12. 当前 Aggregate Specification 实现和 `Reentrant` 完整删除。
13. Bootstrap 不读取、生成、复制、覆盖或删除 Skill。
14. 本轮没有引入框架表自动迁移、单模块初始化或公开项目模板。

## 18. 上下文恢复摘要

如果后续会话发生上下文压缩，只需恢复以下事实：

- 先稳定官方项目契约，再做传播入口。
- 官方默认项目是无示例业务的多模块最小同步项目。
- 默认能力不需要 cap4k 框架表。
- BOM 管版本，Bootstrap 给默认组合，starter 给独立能力。
- 不保留全能力聚合入口。
- JPA Repository 与 Event JPA 解耦。
- Request/Event/Saga 依赖 Locker 抽象，不依赖 JDBC 实现。
- Integration Event、Saga、Snowflake、可靠 Request/Event 和 Transport 全部 opt-in。
- 删除 `event-scan-package` 和 classpath 扫描，改用真实 `@EventListener` Registry。
- Unique 自动生成迁移到 B 类 addon。
- 删除当前 Aggregate Specification 和 `Reentrant`。
- 框架表自动迁移后置。
- Bootstrap 不管理 Skill；用户需要时自行从 GitHub 复制。
- 单模块、GitHub Template、IDEA/Initializr 和 Saga 重建全部后置。
