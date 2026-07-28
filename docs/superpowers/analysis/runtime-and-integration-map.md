# Runtime And Integration Map

## Purpose

本页是 cap4k 当前 Runtime 与集成边界的维护事实索引。它用于定位 capability 的接口、实现、starter 自动装配和验证所有者，不是面向业务项目的使用教程。

## Current Facts

- `ddd-core` 定义持久化中立的 tactical API、静态 `Mediator` namespace、同步 Request、本地 Domain Event、capability slots、Identifier 与 generated-own-ID 契约。其生产依赖中没有 JPA、Hibernate、Spring Data、JDBC Locker、Snowflake 或消息 transport。
- `Mediator` 不再是继承所有 supervisor 的接口，也没有 bean/instance 实现。正式静态入口为 `commands`、`queries`、`requests`、`repositories`、`factories`、`services`、`uow`、`events`、`ioc` 和 `identifiers`；`events` 表示 Integration Event，正式方法是 `attach/detach`。
- Core starter 通过 `CoreIdAutoConfiguration`、`CoreRuntimeAutoConfiguration` 和 `CoreDomainEventAutoConfiguration` 提供 UUIDv7、ID registries、同步 Request、Domain Service、本地事件、EventSubscriberManager、EventTypeCatalog 与静态 facade binder。
- capability 默认 provider 独立使用 `@ConditionalOnMissingBean`。Core binder 在 Context 初始化完成时要求 required capability 恰好一个、optional capability 至多一个；缺失 optional capability 保持未配置并在调用点抛出 `CapabilityUnavailableException`。
- `ddd-domain-repo-jpa` 实现 JPA Repository、`JpaUnitOfWork`、完整聚合加载、generated-own-ID completion、pending owned child reconciliation、soft-delete 与 provider-assigned identity/version 生命周期。它不依赖 `ddd-domain-event-jpa`。
- JPA starter 只装配 Repository、Aggregate Factory、UnitOfWork、persist listeners 和 Web domain-context cleanup；不传递 Request/Event/Saga/Locker/Snowflake/transport。
- 默认同步 Request 不创建 `RequestRecord`。可靠 schedule/result 由 `ReliableRequestSupervisor` 提供，Request JPA starter 装配 JPA repository、可靠 supervisor、补偿/归档任务，并显式要求 `Locker`。
- 默认本地 Domain Event 在 UoW 的事务内 release，并同步进入 Spring `ApplicationEventPublisher`。`@DomainEvent(persist = true)` 或未来 schedule 的事件通过 `ReliableDomainEventProvider`；Domain Event JPA starter提供 EventRecord repository、publisher、可靠 provider、补偿/归档任务，并显式要求 `Locker`。
- 事件类型不再从业务 package/classpath 扫描。Core `SpringEventTypeCatalog` 从 Spring `@EventListener` 方法的显式类型签名构建 integration-event 类型集合；应用也可以替换 catalog。
- Integration Event transport starter 分别装配 HTTP、RabbitMQ 或 RocketMQ publisher/subscriber adapter，并要求用户显式提供 `EventRecordRepository`。transport starter 不传递 Domain Event JPA starter；HTTP-JPA 只把 HTTP subscriber register 替换为 JPA 实现。
- Saga JPA starter 传递 JPA starter但不传递 Locker starter；Saga 仍是 request/process/compensation/retry/archive 范围，不是通用 callback-resume workflow engine。
- JDBC Locker 与 Snowflake 各自拥有独立 starter。Snowflake starter只向 Core registry 贡献 `snowflake` strategy；Core/JPA 默认生产 classpath 不包含 Snowflake 实现。
- 旧 `cap4k-ddd-starter` 已删除，没有 alias、deprecated 或兼容保留合同。关键 JPA Runtime tests 已迁到 `cap4k-ddd-jpa-starter`。

## Starter Ownership

| Starter | Owned capability | Structural dependency | Explicit provider/config |
| --- | --- | --- | --- |
| `cap4k-ddd-core-starter` | UUIDv7、同步 Request、Domain Service、本地 Event、IoC/capability binder | `ddd-core` | 无 |
| `cap4k-ddd-jpa-starter` | Repository、Factory、UoW、JPA lifecycle | Core | DataSource/JPA |
| `cap4k-ddd-request-jpa-starter` | reliable Request persistence/schedule | JPA | `Locker` |
| `cap4k-ddd-domain-event-jpa-starter` | reliable Event persistence/schedule | JPA | `Locker` |
| `cap4k-ddd-saga-jpa-starter` | Saga persistence/schedule | JPA | `Locker` |
| `cap4k-ddd-locker-jdbc-starter` | JDBC `Locker` | Core | locker table |
| `cap4k-ddd-snowflake-starter` | Snowflake strategy | Core | worker table/node config |
| `cap4k-ddd-integration-event-http-starter` | HTTP transport | Core | `EventRecordRepository` |
| `cap4k-ddd-integration-event-http-jpa-starter` | HTTP register JPA | HTTP + JPA | `EventRecordRepository` |
| `cap4k-ddd-integration-event-rabbitmq-starter` | RabbitMQ transport | Core | broker + `EventRecordRepository` |
| `cap4k-ddd-integration-event-rocketmq-starter` | RocketMQ transport | Core | broker + `EventRecordRepository` |

## Source Anchors

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/Mediator.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/CapabilityUnavailableException.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/impl/DefaultRequestSupervisor.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/impl/DefaultReliableRequestSupervisor.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultDomainEventSupervisor.kt`
- `cap4k-ddd-core-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `cap4k-ddd-jpa-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `cap4k-ddd-request-jpa-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `cap4k-ddd-domain-event-jpa-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `cap4k-ddd-integration-event-http-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- `cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/`

## Contracts

- Code、Gradle dependencies、`AutoConfiguration.imports` 和已运行 tests 优先于本页。
- 安装 starter 即选择能力；没有 runtime capability `enabled=true` 开关。
- 未安装 optional capability 不阻止 Core/JPA 默认项目启动；安装了 starter 却缺 provider、配置或表时启动失败。
- 可靠 Request/Event/Integration Event 不得降级为同步、本地或不持久化路径。
- Repository 负责加载，Factory 负责构造并登记 CREATE，UoW `save()` 是默认写事务边界。
- Domain Event 是事务内本地事实；Integration Event 是可靠跨边界事实。不要把 `Mediator.events` 写成 Domain Event 发布 API。

## Verification

从 worktree root 运行：

```powershell
$starterDirs = Get-ChildItem -Directory -Filter 'cap4k-ddd-*-starter' | Select-Object -ExpandProperty Name
rg -n "AutoConfiguration|ConditionalOnMissingBean|CapabilityUnavailableException" @starterDirs ddd-core
rg -n "event-scan-package|eventScanPackage|Mediator\.instance|DefaultMediator|class X" @starterDirs ddd-core
.\gradlew.bat :ddd-core:test :cap4k-ddd-core-starter:test :cap4k-ddd-jpa-starter:test
```

## Drift Watch

- starter import 或 dependency 边改变时，同步更新 ownership table 与 boundary tests。
- capability binder、`@ConditionalOnMissingBean` 或静态 facade 变化时，重新验证缺失/冲突/替换三类场景。
- JPA UoW interceptor 顺序变化时，重新验证本地 Event 事务时序与 reliable Integration Event release。
- transport 新增类型发现机制时，确认没有恢复 package/classpath event scan。
- Saga 出现 external wait/correlation/resume 协议前，不升级其公开语义。

## Not Covered

- 面向项目使用者的战术建模教程。
- 每项属性、数据库表和 broker 部署说明。
- 外部 GitHub Template 的发布版本与远程仓库维护流程。
