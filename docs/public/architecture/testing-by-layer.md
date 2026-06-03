# Testing By Layer

Testing by layer 的目标是让测试责任和 Clean Architecture 边界一致。cap4k 项目中，generated skeleton 提供稳定入口和目录 shape；测试重点应该落在 handwritten business behavior、application orchestration、adapter protocol conversion 和 start runtime wiring 上。

测试不是只证明文件存在。一个 Command、Aggregate、Subscriber 或 Controller 的骨架可以由 generation 提供，但业务状态如何推进、何时拒绝、如何 no-op、如何恢复、如何转换外部协议，仍需要用测试或审查证据覆盖。

## Domain Layer Tests

Domain layer tests 应直接覆盖业务不变量和状态变化。重点是 Aggregate behavior、Factory 创建规则、Value Object 语义、Domain Service 决策和 Domain Event 触发条件。它们应尽量脱离 HTTP、Spring Boot、client-handler 和 persistence implementation。

参考项目锚点是 `ContentBehaviorTest.kt` 和 `ContentFactoryTest.kt`。这些测试应证明 `ContentBehavior.kt` 与 `ContentFactory.kt` 的 handwritten logic 正确表达内容生命周期，而不是通过端到端 smoke path 间接推断领域规则。

审核 domain tests 时，检查 positive state progression、negative invariant rejection、默认值、事件产生条件和边界输入。若关键不变量只在 adapter 或 start smoke test 中被覆盖，应把它视为 residual test risk。

## Application Layer Tests

Application layer tests 应覆盖用例编排和提交边界。Command tests 应检查加载目标 Aggregate、zero-trust validation、Unit of Work 保存、no-op 或 retreat 原因、Domain Event 后续反应和 external capability request 的语义。Query tests 应检查读取意图和结果 shape，但不应让 Query 偷偷改变业务状态。

参考项目锚点包括 `PublishContentCommandContractTest`、`ContentPublicationReadyDomainEventSubscriber`、`MediaProcessingCallbackIntegrationEventSubscriber`、`MediaProcessingPollingFallbackJob` 和 `PaidPublicationSaga`。对 `PublishContentCmd`、`StartMediaProcessingCmd`、`GetContentDetailQry`、`TriggerMediaProcessingCli` 或 `GetMediaProcessingStatusCli` 的测试，应关注 application orchestration，而不是 HTTP payload details。

审核 application tests 时，检查 Command Query Separation 是否被保护，Subscriber 是否幂等，Saga 是否覆盖 retry/recovery/compensation 的关键路径，Scheduled Reaction 是否和 callback 表达同一业务事实时收敛到相同内部语义。

## Adapter Layer Tests

Adapter layer tests 应覆盖 protocol conversion。Controller、API Payload、query adapter、client-handler、inbound integration listener 和 persistence adapter 的测试重点，是外部协议如何映射到 application contract，以及 application result 如何转换成外部 response。

参考项目锚点包括 `ContentController`、`ReviewController`、`QueryController`、`AdvancedPaidPublicationController`、`GetContentDetailQryHandler`、`GetMediaProcessingStatusQryHandler`、`TriggerMediaProcessingCliHandler`、`GetMediaProcessingStatusCliHandler`、paid publication `*CliHandler` 和 `MediaProcessingCallbackIntegrationEventSmokeTest`。其中 smoke test 可以证明 inbound HTTP consumption wiring 可达，但不替代 domain/application 的 focused tests。

审核 adapter tests 时，检查 mapping、status code、external error handling、callback payload conversion、query response shape 和 persistence technical mapping。若测试在 adapter 层断言业务不变量，应确认这些规则也在 domain/application focused tests 中存在。

## Start Layer Tests

Start layer tests 应证明 Spring Boot runtime assembly、local startup、runtime config 和 smoke path 可用。它们适合覆盖 bean wiring、profile selection、module assembly、HTTP happy path、callback wiring 和跨模块 smoke path。

参考项目锚点包括 `StartApplicationSmokeTest`、`ContentStudioHappyPathHttpSmokeTest`、`ContentStudioPaidPublicationSagaSmokeTest`、`ContentStudioDesignContractTest`、`PublishContentCommandContractTest` 和 `MediaProcessingCallbackIntegrationEventSmokeTest`。这些测试能作为 reference project evidence anchors，帮助读者观察 start module 如何把 domain、application 和 adapter 装配到 runtime。

审核 start tests 时，检查它们是否证明 runtime wiring，而不是把全部业务正确性压在 smoke tests 上。smoke path 适合证明系统能跑通；domain behavior 和 application orchestration 仍需要更靠内层的测试。

## Generated Skeleton Boundaries

generated skeleton 的测试责任是结构和 contract 边界，而不是业务含义本身。`ContentStudioDesignContractTest` 这类测试可以证明 design input、generated skeleton 和 public contract 的关系；`PublishContentCommandContractTest` 可以证明 Command contract 稳定。但这些测试不应被当成业务规则已经正确实现的证据。

手写逻辑测试应覆盖 generator 不会自动决定的内容：状态转移、异常路径、幂等、恢复、external capability semantics、protocol mapping 和 runtime wiring。审查测试组合时，可以按一条路径追踪：domain behavior 是否被 focused tests 覆盖，application orchestration 是否被 contract 或 focused tests 覆盖，adapter protocol conversion 是否被 adapter/smoke tests 覆盖，start assembly 是否被 smoke tests 覆盖。

参考项目入口是 [reference-content-studio.md](../examples/reference-content-studio.md)。阅读测试锚点时，应把它们当成 evidence anchors，而不是要求每个项目复制同名测试类。测试名称可以不同，但责任边界应保持一致。
