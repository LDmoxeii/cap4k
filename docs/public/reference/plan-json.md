# Plan JSON

`plan.json` 是 generation 前的 ownership evidence。它回答 generator 准备写什么、写到哪里、用哪个 template、谁拥有输出、遇到已有文件如何处理。

## Local Plan Files

| File | Producer | Scope |
| --- | --- | --- |
| `build/cap4k/plan.json` | `cap4kPlan` | ordinary source generation plan |
| `build/cap4k/analysis-plan.json` | `cap4kAnalysisPlan` | analysis output plan |

这些文件都在 `build/` 下，是本地 generated evidence，不是 committed source truth。

每个 plan report 顶层包含 `outcome`：成功计划为 `SUCCEEDED`；`PipelineDiagnosticsException` 产生的诊断计划为 `FAILED`。失败任务仍会写出 diagnostics/evidence 供排错，但 `FAILED` report 不能因为 configuration/local-input identity 匹配就被解释为可生成的 fresh/OK evidence；`cap4kAgentSnapshot` 会把它归一化为 `invalid` 并要求重新运行对应 plan task。

## Review Fields

| Field | 含义 | 审查问题 |
| --- | --- | --- |
| `generatorId` | planned item 的 generator 来源 | 是否能被 DB/schema、design JSON、type manifest、analysis input 或 addon 解释？ |
| `templateId` | selected template id | 是否匹配预期 artifact family？ |
| `outputKind` | ownership kind | 是 `CHECKED_IN_SOURCE`、`GENERATED_SOURCE`、`GENERATED_RESOURCE` 还是 `OUTPUT_ARTIFACT`？ |
| `resolvedOutputRoot` | optional resolved output root metadata | 是否能解释 generated/artifact root；checked-in source 可以为空，也可以由 planner 提供 source root。 |
| `conflictPolicy` | existing file handling | 会不会覆盖 handwritten logic？ |
| `outputPath` | repo-relative planned path | file name、package path、module placement 是否合理？ |
| `context` | generator-specific context | aggregate、building block、package、module role 是否和输入一致？ |

最小 item shape：

```json
{
  "generatorId": "command",
  "templateId": "design/command.kt.peb",
  "outputKind": "CHECKED_IN_SOURCE",
  "resolvedOutputRoot": "",
  "outputPath": "demo-application/src/main/kotlin/com/acme/demo/application/commands/content/workflow/SubmitContentForReviewCmd.kt",
  "conflictPolicy": "SKIP"
}
```

字段名是 review contract；实际 item 可能包含更多 context。`outputPath` 始终是完整的 repo-relative 目标路径，不要再把它和 `resolvedOutputRoot` 重新拼接。`resolvedOutputRoot` 是可选的 root metadata：checked-in source 可以为空，也可以由 planner 提供 source root；Gradle 对 generated source/resource rebase 后会更新为实际 generated root。

当前内建 design generator ids 使用 `command`、`query`、`domain-service` 这类稳定短 id。

<!-- CAPABILITY_CONTRACT:OUTPUT_KINDS -->
## Output Kind Values

| `outputKind` | 含义 |
| --- | --- |
| `CHECKED_IN_SOURCE` | first-materialized committed source skeleton or type source，通常位于 `<module>/src/main/kotlin`；existing file 固定 SKIP。 |
| `GENERATED_SOURCE` | build-owned generated source，位于 `<module>/build/generated/cap4k/main/kotlin`。 |
| `GENERATED_RESOURCE` | build-owned generated resource，位于 `<module>/build/generated/cap4k/main/resources`；例如生成的 Spring auto-configuration metadata。 |
| `OUTPUT_ARTIFACT` | non-source evidence output；内置 flow 与 drawing-board planner 使用此 ownership。 |
<!-- /CAPABILITY_CONTRACT:OUTPUT_KINDS -->

## Conflict Policy Reading

| `conflictPolicy` | 典型用途 |
| --- | --- |
| `SKIP` | 所有 checked-in source；generator 不覆盖、合并或刷新已有文件。 |
| `OVERWRITE` | build-owned generated source/resource 或明确要重新生成的 artifacts。 |
| `FAIL` | 明确要求已有文件阻止 materialization 的严格输出。 |

`CHECKED_IN_SOURCE` 的 policy 不受 source-generation template override 改写：plan 必须呈现 `SKIP`。作者需要更新 skeleton 时，应通过版本控制自行删除旧文件、重新 materialize 并审查差异。

`src/main/kotlin` 不自动等于 handwritten ownership。Plan fields 必须一起阅读。

## Analysis Plan

`analysis-plan.json` 属于 analysis/observation output。它应把 source id `ir-analysis` route 到 generator ids `flow` 和 `drawing-board`。不要把它读成 ordinary source generation plan。
