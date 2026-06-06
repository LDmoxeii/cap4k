# Mediator

Mediator 是运行时路由 facade，用来把 Command、Query 或其他 application message 交给对应 handler。它让调用方不必直接知道每个 handler 的具体类名和装配方式，但它不是业务编排引擎，也不是 domain layer 的依赖。

当 controller、CLI、subscriber、job 或 adapter entry 需要触发一个应用层用例时，可以通过 Mediator 发送明确的 Command 或 Query。这样入口层只表达“我要执行这个用例”，handler 负责组织应用流程。Mediator 负责找到路由，不负责决定业务下一步应该发生什么。

在 cap4k 中，generator 可以为 Command、Query、client-handler 等生成稳定入口，让 Mediator 的路由对象更容易被发现和维护。生成骨架表达 message-to-handler 的连接面；业务规则、状态判断、外部能力调用时机和事务边界仍在 handler、Aggregate、Domain Service、Saga 或 Subscriber 的手写逻辑中。

Mediator 与 Clean Architecture 的关系要保持克制。domain layer 不应依赖 Mediator 来“调用外部世界”或“派发下一个用例”；Aggregate 应只表达领域行为和事件。application layer 可以使用 Mediator 作为入口协调工具，但如果一个流程需要持久化跨步骤状态，应考虑 Saga；如果只是事件后的反应，应考虑 Subscriber 或明确的 Command delegation。

参考项目入口是 [reference-content-studio.md](../../examples/reference-content-studio.md)。在 `cap4k-reference-content-studio` 中，可以从 `TriggerMediaProcessingCli`、`GetMediaProcessingStatusCli`、`ContentPublicationReadyDomainEventSubscriber` 和 `MediaProcessingPollingFallbackJob` 这些入口观察：它们可以把外部触发转成明确的 Command 或 Query，而不是让入口自己吞下业务流程。

Mediator 的设计边界是 routing，不是 orchestration。常见误用包括让 Mediator 在 domain object 中出现，把一串业务步骤藏在通用 dispatch helper 里，或者让调用方发送含糊的 message 再由 Mediator 决定业务含义。业务意图必须在 Command、Query、event reaction 或 Saga 中命名清楚。

审查 Mediator 使用时，可以看 message 名称是否表达应用意图，handler 是否仍是业务流程的阅读入口，domain layer 是否没有依赖 Mediator，Subscriber 或 Job 是否只通过明确 Command/Query delegation 进入应用层，以及生成路由骨架是否没有被误解成自动业务编排。
