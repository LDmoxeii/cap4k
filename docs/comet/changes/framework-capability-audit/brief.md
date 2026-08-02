# Outcome

Audit Generator, Runtime, and Analyzer after the Skill responsibility reset from PR #153, preserve the decisions in one continuous audit context, and determine when the combined four-block system is coherent enough to begin downstream real-project validation.

# Scope

- Treat the merged thin Skill and machine-readable Agent API direction as the accepted Skill baseline.
- Audit Generator first, then Runtime, then Analyzer against current mainline contracts, implementation, tests, public documentation, Agent API facts, and relevant open issues.
- Classify findings as verified, partial, drift, missing-core, provider/extension, or downstream-evidence.
- Discuss findings before any implementation is authorized.
- Re-evaluate an affected block after an independently implemented fix has merged to `master` and has been brought back to the audit line.
- Produce a combined downstream-readiness decision only after all three remaining blocks have been audited.

# Non-goals

- Do not validate the reference project before the three remaining block audits are complete.
- Do not claim that cap4k covers every strategic DDD concept or can prove domain correctness.
- Do not repair Generator, Runtime, Analyzer, documentation, or downstream projects opportunistically on the audit branch.
- Do not create separate Generator, Runtime, or Analyzer audit branches.
- Do not preserve compatibility for hypothetical users through aliases, shims, dual paths, migration bridges, deprecation periods, or legacy fallback behavior.
- Do not reopen the retired Bootstrap capability.

# Acceptance examples

- Given a Generator contract advertised by descriptors, documentation, or Agent API facts, the audit identifies the actual source, canonical model, planner/template, output ownership, conflict policy, and proportionate tests, or records a specific discrepancy.
- Given a Runtime boundary such as reliable event payload persistence, the audit treats the current runtime semantic boundary as authoritative and identifies generator or documentation drift without weakening that runtime boundary.
- Given an Analyzer observation, the audit distinguishes structural evidence from business truth and identifies freshness, coverage, and transport limitations.
- Given a finding that requires implementation, the audit records the decision and expected acceptance evidence while leaving the code unchanged on this branch.
- Given a fix merged independently to `master`, the audit refreshes from that mainline fact and re-runs the affected gate before changing its conclusion.
- Downstream validation is not opened while any unresolved missing-core or cross-block drift finding remains.

# Constraints and invariants

- `docs/framework-capability-audit` is the only continuous audit and decision branch.
- Framework fixes use isolated short-lived `feature/*` or `fix/*` branches and enter `master` through the repository's normal pull-request path.
- Cap4k currently has no external users; breaking iteration is allowed and a single clean current contract is preferred.
- Humans own strategic/domain decisions and acceptance. Agents assist investigation, translation, implementation, and verification without claiming business authority.
- Skill routes; Generator projects explicit inputs and canonical models; Runtime owns real execution semantics; Analyzer supplies structural observation and engineering evidence.
- Current repository facts outrank historical chat, stale issue text, and old plans.
- PR #152's runtime refusal to persist reliable-event entity payloads is an accepted boundary and must not be reversed as a generator fix.

# Decisions

- PR #153 completed the Skill responsibility reset and is the baseline for this audit.
- Audit the remaining blocks sequentially in this order: Generator, Runtime, Analyzer.
- Keep audit artifacts and cross-block decisions on one branch to minimize context loss.
- Findings are discussed before implementation; implementation is forked into another session and branch by the user when accepted.
- Perform one combined downstream validation only after all four blocks have a coherent current contract.
- Compatibility work requires future evidence of a real consumer and an explicit compatibility requirement.

# Open questions

- Should the current Generator contract remove `Scheduled Reaction` from the Design JSON capability descriptor and generated-skeleton documentation, while retaining it only as a handwritten application reaction/Job surface unless a future first-class carrier and runtime contract are explicitly designed? This is the recommended option because current tags, canonical assembly, planners, templates, runtime carriers, and the thin Skill already support that boundary; the alternative would be a materially new Generator/Runtime capability rather than a documentation correction.

# Verification expectations

- Cite current source files, descriptors, generated plans/templates, tests, public documentation, Agent API outputs, and relevant GitHub issues for each finding.
- Run focused module checks and compile/functional fixtures in proportion to the audited risk; never report an unrun check as passing.
- Record accepted unsupported boundaries and their cross-block impact explicitly.
- Maintain recoverable Comet checkpoints that reference the current audit artifact and next action.
- Before opening downstream validation, verify that no unresolved missing-core or cross-block drift finding remains and that the thin Skill can route an agent to machine-readable facts for all relevant blocks.
