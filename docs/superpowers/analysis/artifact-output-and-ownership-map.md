# Artifact Output And Ownership Map

## Purpose

本 map 是 cap4k pipeline artifact 输出位置、`ArtifactOutputKind` 语义、source-generation plan roots、analysis output roots 和生成骨架所有权的当前事实索引。它用于帮助维护 agent 在实现前判断哪些文件由 generator/template/input 拥有，哪些文件允许手写业务逻辑。

## Current Facts

- `ArtifactOutputKind` 当前只有 `CHECKED_IN_SOURCE` 和 `GENERATED_SOURCE`。`ArtifactPlanItem.outputKind` 和 `RenderedArtifact.outputKind` 默认都是 `CHECKED_IN_SOURCE`，除非 planner 显式设置。
- `CHECKED_IN_SOURCE` 表示输出到可提交 source tree，默认 Kotlin root 是 `<moduleRoot>/src/main/kotlin`。这些文件会受 `templates.conflictPolicy` 或 plan item conflict policy 约束，默认常见 skeleton 使用 `SKIP`，避免覆盖已存在的手写内容。
- `GENERATED_SOURCE` 表示 build-owned Kotlin source，默认计划 root 是 `<moduleRoot>/build/generated/cap4k/main/kotlin`。`AggregateArtifactOutputs.generatedKotlinArtifact` 显式设置 `outputKind = GENERATED_SOURCE`、`conflictPolicy = OVERWRITE` 和 `resolvedOutputRoot`。
- Gradle 执行时，`rebaseGeneratedSourcePlanItem` 会把计划中的 `<moduleRoot>/build/generated/cap4k/main/kotlin` rebased 到实际 Gradle build directory 下的 root-relative path，并写入 `resolvedOutputRoot`。测试中默认多模块样例会出现 `demo-domain/build/generated/cap4k/main/kotlin`；当 Gradle build dir 被重定向时会出现类似 `demo-domain/out/build/generated/cap4k/main/kotlin` 的路径。
- `cap4kGenerateSources` 使用 `generatedSourceTaskConfig`，只保留 `db`、`enum-manifest` source。它只运行 `aggregate`、`aggregate-projection`，并通过 `FilteringArtifactExporter` 只导出 `GENERATED_SOURCE`。它声明 generated source output directories，并把相关 module 的 `compileKotlin` 依赖接到 `cap4kGenerateSources`。
- `generatedSourceModuleRoles` 当前规则：`aggregate` 会为 `domain`、`adapter` 产生 generated source；当 `aggregate.options["artifact.unique"] == true` 时还会包含 `application`；`aggregate-projection` 会为 `adapter` 产生 generated source；`enum-manifest` source 会为 `domain` 产生 generated source。
- `AggregateArtifactOutputs.checkedInKotlinArtifact` 输出到 `<moduleRoot>/src/main/kotlin`，设置 `outputKind = CHECKED_IN_SOURCE` 和 `resolvedOutputRoot = <moduleRoot>/src/main/kotlin`。aggregate factory、specification、behavior 等需要承载手写逻辑的 skeleton 属于 checked-in source。
- design generators 默认输出 checked-in source：`command`、`query`、`query-handler`、`client`、`client-handler`、`api-payload`、`domain-event`、`domain-subscriber`、`integration-event`、`integration-subscriber`、`domain-service`、`saga` 都走 normal Kotlin source root，除非代码以后显式改变 `outputKind`。
- `ProjectConfig.ArtifactLayoutConfig` 默认 `flow.outputRoot = "flows"`、`drawingBoard.outputRoot = "design"`。`ArtifactLayoutResolver` 会 normalize output roots，并拒绝空值、绝对路径、前后空白和 `..` segment。
- `FlowArtifactPlanner` 的 generator ID 是 `flow`，默认从 `model.analysisGraph` 输出 `flows/<slug>.json`、`flows/<slug>.mmd` 和 `flows/index.json`，template IDs 是 `flow/entry.json.peb`、`flow/entry.mmd.peb`、`flow/index.json.peb`，conflict policy 是 `OVERWRITE`。
- `DrawingBoardArtifactPlanner` 的 generator ID 是 `drawing-board`，默认从 `model.drawingBoard.elementsByTag` 输出 `design/drawing_board_<tag>.json`，template ID 是 `drawing-board/document.json.peb`，conflict policy 是 `OVERWRITE`，supported tags 与当前 design interaction tags 对齐。
- `DefaultPipelineRunner` 对内置 observation outputs 有额外规则：`drawing-board` 和 `flow` 的已知 template IDs 总是 resolve 为 `OVERWRITE`，不受用户 template conflict policy override 影响。
- 所有权规则：cap4k-generated skeleton 的文件形状、包路径、类型名、模板字段和初始结构由 generator inputs/templates 拥有；复杂业务逻辑通常写在生成 skeleton 内部的手写区域或后续人工实现中，除非技术设计明确要求从 generator input/template 产生。
- 绕过 generator-owned skeleton 是设计阶段决策。如果实现时发现必须绕开 generator-owned skeleton，不应直接手写平行结构；应停止实现，回到设计 / generator inputs / template ownership 重新决策。

## Source Anchors

- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt`
- `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolverTest.kt`
- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`
- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactOutputs.kt`
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kGenerateSourcesTask.kt`
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- `cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlanner.kt`
- `cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt`

## Contracts

- Output kind contract: every planner must choose whether an artifact is `CHECKED_IN_SOURCE` or `GENERATED_SOURCE`. Default constructor behavior is checked-in, so generated-source artifacts must be explicit.
- Path root contract: checked-in Kotlin artifacts use `ArtifactLayoutResolver.kotlinSourcePath`; generated Kotlin artifacts use `ArtifactLayoutResolver.generatedKotlinSourcePath`; project resources such as `flow` and `drawing-board` use `projectResourcePath` with validated output roots.
- Gradle source contract: generated source directories must stay under the root project directory after rebasing. `toRootRelativeSlash` enforces this and `registerGeneratedKotlinSourceSets` registers `build/generated/cap4k/main/kotlin` as Kotlin `main` source dir for affected modules.
- Export contract: `cap4kGenerateSources` must export only `GENERATED_SOURCE`; `cap4kGenerate` may export checked-in source and generated source according to the full source-generation plan.
- Observation output contract: `flow` and `drawing-board` are analysis outputs. Their built-in templates are overwrite-by-design observations, not hand-edited source skeletons.
- Ownership contract: files under generated source roots are generator-owned and can be overwritten. Checked-in skeleton files are initially generator-created, then often become user-maintained implementation surfaces under conflict policy protection.

## Change Impact

- Moving a planner from `CHECKED_IN_SOURCE` to `GENERATED_SOURCE` changes compile wiring, Gradle up-to-date inputs/outputs, conflict behavior and handwritten ownership expectations.
- Changing generated source roots affects `cap4kGenerateSources`, plan JSON, source set registration, compile task dependencies and functional tests that assert `build/generated/cap4k/main/kotlin`.
- Changing `flow` or `drawing-board` output roots affects `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` outputs, overwrite behavior, and downstream documentation / visualization agents that consume `flows` or `design` directories.
- Changing default conflict policy for observation outputs can leave stale analysis artifacts in repo-local outputs, because flow and drawing-board are snapshots intended to be regenerated.
- Adding a new generator-owned skeleton surface requires a design-stage ownership decision: whether it is checked-in handwritten surface, build-owned generated source, or analysis-only observation output.

## Verification

PowerShell-safe explicit-directory command for output kind, generated roots, flow, drawing-board and analysis docs:

```powershell
rg -n "CHECKED_IN_SOURCE|GENERATED_SOURCE|build/generated/cap4k/main/kotlin|src-generated|flows|drawing" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-gradle cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-generator-design cap4k-plugin-pipeline-generator-flow cap4k-plugin-pipeline-generator-drawing-board docs/superpowers/analysis
```

Additional focused checks used while maintaining this map:

```powershell
rg -n "outputKind = ArtifactOutputKind|ArtifactOutputKind\.GENERATED_SOURCE|ArtifactOutputKind\.CHECKED_IN_SOURCE" cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-generator-design cap4k-plugin-pipeline-generator-types cap4k-plugin-pipeline-core cap4k-plugin-pipeline-api
```

```powershell
rg -n "sourceTaskConfig|generatedSourceTaskConfig|analysisTaskConfig|SOURCE_TASK_SOURCE_IDS|GENERATED_SOURCE_TASK_SOURCE_IDS|ANALYSIS_TASK_SOURCE_IDS|SOURCE_TASK_GENERATOR_IDS|ANALYSIS_TASK_GENERATOR_IDS" cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle
```

## Drift Watch

- 如果 `ArtifactOutputKind` 增加新值，必须重新定义 ownership、export filtering、conflict policy 和 Gradle source set 语义。
- 如果 `cap4kGenerateSources` 开始包含 `design-json` 或 design generators，必须重新判断 checked-in skeleton 与 generated source 的边界。
- 如果 `flow` 或 `drawing-board` 从 analysis task 进入 ordinary source generation task，必须更新 source/generator map、output ownership 和 public documentation routing。
- 如果 Gradle `buildDirectory` rebase 逻辑改变，必须重新确认 plan root、resolved output root 和 compile task dependency 是否仍匹配。
- 如果模板系统引入可声明手写 protected regions 的机制，应补充 skeleton 内部所有权边界。

## Not Covered

- 不覆盖每个 template 的字段级渲染内容。
- 不覆盖 bootstrap skeleton 输出；本 map 只覆盖 pipeline source / analysis generation artifacts。
- 不覆盖 addon artifact 的业务所有权，只说明它们也会进入 plan/export 语义并受 addon template namespace 校验。