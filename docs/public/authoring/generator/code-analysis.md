# Code Analysis Guide

> analysis 任务族是观察与导出链路。它帮助你看清流程和设计结果，不替代默认的业务源码生成。

analysis 在项目交付顺序中的位置见 [项目编写工作流](../project-authoring-workflow.md#7-生成-analysis-和流程图)。分析输出解释默认回到 [示例总览](../examples/index.md) 的同一参考项目。

## 何时进入 `cap4kAnalysisPlan`

只有在下面这种需求成立时，才进入 `cap4kAnalysisPlan`：

- 你已经准备好了 `sources.irAnalysis` 输入。
- 你显式启用了 `generators.flow` 或 `generators.drawingBoard`。
- 你要的是 flow / drawing-board 这类观察型产物，而不是命令、查询、聚合或订阅器源码。

`cap4kAnalysisPlan` 的硬边界：

- 只规划 analysis 任务族。
- 只读取 `irAnalysis` 这条输入链路。
- 只写 `build/cap4k/analysis-plan.json`。
- 不负责 `cap4kPlan` 那条主源码生成计划。

## 何时进入 `cap4kAnalysisGenerate`

以下情况才应该跑 `cap4kAnalysisGenerate`：

- `build/cap4k/analysis-plan.json` 已经显示出你真正需要的 `flow` 或 `drawing-board` 产物。
- 你已经明确这次要导出的是观察结果、可视化材料或分析结果。
- 你接受这些产物默认落到 `layout.flow.outputRoot` 或 `layout.drawingBoard.outputRoot` 指向的位置，而不是业务源码模块。

`cap4kAnalysisGenerate` 的硬边界同样很窄：

- 它不替代 `cap4kGenerate`。
- 它不生成默认业务源码。
- 它不负责 bootstrap。

## flow / drawing-board 的作者视角用途

- `flow`：把当前分析输入导出成更适合审阅、沟通和流程观察的材料。
- `drawing-board`：把分析输入导出成另一类设计 / 文档材料，而不是业务真相面。

作者视角里，这两个选项都属于“看清系统”而不是“写出系统”。项目真相仍然回到源码主面、手写主面和 Default Happy Path。

## analysis 不是默认主生成路径

- 默认主路径仍然是 `cap4kPlan` -> `cap4kGenerate`。
- 即使 `flow` / `drawing-board` 在 DSL 中被启用，它们也不应该抢占项目作者的日常生成顺序。
- 如果你的目标是 domain/application/adapter 业务源码，应该回到 `cap4kPlan` / `cap4kGenerate`，而不是从 analysis 入口倒推项目结构。
- analysis 产物可以辅助审阅或后续链路，但不应被当成“真正业务代码的替身”。

## 常见误用

- 用 `cap4kAnalysisPlan` 期待看到命令、查询、聚合、repository 这类主源码计划。
- 把 analysis 输出目录当成项目作者的默认代码落点。
- 没有 `irAnalysis` 输入，却把问题归咎于 `cap4kGenerate` 没帮你产出 flow / drawing-board。
- 先做 analysis 导出，再拿结果倒逼主源码生成边界。

## 最低验证

- 跑 `./gradlew cap4kAnalysisPlan`。
- 检查 `build/cap4k/analysis-plan.json` 是否只包含你预期的 `flow` / `drawing-board` 导出。
- 跑 `./gradlew cap4kAnalysisGenerate`。
- 确认产物落在 `layout.flow.outputRoot` 或 `layout.drawingBoard.outputRoot` 指向的目录，而不是误入业务源码模块。
