# Cap4k Repository Read Persist Default Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Flip repository and aggregate read APIs to default `persist = false`, then make write-path call sites explicit and synchronize docs and skills.

**Architecture:** Keep the existing `persist` runtime meaning intact, but invert the API default along the full read chain. Update focused runtime tests first, then change API defaults, then migrate command/sample callers that depend on implicit enlistment. Finish by syncing public authoring docs, analysis docs, and skill guidance so the write boundary is described explicitly.

**Tech Stack:** Kotlin, Gradle, JUnit 5, MockK, Markdown docs, repo-local skills.

---

## Scope

- API defaults change on `Repository`, `RepositorySupervisor`, `AggregateSupervisor`, and `Mediator`.
- Runtime semantics for explicit `persist = true` stay unchanged.
- Affected command/sample call sites are updated to opt in explicitly.
- `docs/public/authoring`, `docs/superpowers/analysis`, and `skills` are synchronized.

## File Structure

Runtime and API files:

- Modify `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/Repository.kt`
- Modify `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/RepositorySupervisor.kt`
- Modify `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregateSupervisor.kt`
- Modify `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt`

Focused tests:

- Modify `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/AbstractJpaRepositoryTest.kt`
- Modify `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt`
- Modify `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/aggregate/impl/DefaultAggregateSupervisorTest.kt`

Sample/downstream-style call sites inside cap4k workspace:

- Modify any reference/sample command or adapter handlers that rely on implicit default persistence tracking.

Docs and skills:

- Modify relevant files under `docs/public/authoring/`
- Modify relevant files under `docs/superpowers/analysis/`
- Modify relevant files under `skills/`

## Task 1: Lock the New Default in Focused Tests

**Files:**

- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/AbstractJpaRepositoryTest.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/aggregate/impl/DefaultAggregateSupervisorTest.kt`

- [ ] Add or rename tests so they assert the default read path does not enlist into persistence tracking.
- [ ] Keep explicit `persist = true` tests so opt-in behavior stays covered.
- [ ] Run:
  `./gradlew.bat :ddd-domain-repo-jpa:test --tests "*DefaultRepositorySupervisorTest" --tests "*DefaultAggregateSupervisorTest" --tests "*AbstractJpaRepositoryTest"`

## Task 2: Flip API Defaults Across the Read Chain

**Files:**

- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/Repository.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/RepositorySupervisor.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregateSupervisor.kt`

- [ ] Change default `persist` values from `true` to `false` on repository read methods.
- [ ] Change default `persist` values from `true` to `false` on repository supervisor read methods.
- [ ] Change default `persist` values from `true` to `false` on aggregate supervisor read methods.
- [ ] Run the focused repository/aggregate tests again.

## Task 3: Keep Mediator Surface Consistent

**Files:**

- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt`

- [ ] Change mediator-side default `persist` values for `repositories` and `aggregates` read methods to `false`.
- [ ] Scan for any compile fallout from changed defaults.
- [ ] Run:
  `./gradlew.bat :ddd-core:test :ddd-domain-repo-jpa:test --tests "*DefaultRepositorySupervisorTest" --tests "*DefaultAggregateSupervisorTest" --tests "*AbstractJpaRepositoryTest"`

## Task 4: Migrate Implicit Write Paths to Explicit Opt-In

**Files:**

- Modify the affected command/sample handlers that load then mutate entities or aggregates.

- [ ] Identify write-path reads that currently rely on old implicit enlistment.
- [ ] Add explicit `persist = true` only where later in-memory mutation plus `Mediator.uow.save()` depends on enlistment.
- [ ] Leave query and validation reads on the new default unless explicit persistence tracking is actually required.
- [ ] Run the smallest relevant compile or test commands for each affected module.

## Task 5: Sync Authoring Docs, Analysis Docs, and Skills

**Files:**

- Modify relevant files under `docs/public/authoring/`
- Modify relevant files under `docs/superpowers/analysis/`
- Modify relevant files under `skills/`

- [ ] Update public authoring guidance so read paths are described as detached by default and write paths explicitly opt into `persist = true`.
- [ ] Update analysis docs so the public tactical model and write-boundary explanation match the new default.
- [ ] Update skills or shared rules that teach repository usage so they no longer imply implicit read enlistment.
- [ ] Mark the behavior change as breaking where appropriate.

## Task 6: Verify and Prepare Issue Evidence

**Files:**

- Modify: `docs/superpowers/specs/2026-05-22-cap4k-repository-read-persist-default-design.md`
- Modify: `docs/superpowers/plans/2026-05-22-cap4k-repository-read-persist-default.md`
- Update GitHub issue `#70`

- [ ] Run `git diff --check`.
- [ ] Run targeted compile and test commands covering changed modules.
- [ ] Capture the exact commands and results for the issue comment.
- [ ] Update issue `#70` lifecycle after spec, plan, implementation, and verification milestones.
