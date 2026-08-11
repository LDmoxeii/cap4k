# Runtime And Integration Map

## Purpose

本页是 cap4k 当前 Runtime 与集成边界的维护事实索引。它用于定位 capability 的接口、实现、starter 自动装配和验证所有者，不是面向业务项目的使用教程。

## Current Facts

- `ddd-core` 定义持久化中立的 tactical API、静态 `Mediator` namespace、Command/Query/Capability、ExecutionContext、InvocationScope、本地 Domain Event、capability slots、Identifier 与 generated-own-ID 契约。其生产依赖中没有 JPA、Hibernate、Spring Data、分布式协调实现或消息 transport。
- `Mediator` 不再是继承所有 supervisor 的接口，也没有 bean/instance 实现。正式静态入口为 `commands`、`queries`、`capabilities`、`repositories`、`factories`、`services`、`events`、`ioc` 和 `identifiers`；没有 generic `requests` 或公共 `uow` 入口。`events` 表示 Integration Event，正式方法是 `attach/detach`。
- Core starter 通过 `CoreIdAutoConfiguration`、`CoreRuntimeAutoConfiguration` 和 `CoreDomainEventAutoConfiguration` 提供 UUIDv7、ID registries、Command/Query/Capability supervisors、ExecutionContext/InvocationScope、本地事件、统一 Event Handler descriptor/registry/dispatcher、EventTypeCatalog 与静态 facade binder。
- capability 默认 provider 独立使用 `@ConditionalOnMissingBean`。Core binder 在 Context 初始化完成时要求 required capability 恰好一个、optional capability 至多一个；缺失 optional capability 保持未配置并在调用点抛出 `CapabilityUnavailableException`。
- `ddd-domain-repo-jpa` 实现 JPA Repository、Hibernate-backed `JpaUnitOfWork`、Query read-only execution、generated-own-ID completion、聚合变化识别、审计 enrich、soft-delete 与 provider-assigned identity/version 生命周期。Repository 按需保留 managed lazy navigation，不再展开完整对象图或接受 `AggregateLoadPlan`。
- JPA starter 只装配 Repository、Aggregate Factory、聚合根目录、JPA 持久化协调和 Web domain-context cleanup；不传递 Command/Event/transport。Command JPA starter 在它之上提供外层 REQUIRED 事务和自动 UoW 完成。
- 同步 Command 直接执行，不创建可靠记录。可靠 `send()` 由 `DefaultReliableCommandSupervisor` 登记，Command JPA starter 装配 JPA repository、可靠 supervisor，以及 Runtime-owned 的原子 claim、lease、delivery token、retry、manual redrive 和 retention/cleanup。可靠记录独立保存 origin ExecutionContext，不依赖公共分布式锁 capability。
- 默认本地 Domain Event 在 Command UoW 的同步 frontier 中 release，并直接进入统一 `EventHandlerDispatcher`。Spring `ApplicationEventPublisher` 仅用于可靠 Domain Event / Integration Event 的事务提交后唤醒信号，不是业务事件 Handler 的分发路径。`@DomainEvent(persist = true)` 或未来 schedule 的事件通过 `ReliableDomainEventProvider`；Domain Event JPA starter 提供 EventRecord repository、publisher、可靠 provider，以及同一套 Runtime-owned claim/lease/token/retry/redrive/retention 状态机。可靠记录独立保存 origin ExecutionContext，不依赖公共分布式锁 capability。
- 本地 Domain Event 与入站 Integration Event 共用方法级 `@EventListener` authoring surface，以及同步、串行、fail-fast dispatcher。不同方法级 `@Order` 值按数值从小到大执行，相同值不承诺次序。Handler 返回后等待其 InvocationScope 内登记的 `askAsync()` / `callAsync()`；失败传播到本地事务或 transport delivery。
- 事件类型不再从业务 package/classpath 扫描。Core `SpringEventTypeCatalog` 从 Spring `@EventListener` 方法的显式类型签名构建 integration-event 类型集合；共享 inbound registration view 将 active catalog 与合法本地同步 Handler descriptor 取交集。
- Integration Event transport starter 分别装配 HTTP、RabbitMQ 或 RocketMQ publisher/subscriber adapter，并要求用户显式提供 `EventRecordRepository`。transport starter 不传递 Domain Event JPA starter；HTTP 只保留静态 route 与固定接收端点，不存在 subscriber registry 或 HTTP-JPA carrier。
- Cap4k 不提供内建 Saga runtime、持久化、starter 或 generator family。超过可靠 Command 与 Integration Event 组合能力的长流程，由应用显式编排或接入外部 provider。
- Cap4k 不再提供公共 Locker API、分布式锁 Runtime 模块、starter 或数据库表。UUID7 是唯一内置的 application-side Strong ID 分配策略；数据库 identity 仍是 persistence policy。
- 旧 `cap4k-ddd-starter` 已删除，没有 alias、deprecated 或兼容保留合同。关键 JPA Runtime tests 已迁到 `cap4k-ddd-jpa-starter`。

## Starter Ownership

| Starter | Owned capability | Structural dependency | Explicit provider/config |
| --- | --- | --- | --- |
| `cap4k-ddd-core-starter` | UUIDv7、Command/Query/Capability、ExecutionContext、Domain Service、本地 Event、IoC/capability binder | `ddd-core` | 无 |
| `cap4k-ddd-jpa-starter` | Repository、Factory、Hibernate UoW、Query read-only transaction、audit lifecycle | Core | DataSource/JPA |
| `cap4k-ddd-command-jpa-starter` | Command REQUIRED transaction、automatic UoW、reliable Command persistence/schedule/claim/retry/redrive/retention | JPA | DataSource/JPA |
| `cap4k-ddd-domain-event-jpa-starter` | reliable Event persistence/schedule/claim/retry/redrive/retention | JPA | DataSource/JPA |
| `cap4k-ddd-integration-event-http-starter` | HTTP transport | Core | `EventRecordRepository` |
| `cap4k-ddd-integration-event-rabbitmq-starter` | RabbitMQ transport | Core | broker + `EventRecordRepository` |
| `cap4k-ddd-integration-event-rocketmq-starter` | RocketMQ transport | Core | broker + `EventRecordRepository` |

## Source Anchors

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/Mediator.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/CapabilityUnavailableException.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/command/impl/DefaultCommandSupervisor.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/command/impl/DefaultReliableCommandSupervisor.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/impl/DefaultQuerySupervisor.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/capability/impl/DefaultCapabilitySupervisor.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultDomainEventSupervisor.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/Cap4kEventHandlerDescriptor.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/Cap4kEventHandlerRegistry.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultEventHandlerDispatcher.kt`
- `cap4k-ddd-core-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `cap4k-ddd-jpa-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `cap4k-ddd-command-jpa-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `cap4k-ddd-domain-event-jpa-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `cap4k-ddd-integration-event-http-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- `cap4k-ddd-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/`

## Contracts

- Code、Gradle dependencies、`AutoConfiguration.imports` 和已运行 tests 优先于本页。
- 安装 starter 即选择能力；没有 runtime capability `enabled=true` 开关。
- 未安装 optional capability 不阻止 Core/JPA 默认项目启动；安装了 starter 却缺 provider、配置或表时启动失败。
- 可靠 Command/Event/Integration Event 不得降级为同步、本地或不持久化路径。
- Repository 负责加载，Factory 负责构造并登记 CREATE，外层 Command Coordinator 自动完成 UoW；应用代码没有 `save()`、`persist()` 或 `flush()` 入口。
- Domain Event 是事务内本地事实；Integration Event 是可靠跨边界事实。不要把 `Mediator.events` 写成 Domain Event 发布 API。

## Verification

从 worktree root 运行：

```powershell
$starterDirs = Get-ChildItem -Directory -Filter 'cap4k-ddd-*-starter' | Select-Object -ExpandProperty Name
rg -n "AutoConfiguration|ConditionalOnMissingBean|CapabilityUnavailableException" @starterDirs ddd-core
rg -n "event-scan-package|eventScanPackage|Mediator\.instance|DefaultMediator|class X" @starterDirs ddd-core
.\gradlew.bat :ddd-core:test :cap4k-ddd-core-starter:test :cap4k-ddd-jpa-starter:test :cap4k-ddd-command-jpa-starter:test
```

## Drift Watch

- starter import 或 dependency 边改变时，同步更新 ownership table 与 boundary tests。
- capability binder、`@ConditionalOnMissingBean` 或静态 facade 变化时，重新验证缺失/冲突/替换三类场景。
- JPA UoW 稳定化顺序变化时，重新验证 audit enrich、本地 Event frontier、reliable Command 与 Integration Event 登记时序。
- transport 新增类型发现机制时，确认没有恢复 package/classpath event scan。
- Event Handler discovery、排序、InvocationScope completion 或启动拒绝规则变化时，同步更新 `runtime.json` 的 `eventHandler` contract，并运行 current-runtime-facts stale-term guard。
- 不因临时流程编排需求重新引入 generic Request 或内建 Saga；需要 durable orchestration 时先选择明确 provider 边界。

## Not Covered

- 面向项目使用者的战术建模教程。
- 每项属性、数据库表和 broker 部署说明。
- 外部 GitHub Template 的发布版本与远程仓库维护流程。
