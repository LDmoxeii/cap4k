# Analysis Flow And Verification Map

## Purpose

本页是 cap4k 当前代码分析、IR 输入、analysis task、flow 与 drawing-board 观察输出的维护地图。它帮助维护 agent 判断 `nodes.json` / `rels.json` / `design-elements.json` 从哪里来，`sources.irAnalysis.inputDirs` 如何进入 `cap4kAnalysisPlan` / `cap4kAnalysisGenerate`，以及 flow / drawing-board 与默认业务源码生成路径的区别。

本页不是 public docs。公开教程、用户工作流、截图和概念教育应在 Phase 2 public docs 中展开；这里优先保留代码事实、路径、任务名、source ID、generator ID 和 drift 风险。

## Current Facts

- code-analysis compiler 当前输出三个分析文件：`nodes.json`、`rels.json`、`design-elements.json`。`Cap4kIrGenerationExtension` 通过 `JsonFileMetadataSink` 写 `nodes.json` 与 `rels.json`，随后写 `design-elements.json`。
- 默认 compiler analysis output root 仍是 `build/cap4k-code-analysis`。`Cap4kOptions.outputDir` 默认值是 `Path("build/cap4k-code-analysis")`，`fromSystemProperties()` 没有系统属性时也回退到同一路径；`Cap4kIrGenerationExtension.resolveOutputDir(...)` 会优先基于源码/Gradle 文件推断 module root，然后写到该 module 的 `build/cap4k-code-analysis`。
- compiler option key 仍是 `cap4k.codeanalysis.outputDir`，定义在 `OptionsKeys.OUTPUT_DIR`。同一文件还定义 `cap4k.codeanalysis.scanSpring`、`includeRepoUow`、`mediatorFq`、`unitOfWorkFq`、`repositorySupervisorFq` 等识别选项。
- `IrAnalysisSourceProvider.id` 是 `ir-analysis`。它从 `config.sources["ir-analysis"].options["inputDirs"]` 读取输入目录，要求至少一个目录，并要求每个目录存在 `nodes.json` 与 `rels.json`；`design-elements.json` 是可选存在但会被读取合并的文件。
- Gradle DSL 中 `sources.irAnalysis.inputDirs` 是 analysis 输入选择。`Cap4kProjectConfigFactory.buildSources` 在 `extension.sources.irAnalysis.inputDirs` 非空时创建 source id `ir-analysis`，并把绝对路径排序后放入 `SourceConfig(options = mapOf("inputDirs" to inputDirs))`。
- `cap4kAnalysisPlan` 与 `cap4kAnalysisGenerate` 都调用 `analysisTaskConfig(configFactory.build(project, extension))`。`analysisTaskConfig` 只保留 source id `ir-analysis`，generator id `flow` 与 `drawing-board`。
- `cap4kAnalysisPlan` 输出 `build/cap4k/analysis-plan.json`。`Cap4kAnalysisPlanTask` 用 `buildAnalysisRunner(..., exportEnabled = false)` 生成 plan items 和 diagnostics，然后写 `PlanReport` 到该路径。
- `cap4kAnalysisGenerate` 使用同一个 analysis config，但 `buildAnalysisRunner(..., exportEnabled = true)`，因此会实际写出 flow / drawing-board artifacts。
- flow 与 drawing-board 是 analysis / observation generators，不是默认业务源码生成路径。`PipelinePlugin.buildSourceRunner` 的业务源码生成器列表包含 command/query/client/domain-event/domain-service/saga/integration-event/types/aggregate 等；`buildAnalysisRunner` 只注册 `IrAnalysisSourceProvider`、`DrawingBoardArtifactPlanner`、`FlowArtifactPlanner`。
- pipeline flow generator 的 id 是 `flow`。`FlowArtifactPlanner` 从 `model.analysisGraph` 规划每个 flow 的 JSON 与 Mermaid 文件，以及 `index.json`；输出根来自 `ArtifactLayoutResolver.flowOutputRoot()`。
- pipeline drawing-board generator 的 id 是 `drawing-board`。`DrawingBoardArtifactPlanner` 从 `model.drawingBoard.elementsByTag` 规划 `drawing_board_<tag>.json`，支持 `command`、`query`、`client`、`api_payload`、`domain_event`、`integration_event`、`domain_service`、`saga` tags；输出根来自 `ArtifactLayoutResolver.drawingBoardOutputRoot()`。
- `ArtifactLayoutResolver` 当前默认测试显示 flow output root 是 `flows`，drawing-board output root 是 `design`；可通过 `layout.flow.outputRoot` 与 `layout.drawingBoard.outputRoot` 改变。
- 另有独立 `cap4k-plugin-code-analysis-flow-export` Gradle plugin，plugin id 是 `io.github.ldmoxeii.cap4k.codeanalysis.flow-export`。它注册 `cap4kFlowExport`、`cap4kFlowClean`、`cap4kFlowCompile`、`cap4kFlow`，默认输出 root `flows`，直接读取 input dirs 中的 `nodes.json` / `rels.json` 并导出 flow。它与 pipeline generator `flow` 不是同一路径：前者是独立 flow-export plugin，后者是 `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` 的 analysis generator。
- analysis task dependency inference 基于 `ir-analysis` input dirs。`inferAnalysisDependencies` 会把位于某 project build directory 下的 input dir 映射到该 project 的 `compileKotlin`，并让 `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` 依赖它。

## Source Anchors

- `cap4k-plugin-code-analysis-core/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/core/config/OptionsKeys.kt`
- `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kOptions.kt`
- `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kIrGenerationExtension.kt`
- `cap4k-plugin-pipeline-source-ir-analysis/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProvider.kt`
- `cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlanner.kt`
- `cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowGraphSupport.kt`
- `cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt`
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAnalysisPlanTask.kt`
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAnalysisGenerateTask.kt`
- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt`
- `cap4k-plugin-code-analysis-flow-export/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/flow/Cap4kFlowExportPlugin.kt`
- `cap4k-plugin-pipeline-source-ir-analysis/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProviderTest.kt`
- `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

## Contracts

- Code wins over this map。每次修改 analysis docs、skills 或 public docs 前，应重新检查 compiler、pipeline source、pipeline generator 和 Gradle task source。
- `nodes.json` 与 `rels.json` 是 `ir-analysis` source 的必需输入；`design-elements.json` 当前是可选输入，但 compiler 会输出它。
- `sources.irAnalysis.inputDirs` 是当前 analysis input contract；不要写成 enabled boolean 或旧 KSP-only metadata contract。
- `cap4kAnalysisPlan` 只计划 analysis artifacts，并写 `build/cap4k/analysis-plan.json`；`cap4kAnalysisGenerate` 才导出 analysis artifacts。
- `flow` / `drawing-board` generator 输出是观察与审查材料，不代表默认业务 source generation，也不参与 `cap4kPlan` / `cap4kGenerate` 的 source generator 列表。
- 独立 flow-export plugin 与 pipeline flow generator 必须分开说明：plugin task 是 `cap4kFlowExport` 等，pipeline generator id 是 `flow`。

## Change Impact

- 修改 compiler output filenames 会影响 `IrAnalysisSourceProvider`、flow-export plugin、analysis functional tests、公开分析教程和本页 Verification。
- 修改 `OptionsKeys.OUTPUT_DIR` 或 `Cap4kOptions.outputDir` 会影响编译器参数文档、Gradle sample、flow-export 默认输入推断和 CI 产物收集。
- 修改 `sources.irAnalysis.inputDirs` DSL 会影响 `Cap4kProjectConfigFactory`、`inferAnalysisDependencies`、`cap4kAnalysisPlan` / `cap4kAnalysisGenerate` 和所有 analysis docs。
- 修改 `ANALYSIS_TASK_SOURCE_IDS` 或 `ANALYSIS_TASK_GENERATOR_IDS` 会改变 analysis task 运行内容，必须同步 source/generator contract docs。
- 修改 flow / drawing-board output layout 会影响 generated artifacts、functional tests、public docs 示例和 review automation 读取路径。
- 修改 flow-export plugin 行为时，不要误改 pipeline flow generator 文档；两者只有输入文件格式相似，不是同一个 Gradle 入口。

## Verification

从 cap4k worktree root 运行。PowerShell 下如果未展开的 `cap4k-plugin-*` 通配符导致 `rg` 报路径错误，使用显式目录列表：

```powershell
$codeAnalysisDirs = Get-ChildItem -Directory -Filter 'cap4k-plugin-code-analysis-*' | Select-Object -ExpandProperty Name
rg -n "cap4k.codeanalysis.outputDir|nodes.json|rels.json|design-elements.json|build/cap4k-code-analysis" @codeAnalysisDirs
```

```powershell
$pipelineDirs = Get-ChildItem -Directory -Filter 'cap4k-plugin-pipeline-*' | Select-Object -ExpandProperty Name
rg -n "ir-analysis|flow|drawing-board|analysis-plan.json|inputDirs" @pipelineDirs
```

关键文件复查：

```powershell
Get-Content -Path cap4k-plugin-code-analysis-core/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/core/config/OptionsKeys.kt -Raw
Get-Content -Path cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kIrGenerationExtension.kt -Raw
Get-Content -Path cap4k-plugin-pipeline-source-ir-analysis/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProvider.kt -Raw
Get-Content -Path cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlanner.kt -Raw
Get-Content -Path cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt -Raw
```



## Drift Watch

- 旧的 spaced path 写法不能作为当前事实；当前路径是 `build/cap4k/analysis-plan.json` 与 `build/cap4k-code-analysis`。
- 旧的 flow / drawing-board enabled-switch 说法不能作为当前事实；当前 routing 来自 `analysisTaskConfig` 的 generator id 集合和 `sources.irAnalysis.inputDirs`。
- 如果 `design-elements.json` 从 optional input 变成 required input，更新 `IrAnalysisSourceProvider` contract 与 Verification。
- 如果 `buildAnalysisRunner` 增加业务 source generators，本页必须重新区分 observation output 与 business source generation。
- 如果 flow-export plugin 被删除或并入 pipeline generator，删除独立 plugin 说明并更新 Source Anchors。
- 如果 `inferAnalysisDependencies` 不再基于 input dirs 到 `compileKotlin` 的映射，更新 Gradle dependency contract。

## Not Covered

- 公开用户教程、截图、IDE 配置或完整 Gradle sample。
- `nodes.json` / `rels.json` / `design-elements.json` 的完整 JSON schema。
- `DefaultCanonicalAssembler` 的完整 canonical model 归并逻辑。
- flow Mermaid 样式、drawing-board UI 消费端和人工审查流程。
- 默认 business source generation 的完整 generator catalog；请看 source/generator contract map。