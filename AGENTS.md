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
- if the current branch is `master`, `publish/aliyun-private`, or `publish/maven-central`, stop and create or switch to an isolated worktree before editing
- normal implementation and documentation work starts from `origin/master`
- use a short-lived branch such as `feature/*`, `fix/*`, or `docs/*`; docs/spec/plan edits are not an exception to this rule
- use the existing `.worktrees/` directory for project-local worktrees when it is available and ignored
- do not reuse a worktree that is currently checked out on a publish branch for mainline work

Reading, searching, and review-only commands may run on `master`. Any repository mutation must happen on a non-protected working branch.

## Cap4k Skill Routing

When a task involves cap4k business-project authoring, use the repo-local skill router as the only routing source:

- [skills/cap4k-authoring/SKILL.md](skills/cap4k-authoring/SKILL.md)
- [skills/cap4k-authoring/routing.yaml](skills/cap4k-authoring/routing.yaml)

Load the routed focused skill and every `required_reads` entry from `routing.yaml`.
Do not duplicate per-task route rows in this shell.

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
- Bootstrap or arch-template migration, when implemented, must remain a separate capability rather than widening design-template helper authority.
- the old monolithic generator module `cap4k-plugin-codegen` has been removed from the active repository. Do not reintroduce it or add new compatibility work around that path; mainline generator work belongs to the pipeline plugin family.

## Work Classification

There are three kinds of work in this repo now:

1. Mainline design-generator quality work
2. Real-project integration boundary work
3. Bootstrap or arch-template migration work

## Branch And Release Policy

`cap4k` has three long-lived branches:

- `master`
- `publish/aliyun-private`
- `publish/maven-central`

`cap4k` does not use a long-lived `develop` branch, `release/vX.Y.Z` branch, or `verify/* -> publish/*` lane as a normal integration or release-promotion path.

Use these branch roles instead:

- `feature/*`, `fix/*`, `docs/*`: short-lived working branches for normal changes
- `master`: the main integration branch for framework development
- `publish/aliyun-private`: private Aliyun snapshot publish channel
- `publish/maven-central`: Maven Central release channel

Expected promotion flow:

1. `feature/* -> master`
2. `master -> publish/aliyun-private` when the Aliyun snapshot channel needs the accepted master content
3. `master -> publish/maven-central` when the Central release channel needs the accepted master content
4. `publish/aliyun-private` push -> Aliyun snapshot publish
5. `publish/maven-central` commit -> `v*` tag -> Maven Central release

Direct-development rules:

- do not implement normal work directly on `master`; start from a short-lived branch in an isolated worktree
- do not commit directly on `master`; land mainline code through `feature/* -> master` pull requests
- do not implement normal work directly on either publish branch; publish branches are release channels, not feature branches
- do not commit directly on either publish branch; publish-channel updates must come from `master` by pull request
- if a branch is an issue branch, ad-hoc branch, docs branch, or any other working branch, treat it like `feature/*`: it must land on `master`, not directly on a publish branch

Pull request policy:

- working branch -> `master`: required
- `master -> publish/aliyun-private`: required for Aliyun promotion
- `master -> publish/maven-central`: required for Central promotion
- do not open working-branch, issue-branch, ad-hoc-branch, docs-branch, or `verify/*` pull requests into either publish branch
- publish-branch pull requests must use same-repository `master` as the head branch

CI and branch protection contract:

- the required status check context is `check`
- `master`, `publish/aliyun-private`, and `publish/maven-central` are protected by required PRs, strict `check`, and admin enforcement
- PRs into `master` run Gradle only when the change can affect code, build, scripts, workflows, tests, fixtures, or template resources
- docs-only PRs into `master` skip Gradle but still complete the required `check` job
- docs-only includes `docs/**`, `README*`, root Markdown files, `.github/ISSUE_TEMPLATE/**`, and `.github/PULL_REQUEST_TEMPLATE.md`
- `.github/workflows/**`, `scripts/**`, `buildSrc/**`, `gradle/**`, Gradle files, source files, test files, fixtures, and template resources are not docs-only
- PRs into publish branches only validate that the head is same-repository `master`; they do not run Gradle because `master` already carried the full check
- if GitHub rejects a publish PR because the head is not `master`, do not bypass protection; land the work on `master` first and promote `master`

Release safety rules:

- `master` should stay free of mandatory Central or private-repository publishing credentials
- publish workflow changes are normal mainline changes: working branch -> `master`, then `master -> publish/*`
- publish workflows do not run duplicate Gradle `check`
- Aliyun snapshot publish is branch-push-driven from `publish/aliyun-private`
- Maven Central release is tag-driven, not branch-push-driven
- only push release tags for commits that are contained in `origin/publish/maven-central`
- do not use `develop` as the default base branch for new work, release prep, or issue execution

## Continuing Work

- If the user says "continue the original mainline", use the current GitHub issues plus the newest relevant spec/plan to identify the active slice.
- If the user says "unblock real project integration", read the relevant integration specs first. Do not silently turn an integration workaround into a new global framework rule.
- If the user says "work on bootstrap" or "work on arch-template migration", treat that as a separate slice. Do not silently mix it into design-template migration.

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
- UUID7 is the default application-side ID policy, Snowflake remains explicit as `snowflake-long`, database `@IdGenerator` comments are unsupported, and field-level `@ApplicationSideId` is the runtime contract

## Known Test Fixture Debt

Full `:cap4k-ddd-starter:test` currently fails because of old starter auto-configuration test fixture isolation problems, not because of the UUID7/application-side ID policy slice.

Observed shape:

- targeted UUID7 and application-side ID tests pass
- full starter test run fails around old `@SpringBootTest` context startup tests
- broad test applications under `com.only4.cap4k.ddd` use package-wide `@ComponentScan`, `@EntityScan`, and `@EnableJpaRepositories`, causing test fixtures and framework repositories to be scanned together
- repeated repository bean names such as `sagaJpaRepository`, `requestJpaRepository`, and runtime fixture repositories collide across test contexts
- some contexts start Snowflake auto-configuration without the `__worker_id` table because tests still use stale Snowflake property keys
- some contexts create `DefaultEventSubscriberManager` with a blank event scan package and fail at `ScanUtils.scanClass`
- one initialization test excludes Hibernate JPA auto-configuration while still enabling JPA repository scanning, so `entityManagerFactory` is missing

Do not re-debug this as a functional regression without first checking the open issue for starter test fixture isolation. Treat it as a separate test-maintenance slice unless a fresh failure affects the focused UUID7/application-side ID tests or runtime fixtures.

## Reading Order

1. [AGENTS.md](AGENTS.md)
2. the relevant GitHub issue
3. [2026-04-09-cap4k-pipeline-redesign-design.md](docs/superpowers/specs/2026-04-09-cap4k-pipeline-redesign-design.md)
4. the most recent relevant specs/plans under `docs/superpowers/`

## Notes

- `docs/superpowers/specs/` and `docs/superpowers/plans/` contain the historical slices
- GitHub issues now carry backlog and lifecycle state; docs are no longer the backlog source of truth
