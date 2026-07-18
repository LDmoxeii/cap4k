# Outputs

cap4k 的 output ownership 由 `outputKind`、output root、template 和 conflict policy 共同表达。决定在哪里写 handwritten logic 前，先读 `plan.json`。

## Output Kinds

| `outputKind` | 典型 root | Ownership |
| --- | --- | --- |
| `CHECKED_IN_SOURCE` | `<module>/src/main/kotlin` | committed skeleton 或 type source；可能包含 handwritten slots。 |
| `GENERATED_SOURCE` | `<module>/build/generated/cap4k/main/kotlin` | build-owned generated source；可被覆盖。 |
| `OUTPUT_ARTIFACT` | artifact-specific root | non-source artifact output kind；built-in planners 常见 source generation items 主要使用前两类。 |

## Checked-In Source

`CHECKED_IN_SOURCE` 通常用于 stable skeletons 和 type sources：

- Command / Query skeletons。
- Subscriber / Saga / client / handler surfaces。
- Domain Event, Value Object, enum, factory, specification, repository adapter skeletons。

典型 conflict policy 是 `SKIP`，用于保护 existing handwritten logic。`<module>/src/main/kotlin` 下的文件仍可能包含 generator-managed sections，所以 ownership 不能只靠路径判断。

## Generated Source

`cap4kGenerateSources` 只导出 `GENERATED_SOURCE`。Generated Kotlin root：

```text
<module>/build/generated/cap4k/main/kotlin
```

这个 root 由 build 拥有。典型 conflict policy 是 `OVERWRITE`。不要把它作为长期 handwritten business area。

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
| file is under `src/main/kotlin` | 编辑前先读 `outputKind`、`templateId`、managed sections 和 `conflictPolicy`。 |
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
