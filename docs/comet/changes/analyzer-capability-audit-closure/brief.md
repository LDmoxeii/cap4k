# Outcome

创建一次独立的中文 Comet Native Change，收口 Analyzer 审计并归档三份 canonical target contract。当前事实基线是 `origin/master@226e303697d7ee099a1cfab8e2fcc06076ea36df`，包含 PR #186 的最新 Issue、Comet、Skill 和 CI 治理规则。

本 Change 先确认 Analyzer 的产品边界、目标合同、实现缺口和后续 Issue 归属。它可以在具备清晰独立边界时继续完成对应产品实现；Shape 必须先通过只读代码调查确定并写明本 Change 的实施范围，Build 只执行已确认范围。如果某个实现单元无法在本 Change 内独立 Build、Verify 和记录真实证据，则保留为后续 Issue/implementation Change。本 Change 不把旧 `framework-capability-audit` 分支整体合回主线。

# Scope

- 只读恢复旧审计中仍然成立的 Analyzer 事实，并与最新 `origin/master` 的代码、测试、公开材料、AgentFacts 和 Issue 状态交叉核对。
- 将旧审计的 `analyzer-snapshot-contract`、`analyzer-aggregate-structure` 和 `analyzer-causal-flow` 映射并重塑为三份不重复的 canonical target contract：
  - `analyzer-drawing-board-contract`：规范化 Design JSON 与 Drawing Board design blocks 的往返边界；
  - `analyzer-evidence-model`：`graph`、`designProjection`、`aggregateStructure` 三类 Analyzer 事实及其 ownership、transport、完整性和消费者边界；
  - `pipeline-causal-flow-contract`：入口中心的默认业务因果 Flow 投影。
- 单独解释 `drawing_board_aggregate_elements.json` 的代码事实来源、当前内容、用户价值、非 Design JSON 身份以及保留、删除、SQL/Schema projection 三种后续方向的含义。
- 明确 [Issue #25](https://github.com/LDmoxeii/cap4k/issues/25) 继续负责 Analyzer evidence model 的实现优先级和状态，[Issue #55](https://github.com/LDmoxeii/cap4k/issues/55) 继续负责 entry-centered Flow 的重复/分裂及 process projection 议题；Aggregate Structure 可以进入本 Change 的 Build，不能独立验收的剩余工作再由独立 Issue 和 implementation Change 管理。
- 对未纳入本 Change Build 的独立实施切片，规定从届时最新 `origin/master` 建立工作树、创建独立 Comet Change、读取已归档合同、只验证本切片并通过独立 PR 合入 `master` 的后续流程。

# Non-goals

- 不在 Shape 阶段修改 Analyzer、compiler、IR source、canonical assembler、Flow planner/exporter、Drawing Board planner、Agent API、CapabilityContractFacts、validator、Public Docs、Skill 或构建配置；Shape 通过只读调查确定独立边界和可验证性，并在最终确认前锁定 Build 范围。
- 不恢复旧 Change，不继续旧 `framework-capability-audit` 分支，不整体 merge、rebase 或 cherry-pick 其历史提交。
- 不把旧审计 archive、旧 Comet 安装文件、旧 Runtime 规格或旧安装清单带回主线。
- 不把目标合同同步成 Public Docs、AgentFacts 或 Skill 的当前支持能力；这些投影只能描述已经落地的代码状态。
- 不把 `drawing_board_aggregate_elements.json` 直接删除、重命名、重新实现或转成 SQL/Schema projection。
- 不在本 Change 默认引入自动 process stitching，不冻结 HTTP、RPC、Integration Event、Job 等入口为永久封闭 taxonomy。
- 不把 spec、审计 Verifier 的 `passed` 或 Issue 文本当成产品实现完成证据。
- 不在本 Change 内预先创建三个后续实施分支，也不在合同边界之外扩展 Analyzer 实现；可独立验收的切片必须在 Shape 最终确认前写入当前 Build 范围。

# Acceptance examples

- A1：Change 从 `origin/master@226e303697d7ee099a1cfab8e2fcc06076ea36df` 的干净隔离 worktree 创建，当前 Shape 分支为 `docs/analyzer-capability-audit-closure`，旧审计 worktree 只作为只读证据；若最终 Build 包含产品代码，分支在 Build 前调整为 `feature/analyzer-capability-audit-closure`。
- A2：brief、三份目标合同和后续 verification 使用中文；代码标识、路径、命令、Issue 编号和稳定 wire/status 值按原样保留。
- A3：旧三份 Analyzer spec 到新三份 contract 的映射明确，说明哪些内容被合并、抽出、保留或不再作为 canonical contract，且不存在语义重复的第四份 spec。
- A4：Drawing Board contract 明确 normalized Design JSON 与 designProjection 的往返等价关系，恢复的是规范化战术设计，不恢复任意代码结构；Aggregate Structure、Entity Method、Repository 等结构证据不进入该 design projection。
- A5：Evidence model contract 明确 `AnalyzerSnapshot.graph`、`AnalyzerSnapshot.designProjection`、`AnalyzerSnapshot.aggregateStructure` 是三类不同事实；可以共享一次 compiler observation 和 raw transport，但 schema、canonical owner、完整性状态和消费者不得混淆。
- A6：Evidence model 单独解释 `drawing_board_aggregate_elements.json` 当前从 `AggregateElementMetadata`、compiler output、IR source 和 canonical merge 产生，包含哪些 carrier 事实，解决工程审计/Agent 读取/结构核验问题，并明确它不是 Design JSON、不是业务真相、不是 SQL schema。
- A7：Evidence model 明确 `schema`、`entity`、`repository`、`factory`、`strong-id`、`projection` 是当前目标闭集；`specification`、`unique-query`、`unique-query-handler`、`unique-validator` 是已退役 cap4k 能力的 drift，不得被合同恢复或伪装成兼容能力。
- A8：Causal Flow contract 明确默认 Flow 面向业务因果链，默认可见入口、Command、Event，默认隐藏 Handler/Entity Method，Query、Capability、Validator、read-side 等只保留在底层 Graph；入口节点使用实际 Controller/RPC adapter/Inbound Event/Job 等代码节点身份。
- A9：Causal Flow contract 明确 root 必须同时具备代码证据和最终最小投影后的零入度；路径收缩支持任意长度、分支和汇合；保留 source evidence；visited 使循环有限遍历；不得强制 DAG、静默断链或自动 process stitching。
- A10：Causal Flow contract 明确 Issue #55 的 duplicate Flow 首先属于入口判定或路径收缩问题；有独立入口证据的多个 root 可以各自产生 Flow；是否提供可选 process projection 是独立未决能力。
- A11：`cap4k-plugin-code-analysis-flow-export` 与 Pipeline `flow` 当前同时存在的代码事实被如实记录；本 Change Build 退役独立 plugin，并由 Pipeline `flow` 成为唯一产品入口，完整验证模块、plugin id、任务、发布面、当前能力投影和唯一入口。
- A12：每份合同都明确目标合同不等于当前实现满足，自动化证据与真实项目证据分开记录，后续实施切片不得把未实现目标提前同步为 Public Docs、AgentFacts 或 Skill 当前能力。
- A13：Issue 与后续实施规则明确：#25 继续管理未纳入当前 Build 的 Snapshot 分区、transport/wire 和 Drawing Board round-trip；#55 继续管理 Flow 状态及可选 process projection。当前 Build 的 Aggregate Structure 与 Flow 只有在真实代码和验证证据完成后才计为落地。
- A14：Shape 完成后停在用户审阅；在确认前必须根据只读代码调查明确本 Change 的具体实施切片。只有用户确认 brief、三份合同、实施范围、未决项和验收边界后，才允许执行 Comet Native 后续推进。
- A15：compiler 对 Aggregate Structure 的 `type` 只接受 `schema/entity/repository/factory/strong-id/projection`；四个退役值和其他 unknown/blank 值明确失败，同时不删除或改变 Spring Data JPA `Specification` 查询谓词能力。
- A16：`aggregate-elements.json` 继续由生产 metadata 生成；必需的空数组合法，多 input directory 的相同记录稳定去重，字段冲突携带来源失败，最终记录按稳定 identity 排序。
- A17：canonical model 只有一个独立 Aggregate Structure owner；`AnalysisGraphModel` 与 `DrawingBoardModel` 不再重复拥有同一 `aggregateElements`。本 Change 可以暂时保留平铺 raw `IrAnalysisSnapshot` transport，但不得继续混淆 canonical ownership。
- A18：保留现有 `drawing_board_aggregate_elements.json` 文件名、输出位置和当前公开 output identity，改由独立 Aggregate Structure canonical evidence 驱动；它不进入 Design JSON、Drawing Board design blocks、Graph 或 SQL/Schema projection。本 Change 不新建语义重复的公开 generator/output identity。
- A19：Pipeline Flow 使用真实 Graph node 作为入口，入口 taxonomy 对代码证据开放扩展且不创建合成 `Entry` 节点；默认只投影入口、Command、Domain Event、Integration Event。
- A20：Command Handler、Domain/Integration Event Handler 和 Entity Method 在默认 Flow 中隐藏；任意长度隐藏路径、fan-out、merge 和循环均由同一投影算法处理，投影边稳定去重并在算法/诊断证据中保留对应 raw path reference。
- A21：root 在最终可见投影后判定；孤立 Command/Event 和缺失上游证据的节点不升级为 root，中间 sender/Event 不制造 Issue #55 的重复 Flow，多个真实入口仍分别产生 Flow。
- A22：循环使用稳定 visited node/edge 有限结束并保留关系；默认 Flow 不强制 DAG、不静默断链，也不引入自动 process stitching。
- A23：删除独立 `cap4k-plugin-code-analysis-flow-export` 模块、plugin id、`cap4kFlow*` tasks、Central publication marker 和当前主线引用；历史 archive/spec/plan 只保留为历史证据，不创建兼容 alias 或 migration bridge。
- A24：Pipeline `flow` 继续统一拥有 JSON、Mermaid 和 index artifacts，并成为唯一产品入口；focused/functional tests、完整 Gradle 检查和 capability contract validators 共同证明当前实现与公开投影一致。
- A25：Snapshot 三分区完整 refactor、per-partition completeness、`cap4k.agent.analysis.v2` 和完整双项目 Drawing Board round-trip gate 仍明确标记为未实施；Public Docs、AgentFacts 和 Skill 只同步本 Change 实际落地的能力。

# Constraints and invariants

- 当前唯一开发基线是最新 `origin/master`；本 Change 的 target branch 使用已验证的本地 `master` 基线，但不在 `master` 上修改。若最终 Build 包含产品代码，进入 Build 前必须把当前 `docs/analyzer-capability-audit-closure` 分支改为 `feature/analyzer-capability-audit-closure`，PR 也按非 docs-only 规则验证。
- 旧审计 worktree `framework-capability-audit` 只读；不得清理、修补、恢复或迁移其工作流产物。
- 本 Change 的首要目的仍是审计收口；canonical target contract 记录产品边界，不能以目标合同反向宣称实现状态。产品实现范围必须在 Shape 最终确认前写明，并且只有在 Build/Verify 有实际代码和证据时才计为完成。
- Analyzer observation 不自动反馈 Generator；只有明确的人类或 Agent 操作才能把 Drawing Board design blocks 作为普通 Design JSON 输入。
- `graph`、`designProjection`、`aggregateStructure` 是不同事实，不因共享 compiler invocation、raw 文件目录或 `ir-analysis` source id 而合并为一种 IR graph 事实。
- Public Docs、Skill 和 AgentFacts 是代码能力的投影，只能在相应生产事实和传播闭包落地后更新；不能从手写文档、Skill 或 JSON 反向构造事实。
- 任何后续合同或实现变化都必须遵循 capability dependency graph 和直接/传递传播闭包，不得只更新固定的下游文件。
- Issue 负责 backlog、优先级、依赖和生命周期；spec/plan 负责目标合同和实施细节；审计 archive 不是实现状态数据库。本 Change 的后置 Snapshot/Drawing Board 范围已写入 [Issue #25](https://github.com/LDmoxeii/cap4k/issues/25)，process projection 已写入 [Issue #55](https://github.com/LDmoxeii/cap4k/issues/55)。
- `drawing_board_aggregate_elements.json` 的文件名、目录、物理 output root 和当前 canonical 挂载均不是不可变产品语义。
- 目标 Flow 只陈述静态代码证据，不陈述运行时执行顺序、事务顺序、消息必达、业务结果或领域正确性。

# Decisions

## 旧审计文件到新合同的映射

| 旧审计文件/章节 | 新 canonical contract | 处理方式 |
|---|---|---|
| `analyzer-snapshot-contract/spec.md` 的 `designProjection`、Drawing Board consumer、round-trip 和完整性章节 | `analyzer-drawing-board-contract` | 从混合 Snapshot 中抽出，形成独立的规范化战术设计恢复合同。 |
| `analyzer-snapshot-contract/spec.md` 的三分区、Graph、canonical ownership、transport、Agent wire 和 capability governance 章节 | `analyzer-evidence-model` | 保留逻辑三分区，明确共享物理采集不等于共享 canonical schema。 |
| `analyzer-aggregate-structure/spec.md` 全部产品边界、生产链、carrier 粒度、类型闭集和 drift 章节 | `analyzer-evidence-model` 的 Aggregate Structure 分区 | 合并进 Evidence model，避免创建语义重复的第四份 canonical spec；Aggregate Structure 仍是独立事实分区和后续实施切片。 |
| `analyzer-causal-flow/spec.md` | `pipeline-causal-flow-contract` | 保留入口中心、路径收缩、循环和 Issue #55 边界；记录当前双入口事实，以及退役独立 flow-export、由 Pipeline `flow` 成为唯一产品入口的已确认目标。 |
| 旧 `runtime-capability-reset/spec.md` | 不进入本 Change | Runtime 已在 PR #159-#184 完成并由 PR #185 治理；仅作为历史上下文，不创建 Analyzer canonical contract。 |
| 旧 archive 的 `verification.md` | 不成为 canonical contract | 只作为旧 brief/spec 覆盖性核验，不能证明 Analyzer 产品实现已完成。 |
## 当前事实 / 已确认目标 / 未实施缺口 / 未决事项矩阵

| 主题 | 当前代码事实 | 已确认目标 | 尚未实施缺口 | 未决事项 |
|---|---|---|---|---|
| Drawing Board | `DesignElementSnapshot`、`DrawingBoardArtifactPlanner` 和 `drawing-board` output 已存在；`design-elements.json` 是可选输入，`aggregate-elements.json` 仍由同一 compiler output 产生。 | Drawing Board 只恢复规范化战术设计；显式导入才可作为 Design JSON；Aggregate Structure、Entity Method、Repository 结构证据不进入 design projection。 | 三分区 Snapshot wire/schema、designProjection ownership、完整性与真实双项目 round-trip 尚未按目标合同收口。 | 无新的产品语义问题；实现优先级由 Analyzer evidence model 切片承载。 |
| Analyzer evidence model | `IrAnalysisSnapshot` 仍把 `nodes`、`edges`、`designElements`、`aggregateElements` 放在同一物理 snapshot；`analysis.json` 仍是 `cap4k.agent.analysis.v1` 平铺 section。 | 逻辑模型固定为 `AnalyzerSnapshot.graph`、`designProjection`、`aggregateStructure`；可以共享一次 compiler/raw transport，但 schema、owner、完整性和消费者隔离。 | 当前 Build 只落实 Aggregate Structure 的独立 canonical owner；完整 Snapshot 分区、partitioned wire、Agent codec 和 completeness diagnostics 后置。 | Issue #25 继续跟踪后置实现的优先级和状态。 |
| Aggregate Structure | Build candidate 已将 `aggregateStructure` 建立为唯一 canonical owner；compiler/IR source 仅接受 `schema/entity/repository/factory/strong-id/projection`，继续生成、校验、稳定合并并从独立 owner 导出 `drawing_board_aggregate_elements.json`。 | 保留独立结构观察，最小事实为实际 carrier class；不属于 Design JSON、Graph 或 SQL schema。 | 本 Change 范围内无已知实现缺口，仍需由 Verify 独立确认 focused tests、完整检查和治理传播证据。 | 无产品方向未决；若 Verify 暴露合同外独立缺口，由 Issue 管理而不扩张当前范围。 |
| Causal Flow | Build candidate 已实现开放入口证据、投影后 root、Handler/Entity Method 任意长度路径收缩、fan-out/merge/cycle/稳定去重；独立 `cap4k-plugin-code-analysis-flow-export` 已从当前模块、任务和发布面删除。 | 默认 Flow 是实际入口中心的最小业务因果投影；Command/Event 可见，Handler/Entity Method 隐藏并收缩；不强制 DAG，不自动 stitching；Pipeline `flow` 是唯一产品入口。 | 本 Change 范围内无已知实现缺口，仍需由 Verify 独立确认 Issue #55 regression、完整检查和唯一入口。 | 是否建立可选 process projection；该能力不属于默认 `flow` contract。 |
| 治理投影 | Build candidate 已由 production descriptors 表达 raw graph 输入、Flow 可见/隐藏角色、投影责任、Design Projection 与 Aggregate Structure 输出语义，并同步当前 Public Docs、Skill 和维护地图。 | 目标合同变化必须通过依赖图传播到 AgentFacts、Public Docs、Skill；投影不得提前宣称未实现能力。 | 后置 Snapshot 三分区、analysis.v2、per-partition completeness 与完整 round-trip 未投影；当前候选仍需 capability validators 验证。 | 后续 Snapshot/Drawing Board implementation Change 的具体排期。 |

- 使用全新的 `analyzer-capability-audit-closure`，不恢复旧 `framework-capability-audit` Change。
- 从最新 `origin/master@226e303697d7ee099a1cfab8e2fcc06076ea36df` 创建干净 worktree 和 `docs/analyzer-capability-audit-closure` 分支。
- 采用三份 canonical target contract，而不是复制旧三份 spec：Drawing Board、Analyzer evidence model、Pipeline causal Flow。
- 旧 Snapshot 与 Aggregate Structure spec 合并到 Evidence model；Drawing Board 往返语义从 Snapshot 中抽出成为独立 contract；旧 Causal Flow spec 映射为 Pipeline causal Flow contract。
- `AnalyzerSnapshot.graph`、`AnalyzerSnapshot.designProjection`、`AnalyzerSnapshot.aggregateStructure` 是推荐的逻辑模型；是否共用一次 compiler 和物理 raw bundle可以保留，但不能共用混淆后的 canonical schema 或 ownership。
- Drawing Board 只恢复规范化战术设计。`drawing_board_aggregate_elements.json` 属于独立 Aggregate Structure observation，不属于 Design JSON authoring schema，也不参与 Design JSON round-trip 等价比较。
- Aggregate Structure 的最小事实是实际生成 carrier class 记录，当前目标闭集为 `schema/entity/repository/factory/strong-id/projection`；四个 `specification/unique-*` 名称是退役能力漂移，不恢复兼容桥。
- 默认 Causal Flow 是入口中心的最小有向因果投影，不强制 DAG。入口是实际 Graph node 的角色，不是新增抽象 Entry 节点；HTTP/RPC/Inbound Event/Job 只是开放入口示例。
- 默认 Flow 可见 Command/Event 与真实入口，隐藏 Command Handler、Event Handler、Entity Method，并收缩任意长度隐藏路径；Query、Capability、Validator、read-side 关系仍可留在 Graph 但不进入默认 Flow。
- [Issue #25](https://github.com/LDmoxeii/cap4k/issues/25) 继续承担后置 evidence model 的实现优先级和状态管理；[Issue #55](https://github.com/LDmoxeii/cap4k/issues/55) 继续承担 duplicate/split Flow 与可选 process projection 的优先级和状态管理。
- Build candidate 已删除 `cap4k-plugin-code-analysis-flow-export` 模块、plugin id、`cap4kFlow*` tasks 和 Central publication marker；Pipeline `flow` 保持 JSON、Mermaid、index artifacts 的统一 owner，并成为唯一产品入口。最终状态以 Verify 的独立证据为准。
## 已确认 Build 范围

用户已确认本 Change 在合同归档之外纳入两个完整实施切片：

1. `Aggregate Structure` 闭环：
   - compiler 仅接受 `schema/entity/repository/factory/strong-id/projection`，拒绝四个已退役 type；
   - `aggregateStructure` 成为独立 canonical evidence owner，不再同时由 Graph 与 Drawing Board model 拥有；
   - 保留现有 `drawing_board_aggregate_elements.json` 文件名、输出位置和公开 output identity；它由独立 canonical evidence 驱动，不得混入 Design JSON，也不新建重复 output identity；
   - 更新 focused tests、CapabilityContractFacts 及由代码事实触发的当前 AgentFacts/Public Docs/Skill 投影。
2. `Pipeline Causal Flow + 独立 flow-export 退役` 闭环：
   - Pipeline Flow 实现实际入口 root、Handler/Entity Method 隐藏、任意长度路径收缩、fan-out/merge、循环有限遍历、稳定去重和 Issue #55 regression；
   - 删除 `cap4k-plugin-code-analysis-flow-export` 模块、plugin id、`cap4kFlow*` tasks 和 Central publication marker；
   - Pipeline `flow` 继续统一拥有 JSON、Mermaid 和 index artifacts，并成为唯一产品入口；
   - 更新 current analysis maps、metadata contract、focused/functional tests 和 capability propagation 验证。

本 Change 不实施 Snapshot 三分区 canonical refactor、per-partition completeness、`cap4k.agent.analysis.v2` 或完整双项目 Drawing Board round-trip gate。这些内容跨 `pipeline-api/source/core/agent/gradle` 多层，单独纳入会迫使当前 Change 完成半套 wire 或显著扩大审查面，继续由 [Issue #25](https://github.com/LDmoxeii/cap4k/issues/25) 和后续 implementation Change 承担。

不得修改 Public Docs、AgentFacts 或 Skill 来提前宣称仍未实现的能力；纳入 Build 且经 Verify 通过的能力必须同步其真实当前投影。

# Open questions

- 当前 Shape 不阻塞本次合同收口的后续事项：是否建立可选 process projection 继续由 [Issue #55](https://github.com/LDmoxeii/cap4k/issues/55) 跟踪，不属于默认 `flow` contract；它不进入本 Change 的 Build 范围。
- 已确认：`Aggregate Structure` 保留为独立结构证据分区，不属于 Drawing Board Design JSON，也不改造成 SQL/Schema projection。
- 已确认：本 Change 实现 `Aggregate Structure` 闭环和 `Pipeline Causal Flow + 独立 flow-export 退役` 闭环；Snapshot 三分区、`analysis.v2` 和完整 Drawing Board round-trip gate 后置到 [Issue #25](https://github.com/LDmoxeii/cap4k/issues/25)/后续 Change。
- 已确认最终 Shape：目标、当前 Build 范围、后置 Issue、验收条件、非目标和可选 process projection 边界符合共享理解。

# Verification expectations

- Shape 阶段只读核对新 Change 的基线、分支、worktree、语言和未修改产品代码的边界；进入 Build 后分别记录实际代码修改与证据。
- 每个当前事实至少引用最新主线 source file、symbol、测试或公开文档；旧审计材料只能作为历史证据并标注其非实现性质。
- 分别记录四种状态：当前代码事实、已确认目标合同、尚未实施缺口、仍未决建议；不得用一列混合它们。
- 验证旧文件映射：旧 `analyzer-snapshot-contract` + `analyzer-aggregate-structure` → `analyzer-evidence-model`；旧 Snapshot 的 designProjection/round-trip 章节 → `analyzer-drawing-board-contract`；旧 `analyzer-causal-flow` → `pipeline-causal-flow-contract`。
- 验证三份目标合同均为中文、没有复制旧 Change 的 Comet 安装/运行产物；Shape 无生产代码改动，Build 仅修改已确认实施切片及其由代码事实要求的治理投影。
- 自动化证据与真实项目证据分开：静态 source/test/validator 检查只能证明代码或合同事实；真实项目 round-trip、Flow 可读性和下游消费验证必须单独记录，不能由 spec-only Archive 代替。
- 本 Change 的实现验证只包括 Aggregate type/raw merge/canonical ownership/export/drift、Flow entry/root/projection/cycle/duplicate regression、独立 exporter 退役，以及实际受影响的 capability contract、AgentFacts、Public Docs、Skill 传播闭包。Snapshot wire/partition/completeness 与 Drawing Board round-trip 保持未实施且不得报告为通过。
- Shape 审阅通过后才允许 Comet Native `next --confirmed`；在此之前不进入 Build、Verify、Archive，不创建实现分支或产品 PR。若用户确认当前 Build 范围包含产品代码，先将分支从 `docs/analyzer-capability-audit-closure` 调整为 `feature/analyzer-capability-audit-closure`。