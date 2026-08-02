# Cap4k Agent Guide

## First Read

When continuing work in `cap4k`, read this file first, then read:

- [Original architecture reset spec](docs/superpowers/specs/2026-04-09-cap4k-pipeline-redesign-design.md)
- the relevant GitHub issue that now acts as backlog source of truth
- the most recent relevant spec or plan under `docs/superpowers/`

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

When a task involves cap4k business-project authoring, use the repo-local thin skill as the only cap4k-specific routing source:

- [skills/cap4k-authoring/SKILL.md](skills/cap4k-authoring/SKILL.md)
- [skills/cap4k-authoring/routing.yaml](skills/cap4k-authoring/routing.yaml)

Select the smallest operation route, read its `required_reads`, and load only the listed Agent API sections after reading `build/cap4k/agent/manifest.json`.
Do not recreate phase skills, mandatory DDD artifacts, approval gates, or duplicate route rows in this shell.

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
- PRs into `master` run Gradle only when the change can affect code, build, scripts, workflows, tests, fixtures, or template resources
- docs-only PRs into `master` skip Gradle but still complete the required `check` job
- PR workflow guard tests run in the required `check` job for normal and docs-only pull requests so PR template and PR creation scripts stay aligned
- docs-only includes `docs/**`, `README*`, root Markdown files, `.github/ISSUE_TEMPLATE/**`, and `.github/PULL_REQUEST_TEMPLATE.md`
- `.github/workflows/**`, `scripts/**`, `buildSrc/**`, `gradle/**`, Gradle files, source files, test files, fixtures, and template resources are not docs-only

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

- If the user says "continue the original mainline", use the current GitHub issues plus the newest relevant spec/plan to identify the active slice.
- If the user says "unblock real project integration", read the relevant integration specs first. Do not silently turn an integration workaround into a new global framework rule.
- If a request refers to the retired bootstrap capability, do not restore it; redirect project initialization to the official GitHub Template or explicit manual structure work.

## Current Planning State

GitHub issues are now the backlog source of truth. Repository docs remain design assets:

- issues track backlog, state, and closure
- specs and plans track design and implementation detail
- before starting implementation, re-read the target issue plus the newest relevant spec/plan against current `master`

Do not rely on a static issue list in this file. Query the current issue state or use the issue/spec/plan explicitly named by the user.

Do not execute an old historical plan just because it exists. Re-read the relevant spec and plan against current `master`, update them if the repository or user's latest decisions changed the boundary, and then execute from the refreshed plan.

Recent durable decisions to preserve:

- generated-source routing is an ownership problem, not a simple exporter-root switch
- enum, schema `S*`, standard repository, converter, and aggregate `Unique*` support are default generated-source candidates
- aggregate behavior scaffolds are checked-in source, generated by default per aggregate root as `<AggregateRootName>Behavior.kt`
- old request-family transaction-scope concerns came from JPA lazy-loading failures around unit-of-work save boundaries; those failures were mitigated through object-graph expansion, so transaction-boundary widening is not an active direction without fresh evidence
- CLI/distributed-client requests currently lack a dedicated marker, so avoid accidental command-policy inclusion
- aggregate JPA runtime problems should be reproduced in focused fixtures before replacing repository or unit-of-work backends
- frontend TypeScript generation is currently not planned as a cap4k core slice unless a first-class endpoint tactical model or stable API-contract projection exists
- public README and AI-collaboration rules should be written only after the capability audit clarifies what remains supported, optimized, or deleted
- Application-side entity IDs are generated Strong IDs. Supported strategies are `uuid7` and `snowflake`. The backing type follows JDBC storage; generated typed accessors allocate IDs, and generated catalogs feed the runtime registry.

## Known Test Fixture Debt

The old aggregate `cap4k-ddd-starter` fixture and its configured skips were deleted with the starter split. Do not use its historical 94-test/29-skip result as current evidence.

Current ownership evidence:

- Core starter tests cover UUID7, ID registries, static Mediator binding, synchronous Request, local Domain Event, missing reliable capability, and provider conflict failure.
- JPA starter owns the migrated Strong ID, soft-delete, OwnedEntityList, aggregate graph, provider-assigned field, and UoW runtime fixtures.
- Request/Event/Saga/Locker/Snowflake and each Integration Event transport starter own focused auto-configuration tests.
- event package scanning and capability enable properties are removed; a new failure involving them is a stale reference, not expected fixture debt.

Do not dismiss a fresh failure as known debt. Reproduce it in the capability owner and distinguish framework behavior from test-context isolation with current evidence.

## Reading Order

1. [AGENTS.md](AGENTS.md)
2. the relevant GitHub issue
3. [2026-04-09-cap4k-pipeline-redesign-design.md](docs/superpowers/specs/2026-04-09-cap4k-pipeline-redesign-design.md)
4. the most recent relevant specs/plans under `docs/superpowers/`

## Notes

- `docs/superpowers/specs/` and `docs/superpowers/plans/` contain the historical slices
- GitHub issues now carry backlog and lifecycle state; docs are no longer the backlog source of truth
