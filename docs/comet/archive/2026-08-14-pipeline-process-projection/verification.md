---
generated_from_state_version: 12
---

# Verification

## Current result

- Result: **Passed**
- Assurance: **skill-coordinated**
- Goal cycle: 1
- Iteration: 1
- Verifier attempt: 2
- Completed: 2026-08-14T04:52:56.119Z
- Summary: 第二次独立只读核验通过。同一候选保持 entry-centered 连续因果链、任意长度隐藏路径压缩、投影后 root、双真实入口共享后缀、循环保留和稳定去重；新增 focused/functional 回归及 Public Docs/Skill 投影，未恢复 Validator/unique-validator、SagaCoordinator、process projection、第二 Flow 入口或模板内自定义拓扑。

## Acceptance

| ID | Result | Source | Criterion | Reason |
| --- | --- | --- | --- | --- |
| A1 | passed | brief.md | A1：给定完整的入站 Integration Event 到 follow-up Command 链，默认 `flow` 生成一张 entry JSON、一张 Mermaid，并在 index 中报告 `flowCount = 1`。 | 第二次独立核验确认 brief 验收由 focused test、Gradle functional fixture、公开投影与完整治理检查覆盖。 |
| A2 | passed | brief.md | A2：A1 的公开节点只包含实际入口、Command、Domain Event、Integration Event；Handler 和 Entity Method 被隐藏且路径正确收缩。 | 第二次独立核验确认 brief 验收由 focused test、Gradle functional fixture、公开投影与完整治理检查覆盖。 |
| A3 | passed | brief.md | A3：给定两个各自具有真实入口证据、最终投影后均为零入度的入口，默认 `flow` 生成两张独立 Flow，即使它们共享下游后缀。 | 第二次独立核验确认 brief 验收由 focused test、Gradle functional fixture、公开投影与完整治理检查覆盖。 |
| A4 | passed | brief.md | A4：有上游因果边的中间 Integration Event、Command 或 sender 不被提升为额外 root。 | 第二次独立核验确认 brief 验收由 focused test、Gradle functional fixture、公开投影与完整治理检查覆盖。 |
| A5 | passed | brief.md | A5：默认 `flows/<entry>.json`、`flows/<entry>.mmd`、`flows/index.json`、generator id `flow` 和 output id `flow` 保持不变。 | 第二次独立核验确认 brief 验收由 focused test、Gradle functional fixture、公开投影与完整治理检查覆盖。 |
| A6 | passed | brief.md | A6：focused tests 以一个完整 fixture 同时覆盖入站事件、Command、Domain Event、follow-up Command、隐藏路径压缩和单 Flow 计数，不只依赖分段组合测试。 | 第二次独立核验确认 brief 验收由 focused test、Gradle functional fixture、公开投影与完整治理检查覆盖。 |
| A7 | passed | brief.md | A7：Gradle functional fixture 实际执行 analysis plan/generate，核对 JSON、Mermaid、index 的路径、entry identity、节点/边计数和 `flowCount`。 | 第二次独立核验确认 brief 验收由 focused test、Gradle functional fixture、公开投影与完整治理检查覆盖。 |
| A8 | passed | brief.md | A8：Public Docs 与 Skill 明确：连续 graph evidence 产生一张 Flow；两个真实 root 产生两张 Flow；共享后缀不是自动 stitching 的理由。 | 第二次独立核验确认 brief 验收由 focused test、Gradle functional fixture、公开投影与完整治理检查覆盖。 |
| A9 | passed | brief.md | A9：代码事实、AgentFacts、Public Docs 和 Skill 中不存在 process generator、process output、process task 或第二套 Flow 产品入口。 | 第二次独立核验确认 brief 验收由 focused test、Gradle functional fixture、公开投影与完整治理检查覆盖。 |
| A10 | passed | brief.md | A10：完整 Gradle 检查、capability contract export/validate/test、Skill validator、current Runtime facts validator、PR workflow tests 与 `git diff --check` 全部通过。 | 第二次独立核验确认 brief 验收由 focused test、Gradle functional fixture、公开投影与完整治理检查覆盖。 |
| A11 | passed | specs/pipeline-causal-flow-contract/spec.md | Pipeline `flow` 是面向业务因果链的默认静态投影。它从一个有生产代码或明确 relationship evidence 的实际入口出发，沿可验证的 Command/Event 因果关系生成入口中心的最小有向子图，帮助人类和 Agent 阅读代码结构。 | 第二次独立核验确认唯一 entry-centered Pipeline Flow、Graph 输入边界、退役入口与完整性诊断符合代码事实。 |
| A12 | passed | specs/pipeline-causal-flow-contract/spec.md | Flow 只陈述静态连接与可达性，不证明业务正确性、运行时执行顺序、事务边界、消息必达、重试结果或最终业务结果。 | 第二次独立核验确认唯一 entry-centered Pipeline Flow、Graph 输入边界、退役入口与完整性诊断符合代码事实。 |
| A13 | passed | specs/pipeline-causal-flow-contract/spec.md | 一张 Flow 对应一个具体真实入口及其沿默认因果关系可达的最小投影。 | 第二次独立核验确认唯一 entry-centered Pipeline Flow、Graph 输入边界、退役入口与完整性诊断符合代码事实。 |
| A14 | passed | specs/pipeline-causal-flow-contract/spec.md | 默认产品不提供自动或独立 process projection，不把多个真实入口拼成一个业务过程。 | 第二次独立核验确认唯一 entry-centered Pipeline Flow、Graph 输入边界、退役入口与完整性诊断符合代码事实。 |
| A15 | passed | specs/pipeline-causal-flow-contract/spec.md | Pipeline generator `pipeline.generator.flow`、generator id `flow` 和 output id `flow` 是唯一公开 Flow 产品身份。 | 第二次独立核验确认唯一 entry-centered Pipeline Flow、Graph 输入边界、退役入口与完整性诊断符合代码事实。 |
| A16 | passed | specs/pipeline-causal-flow-contract/spec.md | `cap4kAnalysisPlan` 和 `cap4kAnalysisGenerate` 是 Flow planning 与 generation 的公开 task lane。 | 第二次独立核验确认唯一 entry-centered Pipeline Flow、Graph 输入边界、退役入口与完整性诊断符合代码事实。 |
| A17 | passed | specs/pipeline-causal-flow-contract/spec.md | 已退役的 `cap4k-plugin-code-analysis-flow-export`、`cap4kFlow*` tasks、plugin id、alias 和第二套 output contract 不得恢复。 | 第二次独立核验确认唯一 entry-centered Pipeline Flow、Graph 输入边界、退役入口与完整性诊断符合代码事实。 |
| A18 | passed | specs/pipeline-causal-flow-contract/spec.md | Flow 只消费 `AnalyzerSnapshot.graph` 及其 completeness、freshness、source identity 和 diagnostics。 | 第二次独立核验确认唯一 entry-centered Pipeline Flow、Graph 输入边界、退役入口与完整性诊断符合代码事实。 |
| A19 | passed | specs/pipeline-causal-flow-contract/spec.md | Drawing Board Design Projection 与 Aggregate Structure 不得补造 Flow 节点、边、入口或 process 关系。 | 第二次独立核验确认唯一 entry-centered Pipeline Flow、Graph 输入边界、退役入口与完整性诊断符合代码事实。 |
| A20 | passed | specs/pipeline-causal-flow-contract/spec.md | raw Graph 可以包含默认 Flow 不展示的技术事实，例如 Query、Capability、普通 `validator` observation 和 read-side dependency。 | 第二次独立核验确认唯一 entry-centered Pipeline Flow、Graph 输入边界、退役入口与完整性诊断符合代码事实。 |
| A21 | passed | specs/pipeline-causal-flow-contract/spec.md | `unique-validator` 等已退役 Aggregate Structure 类型不得因 Flow 重新出现。 | 第二次独立核验确认唯一 entry-centered Pipeline Flow、Graph 输入边界、退役入口与完整性诊断符合代码事实。 |
| A22 | passed | specs/pipeline-causal-flow-contract/spec.md | Graph 缺失、不可解析 relationship、identity 冲突或会使投影不可信的完整性问题必须产生可操作诊断；合法零 Flow 与分析不完整必须可区分。 | 第二次独立核验确认唯一 entry-centered Pipeline Flow、Graph 输入边界、退役入口与完整性诊断符合代码事实。 |
| A23 | passed | specs/pipeline-causal-flow-contract/spec.md | 默认可见节点： | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A24 | passed | specs/pipeline-causal-flow-contract/spec.md | 具备入口证据的实际代码节点； | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A25 | passed | specs/pipeline-causal-flow-contract/spec.md | Command； | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A26 | passed | specs/pipeline-causal-flow-contract/spec.md | Domain Event； | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A27 | passed | specs/pipeline-causal-flow-contract/spec.md | Integration Event。 | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A28 | passed | specs/pipeline-causal-flow-contract/spec.md | 默认隐藏但保留在 raw Graph 中的节点： | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A29 | passed | specs/pipeline-causal-flow-contract/spec.md | Command Handler； | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A30 | passed | specs/pipeline-causal-flow-contract/spec.md | Domain Event Handler； | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A31 | passed | specs/pipeline-causal-flow-contract/spec.md | Integration Event Handler； | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A32 | passed | specs/pipeline-causal-flow-contract/spec.md | Entity Method。 | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A33 | passed | specs/pipeline-causal-flow-contract/spec.md | Query、Capability、普通 Validator、read-side dependency 和非 Command/Event 技术关系不进入默认业务因果链。 | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A34 | passed | specs/pipeline-causal-flow-contract/spec.md | 入口节点不是新增的抽象 `Entry` 节点。HTTP Controller/endpoint、RPC adapter、Inbound Integration Event、Job method 和未来其他有生产 relationship evidence 的触发节点，都以其实际 Graph node 身份承担入口角色。入口类别不是由 Public Docs、Skill 或永久手写封闭 taxonomy 定义。 | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A35 | passed | specs/pipeline-causal-flow-contract/spec.md | 默认 Flow 只处理生产 Analyzer 明确识别的 causal relationship，以及从实际入口节点到 Command 的可验证 `*ToCommand` relationship。 | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A36 | passed | specs/pipeline-causal-flow-contract/spec.md | 当两个可见节点之间存在 raw causal path，且内部节点全部属于隐藏集合时，建立一条直接投影边。隐藏路径长度不设固定上限。 | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A37 | passed | specs/pipeline-causal-flow-contract/spec.md | 例如： | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A38 | passed | specs/pipeline-causal-flow-contract/spec.md | 投影为： | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A39 | passed | specs/pipeline-causal-flow-contract/spec.md | 以及： | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A40 | passed | specs/pipeline-causal-flow-contract/spec.md | 完整链投影为： | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A41 | passed | specs/pipeline-causal-flow-contract/spec.md | Command 和 Event 是可见边界，因此路径在这些节点处分段压缩，不把整条链压成入口到最终 Command 的单边。 | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A42 | passed | specs/pipeline-causal-flow-contract/spec.md | 路径压缩只穿过已知隐藏因果角色。遇到既不可见、也不属于隐藏集合的未知 Graph 节点时，不把它自动视为透明中间节点。新增可压缩技术角色属于 Analyzer contract 变化，必须由生产代码、测试和 capability propagation 明确声明。 | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A43 | passed | specs/pipeline-causal-flow-contract/spec.md | 分叉、汇合和共享后缀必须保留。raw edge 与 projected edge 使用稳定 identity 去重，不依赖输入目录或遍历顺序。投影实现保留至少一条对应 raw path evidence 供诊断和测试使用；是否扩展为多路径 evidence 或公开 wire 字段属于独立合同变化，不在本合同中隐式增加。 | 第二次独立核验确认可见/隐藏角色、开放入口 evidence、任意长度路径收缩和分段投影由 Kotlin 生产代码实现并有回归覆盖。 |
| A44 | passed | specs/pipeline-causal-flow-contract/spec.md | 只有同时满足以下条件的节点才生成默认 Flow root： | 第二次独立核验确认 root-after-projection、双真实入口共享后缀、循环保留、有限遍历和稳定去重符合合同。 |
| A45 | passed | specs/pipeline-causal-flow-contract/spec.md | 生产代码、metadata 或可验证 relationship 能证明节点具备入口资格； | 第二次独立核验确认 root-after-projection、双真实入口共享后缀、循环保留、有限遍历和稳定去重符合合同。 |
| A46 | passed | specs/pipeline-causal-flow-contract/spec.md | 完成可见投影和隐藏路径压缩后，该节点没有上游因果边； | 第二次独立核验确认 root-after-projection、双真实入口共享后缀、循环保留、有限遍历和稳定去重符合合同。 |
| A47 | passed | specs/pipeline-causal-flow-contract/spec.md | 该节点在最终投影中存在下游因果边。 | 第二次独立核验确认 root-after-projection、双真实入口共享后缀、循环保留、有限遍历和稳定去重符合合同。 |
| A48 | passed | specs/pipeline-causal-flow-contract/spec.md | 入度为零只是必要条件，不是入口资格的充分条件。孤立 Command/Event、因 metadata 缺失而失去上游的节点、仅因名称或包路径像入口的节点，都不得自动升级为 root。 | 第二次独立核验确认 root-after-projection、双真实入口共享后缀、循环保留、有限遍历和稳定去重符合合同。 |
| A49 | passed | specs/pipeline-causal-flow-contract/spec.md | 有上游因果边的中间 Integration Event、Command、sender 或其他节点不得制造重复 Flow。 | 第二次独立核验确认 root-after-projection、双真实入口共享后缀、循环保留、有限遍历和稳定去重符合合同。 |
| A50 | passed | specs/pipeline-causal-flow-contract/spec.md | 两个各自具有真实入口证据、最终投影后均为零入度的入口必须分别生成两张 Flow，即使它们共享下游后缀。共享 Command、Event、节点名称或静态可达性不是自动 stitching 的充分证据。 | 第二次独立核验确认 root-after-projection、双真实入口共享后缀、循环保留、有限遍历和稳定去重符合合同。 |
| A51 | passed | specs/pipeline-causal-flow-contract/spec.md | 默认 Flow 不强制为 DAG，不拒绝合法循环，不折叠强连通分量。 | 第二次独立核验确认 root-after-projection、双真实入口共享后缀、循环保留、有限遍历和稳定去重符合合同。 |
| A52 | passed | specs/pipeline-causal-flow-contract/spec.md | 遍历使用稳定 visited node/edge 语义有限结束。 | 第二次独立核验确认 root-after-projection、双真实入口共享后缀、循环保留、有限遍历和稳定去重符合合同。 |
| A53 | passed | specs/pipeline-causal-flow-contract/spec.md | 入口可达的 visible cycle 必须保留，不能静默截断或伪装成 DAG。 | 第二次独立核验确认 root-after-projection、双真实入口共享后缀、循环保留、有限遍历和稳定去重符合合同。 |
| A54 | passed | specs/pipeline-causal-flow-contract/spec.md | 没有可证明入口的纯循环仍属于 raw Graph 事实，但不发明默认 Flow root。 | 第二次独立核验确认 root-after-projection、双真实入口共享后缀、循环保留、有限遍历和稳定去重符合合同。 |
| A55 | passed | specs/pipeline-causal-flow-contract/spec.md | 节点、边、entry 和 index 使用稳定排序；重复 raw edge、projected edge 和重复 entry identity 的结果必须确定。 | 第二次独立核验确认 root-after-projection、双真实入口共享后缀、循环保留、有限遍历和稳定去重符合合同。 |
| A56 | passed | specs/pipeline-causal-flow-contract/spec.md | 每个真实 root 生成： | 第二次独立核验确认 Flow JSON、Mermaid、index、identity、layout 和计数字段保持不变，未新增 process artifact。 |
| A57 | passed | specs/pipeline-causal-flow-contract/spec.md | 并统一生成： | 第二次独立核验确认 Flow JSON、Mermaid、index、identity、layout 和计数字段保持不变，未新增 process artifact。 |
| A58 | passed | specs/pipeline-causal-flow-contract/spec.md | Entry JSON 至少包含： | 第二次独立核验确认 Flow JSON、Mermaid、index、identity、layout 和计数字段保持不变，未新增 process artifact。 |
| A59 | passed | specs/pipeline-causal-flow-contract/spec.md | `entryId`； | 第二次独立核验确认 Flow JSON、Mermaid、index、identity、layout 和计数字段保持不变，未新增 process artifact。 |
| A60 | passed | specs/pipeline-causal-flow-contract/spec.md | `entryType`； | 第二次独立核验确认 Flow JSON、Mermaid、index、identity、layout 和计数字段保持不变，未新增 process artifact。 |
| A61 | passed | specs/pipeline-causal-flow-contract/spec.md | `nodeCount`； | 第二次独立核验确认 Flow JSON、Mermaid、index、identity、layout 和计数字段保持不变，未新增 process artifact。 |
| A62 | passed | specs/pipeline-causal-flow-contract/spec.md | `edgeCount`； | 第二次独立核验确认 Flow JSON、Mermaid、index、identity、layout 和计数字段保持不变，未新增 process artifact。 |
| A63 | passed | specs/pipeline-causal-flow-contract/spec.md | projected nodes； | 第二次独立核验确认 Flow JSON、Mermaid、index、identity、layout 和计数字段保持不变，未新增 process artifact。 |
| A64 | passed | specs/pipeline-causal-flow-contract/spec.md | projected edges。 | 第二次独立核验确认 Flow JSON、Mermaid、index、identity、layout 和计数字段保持不变，未新增 process artifact。 |
| A65 | passed | specs/pipeline-causal-flow-contract/spec.md | Index 至少包含 input identities、entry type summary、node/edge summary、`flowCount` 和每张 Flow 的 JSON/Mermaid reference。 | 第二次独立核验确认 Flow JSON、Mermaid、index、identity、layout 和计数字段保持不变，未新增 process artifact。 |
| A66 | passed | specs/pipeline-causal-flow-contract/spec.md | 本合同不改变既有 JSON、Mermaid、index wire shape、output root、slug 规则或 `flow` identity。连续链修正与回归证据不得通过增加 process artifact、process index 或第二个 generator id 实现。 | 第二次独立核验确认 Flow JSON、Mermaid、index、identity、layout 和计数字段保持不变，未新增 process artifact。 |
| A67 | passed | specs/pipeline-causal-flow-contract/spec.md | Issue #55 观察到旧项目中“外部事实到达 follow-up Command 后，后续业务链出现在另一张 Flow”的现象。当前目标按以下规则判断： | 第二次独立核验确认 Issue #55 连续链与独立真实入口语义形成回归证据，并明确不声明漂移下游兼容。 |
| A68 | passed | specs/pipeline-causal-flow-contract/spec.md | 如果 raw Graph evidence 连续，完整的 `Inbound Integration Event -> Command -> Domain Event -> follow-up Command` 必须在同一张 entry-centered Flow 中出现，不需要 process stitching。 | 第二次独立核验确认 Issue #55 连续链与独立真实入口语义形成回归证据，并明确不声明漂移下游兼容。 |
| A69 | passed | specs/pipeline-causal-flow-contract/spec.md | 如果拆分来自中间节点被错误提升为 root、隐藏路径未正确收缩或 relationship 丢失，应修复或以 regression fixture 防止回归。 | 第二次独立核验确认 Issue #55 连续链与独立真实入口语义形成回归证据，并明确不声明漂移下游兼容。 |
| A70 | passed | specs/pipeline-causal-flow-contract/spec.md | 如果两个节点各自具有独立入口证据且最终投影后均为零入度，两张 Flow 是正确结果。 | 第二次独立核验确认 Issue #55 连续链与独立真实入口语义形成回归证据，并明确不声明漂移下游兼容。 |
| A71 | passed | specs/pipeline-causal-flow-contract/spec.md | 多张真实入口 Flow 通过稳定 entry identity、index 和共享可见节点关联阅读；默认产品不自动推断它们属于同一个业务过程。 | 第二次独立核验确认 Issue #55 连续链与独立真实入口语义形成回归证据，并明确不声明漂移下游兼容。 |
| A72 | passed | specs/pipeline-causal-flow-contract/spec.md | 本次不使用长期未更新的 `cap4k-reference-content-studio` 作为验证门槛，使用仓库内等价 focused 与 Gradle functional fixture 提供自动化证据，不声明该下游项目兼容性。 | 第二次独立核验确认 Issue #55 连续链与独立真实入口语义形成回归证据，并明确不声明漂移下游兼容。 |
| A73 | passed | specs/pipeline-causal-flow-contract/spec.md | causal edge 选择、节点角色、入口资格、路径压缩、root、循环、去重和 Mermaid graph topology 由 Kotlin 生产代码拥有。 | 第二次独立核验确认图算法由 Kotlin/planner 拥有，模板仅呈现；未开放 process projection、自定义拓扑或第二产品入口。 |
| A74 | passed | specs/pipeline-causal-flow-contract/spec.md | Generator planner 把已计算结果规划为 JSON、Mermaid 和 index artifacts。 | 第二次独立核验确认图算法由 Kotlin/planner 拥有，模板仅呈现；未开放 process projection、自定义拓扑或第二产品入口。 |
| A75 | passed | specs/pipeline-causal-flow-contract/spec.md | Pebble 模板只负责写出 planner 提供的内容，不决定节点、边、入口、路径压缩或 Flow 数量。 | 第二次独立核验确认图算法由 Kotlin/planner 拥有，模板仅呈现；未开放 process projection、自定义拓扑或第二产品入口。 |
| A76 | passed | specs/pipeline-causal-flow-contract/spec.md | 项目用户可以启用或禁用 `flow`、配置 output root，并按模板合同调整呈现；不能注入自定义遍历、root 或 stitching 算法。 | 第二次独立核验确认图算法由 Kotlin/planner 拥有，模板仅呈现；未开放 process projection、自定义拓扑或第二产品入口。 |
| A77 | passed | specs/pipeline-causal-flow-contract/spec.md | 本合同不新增 topology DSL、arbitrary user projection 或 process profile。 | 第二次独立核验确认图算法由 Kotlin/planner 拥有，模板仅呈现；未开放 process projection、自定义拓扑或第二产品入口。 |
| A78 | passed | specs/pipeline-causal-flow-contract/spec.md | 需要不同图的高级消费者可以读取 raw Graph 并在 cap4k 产品边界之外生成自有视图。若未来多个项目形成稳定共同需求，应创建独立 Change，设计由插件拥有、命名明确、合同固定的 projection profile，而不是在模板中实现图语义。 | 第二次独立核验确认图算法由 Kotlin/planner 拥有，模板仅呈现；未开放 process projection、自定义拓扑或第二产品入口。 |
| A79 | passed | specs/pipeline-causal-flow-contract/spec.md | 保留 `surface.analyzer`、`pipeline.generator.flow` 和 output `flow` 的当前 identity。 | 第二次独立核验确认图算法由 Kotlin/planner 拥有，模板仅呈现；未开放 process projection、自定义拓扑或第二产品入口。 |
| A80 | passed | specs/pipeline-causal-flow-contract/spec.md | Analyzer Graph partition 的 Flow consumer 仍只有 `pipeline.generator.flow`，不得新增未实现的 process consumer 或 output。 | 第二次独立核验确认图算法由 Kotlin/planner 拥有，模板仅呈现；未开放 process projection、自定义拓扑或第二产品入口。 |
| A81 | passed | specs/pipeline-causal-flow-contract/spec.md | `CapabilityContractFacts`、AgentFacts、Public Docs 和 Skill 必须表达默认 entry-centered Flow、隐藏路径压缩、root-after-projection、cycle preservation 和唯一产品入口。 | 第二次独立核验确认图算法由 Kotlin/planner 拥有，模板仅呈现；未开放 process projection、自定义拓扑或第二产品入口。 |
| A82 | passed | specs/pipeline-causal-flow-contract/spec.md | Public Docs 与 Skill 必须说明连续 graph evidence 生成一张 Flow、两个真实 root 生成两张 Flow，以及共享后缀不触发自动 stitching。 | 第二次独立核验确认图算法由 Kotlin/planner 拥有，模板仅呈现；未开放 process projection、自定义拓扑或第二产品入口。 |
| A83 | passed | specs/pipeline-causal-flow-contract/spec.md | Public Docs、Skill 和 AgentFacts 不得宣称 process projection、process output 或用户自定义拓扑算法已经可用。 | 第二次独立核验确认图算法由 Kotlin/planner 拥有，模板仅呈现；未开放 process projection、自定义拓扑或第二产品入口。 |
| A84 | passed | specs/pipeline-causal-flow-contract/spec.md | Runtime 对本合同为 `verified-no-change` 或 `not-applicable`，除非实际实现修改了 Runtime contract。 | 第二次独立核验确认图算法由 Kotlin/planner 拥有，模板仅呈现；未开放 process projection、自定义拓扑或第二产品入口。 |
| A85 | passed | specs/pipeline-causal-flow-contract/spec.md | Focused tests 必须覆盖： | 第二次独立核验确认 focused 与 Gradle functional fixture 覆盖完整链、压缩、root、共享后缀、循环、无效 evidence 和产物物化；全部治理检查有通过记录。 |
| A86 | passed | specs/pipeline-causal-flow-contract/spec.md | 完整入站 Integration Event 到 follow-up Command 的单 fixture，并断言一张 Flow； | 第二次独立核验确认 focused 与 Gradle functional fixture 覆盖完整链、压缩、root、共享后缀、循环、无效 evidence 和产物物化；全部治理检查有通过记录。 |
| A87 | passed | specs/pipeline-causal-flow-contract/spec.md | Handler 与任意长度 Entity Method 路径隐藏和收缩； | 第二次独立核验确认 focused 与 Gradle functional fixture 覆盖完整链、压缩、root、共享后缀、循环、无效 evidence 和产物物化；全部治理检查有通过记录。 |
| A88 | passed | specs/pipeline-causal-flow-contract/spec.md | 有上游节点不制造重复 root； | 第二次独立核验确认 focused 与 Gradle functional fixture 覆盖完整链、压缩、root、共享后缀、循环、无效 evidence 和产物物化；全部治理检查有通过记录。 |
| A89 | passed | specs/pipeline-causal-flow-contract/spec.md | 两个真实入口和共享下游保持两张 Flow； | 第二次独立核验确认 focused 与 Gradle functional fixture 覆盖完整链、压缩、root、共享后缀、循环、无效 evidence 和产物物化；全部治理检查有通过记录。 |
| A90 | passed | specs/pipeline-causal-flow-contract/spec.md | fan-out、merge、visible cycle、纯循环、稳定去重和 source evidence； | 第二次独立核验确认 focused 与 Gradle functional fixture 覆盖完整链、压缩、root、共享后缀、循环、无效 evidence 和产物物化；全部治理检查有通过记录。 |
| A91 | passed | specs/pipeline-causal-flow-contract/spec.md | 合法零 Flow 与无效 Graph evidence 的区别。 | 第二次独立核验确认 focused 与 Gradle functional fixture 覆盖完整链、压缩、root、共享后缀、循环、无效 evidence 和产物物化；全部治理检查有通过记录。 |
| A92 | passed | specs/pipeline-causal-flow-contract/spec.md | Gradle functional fixture 必须实际执行 `cap4kAnalysisPlan` 和 `cap4kAnalysisGenerate`，并核对： | 第二次独立核验确认 focused 与 Gradle functional fixture 覆盖完整链、压缩、root、共享后缀、循环、无效 evidence 和产物物化；全部治理检查有通过记录。 |
| A93 | passed | specs/pipeline-causal-flow-contract/spec.md | entry JSON、Mermaid、index 均实际生成； | 第二次独立核验确认 focused 与 Gradle functional fixture 覆盖完整链、压缩、root、共享后缀、循环、无效 evidence 和产物物化；全部治理检查有通过记录。 |
| A94 | passed | specs/pipeline-causal-flow-contract/spec.md | `entryId`、entry type、节点/边计数和 `flowCount` 一致； | 第二次独立核验确认 focused 与 Gradle functional fixture 覆盖完整链、压缩、root、共享后缀、循环、无效 evidence 和产物物化；全部治理检查有通过记录。 |
| A95 | passed | specs/pipeline-causal-flow-contract/spec.md | Handler 与 Entity Method 不进入公开 Flow； | 第二次独立核验确认 focused 与 Gradle functional fixture 覆盖完整链、压缩、root、共享后缀、循环、无效 evidence 和产物物化；全部治理检查有通过记录。 |
| A96 | passed | specs/pipeline-causal-flow-contract/spec.md | 不生成 process artifact、第二套 Flow output 或已退役 task。 | 第二次独立核验确认 focused 与 Gradle functional fixture 覆盖完整链、压缩、root、共享后缀、循环、无效 evidence 和产物物化；全部治理检查有通过记录。 |
| A97 | passed | specs/pipeline-causal-flow-contract/spec.md | Change Verify 还必须运行完整 Gradle `check`、capability facts export/validate/test、Skill validator、current Runtime facts validator、PR workflow tests 和 `git diff --check`。自动化证据与真实项目证据分别记录；未运行长期漂移的 downstream 项目必须明确声明，不得形成兼容性结论。 | 第二次独立核验确认 focused 与 Gradle functional fixture 覆盖完整链、压缩、root、共享后缀、循环、无效 evidence 和产物物化；全部治理检查有通过记录。 |

## Checks

_No Runtime checks were recorded._

## Blockers

_None._

## Risks and skipped work

- 未来新增隐藏 causal role 时必须同步 hidden role、relationship contract、tests 与 capability propagation。
- 当前每个 source 到隐藏节点只保留首条稳定 raw path evidence，符合至少一条 evidence 的当前合同，不代表多路径 evidence 支持。
- 未运行长期漂移的 cap4k-reference-content-studio，不能据此宣称 downstream 当前兼容。
- 部分文件存在 LF/CRLF 工作树提示，但 git diff --check 已通过。

## Previous iterations

| Goal cycle | Iteration | Attempt | Outcome | Unresolved | Summary | Completed |
| ---: | ---: | ---: | --- | --- | --- | --- |
| 1 | 1 | 1 | pass | — | 通过。独立核验确认现有 FlowGraphSupport 已满足 entry-centered 连续因果链、任意长度隐藏路径压缩、投影后 root、双真实入口共享后缀、循环与稳定去重语义；本 Change 补齐 focused/functional 回归及 Public Docs/Skill 投影，未新增 process projection、第二产品入口或用户自定义算法。 | 2026-08-14T04:44:59.320Z |
| 1 | 1 | 1 | recovery | — | Local Runtime was unavailable at Archive ready; the synchronized implementation must be verified again. | 2026-08-14T04:47:44.274Z |
| 1 | 1 | 2 | pass | — | 第二次独立只读核验通过。同一候选保持 entry-centered 连续因果链、任意长度隐藏路径压缩、投影后 root、双真实入口共享后缀、循环保留和稳定去重；新增 focused/functional 回归及 Public Docs/Skill 投影，未恢复 Validator/unique-validator、SagaCoordinator、process projection、第二 Flow 入口或模板内自定义拓扑。 | 2026-08-14T04:52:56.119Z |

## Conclusion

第二次独立只读核验通过。同一候选保持 entry-centered 连续因果链、任意长度隐藏路径压缩、投影后 root、双真实入口共享后缀、循环保留和稳定去重；新增 focused/functional 回归及 Public Docs/Skill 投影，未恢复 Validator/unique-validator、SagaCoordinator、process projection、第二 Flow 入口或模板内自定义拓扑。
