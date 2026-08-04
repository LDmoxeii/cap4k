# Mediator

Mediator 是运行时路由 facade，用来把 Command、Query 或 Capability call 交给对应 Handler。它让调用方不必直接知道每个 Handler 的具体类名和装配方式，但它不是业务编排引擎，也不是 domain layer 的依赖。

当 controller、subscriber、job 或 adapter entry 需要触发应用层行为时，可以通过 `Mediator.commands`、`Mediator.queries` 或 `Mediator.capabilities` 进入明确分类。Mediator 不暴露 generic `requests` 或应用侧 `uow` 逃生口；Command、Query 与 Capability 各自保留独立事务、异步和调用规则。

Query 与 Capability 都通过同一种阻塞 Handler 同时支持同步和异步 Supervisor API。`askAsync()`、`callAsync()` 使用框架有界 executor 和 ExecutionContext 传播；它们提供可控并行机会，不承诺线程切换或强制取消。异步 Command 则是可靠登记和新事务执行，不能退化成 Caller Runs。

在 cap4k 中，generator 可以为 Command、Query、Capability 与各自 Handler 生成稳定入口。生成骨架表达 message-to-handler 的连接面；业务规则、状态判断、外部能力调用时机和事务边界仍在 Handler、Aggregate、Domain Service 或 Subscriber 的手写逻辑中。

Mediator 与 Clean Architecture 的关系要保持克制。domain layer 不应依赖 Mediator 来“调用外部世界”或“派发下一个用例”；Aggregate 应只表达领域行为和事件。application layer 可以使用 Mediator 做明确路由；需要持久化跨事务状态时，应组合可靠 Command、Integration Event 或选定的外部编排 provider。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。在 `cap4k-reference-content-studio` 中，可以从 `TriggerMediaProcessing`、`GetMediaProcessingStatus`、`ContentPublicationReadyDomainEventSubscriber` 和 `MediaProcessingPollingFallbackJob` 这些入口观察：它们可以把外部触发转成明确的 Command 或 Query，而不是让入口自己吞下业务流程。

Mediator 的设计边界是 routing，不是 orchestration。常见误用包括让 Mediator 在 domain object 中出现，把一串业务步骤藏在通用 dispatch helper 里，或者让调用方发送含糊的 message 再由 Mediator 决定业务含义。业务意图必须在 Command、Query、Capability 或 event reaction 中命名清楚。

审查 Mediator 使用时，可以看 message 名称是否表达应用意图，handler 是否仍是业务流程的阅读入口，domain layer 是否没有依赖 Mediator，Subscriber 或 Job 是否只通过明确 Command/Query delegation 进入应用层，以及生成路由骨架是否没有被误解成自动业务编排。
