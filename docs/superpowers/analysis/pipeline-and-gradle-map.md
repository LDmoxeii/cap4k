# Pipeline And Gradle Map

本页记录 `cap4k-plugin-pipeline-gradle` 的当前维护事实。精确 capability/input/ownership 状态应优先读取 provider descriptor、源码/测试和 `cap4kAgentSnapshot`。

## Public Surface

Plugin id 是 `io.github.ldmoxeii.cap4k.pipeline`。`PipelinePlugin.apply` 注册：

- `cap4kAgentSnapshot`：只读 inspection，输出 `build/cap4k/agent/` 八文件 manifest-first snapshot；
- `cap4kPlan`：ordinary source-generation plan，输出 `build/cap4k/plan.json`；
- `cap4kGenerate`：物化 ordinary source-generation plan；
- `cap4kGenerateSources`：只物化 build-owned `GENERATED_SOURCE` 并接入 Kotlin main compile；
- `cap4kAnalysisPlan`：analysis plan，输出 `build/cap4k/analysis-plan.json`；
- `cap4kAnalysisGenerate`：物化 flow/drawing-board `OUTPUT_ARTIFACT`。

项目初始化 task/DSL/module 已删除；项目结构来自官方 GitHub Template、团队模板或人工建立。

## Task Routing

| Lane | Sources | Configured generator IDs |
| --- | --- | --- |
| ordinary source | `db`, `design-json`, `enum-manifest`, `value-object-manifest` | `command`, `query`, `query-handler`, `capability`, `capability-handler`, `api-payload`, `domain-event`, `domain-subscriber`, `domain-service`, `integration-event`, `integration-subscriber`, `types-value-object`, `aggregate`, `aggregate-projection` |
| generated source | `db`, `enum-manifest`, `value-object-manifest` | `types-value-object`, `aggregate`, `aggregate-projection` |
| analysis | `ir-analysis` | `flow`, `drawing-board` |

Runner 实际安装的 planner catalog 来自 built-in provider 实例及已成功加载的 Pipeline Extension contribution；task config 负责输入/lane 收窄，不是第二套 capability truth。Activation 由 descriptor 的 `EXPLICIT_CONFIGURATION`、`INPUT_DRIVEN`、`INSTALLED` 元数据控制，runner 与 Agent effective view 使用同一规则。

## DSL And Inputs

`cap4k` 顶层 blocks 是：

- `project`
- `types`
- `sources`
- `generators`
- `templates`
- `layout`
- `managedFields`
- `pipelineExtensions`

`sources.irAnalysis.inputDirs` 选择 analysis input；不存在旧 `.enabled` switch。DB source 是 live external input：Agent snapshot 只披露配置、安全类别和 plan next action，不调用 DB collect。Design/enum/value-object/IR local source 通过 provider `localInputPaths` 参与 validation 和 stable local-input identity。

## Generated Source Wiring

`cap4kGenerateSources` 只导出 `GENERATED_SOURCE`。Gradle 将每个受影响 module 的实际 `<buildDirectory>/generated/cap4k/main/kotlin` 注册为 Kotlin `main` source dir，并让 `compileKotlin` 依赖该 task。Root state 文件只记录 cap4k-owned generated roots，用于安全清理已记录 root；不会保护或 merge 项目 root source。

## Agent API Contract

`cap4kAgentSnapshot` 每次写出：`manifest.json`、`project.json`、`capabilities.json`、`inputs.json`、`ownership.json`、`runtime.json`、`analysis.json`、`diagnostics.json`。分区先原子替换、旧额外文件清理后，manifest 最后发布。Invalid required state 尽量留下 snapshot 后非零；optional unavailable 为 `partial` 成功。

Snapshot 不刷新 plan。`PlanReport.outcome = FAILED` 即使 identity 匹配也必须归一化为 invalid。Analysis 分区区分 planned 与 available outputs，并只把存在且不早于当前 plan 的 project-owned files 报告为 available。

## Source Anchors

- `cap4k-plugin-pipeline-gradle/.../PipelinePlugin.kt`
- `cap4k-plugin-pipeline-gradle/.../Cap4kExtension.kt`
- `cap4k-plugin-pipeline-gradle/.../Cap4kProjectConfigFactory.kt`
- `cap4k-plugin-pipeline-gradle/.../Cap4kAgentSnapshotTask.kt`
- `cap4k-plugin-pipeline-gradle/.../Cap4kPlanTask.kt`
- `cap4k-plugin-pipeline-gradle/.../Cap4kAnalysisPlanTask.kt`
- `cap4k-plugin-pipeline-api/.../PipelineCapabilityDescriptors.kt`

## Verification

```powershell
rg -n "cap4kAgentSnapshot|cap4kPlan|cap4kGenerate|cap4kGenerateSources|cap4kAnalysisPlan|cap4kAnalysisGenerate" cap4k-plugin-pipeline-gradle/src/main/kotlin
rg -n "SOURCE_TASK_SOURCE_IDS|SOURCE_TASK_GENERATOR_IDS|GENERATED_SOURCE_TASK|ANALYSIS_TASK" cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt
pwsh -NoLogo -NoProfile -File skills/scripts/validate-cap4k-skills.ps1
```
