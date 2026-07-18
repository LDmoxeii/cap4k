# Spiral Authoring Loop

cap4k authoring 的核心不是“先建模、再生成、最后测试”的单向流程，而是一个证据纠偏的螺旋。作者先把业务意图变成模型和技术设计，再把设计投影到 generator 能消费的输入；生成骨架提供稳定合同，手写逻辑表达业务判断；验证证据再把问题带回前面的任何一层。

[Reference Content Studio](../examples/reference-content-studio.md) 是本章共同示例。默认内容发布路径里，`Content`、`MediaProcessingTask`、HTTP integration-event callback、`ContentPublicationReadyDomainEvent` 和 `PublishContentCmd` 共同展示了这条螺旋：业务上先确认“审核通过并且媒体就绪后才能发布”，模型上区分内容生命周期和媒体处理任务，技术设计上拆出 Command、Subscriber 和 external capability，生成输入再表达 command、event、query、client、schema 和 type manifests。

## The Spiral

一轮 authoring 通常会经过这些面：

1. `intent`：说明业务要解决什么问题，哪些词必须被统一，哪些行为会改变事实。
2. `model`：决定 Aggregate、Entity、Value Object、Domain Event、Integration Event、Domain Service、Saga 和 external capability 的边界。
3. `technical design`：决定 module、Command、Query、Subscriber、Scheduled Reaction、adapter、persistence 和 testing responsibility。
4. `generator inputs`：把设计投影到 schema、`design/design.json`、`design/value-objects.json`、`design/enums.json`、`types.enumManifest`、`types.valueObjectManifest` 和 Gradle extension configuration。
5. `plan review`：阅读本地 `build/cap4k/plan.json` 或 bootstrap plan，确认 output path、`moduleRole`、`templateId`、`generatorId`、`outputKind` 和 `conflictPolicy`。
6. `generation`：让 generator 写出或更新稳定骨架，保持命名、目录、接口和 wiring 一致。
7. `handwritten implementation`：在生成骨架暴露的 intended surface 中写业务判断、状态推进、幂等、补偿、协议转换和错误处理。
8. `verification`：用静态审查、focused tests、HTTP examples、generation evidence 和 analysis evidence 检查设计与实现是否一致。
9. `feedback`：把发现的问题回填到业务意图、模型、技术设计、输入投影或手写实现。

这些面有自然顺序，但它们不是瀑布。比如 `plan.json` 显示 `PublishContentCmd` 落错模块时，问题可能在 technical design 或 generator inputs；`ContentBehaviorTest` 暴露发布条件不清时，问题可能在业务意图或 Aggregate 边界；HTTP callback smoke path 说明 adapter 偷做状态判断时，问题可能在 technical design 的 adapter boundary。

## Later Evidence Can Move Backward

authoring 里的“往回走”不是返工失败，而是证据起作用。cap4k 的结构优势在于每个证据面都能指向一个较明确的修正位置。

- 业务词不稳定：回到业务意图和 ubiquitous terms，不要急着改 `design/design.json`。
- 一个命令同时做审核、媒体处理和发布：回到 Command/Query boundary 与 application orchestration。
- 一个 Value Object 只是 DTO 换名：回到值语义，确认它是否不可变、按值相等、被 Aggregate 持有。
- Saga 只是在同一事务中顺手调用下一步：回到技术设计，判断它是否真的需要 persisted coordination、retry、recovery 或 compensation。
- 生成骨架没有合适入口：回到 generator input projection，而不是直接在随机类里补业务逻辑。
- `conflictPolicy` 会覆盖手写代码：先停在 plan review，不进入 generation。
- analysis flow 显示 adapter 直接推进业务状态：回到 adapter boundary 与 application entry。

这就是螺旋的实际价值：后续证据不会只生成“通过/不通过”的结论，它会告诉作者该回到哪一个设计层。

## Human Judgment Remains Central

generator 只消费已经表达出来的事实，并输出结构、命名、入口和 ownership 证据。它不会自动判断业务边界，不会替团队决定 `Content` 何时可发布，也不会自动推导 paid publication 的补偿语义。AI 也不能替代这类判断。authoring 的公开流程应该让人类业务判断更可审计，而不是把它藏进生成或自动化叙述里。

在 [Reference Content Studio](../examples/reference-content-studio.md) 中，`design/design.json` 描述 `command`、`query`、`domain_event`、`integration_event`、`client`、`saga` 等输入；schema 描述聚合表、字段类型和引用；type manifests 描述 `ReleasePolicy`、`MediaProcessingResultStatus` 和 `MediaProcessingResultSnapshot`。这些输入共同服务于骨架生成，但发布规则、media-ready 判断、Saga 补偿、no-op 和幂等仍然在手写逻辑与测试证据中被表达。

## Reading The Loop With Evidence

可以把每个 evidence surface 读成一个问题：

- business notes 和 example narrative：业务意图是否一致。
- concepts 链接：该模型是否使用了合适 building block。
- architecture 链接：代码责任是否落在正确层。
- design inputs 和 schema：generator 是否获得了正确事实。
- `build/cap4k/plan.json`：本地生成计划是否保护 ownership。
- Kotlin source：生成骨架与手写逻辑是否清楚分工。
- tests 和 `.http` examples：行为与运行路径是否可观察。
- `analysis/flows`、`analysis/drawing-board` 和本地 `build/cap4k/analysis-plan.json`：结构证据是否和设计叙述一致。

每次读到不一致，都把它当成下一轮 authoring 的入口，而不是在这一层硬补。

<!-- IMAGE_PROMPT:
Purpose: 帮助读者理解 cap4k authoring 是从业务意图到验证反馈的螺旋式证据纠偏过程。
Type: workflow diagram
Prompt: Draw a spiral workflow diagram for cap4k authoring. Use Chinese labels while preserving English identifiers. Show the spiral moving through 业务意图 intent, 模型 model, 技术设计 technical design, generator inputs, plan review, generation, handwritten implementation, verification, and feedback. Show feedback arrows that can return to earlier decisions. Show generator as a structure and ownership tool, not as business judgment.
Must show: 螺旋循环, feedback arrows back to earlier decisions, human business judgment, design/design.json, types.enumManifest, types.valueObjectManifest, build/cap4k/plan.json, 生成骨架, 手写逻辑, 验证证据
Must avoid: one-way waterfall, implying generator or AI replaces human business judgment, showing business rules created automatically, arrows that skip Clean Architecture boundaries
Alt text after insertion: cap4k authoring 螺旋流程图，展示业务意图、模型、技术设计、生成输入、计划审查、生成骨架、手写实现、验证和反馈如何循环纠偏。
-->
