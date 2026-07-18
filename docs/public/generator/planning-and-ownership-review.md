# Planning And Ownership Review

planning and ownership review 是 generation 前必须完成的人工审查。`cap4kPlan`、`cap4kBootstrapPlan` 和它们写出的 plan evidence 说明 generator 准备写什么、写到哪里、谁拥有、遇到已有文件如何处理。它们不说明业务规则已经正确。

source generation plan 的本地产物是 `build/cap4k/plan.json`。bootstrap plan 的本地产物是 `build/cap4k/bootstrap-plan.json`。analysis plan 的本地产物是 `build/cap4k/analysis-plan.json`，它属于 [Analysis Evidence](analysis-evidence.md) 范围。所有这些 `build/` 下的 plan files 都是本地 generated outputs，不是 committed source truth。

## cap4kPlan

`cap4kPlan` 使用 source generation config。它根据 schema、`design/design.json`、`types.valueObjectManifest`、`types.enumManifest`、module layout 和 generator configuration 生成 `build/cap4k/plan.json`。

阅读 `plan.json` 时，重点看每个 item 的：

- `generatorId`
- `templateId`
- `outputKind`
- `resolvedOutputRoot`
- `conflictPolicy`
- `outputPath`
- context 中的 aggregate、building block、module 和 package 信息

这些字段共同回答 ownership 问题。比如 Command skeleton 如果是 `CHECKED_IN_SOURCE`，通常会落到 `<module>/src/main/kotlin`；build-owned generated Kotlin source 如果是 `GENERATED_SOURCE`，通常会落到 `<module>/build/generated/cap4k/main/kotlin`。

## cap4kBootstrapPlan

`cap4kBootstrapPlan` 写出 `build/cap4k/bootstrap-plan.json`。它服务于项目结构 bootstrap，不服务于业务 source generation。

审查 bootstrap plan 时，重点看：

- root project 和 module path。
- domain/application/adapter/start 是否形成四层多模块结构。
- package 是否符合 base package。
- bootstrap 输出是否会碰到已有文件。
- 后续 `cap4kPlan` 能否在这些 module 中正确落位。

如果 bootstrap plan 暴露结构错位，先修正 bootstrap configuration 或手工结构，再进入 source generation。不要用后续生成任务修补错误项目结构。

## Ownership Fields

`generatorId` 表示哪个 generator 计划产出这个 item。它帮助作者区分 aggregate family、design JSON building block、type manifest、analysis flow 或 drawing-board 等来源。

`templateId` 表示使用哪个模板。模板不只是文本差异，它会影响输出 family、slot shape 和 managed sections。

`outputKind` 表示输出归属。常见值包括：

- `CHECKED_IN_SOURCE`：仓库内的稳定 skeleton 或 type source，通常在 `<module>/src/main/kotlin`。
- `GENERATED_SOURCE`：build-owned generated source，通常在 `<module>/build/generated/cap4k/main/kotlin`。
- `OUTPUT_ARTIFACT`：非源码 artifact 的输出类型；内置计划常见项主要使用前两类，具体仍以 plan evidence 为准。

`resolvedOutputRoot` 表示实际输出根。它可以帮助作者检查 source root 是否落在预期 module，而不是只看文件名。

`conflictPolicy` 表示遇到已有文件时如何处理。checked-in skeleton 常用 `SKIP` 保护已有 handwritten logic；build-owned generated source 常用 `OVERWRITE`，因为 build 拥有该 root。

## Generated Vs Handwritten Ownership

plan review 要把输出分成三种不同责任：

- generated structure：generator 负责命名、位置、接口、模板和 wiring shape。
- handwritten logic：作者负责业务判断、状态推进、幂等、补偿、协议转换和异常语义。
- generated source：build 负责维护的 source root，作者不应把它当作长期手写区。

`src/main/kotlin` 不自动等于 handwritten ownership。许多 checked-in skeleton 位于 `src/main/kotlin`，但其中可能有 generator-managed sections 或稳定 slot。是否可以写业务逻辑，要结合 `outputKind`、`templateId`、managed sections 和 `conflictPolicy` 判断。

## Managed Sections

managed sections 是 generator 和作者共享文件时需要审查的边界。作者应确认：

- 哪些部分由模板维护。
- 哪些 slot 预期填入 handwritten logic。
- 再次生成时 `conflictPolicy` 是否保护手写内容。
- 文件已有逻辑是否会被覆盖、跳过或保留。

如果 plan 或输出文件无法让作者判断 managed section，先暂停 generation，查 [Outputs](../reference/outputs.md)、[Plan JSON](../reference/plan-json.md) 和对应 generator documentation。不要在 ownership 不清楚时继续写业务逻辑。

## Review Before Generation

进入 `cap4kGenerate` 或 `cap4kGenerateSources` 前，至少确认：

- `generatorId` 和输入来源能被 schema、design JSON 或 manifest 解释。
- `templateId` 与预期 output family 一致。
- `outputKind` 与预期 ownership 一致。
- `resolvedOutputRoot` 指向正确 module 和 source root。
- `conflictPolicy` 不会覆盖已有 handwritten logic。
- checked-in skeleton、generated source 和 analysis evidence 没有混为一类。

如果发现错位，反馈路径是回到 [Inputs And Sources](inputs-and-sources.md)、[Generator Input Projection](../authoring/generator-input-projection.md) 或 [Technical Design](../authoring/technical-design.md)。generation 前停下来，是 plan evidence 的价值。

<!-- IMAGE_PROMPT:
Purpose: 帮助读者理解 cap4k plan review 如何在 generation 前审查 output ownership、conflictPolicy 和 managed sections。
Type: workflow diagram
Prompt: Draw a cap4k planning and ownership review workflow. Start from explicit inputs, then cap4kBootstrapPlan and cap4kPlan, then bootstrap-plan.json and plan.json, then human review of generatorId, templateId, outputKind, resolvedOutputRoot, conflictPolicy, and managed sections before generation. Use Chinese labels while preserving English identifiers.
Must show: cap4kBootstrapPlan, cap4kPlan, bootstrap-plan.json, plan.json, generatorId, templateId, outputKind, resolvedOutputRoot, conflictPolicy, CHECKED_IN_SOURCE, GENERATED_SOURCE, handwritten logic, managed sections, review before generation
Must avoid: 不要暗示 plan.json 是业务规则来源；不要把 GENERATED_SOURCE 画成手写业务区；不要把 analysis-plan.json 放进 ordinary source generation；不要画出未审查就生成的路径
Alt text after insertion: cap4k plan ownership 审查流程图，展示 bootstrap-plan.json、plan.json、ownership 字段、managed sections 和 generation 前人工审查。
-->
