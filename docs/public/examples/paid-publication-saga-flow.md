# Paid Publication Saga Flow

Paid publication 是 `cap4k-reference-content-studio` 的显式 opt-in 高级路径。它用 [Saga](../concepts/modeling-building-blocks/saga.md) 展示跨步骤、可重试、可恢复、可补偿的业务协调；它不是默认内容发布路径。

默认内容仍然走 [Default Publication Flow](default-publication-flow.md)：媒体处理成功后，`PublishContentCmd` 发布 `ReleasePolicy.IMMEDIATE` 内容。Paid content 必须先通过 `http/paid-publication.http` 创建，后续才会在发布准备完成时进入 `PaidPublicationSaga`。

## HTTP Entry

paid opt-in 手工路径从 `http/paid-publication.http` 开始：

1. `POST /advanced/contents/paid` 创建 paid draft。
2. 执行 `http/review.http`，提交审核并审核通过。
3. 执行 `http/query.http`，复制 `task.externalTaskId`。
4. 执行 `http/media-processing.http`，发送媒体处理成功 callback。
5. 再执行 `http/query.http`，观察 paid publication Saga 发布后的内容状态。

前四步仍复用默认路径里的 review、media-processing task 和 integration-event callback。差异发生在 `ContentPublicationReadyDomainEvent` 之后：`PublishContentCmd` 会拒绝非 `ReleasePolicy.IMMEDIATE` 内容，而 `TryStartPaidPublicationCmd` 会在 paid eligibility 通过后调度 `PaidPublicationSaga`。

## Eligibility

`ContentPublicationReadyDomainEventSubscriber` 对同一个 domain event 有两个反应：

- `continueImmediatePublication` 发送 `PublishContentCmd`。该 command 只发布 `ReleasePolicy.IMMEDIATE` 且已准备好的内容。
- `continuePaidPublication` 发送 `TryStartPaidPublicationCmd`。该 command 使用 `PaidPublicationEligibilityService` 判断内容是否是 `ReleasePolicy.PAID`、是否已经发布、是否已经 publication-ready、是否已有 Saga。

`PaidPublicationEligibilityService` 返回 `Eligible` 时，`TryStartPaidPublicationCmd` 会创建或复用 `PaidPublicationTask`，调度 `PaidPublicationSaga.Request`，并记录 Saga id。其他 decision 会作为 no-op 结果返回，不会把普通内容推入 paid Saga。

## Saga Steps

`PaidPublicationSaga` 的正向流程围绕 `PaidPublicationTask` 推进：

1. `ReserveCreatorPayoutHoldCmd`：预留创作者收益。
2. `CreateAccessEntitlementPlanCmd`：创建访问权益计划。
3. `PublishPaidPublicationContentCmd`：确认 paid content 已满足发布条件，并发布内容。
4. `MarkPaidPublicationContentPublishedCmd`：记录 paid publication task 中的内容发布事实。
5. `ActivateAccessEntitlementPlanCmd`：激活访问权益计划。

`PublishPaidPublicationContentCmd` 会再次检查 `ReleasePolicy.PAID`、`ContentStatus`、payout hold、entitlement plan 和内容 readiness。Saga 协调步骤顺序，但 Aggregate 仍然保护自己的不变量。

## Compensation

Paid publication 示例展示的是补偿型 Saga。`PaidPublicationSaga` 使用 compensable process 保存可撤销步骤，并在后续步骤失败时请求补偿：

- 如果 payout hold 已预留，补偿命令是 `ReleasePayoutHoldIfReservedCmd`。
- 如果 entitlement plan 已创建，补偿命令是 `CancelEntitlementPlanIfCreatedCmd`。
- 如果内容发布后需要记录失败，补偿命令是 `MarkPaidPublicationFailedCmd`。

补偿不是简单地“倒放代码”。每个补偿命令都表达业务允许撤销的副作用，且需要保持幂等。不能撤销或不应撤销的事实，应在领域规则里明确处理。

## Code Anchors

- `PaidPublicationSaga`
- `TryStartPaidPublicationCmd`
- `PublishPaidPublicationContentCmd`
- `PaidPublicationEligibilityService`
- `ContentPublicationReadyDomainEventSubscriber`
- `PaidPublicationTask`
- `ReserveCreatorPayoutHoldCmd`
- `ReleasePayoutHoldIfReservedCmd`
- `CreateAccessEntitlementPlanCmd`
- `CancelEntitlementPlanIfCreatedCmd`
- `ActivateAccessEntitlementPlanCmd`
- `MarkPaidPublicationContentPublishedCmd`
- `MarkPaidPublicationFailedCmd`

## Evidence Anchors

- `ContentStudioPaidPublicationSagaSmokeTest`
- `PaidPublicationEligibilityServiceTest`
- `analysis/drawing-board/drawing_board_saga.json`
- `analysis/flows/*.json`
- `analysis/flows/*.mmd`

这些文件帮助检查 paid route 是否从显式 opt-in 入口进入，Saga 是否有持久化协调和补偿语义，默认发布路径是否没有被误写成 Saga。

<!-- IMAGE_PROMPT:
Purpose: 帮助读者理解 paid publication 作为显式 opt-in Saga 路径如何推进正向步骤和补偿步骤。
Type: workflow diagram
Prompt: Draw the paid publication Saga workflow for cap4k-reference-content-studio. Start from http/paid-publication.http and POST /advanced/contents/paid, then review approval, media-processing callback, ContentPublicationReadyDomainEvent, TryStartPaidPublicationCmd, PaidPublicationEligibilityService, PaidPublicationSaga, ReserveCreatorPayoutHoldCmd, CreateAccessEntitlementPlanCmd, PublishPaidPublicationContentCmd, MarkPaidPublicationContentPublishedCmd, ActivateAccessEntitlementPlanCmd, and compensation commands ReleasePayoutHoldIfReservedCmd, CancelEntitlementPlanIfCreatedCmd, MarkPaidPublicationFailedCmd. Use Chinese labels and preserve English identifiers.
Must show: opt-in paid entry, ReleasePolicy.PAID, eligibility decision, persisted PaidPublicationTask, PaidPublicationSaga forward steps, compensation commands, content publication inside Saga
Must avoid: implying paid Saga is the default publication path, implying Saga replaces aggregate invariants, implying generator writes business decisions automatically
Alt text after insertion: paid publication Saga 工作流，展示 opt-in 入口、eligibility 判断、正向步骤、补偿命令和最终发布。
-->
