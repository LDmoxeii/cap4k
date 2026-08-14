# Outcome

完成 GitHub Issue #25 的 Analyzer transport/model 收口：把同一次 IR analysis observation 明确建模为 `AnalyzerSnapshot.graph`、`AnalyzerSnapshot.designProjection`、`AnalyzerSnapshot.aggregateStructure` 三个逻辑分区，为每个分区提供独立的来源、完整性、计数和诊断，并把 Agent `analysis.json` 升级为能够表达该边界的分区化 wire contract。

同时保留已经存在的 Drawing Board 双临时项目往返测试作为仓内 compiler-backed 硬门禁，证明规范化 Design JSON 可以经生成 skeleton、真实 compiler Analyzer、Drawing Board design projection，再由显式 Design JSON source 重新生成等价 skeleton。长期未更新的下游参考项目不作为本 Change 的硬验收；完成后关闭 Issue #25。本 Change 允许修改产品代码、测试、代码派生事实和实际受影响的公开投影。

# Scope

- 在 pipeline API 中以强类型 `AnalyzerSnapshot` 表达 `graph`、`designProjection`、`aggregateStructure` 三分区，替代当前平铺的 `IrAnalysisSnapshot` 语义。
- 保留现有 `nodes.json`、`rels.json`、`design-elements.json`、`aggregate-elements.json` 物理 sidecar 及一次 compiler observation；本次不因逻辑分区重命名 raw 文件或拆成多次编译。
- `IrAnalysisSourceProvider` 按 input directory 收集来源身份，并分别计算三个分区的状态、覆盖、计数和诊断；一个分区的问题不得由另一个分区的非空事实掩盖。
- Graph 分区按稳定 node/edge identity 合并；同一 node identity 的语义字段冲突必须失败，`missingMetadata` 可以稳定合并，metadata owner 冲突必须失败。
- Design Projection 分区必须区分合法空结果、未配置、raw sidecar 缺失、metadata 缺失、冲突和无效；请求 Drawing Board 时不得生成外观完整的 partial board。
- Aggregate Structure 分区继续使用已落地的独立 canonical owner、`carrierQualifiedName` 去重和冲突拒绝，不改变受支持 type 闭集和现有公开 artifact identity。
- `DefaultCanonicalAssembler` 只从对应分区建立 `AnalysisGraphModel`、`DrawingBoardModel` 和 `CanonicalModel.aggregateStructure`，不得跨分区复制或补造事实。
- 将 Agent `analysis.json` 升级为分区化 schema：顶层状态由三分区确定性聚合；每个分区投影 status、counts、source attribution、evidence freshness、planned/available output paths、diagnostic IDs 和 reason。
- `CapabilityContractFacts`、AgentFacts、Public Docs 和 Skill 仅在对应产品代码完成后，从生产声明传播当前已实现的 Analyzer 子契约、消费者依赖和 wire schema。
- 保留现有 `DesignRoundTripFunctionalTest` 作为 compiler-backed 自动化双项目回归；不要求在本 Change 中升级或跑通长期未更新的 `cap4k-reference-content-studio`。
- 更新 Issue #25 的生命周期证据；实现、Verify、Archive 和 PR 合并完成后再关闭 Issue。

# Non-goals

- 不修改 Issue #55 的默认 entry-centered Flow 或引入 process projection。
- 不恢复已退役的 `cap4k-plugin-code-analysis-flow-export`。
- 不重命名或迁移 `drawing_board_aggregate_elements.json`，不把它作为 Design JSON、SQL/Schema projection 或 Generator 输入。
- 不把 Analyzer output 自动反馈到 Generator；往返必须由用户或测试显式注册 Drawing Board design files。
- 不改变现有 Drawing Board generator/output identity、Flow output identity或 pipeline 固定阶段顺序。
- 不从 Kotlin 方法体、Repository implementation、Entity Method、JPA mapping 或运行时 trace 推断缺失的 Design JSON 语义。
- 不为迁移而保留两个并列的 Analyzer 权威模型或让 Public Docs、Skill、手写 JSON 反向成为 AgentFacts 的事实源。
- 不升级、修改或迁移下游参考项目；其当前漂移不用于放宽 cap4k 合同，也不阻塞本 Change。

# Acceptance examples

- A1：Change 从 `origin/master@720ad9a44e1610865c71109fbaa90e827aaa0753` 创建隔离 worktree `analyzer-snapshot-boundaries`，分支为 `feature/analyzer-snapshot-boundaries`；Issue #25 处于 Open，PR #187 的归档合同作为当前基线。
- A2：pipeline API 存在一个权威 `AnalyzerSnapshot`，明确包含 `graph`、`designProjection`、`aggregateStructure` 三个强类型分区；生产代码不再把平铺 `IrAnalysisSnapshot` 当作并列权威模型。
- A3：现有四个 raw sidecar 和一次 compiler observation 保持兼容；逻辑分区不要求项目用户改变 `sources.ir-analysis.inputDirs` 或运行额外编译任务。
- A4：每个配置 input directory 都形成稳定来源身份；分区记录自己的 sources、status、counts 和 diagnostic IDs，顺序稳定且不会泄漏无法复现的临时绝对路径。
- A5：Graph 分区独立承载 nodes/relationships；重复 identity 的等价记录稳定去重，node 语义冲突和 metadata owner 冲突明确失败，Graph 缺失不被 Design Projection 或 Aggregate Structure 掩盖。
- A6：Design Projection 分区能够区分合法空设计、未配置、缺少 `design-elements.json`、缺少必要 metadata、跨模块冲突和无效 JSON；请求 Drawing Board 时，partial 或 invalid 分区不能输出伪完整 design board。
- A7：Aggregate Structure 分区独立承载 aggregate elements，继续以 `carrierQualifiedName` 稳定去重并拒绝冲突；`specification`、`unique-query`、`unique-query-handler`、`unique-validator` 仍被拒绝。
- A8：Canonical assembler 分别消费三个分区；Flow 只能消费 Graph，Drawing Board design files 只能消费 Design Projection，structure artifact 只能消费 Aggregate Structure。
- A9：`analysis.json` 使用新的分区化 schema；顶层状态按 `INVALID > PARTIAL/UNAVAILABLE > OK` 的既有语义确定性汇总，每个分区的错误、状态、来源、计数、freshness 和 outputs 可独立观察。
- A10：Analyzer collect/parse/merge 异常不会再被 `.getOrNull()` 静默吞掉；Agent diagnostics 能稳定关联到受影响分区和来源，未受影响分区仍保持自己的真实状态。
- A11：代码派生 capability facts 明确三个 Analyzer 分区及直接消费者：Graph → Pipeline Flow，Design Projection → Drawing Board，Aggregate Structure → structure evidence output；传播闭包验证通过。
- A12：现有 compiler-backed `DesignRoundTripFunctionalTest` 继续通过，覆盖七类 tactical tag、artifact selection、event semantics、type identity、nullability、default expression、fields/resultFields 声明顺序和生成 skeleton 等价。
- A13：仓内 `DesignRoundTripFunctionalTest` 使用真实 compiler Analyzer 和两个隔离项目完成 producer/consumer 往返，不用 mock 或仅序列化比较替代。
- A14：verification 明确记录下游参考项目验证未执行，并记录已观察到的项目旧输入漂移；不得把该项目描述为已经通过，也不得让其漂移阻塞 #25。
- A15：Public Docs、Skill 与 AgentFacts 只声明本 Change 实际完成且由代码派生的当前能力；未实现、不可复现或仅目标合同中的行为不得提前发布。
- A16：focused tests、完整 Gradle `check`、capability contract validators、Skill validator、current runtime facts validator 和 `git diff --check` 全部通过。
- A17：Comet Verify/Archive、Issue #25 生命周期证据和独立 `feature/* -> master` PR 均完成后，Issue #25 才可关闭。

# Constraints and invariants

- canonical contract 以 `docs/comet/specs/analyzer-evidence-model/spec.md` 和 `docs/comet/specs/analyzer-drawing-board-contract/spec.md` 为权威基线；本 Change 只能收紧或实现它们，不重新进行 Analyzer 产品 Shape。
- 物理 raw transport 可以共享，逻辑 schema、ownership、completeness 和 consumer 必须分离。
- 完整性、freshness、来源和诊断是横切状态，不是第四类业务事实。
- source observation identity 与 artifact plan freshness 必须区分：raw source 的可追溯性不能伪装成输出已新鲜，输出时间戳也不能证明 compiler evidence 完整。
- `AgentSnapshotStatus` 继续使用 `ok`、`partial`、`invalid`、`unavailable`；不得新增同义状态词汇。
- Agent wire、Public Docs 和 Skill 都是生产代码事实的投影，不承担迁移历史说明，也不能反向定义 Analyzer。
- 下游 sibling repository 不属于本 Change 的 CI 或 Verify 门禁；仓内 compiler-backed fixture 是可重复执行的产品验收证据。
- 若 Build 证明完整 #25 无法作为一个 PR 独立审查，才依据真实代码边界拆为 Issue 子项；不按内部类或目录预先制造虚假切片。

# Decisions

- Issue #25 于 2026-08-13 重新打开；PR #187 只完成审计合同、Aggregate Structure ownership/type drift 和默认 Flow，不构成 #25 完成证据。
- 本 Change 名称为 `analyzer-snapshot-boundaries`，目标是完成 #25，而不是只写新的调查文档。
- 逻辑模型采用 `AnalyzerSnapshot.graph`、`AnalyzerSnapshot.designProjection`、`AnalyzerSnapshot.aggregateStructure` 三分区。
- 保留现有四个 raw sidecar、`sources.ir-analysis.inputDirs` 配置和一次 compiler observation；本次只重构模型、完整性和 wire，不迁移物理文件合同。
- canonical 三个消费者 owner 已经分开，本 Change 在 source snapshot 和 Agent wire 上补齐同样的边界，不撤销 PR #187 已完成的 Aggregate Structure 分离。
- 每个分区复用现有 `AgentSnapshotStatus` 词汇；顶层状态由分区状态确定性聚合。
- `analysis.json` 直接从 `cap4k.agent.analysis.v1` 升级并替换为单一 `cap4k.agent.analysis.v2`；不双写 v1/v2，不保留公开兼容桥。当前仓内没有生产 decoder 依赖 v1，且项目处于 breaking redesign，单一权威 wire 可以避免两套 schema、validator 和后续退役债务。
- Graph duplicate node 的 `name`、`fullName`、`type` 必须一致；不再接受静默 first-wins。
- Design Projection 合法空结果必须由“没有候选 design metadata”证明；存在候选但缺 sidecar/metadata 时为 invalid，不等同于空设计。
- Drawing Board round-trip 的产品链路已有 compiler-backed 自动化基础，本 Change 不重写链路，而是把分区 transport/wire 接入该门禁。
- `cap4k-reference-content-studio` 当前含 Bootstrap、旧 aggregate DSL、旧 Value Object persistence 字段和旧 DB annotation 等输入漂移；本 Change 不承担其现代化，也不把未跑通的尝试作为 #25 失败。

# Open questions

- 无。

# Verification expectations

- API/model：验证三分区类型、状态聚合、稳定来源身份和不存在第二权威 snapshot。
- IR source：覆盖每目录 raw 文件存在/缺失/空/无效、metadata completeness、node/aggregate/design 冲突、稳定去重、来源与诊断关联。
- Canonical：验证三个分区只进入自己的 owner，任一分区失败不能由其他分区遮蔽。
- Agent：验证 `analysis.json` schema、manifest reference/counts/hash、per-partition status/freshness/outputs/diagnostics、collect failure 不被吞掉。
- Drawing Board：运行现有 `DesignRoundTripFunctionalTest`，验证 normalized projection 和 skeleton 等价。
- 下游证据：记录 `cap4k-reference-content-studio` 未纳入硬门禁及已观察到的旧输入漂移，不宣称该项目已通过当前版本验证。
- 治理：运行 capability facts export/validation、Skill validation、current runtime facts validation、PR workflow tests 和 PR body validation。
- 全量：运行 `./gradlew check` 与 `git diff --check origin/master...HEAD`。
