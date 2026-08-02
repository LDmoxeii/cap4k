# Outcome
Re-audit and complete PR #154 on `fix/generator-contract-surface` so the live Generator contract surface matches the 2026-08-02 Generator Capability Audit. The branch must be ready for a pull request into `master`, with no retired generator or validator compatibility surface restored.

# Scope
- Remove `Scheduled Reaction` from the Design JSON capability descriptor and every current Agent API projection/test that would expose it as a generated tactical carrier.
- Keep Scheduled Reaction/Job as a handwritten application implementation surface; do not add a design tag, canonical carrier, planner, template, runtime provider, alias, or no-op generator for it.
- Align public `plan.json` and Agent ownership examples/tests with live short generator ids, complete repository-relative `outputPath`, and optional `resolvedOutputRoot` metadata.
- Retire the standalone Python generator-input validator, its public documentation entry/navigation, and any current fixture, command, CI, README, skill, or maintenance references that exist only to keep that second rule set alive.
- Remove the unused generic aggregate Specification layout helper and its test; do not create or restore a Specification generator.
- Synchronize descriptor, supported-tag/canonical-artifact/planner evidence, Agent API snapshot evidence, public documentation, renderer/codec fixtures, and focused regression tests.

# Non-goals
- Do not implement Strong ID Spring MVC binding, semantic Design/Analyzer round trip, analysis metadata migration, H2 isolation, projection extensions, or diagnostics backlog.
- Do not edit historical audit/spec/plan or archived Comet evidence merely to erase historical references; current live product, public, skill, test, fixture, script, and CI surfaces are the target.
- Do not restore `design-*` generator ids, validator wrappers, deprecated aliases, migration bridges, or compatibility entry points.

# Acceptance examples
- The Design JSON descriptor tactical carriers are exactly `Command`, `Query`, `Capability`, `API Payload`, `Domain Event`, `Integration Event`, `Domain Service`, and `Subscriber`; `Scheduled Reaction` is absent.
- `scheduled_reaction` and `job` Design JSON entries fail, and no canonical default artifact or registered authoring planner exists for them.
- A real seven-tag Design JSON fixture plans the exact current design generator ids and derives subscribers only from event artifacts; no scheduled/job/validator generator appears.
- Public and Agent ownership examples use current ids, complete repository-relative `outputPath`, and explain that `resolvedOutputRoot` is optional metadata that must not be concatenated with `outputPath`.
- Renderer and codec positive fixtures use current generator/provider ids rather than retired `design-*` or generic `design` ids.
- The standalone validator script/page/navigation/commands/wiring are absent from live surfaces; source parsing, canonical assembly, `cap4kPlan`, and `cap4kAgentSnapshot` remain authoritative.
- `ArtifactLayoutResolver` exposes no generic aggregate Specification package helper and no corresponding test remains.

# Constraints and invariants
- Work stays on the isolated `fix/generator-contract-surface` worktree/branch based on current `origin/master`; PR base remains `master`.
- Descriptor facts, canonical supported tags/default artifacts, planner registration, plan JSON, renderer/codec fixtures, and Agent API must not drift into a second public truth.
- `outputPath` is the final repository-relative target path. `resolvedOutputRoot` is optional root metadata; checked-in items may leave it empty or provide a source root, and generated-source rebasing remains unchanged.
- Checked-in source keeps `SKIP`; fixed pipeline and runtime/event boundaries remain unchanged.
- Breaking cleanup is required; no compatibility surface is permitted.

# Decisions
- The audit contract is the source of truth.
- Scheduled Reaction remains handwritten application/job implementation, not a Design JSON carrier.
- Existing source/canonical/plan/Agent API owners remain authoritative; the retired Python validator is not replaced.
- Historical specs/plans and archived Comet evidence retain provenance and are excluded from live-surface zero-reference scans.
- The user confirmed this bounded contract with “继续” on 2026-08-02.

# Open questions
- None.

# Verification expectations
- Run focused Design JSON source/descriptor, API layout, pipeline Agent Snapshot/plan, Agent codec, renderer, and generated-source rebase tests affected by the repair.
- Run live-surface scans for Scheduled Reaction generator overclaims, old positive generator ids, retired validator entry points, and Specification helper references, excluding historical specs/plans and archived evidence.
- Run `git diff --check`, inspect the final diff, record actual evidence in Comet, push the branch, and update PR #154 rather than opening a duplicate PR.
