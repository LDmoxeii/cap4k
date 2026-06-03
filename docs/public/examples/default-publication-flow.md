# Default Publication Flow

默认发布路径展示 `cap4k-reference-content-studio` 如何把 [Aggregate](../concepts/modeling-building-blocks/aggregate.md)、[Domain Event](../concepts/modeling-building-blocks/domain-event.md)、[Integration Event](../concepts/modeling-building-blocks/integration-event.md)、[Command](../concepts/execution-and-ownership/command.md) 和 [Subscriber](../concepts/execution-and-ownership/subscriber.md) 串成一条可审查的业务流程。

这条路径的业务范围是：content draft -> submit review -> approve review -> media-processing callback -> content published。Paid publication 不属于默认路径；它是 [Paid Publication Saga Flow](paid-publication-saga-flow.md) 中的显式 opt-in 高级路径。

## Entry Files

默认路径的手工操作入口是这些 `.http` 文件：

- `http/content.http`
- `http/review.http`
- `http/query.http`
- `http/media-processing.http`

对应的 runtime smoke 证据主要是 `ContentStudioHappyPathHttpSmokeTest` 和 `MediaProcessingCallbackIntegrationEventSmokeTest`。

## Flow

1. `http/content.http` 调用 `POST /contents`，adapter layer 的 controller 把 HTTP payload 转成 `CreateContentDraftCmd.Request`。
2. `CreateContentDraftCmd` 使用 `ContentFactory` 创建 `Content` draft。`ContentBehavior.kt` 中的 `onCreate` 记录 `ContentDraftCreatedDomainEvent`。
3. `http/review.http` 先调用 `POST /contents/{contentId}/submit-review`，再调用 `POST /contents/{contentId}/approve`。这两步分别进入 `SubmitContentForReviewCmd` 和 `ApproveContentReviewCmd`。
4. `Content.approve` 在审核通过后记录 `ContentReviewApprovedDomainEvent`。如果内容还没有媒体就绪时间，它还会记录 `ContentRequiresMediaProcessingDomainEvent`。
5. `ContentRequiresMediaProcessingDomainEventSubscriber` 接收 domain event，并发送 `StartMediaProcessingCmd`。该 command 创建或复用媒体处理任务。
6. `http/query.http` 查询 `GET /media-processing/{contentId}`，等任务出现后复制 `task.externalTaskId`。
7. `http/media-processing.http` 通过 `POST /cap4k/integration-event/http/consume?...` 发送媒体处理成功 integration-event callback。
8. `MediaProcessingCallbackIntegrationEventSubscriber` 把 callback 转成 `MarkMediaProcessingSucceededCmd`。该 command 标记媒体任务成功，并写入 `MediaProcessingResultSnapshot`。
9. `MediaProcessingSucceededDomainEventSubscriber` 接收媒体成功事件，并发送 `RecordContentMediaReadyCmd`。
10. `Content.recordMediaReady` 写入媒体就绪时间。如果审核已通过且内容尚未发布，它会记录 `ContentPublicationReadyDomainEvent`。
11. `ContentPublicationReadyDomainEventSubscriber` 发送 `PublishContentCmd`。`PublishContentCmd` 只发布 `ReleasePolicy.IMMEDIATE` 且已经 publication-ready 的内容。
12. `Content.publish` 把 `contentStatus` 推进到 `PUBLISHED`，并记录 `ContentPublishedDomainEvent`。`ContentPublishedDomainEventSubscriber` 是后续发布事实的 subscriber 锚点。

## Why This Is Not A Saga

默认路径有多个事件和 subscriber，但它不是 Saga。媒体处理 callback 是一次 inbound integration event；发布准备后的 `PublishContentCmd` 是 application reaction；这些步骤不需要 `PaidPublicationSaga` 那种持久化跨步骤协调、补偿和恢复语义。

如果要理解 Saga，请看 paid opt-in 路径。默认路径的重点是 Aggregate 保护发布不变量，Domain Event 表达领域事实，Subscriber 把事实转成 application layer 的下一步 command。

## Code Anchors

- `ContentBehavior.kt`
- `ContentFactory.kt`
- `CreateContentDraftCmd`
- `SubmitContentForReviewCmd`
- `ApproveContentReviewCmd`
- `StartMediaProcessingCmd`
- `MarkMediaProcessingSucceededCmd`
- `RecordContentMediaReadyCmd`
- `PublishContentCmd`
- `ContentRequiresMediaProcessingDomainEventSubscriber`
- `MediaProcessingCallbackIntegrationEventSubscriber`
- `MediaProcessingSucceededDomainEventSubscriber`
- `ContentPublicationReadyDomainEventSubscriber`
- `ContentPublishedDomainEventSubscriber`

## Evidence Anchors

- `ContentStudioHappyPathHttpSmokeTest`
- `MediaProcessingCallbackIntegrationEventSmokeTest`
- `analysis/flows/index.json`
- `analysis/flows/*.json`
- `analysis/flows/*.mmd`

这些 evidence surfaces 用来交叉检查 HTTP path、controller flow、integration-event subscriber flow 和 application command flow 是否和手工 `.http` 路径一致。

<!-- IMAGE_PROMPT:
Purpose: 帮助读者理解默认内容发布路径如何从 HTTP 操作进入 command、domain event、subscriber 和最终发布。
Type: workflow diagram
Prompt: Draw the default publication workflow for cap4k-reference-content-studio. Show http/content.http, http/review.http, http/query.http, http/media-processing.http, CreateContentDraftCmd, SubmitContentForReviewCmd, ApproveContentReviewCmd, ContentRequiresMediaProcessingDomainEvent, StartMediaProcessingCmd, MediaProcessingCallbackIntegrationEventSubscriber, MarkMediaProcessingSucceededCmd, RecordContentMediaReadyCmd, ContentPublicationReadyDomainEvent, PublishContentCmd, and final contentStatus PUBLISHED. Use Chinese labels and preserve English identifiers.
Must show: default path only, review approval, media-processing callback, Domain Event to Subscriber reactions, PublishContentCmd, contentStatus PUBLISHED, processingStatus SUCCEEDED
Must avoid: showing PaidPublicationSaga as part of the default path, implying every callback is Saga, implying generator writes business decisions automatically
Alt text after insertion: 默认内容发布工作流，从创建草稿、审核通过、媒体处理回调到 PublishContentCmd 发布内容。
-->
