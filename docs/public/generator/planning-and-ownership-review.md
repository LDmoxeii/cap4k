# Planning And Ownership Review

planning and ownership review 是 generation 前必须完成的人工审查。`cap4kPlan` 与 `cap4kAnalysisPlan` 写出的 plan evidence 说明 generator 准备写什么、写到哪里、谁拥有、遇到已有文件如何处理。它们不说明业务规则已经正确。

source generation plan 的本地产物是 `build/cap4k/plan.json`。analysis plan 的本地产物是 `build/cap4k/analysis-plan.json`，它属于 [Analysis Evidence](analysis-evidence.md) 范围。这些 `build/` 下的 plan files 都是本地 generated outputs，不是 committed source truth。

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

## Ownership Fields

`generatorId` 表示哪个 generator 计划产出这个 item。它帮助作者区分 aggregate family、design JSON building block、type manifest、analysis flow 或 drawing-board 等来源。

`templateId` 表示首次 materialization 使用哪个模板。模板会影响输出 family 和初始 slot shape；checked-in file 生成后不再由模板同步维护。

`outputKind` 表示输出归属。常见值包括：

- `CHECKED_IN_SOURCE`：仓库内的稳定 skeleton 或 type source，通常在 `<module>/src/main/kotlin`。
- `GENERATED_SOURCE`：build-owned generated source，通常在 `<module>/build/generated/cap4k/main/kotlin`。
- `OUTPUT_ARTIFACT`：非源码 evidence output；内置 flow 与 drawing-board planner 使用该类型。

`resolvedOutputRoot` 表示实际输出根。它可以帮助作者检查 source root 是否落在预期 module，而不是只看文件名。

`conflictPolicy` 表示遇到已有文件时如何处理。checked-in skeleton 固定使用 `SKIP`，只承诺第一次 materialization；build-owned generated source 通常使用 `OVERWRITE`，因为 build 拥有该 root。

## Generated Vs Handwritten Ownership

plan review 要把输出分成三种不同责任：

- generated structure：generator 负责命名、位置、接口、模板和 wiring shape。
- handwritten logic：作者负责业务判断、状态推进、幂等、补偿、协议转换和异常语义。
- generated source：build 负责维护的 source root，作者不应把它当作长期手写区。

`src/main/kotlin` 不自动等于某一种业务职责，但 `CHECKED_IN_SOURCE` materialize 后按普通提交源码维护。是否适合写业务逻辑，要结合 `outputKind`、`templateId` 和 building-block 责任判断；generator 不再对其中任何 section 承诺刷新。

## Checked-In Refresh Boundary

checked-in source 不是 generator 与作者长期共享维护的文件。作者应确认：

- 初始 template 是否提供了合适的 handwritten surface。
- plan 中 `outputKind = CHECKED_IN_SOURCE` 且 `conflictPolicy = SKIP`。
- 后续 template 演进不会自动进入已提交文件。
- 如果确实需要重建，先用版本控制保护当前实现，再删除、materialize 和审查差异。

如果 plan 或输出文件无法让作者判断 ownership，先暂停 generation，查 [Outputs](../reference/outputs.md)、[Plan JSON](../reference/plan-json.md) 和对应 generator documentation。不要在 ownership 不清楚时继续写业务逻辑。

## Review Before Generation

进入 `cap4kGenerate` 或 `cap4kGenerateSources` 前，至少确认：

- `generatorId` 和输入来源能被 schema、design JSON 或 manifest 解释。
- `templateId` 与预期 output family 一致。
- `outputKind` 与预期 ownership 一致。
- `resolvedOutputRoot` 指向正确 module 和 source root。
- `conflictPolicy` 不会覆盖已有 handwritten logic。
- checked-in skeleton、generated source 和 analysis evidence 没有混为一类。

如果发现错位，反馈路径是回到 [Inputs And Sources](inputs-and-sources.md)、[Generator Input Projection](../authoring/generator-input-projection.md) 或 [Technical Design](../authoring/technical-design.md)。generation 前停下来，是 plan evidence 的价值。
