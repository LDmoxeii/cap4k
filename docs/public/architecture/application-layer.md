# Application Layer

Application layer 是 cap4k 项目的用例编排层。它把外部入口转换后的意图组织成 Command、Query、Subscriber、Saga 或 Scheduled Reaction，调用 domain layer，管理提交边界，并把需要外部能力的请求表达成 application-facing contract。

## 负责

Application layer 组织 [Command](../concepts/execution-and-ownership/command.md)、[Query](../concepts/execution-and-ownership/query.md)、[Subscriber](../concepts/execution-and-ownership/subscriber.md)、[Saga](../concepts/modeling-building-blocks/saga.md)、[Scheduled Reaction](../concepts/execution-and-ownership/scheduled-reaction.md) 和 external capability requests。它在写入提交和入口路由中使用 [Unit Of Work](../concepts/execution-and-ownership/unit-of-work.md) 与 [Mediator](../concepts/execution-and-ownership/mediator.md) 这些框架能力；业务项目通常是在 handler 中调用它们，而不是重新实现它们。它的核心职责是用例 orchestration，而不是领域不变量本身。

Command 处理改变业务状态的意图，例如发布内容或启动媒体处理。Query 处理读取观察，例如内容详情或媒体处理状态。Subscriber 处理 Domain Event 或 Integration Event 之后的反应。Saga 处理需要持久化进度、retry、recovery 或 compensation 的跨步骤协调。Scheduled Reaction 表达定时或轮询触发的 application reaction。

## 不负责

Application layer 不负责 HTTP payload details、Controller request mapping、API response shape、external service raw payload、Spring Boot startup 或具体 persistence implementation。它可以定义 `TriggerMediaProcessingCli` 这样的 external capability request class，但不应该知道 adapter 中如何发 HTTP、如何解析 callback body 或如何映射 status code。

Application layer 也不应该把领域不变量挪到 handler 里。Command handler 可以做 zero-trust validation、读取、调用 Aggregate 行为，并通过 Unit of Work 提交持久化意图；但内容是否可发布、状态如何转换、Domain Event 何时出现，应由 domain layer 的手写逻辑表达。

## 生成骨架

cap4k generation 可以为 Command、Query、Subscriber、Scheduled Reaction、Saga、external capability request 和 handler wiring 提供稳定骨架。骨架让 `PublishContentCmd`、`StartMediaProcessingCmd`、`GetContentDetailQry`、`ContentPublicationReadyDomainEventSubscriber`、`MediaProcessingPollingFallbackJob`、`PaidPublicationSaga`、`TriggerMediaProcessingCli` 和 `GetMediaProcessingStatusCli` 这类入口可被一致定位。

生成骨架负责组织位置和命名，不负责决定用例语义。一个 Command 是否需要 no-op，Subscriber 是否需要幂等，Saga 如何恢复，external capability request 代表什么业务能力，都属于 handwritten logic。

## 手写逻辑

手写逻辑应该落在 Command/Query handler、Subscriber reaction、Saga step、Scheduled Reaction 和 external capability request 使用处。它负责加载目标 Aggregate、调用 domain behavior、使用 Unit of Work 记录和提交持久化意图、通过 Mediator 分发内部请求、表达 no-op 或 retreat 原因，并在需要时调用 external capability contract。

参考项目锚点包括 `PublishContentCmd`、`StartMediaProcessingCmd`、`GetContentDetailQry`、`ContentPublicationReadyDomainEventSubscriber`、`MediaProcessingCallbackIntegrationEventSubscriber`、`MediaProcessingPollingFallbackJob`、`PaidPublicationSaga`、`TriggerMediaProcessingCli` 和 `GetMediaProcessingStatusCli`。这些入口展示 application layer 如何把发布、媒体处理和 paid publication 组织成可审查用例。

## 依赖方向

Application layer 可以依赖 domain layer 和 application-level abstractions。它不依赖 adapter 或 start。adapter 可以调用 application 的 Command、Query 或 client request contract，start 可以装配 application bean，但 application 不应 import Controller、API Payload、client-handler implementation 或 Spring Boot application class。

当外部协议需要进入 application layer 时，adapter 应先把它转换成 Command、Query、Integration Event 或 external capability result。application layer 的参数应该表达用例语言，而不是传递 HTTP body 或 callback raw schema。

## 参考项目

参考项目入口是 [reference-content-studio.md](../examples/reference-content-studio.md)。阅读 `cap4k-reference-content-studio-application` 时，优先定位这些锚点：

- `PublishContentCmd`
- `StartMediaProcessingCmd`
- `GetContentDetailQry`
- `ContentPublicationReadyDomainEventSubscriber`
- `MediaProcessingCallbackIntegrationEventSubscriber`
- `MediaProcessingPollingFallbackJob`
- `PaidPublicationSaga`
- `TriggerMediaProcessingCli`
- `GetMediaProcessingStatusCli`

这些文件能展示 application layer 如何连接 generated skeleton 和 handwritten use case orchestration。

## 审核

审核 application layer 时，先看 Command/Query separation 是否清晰，再看写入路径是否通过 Repository 读取 Aggregate、通过 Unit of Work 提交持久化意图。随后检查 Subscriber、Saga 和 Scheduled Reaction 是否表达 application reaction，而不是隐藏 adapter protocol。最后确认 external capability requests 是业务能力语义，HTTP payload details 和 client-handler implementation 没有进入 application layer。
