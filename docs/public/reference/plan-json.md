# Plan JSON

`plan.json` 是 generation 前的 ownership evidence。它回答 generator 准备写什么、写到哪里、用哪个 template、谁拥有输出、遇到已有文件如何处理。

## Local Plan Files

| File | Producer | Scope |
| --- | --- | --- |
| `build/cap4k/bootstrap-plan.json` | `cap4kBootstrapPlan` | project structure bootstrap plan |
| `build/cap4k/plan.json` | `cap4kPlan` | ordinary source generation plan |
| `build/cap4k/analysis-plan.json` | `cap4kAnalysisPlan` | analysis output plan |

这些文件都在 `build/` 下，是本地 generated evidence，不是 committed source truth。

## Review Fields

| Field | 含义 | 审查问题 |
| --- | --- | --- |
| `generatorId` | planned item 的 generator 来源 | 是否能被 DB/schema、design JSON、type manifest、analysis input 或 addon 解释？ |
| `templateId` | selected template id | 是否匹配预期 artifact family？ |
| `outputKind` | ownership kind | 是 `CHECKED_IN_SOURCE`、`GENERATED_SOURCE` 还是 `OUTPUT_ARTIFACT`？ |
| `resolvedOutputRoot` | resolved output root | 是否落在正确 module 与 source/artifact root？ |
| `conflictPolicy` | existing file handling | 会不会覆盖 handwritten logic？ |
| `outputPath` | relative output path | file name、package path、module placement 是否合理？ |
| `context` | generator-specific context | aggregate、building block、package、module role 是否和输入一致？ |

最小 item shape：

```json
{
  "generatorId": "design-command",
  "templateId": "design/command.kt.peb",
  "outputKind": "CHECKED_IN_SOURCE",
  "resolvedOutputRoot": "demo-application/src/main/kotlin",
  "outputPath": "com/acme/demo/application/commands/content/workflow/SubmitContentForReviewCmd.kt",
  "conflictPolicy": "SKIP"
}
```

字段名是 review contract；实际 item 可能包含更多 context。

## Output Kind Values

| `outputKind` | 含义 |
| --- | --- |
| `CHECKED_IN_SOURCE` | committed source skeleton or type source，通常位于 `<module>/src/main/kotlin`。 |
| `GENERATED_SOURCE` | build-owned generated source，位于 `<module>/build/generated/cap4k/main/kotlin`。 |
| `OUTPUT_ARTIFACT` | non-source artifact output kind；built-in planners 常见 source generation items 主要使用前两类，具体以 plan evidence 为准。 |

## Conflict Policy Reading

| `conflictPolicy` | 典型用途 |
| --- | --- |
| `SKIP` | 可能包含 handwritten logic 的 checked-in skeletons。 |
| `OVERWRITE` | build-owned generated source 或明确要重新生成的 artifacts。 |
| `FAIL` | bootstrap 或 guarded output；已有文件应阻止 materialization。 |

`src/main/kotlin` 不自动等于 handwritten ownership。Plan fields 必须一起阅读。

## Bootstrap Plan

`bootstrap-plan.json` 属于 project structure bootstrap。审查重点：

- root project name。
- domain/application/adapter/start module names。
- base package。
- template output paths。
- `conflictPolicy` 对已有 project files 的处理。

bootstrap plan 不验证 business design、schema、design JSON、enum manifest 或 value-object manifest。

## Analysis Plan

`analysis-plan.json` 属于 analysis/observation output。它应把 source id `ir-analysis` route 到 generator ids `flow` 和 `drawing-board`。不要把它读成 ordinary source generation plan。
