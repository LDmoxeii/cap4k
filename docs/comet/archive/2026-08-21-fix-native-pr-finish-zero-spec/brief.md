# Outcome

修复仓库自有的 Comet Native PR finish provider，并明确 `repository-pr-finish` canonical contract：合法的 implementation-only change（`spec_changes: []`）能够使用空的 `artifact.source.specs` 完成 pull-request create/reuse，同时保持所有既有 authoring fingerprint、Git tree、Archive progression、模板、capability facts 和远端 PR 校验不变。

# Scope

- 为 `scripts/comet-finish-pr.ps1` 增加对唯一合法 inline 空序列 `spec_changes: []` 的显式解析。
- 允许 archived state 与 pre-Archive state 的 expected spec 集合均为空，并要求 authoring artifact 的 `source.specs` 也精确为空。
- 保留非空 `spec_changes` 的现有 block-list 解析，并要求 pre-Archive 与 Archive 的 capability/source 映射精确一致。
- 在 `scripts/test-pr-workflow.ps1` 增加完整的 zero-spec provider create/reuse fixture，并覆盖非法 inline 值、active/archive mapping drift 或 artifact/spec mismatch 的远端 mutation 前 fail-closed。
- 修改 `repository-pr-finish` canonical Spec，固化 zero-spec 的唯一合法表示、三方 exact-set 一致性、非空映射一致性及其余安全边界不减弱的合同。
- 运行 PR workflow、Gradle check、capability/Skill/Runtime 治理检查。

# Non-goals

- 不创建无关 capability、占位 capability 或仅为规避 provider 而存在的空洞 Spec。
- 不修改 Comet Runtime、Archive transaction 或 Native state schema。
- 不自动生成 Native PR authoring artifact；artifact 仍由当前 Native Agent 按既有 repository-owned contract 生成。
- 不放宽 pre-Archive tree、Archive changed-path、fingerprint、template、facts、remote PR identity/body 的校验。
- 不顺带修改 Gradle Wrapper 或其他产品代码。

# Acceptance examples

- A1: archived state 使用 `spec_changes: []` 且 authoring artifact 使用 `source.specs: []` 时，provider 能创建 PR，并在相同 evidence 重试时复用同一 PR，不产生重复 PR。
- A2: zero-spec 路径仍要求 pre-Archive active root 到 Archive root 的精确 Runtime-owned progression，不能夹带 canonical spec 或其他额外路径。
- A3: 非空 spec change 的既有 create/reuse、hash、canonical publication 与 exact-set 校验继续通过，且 pre-Archive/Archive capability-source mapping 必须完全一致。
- A4: 非法 inline `spec_changes` 值、state/artifact specs 数量、路径、capability/source identity 不匹配时，在任何远端 mutation 前 fail closed。
- A5: `scripts/test-pr-workflow.ps1`、`gradlew.bat check`、capability facts export/validation、Skill validation、Runtime facts 与 capability-contract tests 全部通过。
- A6: `repository-pr-finish` canonical Spec 为 `modified`；Runtime、Generator、Analyzer、AgentFacts、Public Docs、Skill 的传播结论为 `verified-no-change` 或 `not-applicable`。
- A7: Archive 将完整目标 Spec 精确发布到 `docs/comet/specs/repository-pr-finish/spec.md`，并能通过旧 provider 的非空 Spec 路径完成本修复的 PR finish；合并后的 provider 能处理后续真正的 zero-spec change。

# Constraints and invariants

- 只接受精确的 inline 空序列 `spec_changes: []` 作为零项；不能把任意 inline scalar、map、带注释或替代空列表拼写当成空集合。
- 空集合必须在 archived state、pre-Archive state 与 authoring artifact 三个边界上精确一致。
- 非空 spec change 的 capability/source 对必须在 archived state 与 pre-Archive state 间精确一致。
- provider 的 Git management artifact、accepted snapshot、fingerprint 与远端 PR 验证合同保持不变。
- 修复必须是最小、可测试的 repository-governance change。

# Decisions

- 本缺陷既是 provider 实现修复，也是对既有 `repository-pr-finish` capability contract 的明确化；使用真实 `modify` Spec 固化 zero-spec exact-set 语义。
- 该 canonical MODIFY 同时提供旧 provider 可验证的非空 Spec 路径，使修复能够通过既有 Native PR finish 合同落地；它不是绕过或占位。
- 使用专用 spec-change parser，而不是泛化 `Get-YamlSection` 的行为。
- zero-spec fixture 必须覆盖 create 与 reuse；负向 fixture 必须证明非法 archived/active inline 值和 capability mapping drift 均不会触达远端 mutation。

# Open questions

- 无。

# Verification expectations

- 聚焦执行 `scripts/test-pr-workflow.ps1`，确认 zero-spec create/reuse、非空 compatibility 与负向 fail-closed 用例。
- 执行 `gradlew.bat check --no-daemon --console=plain`。
- 执行 `scripts/export-capability-contract-facts.ps1`、`scripts/validate-capability-contract.ps1`、`skills/scripts/validate-cap4k-skills.ps1`、`scripts/validate-current-runtime-facts.ps1`、`scripts/test-capability-contract.ps1`。
- 执行 `git diff --check`，确认无额外路径或占位 Spec。
