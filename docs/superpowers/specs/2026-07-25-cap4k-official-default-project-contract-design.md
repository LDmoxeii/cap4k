# Cap4k 官方默认项目契约设计

**日期：** 2026-07-25
**最近修订：** 2026-07-28
**状态：** 已批准，等待二次书面审阅
**实施顺序：** Runtime -> Generator -> GitHub Template
**二次修订源码基线：** `master@e8cc1247`（包含 PR #136、#138、#140；PR #141 只迁移 Comet/workspace 治理，不改变本文产品合同）

## 1. 文档权威与冷启动契约

本文是“cap4k 官方默认项目契约”工作的唯一规范来源。

下列材料不具有规范效力：

- 历史会话。
- `2026-07-25-cap4k-official-default-project-and-runtime-boundaries-design.md`。
- 当前实现中由历史耦合形成的模块和配置。
- 未在本文中明确写出的行业惯例或所谓常识。

旧文档只能用于定位源码事实，不能用于补充、解释或覆盖本文。实施计划不得要求读者先阅读旧文档或历史会话，也不得使用“按之前讨论”“保持现状”“后续再定”等需要外部上下文才能理解的表述。

二次修订基线中已经合并 Strong ID 创建时分配、软删除 ID/storage 语义、数据库托管 identity/version 和 owned relation 调整。它们各自的历史 spec 不作为本文的前置阅读材料；凡本轮 Runtime 或 Generator 改造可能触及的约束，本文必须完整重述。超出本文改造范围的既有能力由当前生产源码和回归测试保护，本轮不得借 starter 拆分或 Factory 默认值重构重新设计。

一个完全没有参与设计讨论的新会话，必须能够仅凭本文和当时的 cap4k 源码完成以下工作：

1. 确定目标产品行为。
2. 确定要新增、迁移和删除的模块、API、配置及测试。
3. 为当前阶段编写实施计划。
4. 判断哪些内容明确不在当前阶段中。
5. 选择验证命令并判断阶段是否完成。

分阶段只拆分工作量，不拆分产品答案。任何阶段都不得引入不属于最终目标的临时 starter、别名、配置或双轨实现。

若实施时发现本文不足以决定一个产品行为，实施会话必须停止该项工作，先修订本文并重新获得审阅；不得自行填补产品决策。

## 2. 问题定义

cap4k 需要低门槛的项目初始化体验，但 GitHub Template、Bootstrap、IDEA 向导或 Initializr 都只是分发渠道。若“cap4k 官方默认项目是什么”没有稳定答案，每个渠道都会复制当前实现的偶然结构，并在运行时边界变化后产生漂移。

当前问题不是单纯的配置过多，而是产品边界、模块边界和实现边界没有对齐：

- `cap4k-ddd-starter` 同时聚合 Core、JPA Repository、可靠 Request、可靠 Event、Saga、Snowflake、JDBC Locker 和多种 Integration Event adapter。
- `ddd-domain-repo-jpa` 依赖 `ddd-domain-event-jpa`，选择业务仓储会被动选择可靠事件持久化。
- 同步 Request 与可靠 Request 共享需要 `RequestRecordRepository` 的实现。
- Core 事件实现需要业务包扫描，并依赖可靠事件仓储。
- Bootstrap、生成器 DSL 和运行时配置暴露了大量内部实现选择。

这些事实说明当前默认体验是历史实现的总和，而不是经过产品定义的项目基线。

## 3. 产品目标与成功标准

### 3.1 首要目标

官方默认项目是一个真实项目的干净起点：

- 保留正式的四模块架构。
- 提供 cap4k 的基础同步使用手感。
- 不包含示例业务、Demo Controller、演示数据或需要删除的教程代码。
- 首次构建、测试和启动不要求外部服务或用户先选择配置。

学习型示例和教程由独立参考项目及文档承担，不由默认项目承担。

### 3.2 成功标准

设计完成后的系统必须满足：

1. 新项目下载依赖后可以直接执行构建、启动烟雾测试和 `bootRun`。
2. 默认项目不存在任何 `cap4k.*` 运行时配置。
3. 默认项目不创建任何 cap4k 框架表。
4. 同步 Command/Query、本地 Domain Event、Repository、Factory 和 UoW 不依赖可靠 Request/Event、Saga、Locker、Snowflake 或 transport。
5. 业务代码继续使用 `Mediator.*` 静态 API，不需要注入 cap4k runtime 对象。
6. 每个高级能力由独立 starter 选择，依赖树能真实表达结构性前置与可替换 provider。
7. 生成器在无输入时成功 no-op，并且不会制造空占位文件。
8. 官方 GitHub Template 只依赖正式发布的 cap4k 制品。
9. cap4k 主仓库完整构建通过，且静态搜索没有被删除 API 的残留。

## 4. 范围与非目标

### 4.1 本文范围

本文定义：

- 官方默认项目契约。
- 为实现该契约所需的 Runtime 解耦。
- starter 依赖树和自动装配所有权。
- Mediator 静态 API 与 capability provider 机制。
- 同步请求、Repository、UoW 和本地事件的事务语义。
- 与默认契约直接相关的生成器默认行为。
- Bootstrap 与 GitHub Template 的分发边界。
- Runtime、Generator 和 GitHub Template 三个实施阶段及验收门槛。

### 4.2 明确非目标

本文不重新设计：

- 可靠 Request、可靠 Event 和 Saga 的完整业务语义。
- 各 Integration Event transport 的消息协议。
- 高级能力现有的线程、分区、重试、表名或 transport 调优参数。
- 自动迁移业务表或 cap4k 框架表的体系。
- 通用 ORM SPI 或 JPA 之外的替代 ORM。
- 通用历史生成文件识别与删除协议。
- 新的 Bootstrap 工作流、init 命令、IDEA 向导或 Initializr。
- `Mediator.events` 到其他名称的重命名。
- Strong ID 的策略名称、存储矩阵、JSON/JPA 映射和 generator accessor/catalog 协议。
- 软删除的 ID/storage 支持矩阵、sentinel、SQL dialect 和 Hibernate `SQLDelete`/`Where` 语义。
- 数据库托管 identity/version、structural parentRef、自动 inverse navigation 删除和 owned relation 业务契约。
- 新增 owned-child Factory payload、父 ID/Entity 暴露模式或 managed-field lifecycle SPI。
- 新建 Unique addon、`cap4kAddon` 选择面或 ServiceLoader 集成；本轮只删除 aggregate generator 主线中的 Unique 生成功能。

高级能力和上述已合并 Generator/Runtime 能力的既有业务语义在模块边界迁移时保持不变；只有本文明确否定的耦合、启停方式、依赖关系和默认生成表面需要改变。

## 5. 官方默认项目契约

### 5.1 项目形态

默认项目固定为四个无前缀模块：

```text
domain/
application/
adapter/
start/
build.gradle.kts
settings.gradle.kts
gradle/libs.versions.toml
gradlew
gradlew.bat
```

项目不包含示例聚合、Command、Query、Controller、事件、业务表或演示数据。

`settings.gradle.kts` 不设置 `rootProject.name`。Gradle 根项目名由本地目录名决定。默认包名是 `com.example.demo`；体验使用者可以直接运行，生产项目由用户一次性重命名目录和包名。本文不提供 init 或自动重命名工具。

### 5.2 项目依赖方向

项目依赖必须是：

```text
domain
  ^
application
  ^  ^
  |  |
adapter
  ^
start
```

规范化表达为：

- `domain` 不依赖其他业务模块。
- `application -> domain`。
- `adapter -> application + domain`。
- `start -> adapter`。
- 不允许任何反向项目依赖。

`start` 不直接声明 `application` 或 `domain` 项目依赖；它们通过 `adapter` 的结构依赖进入运行时。

### 5.3 默认运行能力

默认项目包含：

- Spring Boot Servlet Web。
- Bean Validation。
- 同步 Mediator Command/Query。
- JPA Repository、查询事务和 UoW 写事务。
- 同线程、事务提交前的本地 Domain Event。
- Aggregate Factory 和 `PersistIntent.CREATE`。
- 凡模型选择 application-side own ID 的生成实体，都生成该实体专属的 Strong ID；database identity 不因此被强制包装为 Strong ID。
- application-side generated-own-ID 的官方默认策略是 UUIDv7；Factory、直接 CREATE 登记、generated owned relation mutation 和最终 UoW backstop 按第 9 节保证缺失 ID 在对应生命周期返回点可用。
- `Mediator.ioc` 对当前 Spring `ApplicationContext` 的静态访问。

默认项目不包含：

- 可靠 Request。
- 可靠 Event 持久化。
- Saga。
- Integration Event transport。
- JDBC Locker。
- Snowflake。
- 任何 cap4k 框架表。
- Flyway 或 Liquibase。

数据库托管 identity/version 是 Generator 和 JPA adapter 支持的可选模型，不替代官方默认项目的 UUIDv7。选择数据库托管字段时，Factory 创建后它们保持 `null`，成功 `save()` 并 flush 后才由 JPA provider 赋值。

### 5.4 主应用和扫描根

`start` 中只有一个位于 `com.example.demo` 根包下的 `@SpringBootApplication`。所有模块的生成代码和手写代码都必须位于该根包下。

Spring Boot 的 `AutoConfigurationPackages` 是 Core/JPA 自动装配发现业务类型的唯一包根。默认项目不提供 `@EnableCap4k`、包名配置或全 classpath 扫描。

### 5.5 默认配置和数据库

默认项目不设置 `spring.application.name`，也不包含任何 `cap4k.*` 运行时配置。

`application.yml` 只设置：

```yaml
spring:
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
```

H2 作为 `start` 的 runtime dependency，只服务首次启动和测试。默认项目不显式设置 H2 URL、用户、驱动或方言，也不把 H2 通过测试当成生产数据库兼容性证明。

空项目没有实体和业务表，所以 `ddl-auto=validate` 可以启动。生成实体后，用户必须自行提供业务表。cap4k 不创建、更新或迁移业务 schema。

### 5.6 默认依赖所有权

依赖跟随直接使用该类型的源码模块：

| 模块 | 项目依赖 | 直接第三方或 cap4k API 依赖 | 禁止项 |
|---|---|---|---|
| `domain` | 无 | `ddd-core`、生成源码直接使用的 JPA API、Jackson 和 Spring Context API | Spring Boot starter |
| `application` | `domain` | `ddd-core`、Jakarta Validation API、生成源码直接使用的 Spring API | JPA runtime、Spring Boot starter |
| `adapter` | `application`、`domain` | `ddd-core`、`ddd-domain-repo-jpa`、源码直接使用的 Spring Data/JPA API | Spring Boot starter |
| `start` | `adapter` | `cap4k-ddd-jpa-starter`、Web starter、Validation starter、H2 runtime | 可靠能力 starter |

生成的 Strong ID 和 Value Object 当前允许直接包含 JPA 映射、`AttributeConverter`、Jackson annotation 和 Jackson 转换代码。这延续当前选择的 JPA 默认 adapter；它不表示 JPA 是 cap4k 永久不可替换的核心。

Core/JPA starter 不替业务模块偷偷补齐其源码所需的编译依赖。

### 5.7 Gradle 构建

Gradle Version Catalog 是插件和依赖版本的唯一来源。本轮不增加 cap4k BOM。

根构建保持轻量。每个模块显式声明自己的插件和依赖，不使用：

- `subprojects {}`。
- `allprojects {}` 统一依赖注入。
- `buildSrc`。
- convention plugin。

默认仓库包含 Gradle Wrapper。

### 5.8 默认测试

`start` 包含一个可长期保留的 `@SpringBootTest` 启动烟雾测试。测试不创建示例业务，只证明默认依赖、H2、自动装配和空 Spring Context 可以启动。

## 6. Runtime 模块边界

### 6.1 `ddd-core`

`ddd-core` 只包含：

- 持久化中立的公共契约。
- Mediator 静态门面和 capability contract。
- 同步 Command/Query、Domain Service 等基础实现。
- 本地 Domain Event 所需的领域契约。
- 可选可靠能力所依赖的抽象接口。
- `IdentifierStrategy`、`IdentifierGenerator`、`GeneratedOwnIdAccessor`、`GeneratedOwnIdCatalog` 和 `GeneratedOwnIdRegistry` 等持久化中立 ID 契约。

`ddd-core` 不得出现：

- JPA、Hibernate 或 Spring Data 类型。
- `RequestRecordRepository`、`EventRecordRepository` 或 `SagaRecordRepository` 的具体实现。
- JDBC Locker、Snowflake 或消息 transport 实现。
- 通过 compile-only adapter 探测并集中装配高级能力的逻辑。

Core 中需要兼容 Spring Web 的代码只能使用 `compileOnly`，并由条件自动装配保护。`cap4k-ddd-core-starter` 不传递 Web starter。

`cap4k-ddd-core-starter` 拥有默认 UUIDv7 strategy、`IdentifierStrategyRegistry`、`IdentifierGenerator` 和 `GeneratedOwnIdRegistry` 聚合装配。它不得编译依赖或探测 Snowflake 实现；Snowflake starter 只能以附加 `IdentifierStrategy` 的方式扩展同一 registry。

`GeneratedOwnIdAccessor`、catalog 和 registry 是 generated-own-ID 的唯一发现与分配协议。generated owning relation 可以在 mutation 前直接调用其 child typed accessor，但 `OwnedEntityList` 本身保持基础设施中立，不注入或定位 Mediator、UoW、Spring 或 JPA 服务。

### 6.2 `ddd-domain-repo-jpa`

`ddd-domain-repo-jpa` 只实现：

- JPA Repository 支持。
- JPA Unit of Work。
- 查询只读事务。
- 写事务和本地事件提交时序的 JPA 集成。
- Aggregate Factory 与 JPA UoW 的连接。
- 使用 Core `GeneratedOwnIdRegistry` 在 CREATE、EXISTING 和保存前协调根聚合图中新可达实体的应用侧 ID 补齐。
- 在能够由 pending root 证明 child 归属时，把 separately pending child 吸收到最外层 root entry，并拒绝无关 root 共享同一 child。
- 依靠现有 JPA persist/cascade/flush 和必要的 root refresh 使数据库托管 identity/version 可观察；不得为此新增通用 managed-field lifecycle SPI。

必须删除 `ddd-domain-repo-jpa -> ddd-domain-event-jpa` 依赖。选择业务 Repository 不得被动选择可靠事件持久化。

### 6.3 实现模块和 starter 模块

实现模块承载能力实现，starter 模块承载 Spring Boot 自动装配和依赖选择。高级能力不得只靠“实现 jar 在 classpath”自行装配。

目标 starter 如下：

| Starter | 提供能力 | 传递的结构性 starter | 必须由用户显式提供 |
|---|---|---|---|
| `cap4k-ddd-core-starter` | 同步 Command/Query、本地事件、基础 capability、IoC、UUIDv7 ID policy 和 generated-own-ID registry | 无 | 无 |
| `cap4k-ddd-jpa-starter` | Repository、UoW、Factory、JPA 事务、根聚合图 ID 生命周期和 provider-assigned 字段观察 | Core | 无 |
| `cap4k-ddd-request-jpa-starter` | 可靠 Request JPA 实现 | JPA | `Locker` |
| `cap4k-ddd-domain-event-jpa-starter` | DDD 领域层可靠事件存储 | JPA | `Locker` |
| `cap4k-ddd-saga-jpa-starter` | Saga JPA 实现 | JPA | `Locker` |
| `cap4k-ddd-locker-jdbc-starter` | JDBC `Locker` provider | Core | 对应框架表 |
| `cap4k-ddd-snowflake-starter` | Snowflake generator 和附加 `IdentifierStrategy` | Core | 必要节点配置 |
| `cap4k-ddd-integration-event-http-starter` | HTTP transport | Core | `EventRecordRepository` |
| `cap4k-ddd-integration-event-http-jpa-starter` | HTTP 订阅注册表 JPA 实现 | HTTP + JPA | `EventRecordRepository` |
| `cap4k-ddd-integration-event-rabbitmq-starter` | RabbitMQ transport | Core | `EventRecordRepository` |
| `cap4k-ddd-integration-event-rocketmq-starter` | RocketMQ transport | Core | `EventRecordRepository` |

`domain` 在 `domain-event-jpa` 中表示 DDD 领域层，不表示只支持 Domain Event。`ddd-domain-event-jpa` 和对应 starter 保持该名称；它提供的可靠事件存储也供 Integration Event 使用。

### 6.4 starter 依赖规则

starter 只传递以下两类前置：

1. 没有实现分支的结构性前置，例如 JPA starter 到 Core starter。
2. starter 名称已经明确选择的实现，例如 `-jpa` starter 到 JPA starter。

存在当前或未来实现分支的 provider 必须由用户显式选择：

- Request/Event/Saga JPA starter 不传递 JDBC Locker starter。
- Integration Event transport starter 不传递 Domain Event JPA starter。
- HTTP JPA starter 可以传递 HTTP 和 JPA starter，因为名称已经明确选择这两个结构实现。

缺少显式 provider 时不得选择一个“当前只有这一种实现”的默认值。

Core starter 到 Snowflake 实现的依赖属于禁止边。Snowflake starter 可以依赖 Core starter 并贡献 strategy bean；移除 Snowflake starter 后，Core/JPA 默认组合仍必须完整提供 UUIDv7、ID registry 和 Factory/UoW 能力。

### 6.5 启用和失败时机

显式加入 starter 依赖即启用该能力，不提供 runtime capability 的 `enabled=true` 开关。

- 未安装的可选能力不阻止默认项目启动。
- 实际调用未安装能力时抛出明确的 capability unavailable 异常。
- 已安装 starter 缺少其必需 provider、配置或框架表时启动失败。
- 可靠调用不得自动降级为同步、本地或非持久化调用。

### 6.6 删除聚合 starter

删除 `cap4k-ddd-starter` 模块、发布物和 `AutoConfiguration.imports`。不保留：

- alias starter。
- deprecated starter。
- 兼容聚合 starter。
- 同时探测多个 Integration Event adapter 的 compile-only 自动装配中心。

原聚合 starter 中的自动装配必须迁回对应 capability starter。仓库内部项目、fixture 和测试直接迁移到新 starter。

## 7. Mediator 静态 API 与 capability provider

### 7.1 用户 API 手感

业务代码继续使用静态 API，不要求构造器注入 cap4k runtime 对象：

```kotlin
Mediator.commands.send(command)
Mediator.queries.send(query)
Mediator.repositories.find(...)
Mediator.factories.create(payload)
Mediator.uow.save()
Mediator.events.attach(...)
Mediator.ioc.getBean(...)
```

规范命名只保留：

- `commands`。
- `queries`。
- `requests`。
- `repositories`。
- `factories`。
- `services`。
- `uow`。
- `events`。
- `ioc`。

删除 `cmd`、`qry`、`req`、`repo`、`fac`、`svc` 和整个 `X` 门面。`Mediator.uow` 保持正式名称。`Mediator.events` 本轮继续表示 Integration Event，不在本文中重命名。

聚合上的 `events().attach(entity) { event }` 属于 Domain Event 附着 API，不等同于 `Mediator.events`。

### 7.2 目标内部形态

`Mediator` 是纯静态 capability namespace，不再是继承全部 Supervisor 的巨型接口。

删除：

- `Mediator.instance`。
- `DefaultMediator`。
- 统一实例委托链。
- 要求一个对象同时实现所有 runtime 能力的设计。

每组 API 由独立 capability provider 支撑。Core、JPA 和高级 starter 只注册自己拥有的 provider。缺失一个可选 provider 不影响其他组 API。

### 7.3 Spring 装配和用户替换

每个默认 provider 独立使用 `@ConditionalOnMissingBean`。应用可以替换某一项 capability，而不需要替换整个 Mediator。

provider 装配必须满足：

- 恰好一个可选实现时连接静态 facade。
- 多个无法唯一选择的实现时启动失败，并报告冲突 capability 和 bean。
- Core/JPA 基线 provider 在 Context 启动完成前连接。
- 可选能力未安装时 facade 保持未配置状态，直到实际调用才报告 unavailable。

错误消息必须指出缺失的 capability；存在官方 starter 时应指出可选择的 starter 名称，但不得自动添加或选择依赖。

### 7.4 `Mediator.ioc`

`Mediator.ioc` 是正式 API，由 Core starter 连接当前 Spring `ApplicationContext`。它支持在不使用构造器注入的业务位置静态获取 Bean。

只有 IoC capability 暴露 Spring Context；其他 provider 不得为了实现静态 facade 而统一依赖 `ApplicationContext`。

## 8. 同步请求和 Bean Validation

### 8.1 默认同步路径

`Mediator.commands.send(...)` 和 `Mediator.queries.send(...)` 在当前线程同步执行。默认同步路径：

- 不创建 RequestRecord。
- 不依赖 `RequestRecordRepository`。
- 不依赖 Locker、scheduler 或 Request 框架表。
- 不因为可靠 Request starter 缺失而影响启动。

Core 中必须拆开同步 send 与持久化调度。`commands.async(...)`、延迟请求和明确的可靠调用路由到可选可靠 Request provider；provider 缺失时在调用处明确失败。

### 8.2 校验依赖和时序

官方默认项目提供完整 Bean Validation：

- `application` 直接声明 Jakarta Validation API 编译依赖。
- `start` 显式声明 `spring-boot-starter-validation` 运行时实现。
- Core starter 不传递具体 Validation provider。

Validator 在 Handler 之前执行。约束异常、Handler 业务异常和程序错误保持原始语义向上传播，不统一包装为其他异常。

## 9. Repository、Factory 和 UoW

### 9.1 Repository 查询事务

Mediator 请求调度本身不自动创建写事务。Repository 调用具有独立的短只读事务：

1. 打开只读事务。
2. 查询聚合。
3. 默认按照 `AggregateLoadPlan.WHOLE_AGGREGATE` 初始化当前支持的全部 `@OneToMany` 聚合关系。
4. 关闭查询事务。
5. 返回加载完成的聚合。

`AggregateLoadPlan.MINIMAL` 是高级性能选项。选择它的用户承担未初始化关系和不完整聚合风险。

本文不为当前模型中不存在的 `@OneToOne` 或其他关联形式设计额外协议。

### 9.2 Factory 是必备组件

每个聚合根必须有 Aggregate Factory。通过 `Mediator.factories.create(payload)` 创建聚合后，Factory provider 立即向 UoW 登记 `PersistIntent.CREATE`。

创建过程不提前打开数据库写事务。Factory、ID capability 和 UoW 的责任是：

- Factory 只根据业务输入构造聚合根及当前 Generator 已支持的 owned graph，不直接分配 ID、不 flush，也不访问 `EntityManager`。
- Factory provider 对返回的根聚合执行 `persist(root, PersistIntent.CREATE)`；这是生命周期登记，不是写事务。
- Factory provider 和调用方直接执行 `persist(root, PersistIntent.CREATE)` 时，JPA UoW 使用 Core `GeneratedOwnIdRegistry` 与生成的 typed accessor/catalog 补齐 root 和当前可达 owned graph 的缺失应用侧 own ID；登记调用返回时即可读取。
- generated owning relation 的 `add(child)` 和非空 `replace(child)` 在修改 relation 前直接调用 child typed accessor；失败时 relation 保持不变，`replace(null)` 不分配 ID，mutation 返回时 child ID 可读取。
- 对 `EXISTING` root，UoW 保留 repository observation 或 provider-managed evidence，保护既有 root/child identity，只为新可达且缺失 ID 的 owned child 分配，并在最终 save 前再次进行幂等补齐。
- 已由高级调用方预先赋值的应用侧 ID 保持不变；不得使用反射、Strong ID companion 查找或 JPA annotation 扫描作为 fallback。
- 若 separately pending child 可由 pending root 的 forward owned graph 证明归属，save 前必须吸收到最外层 root entry；child 不独立进入 interceptor、listener 或 JPA persist/merge 集合。同一 child 属于两个无关 root 时确定性失败，reachable child 的直接 remove 仍失败。
- 没有 pending owner 的孤立 caller-declared `persist(child, CREATE)` 继续按 caller-declared top-level entry 处理；本文不新增静态 root/child marker 或公共 child-persist API 来猜测其归属。
- owned child 由根图拥有，不创建独立公共 child Factory；在归属可证明时不保留独立最终 UoW entry。
- 数据库托管 identity/version 不由 Factory 或 ID capability 合成；创建后保持 `null`。
- UoW 记录 `CREATE`、`EXISTING` 和 remove intent。
- `Mediator.uow.save()` 执行最终写事务。

### 9.3 唯一写事务边界

`Mediator.uow.save()` 是默认业务模型唯一真实写事务边界。Repository 查询、Command 调度和 Factory 创建都不替代该边界。

一次 `save()` 必须原子处理已登记的聚合变更和事务内本地事件。写入或事件 Handler 失败时事务回滚。

JPA provider-assigned 字段满足以下观察契约：

- `create(...)` 返回后，数据库 identity/version 仍为 `null`。
- 成功 `save()` 必须 persist 或 merge 根图并 flush，使根、初始 owned child 和既有 root 新增的 owned child 的数据库 identity/version 可观察为非空。
- 当 owned child 的归属可由 pending root 证明时，最终 UoW entry 保持最外层根聚合；child 依靠 forward cascade 持久化，不独立调用 `EntityManager.persist`。
- 若 provider 已赋值后事务回滚，cap4k 不清空实体内存中的 identity/version；调用方必须丢弃或重新加载回滚后的实例。
- 上述行为依赖现有 JPA provider 生命周期，不把普通 managed `READ_ONLY` 字段提升为 identity/version，也不建立新的通用字段观察 SPI。

## 10. Domain Event 与 Integration Event

### 10.1 默认本地事件

默认 Domain Event 流程是：

```text
aggregate.events().attach { event }
-> UoW save
-> 写事务内、提交前
-> Spring ApplicationEventPublisher
-> 同线程 @EventListener
-> 提交事务
```

默认本地事件：

- 不创建 EventRecord。
- 不依赖 EventRecordRepository。
- 不依赖 Locker 或调度器。
- Handler 失败会向上传播并回滚事务。

本地事件 Handler 不应执行不可回滚的外部 I/O。需要可靠跨边界交付的行为应使用可靠 Event 或 Integration Event。

### 10.2 订阅发现

业务订阅者继续使用 `@Service` 和 `@EventListener`。保留 cap4k 自定义 EventListenerFactory 的类型诊断。

删除：

- `event-scan-package`。
- 业务包 classpath 事件扫描。
- 独立事件扫描配置。

Spring Boot 主应用包是唯一组件扫描根。

### 10.3 可靠事件路由

以下事件必须路由到可靠 Event provider：

- `@DomainEvent(persist = true)`。
- 延迟、定时或其他明确要求持久化的事件。
- Integration Event 的可靠发送。

未安装可靠 Event starter 时，在实际释放这些事件时明确失败。不得降级为本地 Domain Event。

### 10.4 Integration Event

默认项目不注册 `Mediator.events` 的 Integration Event provider。没有业务调用时项目正常启动；实际调用时报告 capability unavailable。

引入 transport starter 后，缺少 `EventRecordRepository` 是安装错误，必须在启动时失败。transport starter 不替用户选择 Domain Event JPA 存储。

## 11. 配置所有权和框架表

### 11.1 能力选择

配置遵循“依赖选择能力，配置只提供参数”的规则。

Core/JPA 默认组合不要求任何 `cap4k.*` 配置。高级 starter 被加入依赖后自动启用，不再保留 Request、Event、Saga、Locker、Snowflake 或 transport 的 `enabled=true` 开关。

能力所需的连接信息、节点标识和调优参数仍由对应 starter 自己拥有。缺少必填值时启动失败。

### 11.2 本文要求删除的配置

必须删除：

- `event-scan-package` 及相关扫描配置。
- runtime capability 的启停开关。
- `generators.aggregate.artifacts.factory`。
- `generators.aggregate.artifacts.specification`。
- `generators.aggregate.artifacts.unique`。
- 内置官方 Bootstrap preset 的模板资源和默认项目选择。

### 11.3 本文要求保留的配置

`bootstrap.enabled`、slots、managed block 和冲突配置属于保留的 Bootstrap API，但不得进入官方 GitHub Template。

高级能力的其他配置名称、默认值和调优语义不在本文的重新设计范围内。实施不得借“配置瘦身”顺便删除尚未逐项确认的线程、分区、重试、表名或 transport 参数。

### 11.4 框架表

cap4k 本轮不提供自动框架表迁移体系：

- 默认项目没有 Request/Event/Saga/Locker 等框架表。
- 用户显式选择高级能力后，自行管理对应 DDL。
- starter 必须通过对应持久化实现的启动期 schema 校验确认必需表可用，但不得自动创建或更新表。

## 12. 生成器默认契约

### 12.1 官方模板中的插件配置

官方模板在根构建中预应用 cap4k Gradle plugin，只配置稳定项目坐标：

```kotlin
cap4k {
    project {
        basePackage.set("com.example.demo")
        domainModulePath.set("domain")
        applicationModulePath.set("application")
        adapterModulePath.set("adapter")
    }
}
```

模板不配置 Bootstrap，不放置空 schema、空 design JSON 或占位 manifest。

### 12.2 无输入行为

用户尚未提供生成输入时：

- `cap4kGenerateSources` 成功 no-op。
- 编译任务仍依赖 `cap4kGenerateSources`。
- 不创建空生成目录或伪产物。
- `cap4kPlan` 和 `cap4kGenerate` 保持可显式调用。

缺少可选输入不是错误；存在已配置但无效的输入仍应按照现有输入校验规则失败。

### 12.3 产物所有权

可重复生成且无需用户补充业务逻辑的源码进入 `build/generated`：

- 参与对应模块编译。
- 不提交 Git。
- 可以由下一次生成完整重建。

需要用户补充业务逻辑的骨架：

- 先由 `cap4kPlan` 展示。
- 再由 `cap4kGenerate` 写入受控源码目录。
- 受现有冲突策略和 ownership 规则保护。
- 不由 compile task 静默写入手写源码目录。

### 12.4 Aggregate Factory

Aggregate Factory 是每个聚合根的必备产物。删除 `artifacts.factory` 开关；只要模型中存在聚合根，planner 就必须计划 Factory。

Factory payload 只包含用户创建时允许写入的业务输入。下列字段不是 payload，也不得被当成缺失构造参数：

- 应用侧 generated own ID。
- 数据库托管 identity。
- 数据库托管 version。
- soft-delete 等具有已定义 initializer 的 `SYSTEM_TRANSITION_ONLY` 字段。
- structural parentRef 和已删除的 automatic inverse navigation。
- 具有合法生成构造默认值的字段。

这些字段不能被统一归类为一个模糊的“框架管理字段”。Generator 必须保持以下角色差异：

| 角色 | Factory payload / constructor | Entity property | Schema / 物理模型 | Runtime 生命周期 |
|---|---|---|---|---|
| application-side generated own ID | 排除 own ID 输入 | 实体专属 Strong ID，保持既有 generated-own-ID shape | backing 服从 JDBC storage evidence | typed accessor 在 lifecycle entry 分配，UoW 幂等补齐 |
| resolved database identity | 排除 | nullable，初始 `null` | 保留 DB 物理类型和 nullability | JPA persist/flush 后赋值并可观察 |
| resolved version | 排除 | nullable，初始 `null` | 保留 DB 物理类型和 nullability | JPA provider 在 save/flush 生命周期赋值 |
| soft-delete `SYSTEM_TRANSITION_ONLY` | 排除 | mapped property 使用已批准 active initializer | 保留 deleted column | Hibernate soft-delete transition 使用已批准 sentinel/SQL |
| structural parentRef | 不作为 domain/Factory scalar | 不生成默认 scalar 或 automatic inverse navigation | 保留 relation binding/cardinality 的物理证据 | parent-side forward owned relation 和 cascade 使用 |
| ordinary managed `READ_ONLY` | 维持当前 constructor 规则；无法推导时仍是 blocker | 不自动变成 nullable/null | 维持当前物理模型 | 不获得 identity/version lifecycle，等待独立设计 |

provider-assigned 分类只由 resolved database-side own-ID role 和 resolved version policy 决定；`READ_ONLY` 本身不构成分类。Entity 的 Kotlin nullable construction shape 不得改变 Schema/DB 的物理 nullability。

构造映射必须比较 payload 与生成实体的实际构造要求，而不是把 canonical model 的全部字段都视为构造参数。对于已经支持的 application-side ID、数据库 identity/version、soft-delete system-transition field 和 structural parentRef 组合，只要没有独立的业务字段或 managed-field blocker，就必须生成真实聚合构造调用，不允许退回 `TODO`。

只有在业务构造输入或尚未设计 lifecycle 的 managed field 确实无法安全推导时，才生成可编译的 `TODO("Implement aggregate construction")`。此时：

- plan 必须列出未解析字段并把该 Factory 标记为必须手写。
- 受控骨架继续遵守冲突策略和 ownership 规则。
- 不允许通过反射、无参构造、占位 identity/version 或字段赋值绕过聚合不变量。

Generator 必须继续生成 application-side own ID 所需的 typed accessor 和 module catalog。Factory 负责构造，UoW 生命周期负责调用 registry 分配 ID；Factory template 不直接调用 ID generator。

本阶段只统一 Factory 的必生成默认值和 unresolved 处理，不重新设计已合并的 Strong ID、soft-delete、数据库托管字段、parentRef 或 owned relation 语义。

### 12.5 Structural parentRef 与 owned relation

parentRef 的物理关系证据与 domain/API 投影必须分开：

- parentRef 物理列继续作为 direct owned-parent binding 和 cardinality inference 的必需证据，即使数据库没有物理 FK constraint。
- parentRef 不投影为默认 Entity scalar、Schema scalar、Factory payload 或自动 child-to-parent inverse navigation。
- parent-side forward owned relation、Schema join、nested forward join 和 cascade 保留。
- parentRef 等于 child primary key 的 shared-primary-key owned child 必须拒绝。
- owned-one 由 unique parentRef 加独立 child primary key 证明；其他已支持 parentRef 形态按现有 owned-many 规则处理。
- 删除 domain scalar 或 inverse API 不得删除 relation binding、cardinality inference、physical unique constraint 或 forward join 所需的 metadata。

新增 parent ID/entity 访问模式、owned-child Factory payload 或 public child Factory 仍是非目标。

### 12.6 删除 Aggregate Specification

删除当前“UoW 保存前自动校验”Specification 的全部表面：

- `ddd-core` API、Manager 和 Interceptor。
- JPA 自动装配。
- generator planner、template 和 option。
- renderer、fixture 和测试。
- 文档、skill 和 capability map 中的承诺。

该删除只否定当前实现，不禁止未来以新设计引入其他 Specification 概念。

只清理 cap4k 仓库中的模板、测试和受控 fixture。本文不建设通用历史生成文件删除机制。

### 12.7 删除 Unique 生成主线

完整删除 aggregate generator 主线中根据数据库唯一约束生成 Query、Handler 和 Validator 的能力：

- 删除 `generators.aggregate.artifacts.unique` 配置表面。
- 删除 `AggregateArtifactSelection.uniqueEnabled`、`AggregateUniqueConstraintPlanning`、Unique Query/Handler/Validator planner 和 renderer template。
- 删除相关 fixture、functional/compile test、public docs、skill 和 capability map 承诺。
- 不创建替代 addon artifact，不引入 `cap4kAddon`、ServiceLoader 或新的依赖选择面。

删除生成能力不删除 DB schema/canonical model 中的 physical unique constraint metadata。数据库唯一约束继续承担最终存储完整性，owned relation cardinality inference 继续使用 unique parentRef 等已批准物理证据。

若未来重新提供 Unique 生成能力，必须作为独立 change 重新定义输入、作用域、异常传播、parent-scoped/global 语义和产物所有权；本文不预先给出该答案。

## 13. 删除 `Reentrant`

完整删除当前声明式锁实现：

- `Reentrant` annotation。
- `ReentrantAspect`。
- JDBC Locker 自动装配中的 Aspect bean。
- 对应测试、文档和配置引用。

当前实现不提供真实的重入语义：`value=true` 直接跳过加锁，每次加锁使用新随机密码，默认 key 不区分业务参数，失败返回 `null`，并包装原始异常。

JDBC Locker starter 只提供 `Locker` provider。未来如需面向业务方法的声明式锁，必须作为独立能力重新设计。

## 14. Bootstrap 边界

Bootstrap 不再是官方项目初始化路径，但不删除整个 Bootstrap 子系统。

保留：

- pipeline bootstrap 模块。
- `cap4kBootstrapPlan` 和 `cap4kBootstrap` 任务 API。
- slots API。
- managed block 标记。
- 冲突处理 API。
- 用户自定义模板能力。

删除内置“官方默认项目”模板副本，避免 cap4k 主仓库和 GitHub Template 仓库形成两个真相来源。

本文不重新设计 Bootstrap 工作流。官方 GitHub Template 不包含 `bootstrap.enabled=true`、slots 或 Bootstrap 模板配置。

## 15. 官方 GitHub Template

### 15.1 仓库职责

建立独立官方 GitHub Template 仓库。它是官方默认项目文件的唯一模板来源。

cap4k 主仓库不得保存 GitHub Template 的镜像副本，也不得通过以下方式建立运行时耦合：

- composite build。
- Git submodule。
- snapshot dependency。
- 本地 Maven 仓库。
- cap4k 源码路径。
- 跨仓库原子提交或文件同步脚本。

### 15.2 用户流程

```text
Use this template
-> 创建仓库或下载
-> 可选：重命名本地目录和 com.example.demo
-> ./gradlew build
-> ./gradlew :start:bootRun
```

不提供 init、交互式向导或自动包名重写。

### 15.3 仓库内容

Template 仓库包含：

- Gradle Wrapper。
- 四个固定模块及显式构建文件。
- `gradle/libs.versions.toml`。
- 根 cap4k generator 配置。
- `start` 模块的 `@SpringBootApplication`。
- 只含 JPA 两项配置的 `application.yml`。
- 一个 `@SpringBootTest` 启动烟雾测试。
- `.gitignore`。
- 只记录重命名、构建、启动和生成入口的简洁 README。
- 无需 Secrets 的 GitHub Actions 构建工作流，执行 `./gradlew build`。

GitHub Actions workflow 会被复制到用户项目，作为真实项目的基础构建检查，不属于演示内容。

### 15.4 版本策略

Template 只消费正式发布的 cap4k runtime、starter、plugin 和 addon。

本文编写时的兼容基线是：

- JDK 17。
- Kotlin 2.2.20。
- Spring Boot 3.5.6。

Template 阶段可以随已经验证的新 cap4k release 原子升级这些版本，但必须满足：

- 所有具体版本固定在 Version Catalog。
- 不使用 `latest`、动态版本或模块内分散版本。
- 升级后的完整 Template CI 通过。
- cap4k 版本已经存在于正式制品仓库。

### 15.5 跨仓库发布流程

1. cap4k 完成 Runtime 和 Generator 阶段。
2. cap4k 发布一组版本一致的 runtime、starter、plugin 和 addon 正式制品。
3. Template 仓库在独立 PR 中升级 Version Catalog。
4. Template CI 执行构建、启动烟雾测试和无输入生成 no-op 验证。
5. 验证通过后更新 Template 默认分支。

## 16. 当前实现到目标状态的迁移

### 16.1 Runtime 模块迁移

| 当前状态 | 目标状态 |
|---|---|
| `cap4k-ddd-starter` 聚合全部能力 | 删除，由独立 starter 取代 |
| `ddd-domain-repo-jpa -> ddd-domain-event-jpa` | 删除该依赖 |
| 高级自动装配集中在旧 starter | 迁入对应 capability starter |
| `Mediator` 继承所有 Supervisor | 静态 capability namespace |
| `Mediator.instance` / `DefaultMediator` | 删除 |
| `X` 和短别名 | 删除 |
| 同步 Request 需要 Request repository | 拆为 Core 同步 provider 和可选可靠 provider |
| 默认 Event 实现需要 Event repository | 本地 Core provider 与可靠 Event provider 分离 |
| 旧 `IdPolicyAutoConfiguration` 同时引用 UUIDv7、Snowflake 和 generated-own-ID registry | Core starter 拥有 UUIDv7/registry，Snowflake starter 独立贡献 strategy |
| generated-own-ID 生命周期测试位于旧聚合 starter | Core 保留中立契约测试，JPA starter 保留 CREATE/根图/UoW 集成测试 |
| aggregate generator 主线包含可选 Unique Query/Handler/Validator | 完整删除配置、planner、template、fixture、测试和文档承诺；不创建 addon |
| `event-scan-package` | 删除，使用 Boot 扫描根 |
| Aggregate Specification | 完整删除 |
| `Reentrant` | 完整删除 |

### 16.2 新增 starter 模块

Runtime 阶段必须新增第 6.3 节列出的全部 starter；正式发布在阶段二完成门槛达成后统一进行。starter 自动配置资源必须只导入本 starter 所拥有的配置类。

不得通过把全部自动配置复制到每个 starter 来制造表面拆分。每个 starter 的生产 classpath 和 `AutoConfiguration.imports` 都要有依赖断言测试。

### 16.3 内部消费者迁移

必须搜索并迁移：

- cap4k 控制台和内部测试应用。
- generator renderer fixture 和 functional fixture。
- public docs、examples 和 reference project 文档。
- `.agents`/skills/capability map 中对旧 API 的承诺。
- 构建脚本和发布配置中的旧 artifact 名称。
- 对 `Mediator` 短别名、`X`、Specification 和 Reentrant 的源码引用。
- 旧 starter 下的 Strong ID、soft-delete、数据库 identity/version、owned graph 和 UoW runtime 测试。
- generator 中对 generated-own-ID accessor/catalog、system-transition field、entrusted field 和 structural parentRef 的既有处理。
- generator 主线中的 Unique option、planner、template、fixture、测试和文档/skill 调用方。

不存在外部用户兼容要求。迁移时不得保留已删除 API 的 deprecated 壳。

### 16.4 跨层删除规则

阶段边界不允许留下不可构建仓库。若 Runtime 删除会使 generator template、fixture 或文档引用不存在的 API，相关引用随 Runtime 阶段一起删除或迁移，即使文件位于 generator 模块。

Generator 阶段只负责仍可独立演进的生成器产品契约，例如 Factory 必生成、字段角色保持和 Unique 主线完整删除。

## 17. 分阶段实施

### 17.0 当前实施状态

截至 2026-07-28，本文对应的仓库内阶段已经在当前工作分支实现并通过全仓验证：

- 阶段一 Runtime 边界已完成：静态 Mediator、同步/可靠 Request 分离、本地/可靠 Domain Event 分离、capability-specific starter、旧 `cap4k-ddd-starter`/Specification/Reentrant/事件包扫描删除均已落地。
- 阶段二 Generator 契约已完成：Factory 必生成、无输入 compile/no-op、Aggregate Specification 删除、Unique 主线生成能力完整删除且没有新增 addon、内置官方 Bootstrap preset 删除并保留通用 Bootstrap API 均已落地。
- `.\gradlew.bat check --console=plain` 已通过；Core/JPA runtime classpath 均确认不包含 Snowflake 实现。
- 阶段三 GitHub Template 尚未开始。它仍以阶段二能力发布为正式仓库中的稳定版本为前置条件，不允许用本地工程依赖、snapshot 或未发布坐标提前创建远程模板。

下面保留分阶段范围和门槛，作为回归审查以及阶段三实施的权威边界，而不是待执行的阶段一/阶段二计划。

### 17.1 总体规则

实施顺序固定为：

```text
Runtime -> Generator -> GitHub Template
```

每个阶段：

- 从绿色仓库开始。
- 以绿色仓库结束。
- 有独立实施计划和验收记录。
- 只实现本文定义的最终状态。
- 不依赖当前或历史会话记忆。
- 未通过验收前不得开始下一阶段计划。

### 17.2 阶段一：Runtime 边界

#### 输入

- 本文。
- 阶段开始时的 cap4k Git 状态。
- 当前 runtime、starter、generator fixture 和文档源码。

#### 范围

- Mediator capability provider 重构。
- Core/JPA 及全部高级 starter 拆分。
- ID policy 自动装配拆分：Core 提供 UUIDv7/registry，Snowflake starter 只贡献 Snowflake strategy。
- generated-own-ID CREATE/EXISTING 登记、relation mutation 前分配、根图补齐、pending child reconciliation 和数据库托管字段的 UoW 生命周期保持不变。
- Repository、UoW、Factory 和本地事件事务语义。
- 同步 Request 与可靠 Request 分离。
- 本地 Event 与可靠 Event 分离。
- 删除旧 starter、Specification、Reentrant、事件扫描和错误模块依赖。
- 删除直接引用旧 starter 的内置官方 Bootstrap preset。
- 迁移全部仓库内部消费者、测试和文档。
- 清理直接引用已删除 runtime API 的 generator 表面。

#### 明确不做

- Factory generator 默认值重构。
- 新建或设计 Unique addon。
- GitHub Template 仓库创建。
- 高级可靠能力业务语义重写。
- Strong ID/storage、soft-delete、数据库托管字段、parentRef 或 owned relation 语义重写。

#### 完成门槛

- runtime 模块单元测试通过。
- 每个 starter 的 auto-configuration 测试通过。
- 默认 Core/JPA 测试应用使用 H2 启动。
- Core starter 在没有 Snowflake 实现的 classpath 上提供 UUIDv7、identifier registry/generator 和 generated-own-ID registry。
- 安装 Snowflake starter 后只增加 Snowflake strategy；移除后 UUIDv7 默认路径仍可用。
- Repository 完整聚合加载测试通过。
- UoW 写事务和 Domain Event 回滚测试通过。
- Factory 和直接 `persist(root, CREATE)` 返回后 application-side root/当前可达 owned ID 可用，预赋值 ID 保持不变。
- generated owned relation `add/replace` 在 mutation 返回前完成 child ID，失败不改变 relation；`EXISTING` root 的新 child 由 UoW backstop 补齐。
- 能证明归属的 separately pending child 吸收到最外层 root entry；无关 root 共享 child 失败；孤立 caller-declared child 不通过新增 marker 重新分类。
- 数据库 identity/version 在 create 后为 `null`、成功 save 后可观察，回滚不承诺清空已赋值内存状态。
- Strong ID、soft-delete 和数据库托管 owned graph 的现有 JPA runtime 回归测试迁入新所有者并通过。
- capability 缺失、冲突及高级 starter 安装错误测试通过。
- 旧 API 和 artifact 静态搜索无残留。
- cap4k 完整构建通过。

### 17.3 阶段二：Generator 契约

#### 输入

- 本文。
- 已合并且通过验收的 Runtime 阶段代码。

#### 范围

- Factory 对每个聚合根必生成。
- Factory payload/constructor mapping 保留 generated own ID、数据库 identity/version、system-transition field 和 structural parentRef 的角色分类。
- generated-own-ID typed accessor/catalog 继续生成并接入 Runtime registry。
- 无输入 `cap4kGenerateSources` no-op。
- 生成源码和手写骨架 ownership。
- 删除 aggregate generator 主线中的 Unique 配置、planner、template、fixture、测试和文档承诺。
- 删除核心 Factory/Unique 配置开关。
- 验证 Runtime 阶段删除内置官方 Bootstrap preset 后，slots、managed block 和冲突 API 仍可独立工作。

#### 明确不做

- 改变 Runtime 模块边界、运行时语义或 starter 树。
- 新增或恢复官方 Bootstrap 业务工作流。
- 创建 GitHub Template 仓库。
- 自动清理历史生成文件。
- 重新设计 Strong ID、soft-delete、entrusted field、parent access、owned relation 或 managed-field lifecycle。
- 新增 owned-child Factory payload 或父 ID/Entity 暴露模式。
- 创建或设计 Unique addon、`cap4kAddon` 或 ServiceLoader 集成。

#### 完成门槛

- planner 和 renderer 测试通过。
- Gradle functional 测试通过。
- 无输入 fixture 构建通过且不产生伪产物。
- application-side ID、数据库 identity/version、soft-delete 和 structural parentRef 的已支持组合生成真实 Factory 构造调用并通过编译测试。
- 只有真正 unresolved 的业务或 managed field 进入 TODO 路径，plan 明确列出 blocker，并有编译测试。
- generated-own-ID accessor/catalog 生成与 Runtime registry 集成测试通过。
- Unique option、planner、template、fixture、测试和文档生成承诺没有残留；physical unique metadata 和 owned-one 推导回归仍通过。
- soft-delete 五种 ID/storage 组合的 planner/renderer/compile 证据保持，H2 生命周期和真实 PostgreSQL native UUID evidence 继续通过。
- Bootstrap 通用 API 回归测试通过。
- cap4k 完整构建通过。
- runtime、starter、plugin 和 addon 以一致版本发布到正式仓库。

### 17.4 阶段三：GitHub Template

#### 输入

- 本文。
- 阶段二发布的正式制品坐标和版本。
- 已通过验收的 cap4k Git 状态。

#### 范围

- 创建独立 GitHub Template 仓库。
- 按第 5、12 和 15 节建立文件契约。
- 建立 CI 和发布升级流程。

#### 明确不做

- 重构 Runtime 或 Generator 的设计与实现。
- 在 Template 初始化时自动改名或推断用户包名。
- 建立 Template 与 cap4k 源码仓库的同步或源代码链接。
- 提供默认契约以外的项目变体。

#### 完成门槛

必须在全新临时目录中模拟 Template 使用：

1. 不修改默认包名即可构建和运行启动烟雾测试。
2. 换一个目录名后仍可构建，证明没有固定 `rootProject.name`。
3. 无生成输入时 compile/no-op 成功。
4. 依赖解析不访问本地仓库、snapshot 或 cap4k 源码。
5. GitHub Actions 与本地 Gradle Wrapper 构建均通过。

## 18. 验证矩阵

### 18.1 结构验证

实施计划至少包含以下静态验证：

- Gradle project dependency graph 不存在禁止边。
- `ddd-core` 不引用 JPA、Hibernate 或 Spring Data。
- Core starter 不传递 Web。
- Core starter 不依赖 Snowflake 实现；Snowflake starter 只能单向依赖 Core 并贡献 strategy。
- `GeneratedOwnIdAccessor`、`GeneratedOwnIdCatalog` 和 `GeneratedOwnIdRegistry` 契约位于 `ddd-core`，不引用 JPA。
- JPA Repository module 不依赖 Event JPA。
- Request/Event/Saga starter 不依赖 JDBC Locker starter。
- transport starter 不依赖 Domain Event JPA starter。
- 旧 `cap4k-ddd-starter` artifact 和自动配置导入不存在。
- 被删除 API 没有生产源码、测试、template 或文档引用。

### 18.2 行为验证

至少覆盖：

- 同步 Command/Query 无 Request repository 启动和执行。
- 默认 Bean Validation 在 Handler 前执行。
- Repository 查询事务关闭前完成 WHOLE_AGGREGATE 加载。
- MINIMAL 不被默认选择。
- Factory 创建登记根聚合 CREATE intent，但不打开事务、flush 或直接访问 `EntityManager`。
- application-side generated own ID 在 Factory 和直接 CREATE 登记期间覆盖根和当前可达 owned graph，调用返回前可用，预赋值 ID 不被覆盖。
- generated owned relation `add/replace` 在 mutation 前完成 child ID，失败不改变 relation；`replace(null)` 不分配。
- `EXISTING` root 只为新可达且缺失 ID 的 child 分配，既有 observed identity 保持不变，save 前再次幂等补齐。
- separately pending reachable child 吸收到最外层 root entry，不独立调用 `EntityManager.persist/merge`；同一 child 属于两个无关 root 时失败。
- 没有 pending owner 的孤立 caller-declared `persist(child, CREATE)` 不通过新增静态 marker 猜测归属。
- 数据库 identity/version 在 create 后为 `null`，成功 save/flush 后根和 owned child 可观察为非空。
- provider 已赋值后回滚不触发 cap4k 清空内存 identity/version。
- 现有 soft-delete ID/storage 矩阵的 create/query/delete 行为在 starter 迁移后不回归。
- UoW save 是写事务边界。
- 本地 Domain Event 同线程、提交前执行。
- Event Handler 失败导致事务回滚。
- `persist=true` 缺少可靠 provider 时失败且不降级。
- 缺少 Integration Event provider 只在调用时失败。
- 已安装高级 starter 缺少必需 provider 时启动失败。
- 多 provider 冲突时启动失败。

### 18.3 生成器验证

至少覆盖：

- 无输入 no-op。
- compile task 与 generated source wiring。
- Factory 必生成。
- Factory payload 排除 generated own ID、数据库 identity/version、system-transition field 和 structural parentRef。
- 已支持的 application-side ID、数据库 identity/version、soft-delete 和 parentRef 组合生成真实构造调用，不产生 TODO。
- 真正不可推导的业务或 managed field 产生明确 TODO、未解析字段列表和 plan 标记。
- generated-own-ID typed accessor 和 module catalog 继续生成。
- generated owning relation 继续渲染 typed accessor mutation hook。
- soft-delete 字段保持 mapped initializer，但不进入 constructor 或用户写表面。
- soft-delete 保留 identity、Snowflake Long/String、UUID7 String、native UUID 五种 ID/storage 组合、精确 dialect/quoting、H2 生命周期和真实 PostgreSQL native UUID evidence。
- structural parentRef scalar 与 automatic inverse navigation 不被 Factory、Entity 或 Schema planner 恢复；physical parentRef、forward relation/Schema join、shared-PK rejection 和 owned-one unique-parentRef inference 保持。
- 普通 managed `READ_ONLY` field 不被泛化成 provider-assigned 或 synthetic nullable/null field。
- Aggregate Specification 不再存在。
- aggregate generator 不再存在 Unique 配置、planner、template、fixture、测试或文档生成承诺，且没有替代 addon。
- 删除 Unique 产物后 physical unique constraint metadata 与 owned relation cardinality inference 仍保留。
- Bootstrap slots API 未回归。

### 18.4 Template 验证

至少覆盖：

- 固定四模块和依赖方向。
- 只有 Version Catalog 包含版本。
- 没有 cap4k runtime 配置。
- 没有示例业务和 placeholder input。
- H2 空项目启动。
- `ddl-auto=validate` 和 `open-in-view=false`。
- 没有 `spring.application.name` 和 `rootProject.name`。
- 只消费正式发布制品。

## 19. 当前源码事实索引

以下链接说明实施后的当前事实，不替代本文的目标规范：

- [Core starter 自动配置导入](../../../cap4k-ddd-core-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports)
- [Core capability binder](../../../cap4k-ddd-core-starter/src/main/kotlin/com/only4/cap4k/ddd/core/autoconfigure/CoreRuntimeAutoConfiguration.kt)
- [UUIDv7 strategy](../../../cap4k-ddd-core-starter/src/main/kotlin/com/only4/cap4k/ddd/core/autoconfigure/Uuid7IdentifierStrategy.kt)
- [Snowflake starter 自动配置](../../../cap4k-ddd-snowflake-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/distributed/SnowflakeAutoConfiguration.kt)
- [Snowflake strategy](../../../cap4k-ddd-snowflake-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/SnowflakeIdentifierStrategy.kt)
- [JPA Repository/UoW 自动配置](../../../cap4k-ddd-jpa-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt)
- [JPA Repository 当前依赖](../../../ddd-domain-repo-jpa/build.gradle.kts)
- [GeneratedOwnIdAccessor 契约](../../../ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/GeneratedOwnIdAccessor.kt)
- [GeneratedOwnIdCatalog 契约](../../../ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/GeneratedOwnIdCatalog.kt)
- [GeneratedOwnIdRegistry 契约和默认实现](../../../ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/GeneratedOwnIdRegistry.kt)
- [当前 JPA generated-own-ID 支持](../../../ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedStrongIdSupport.kt)
- [当前 JpaUnitOfWork](../../../ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt)
- [JPA starter Strong ID/UoW runtime 测试](../../../cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdUowRuntimeTest.kt)
- [JPA starter soft-delete runtime 测试目录](../../../cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/softdelete)
- [数据库托管字段和 owned graph runtime 测试](../../../cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt)
- [同步 Request 实现](../../../ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/impl/DefaultRequestSupervisor.kt)
- [可靠 Request 实现](../../../ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/impl/DefaultReliableRequestSupervisor.kt)
- [本地 Domain Event 实现](../../../ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultDomainEventSupervisor.kt)
- [显式 EventTypeCatalog](../../../cap4k-ddd-core-starter/src/main/kotlin/com/only4/cap4k/ddd/core/autoconfigure/SpringEventTypeCatalog.kt)
- [静态 Mediator](../../../ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/Mediator.kt)
- [AggregateLoadPlan](../../../ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/AggregateLoadPlan.kt)
- [当前 Gradle extension](../../../cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt)
- [Factory planner](../../../cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt)
- [Entity planner](../../../cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt)
- [数据库托管字段规划](../../../cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEntrustedFieldPlanning.kt)
- [soft-delete SQL 和 initializer 渲染](../../../cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateSoftDeleteRendering.kt)
- [generated-own-ID accessor 规划](../../../cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/GeneratedOwnIdArtifactPlanner.kt)
- [Aggregate artifact planner](../../../cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt)
- [无输入编译接线](../../../cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt)
- [Bootstrap 通用 provider](../../../cap4k-plugin-pipeline-bootstrap/src/main/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProvider.kt)
- [Bootstrap test-only template bundle](../../../cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-template-bundle)

计划会话必须重新搜索相关调用方，不能把本索引当成完整文件清单。

## 20. 后续会话协议

继续本文相关工作的会话必须：

1. 完整阅读本文。
2. 确认当前工作属于已实现能力的回归修复，还是阶段三 GitHub Template。
3. 读取该阶段源码事实，不信任历史 spec。
4. 搜索所有生产、测试、fixture、template、文档和 skill 调用方。
   - Runtime 回归必须保持 Strong ID、soft-delete、数据库托管字段和 owned graph runtime 测试的新 starter 所有权。
   - Generator 回归必须保持 generated-own-ID、system-transition field、entrusted field 和 structural parentRef 的 planner/renderer 覆盖，并防止 Unique option/planner/template/fixture/test/docs 被恢复。
5. 将本文中的完成门槛转换成可执行验证任务。
6. 明确列出本阶段非目标。
7. 保证每个计划任务结束时仓库可构建。
8. 不引入兼容壳、临时 API 或下一阶段才会删除的过渡设计。

阶段一和阶段二不得作为待实施计划重新执行。阶段三只有在 Runtime、Generator、plugin 和 starter 以一致稳定版本发布到正式仓库后才能开始；届时应在独立 GitHub Template 仓库中规划和验收。

## 21. 文档自检标准

本文只有在以下检查全部通过后才可以作为后续回归或阶段三规划的权威输入：

- 没有 `TBD`、`TODO` 或未决占位符；示例 Factory 中规范要求的 `TODO("Implement aggregate construction")` 不属于文档占位符。
- 模块矩阵和 starter 依赖规则不矛盾。
- UUIDv7/ID registry 归 Core starter，Snowflake 只从独立 starter 单向扩展，JPA UoW 只消费 Core 契约。
- 默认项目能力与默认依赖一致。
- 事务边界与事件时序一致。
- generated-own-ID 的 Factory/direct CREATE、relation mutation、EXISTING backstop 和 pending child reconciliation 生命周期没有缺口或绝对化矛盾。
- provider-assigned 分类只覆盖 resolved database identity/version，普通 `READ_ONLY` 字段和 Schema 物理 nullability 不被误改。
- parentRef physical evidence、forward join、shared-PK rejection 和 owned-one inference 与 scalar/inverse 删除规则一致。
- Factory payload、构造映射和 TODO 边界不允许回归已支持的 ID、soft-delete、entrusted field 或 parentRef 组合。
- Unique 主线生成功能完整删除且没有替代 addon；physical unique metadata 和 relation inference 仍保留。
- 删除清单覆盖生产、测试、fixture、template 和文档。
- 每个阶段都有输入、范围、非目标和完成门槛。
- GitHub Template 不依赖未发布或本地 cap4k 制品。
- 新会话无需历史会话即可确认阶段一和阶段二已实现，并识别阶段三的正式发布前置条件。

本文没有未决产品问题。未来能力只以明确非目标或后置项出现，不允许在实施计划中自行扩张。
