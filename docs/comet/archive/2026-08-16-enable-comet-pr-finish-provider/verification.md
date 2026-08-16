---
generated_from_state_version: 18
---

# Verification

## Current result

- Result: **Passed**
- Assurance: **skill-coordinated**
- Goal cycle: 2
- Iteration: 2
- Verifier attempt: 1
- Completed: 2026-08-16T12:05:56.066Z
- Summary: Independent read-only verification passed. A1-A62 were reviewed exactly once against AGENTS, active state, brief, complete target Spec, implementation diff and Runtime checks. The prior A7/A9/A35/A36/A45/A60 gaps are substantively repaired: fingerprint/source identity binds preArchiveHeadSha and preArchiveTreeSha; exact tree/blob/path proof replaces broad prefix allowlisting; recomputed-fingerprint valid-tree replacement and allowed-path internal drift fail before remote mutation; and isolated-index snapshot tests prove the real index tree and bytes remain unchanged. No additional checks are required.

## Acceptance

| ID | Result | Source | Criterion | Reason |
| --- | --- | --- | --- | --- |
| A1 | passed | brief.md | A1: `.comet/config.yaml` 使用项目相对 PowerShell adapter 后，Comet 可发现该 provider，且原有 Native snapshot 配置保持不变。 | Config registers the project-relative repository-command provider and preserves the existing snapshot contract; workflow tests guard both. |
| A2 | passed | brief.md | A2: adapter 只接受 `comet.native.pull-request-finish-input.v1`，拒绝错误 schema、非 `master` base、非法 head、无效 Git OID 和越界输入；stdout 成功时只包含一个 result JSON。 | Provider enforces bounded UTF-8 input, exact v1 schema/properties, origin/master, short-lived head, valid OIDs/current HEAD, and stdout/stderr purity. |
| A3 | passed | brief.md | A3: 用户选择 `finish=pull-request` 后，Native Agent 自动生成符合 tracked template 的 repository-owned authoring artifact，不展示 title/body 二次确认；artifact 同时绑定 change、base、head、已验收的 pre-Archive commit identity、当前已验收 working tree 的完整 Git tree snapshot identity（`source.preArchiveTreeSha`）、来源 state/verification identity 与内容指纹。 | AGENTS authorizes no-second-confirmation AI authoring after accepted Verify; artifact validation binds change/base/head, state/verification, pre-Archive commit/tree, and fingerprint. |
| A4 | passed | brief.md | A4: authoring artifact 中的语义内容覆盖 Summary、Issue/Acceptance、六个 capability surfaces、shared contracts、propagation closure、composition/sibling responsibility、audit focus、实际 verification、Agent Review 和 release note；本地 validator 使用当前 diff 与 capability facts 验证承重字段后才允许远端 mutation。 | Tracked-template validator requires concrete reviewer context for issues, acceptance, six surfaces, closure, composition, audit, checks, agent review, and release note using current diff/facts. |
| A5 | passed | brief.md | A5: 没有现有 PR 时，provider 通过 `scripts/create-pr.ps1` 创建唯一 PR，并仅在远端 body 重新通过仓库 validator 后返回 `created` 与 `remoteVerified: true`。 | Repository entry creates exactly one PR only when none exists and returns created/remoteVerified only after remote identity/body revalidation. |
| A6 | passed | brief.md | A6: 已有唯一 open PR 时，provider 不重复创建；它只在远端 title/body 与 authoring artifact 一致且 identity 合规时复用并重新核验该 PR，返回 `reused`。任何漂移都 fail closed。 | Unique open PR reuse verifies recovery identity, remote head/title/body and fails closed on drift; fixture covers duplicate and drift cases. |
| A7 | passed | brief.md | A7: authoring artifact 缺失、过期、指纹不符、含占位符、无法唯一定位，或 `source.preArchiveTreeSha` 缺失、无效、不是 tree、与已验收 working tree 不一致，或 snapshot 后 final Archive head 出现非 Runtime Archive progression 路径变化，或 body validation 未通过时，provider 在创建或修改远端 PR 前失败，且不声称 remote verification 成功。 | Artifact/tree/source/exact progression/fingerprint checks all occur before repository PR entry; valid-tree replacement and internal drift are rejected. |
| A8 | passed | brief.md | A8: provider 的 number、URL、base、head 与精确 Archive head SHA 进入 result；Comet 随后仍独立执行通用远端 identity 核验。 | Provider result preserves PR number, URL, base, head branch and exact Archive head SHA while leaving generic remote verification to Comet. |
| A9 | passed | brief.md | A9: PR workflow tests 覆盖 authoring contract、pre-Archive commit/tree 双重绑定、snapshot 缺失/非法/篡改与 snapshot 后非 Archive 路径变化、JSON contract、create/reuse、失败恢复、stdout/stderr 边界和 root-script CI classification；既有手工 `create-pr.ps1` dry-run 入口继续通过。 | Workflow fixture covers config, JSON, create/reuse, recovery, stdout/stderr, root-script classification, snapshots, valid-tree tamper, internal drift, and manual DryRun. |
| A10 | passed | brief.md | A10: `scripts/validate-capability-contract.ps1`、`scripts/test-capability-contract.ps1`、Skill/Runtime/PR workflow guards 与必要 Gradle 检查均通过，或明确记录真实的无关环境阻塞。 | Runtime parser, PR workflow, capability, Skill, Runtime facts, full Gradle and diff checks all passed. |
| A11 | passed | specs/repository-pr-finish/spec.md | cap4k owns the semantic and deterministic policy for pull requests created by Comet Native Archive. Comet owns the generic finish transaction; the repository owns the reviewer context, template, capability-closure validation, create/reuse policy, and remote body verification. | Implementation preserves Comet generic finish ownership and repository semantic/validation/create-reuse ownership. |
| A12 | passed | specs/repository-pr-finish/spec.md | A user selection of `finish=pull-request` authorizes the current Native Agent to author the PR title and body from accepted change evidence. | AGENTS defines finish=pull-request as authorization for the current Agent to author title/body. |
| A13 | passed | specs/repository-pr-finish/spec.md | The Agent MUST NOT ask for a second title/body confirmation. | AGENTS explicitly forbids a second title/body confirmation. |
| A14 | passed | specs/repository-pr-finish/spec.md | Automatic authoring MUST occur only after Native Verify has accepted the current candidate and Archive permits pull-request finish. | Authoring is constrained to accepted Verify plus Archive permission; provider also requires completed passing archived state. |
| A15 | passed | specs/repository-pr-finish/spec.md | The authored content is untrusted until the repository validator accepts its structure, current diff, capability facts, and identity bindings. | Artifact remains untrusted until structure, source, tree, diff, facts and fingerprint validation complete before remote mutation. |
| A16 | passed | specs/repository-pr-finish/spec.md | The Agent MUST derive PR reviewer context from the current tracked PR template and the following available evidence: | Authoring policy derives reviewer context from accepted state/artifacts, diff, capability facts and tracked template. |
| A17 | passed | specs/repository-pr-finish/spec.md | active change name, branch, target branch, and accepted Native state; | Artifact and request bind active change, target/base, head branch and current HEAD. |
| A18 | passed | specs/repository-pr-finish/spec.md | brief, complete target Specs, verification result, and acceptance outcomes; | Provider validates brief/spec hashes and complete verification state/report identity. |
| A19 | passed | specs/repository-pr-finish/spec.md | owning Issue hierarchy when one exists; | Validator enforces Issue hierarchy and Acceptance IDs, allowing only reasoned N/A where appropriate. |
| A20 | passed | specs/repository-pr-finish/spec.md | current `origin/master...finalHead` diff and changed-path classification as PR evidence, without assuming the accepted pre-Archive commit alone contains all implementation changes; | Reviewer changed-file evidence is origin/master...finalHead, independent of the pre-Archive commit. |
| A21 | passed | specs/repository-pr-finish/spec.md | generated capability facts and propagation closure; | Provider re-exports and binds capability facts; validator checks changed seeds and propagation closure. |
| A22 | passed | specs/repository-pr-finish/spec.md | actual checks run, skipped, failed, or blocked. | Validator requires actual verification evidence or a concrete exclusive not-run reason; Runtime records executed checks. |
| A23 | passed | specs/repository-pr-finish/spec.md | The repository-owned authoring artifact MUST: | Versioned repository-owned artifact contract is implemented and exercised by the integration fixture. |
| A24 | passed | specs/repository-pr-finish/spec.md | use a versioned schema; | Only cap4k.native-pr-authoring.v1 is accepted. |
| A25 | passed | specs/repository-pr-finish/spec.md | contain the exact title and body to be published; | Artifact contains exact title/body and remote create/reuse revalidates them. |
| A26 | passed | specs/repository-pr-finish/spec.md | identify the change, base, and head; | Artifact change/base/head identity is mandatory and matched to finish input. |
| A27 | passed | specs/repository-pr-finish/spec.md | record both the accepted pre-Archive commit identity and `source.preArchiveTreeSha`, the complete Git tree snapshot identity of the accepted working tree from which it was authored; the Native Agent MUST create that tree through an isolated temporary Git index equivalent to reading `HEAD`, staging all working-tree changes, and writing the tree, without changing the real index, committing the snapshot, or adding it to product source; | Artifact records preArchiveHeadSha/treeSha; isolated GIT_INDEX_FILE read-tree/add -A/write-tree is proven not to alter the real index tree or bytes. |
| A28 | passed | specs/repository-pr-finish/spec.md | include a deterministic content fingerprint; | Fingerprint uses ordered compact JSON, normalized text, ordinal spec ordering, UTF-8 without BOM and SHA-256. |
| A29 | passed | specs/repository-pr-finish/spec.md | remain local and reusable for an Archive retry without being committed as product source. | Artifact is stored under Git management path and remains local/reusable rather than product source. |
| A30 | passed | specs/repository-pr-finish/spec.md | AI owns semantic synthesis such as Summary, Audit Focus, boundary explanations, risk, composition, and sibling responsibility. Deterministic scripts MUST NOT fabricate these narratives from fixed sentence templates. | AI owns semantic narratives; deterministic scripts validate rather than fabricate them. |
| A31 | passed | specs/repository-pr-finish/spec.md | The configured provider MUST: | Configured thin repository provider implements the accepted provider contract. |
| A32 | passed | specs/repository-pr-finish/spec.md | accept only `comet.native.pull-request-finish-input.v1` on stdin; | Provider accepts only comet.native.pull-request-finish-input.v1. |
| A33 | passed | specs/repository-pr-finish/spec.md | emit only one `comet.native.pull-request-finish-result.v1` JSON object on stdout when successful; | Success emits exactly one comet.native.pull-request-finish-result.v1 JSON object. |
| A34 | passed | specs/repository-pr-finish/spec.md | write diagnostics only to stderr; | Failures diagnose only on stderr; tests assert success/failure stream boundaries. |
| A35 | passed | specs/repository-pr-finish/spec.md | validate schema, base, head, the accepted pre-Archive commit identity, `source.preArchiveTreeSha` as an existing Git tree object, path containment, and bounded input before remote mutation; | Schema/base/head/OID/containment/pre-Archive commit/tree/source/progression validation completes before remote entry. |
| A36 | passed | specs/repository-pr-finish/spec.md | require a current repository-owned authoring artifact and fail closed for missing, stale, ambiguous, placeholder-bearing, or fingerprint-mismatched content; missing, invalid, non-tree, or tampered snapshot identity; or any difference between the snapshot tree and the final Archive head tree outside Runtime-owned Archive progression such as active-change-to-archive movement, canonical Spec publication, and `.comet/current-change.json` updates; | Exact active/archive file sets, immutable blobs, state/verification, selection, canonical spec and changed-path proofs replace the former broad prefix allowlist. |
| A37 | passed | specs/repository-pr-finish/spec.md | invoke the repository PR entry rather than duplicating its template or validation logic; | Adapter delegates PR create/reuse/template/body validation to scripts/create-pr.ps1. |
| A38 | passed | specs/repository-pr-finish/spec.md | return `remoteVerified: true` only after repository validation succeeds against the remote PR. | remoteVerified is accepted only after repository entry fetches and revalidates the remote PR body and identity. |
| A39 | passed | specs/repository-pr-finish/spec.md | Comet remains responsible for commit, push, provider invocation, independent remote PR identity verification, recovery state, and worktree cleanup. | Provider does not take over commit, push, Native recovery or worktree cleanup. |
| A40 | passed | specs/repository-pr-finish/spec.md | `scripts/create-pr.ps1` is the single repository entry for manual and Native-created pull requests. It MUST: | scripts/create-pr.ps1 remains the single manual and machine-readable repository PR entry. |
| A41 | passed | specs/repository-pr-finish/spec.md | enforce `master` as base and a same-repository short-lived head; | Repository entry restricts base=master and feature/fix/docs same-repository short-lived heads. |
| A42 | passed | specs/repository-pr-finish/spec.md | discover the tracked PR template case-insensitively; | Tracked PR template discovery is case-insensitive and overrides must be tracked templates. |
| A43 | passed | specs/repository-pr-finish/spec.md | validate the authored body before remote mutation using `origin/master...finalHead` changed-files and capability-facts evidence available to CI, while separately comparing the accepted working-tree snapshot with the final Archive head tree so implementation already present in the snapshot is not misclassified as Archive-only progression; | Body validation uses origin/master...finalHead facts while snapshot-to-final Archive progression is checked separately. |
| A44 | passed | specs/repository-pr-finish/spec.md | create exactly one PR when none exists; | Zero open PR creates one; retry reuses and createCount remains one. |
| A45 | passed | specs/repository-pr-finish/spec.md | reuse a unique open PR only when its title, body, base, head, accepted pre-Archive commit identity, accepted tree snapshot identity, and expected remote identity remain consistent with the authoring artifact; | Fingerprint/source proof binds accepted commit/tree and reuse independently revalidates PR number/URL/base/head/SHA/title/body. |
| A46 | passed | specs/repository-pr-finish/spec.md | fail closed rather than editing a drifted existing PR; | Any multiple-PR or identity/title/body drift fails closed and no edit path exists. |
| A47 | passed | specs/repository-pr-finish/spec.md | fetch and revalidate the remote body after creation or reuse; | Both create and reuse fetch the remote PR and rerun body validation. |
| A48 | passed | specs/repository-pr-finish/spec.md | provide a machine-readable result to the thin Comet adapter while preserving the supported manual dry-run entry. | Machine-readable result is supported while manual DryRun remains tested. |
| A49 | passed | specs/repository-pr-finish/spec.md | The authored PR body MUST preserve the tracked template and provide non-placeholder evidence for: | Tracked template headings/options and all reviewer-context fields are required and placeholder-free. |
| A50 | passed | specs/repository-pr-finish/spec.md | Summary and target/change type; | Summary and target/change type are concretely validated. |
| A51 | passed | specs/repository-pr-finish/spec.md | Issue hierarchy and Acceptance IDs; | Issue hierarchy and Acceptance IDs are concretely validated. |
| A52 | passed | specs/repository-pr-finish/spec.md | Runtime, Generator, Analyzer, AgentFacts, Public Docs, and Skill impact; | Runtime, Generator, Analyzer, AgentFacts, Public Docs and Skill rows require status plus evidence. |
| A53 | passed | specs/repository-pr-finish/spec.md | shared contracts and propagation closure; | Shared contracts and propagation closure are validated against generated facts. |
| A54 | passed | specs/repository-pr-finish/spec.md | composition evidence and sibling-slice responsibility; | Composition evidence and sibling responsibility require concrete content or reasoned N/A. |
| A55 | passed | specs/repository-pr-finish/spec.md | reviewer Audit Focus; | Audit Focus requires concrete reviewer context. |
| A56 | passed | specs/repository-pr-finish/spec.md | actual verification and any justified skips; | Verification requires selected executed checks or one concrete not-run reason, never both. |
| A57 | passed | specs/repository-pr-finish/spec.md | Agent Review request state; | Agent Review requires exactly one tracked option and a concrete reason when not requested. |
| A58 | passed | specs/repository-pr-finish/spec.md | release note. | Release Note requires concrete non-placeholder content. |
| A59 | passed | specs/repository-pr-finish/spec.md | The template is a structured context contract for human and AI reviewers. It is not a separate approval form. | Template is reviewer context for automatic PR finish, not another approval form. |
| A60 | passed | specs/repository-pr-finish/spec.md | Before any remote mutation, the flow MUST fail if the authoring artifact or deterministic validation is invalid, including a missing, invalid, non-tree, or tampered snapshot or any post-snapshot non-Archive path change. Tests MUST cover the isolated-index snapshot without altering the real index, permitted Runtime Archive progression, and each fail-closed snapshot case. After a PR has been observed, any provider failure MUST preserve enough verified identity for safe retry without treating unverified provider claims as trusted output. A failed finish MUST preserve the PR and worktree and expose the Native recovery command. Retrying with the same accepted commit, tree snapshot, and authored content MUST reuse the unique PR rather than create a duplicate. | Tests prove real-index immutability, permitted Archive progression, recomputed-fingerprint valid-tree/internal drift fail-closed before remote, unsupported final drift rejection, and idempotent create/reuse. |
| A61 | passed | specs/repository-pr-finish/spec.md | Native Builder/Verifier is the pre-PR acceptance loop. External AI reviewers such as CodeRabbit form a post-PR advisory loop. Review findings MUST be treated as untrusted input and independently checked against the current SHA before code is changed. | Native Builder/Verifier remains pre-PR acceptance; external AI findings remain untrusted advisory input. |
| A62 | passed | specs/repository-pr-finish/spec.md | The repository required `check` remains the deterministic merge gate. CodeRabbit or another external AI review loop is deferred until external-contributor pull requests create a concrete need. This capability does not install CodeRabbit, make an AI review a required approval, automate review-thread fixes, or automatically merge a PR. | Required check remains deterministic merge gate; no CodeRabbit install, required approval, auto-fix or auto-merge was added. |

## Checks

| Check | Command | Working directory | Status | Exit | Duration |
| --- | --- | --- | --- | ---: | ---: |
| PowerShell script syntax | -NoProfile -Command $files=@('scripts/comet-finish-pr.ps1','scripts/create-pr.ps1','scripts/validate-pr-body.ps1','scripts/test-pr-workflow.ps1'); foreach($file in $files){$tokens=$null;$errors=$null;[void][System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path $file),[ref]$tokens,[ref]$errors);if($errors.Count -gt 0){$errors\|ForEach-Object{Write-Error "$file`: $($_.Message)"};exit 1}};Write-Output 'OK: PowerShell parser' | . | passed | 0 | 515 ms |
| PR workflow integration tests | -NoProfile -ExecutionPolicy Bypass -File scripts/test-pr-workflow.ps1 | . | passed | 0 | 98816 ms |
| Capability contract validation | -NoProfile -ExecutionPolicy Bypass -File scripts/validate-capability-contract.ps1 | . | passed | 0 | 5323 ms |
| Capability contract validator tests | -NoProfile -ExecutionPolicy Bypass -File scripts/test-capability-contract.ps1 | . | passed | 0 | 35037 ms |
| Skill validation | -NoProfile -ExecutionPolicy Bypass -File skills/scripts/validate-cap4k-skills.ps1 | . | passed | 0 | 4494 ms |
| Current runtime facts validation | -NoProfile -ExecutionPolicy Bypass -File scripts/validate-current-runtime-facts.ps1 | . | passed | 0 | 6193 ms |
| Full Gradle check | /d /s /c gradlew.bat check | . | passed | 0 | 436852 ms |
| Git diff whitespace validation | diff --check | . | passed | 0 | 142 ms |

## Blockers

_None._

## Risks and skipped work

- Build did not create a real GitHub PR; this is the expected boundary because real remote mutation belongs to Archive after fresh Verify acceptance. Create/reuse and remote revalidation passed in an isolated Git repository with fake gh.
- Runtime-owned comet-state.yaml and verification.md changes are validated by strict critical semantics rather than whole-blob equality; every other accepted artifact, canonical Spec and changed path has exact path/blob proof.
- The worktree remains uncommitted and active until Archive, which owns commit, push, provider invocation and cleanup.
- One PostgreSQL environment-gated test was skipped as designed; the full Gradle check passed.

## Previous iterations

| Goal cycle | Iteration | Attempt | Outcome | Unresolved | Summary | Completed |
| ---: | ---: | ---: | --- | --- | --- | --- |
| 1 | 1 | 1 | pass | — | 独立 Verifier 完整读取 brief、目标 Spec、state、AGENTS 及全部变更，审查 Archive 后 provider 时序、JSON/Git/path/artifact 边界、单一 create-pr 入口、远端复验和恢复幂等，并结合 Runtime 全部通过的 8 项检查确认 A1-A62 全部通过；未发现阻止 Verify 接受的问题。 | 2026-08-16T10:13:59.934Z |
| 1 | 1 | 1 | recovery | — | Archive authoring preflight uncovered an invalidation: the current provider compares preArchiveHeadSha to the final Archive head and permits only Archive artifact paths, but the accepted implementation is still uncommitted before Archive, so the Archive commit would also contain implementation paths and the provider would fail every real first run. Return to Build to bind the authored pre-Archive working tree deterministically and reverify the real Archive ordering. | 2026-08-16T10:24:24.338Z |
| 1 | 2 | 0 | recovery | — | Native confirmed acceptance criteria changed | 2026-08-16T10:46:40.420Z |
| 2 | 1 | 1 | fail | A7, A9, A35, A36, A45, A60 | 此前未提交实现与单次 Archive commit 的结构性时序缺陷已修复，但 accepted snapshot 仍可被替换成另一个有效 tree。下一轮 Build 需要把 preArchive commit/tree 与关键 source identity 纳入确定性 artifact digest 或等价 Archive 可验证绑定，收紧 Runtime Archive progression 证明，并补充有效 tree 篡改、允许路径内非 Runtime 漂移和真实 index 不变测试。 | 2026-08-16T11:21:33.822Z |
| 2 | 2 | 1 | pass | — | Independent read-only verification passed. A1-A62 were reviewed exactly once against AGENTS, active state, brief, complete target Spec, implementation diff and Runtime checks. The prior A7/A9/A35/A36/A45/A60 gaps are substantively repaired: fingerprint/source identity binds preArchiveHeadSha and preArchiveTreeSha; exact tree/blob/path proof replaces broad prefix allowlisting; recomputed-fingerprint valid-tree replacement and allowed-path internal drift fail before remote mutation; and isolated-index snapshot tests prove the real index tree and bytes remain unchanged. No additional checks are required. | 2026-08-16T12:05:56.066Z |

## Conclusion

Independent read-only verification passed. A1-A62 were reviewed exactly once against AGENTS, active state, brief, complete target Spec, implementation diff and Runtime checks. The prior A7/A9/A35/A36/A45/A60 gaps are substantively repaired: fingerprint/source identity binds preArchiveHeadSha and preArchiveTreeSha; exact tree/blob/path proof replaces broad prefix allowlisting; recomputed-fingerprint valid-tree replacement and allowed-path internal drift fail before remote mutation; and isolated-index snapshot tests prove the real index tree and bytes remain unchanged. No additional checks are required.
