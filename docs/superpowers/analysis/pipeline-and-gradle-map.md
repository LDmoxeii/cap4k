# Pipeline And Gradle Map

## Purpose

本页是 `cap4k-plugin-pipeline-gradle` 当前 Gradle plugin、task、DSL、source/generator routing 和输出路径的维护地图。它帮助维护 agent 判断 pipeline 行为是否来自当前 Kotlin source，而不是旧文档或旧分析结论。

## Current Facts

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
- Plan output paths verified from task source:
  - `Cap4kBootstrapPlanTask` writes `build/cap4k/bootstrap-plan.json`.
  - `Cap4kPlanTask` writes `build/cap4k/plan.json`.
  - `Cap4kAnalysisPlanTask` writes `build/cap4k/analysis-plan.json`.
- `cap4kPlan` uses `sourceTaskConfig(configFactory.build(project, extension))` and `buildSourceRunner(..., exportEnabled = false)`.
- `cap4kGenerate` uses source pipeline behavior with export enabled; verify exact behavior in `Cap4kGenerateTask.kt` before changing docs.
- `cap4kGenerateSources` uses `generatedSourceTaskConfig(...)`, `buildGeneratedSourceRunner(...)`, and output directories from `generatedSourceOutputDirectories(...)`.
- `cap4kGenerateSources` generated Kotlin root is `build/generated/cap4k/main/kotlin` under each resolved target module build directory. `registerGeneratedKotlinSourceSets` adds that directory to Kotlin `main` source set via `srcDir`, and `wireGeneratedSourceCompilation` makes `compileKotlin` depend on `cap4kGenerateSources`.
- Source IDs routed by `PipelinePlugin.kt`:
  - regular source task: `db`, `design-json`, `enum-manifest`, `value-object-manifest`
  - generated source task: `db`, `enum-manifest`
  - analysis task: `ir-analysis`
- Generator IDs routed by `PipelinePlugin.kt`:
  - regular source task: `command`, `query`, `query-handler`, `client`, `client-handler`, `api-payload`, `domain-event`, `domain-subscriber`, `domain-service`, `saga`, `integration-event`, `integration-subscriber`, `types-value-object`, `aggregate`, `aggregate-projection`
  - generated source task: `aggregate`, `aggregate-projection`
  - analysis task: `flow`, `drawing-board`
- `sources.irAnalysis.inputDirs` drives analysis input selection. `Cap4kProjectConfigFactory.buildSources` creates source id `ir-analysis` only when `extension.sources.irAnalysis.inputDirs` is not empty, and stores the absolute sorted paths in option `inputDirs`.
- Analysis task dependency inference is based on `ir-analysis` `inputDirs`: `inferAnalysisDependencies` maps input dirs under a project build directory to that project `compileKotlin` task.
- Current `Cap4kExtension` exposes DSL blocks `project`, `types`, `sources`, `generators`, `templates`, `bootstrap`, `layout`, and `addons`.
- Current generator configuration has `aggregate` and `aggregateProjection` as configured generators for regular/build source generation. `drawingBoard` and `flow` exist as DSL blocks, but current analysis task routing is selected through `analysisTaskConfig` generator IDs rather than documented `enabled` switches.
- Do not document a KSP metadata source contract for current `cap4kPlan` or `cap4kGenerate` behavior. Current dependency inference for regular source tasks returns `emptyList()` in `inferSourceDependencies`.

## Source Anchors

- `cap4k-plugin-pipeline-gradle/build.gradle.kts`: plugin id and Gradle plugin declaration.
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`: task registration, task config partitioning, source/generator IDs, generated source-set registration, compile task wiring, dependency inference.
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`: `cap4k { ... }` DSL shape and available blocks/properties.
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`: extension-to-`ProjectConfig` mapping, `sources.irAnalysis.inputDirs`, source IDs, generator IDs, template and layout config.
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapPlanTask.kt`: `build/cap4k/bootstrap-plan.json`.
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kPlanTask.kt`: `build/cap4k/plan.json`.
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAnalysisPlanTask.kt`: `build/cap4k/analysis-plan.json`.
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kGenerateSourcesTask.kt`: generated source task inputs, outputs, and runner invocation.

## Contracts

- Code wins over this map. Re-check Kotlin source before changing public docs, skills, or downstream analysis assumptions.
- `cap4kPlan` plans source-task artifacts and writes `build/cap4k/plan.json`; `cap4kGenerate` materializes source-task artifacts.
- `cap4kGenerateSources` is build-owned generated Kotlin source generation. It writes generated source roots under module `build/generated/cap4k/main/kotlin`, registers those roots with Kotlin `main`, and wires `compileKotlin` to depend on the task.
- `cap4kAnalysisPlan` and `cap4kAnalysisGenerate` consume `ir-analysis` source config built from `sources.irAnalysis.inputDirs` and route to `flow` / `drawing-board` planners.
- Analysis source selection must be documented as `sources.irAnalysis.inputDirs`, not as an `enabled` boolean.
- Public docs should explain user workflows later; this map should stay a terse fact index for maintenance.

## Change Impact

- Adding a new source provider requires updates to source module code, `PipelinePlugin` source ID sets, `Cap4kProjectConfigFactory.buildSources`, tests, and this map.
- Adding a new generator requires updates to generator module code, `PipelinePlugin` generator ID sets, config factory mapping if it has DSL options, tests, and this map.
- Changing generated source output paths affects source-set registration, compile task wiring, generated output ownership, tests, and any public docs that mention build-owned output.
- Changing analysis input semantics affects `sources.irAnalysis.inputDirs`, `inferAnalysisDependencies`, `cap4kAnalysisPlan`, `cap4kAnalysisGenerate`, and downstream flow/drawing-board docs.
- Changing plan report paths affects automation and review evidence that reads `build/cap4k/*.json`.

## Verification

Run these commands from the cap4k worktree root:

```powershell
rg -n "cap4kBootstrapPlan|cap4kBootstrap|cap4kPlan|cap4kGenerate|cap4kGenerateSources|cap4kAnalysisPlan|cap4kAnalysisGenerate" cap4k-plugin-pipeline-gradle/src/main/kotlin
rg -n "inputDirs|build/generated/cap4k/main/kotlin|analysis-plan.json|plan.json|bootstrap-plan.json" cap4k-plugin-pipeline-gradle/src/main/kotlin
```

Additional useful checks:

```powershell
Get-Content -Path cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt -Raw
Get-Content -Path cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt -Raw
Get-Content -Path cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt -Raw
Get-Content -Path cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kGenerateSourcesTask.kt -Raw
```

## Drift Watch

- Old wording that `cap4kPlan` / `cap4kGenerate` depend on KSP metadata is stale for current code. Reintroduce only if task source again wires such a source contract.
- Old wording that `cap4kGenerateSources` is wired into KSP, including `kspKotlin`, is stale for current code. Current wiring is Kotlin `main` source-set registration plus `compileKotlin` dependency.
- Old `sources.irAnalysis.enabled` wording is stale unless code reintroduces that property. Current selection is `sources.irAnalysis.inputDirs`.
- Old `generators.flow.enabled` and `generators.drawingBoard.enabled` DSL switch wording is stale unless code reintroduces those properties. Current `FlowGeneratorExtension` and `DrawingBoardGeneratorExtension` are empty DSL classes.
- If `inferSourceDependencies` stops returning `emptyList()`, re-check whether new task dependencies need to be documented for `cap4kPlan`, `cap4kGenerate`, or generated source tasks.
- If generated source consumer task names expand beyond `compileKotlin`, update the compile wiring contract and verification command.

## Not Covered

- Public user tutorial for applying the Gradle plugin.
- Full template catalog, template IDs, or rendered artifact examples.
- Full bootstrap preset slot semantics beyond task/output anchors.
- Runtime DDD behavior inside `ddd-*` modules.
- Code analysis compiler internals outside the `ir-analysis` pipeline boundary.