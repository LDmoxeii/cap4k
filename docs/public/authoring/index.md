# Authoring

`authoring/` 说明一个人如何设计、生成、实现并继续演进一个 cap4k business slice。它不是技能执行手册，也不是把某个命令列表照顺序跑完的线性清单；它解释的是 public workflow：业务判断如何被写成可追踪输入，生成骨架如何保护结构，手写逻辑如何表达业务含义，验证证据如何把作者带回更早的设计决定。

本章的运行示例是 [Reference Content Studio](../examples/reference-content-studio.md)。这个 sibling repo 展示内容草稿、审核、媒体处理 callback、默认发布路径，以及显式 opt-in 的 paid publication Saga。authoring 页面不会重新讲完整示例代码，而是把这个项目当作共同锚点，说明从业务想法到可审查代码的作者流程。

## Authoring Loop

cap4k authoring 是一个螺旋式循环：

1. 先说清业务意图、用语和边界。
2. 再把边界放进 Aggregate、Value Object、Event、external capability 和 policy。
3. 再设计 Clean Architecture 中的 module、Command、Query、Subscriber、Scheduled Reaction、Saga、adapter 和 persistence 责任。
4. 再把这些设计投影到 schema、`design/design.json`、`design/value-objects.json`、`design/enums.json`、type manifests 和 Gradle extension configuration。
5. 再读 generation plan，确认 ownership、module placement、`templateId` 和 `conflictPolicy`。
6. 再生成骨架，并在预期的 handwritten surface 中实现业务逻辑。
7. 再用静态审查、focused tests、HTTP examples、generation evidence 和 analysis evidence 复核。
8. 任何发现都可以把作者带回业务意图、模型、技术设计或生成输入。

这组动作有推荐阅读顺序，但没有一次通过的承诺。后续证据可能说明一个命令边界过宽、一个 Value Object 命名不准、一个 Saga 其实只是同步反应、一个 adapter 判断混入业务规则，或者一个 plan item 暴露了 ownership 问题。authoring 的目标不是把步骤跑完，而是让这些反馈能回到正确的设计层。

## Pages

- [Spiral Authoring Loop](spiral-authoring-loop.md)：核心页面，说明意图、模型、技术设计、生成输入、计划审查、生成、手写实现、验证和反馈如何循环。
- [Business Intent And Modeling](business-intent-and-modeling.md)：先写业务意图、通用语言、边界、Aggregate、Value Object、Event、external capabilities 和 policies。
- [Technical Design](technical-design.md)：把模型放进 Clean Architecture、module、Command/Query、events、Saga、Subscriber、Scheduled Reaction、adapter、persistence 和 testing 责任。
- [Generator Input Projection](generator-input-projection.md)：把设计投影到 schema、`design/design.json`、`types.enumManifest`、`types.valueObjectManifest`、module layout 和 Gradle extension configuration。
- [Plan Review And Generation](plan-review-and-generation.md)：阅读 `cap4kPlan`、`cap4kBootstrapPlan`、`cap4kGenerate`、`cap4kGenerateSources` 相关证据，判断何时暂停生成。
- [Implementation Inside Generated Skeletons](implementation-inside-generated-skeletons.md)：在生成骨架提供的合同内写复杂业务逻辑，不和 ownership 对抗。
- [Verification And Feedback](verification-and-feedback.md)：把静态审查、focused tests、HTTP examples、generation evidence 和 analysis evidence 变成下一轮 authoring 的反馈。

## Reading Anchors

authoring 章节会频繁回链这些已批准页面：

- examples：[Reference Content Studio](../examples/reference-content-studio.md)、[Run The Reference Project](../examples/run-the-reference-project.md)、[Generation And Analysis Evidence](../examples/generation-and-analysis-evidence.md)
- concepts：[Aggregate](../concepts/modeling-building-blocks/aggregate.md)、[Value Object](../concepts/modeling-building-blocks/value-object.md)、[Command Query Separation](../concepts/execution-and-ownership/command-query-separation.md)、[Generated Skeleton And Handwritten Logic](../concepts/execution-and-ownership/generated-skeleton-and-handwritten-logic.md)
- architecture：[Architecture](../architecture/index.md)、[Application Layer](../architecture/application-layer.md)、[Adapter Layer](../architecture/adapter-layer.md)、[Testing By Layer](../architecture/testing-by-layer.md)

如果读者只想理解一个最小路径，可以先读 [Spiral Authoring Loop](spiral-authoring-loop.md)，再打开 [Reference Content Studio](../examples/reference-content-studio.md) 对照。其余页面用于在某一轮反馈中深入检查具体设计面。
