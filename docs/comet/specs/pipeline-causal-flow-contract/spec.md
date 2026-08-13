# Pipeline Causal Flow 合同

## 目标

默认 `flow` output 是面向业务因果链的静态最小投影：从一个有代码证据的实际入口节点出发，沿可验证的 Command/Event 因果关系形成入口中心的最小有向子图。它帮助人类和 Agent 阅读代码结构，不证明业务正确性、运行时执行顺序、事务边界、消息必达或业务结果。

## 默认产品语义

- 一张 Flow 对应一个具体入口节点及其沿默认因果关系可达的最小有向子图。
- 默认可见节点为真实入口、Command、Domain Event、Integration Event；入口节点不是新增的抽象 `Entry` 节点。
- HTTP Controller/endpoint、RPC adapter、Inbound Integration Event、Job method 和未来其他有生产代码证据的触发节点，都以实际 Graph node 身份承担入口角色。
- Query、Capability、Validator、read-side dependency、普通技术调用和其他非 Command/Event 关系可以保留在底层 Graph，但不进入默认业务因果链。
- 默认 Flow 不负责跨入口、跨服务或跨 context 的 process model；可选 process projection 必须另有事实模型和独立产品合同。

## Root 规则

只有同时满足以下条件的节点才可生成默认 Flow root：

1. 生产代码、明确 metadata 或可验证的 Analyzer relationship 能证明该节点具备入口资格；
2. 完成最小可见投影后，该节点没有上游因果边。

入度为零只是必要条件，不是入口资格的充分条件。孤立 Command/Event、因 metadata 缺失而失去上游的节点、仅因为名字或包路径像入口的节点，都不得自动升级为 root。

每个真实 root 分别生成一张入口中心 Flow。多个真实 root 可以共享下游后缀并各自重复该后缀；不能为了消除重复任意选择一个 root 或自动拼成一张 process Flow。

## 可见性与路径投影

默认隐藏但不能从 raw Graph 删除的节点：

- Command Handler；
- Domain Event Handler；
- Integration Event Handler；
- Entity Method。

路径投影必须收缩任意长度的隐藏路径：当两个可见节点之间存在 raw causal path，且内部节点全部属于隐藏集合时，建立直接投影边。例如：

```text
Command -> Command Handler -> Entity Method A -> Entity Method B -> Domain Event
```

投影为：

```text
Command -> Domain Event
```

以及：

```text
Domain Event -> Event Handler -> Command
```

第二条路径投影为：

```text
Domain Event -> Command
```

分叉、汇合和多条独立路径必须保留。投影边按稳定 identity 去重，不得依赖输入目录或遍历顺序。投影算法及其诊断/测试证据必须保留对应隐藏路径、raw relationship reference 或等价 source evidence；本 Change 不为此强制改变现有公开 JSON、Mermaid 或 index wire shape。

入口节点本身不可因为同时具有 Controller、Handler、sender 或 Event 等多个技术身份而被复制；其可见性由它在当前 Flow 中承担的入口角色决定。

## 循环、停止和完整性

- 默认 Flow 不强制为 DAG，不拒绝合法循环，不折叠强连通分量。
- 遍历使用稳定的 visited node/edge 语义有限结束；入口可达循环必须如实保留，不能静默截断或假装 DAG。
- 没有可证明入口的纯循环仍属于 raw Graph 事实，但不发明默认 Flow root。
- `flow` 只消费 `AnalyzerSnapshot.graph` 及其 completeness；不得从 Drawing Board 或 Aggregate Structure 补造因果关系。
- 入口证据缺失、relationship 不可解析、identity 冲突或会使路径收缩不可信的完整性问题必须给出可操作诊断；合法零 Flow 与分析不完整必须可区分。
- Flow 只陈述静态可达性，不把节点顺序解释成执行顺序、消息投递顺序、事务顺序或业务结果。

## Issue #55 与 process projection

[Issue #55](https://github.com/LDmoxeii/cap4k/issues/55) 观察到的“外部事实链到达后续 Command，但中间 Command 又生成独立 Flow”首先按 root 资格、最终投影后的入度和隐藏路径收缩检查。若中间技术节点已有上游因果关系，不应继续生成重复 root；若两个节点各自有独立入口证据并在投影后零入度，生成两张 Flow 是正确行为。

默认 Flow 不自动 process stitching。是否建立独立 process projection，需要决定它的事实模型、入口/过程边界、跨服务语义、稳定 identity、消费者和验证证据；在这些决定确认前，它只是 [Issue #55](https://github.com/LDmoxeii/cap4k/issues/55) 的未决后续，不属于默认 `flow` contract。

## Flow 产品入口

Build candidate 已落实已确认的唯一入口边界：

- Pipeline analysis generator `pipeline.generator.flow` / output `flow` 由 `cap4kAnalysisPlan` 和 `cap4kAnalysisGenerate` 使用；
- 独立 `cap4k-plugin-code-analysis-flow-export` 模块、plugin id、`cap4kFlow*` tasks 和 Central publication marker 已从当前主线候选删除；
- JSON、Mermaid、index 及后续 Flow artifacts 统一由 Pipeline Flow 拥有，不保留第二套公开任务、alias 或兼容入口。

历史 archive/spec/plan 可以保留旧 exporter 事实作为历史证据，但不能被解释为当前支持面。上述实现状态仍须由 Verify 的实际 diff、focused tests、完整 Gradle 检查和 capability validators 独立确认。
## Capability 与公开投影

- 保留 `surface.analyzer`、`pipeline.generator.flow` 和公开 output `flow` 的现有 identity。
- 已退役的独立 flow-export 不保留第二个公开 capability identity；当前代码、Gradle tasks、模块/发布面和公开投影不得恢复该入口。
- `CapabilityContractFacts` 必须表达 Flow 对 `AnalyzerSnapshot.graph` 的依赖、默认可见/隐藏角色、projection responsibility 和到 AgentFacts、Public Docs、Skill 的传播闭包。
- 入口类别由代码事实开放扩展，不通过 Public Docs、Skill 或永久手写 taxonomy 冻结。
- Public Docs、Skill 和 AgentFacts 只能描述已落地代码状态；旧 v1 中 Handler/Entity Method 可见的历史行为不能继续作为当前默认合同文案。

## 验证证据

- focused projection tests：实际 Controller、RPC adapter、Inbound Event、Job 作为 root，不生成额外 Entry 节点；入口示例不是封闭 taxonomy。
- projection tests：Command Handler、Event Handler、单层/多层 Entity Method 隐藏，任意长度收缩，fan-out/merge、稳定去重和 source evidence。
- root tests：最终投影后判 root，中间 sender/Event 不制造 duplicate Flow，多个真实 root 仍分别输出。
- [Issue #55](https://github.com/LDmoxeii/cap4k/issues/55) regression fixture：外部事实 -> Command -> Domain Event -> 后续 Command 不依赖 process stitching 才能得到连续入口 Flow。
- cycle/completeness tests：循环有限遍历、关系保留、纯循环不伪造 root、缺失/冲突 Graph evidence 不静默输出完整 Flow。
- 自动化 focused tests、capability validators 与真实项目 Flow 可读性验证分别记录；Shape 不执行这些实现验证，当前 Build 必须在 Verify 前提供对应证据。

### 自动化证据

- `FlowArtifactPlannerTest` 覆盖入口角色开放扩展、Handler/Entity Method 隐藏路径收缩、fan-out/merge、root、重复 Flow、循环和 source evidence。
- Pipeline Gradle functional fixture 覆盖 `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` 对 JSON、Mermaid 和 index artifacts 的实际物化。
- 完整 Gradle `check` 与 capability contract validators 独立验证编译、模块退役、公开 output identity 和传播闭包；这些结果由 Comet Runtime 管理的 `verification.md` 记录，不由本合同预先宣称通过。

### 真实项目 Flow artifact 阅读记录

2026-08-13 使用真实业务项目 `only-danmuku-zero` 的 `only-danmuku-adapter/build/cap4k-code-analysis` 作为 Analyzer raw graph 输入，通过当前候选的复合构建执行 `cap4kAnalysisPlan` 和 `cap4kAnalysisGenerate`，得到以下独立阅读证据：

- Pipeline `flow` 生成 48 张实际 Controller method 入口 Flow，共 97 个 `index.json`、entry JSON 和 Mermaid 文件；`index.json` 的 `flowCount=48`、`entryTypes=[controllermethod]`。
- 逐份读取 index 引用的 48 组 JSON/Mermaid：文件均存在，entry identity 一致，JSON 的 node/edge count 与数组一致，Mermaid 均含 `flowchart TD` 及 JSON 中的可见节点，发现 0 个一致性问题。
- 抽样入口 `edu.only4.danmuku.adapter.portal.api.admin.AdminAccountController::login` 以真实 `controllermethod` 身份作为 root，直接连接 `RecordLoginLogCmd.Request` Command；产物没有合成 `Entry` 节点，也没有把 Command Handler 放进可见链。
- 该记录证明当前 Pipeline Flow artifact 对真实项目 adapter 代码证据可生成、可索引并可读；它仍只陈述静态可达性，不等同于运行时顺序或领域正确性。
- 真实项目完整多模块 raw bundle 仍含旧 `design-elements.json` 已移除字段和 domain relationship 短 identity 缺失端点。当前候选会以可操作诊断失败而不是输出伪完整 Flow；完整 Snapshot transport、跨模块 identity 和 per-partition completeness 继续由 Issue #25 管理。本次可读性核验为隔离 Flow consumer，使用 adapter raw graph 并将可选 design projection 输入置为空数组，不把这一做法解释为完整多模块证据。

## 本 Change 的边界

- 本合同是 canonical target contract，合同文本本身不构成实现证据；当前 Build candidate 是否满足语义，必须以实际 diff、测试和 validator 结果为准。
- Shape 阶段不修改产品代码。当前 Build 必须在 Pipeline Flow 中完成真实入口判定、最终投影后 root、Handler/Entity Method 任意长度隐藏路径收缩、fan-out/merge、cycle、稳定去重和 Issue #55 regression。
- 当前 Build 必须删除独立 `cap4k-plugin-code-analysis-flow-export` 模块、plugin id、`cap4kFlow*` tasks、Central publication marker 和当前主线引用；不保留 alias、兼容任务或第二套公开 Flow 入口。历史 archive/spec/plan 保持历史原文。
- Pipeline `flow` 保持 JSON、Mermaid 和 index artifacts 的唯一 owner；除非现有测试证明合同无法表达，本 Change 不借投影修正任意改变公开 wire shape。
- 可选 process projection 不进入当前 Build。其他未纳入范围的后续实现必须从届时最新 `origin/master` 创建独立 Comet Change，读取本合同和 [Issue #55](https://github.com/LDmoxeii/cap4k/issues/55)，并通过独立 PR 合入 `master`。