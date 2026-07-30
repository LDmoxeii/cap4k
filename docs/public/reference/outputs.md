# Outputs

cap4k 的 output ownership 由 `outputKind`、output root、template 和 conflict policy 共同表达。决定在哪里写 handwritten logic 前，先读 `plan.json`。

## Output Kinds

| `outputKind` | 典型 root | Ownership |
| --- | --- | --- |
| `CHECKED_IN_SOURCE` | `<module>/src/main/kotlin` | 首次 materialization 的 committed skeleton 或 type source；existing file 固定 SKIP。 |
| `GENERATED_SOURCE` | `<module>/build/generated/cap4k/main/kotlin` | build-owned generated source；可被覆盖。 |
| `OUTPUT_ARTIFACT` | artifact-specific root | non-source artifact output kind；built-in planners 常见 source generation items 主要使用前两类。 |

## Checked-In Source

`CHECKED_IN_SOURCE` 通常用于 stable skeletons 和 type sources：

- Command / Query skeletons。
- Subscriber / Capability / handler surfaces。
- Domain Event, Value Object, enum, factory, behavior、repository adapter skeletons。

conflict policy 固定为 `SKIP`。cap4k 只承诺第一次 materialization，不承诺 checked-in file 在后续 generation 中追平最新 template，也不提供 merge、patch 或 managed-section refresh。需要重建时，由项目作者基于版本控制自行删除、生成和审查。

Factory、Behavior、Command、Query、Capability、Event、Value Object 以及 owned-child `*Creation` 都遵循这条 checked-in contract。它们生成后可以承载手写语义，但不会被 generator 覆盖。

## Generated Source

`cap4kGenerateSources` 只导出 `GENERATED_SOURCE`。Generated Kotlin root：

```text
<module>/build/generated/cap4k/main/kotlin
```

这个 root 由 build 拥有。source generation 会在完整重建前清理受控的 `build/generated/cap4k/main/kotlin` root，再 materialize 当前计划，因此已经移除的 projection 不会留下 stale source。典型 conflict policy 是 `OVERWRITE`。不要把它作为长期 handwritten business area。

显式 JSON persistence projection 产生的 `<ValueObjectName>JsonAttributeConverter` 属于这里；Value Object class 本身仍留在 checked-in domain source。

## Output Artifact

`OUTPUT_ARTIFACT` 表示不属于 ordinary Kotlin source ownership 的 artifact。按 artifact-specific output 审查：

- `generatorId`
- `templateId`
- `resolvedOutputRoot`
- `outputPath`
- `conflictPolicy`

不要因为 enum value 存在，就假设某个 built-in planner 一定使用 `OUTPUT_ARTIFACT`。

## Generated Vs Handwritten Ownership

| Situation | 正确读法 |
| --- | --- |
| file is under `src/main/kotlin` | 它是 first-materialized checked-in source；确认 `outputKind` 和 `templateId` 后直接按项目源码管理，后续 generation 会 SKIP。 |
| file is under `build/generated/cap4k/main/kotlin` | build owns it；改 input、template 或 source skeleton，不手改 generated source。 |
| skeleton has empty handler body | 它可能是 intended handwritten slot；不要只因空实现而删除。 |
| source snapshot was copied elsewhere | Snapshot 是 evidence 或 learning material，不是 active generator output。 |

## Review Fields

generation 前，或在 generated-capable surfaces 附近手写前，先检查：

- `generatorId`
- `templateId`
- `outputKind`
- `resolvedOutputRoot`
- `outputPath`
- `conflictPolicy`

这些字段共同定义 ownership。单个 path segment 不够。
