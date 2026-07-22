# Runtime And Integration Map

## Purpose

本页是 cap4k 当前运行时与集成边界的维护地图。它服务于维护 agent 快速定位代码事实：Spring Boot autoconfiguration 如何装配 DDD runtime、Mediator 如何作为统一入口委托请求与运行时能力、Repository / UnitOfWork 如何落到 JPA、DomainEvent / IntegrationEvent 如何跨 domain 与 adapter 边界流动，以及 Saga 当前可验证的运行范围。

本页不是公开概念教程。面向用户的战术建模、分层教育和使用示例应放到 Phase 2 public docs；这里保留 70% AI-friendly 的事实索引和 30% human-maintainer-friendly 的审查说明。

## Current Facts

- starter 自动配置入口由 `cap4k-ddd-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 决定。当前导入包含 `MediatorAutoConfiguration`、`DomainEventAutoConfiguration`、`JpaRepositoryAutoConfiguration`、`RequestAutoConfiguration`、`SagaAutoConfiguration`、`IntegrationEventAutoConfiguration`、`JdbcLockerAutoConfiguration`、`SnowflakeAutoConfiguration`、`IdPolicyAutoConfiguration`、`DomainServiceAutoConfiguration` 与配置属性类。
- runtime 责任分布不是单一模块完成：`ddd-core` 定义核心接口和默认实现骨架，`ddd-domain-repo-jpa` 提供 JPA Repository / UnitOfWork 实现，`cap4k-ddd-starter` 把这些实现装配成 Spring beans，`ddd-integration-event-http` / `ddd-integration-event-http-jpa` / `ddd-integration-event-rabbitmq` / `ddd-integration-event-rocketmq` 提供外部集成事件 adapter，`cap4k-ddd-console` 暴露控制台相关能力。
- domain 侧职责由 `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/**` 和 starter 的 `domain/**` autoconfiguration 装配。`DomainEventAutoConfiguration` 注册 `DefaultDomainEventSupervisor`、`DomainEventUnitOfWorkInterceptor`、`DefaultEventPublisher`、`DefaultEventSubscriberManager`、JPA event record repository 与定时补偿/归档服务。
- application 侧职责由 `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/**` 和 starter 的 `application/**` autoconfiguration 装配。请求分发在 `RequestAutoConfiguration`，Saga 在 `SagaAutoConfiguration`，IntegrationEvent 在 `IntegrationEventAutoConfiguration`，分布式锁在 `JdbcLockerAutoConfiguration`。
- adapter / infrastructure 边界主要体现为 JPA、HTTP、RabbitMQ、RocketMQ 与 console 模块。JPA repository / UoW 在 `ddd-domain-repo-jpa`，HTTP/RabbitMQ/RocketMQ integration adapter 在 `ddd-integration-event-*`，starter 按 classpath 与配置条件加载 adapter launcher。
- `Mediator` 是运行时统一入口和委托门面，不是另一个独立业务执行引擎。`ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/Mediator.kt` 让它继承 `AggregateFactorySupervisor`、`RepositorySupervisor`、`DomainServiceSupervisor`、`UnitOfWork`、`IntegrationEventSupervisor`、`RequestSupervisor`；`DefaultMediator` 的方法逐一委托到这些 supervisor / `UnitOfWork.instance` / `RequestSupervisor.instance`。`MediatorAutoConfiguration` 只在缺少 `Mediator` bean 时创建 `DefaultMediator` 并配置 `MediatorSupport`。
- Repository 当前 JPA 行为由 `AbstractJpaRepository` 和 `DefaultRepositorySupervisor` 支撑。`AbstractJpaRepository` 接受 Spring Data `JpaRepository` 与 `JpaSpecificationExecutor`，按 `JpaPredicate` 恢复 id / ids / specification，并在 `persist = false` 时对读取出的实体执行 `entityManager.detach(...)`；读取可套用 `AggregateLoadPlan`。
- `JpaRepositoryAutoConfiguration` 收集 `Repository<*>` beans，创建 `DefaultRepositorySupervisor`、`DefaultAggregateFactorySupervisor`、`JpaUnitOfWork`、`DefaultPersistListenerManager`、`DefaultSpecificationManager` 与 `SpecificationUnitOfWorkInterceptor`。它同时配置 `RepositorySupervisorSupport`、`AggregateFactorySupervisorSupport`、`UnitOfWorkSupport`。
- `UnitOfWork` 合同只有 `persist(entity, intent = PersistIntent.UPDATE)`、`remove`、`save(propagation)`。工厂创建路径显式注册 `CREATE`，repository 的可写读取路径显式注册 `UPDATE`。当前 JPA 实现 `JpaUnitOfWork` 用 object identity thread-local pending changes 合并同实例意图，在 `save` 中按最终 intent 分配 application-side id、校验同数据库身份冲突、调用 `UnitOfWorkInterceptor` 生命周期并执行 `EntityManager.persist/merge/remove/flush/refresh`；保存操作选择不再查询数据库存在性，并按 Spring `Propagation` 包装事务。
- DomainEvent flow 当前绑定到 UoW 生命周期。`DomainEventUnitOfWorkInterceptor.postEntitiesPersisted` 调用 `domainEventManager.release(entities)`；`DomainEventAutoConfiguration` 注册该 interceptor。`DefaultEventPublisher` 对 domain event 使用进程内 `EventSubscriberManager.dispatch(event.payload)`，并在 domain dispatch scope 内调用 `integrationEventManager.release()`，使 domain event handler 中 attach 的 integration event 能在当前事件流程后释放。
- IntegrationEvent flow 当前使用 attach / detach / release 语义。`DefaultIntegrationEventSupervisor.attach(...)` 只接受带 `@IntegrationEvent` 注解的 payload，将 attachment 放入 `EventRuntimeContext`；`IntegrationEventUnitOfWorkInterceptor.postInTransaction` 调用 `integrationEventManager.release()`；release 会创建并保存 `EventRecord`，发布 `IntegrationEventAttachedTransactionCommittedEvent`，事务提交事件监听器再调用 `eventPublisher.publish`。
- IntegrationEvent adapter 边界由 `IntegrationEventAutoConfiguration` 按 classpath 条件加载。HTTP adapter 注册 subscribe / unsubscribe / events / subscribers / consume handlers；RabbitMQ 和 RocketMQ adapter 需要对应 class 与连接配置才创建 publisher/subscriber adapter。`DefaultEventPublisher.internalPublish4IntegrationEvent` 只遍历已装配的 `IntegrationEventPublisher`。
- Saga 当前代码支持请求型 Saga、子流程、可补偿子流程、显式请求补偿、重试、归档和定时补偿。`SagaProcessSupervisor.sendCompensableProcess` 注册 compensation request；`DefaultSagaSupervisor.requestCompensation(...)` 和 `SagaManager.requestCompensation(...)` 进入补偿路径；`runCompensation` 按 compensation process codes 调用 `RequestSupervisor.instance.send(compensationRequest)`。因此可以称为 compensation-oriented Saga runtime。不要把它描述成通用 callback-resume workflow engine，除非后续代码出现可验证的 callback workflow 语义。
- runtime tests 位于 `cap4k-ddd-starter/src/test/kotlin/`，覆盖 autoconfiguration 加载、bean 依赖、JPA runtime、Strong ID runtime、RepositorySupervisor lazy 初始化等；更底层的 repository / UoW / event 测试还分布在 `ddd-domain-repo-jpa/src/test/kotlin/` 与 `ddd-core/src/test/kotlin/`。

## Source Anchors

- `cap4k-ddd-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/`
- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/MediatorAutoConfiguration.kt`
- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt`
- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/event/DomainEventAutoConfiguration.kt`
- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/application/event/IntegrationEventAutoConfiguration.kt`
- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/application/saga/SagaAutoConfiguration.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/Mediator.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/UnitOfWork.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DomainEventUnitOfWorkInterceptor.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultEventPublisher.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/impl/DefaultIntegrationEventSupervisor.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/impl/IntegrationEventUnitOfWorkInterceptor.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/saga/impl/DefaultSagaSupervisor.kt`
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/AbstractJpaRepository.kt`
- `runtime modules under cap4k-ddd-*`: 当前直接匹配到 `cap4k-ddd-starter`、`cap4k-ddd-console`；实际 runtime 实现还需同步检查 `ddd-core`、`ddd-domain-repo-jpa`、`ddd-integration-event-*`。
- `runtime tests under cap4k-ddd-starter/src/test/kotlin/`

## Contracts

- Code wins over this map。修改 runtime、public docs、skills 或生成器前，必须重新读当前 Kotlin source / tests / build files。
- `AutoConfiguration.imports` 是 starter 对 Spring Boot 自动装配暴露面的事实来源；不要从旧 dated analysis 推断当前启动边界。
- `Mediator` 文档只能说它是统一入口 / 委托门面；不要把它写成替代 `RequestSupervisor`、`UnitOfWork`、repository 或 event manager 的独立执行层。
- Repository 读取是否进入持久化上下文取决于 `persist` 参数和当前实现的 detach 行为；命令路径是否保存仍以显式 `UnitOfWork.persist/remove/save` 和 handler 语义为准。
- DomainEvent 是 domain fact 与进程内分发边界；IntegrationEvent 是 application/integration 边界，当前通过 attach 后持久化 `EventRecord` 并交由装配的 external publisher 发布。
- Saga 只能按当前代码描述为 request/SagaParam、process、compensable process、retry、scheduled compensation；callback-resume 术语需要独立代码证据。
- 公开战术概念教育属于 Phase 2 public docs，不应塞进 analysis map。

## Change Impact

- 修改 `AutoConfiguration.imports` 会改变 starter 默认装配面，影响 runtime docs、autoconfiguration tests、starter smoke tests 与用户启动行为。
- 修改 `Mediator` / `DefaultMediator` 委托面会影响 AI 生成代码对统一入口的调用方式，也会影响 code-analysis 对 `Mediator` 的识别。
- 修改 Repository / UoW 行为会影响 aggregate 保存、value object 存在性检查、application-side id、event interceptor 触发时机和 JPA runtime tests。
- 修改 DomainEvent release 时机会影响 integration event attach 后释放、事件持久化、订阅者调度、补偿/归档任务和 event tests。
- 修改 IntegrationEvent adapter 条件或 publisher callback 会影响 HTTP/RabbitMQ/RocketMQ 的可选边界、starter 自动装配和跨服务事件投递。
- 修改 Saga compensation 行为会影响 `SagaRecord` 状态机、定时补偿、控制台 retry / search、请求处理和 public Saga 说明。

## Verification

从 cap4k worktree root 运行：

```powershell
Get-Content -Path cap4k-ddd-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports -Raw
```

PowerShell 下不要依赖 Unix shell 风格的未展开通配符。若 `rg ... cap4k-ddd-* cap4k-ddd-starter` 失败，先取显式目录：

```powershell
$dddDirs = Get-ChildItem -Directory -Filter 'cap4k-ddd*' | Select-Object -ExpandProperty Name
rg -n "Mediator|UnitOfWork|Repository|DomainEvent|IntegrationEvent|Saga|AutoConfiguration" @dddDirs ddd-core ddd-domain-repo-jpa ddd-integration-event-http ddd-integration-event-http-jpa ddd-integration-event-rabbitmq ddd-integration-event-rocketmq
```

也可用最小 starter 范围复查自动配置与测试锚点：

```powershell
rg -n "Mediator|UnitOfWork|Repository|DomainEvent|IntegrationEvent|Saga|AutoConfiguration" cap4k-ddd-starter cap4k-ddd-console
rg -n "JpaUnitOfWork|AbstractJpaRepository|DefaultEventPublisher|DefaultIntegrationEventSupervisor|DefaultSagaSupervisor" ddd-core ddd-domain-repo-jpa cap4k-ddd-starter/src/test/kotlin
```

## Drift Watch

- 如果 `AutoConfiguration.imports` 删除或新增 autoconfiguration，本页的 starter 装配事实必须同步更新。
- 如果 `Mediator` 不再继承当前 supervisor interfaces，或 `DefaultMediator` 不再纯委托，应重新描述入口语义。
- 如果 `JpaUnitOfWork.save` 改变 interceptor 顺序，DomainEvent / IntegrationEvent release 时机要重新审查。
- 如果 integration event adapter 从 classpath 条件加载改为显式配置开关，adapter 边界和 public docs 都要重写。
- 如果 Saga 新增 callback correlation、external wait state 或 resumable workflow protocol，再考虑是否能描述为 callback-resume workflow engine；当前不要这样写。
- 如果 runtime module 命名从 `ddd-*` 迁移回 `cap4k-ddd-*`，Verification 的显式目录列表要调整。

## Not Covered

- public tactical modeling 教程、示例项目 walkthrough 或用户入门说明。
- 每个 autoconfiguration property 的完整配置表。
- `RequestAutoConfiguration` 的完整 command/query/CLI 调度细节。
- 所有 integration adapter 的协议字段、消息头和部署拓扑。
- Saga persistence schema 与控制台 API 的完整字段说明。
