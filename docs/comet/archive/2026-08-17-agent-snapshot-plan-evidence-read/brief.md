# Outcome

修复 `cap4kAgentSnapshot` 对合法 `build/cap4k/plan.json` 的读取缺陷：当 plan 包含 managed-field policy provenance/definition owner 等抽象模型时，Snapshot 仍能读取它实际需要的 plan outcome、items、diagnostics 与 evidence，正确生成 ownership/output facts，而不是把合法 plan 降级为 `plan-evidence-invalid`、空 ownership 和 `partial`。

本 change 实现既有 AgentFacts/plan ownership 合同，不改变 plan JSON wire shape、Pipeline API 或 managed-field canonical model。

# Scope

- 在 `Cap4kAgentSnapshotTask` 的 plan evidence 读取边界引入私有、最小的 Snapshot projection，只物化 Snapshot 实际消费的字段。
- 保留现有 plan JSON 结构和 `PlanReport` 写出行为。
- 为包含 managed-field policies 的合法 plan 增加回归测试，证明 ownership items、freshness/status 与 diagnostics 正确。
- 保留 corrupt/invalid plan 的既有诊断与 partial 语义。
- 核验 AgentFacts、Public Docs、Skill、Runtime、Generator、Analyzer 的传播闭包；预计仅 AgentFacts 实现与测试 modified，其余 verified-no-change。

# Non-goals

- 不给 `ManagedPolicySelectionProvenance` 或 `ManagedPolicyDefinitionOwner` 增加 Jackson discriminator。
- 不改变 `build/cap4k/plan.json` 字段、序列化格式或兼容策略。
- 不重构整个 Agent Snapshot 或 Pipeline JSON mapper。
- 不改变 managed-field policy 的 canonical/API 类型层次。
- 不修改 Analyzer、Generator planning 或 downstream payment 业务实现。

# Acceptance examples

- A1：一个包含至少一个 ArtifactPlanItem 和非空 managed-field policies 的合法 plan 能被 `cap4kAgentSnapshot` 读取，ownership items 保留 generator、template、module role、output path/kind/root 与 conflict policy。
- A2：上述合法 plan 不再产生 `plan-evidence-invalid-*` diagnostic；plan evidence freshness/status 正常，Snapshot 不因该计划读取路径而变成 partial。
- A3：Snapshot 不需要实例化 `ManagedPolicySelectionProvenance` 或 `ManagedPolicyDefinitionOwner` 的具体子类；修复同时覆盖二者，不留下“修完 selection 后在 definitionOwner 再失败”的串联缺陷。
- A4：`cap4kPlan` 写出的 plan JSON 字节合同和字段结构保持不变，不新增 type discriminator，不要求重写旧 plan。
- A5：损坏、缺字段或结构非法的 plan 仍产生可操作的 invalid evidence diagnostic，并保持既有降级语义。
- A6：任务级测试与 Gradle plugin functional test 覆盖 managed-policy plan → Agent Snapshot 的真实链路；模块测试与 capability contract validators 通过。

# Constraints and invariants

- Snapshot projection 是 Gradle plugin 内部读取 DTO，不提升到 pipeline-api，不成为新的公共模型。
- plan writer 继续以 `PlanReport` 为真源；reader 只投影自己需要的字段。
- ownership facts 必须来自实际 plan items，不得从生成目录或 Git 猜测。
- 合法 plan 的新增字段仍应被边界读取容忍；Snapshot 不应因与自身无关的 review-only plan 字段不可物化而失效。

# Decisions

- 采用 Snapshot-private projection DTO，而不是给公共 sealed interfaces 增加 Jackson 多态元数据。
- 这是既有 public contract 的实现修复，不新增 canonical spec delta。
- 重点回归 fixture 必须包含 managed policies 和至少一个 output item，直接复现 payment reference 暴露的问题。

# Open questions

- 无。该缺陷已有下游合法 plan、warning、空 ownership 与现行 canonical ownership 合同作为确定证据。

# Verification expectations

- 运行 `:cap4k-plugin-pipeline-gradle:test`，至少包含 focused `Cap4kAgentSnapshotTaskTest` 与 `Cap4kAgentSnapshotFunctionalTest`。
- 运行 `check` 或 Runtime 要求的等价完整检查。
- 运行 `scripts/export-capability-contract-facts.ps1` 与 `scripts/validate-capability-contract.ps1`，记录传播闭包状态。
- 使用 downstream `cap4k-reference-payment` Composite 重新运行 `cap4kPlan cap4kAgentSnapshot`，确认 ownership 非空、无 `plan-evidence-invalid`、manifest 不再因该缺陷 partial。
