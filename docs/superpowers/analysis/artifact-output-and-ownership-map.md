# Artifact Output And Ownership Map

本页是当前 pipeline artifact 类型、输出位置和所有权的维护索引。Provider descriptor、plan item、Agent API ownership 分区、源码和测试是权威事实。

## Current Facts

- `ArtifactOutputKind` 包含 `CHECKED_IN_SOURCE`、`GENERATED_SOURCE`、`OUTPUT_ARTIFACT`。
- `CHECKED_IN_SOURCE` 是首次物化到项目 source tree 的骨架/类型源码。内建 checked-in planner 明确使用 `ConflictPolicy.SKIP`；现有文件不会被覆盖或 managed-section merge。删除后重新生成属于显式 rematerialization，必须经版本控制审查。
- `GENERATED_SOURCE` 是 build-owned、可替换的 Kotlin source，默认 root 为 `<module>/build/generated/cap4k/main/kotlin`。Aggregate/aggregate-projection 和需要 persistence adapter 的 value-object family 可产出此类文件；`cap4kGenerateSources` 只导出这一类型。manifest-authored Business Enum 不再属于 generated-source lane。
- `OUTPUT_ARTIFACT` 是非源码 evidence。`FlowArtifactPlanner` 与 `DrawingBoardArtifactPlanner` 已显式使用该类型、`OVERWRITE` 和 project resource output root；它们不属于 checked-in skeleton 或长期手写业务逻辑位置。
- Gradle 会把 generated-source plan root rebase 到实际 module build directory，并记录 `resolvedOutputRoot`。已记录 managed roots 只服务于 generated-source 清理/compile wiring，与已删除的项目初始化保护区、marker 或 merge 机制无关。

## Current Planner Families

- Aggregate planners：checked-in Factory/Repository/Behavior 等 author-owned skeleton，加 build-owned Entity/Schema/Strong ID/converter/projection 等 generated source。manifest Business Enum 的 `value` / `description` / `valueOfOrNull` / nested `Converter` 基础 API 保持，同时可按有序 `fields` schema 生成显式 typed properties。
- Design planners：`command`、`query`、`query-handler`、`capability`、`capability-handler`、`api-payload`、`domain-event`、`domain-subscriber`、`domain-service`、`integration-event`、`integration-subscriber`，默认产出 checked-in source。
- Type planners：`enum` 与 `types-value-object`。manifest-authored shared/local Business Enum 固定为 domain `src/main/kotlin` 下的 `CHECKED_IN_SOURCE + SKIP`，首次物化后允许作者增加领域逻辑；`types-value-object` 的具体 output kind 仍由 plan item 说明。
- Analysis planners：`flow` 与 `drawing-board`，只在 analysis lane 产出 `OUTPUT_ARTIFACT`。
- Pipeline Extension artifact addon 必须通过自己的 provider descriptor 与 plan item 披露 capability、template、output kind/root 和 conflict policy。

## Review Contract

每个 planned artifact 必须一起审查：

- stable generator/capability identity；
- `templateId`；
- `outputKind`；
- `outputPath` 与 `resolvedOutputRoot`；
- `conflictPolicy`；
- 对应 author input 与 runtime/provider boundary。

不要把 `src/main/kotlin` 自动等同于“可随意手写”，也不要把 `build/generated` 或 analysis output 当作业务 source truth。若已有 supported input 能表达结构，应更新 input、重新 plan，再 mutation；不要手写平行 skeleton 只为通过编译。

## Source Anchors

- `cap4k-plugin-pipeline-api/.../PipelineModels.kt`
- `cap4k-plugin-pipeline-api/.../PipelineCapabilityDescriptors.kt`
- `cap4k-plugin-pipeline-generator-aggregate/`
- `cap4k-plugin-pipeline-generator-design/`
- `cap4k-plugin-pipeline-generator-types/`
- `cap4k-plugin-pipeline-generator-flow/`
- `cap4k-plugin-pipeline-generator-drawing-board/`
- `cap4k-plugin-pipeline-gradle/.../PipelinePlugin.kt`
- `cap4k-plugin-pipeline-gradle/.../Cap4kAgentSnapshotTask.kt`

## Verification

```powershell
rg -n "outputKind = ArtifactOutputKind|CHECKED_IN_SOURCE|GENERATED_SOURCE|OUTPUT_ARTIFACT|resolvedOutputRoot|conflictPolicy" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-generator-* cap4k-plugin-pipeline-gradle
```

修改 planner output kind/root/policy 时，必须同步 provider descriptor、plan tests、Gradle export/compile wiring、Agent API normalization、public docs 和 thin skill ownership reference。
