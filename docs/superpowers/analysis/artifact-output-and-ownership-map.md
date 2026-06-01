# Artifact Output And Ownership Map

## Purpose

本 map 是 cap4k pipeline artifact 输出位置、`ArtifactOutputKind` 语义、source-generation plan roots、analysis output roots 和生成骨架所有权的当前事实索引。它用于帮助维护 agent 在实现前判断哪些文件由 generator/template/input 拥有，哪些文件允许手写业务逻辑。

## Current Facts

- `ArtifactOutputKind` 当前只有 `CHECKED_IN_SOURCE` 和 `GENERATED_SOURCE`。`ArtifactPlanItem.outputKind` 和 `RenderedArtifact.outputKind` 默认都是 `CHECKED_IN_SOURCE`，除非 planner 显式设置。
- `CHECKED_IN_SOURCE` 表示输出到可提交 source tree，默认 Kotlin root 是 `<moduleRoot>/src/main/kotlin`。这些文件会受 `templates.conflictPolicy` 或 plan item conflict policy 约束，默认常见 skeleton 使用 `SKIP`，避免覆盖已存在的手写内容。
- `GENERATED_SOURCE` 表示 build-owned Kotlin source，默认计划 root 是 `<moduleRoot>/build/generated/cap4k/main/kotlin`。`AggregateArtifactOutputs.generatedKotlinArtifact` 显式设置 `outputKind = GENERATED_SOURCE`、`conflictPolicy = OVERWRITE` 和 `resolvedOutputRoot`。
- Gradle 执行时，`rebaseGeneratedSourcePlanItem` 会把计划中的 `<moduleRoot>/build/generated/cap4k/main/kotlin` rebased 到实际 Gradle build directory 下的 root-relative path，并写入 `resolvedOutputRoot`。测试中默认多模块样例会出现 `demo-domain/build/generated/cap4k/main/kotlin`；当 Gradle build dir 被重定向时会出现类似 `demo-domain/out/build/generated/cap4k/main/kotlin` 的路径。
- `cap4kGenerateSources` 使用 `generatedSourceTaskConfig`，只保留 `db`、`enum-manifest` source。它只运行 `aggregate`、`aggregate-projection`，并通过 filtering exporter 只导出 `GENERATED_SOURCE`。它声明 generated source output directories，并把相关 module 的 `compileKotlin` 依赖接到 `cap4kGenerateSources`。
- `generatedSourceModuleRoles` 当前规则：`aggregate` 会为 `domain`、`adapter` 产生 generated source；当 `aggregate.options["artifact.unique"] == true` 时还会包含 `application`；`aggregate-projection` 会为 `adapter` 产生 generated source；`enum-manifest` source 会为 `domain` 产生 generated source。
- `AggregateArtifactOutputs.checkedInKotlinArtifact` 输出到 `<moduleRoot>/src/main/kotlin`，设置 `outputKind = CHECKED_IN_SOURCE` 和 `resolvedOutputRoot = <moduleRoot>/src/main/kotlin`。aggregate factory、specification、behavior 等需要承载手写逻辑的 skeleton 属于 checked-in source。
- design generators 默认输出 checked-in source：`command`、`query`、`query-handler`、`client`、`client-handler`、`api-payload`、`domain-event`、`domain-subscriber`、`integration-event`、`integration-subscriber`、`domain-service`、`saga` 都走 normal Kotlin source root，除非代码以后显式改变 `outputKind`。
- `ProjectConfig.ArtifactLayoutConfig` 默认 `flow.outputRoot = "flows"`、`drawingBoard.outputRoot = "design"`。`ArtifactLayoutResolver` 会 normalize output roots，并拒绝空值、绝对路径、前后空白和 `..` segment。
- `FlowArtifactPlanner` 的 generator ID 是 `flow`，默认从 `model.analysisGraph` 输出 `flows/<slug>.json`、`flows/<slug>.mmd` 和 `flows/index.json`，template IDs 是 `flow/entry.json.peb`、`flow/entry.mmd.peb`、`flow/index.json.peb`，conflict policy 是 `OVERWRITE`。
- `DrawingBoardArtifactPlanner` 的 generator ID 是 `drawing-board`，默认从 `model.drawingBoard.elementsByTag` 输出 `design/drawing_board_<tag>.json`，template ID 是 `drawing-board/document.json.peb`，conflict policy 是 `OVERWRITE`，supported tags 与当前 design interaction tags 对齐。
- `DefaultPipelineRunner` 对内置 observation outputs 有额外规则：`drawing-board` 和 `flow` 的已知 template IDs 总是 resolve 为 `OVERWRITE`，不受用户 template conflict policy override 影响。
- 所有权规则：cap4k-generated skeleton 的文件形状、包路径、类型名、模板字段和初始结构由 generator inputs/templates 拥有；复杂业务逻辑通常写在生成 skeleton 内部的手写区域或后续人工实现中，除非技术设计明确要求从 generator input/template 产生。
- 绕过 generator-owned skeleton 是设计阶段决策。如果实现时发现必须绕开 generator-owned skeleton，不应直接手写平行结构；应停止实现，回到设计 / generator inputs / template 所有权重新决策。

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

- 输出类型契约：每个 planner 都必须决定 artifact 属于 `CHECKED_IN_SOURCE` 还是 `GENERATED_SOURCE`。构造器默认行为是 checked-in，因此 generated-source artifact 必须显式声明。
- 路径根契约：checked-in Kotlin artifact 使用 `ArtifactLayoutResolver.kotlinSourcePath`；generated Kotlin artifact 使用 `ArtifactLayoutResolver.generatedKotlinSourcePath`；`flow`、`drawing-board` 这类 project resource 使用 `projectResourcePath`，并依赖已校验的 output root。
- Gradle source 契约：generated source directory 在 rebase 后必须仍位于 root project directory 下。`toRootRelativeSlash` 负责强制这一点，`registerGeneratedKotlinSourceSets` 会把 `build/generated/cap4k/main/kotlin` 注册为受影响 module 的 Kotlin `main` source dir。
- 导出契约：`cap4kGenerateSources` 只能导出 `GENERATED_SOURCE`；`cap4kGenerate` 可以按完整 source-generation plan 导出 checked-in source 和 generated source。
- 观察输出契约：`flow` 和 `drawing-board` 是 analysis output。它们的内置 template 是设计上可覆盖的观察结果，不是需要手工编辑的 source skeleton。
- 所有权契约：generated source root 下的文件属于 generator-owned，可被覆盖。checked-in skeleton 文件最初由 generator-created，之后通常在 conflict policy 保护下成为 user-maintained implementation surface。

## Change Impact

- 将 planner 从 `CHECKED_IN_SOURCE` 改为 `GENERATED_SOURCE` 会改变 compile wiring、Gradle up-to-date inputs/outputs、conflict behavior，以及手写逻辑的所有权预期。
- 修改 generated source root 会影响 `cap4kGenerateSources`、plan JSON、source set registration、compile task dependencies，以及断言 `build/generated/cap4k/main/kotlin` 的 functional tests。
- 修改 `flow` 或 `drawing-board` output root 会影响 `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` 的输出、overwrite behavior，以及消费 `flows` 或 `design` 目录的下游 documentation / visualization agents。
- 修改 observation output 的默认 conflict policy 可能让 repo-local outputs 留下过期 analysis artifacts，因为 `flow` 和 `drawing-board` 是预期可重新生成的 snapshot。
- 新增 generator-owned skeleton surface 时，需要在设计阶段先决定所有权：它是 checked-in handwritten surface、build-owned generated source，还是 analysis-only observation output。

## Verification

用于检查输出类型、generated roots、flow、drawing-board 和 analysis docs 的 PowerShell 安全显式目录命令：

```powershell
rg -n "CHECKED_IN_SOURCE|GENERATED_SOURCE|build/generated/cap4k/main/kotlin|src-generated|flows|drawing" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-gradle cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-generator-design cap4k-plugin-pipeline-generator-flow cap4k-plugin-pipeline-generator-drawing-board docs/superpowers/analysis
```

维护本 map 时使用的补充聚焦检查：

```powershell
rg -n "outputKind = ArtifactOutputKind|ArtifactOutputKind\.GENERATED_SOURCE|ArtifactOutputKind\.CHECKED_IN_SOURCE" cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-generator-design cap4k-plugin-pipeline-generator-types cap4k-plugin-pipeline-core cap4k-plugin-pipeline-api
```

```powershell
rg -n "sourceTaskConfig|generatedSourceTaskConfig|analysisTaskConfig|SOURCE_TASK_SOURCE_IDS|GENERATED_SOURCE_TASK_SOURCE_IDS|ANALYSIS_TASK_SOURCE_IDS|SOURCE_TASK_GENERATOR_IDS|ANALYSIS_TASK_GENERATOR_IDS" cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle
```

## Drift Watch

- 如果 `ArtifactOutputKind` 增加新值，必须重新定义所有权、export filtering、conflict policy 和 Gradle source set 语义。
- 如果 `cap4kGenerateSources` 开始包含 `design-json` 或 design generators，必须重新判断 checked-in skeleton 与 generated source 的边界。
- 如果 `flow` 或 `drawing-board` 从 analysis task 进入 ordinary source generation task，必须更新 source/generator map、输出所有权和 public documentation routing。
- 如果 Gradle `buildDirectory` rebase 逻辑改变，必须重新确认 plan root、resolved output root 和 compile task dependency 是否仍匹配。
- 如果模板系统引入可声明手写 protected regions 的机制，应补充 skeleton 内部所有权边界。

## Not Covered

- 不覆盖每个 template 的字段级渲染内容。
- 不覆盖 bootstrap skeleton 输出；本 map 只覆盖 pipeline source / analysis generation artifacts。
- 不覆盖 addon artifact 的业务所有权，只说明它们也会进入 plan/export 语义并受 addon template namespace 校验。