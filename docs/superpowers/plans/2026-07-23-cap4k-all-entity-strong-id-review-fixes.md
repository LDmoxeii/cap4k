# Cap4k All-Entity Strong ID Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the blocking review findings discovered on PR #132 before merge.

**Architecture:** Keep the Phase 2 design intact: `EXISTING` is an enrollment/baseline intent, not create inference; own Strong IDs may be generated for new create graphs and new owned children under observed existing roots, but not for existing roots. Keep generator Strong ID resolution aligned across entity and factory planners. Keep evidence conservative and reproducible.

**Tech Stack:** Kotlin, Spring Data JPA, Hibernate, Gradle, PowerShell PR workflow scripts.

## Global Constraints

- Do not reintroduce public `PersistIntent.UPDATE`, internal `UnitOfWorkIntent.UPDATE`, or `persistIfNotExist`.
- Do not reintroduce root-only Strong ID generation gates.
- Do not infer Strong IDs from removed `@Reference`; remaining `@Reference` usage must be rejection tests only.
- `PersistType.UPDATE` remains a result/listener type only.
- `EXISTING` must not generate or replace an existing root own Strong ID.
- Repository-loaded clean existing entities must not emit update listeners.
- Generated own ID fields use Strong ID wrappers and `@EmbeddedId`; reference Strong IDs remain non-generating reference values.
- PR body must keep all headings from `.github/PULL_REQUEST_TEMPLATE.md`.

---

### Task 11: Fix JPA Existing Enrollment Runtime Semantics

**Files:**
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaHibernateDirtyInspector.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedStrongIdSupport.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaRepositoryObservationBaseline.kt`
- Test: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`
- Test: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdUowRuntimeTest.kt`

**Interfaces:**
- Consumes: `JpaRepositoryObservationBaseline.record(root, observed)` and `JpaGeneratedOwnedRelationTraversal.reachableOwnedEntities(root)`.
- Produces: runtime behavior where clean detached existing entities are not listener-updated, dirty managed/merged existing entities are listener-updated, and `EXISTING` never fills missing root own Strong IDs.

- [ ] **Step 1: Write failing tests**

Add tests that fail on current code:

```kotlin
// In JpaUnitOfWorkTest.kt or a focused runtime fixture:
// 1. EXISTING with a missing root @EmbeddedId Strong ID fails before completion.
// 2. EXISTING completes only a new owned child missing own Strong ID under an observed root.
// 3. A clean detached existing entity merged by UoW does not emit PersistType.UPDATE.
// 4. A loaded existing entity mutated before save emits PersistType.UPDATE through real Hibernate dirty inspection.
```

- [ ] **Step 2: Verify RED**

Run focused tests:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest" --rerun-tasks --console=plain
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdUowRuntimeTest" --rerun-tasks --console=plain
```

Expected: at least one new test fails because current code uses the detached instance for dirty inspection or completes the missing root ID.

- [ ] **Step 3: Implement minimal runtime fix**

Required behavior:

```kotlin
private fun applyExisting(entity: Any, results: FlushResult) {
    validateExistingRootIdentified(entity)
    val managed = if (entityManager.contains(entity)) entity else entityManager.merge(entity)
    results.existing.add(managed)
    results.needsFlush = true
}
```

Also adjust Strong ID existing completion so it validates root identity first and only completes unobserved owned children, not the root. Enforce observed identity consistency using the recorded baseline identity, failing fast if an observed entity's ID changes before flush.

- [ ] **Step 4: Verify GREEN**

Run the same focused commands from Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt `
        ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaHibernateDirtyInspector.kt `
        ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedStrongIdSupport.kt `
        ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaRepositoryObservationBaseline.kt `
        ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt `
        cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdUowRuntimeTest.kt
git commit -m "fix: preserve existing strong id enrollment semantics"
```

### Task 12: Fix Factory Strong ID Resolution Ambiguity

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt`
- Test: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

**Interfaces:**
- Consumes: `StrongIdModel.kind`, `StrongIdModel.fqn()`, `FieldModel.type`.
- Produces: factory payload and constructor Strong ID resolution that mirrors entity planner selection: own fields use owner `OWN_ID`; reference fields prefer non-`OWN_ID` when the same simple name exists.

- [ ] **Step 1: Write failing planner test**

Add a test where the canonical model contains both an own `AuthorId` and a reference `AuthorId`, and a factory payload/constructor field references `AuthorId`. Current code should fail with `ambiguous strong id type AuthorId`.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --rerun-tasks --console=plain
```

Expected: the new test fails on the current ambiguity.

- [ ] **Step 3: Implement minimal planner fix**

Update `FactoryArtifactPlanner.resolveStrongId(...)` to apply deterministic selection instead of `matches.size <= 1`. For fields that are not the entity own ID, prefer non-`OWN_ID` Strong IDs when both own and reference wrappers share a simple name. Keep fully qualified matches exact.

- [ ] **Step 4: Verify GREEN**

Run the same focused command from Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt `
        cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "fix: resolve factory strong id references deterministically"
```

### Task 13: Repair Evidence, PR Body, And Governance Guardrails

**Files:**
- Modify: `docs/superpowers/specs/2026-07-22-cap4k-all-entity-strong-id-design.md`
- Modify: `docs/superpowers/specs/2026-07-22-cap4k-identity-roadmap-design.md`
- Modify: `docs/superpowers/plans/2026-07-23-cap4k-all-entity-strong-id-phase2.md`
- Modify: `.superpowers/sdd/progress.md`
- Modify: `.github/workflows/ci.yml`
- Test: `scripts/test-pr-workflow.ps1`

**Interfaces:**
- Consumes: Task 11 and Task 12 verification results.
- Produces: conservative verification evidence and CI-level live PR body validation.

- [ ] **Step 1: Add PR body validation guard to CI**

Add a pull-request-only step before Gradle checks that writes `${{ github.event.pull_request.body }}` to a temporary file and runs:

```powershell
./scripts/validate-pr-body.ps1 -BodyFile $env:RUNNER_TEMP/pr-body.md -Base $env:BASE_REF -RequireChangeType
```

- [ ] **Step 2: Align docs evidence**

Update the spec and plan so broad Windows verification is not overstated. If the only full green signal is GitHub CI, say so. Record any local command that timed out as timeout, not pass.

- [ ] **Step 3: Fix roadmap drift**

Make the Phase 3 summary table and Phase 3 section agree that Phase 3 mediator identifier generation has been completed and merged to `master` on 2026-07-23.

- [ ] **Step 4: Update PR body**

Update PR #132 so the `Verification` section includes Task 11 and Task 12 focused commands, final static drift scan, `git diff --check`, and GitHub CI status. Keep every template heading.

- [ ] **Step 5: Verify**

```powershell
.\scripts\test-pr-workflow.ps1
.\scripts\validate-pr-body.ps1 -BodyFile <current-pr-body-file> -Base master -RequireChangeType
git diff --check
```

Expected: all commands PASS.

- [ ] **Step 6: Commit**

```powershell
git add .github/workflows/ci.yml scripts/test-pr-workflow.ps1 `
        docs/superpowers/specs/2026-07-22-cap4k-all-entity-strong-id-design.md `
        docs/superpowers/specs/2026-07-22-cap4k-identity-roadmap-design.md `
        docs/superpowers/plans/2026-07-23-cap4k-all-entity-strong-id-phase2.md `
        .superpowers/sdd/progress.md
git commit -m "docs: reconcile strong id review evidence"
```
