# Analysis Evidence

analysis evidence 用来观察已经存在的代码结构。它帮助作者检查 controller、subscriber、job、Saga、Command dispatch、Query path、event reaction 和 external capability wiring 是否和设计一致。它不是 ordinary source generation，也不产出 source skeleton。

`cap4kAnalysisPlan` 写出本地 `build/cap4k/analysis-plan.json`。`cap4kAnalysisGenerate` 根据 analysis plan 产出 flow 和 drawing-board evidence。与 `build/cap4k/plan.json` 一样，`build/cap4k/analysis-plan.json` 是本地 generated output，不是 committed source truth。

## Analysis Inputs

analysis generation 使用 `sources.irAnalysis.inputDirs` 选择 IR analysis input。Compiler analysis output root 是：

```text
build/cap4k-code-analysis
```

必要 input files 是：

- `nodes.json`
- `rels.json`

`design-elements.json` 是可选 input，但 compiler analysis 会输出它。analysis generation 使用 source id `ir-analysis`，并 route 到 generator ids：

- `flow`
- `drawing-board`

这些 input 描述的是代码结构关系。它们不替代 schema、`design/design.json`、`types.valueObjectManifest` 或 `types.enumManifest`。

## cap4kAnalysisPlan

`cap4kAnalysisPlan` 读取 IR analysis input，写出 `build/cap4k/analysis-plan.json`。它说明 analysis generator 将如何生成 flow 与 drawing-board outputs。

阅读 analysis plan 时，重点看：

- input dirs 是否指向预期 IR analysis output。
- `flow` 和 `drawing-board` generator ids 是否覆盖预期 evidence。
- 输出位置是否和项目的 analysis evidence surface 一致。
- plan item 是否被误解成 source generation item。

analysis plan 的价值是让 observation output 可审查。它不说明业务正确，也不应该被用来反推业务模型。

## cap4kAnalysisGenerate

`cap4kAnalysisGenerate` 生成 analysis/observation outputs。Public docs 主要讨论两类：

- flows
- drawing-board

这些 outputs 适合提交成 reference project evidence，帮助读者不运行应用也能阅读结构连接。在 [Generation And Analysis Evidence](../examples/generation-and-analysis-evidence.md) 中，reference project committed evidence 使用：

- `analysis/flows`
- `analysis/drawing-board`

本地 `build/cap4k/*` plan outputs 只有在运行相关任务后才会出现。

## Flows

`analysis/flows` 展示结构化 execution path。常见文件包括：

- `analysis/flows/index.json`
- `analysis/flows/*.json`
- `analysis/flows/*.mmd`

flow evidence 可以帮助作者检查：

- HTTP controller 是否委托到正确 Command 或 Query。
- inbound integration event subscriber 是否只做边界转换和后续委托。
- domain event subscriber 是否没有把复杂业务规则藏在错误层。
- job 或 scheduled reaction 是否表达恢复、轮询或时间触发路径。
- Saga path 是否保持持久化协调和补偿边界。

flow evidence 只能说明代码连接方式。连接存在不代表业务规则正确；连接错位则应反馈到 technical design 或 implementation。

## Drawing Board

`analysis/drawing-board` 按 building block 分类展示项目中已经存在的结构锚点。reference project 中常见文件包括：

- `analysis/drawing-board/drawing_board_client.json`
- `analysis/drawing-board/drawing_board_command.json`
- `analysis/drawing-board/drawing_board_domain_event.json`
- `analysis/drawing-board/drawing_board_integration_event.json`
- `analysis/drawing-board/drawing_board_query.json`
- `analysis/drawing-board/drawing_board_saga.json`

drawing-board evidence 适合回答：“项目里有哪些 Command、Query、client、event 和 Saga 锚点？”它不回答这些锚点是否完成业务实现。

## Verification Usage

在 verification 中使用 analysis evidence，可以按这个顺序：

1. 先用 design inputs 和 source 确认作者本来想表达什么。
2. 再用 `cap4kAnalysisPlan` 的 `analysis-plan.json` 确认 observation output 将如何生成。
3. 再读 `analysis/flows`，看 runtime-adjacent path 是否经过预期 controller、subscriber、job、Saga 和 application use case。
4. 再读 `analysis/drawing-board`，确认 Command、Query、event、client 和 Saga 锚点是否和 design JSON/source 对齐。
5. 最后把发现反馈到 [Verification And Feedback](../authoring/verification-and-feedback.md)、[Technical Design](../authoring/technical-design.md) 或 [Implementation Inside Generated Skeletons](../authoring/implementation-inside-generated-skeletons.md)。

常见反馈包括：

- controller flow 绕过 Command，说明 adapter 和 application 边界需要修正。
- subscriber flow 直接堆叠复杂状态判断，说明应回到 Command、Domain Service 或 Saga。
- drawing-board 中缺少预期 Command/Query，说明 design input 或 skeleton 落位可能不完整。
- flow 中出现第二套事实来源，说明 external capability 或 polling fallback 需要重新审查。

## Evidence Boundary

analysis evidence 是观察证据，不是 source truth。public docs 中要保持这些边界：

- `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` 不参与 ordinary `cap4kPlan` / `cap4kGenerate` source generation。
- `flow` 和 `drawing-board` 是 analysis/observation outputs，不是 business source skeletons。
- `analysis/flows` 和 `analysis/drawing-board` 可以作为已提交 reference evidence。
- `build/cap4k/analysis-plan.json` 需要本地运行 analysis plan task 后才出现。
- 业务正确性仍需 source、tests、HTTP examples、generation plan 和 human review 共同证明。

analysis evidence 的价值，是让 authoring loop 在验证阶段看见结构反馈，而不是把代码连接图误当成业务设计本身。

<!-- IMAGE_PROMPT:
Purpose: 帮助读者理解 cap4k analysis evidence 如何从 IR analysis input 生成 flows 与 drawing-board，并在 verification 中反馈 authoring loop。
Type: workflow diagram
Prompt: Draw a cap4k analysis evidence workflow. Start with build/cap4k-code-analysis containing nodes.json and rels.json, selected by sources.irAnalysis.inputDirs. Show cap4kAnalysisPlan producing build/cap4k/analysis-plan.json, then cap4kAnalysisGenerate producing analysis/flows and analysis/drawing-board. Then show verification feedback to technical design and implementation. Use Chinese labels while preserving English identifiers.
Must show: sources.irAnalysis.inputDirs, build/cap4k-code-analysis, nodes.json, rels.json, design-elements.json as optional input, cap4kAnalysisPlan, analysis-plan.json, cap4kAnalysisGenerate, flow, drawing-board, analysis/flows, analysis/drawing-board, verification feedback
Must avoid: 不要暗示 analysis outputs 是 source skeletons；不要把 flow/drawing-board 放进 cap4kPlan/cap4kGenerate；不要暗示 analysis evidence 会证明业务规则自动正确
Alt text after insertion: cap4k analysis evidence 工作流，展示 IR input、cap4kAnalysisPlan、analysis-plan.json、cap4kAnalysisGenerate、flows、drawing-board 和 verification feedback。
-->
