# Common Mistakes

本页是 cap4k public docs 和 project input 常见错误查表。字段合同见相邻 reference pages。

## Generation And Input Mistakes

| 错误 | 正确合同 |
| --- | --- |
| 把 generator 当作 CRUD scaffold。 | Generator 写出明确 architecture 和 design inputs 的结果；business behavior 仍属于 handwritten domain/application code。 |
| 等 generated files 出现后才写 `design.json`。 | 先写 schema、`design/design.json`、`types.enumManifest`、`types.valueObjectManifest`，再 review `plan.json`。 |
| generation 前跳过 `plan.json` review。 | materialization 前检查 `generatorId`、`templateId`、`outputKind`、`resolvedOutputRoot`、`outputPath` 和 `conflictPolicy`。 |
| 手改 `build/generated/cap4k/main/kotlin`。 | `GENERATED_SOURCE` 由 build 拥有；改 inputs、templates 或 checked-in skeletons。 |
| 把 `build/cap4k/*` 当作 committed source truth。 | `build/cap4k/agent/`、`plan.json`、`analysis-plan.json` 是 `build/` 下的本地 generated evidence。 |

## Design JSON Boundaries

| 错误 | 正确合同 |
| --- | --- |
| 把 adapter protocol details 放进 `capability` 或 `domain_service` entries。 | `capability` 表达 application-facing external capability；protocol mapping 属于 adapter handler。 |
| 把 `integration_event` 当作 transport runtime configuration。 | `integration_event` 是 published-language contract 和 skeleton signal；transport details 不属于 domain design input。 |

## Command And Query Mistakes

| 错误 | 正确合同 |
| --- | --- |
| 让 `query` repair 或 mutate aggregate state。 | Query 只观察。 |
| 让 `command` 为了 UI convenience 返回 read model。 | Command 表达 state-changing intent；read shapes 属于 Query 或 API payload result fields。 |
| 让 controller 承载 business state decisions。 | Controller 把 protocol input 转成 Command/Query 并委托。 |
| 直接持久化 owned child，或依赖手动 `save()` 完成 Command。 | Existing root 通过 Repository 保持 managed，创建/删除 root 分别通过 Factory/Repository 表达；外层 Command 自动稳定化和提交。 |

## Analysis Mistakes

| 错误 | 正确合同 |
| --- | --- |
| 把 `cap4kAnalysisGenerate` 当作 source generation。 | 它导出 analysis/observation artifacts，尤其是 flow 和 drawing-board。 |
| 期待 `flow` 和 `drawing-board` 创建 source skeletons。 | 它们通过 IR analysis input 观察 existing code structure。 |
| `build/cap4k-code-analysis` 下缺少 `nodes.json`、`rels.json` 或 `aggregate-elements.json`。 | IR analysis input 不完整；没有 Aggregate element 时也要提供 `aggregate-elements.json`，内容为 `[]`。 |
| 把 `drawing_board_aggregate_elements.json` 注册到 `sources.designJson.files`。 | 该文件是 Aggregate element 结构证据，不是 Design JSON；只选择普通 `drawing_board_<tag>.json` 文件。 |

## Orchestration And Event Mistakes

| 错误 | 正确合同 |
| --- | --- |
| 把 cap4k 当成内置长流程编排器。 | 先组合 reliable Command 与 Integration Event；仍不足时选择显式 orchestration provider。 |
| 把 Domain Event 当作 technical continuation step。 | Domain Event 描述 aggregate state change 之后形成的 business fact。 |
| 因为 `domain_event.aggregates` 声明了 owner，就把 Aggregate/Entity 放进 payload。 | `aggregates` 仅表达归属；payload 只来自显式 `fields`，使用 Strong ID、Value Object、标量或不可变 snapshot。运行时 Entity 拒绝规则不能放宽。 |
| 通过 templates 或 addon magic 直接发布 outbound integration event payloads。 | Business code 从 application orchestration points attach outbound facts。 |
| 把 adapter/protocol concerns 放进 domain。 | Domain keeps business language；adapter 处理 HTTP、messaging、persistence mapping、callback protocol 和 external API details。 |
