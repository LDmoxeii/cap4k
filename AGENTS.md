# Cap4k Agent Guide

## First Read

When continuing work in `cap4k`, read this file first, then read:

- [Original architecture reset spec](docs/superpowers/specs/2026-04-09-cap4k-pipeline-redesign-design.md)
- the relevant GitHub issue that now acts as backlog source of truth
- the most recent relevant spec or plan under `docs/superpowers/`

## AI Authoring Skill

When a task involves cap4k project authoring, AI-assisted DDD implementation, generated-vs-handwritten ownership, tactical model placement, testing-contract evidence, or issue lifecycle updates for project-authoring work, route through the repo-local formal skill.

| Task | Read | Workflow |
|---|---|---|
| Decide or review cap4k authoring direction | [skills/cap4k-authoring/SKILL.md](skills/cap4k-authoring/SKILL.md), [skills/cap4k-authoring/rules/role-boundary.md](skills/cap4k-authoring/rules/role-boundary.md) | [skills/cap4k-authoring/workflows/design-before-code.md](skills/cap4k-authoring/workflows/design-before-code.md) |
| Implement a cap4k project slice | [skills/cap4k-authoring/SKILL.md](skills/cap4k-authoring/SKILL.md), [skills/cap4k-authoring/rules/layering-and-tactical-model.md](skills/cap4k-authoring/rules/layering-and-tactical-model.md), [skills/cap4k-authoring/rules/generator-ownership.md](skills/cap4k-authoring/rules/generator-ownership.md) | [skills/cap4k-authoring/workflows/implement-cap4k-project-slice.md](skills/cap4k-authoring/workflows/implement-cap4k-project-slice.md) |
| Finish with evidence and issue update | [skills/cap4k-authoring/SKILL.md](skills/cap4k-authoring/SKILL.md), [skills/cap4k-authoring/rules/verification-contract.md](skills/cap4k-authoring/rules/verification-contract.md), [skills/cap4k-authoring/references/issue-lifecycle.md](skills/cap4k-authoring/references/issue-lifecycle.md) | [skills/cap4k-authoring/workflows/close-task-with-evidence.md](skills/cap4k-authoring/workflows/close-task-with-evidence.md) |

Keep this file as a routing shell. Do not duplicate the skill's rules here.

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

## Continuing Work

- If the user says "continue the original mainline", use the current GitHub issues plus the newest relevant spec/plan to identify the active slice.
- If the user says "unblock real project integration", read the relevant integration specs first. Do not silently turn an integration workaround into a new global framework rule.
- If the user says "work on bootstrap" or "work on arch-template migration", treat that as a separate slice. Do not silently mix it into design-template migration.

## Current Planning State

GitHub issues are now the backlog source of truth. Repository docs remain design assets:

- issues track backlog, state, and closure
- specs and plans track design and implementation detail
- before starting implementation, re-read the target issue plus the newest relevant spec/plan against current `master`

Current high-signal issues include:

- `#15` framework capability audit
- `#16` README rewrite after capability positioning stabilizes
- `#17` DDD + cap4k + AI collaboration guide
- other open issues only when their boundaries are still current after reading the latest specs/plans

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
