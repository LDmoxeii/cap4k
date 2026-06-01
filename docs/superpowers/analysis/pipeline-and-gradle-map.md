# Pipeline And Gradle Map

## 用途

本页是 `cap4k-plugin-pipeline-gradle` 当前 Gradle plugin、task、DSL、source/generator routing 和输出路径的维护地图。它帮助维护 agent 判断 pipeline 行为是否来自当前 Kotlin source，而不是旧文档或旧分析结论。

## 当前事实

- Gradle plugin id 是 `io.github.ldmoxeii.cap4k.pipeline`，定义在 `cap4k-plugin-pipeline-gradle/build.gradle.kts` 的 `gradlePlugin { plugins { ... } }` block 中。
- `PipelinePlugin.apply` 创建 extension `cap4k`，类型是 `Cap4kExtension`。
- `PipelinePlugin.apply` 注册这些 Gradle tasks:
  - `cap4kBootstrapPlan`
  - `cap4kBootstrap`
  - `cap4kPlan`
  - `cap4kGenerate`
  - `cap4kGenerateSources`
  - `cap4kAnalysisPlan`
  - `cap4kAnalysisGenerate`
- 已从 task source 验证的 plan output paths:
  - `Cap4kBootstrapPlanTask` 输出 `build/cap4k/bootstrap-plan.json`。
  - `Cap4kPlanTask` 输出 `build/cap4k/plan.json`。
  - `Cap4kAnalysisPlanTask` 输出 `build/cap4k/analysis-plan.json`。
- `cap4kPlan` 通过 `sourceTaskConfig(configFactory.build(project, extension))` 和 `buildSourceRunner(..., exportEnabled = false)` 构建 source task 配置与 runner。
- `cap4kGenerate` 使用启用 export 的 source pipeline 行为；修改文档前必须先在 `Cap4kGenerateTask.kt` 中核对精确行为。
- `cap4kGenerateSources` 通过 `generatedSourceTaskConfig(...)`、`buildGeneratedSourceRunner(...)`，以及来自 `generatedSourceOutputDirectories(...)` 的输出目录运行。
- `cap4kGenerateSources` 的已生成 Kotlin 根目录，是每个已解析 target module build directory 下的 `build/generated/cap4k/main/kotlin`。`registerGeneratedKotlinSourceSets` 通过 `srcDir` 把该目录加入 Kotlin `main` source set，`wireGeneratedSourceCompilation` 则让 `compileKotlin` 依赖 `cap4kGenerateSources`。
- `PipelinePlugin.kt` 路由的 source IDs:
  - regular source task: `db`, `design-json`, `enum-manifest`, `value-object-manifest`
  - generated source task: `db`, `enum-manifest`
  - analysis task: `ir-analysis`
- `PipelinePlugin.kt` 路由的配置层 generator IDs:
  - regular source task: `command`, `query`, `query-handler`, `client`, `client-handler`, `api-payload`, `domain-event`, `domain-subscriber`, `domain-service`, `saga`, `integration-event`, `integration-subscriber`, `types-value-object`, `aggregate`, `aggregate-projection`
  - generated source task 配置层业务 generator IDs: `aggregate`, `aggregate-projection`
  - analysis task: `flow`, `drawing-board`
- 用于生成 source 的 runner，其实际 planner 安装范围大于配置层 generator ID 过滤范围。`buildSourceRunner(...)` 安装的 built-in planners 包括 `EnumManifestArtifactPlanner`（`id = "enum"`）、`AggregateArtifactPlanner` 和 `AggregateProjectionArtifactPlanner`；因此 `cap4kGenerateSources` 会把 `config.generators` 收窄到 `aggregate` / `aggregate-projection`，但已安装的 `enum` planner 仍可从 `enum-manifest` source input 产出 `GENERATED_SOURCE`。
- `sources.irAnalysis.inputDirs` 驱动 analysis input selection。`Cap4kProjectConfigFactory.buildSources` 只在 `extension.sources.irAnalysis.inputDirs` 非空时创建 source id `ir-analysis`，并把绝对路径排序后存入 option `inputDirs`。
- Analysis task 的依赖推断基于 `ir-analysis` 的 `inputDirs`：`inferAnalysisDependencies` 会把位于某个 project build directory 下的 input dirs 映射到该 project 的 `compileKotlin` task。
- 当前 `Cap4kExtension` 暴露 DSL blocks `project`、`types`、`sources`、`generators`、`templates`、`bootstrap`、`layout` 和 `addons`。
- 当前 generator configuration 为 regular/build source generation 配置了 `aggregate` 和 `aggregateProjection` 两个 generators。`drawingBoard` 和 `flow` 作为 DSL blocks 存在，但当前 analysis task routing 通过 `analysisTaskConfig` generator IDs 选择，而不是通过文档曾描述的 `enabled` 开关。
- 不要为当前 `cap4kPlan` 或 `cap4kGenerate` 行为记录 KSP metadata source contract。当前 regular source tasks 的依赖推断在 `inferSourceDependencies` 中返回 `emptyList()`。

## 来源锚点

- `cap4k-plugin-pipeline-gradle/build.gradle.kts`: plugin id 和 Gradle plugin 声明。
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`: task 注册、task config 分区、source/generator IDs、generated source-set 注册、compile task wiring、依赖推断。
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`: `cap4k { ... }` DSL 形状和可用 blocks/properties。
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`: extension-to-`ProjectConfig` 映射、`sources.irAnalysis.inputDirs`、source IDs、generator IDs、template 和 layout config。
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapPlanTask.kt`: `build/cap4k/bootstrap-plan.json`。
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kPlanTask.kt`: `build/cap4k/plan.json`。
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAnalysisPlanTask.kt`: `build/cap4k/analysis-plan.json`。
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kGenerateSourcesTask.kt`: generated source task inputs、outputs 和 runner 调用。

## 契约

- 以 code 为准。修改 public docs、skills 或 downstream analysis assumptions 前，必须重新核对 Kotlin source。
- `cap4kPlan` 规划 source-task artifacts 并输出 `build/cap4k/plan.json`；`cap4kGenerate` 物化 source-task artifacts。
- `cap4kGenerateSources` 负责由 build 拥有的已生成 Kotlin source 生成。它在 module `build/generated/cap4k/main/kotlin` 下输出 generated source roots，把这些 roots 注册到 Kotlin `main`，并让 `compileKotlin` 依赖该 task。
- `cap4kAnalysisPlan` 和 `cap4kAnalysisGenerate` 消费由 `sources.irAnalysis.inputDirs` 构建的 `ir-analysis` source config，并路由到 `flow` / `drawing-board` planners。
- Analysis source selection 必须记录为 `sources.irAnalysis.inputDirs`，不能记录为 `enabled` boolean。
- Public docs 后续再解释用户 workflow；本地图应保持为维护用的简明事实索引。

## 变更影响

- 添加新的 source provider 时，需要更新 source module code、`PipelinePlugin` source ID sets、`Cap4kProjectConfigFactory.buildSources`、tests 和本地图。
- 添加新的 generator 时，需要更新 generator module code、`PipelinePlugin` generator ID sets、config factory mapping（如果该 generator 有 DSL options）、tests 和本地图。
- 修改 generated source output paths 会影响 source-set 注册、compile task wiring、generated output ownership、tests，以及任何提到 build-owned output 的 public docs。
- 修改 analysis input semantics 会影响 `sources.irAnalysis.inputDirs`、`inferAnalysisDependencies`、`cap4kAnalysisPlan`、`cap4kAnalysisGenerate` 和 downstream flow/drawing-board docs。
- 修改 plan report paths 会影响读取 `build/cap4k/*.json` 的 automation 和 review evidence。

## 验证

在 cap4k worktree root 执行这些命令：

```powershell
rg -n "cap4kBootstrapPlan|cap4kBootstrap|cap4kPlan|cap4kGenerate|cap4kGenerateSources|cap4kAnalysisPlan|cap4kAnalysisGenerate" cap4k-plugin-pipeline-gradle/src/main/kotlin
rg -n "inputDirs|build/generated/cap4k/main/kotlin|analysis-plan.json|plan.json|bootstrap-plan.json" cap4k-plugin-pipeline-gradle/src/main/kotlin
```

其他有用检查：

```powershell
Get-Content -Path cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt -Raw
Get-Content -Path cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt -Raw
Get-Content -Path cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt -Raw
Get-Content -Path cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kGenerateSourcesTask.kt -Raw
```

## 漂移监控

- 旧说法曾描述 `cap4kPlan` / `cap4kGenerate` 依赖 KSP metadata；这对当前代码已过时。只有在 task source 重新接入这类 source contract 时，才能恢复该说法。
- 旧说法曾描述 `cap4kGenerateSources` 接入 KSP，包括 `kspKotlin`；这对当前代码已过时。当前 wiring 是 Kotlin `main` source-set registration 加 `compileKotlin` dependency。
- 旧的 `sources.irAnalysis.enabled` 说法已过时，除非代码重新引入该 property。当前 selection 是 `sources.irAnalysis.inputDirs`。
- 旧的 `generators.flow.enabled` 和 `generators.drawingBoard.enabled` DSL switch 说法已过时，除非代码重新引入这些 properties。当前 `FlowGeneratorExtension` 和 `DrawingBoardGeneratorExtension` 是空 DSL classes。
- 如果 `inferSourceDependencies` 不再返回 `emptyList()`，需要重新核对是否要为 `cap4kPlan`、`cap4kGenerate` 或 generated source tasks 记录新的 task dependencies。
- 如果 generated source consumer task names 扩展到 `compileKotlin` 之外，需要更新 compile wiring contract 和 verification command。

## 未覆盖范围

- 应用 Gradle plugin 的 public user tutorial。
- 完整 template catalog、template IDs 或 rendered artifact examples。
- 超出 task/output anchors 的完整 bootstrap preset slot semantics。
- `ddd-*` modules 内部的 runtime DDD behavior。
- `ir-analysis` pipeline boundary 之外的 code analysis compiler internals。
