# Plan Review And Generation

plan review 是进入 generation 前的 ownership 审查。作者已经有了业务意图、模型、技术设计和 generator inputs，但还不能直接假设输出会安全落位。`cap4kPlan`、`cap4kBootstrapPlan`、`cap4kGenerate` 和 `cap4kGenerateSources` 这些任务名背后，真正需要阅读的是 plan evidence、ownership、module placement、template choice 和 conflict behavior。

[Generation And Analysis Evidence](../examples/generation-and-analysis-evidence.md) 说明了参考项目中的 evidence surfaces。已提交的 inspection surfaces 是 design inputs、schema、source、tests、`.http`、`analysis/flows` 和 `analysis/drawing-board`；`build/cap4k/plan.json` 与 `build/cap4k/analysis-plan.json` 是运行 README 中 generation / analysis 入口后的本地 generated evidence。

## cap4kPlan

`cap4kPlan` 的核心产物是本地 `build/cap4k/plan.json`。它不替代 source truth，但它是生成前最重要的 ownership 审查文件。

阅读 plan 时，重点看每个 item 的：

- `outputPath`
- `generatorId`
- `templateId`
- `moduleRole`
- `outputKind`
- `conflictPolicy`
- context 中的 building-block 信息

这些字段回答的是“generator 计划写什么、写到哪里、用哪个模板、归属哪一层、遇到已有文件如何处理”。它们不回答“业务规则是否正确”。业务规则仍要回到 model、technical design、source 和 tests。

## cap4kBootstrapPlan

`cap4kBootstrapPlan` 面向项目结构 bootstrap 的计划审查。它适合在项目初始结构或模块布局需要由 bootstrap 参与时使用，用来阅读 bootstrap 将如何创建或保护 root、module、package 和 template output。

可以把 `cap4kBootstrap` 理解为 paired bootstrap action：plan 先展示 bootstrap 打算做什么，bootstrap action 再应用结构输出。但 authoring 章节不把 bootstrap 作为默认学习入口。对于已经存在的 reference project，读者通常先从 [Reference Content Studio](../examples/reference-content-studio.md)、design inputs、schema、source 和 `cap4kPlan` 进入。

## cap4kGenerate

`cap4kGenerate` 根据已经审查过的输入和计划应用 generation。它可以写出或更新 checked-in skeleton、adapter surface、application entry、domain type、payload、client-handler、repository adapter 等输出，具体由 generator configuration、template 和 `conflictPolicy` 决定。

进入 `cap4kGenerate` 前，作者应确认：

- plan 中的 module placement 和 Clean Architecture 一致。
- `templateId` 与预期 output family 一致。
- `conflictPolicy` 不会覆盖应该保留的 handwritten logic。
- checked-in skeleton 与 build-owned generated source 的 ownership 已经分清。
- plan 没有暴露出输入命名、package 或 boundary 的明显错位。

generation 的作用是维护结构和合同，不是完成业务实现。生成后仍要在 intended handwritten surface 中实现业务判断。

## cap4kGenerateSources

`cap4kGenerateSources` 表达 build-owned generated source 面。它适合被理解为构建期生成源输出，而不是长期手写业务逻辑入口。public authoring 不应把它写成“提交 generated snapshots 后再读”的学习路径。

参考项目 README 已经把主要 generation ownership 入口收敛到 `cap4kPlan`、`cap4kGenerate` 和本地 `build/cap4k/plan.json`。如果某个项目仍然使用 `cap4kGenerateSources`，审查重点是确认这些生成源不被当成手写业务代码区，也不被当成已提交的业务判断证据。

## Ownership Review

plan review 要把 output 分成几类：

- checked-in skeletons：例如 Command、Query、Subscriber、client、client-handler、Saga、API Payload 或 adapter surface，它们可以作为仓库中的稳定入口。
- build-owned generated source：构建期维护的输出，不应成为手写业务规则位置。
- handwritten logic locations：作者在 skeleton 暴露的 surface 中维护业务判断、状态推进、补偿、幂等和协议转换。
- evidence files：`plan.json`、analysis plan、flow output、drawing-board output，用来审查结构和 ownership。

这类区分直接连接 [Generated Skeleton And Handwritten Logic](../concepts/execution-and-ownership/generated-skeleton-and-handwritten-logic.md)。如果 ownership 不清楚，generation 后就容易出现两类坏结果：手写代码被覆盖，或者作者开始和生成骨架对抗。

## When To Stop Before Generation

这些情况应该先暂停 generation，回到前面的 authoring 圈：

- `outputPath` 落在错误 module 或错误 layer。
- `templateId` 和作者预期的 family 不一致。
- `conflictPolicy` 会覆盖已有 handwritten logic。
- plan item 对应的业务概念在 `design/design.json` 或 schema 中无法解释。
- Command/Query、Event 或 Saga 命名暴露出模型混乱。
- Value Object、enum 或 DB marker 和业务语义不一致。
- plan 中出现大量作者无法解释的输出。

暂停不是流程中断，而是 plan evidence 正在纠偏。先修正 intent、model、technical design 或 generator inputs，再回到 plan review。

## After Generation

generation 后的阅读顺序仍然是螺旋式的：

1. 对照 plan 看输出是否符合 ownership。
2. 对照 source 看 generated skeleton 和 handwritten logic 是否分开。
3. 对照 tests、`.http` examples、analysis outputs 看行为和结构是否一致。
4. 把发现反馈到更早的 design surface。

不要把“文件已经生成”解释成“业务已经实现”。生成只说明结构到位；业务正确性需要 handwritten implementation 和 verification evidence 共同证明。
