# Cap4k Agent Guide

## First Read

When continuing work in `cap4k`, read this file first, then read:

- [Original architecture reset spec](docs/superpowers/specs/2026-04-09-cap4k-pipeline-redesign-design.md)
- the relevant GitHub issue when the work comes from backlog or parent/child governance
- the active Comet Native change when starting or resuming a change
- the most recent relevant canonical spec or historical design asset

## Repository Entry Guard

Before modifying files, committing, pushing, or opening a pull request, check the current branch and worktree state:

- run `git status --short --branch`
- run `git branch --show-current`
- if the current branch is `master`, stop and create or switch to an isolated worktree before editing
- normal implementation and documentation work starts from `origin/master`
- use a short-lived branch such as `feature/*`, `fix/*`, or `docs/*`; docs/spec/plan edits are not an exception to this rule
- use the existing `.worktrees/` directory for project-local worktrees when it is available and ignored
- historical `publish/*` refs, if they still exist during release-governance migration, are retirement targets rather than valid working bases

Reading, searching, and review-only commands may run on `master`. Any repository mutation must happen on a non-protected working branch.

## Cap4k Skill Routing

Use the repo-local thin authoring skill only when the target is a downstream business project that consumes cap4k:

- [skills/cap4k-authoring/SKILL.md](skills/cap4k-authoring/SKILL.md)
- [skills/cap4k-authoring/routing.yaml](skills/cap4k-authoring/routing.yaml)

Select the smallest operation route, read its `required_reads`, and load only the listed Agent API sections after reading `build/cap4k/agent/manifest.json`.
Do not recreate phase skills, mandatory DDD artifacts, approval gates, or duplicate route rows in this shell.

Do not load `cap4k-authoring` for development or governance of the cap4k framework repository itself. Runtime, Generator, Analyzer, Pipeline plugin, build logic, AgentFacts, public-doc, release, and repository-governance work follows this file plus the relevant repository design assets. Load `issue-governance` only when an Issue is actually being created, split, linked, updated, audited, or closed. Use Comet Native when starting or resuming a formal change.

Keep this file as a routing shell. Do not duplicate focused skill rules here.

## What This Project Is Doing

`cap4k` is in a breaking redesign from the old mixed Gradle/codegen/plugin model to a fixed-stage pipeline.

The stable direction is:

- fixed pipeline stages owned by plugin developers
- repository-level source and generator configuration
- canonical model between sources and generators
- renderer helpers that stay thin and do not take type-resolution ownership back from Kotlin code

## Do Not Reopen These Boundaries

- Pipeline stage order is not customizable by project users.
- Project users can enable or disable sources and generators, but cannot inject custom runtime logic.
- Sibling design-entry type references are still unsupported.
- Short-name auto resolution must stay conservative.
- Symbol identity and explicit FQN remain the source of truth for imports.
- `use()` is design-template-only and must remain a thin explicit-import helper.
- Bootstrap capability is retired. Do not restore its tasks, DSL, module, root guards, managed markers, slot/merge workflow, aliases, or migration bridge; use the official GitHub Template or manual project setup.
- the old monolithic generator module `cap4k-plugin-codegen` has been removed from the active repository. Do not reintroduce it or add new compatibility work around that path; mainline generator work belongs to the pipeline plugin family.

## Work Classification

There are three kinds of work in this repo now:

1. Mainline design-generator quality work
2. Real-project integration boundary work

## Capability Contract Governance

Runtime, Generator, and Analyzer are product capability surfaces. AgentFacts, Public Docs, and the authoring Skill are downstream projections and operation surfaces. A capability-contract change is complete only after its declared direct and transitive dependencies have been audited.

Contribution rules:

- derive enumerable facts from production descriptors, registries, task registration, artifact/output contracts, and Agent section definitions; do not add a second handwritten task/provider/section/output catalog
- use `scripts/export-capability-contract-facts.ps1` and `scripts/validate-capability-contract.ps1` to inspect the current dependency graph and projection alignment
- for every PR, record Runtime, Generator, Analyzer, AgentFacts, Public Docs, and Skill as `modified`, `verified-no-change`, or `not-applicable`, with concrete evidence or a reason
- a Runtime contract change must evaluate the complete propagation closure, not only Generator and Analyzer; the same closure rule applies from any changed contract node
- Public Docs and Skill describe only current supported behavior and boundaries; history belongs in Git, releases, changelogs, migration surfaces, GitHub issues, and archived internal design assets
- AgentFacts remain generated from production contracts and current project observation; Public Docs and Skill never become reverse inputs to the facts catalog
- documentation and governance/Skill-only pull requests may skip the full Gradle `check`, but they must still export code-derived facts and run capability, Skill, Runtime, PR-template, and workflow validation

Governance objects have separate roles:

- a GitHub Issue preserves backlog intent, priority, dependencies, ownership, and lifecycle across changes; an immediately authorized coherent change does not require an Issue first
- a GitHub Parent Issue fixes overall intent, acceptance IDs, child inventory, dependencies, and composition status
- Child Issues own independently reviewable and mergeable slices; when a Child/Standalone Issue enters execution, it normally maps to one Comet change and its implementation PR
- Parent closure requires all required children plus composition evidence on one accepted `origin/master` lineage
- Comet Shape owns the detailed target specification and acceptance loop for one change unit; the Issue remains macro-level and must not duplicate the change specification
- `docs/comet/changes/<change>/specs/**` describes the complete target behavior of the active change; `docs/comet/specs/**` describes the accepted authoritative capability contract after Archive
- canonical Comet specs may lead implementation when a spec/audit change confirms the target, but they are not backlog storage; undecided ideas remain in Issues, while accepted but deferred implementation keeps an Issue for priority and status
- Comet is not a live multi-PR dependency graph; a Parent Issue normally composes several short-lived changes rather than one long-running change

Audit and implementation have different lifecycles:

- read-only audit may inspect `master`, but persisted audit notes or Comet artifacts must use a short-lived docs worktree from current `origin/master`
- an audit change records verified current facts, accepted target decisions, implementation status, and independently executable slices; it may Archive a spec-only target before implementation only when the brief and verification explicitly state that scope and gap
- merge accepted audit conclusions and canonical target specs back to `master`; do not keep an audit branch alive as the integration point for later implementation PRs, and do not project unimplemented targets into Public Docs, AgentFacts, or Skill as current support
- each independently mergeable implementation starts from current `origin/master` and its own Comet change, consumes the current canonical contract, adds a spec change only when the contract itself changes, then Builds, Verifies, Archives, and lands code plus evidence together

## Branch And Release Policy

`cap4k` has one long-lived source branch: `master`.

`cap4k` does not use long-lived `develop`, `release/*`, `verify/*`, or `publish/*` branches. Artifact destinations and local source substitution are not source-branch roles.

Use these branch roles instead:

- `feature/*`, `fix/*`, `docs/*`: short-lived working branches for normal changes
- `master`: the main integration branch for framework development

Expected integration and release flow:

1. `feature/* -> master`
2. accepted `master` commit -> exact `v<major>.<minor>.<patch>` tag
3. tag workflow -> Maven Central release and GitHub Release

Direct-development rules:

- do not implement normal work directly on `master`; start from a short-lived branch in an isolated worktree
- do not commit directly on `master`; land mainline code through `feature/* -> master` pull requests
- if a historical publish branch still exists during migration, do not merge, commit, tag, or open new promotion PRs against it

Pull request policy:

- working branch -> `master`: required
- `master` is the only supported PR base
- the head must be an unqualified same-repository short-lived branch, not `master` or another long-lived branch
- release workflow and governance changes follow the same working-branch -> `master` path as other changes
- before opening a pull request, use `scripts/create-pr.ps1` so tracked PR templates are discovered case-insensitively, the completed body is validated against the template headings, and the created PR body is checked after creation
- direct `gh pr create` usage is reserved for cases where `scripts/create-pr.ps1` cannot run; when using it directly, first discover templates with `git ls-files | rg -i '(^|/)(pull_request_template\.md|pull_request_template/.*\.md)$'`, fill the tracked template, and validate the final body with `scripts/validate-pr-body.ps1 -Base <base-branch> -RequireChangeType`

CI and branch protection contract:

- the required status check context is `check`
- `master` is protected by required PRs, strict `check`, and admin enforcement
- PRs into `master` run Gradle only when the change can affect code, build, root governance scripts, workflows, tests, fixtures, or template resources
- documentation and governance/Skill-only PRs into `master` skip Gradle but still complete the required `check` job
- PR workflow guard tests run in the required `check` job for normal and lightweight pull requests so PR template, PR creation scripts, and CI path classification stay aligned
- documentation-only includes `docs/**`, `README*`, root Markdown files except `AGENTS.md`, `.github/ISSUE_TEMPLATE/**`, and `.github/PULL_REQUEST_TEMPLATE.md`
- governance/Skill-only includes `AGENTS.md`, `.agents/skills/**`, `skills/**`, and `.comet/config.yaml`; focused capability, Skill, Runtime, PR-template, and workflow guards remain mandatory
- `.github/workflows/**`, root `scripts/**`, `buildSrc/**`, `gradle/**`, Gradle files, source files, test files, fixtures, and template resources are Gradle-impacting even when mixed with lightweight paths
- renames and copies classify both the source and destination path; crossing from a lightweight path to a Gradle-impacting path, or the reverse, requires Gradle

Release safety rules:

- `master`, ordinary CI, local Composite Builds, and consumer builds must stay free of mandatory publishing credentials
- publish workflow changes are normal mainline changes: working branch -> `master`
- publish workflows do not run duplicate Gradle `check`
- Maven Central release is driven only by an exact `v<major>.<minor>.<patch>` tag
- only push release tags for commits that are contained in `origin/master`
- do not add Aliyun, Central Snapshot, another remote Snapshot channel, or `com.only4` compatibility without a new confirmed design
- local cap4k/consumer co-development uses an explicitly enabled Gradle Composite Build; the official Template must not contain sibling paths, Snapshot repositories, filesystem repositories, or `mavenLocal()`
- keep the existing `v2.0.1` tag and historical promotion merges unchanged; the single-mainline contract applies to future releases
- retire historical publish branches, their protection rules, and Aliyun secrets only after the single-mainline workflow is merged and its required smoke checks pass
- do not use `develop` as the default base branch for new work, release prep, or issue execution

## Continuing Work

- If the user says "continue the original mainline", use the current GitHub Issues when they exist, active Comet changes, canonical contracts, and latest `origin/master` facts to identify the active slice.
- If the user says "unblock real project integration", read the relevant integration specs first. Do not silently turn an integration workaround into a new global framework rule.
- If a request refers to the retired bootstrap capability, do not restore it; redirect project initialization to the official GitHub Template or explicit manual structure work.

## Current Planning State

GitHub Issues are the backlog source of truth when work needs a durable backlog record. Comet is the execution source of truth for an authorized change. Repository docs remain design assets:

- Issues track macro intent, priority, dependencies, state, and closure; they are optional for an immediately authorized coherent change
- an active Comet change tracks its goal, detailed target specs, acceptance loop, and evidence
- canonical Comet specs track accepted authoritative contracts and may precede implementation; code-derived facts, tests, AgentFacts, and Public Docs determine what the current build actually supports
- archived Comet changes and `docs/superpowers/` assets preserve history and design context; they are not active backlog queues
- before starting implementation, re-read the target Issue when one exists, then Shape or resume the Comet change against current `origin/master` and current canonical contracts

Do not rely on a static Issue list in this file. Query the current Issue state or use the Issue or Comet change explicitly named by the user.

Do not execute an old historical plan or archived change just because it exists. Re-read the relevant current contracts and design context against current `origin/master`, refresh the active Shape when the repository or user's latest decisions changed the boundary, and then execute from that refreshed change.

Recent durable decisions to preserve:

- generated-source routing is an ownership problem, not a simple exporter-root switch
- enum, schema `S*`, standard repository, converter, and aggregate `Unique*` support are default generated-source candidates
- aggregate behavior scaffolds are checked-in source, generated by default per aggregate root as `<AggregateRootName>Behavior.kt`
- old request-family transaction-scope concerns came from JPA lazy-loading failures around unit-of-work save boundaries; those failures were mitigated through object-graph expansion, so transaction-boundary widening is not an active direction without fresh evidence
- CLI/distributed-client requests currently lack a dedicated marker, so avoid accidental command-policy inclusion
- aggregate JPA runtime problems should be reproduced in focused fixtures before replacing repository or unit-of-work backends
- frontend TypeScript generation is currently not planned as a cap4k core slice unless a first-class endpoint tactical model or stable API-contract projection exists
- public README and AI-collaboration rules describe current production contracts and are validated against code-derived capability facts; they do not depend on a historical capability-audit branch.
- Application-side entity IDs are generated Strong IDs. `uuid7` is the only built-in application-side allocation strategy. The backing type follows JDBC storage; generated typed accessors allocate IDs, and generated catalogs feed the runtime registry. Database-assigned identity remains a persistence policy, not an application-side generator.

## Known Test Fixture Debt

The old aggregate `cap4k-ddd-starter` fixture and its configured skips were deleted with the starter split. Do not use its historical 94-test/29-skip result as current evidence.

Current ownership evidence:

- Core starter tests cover UUID7, ID registries, static Mediator binding, synchronous Request, local Domain Event, missing reliable capability, and provider conflict failure.
- JPA starter owns the migrated Strong ID, soft-delete, OwnedEntityList, aggregate graph, provider-assigned field, and UoW runtime fixtures.
- Command JPA, Domain Event JPA, and each Integration Event transport starter own focused auto-configuration tests.
- event package scanning and capability enable properties are removed; a new failure involving them is a stale reference, not expected fixture debt.

Do not dismiss a fresh failure as known debt. Reproduce it in the capability owner and distinguish framework behavior from test-context isolation with current evidence.

## Reading Order

1. [AGENTS.md](AGENTS.md)
2. the relevant GitHub Issue, when one exists
3. the active Comet change, when starting or resuming one
4. [2026-04-09-cap4k-pipeline-redesign-design.md](docs/superpowers/specs/2026-04-09-cap4k-pipeline-redesign-design.md)
5. the relevant canonical Comet specs and most recent historical design assets

## Notes

- `docs/superpowers/specs/` and `docs/superpowers/plans/` contain the historical slices
- GitHub Issues carry durable backlog and cross-change lifecycle state; active Comet changes carry execution detail
