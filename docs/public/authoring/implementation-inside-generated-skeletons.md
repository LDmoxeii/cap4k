# Implementation Inside Generated Skeletons

cap4k 的生成骨架是作者实现业务逻辑的合同。它给出稳定命名、目录、接口、wiring 和 ownership，让复杂业务逻辑落到预期 surface 中，而不是散落在 controller、repository adapter、build-owned generated source 或临时 helper 里。

本页延续 [Generated Skeleton And Handwritten Logic](../concepts/execution-and-ownership/generated-skeleton-and-handwritten-logic.md) 的边界：generator 负责结构，handwritten logic 负责业务含义。复杂逻辑属于 intended generated skeleton surfaces；如果作者发现自己必须绕过 skeleton，通常说明前面的设计或输入需要回看。

## Skeletons As Contracts

生成骨架提供的合同包括：

- 文件名和 package 表达职责。
- Command、Query、Subscriber、Saga、client、client-handler、payload、factory、repository adapter 等入口位置稳定。
- module placement 和 Clean Architecture 层级可审查。
- `conflictPolicy` 说明 generator 与已有文件如何相处。
- `@BuildingBlock` 或 output metadata 帮助区分结构来源。

这些合同让 code review 能先问“这个逻辑是否在正确 surface 中”，再问“这个逻辑是否正确”。没有这个分工，作者容易把发布规则写进 adapter，把协议转换写进 domain，或者把补偿逻辑塞进普通 command helper。

## Where Business Logic Belongs

不同业务逻辑应该落在不同 surface：

- Aggregate behavior：状态推进、不变量、领域事实触发，例如 `Content` 的审核、媒体就绪和发布准备。
- Factory：聚合创建和默认状态，例如内容草稿创建。
- Value Object：不可变值语义、构造校验和值相等，例如 `MediaProcessingResultSnapshot`。
- Command handler：加载 Aggregate、zero-trust validation、调用行为、使用 Unit of Work 保存、处理 no-op。
- Query handler：读取和投影，不改变业务事实。
- Subscriber：响应 Domain Event 或 Integration Event，路由后续 Command。
- Scheduled Reaction：定时或 polling fallback 的 application reaction。
- Saga：持久化跨步骤协调、retry、recovery 和 compensation。
- client-handler：外部能力协议转换和错误转换。
- Controller / Payload：HTTP request 和 response shape 的转换。

复杂业务逻辑不应因为“实现起来方便”而越过这些 surface。比如 `Content` 是否可发布是 domain/application 问题，不应该写在 `ContentController`；媒体处理 callback 的 raw payload 是 adapter 问题，不应该进入 Aggregate；paid publication 补偿是 Saga/application 问题，不应该藏在 external client adapter。

## Do Not Fight Ownership

ownership 冲突通常有几个表现：

- 在 build-owned generated source 中写长期业务规则。
- 手改 generator 会覆盖的文件，却没有回到 plan review 检查 `conflictPolicy`。
- 绕过 generated Command handler，直接在 Controller 里加载并修改 Aggregate。
- 因为 skeleton 缺少入口，就在不相关 package 中创建“临时”业务流程。
- 把 generated metadata 当成业务规则已经完成的证据。

正确做法是回到 ownership：先读 `build/cap4k/plan.json`，确认 output family、`outputPath`、`templateId` 和 `conflictPolicy`；再判断现有 skeleton 是否就是 intended surface；如果不是，回到 generator input projection 或 technical design 修改输入和边界。

## Bypassing Skeletons Means Returning To Design

绕过 skeleton 不是完全禁止，但它需要更强的设计理由。作者必须能解释：

- 为什么现有 skeleton 不适合这个业务行为。
- 绕过后如何保持 Command/Query、domain/application/adapter/start 边界。
- 代码审查者如何从命名和目录看出职责。
- generation ownership 如何避免覆盖或重复。
- 后续 evidence 如何证明这个 fallback surface 仍然正确。

如果这些问题无法回答，就不应该继续绕过。缺 skeleton 是 generator input 或 template coverage 问题；缺业务理由是 modeling 或 technical design 问题；ownership 冲突是 plan review 问题。

## Reference Project Anchors

在 [Reference Content Studio](../examples/reference-content-studio.md) 中，可以按这些锚点阅读 skeleton 与 handwritten logic：

- `ContentBehavior.kt`：Aggregate behavior 中的内容生命周期规则。
- `ContentFactory.kt`：草稿创建和默认状态。
- `MediaProcessingResultSnapshot.kt`：JSON-backed Value Object。
- `PublishContentCmd`、`StartMediaProcessingCmd`、`MarkMediaProcessingSucceededCmd`：Command contract 与 handler surface。
- `ContentPublicationReadyDomainEventSubscriber`：领域事实后的 application reaction。
- `MediaProcessingCallbackIntegrationEventSubscriber`：外部 callback 事实进入内部命令。
- `MediaProcessingPollingFallbackJob`：Scheduled Reaction fallback。
- `PaidPublicationSaga`：paid publication 的持久化跨步骤协调。
- `TriggerMediaProcessingCliHandler`、`GetMediaProcessingStatusCliHandler`：external capability adapter。

这些文件不要求每个项目复制同名类。它们展示的是作者应该如何让复杂逻辑在可审查 surface 中出现。

## Implementation Review Questions

实现阶段可以用这些问题自审：

- 这段逻辑是否位于生成骨架预期的 surface。
- 如果它改变业务事实，是否通过 Command 和 Aggregate 行为完成。
- 如果它读取数据，是否保持 Query 无副作用。
- 如果它响应事件，Subscriber 是否只路由后续用例，写入资格是否仍由 Command 校验。
- 如果它调用外部能力，application contract 和 adapter implementation 是否分开。
- 如果它协调多个持久化步骤，Saga 是否记录进度、恢复和补偿。
- 如果它处理协议，adapter 是否没有承载领域判断。
- 如果它看起来需要绕过 skeleton，是否已经回到前面的 authoring 圈修正设计。

实现阶段的目标不是“把所有空方法填满”，而是让每个 business decision 都落在能被长期维护和验证的位置。
