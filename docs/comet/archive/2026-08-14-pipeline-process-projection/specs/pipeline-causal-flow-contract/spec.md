# Pipeline Causal Flow 合同

## 目标

Pipeline `flow` 是面向业务因果链的默认静态投影。它从一个有生产代码或明确 relationship evidence 的实际入口出发，沿可验证的 Command/Event 因果关系生成入口中心的最小有向子图，帮助人类和 Agent 阅读代码结构。

Flow 只陈述静态连接与可达性，不证明业务正确性、运行时执行顺序、事务边界、消息必达、重试结果或最终业务结果。

## 产品边界

- 一张 Flow 对应一个具体真实入口及其沿默认因果关系可达的最小投影。
- 默认产品不提供自动或独立 process projection，不把多个真实入口拼成一个业务过程。
- Pipeline generator `pipeline.generator.flow`、generator id `flow` 和 output id `flow` 是唯一公开 Flow 产品身份。
- `cap4kAnalysisPlan` 和 `cap4kAnalysisGenerate` 是 Flow planning 与 generation 的公开 task lane。
- 已退役的 `cap4k-plugin-code-analysis-flow-export`、`cap4kFlow*` tasks、plugin id、alias 和第二套 output contract 不得恢复。

## 事实输入

- Flow 只消费 `AnalyzerSnapshot.graph` 及其 completeness、freshness、source identity 和 diagnostics。
- Drawing Board Design Projection 与 Aggregate Structure 不得补造 Flow 节点、边、入口或 process 关系。
- raw Graph 可以包含默认 Flow 不展示的技术事实，例如 Query、Capability、普通 `validator` observation 和 read-side dependency。
- `unique-validator` 等已退役 Aggregate Structure 类型不得因 Flow 重新出现。
- Graph 缺失、不可解析 relationship、identity 冲突或会使投影不可信的完整性问题必须产生可操作诊断；合法零 Flow 与分析不完整必须可区分。

## 可见节点与隐藏节点

默认可见节点：

- 具备入口证据的实际代码节点；
- Command；
- Domain Event；
- Integration Event。

默认隐藏但保留在 raw Graph 中的节点：

- Command Handler；
- Domain Event Handler；
- Integration Event Handler；
- Entity Method。

Query、Capability、普通 Validator、read-side dependency 和非 Command/Event 技术关系不进入默认业务因果链。

入口节点不是新增的抽象 `Entry` 节点。HTTP Controller/endpoint、RPC adapter、Inbound Integration Event、Job method 和未来其他有生产 relationship evidence 的触发节点，都以其实际 Graph node 身份承担入口角色。入口类别不是由 Public Docs、Skill 或永久手写封闭 taxonomy 定义。

## 因果关系与路径压缩

默认 Flow 只处理生产 Analyzer 明确识别的 causal relationship，以及从实际入口节点到 Command 的可验证 `*ToCommand` relationship。

当两个可见节点之间存在 raw causal path，且内部节点全部属于隐藏集合时，建立一条直接投影边。隐藏路径长度不设固定上限。

例如：

```text
Command
-> Command Handler
-> Entity Method A
-> Entity Method B
-> Domain Event
```

投影为：

```text
Command -> Domain Event
```

以及：

```text
Integration Event
-> Integration Event Handler
-> Command
-> Command Handler
-> Entity Method
-> Domain Event
-> Domain Event Handler
-> Follow-up Command
```

完整链投影为：

```text
Integration Event -> Command -> Domain Event -> Follow-up Command
```

Command 和 Event 是可见边界，因此路径在这些节点处分段压缩，不把整条链压成入口到最终 Command 的单边。

路径压缩只穿过已知隐藏因果角色。遇到既不可见、也不属于隐藏集合的未知 Graph 节点时，不把它自动视为透明中间节点。新增可压缩技术角色属于 Analyzer contract 变化，必须由生产代码、测试和 capability propagation 明确声明。

分叉、汇合和共享后缀必须保留。raw edge 与 projected edge 使用稳定 identity 去重，不依赖输入目录或遍历顺序。投影实现保留至少一条对应 raw path evidence 供诊断和测试使用；是否扩展为多路径 evidence 或公开 wire 字段属于独立合同变化，不在本合同中隐式增加。

## Root 与 Flow 数量

只有同时满足以下条件的节点才生成默认 Flow root：

1. 生产代码、metadata 或可验证 relationship 能证明节点具备入口资格；
2. 完成可见投影和隐藏路径压缩后，该节点没有上游因果边；
3. 该节点在最终投影中存在下游因果边。

入度为零只是必要条件，不是入口资格的充分条件。孤立 Command/Event、因 metadata 缺失而失去上游的节点、仅因名称或包路径像入口的节点，都不得自动升级为 root。

有上游因果边的中间 Integration Event、Command、sender 或其他节点不得制造重复 Flow。

两个各自具有真实入口证据、最终投影后均为零入度的入口必须分别生成两张 Flow，即使它们共享下游后缀。共享 Command、Event、节点名称或静态可达性不是自动 stitching 的充分证据。

## 循环、停止与确定性

- 默认 Flow 不强制为 DAG，不拒绝合法循环，不折叠强连通分量。
- 遍历使用稳定 visited node/edge 语义有限结束。
- 入口可达的 visible cycle 必须保留，不能静默截断或伪装成 DAG。
- 没有可证明入口的纯循环仍属于 raw Graph 事实，但不发明默认 Flow root。
- 节点、边、entry 和 index 使用稳定排序；重复 raw edge、projected edge 和重复 entry identity 的结果必须确定。

## 公开输出合同

每个真实 root 生成：

```text
flows/<entry-slug>.json
flows/<entry-slug>.mmd
```

并统一生成：

```text
flows/index.json
```

Entry JSON 至少包含：

- `entryId`；
- `entryType`；
- `nodeCount`；
- `edgeCount`；
- projected nodes；
- projected edges。

Index 至少包含 input identities、entry type summary、node/edge summary、`flowCount` 和每张 Flow 的 JSON/Mermaid reference。

本合同不改变既有 JSON、Mermaid、index wire shape、output root、slug 规则或 `flow` identity。连续链修正与回归证据不得通过增加 process artifact、process index 或第二个 generator id 实现。

## Issue #55 收口规则

Issue #55 观察到旧项目中“外部事实到达 follow-up Command 后，后续业务链出现在另一张 Flow”的现象。当前目标按以下规则判断：

- 如果 raw Graph evidence 连续，完整的 `Inbound Integration Event -> Command -> Domain Event -> follow-up Command` 必须在同一张 entry-centered Flow 中出现，不需要 process stitching。
- 如果拆分来自中间节点被错误提升为 root、隐藏路径未正确收缩或 relationship 丢失，应修复或以 regression fixture 防止回归。
- 如果两个节点各自具有独立入口证据且最终投影后均为零入度，两张 Flow 是正确结果。
- 多张真实入口 Flow 通过稳定 entry identity、index 和共享可见节点关联阅读；默认产品不自动推断它们属于同一个业务过程。
- 本次不使用长期未更新的 `cap4k-reference-content-studio` 作为验证门槛，使用仓库内等价 focused 与 Gradle functional fixture 提供自动化证据，不声明该下游项目兼容性。

## 算法、模板与用户定制边界

- causal edge 选择、节点角色、入口资格、路径压缩、root、循环、去重和 Mermaid graph topology 由 Kotlin 生产代码拥有。
- Generator planner 把已计算结果规划为 JSON、Mermaid 和 index artifacts。
- Pebble 模板只负责写出 planner 提供的内容，不决定节点、边、入口、路径压缩或 Flow 数量。
- 项目用户可以启用或禁用 `flow`、配置 output root，并按模板合同调整呈现；不能注入自定义遍历、root 或 stitching 算法。
- 本合同不新增 topology DSL、arbitrary user projection 或 process profile。
- 需要不同图的高级消费者可以读取 raw Graph 并在 cap4k 产品边界之外生成自有视图。若未来多个项目形成稳定共同需求，应创建独立 Change，设计由插件拥有、命名明确、合同固定的 projection profile，而不是在模板中实现图语义。

## Capability 与公开投影

- 保留 `surface.analyzer`、`pipeline.generator.flow` 和 output `flow` 的当前 identity。
- Analyzer Graph partition 的 Flow consumer 仍只有 `pipeline.generator.flow`，不得新增未实现的 process consumer 或 output。
- `CapabilityContractFacts`、AgentFacts、Public Docs 和 Skill 必须表达默认 entry-centered Flow、隐藏路径压缩、root-after-projection、cycle preservation 和唯一产品入口。
- Public Docs 与 Skill 必须说明连续 graph evidence 生成一张 Flow、两个真实 root 生成两张 Flow，以及共享后缀不触发自动 stitching。
- Public Docs、Skill 和 AgentFacts 不得宣称 process projection、process output 或用户自定义拓扑算法已经可用。
- Runtime 对本合同为 `verified-no-change` 或 `not-applicable`，除非实际实现修改了 Runtime contract。

## 验证合同

Focused tests 必须覆盖：

- 完整入站 Integration Event 到 follow-up Command 的单 fixture，并断言一张 Flow；
- Handler 与任意长度 Entity Method 路径隐藏和收缩；
- 有上游节点不制造重复 root；
- 两个真实入口和共享下游保持两张 Flow；
- fan-out、merge、visible cycle、纯循环、稳定去重和 source evidence；
- 合法零 Flow 与无效 Graph evidence 的区别。

Gradle functional fixture 必须实际执行 `cap4kAnalysisPlan` 和 `cap4kAnalysisGenerate`，并核对：

- entry JSON、Mermaid、index 均实际生成；
- `entryId`、entry type、节点/边计数和 `flowCount` 一致；
- Handler 与 Entity Method 不进入公开 Flow；
- 不生成 process artifact、第二套 Flow output 或已退役 task。

Change Verify 还必须运行完整 Gradle `check`、capability facts export/validate/test、Skill validator、current Runtime facts validator、PR workflow tests 和 `git diff --check`。自动化证据与真实项目证据分别记录；未运行长期漂移的 downstream 项目必须明确声明，不得形成兼容性结论。

## 非目标

- 自动 process stitching；
- 跨服务或跨 bounded context 的业务过程推断；
- Runtime trace、事务或消息顺序重建；
- 普通 Validator raw Graph observation 的退役；
- raw path evidence 的公开 wire 扩展或多路径 evidence redesign；
- 用户注入自定义图算法；
- 恢复 standalone flow-export。