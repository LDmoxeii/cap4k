# Common Mistakes

本页是 cap4k public docs 和 project input 常见错误查表。字段合同见相邻 reference pages。

## Generation And Input Mistakes

| 错误 | 正确合同 |
| --- | --- |
| 把 generator 当作 CRUD scaffold。 | Generator 写出明确 architecture 和 design inputs 的结果；business behavior 仍属于 handwritten domain/application code。 |
| 等 generated files 出现后才写 `design.json`。 | 先写 schema、`design/design.json`、`types.enumManifest`、`types.valueObjectManifest`，再 review `plan.json`。 |
| generation 前跳过 `plan.json` review。 | materialization 前检查 `generatorId`、`templateId`、`outputKind`、`resolvedOutputRoot`、`outputPath` 和 `conflictPolicy`。 |
| 手改 `build/generated/cap4k/main/kotlin`。 | `GENERATED_SOURCE` 由 build 拥有；改 inputs、templates 或 checked-in skeletons。 |
| 把 `build/cap4k/*` 当作 committed source truth。 | `build/cap4k/plan.json`、`bootstrap-plan.json`、`analysis-plan.json` 是 `build/` 下的本地 generated evidence。 |

## Design JSON Mistakes

| 错误 | 正确合同 |
| --- | --- |
| 使用 normal `design.json` tag `value_object`。 | 使用 `types.valueObjectManifest` 和 `design/value-objects.json`。 |
| 使用 normal `design.json` tag `validator`。 | 使用 aggregate unique helpers 或 addon-owned artifacts；normal supported tags exclude `validator`。 |
| 把 adapter protocol details 放进 `client` 或 `domain_service` entries。 | `client` 表达 application-facing external capability；protocol mapping 属于 adapter handler。 |
| 把 `integration_event` 当作 transport runtime configuration。 | `integration_event` 是 published-language contract 和 skeleton signal；transport details 不属于 domain design input。 |

## Command And Query Mistakes

| 错误 | 正确合同 |
| --- | --- |
| 让 `query` repair 或 mutate aggregate state。 | Query 只观察。 |
| 让 `command` 为了 UI convenience 返回 read model。 | Command 表达 state-changing intent；read shapes 属于 Query 或 API payload result fields。 |
| 让 controller 承载 business state decisions。 | Controller 把 protocol input 转成 Command/Query 并委托。 |
| 在一个 command 中把多个 aggregate roots 当成 shared write ownership。 | 一个 command path 应只 persist 一个 aggregate root；除非显式建模，否则其他 reads 只是 validation facts。 |

## Analysis Mistakes

| 错误 | 正确合同 |
| --- | --- |
| 把 `cap4kAnalysisGenerate` 当作 source generation。 | 它导出 analysis/observation artifacts，尤其是 flow 和 drawing-board。 |
| 期待 `flow` 和 `drawing-board` 创建 source skeletons。 | 它们通过 IR analysis input 观察 existing code structure。 |
| 使用 stale `sources.irAnalysis.enabled`。 | 使用 `sources.irAnalysis.inputDirs`。 |
| 使用 stale `generators.flow.enabled`。 | 配置 `sources.irAnalysis.inputDirs`；`flow {}` 没有 enabled switch。 |
| 使用 stale `generators.drawingBoard.enabled`。 | 配置 `sources.irAnalysis.inputDirs`；`drawingBoard {}` 没有 enabled switch。 |
| `build/cap4k-code-analysis` 下缺少 `nodes.json` 或 `rels.json`。 | IR analysis input 不完整。 |

## Bootstrap Mistakes

| 错误 | 正确合同 |
| --- | --- |
| bootstrap 时没有匹配 module/base package expectations。 | Review `build/cap4k/bootstrap-plan.json` 中的 root project、module names、base package、template output 和 conflict policy。 |
| 把 bootstrap 当作 business modeling。 | Bootstrap creates structure；schema、design JSON、enum manifest、value-object manifest 仍需要 author input。 |
| 用 source generation 修补错误 bootstrap layout。 | source generation 前先修正 bootstrap configuration 或 module layout。 |

## Saga And Event Mistakes

| 错误 | 正确合同 |
| --- | --- |
| 把每个 callback 都建模成 Saga。 | Saga 用于有 recovery/compensation needs 的 long-running coordination；simple external facts 可以 route 到 command/subscriber paths。 |
| 把 Domain Event 当作 technical continuation step。 | Domain Event 描述 aggregate state change 之后形成的 business fact。 |
| 通过 templates 或 addon magic 直接发布 outbound integration event payloads。 | Business code 从 application orchestration points attach outbound facts。 |
| 把 adapter/protocol concerns 放进 domain。 | Domain keeps business language；adapter 处理 HTTP、messaging、persistence mapping、callback protocol 和 external API details。 |

## Quick Scan Terms

如果这些词出现在 normal source-generation guidance 中，需要审查上下文：

- `sources.irAnalysis.enabled`
- `generators.flow.enabled`
- `generators.drawingBoard.enabled`
- `value_object` in normal `design.json`
- `validator` in normal `design.json`
