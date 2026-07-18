# Verification And Feedback

verification 是 authoring 螺旋的反馈面。它不只是证明某个命令执行成功，也不只是证明文件已经生成；它把静态审查、focused tests、HTTP examples、generation evidence 和 analysis evidence 放在一起，检查业务意图、模型、技术设计、生成输入、生成骨架和手写逻辑是否一致。

本页描述 public workflow concepts。它说明读者可以如何理解这些证据面；阅读本文不要求运行 build、test 或 HTTP。

## Static Review

静态审查先看结构和 ownership：

- 业务规则是否在 domain/application 的手写位置，而不是 adapter 或 build-owned generated source。
- Command、Query、Subscriber、Saga、Scheduled Reaction 和 client-handler 是否各自承担正确职责。
- `design/design.json`、schema、`types.enumManifest`、`types.valueObjectManifest` 和 source 是否使用一致命名。
- module placement 是否符合 [Architecture](../architecture/index.md)。
- `build/cap4k/plan.json` 中的 `outputPath`、`templateId`、`moduleRole` 和 `conflictPolicy` 是否保护生成骨架与手写逻辑。

静态审查适合发现“看起来能跑，但边界已经错位”的问题。比如 adapter 中出现发布条件判断，或 Query handler 中出现状态变更，即使 smoke path 仍然成功，也应该反馈到 technical design。

## Focused Tests

focused tests 把业务行为和层级责任直接写成证据。它们不是越多越好，而是要覆盖 generator 不会自动决定的内容：

- domain tests：Aggregate behavior、Factory、Value Object、Domain Service、Domain Event 触发条件。
- application tests：Command orchestration、zero-trust validation、Unit of Work、Subscriber reaction、typed inbound Integration Event interpretation、idempotency、semantic translation、Command/application delegation、Saga recovery/compensation、Scheduled Reaction 和 external capability semantics。
- adapter tests：Controller mapping、payload conversion、client-handler error conversion、query output shape 和 protocol shape mapping。
- start smoke tests：runtime assembly、module wiring、HTTP happy path 和 framework/runtime callback transport wiring。

[Testing By Layer](../architecture/testing-by-layer.md) 说明了这些责任。参考项目中的 `ContentBehaviorTest`、`ContentFactoryTest`、`PublishContentCommandContractTest`、`ContentStudioHappyPathHttpSmokeTest`、`ContentStudioPaidPublicationSagaSmokeTest` 和 `MediaProcessingCallbackIntegrationEventSmokeTest` 都可以作为 evidence anchors。

如果一个关键规则只在外层 smoke test 中被间接观察，应把它视为残余风险。authoring 反馈可能不是“补更多 smoke”，而是回到 domain 或 application 增加更直接的 evidence。

## HTTP Examples

HTTP examples 是运行路径证据。参考项目的 [Run The Reference Project](../examples/run-the-reference-project.md) 说明 `.http` 文件如何观察默认内容发布路径和 paid opt-in 路径：

- `http/content.http`
- `http/review.http`
- `http/query.http`
- `http/media-processing.http`
- `http/paid-publication.http`

这些 examples 帮助读者观察外部入口、framework/runtime callback transport wiring、application subscriber reaction、状态返回和 happy path。它们不能替代内层 focused tests，也不能把 HTTP response shape 当成领域模型来源。HTTP evidence 发现的问题，应回到 adapter mapping、application command、application subscriber 或 domain behavior 分别处理。

## Generation Evidence

generation evidence 说明 generator 如何理解输入和 ownership。参考项目中：

- committed evidence：`design/design.json`、`design/value-objects.json`、`design/enums.json`、schema、source、tests、`.http`、`analysis/flows`、`analysis/drawing-board`。
- local generated evidence：运行 README generation 入口后出现的 `build/cap4k/plan.json`。

`build/cap4k/plan.json` 适合审查 generator output ownership。它可以暴露 module placement、template choice、output kind 和 conflict policy 问题。它不应该被解释为业务规则来源，也不应该替代 source 和 tests。

当 plan evidence 和 source 不一致时，反馈路径通常是：

1. 回到 generator input projection，检查 schema、design JSON、manifest 和 Gradle extension。
2. 回到 technical design，检查 module 和 layer boundary。
3. 回到 implementation，检查是否和 generated skeleton ownership 对抗。

## Analysis Evidence

analysis evidence 说明现有代码结构如何连接。参考项目中，已提交的 analysis inspection surfaces 包括：

- `analysis/flows/index.json`
- `analysis/flows/*.json`
- `analysis/flows/*.mmd`
- `analysis/drawing-board/drawing_board_client.json`
- `analysis/drawing-board/drawing_board_command.json`
- `analysis/drawing-board/drawing_board_domain_event.json`
- `analysis/drawing-board/drawing_board_integration_event.json`
- `analysis/drawing-board/drawing_board_query.json`
- `analysis/drawing-board/drawing_board_saga.json`

运行 README analysis 入口后，本地 `build/cap4k/analysis-plan.json` 可作为 analysis generation plan evidence。它和 `build/cap4k/plan.json` 一样，是本地 generated evidence，不是提交源码真相。

analysis flow 可以帮助作者看到 controller、subscriber、job、Saga 和 application flow 的连接方式。若 flow 显示 adapter 直接推进状态、Subscriber 过度分支、polling fallback 产生第二套事实来源，反馈应该回到 technical design 或 implementation surface。

## Feedback Into The Next Spiral

verification 的结论要回写到下一轮 authoring：

- 静态审查发现词义不一致：回到业务意图和建模。
- focused tests 暴露 Aggregate 行为不完整：回到 model 或 domain implementation。
- application tests 暴露 Command 边界过宽：回到 technical design。
- HTTP examples 暴露 payload 和内部语义错位：回到 adapter boundary 或 generator input projection。
- `plan.json` 暴露 output ownership 问题：回到 plan review 和 Gradle/input configuration。
- analysis evidence 暴露 flow 结构错位：回到 technical design、Subscriber、Saga 或 Scheduled Reaction 设计。

好的 verification 不只是给一个通过状态。它应该指出证据来自哪里、保护了什么、还留下什么风险，以及下一轮 authoring 应该修正哪个设计面。

## Public Evidence Discipline

公开文档中要清楚区分证据类型：

- 已提交的 evidence 可以被读者直接在仓库中检查。
- 本地 generated evidence 需要读者在项目本地按 README 入口生成后检查。
- tests 和 HTTP examples 是行为证据，不是 generator input。
- `plan.json` 和 `analysis-plan.json` 是计划或结构证据，不是业务规则来源。
- generated skeleton 是合同，不是完整业务实现。

只要这个区分保持清楚，verification 就能持续服务 authoring 螺旋，而不是变成最后才补的一组检查项。
