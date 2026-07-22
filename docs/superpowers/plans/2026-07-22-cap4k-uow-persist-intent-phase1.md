# Cap4k UoW Persist Intent Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement cap4k identity roadmap Phase 1 by making Unit of Work create/update/remove intent explicit before JPA operation selection.

**Architecture:** Add a small core `PersistIntent` API, route aggregate factory creation as `CREATE`, keep repository `persist=true` as `UPDATE`, and refactor `JpaUnitOfWork` from separate persist/remove sets into one object-identity pending-change model. JPA save flow must classify listener results from UoW intent, not from ID defaultness, JPA `isNew`, or database existence queries.

**Tech Stack:** Kotlin/JVM, Spring Data JPA, Jakarta Persistence `EntityManager`, Spring `Propagation`, Gradle Kotlin DSL, JUnit 5, MockK, H2 runtime tests, ripgrep static verification.

## Global Constraints

- Work only in `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\uow-persist-intent-phase1` on branch `codex/uow-persist-intent-phase1`.
- Do not implement directly on `master`.
- The two copied spec files must stay in this worktree under `docs/superpowers/specs/`; the original copies on `master` must not be moved or deleted.
- Primary specs for this slice are `docs/superpowers/specs/2026-07-22-cap4k-identity-roadmap-design.md` and `docs/superpowers/specs/2026-07-22-cap4k-uow-persist-intent-design.md`.
- In scope: `UnitOfWork` public contract, `DefaultMediator` forwarding, `DefaultAggregateFactorySupervisor`, `DefaultRepositorySupervisor`, `JpaUnitOfWork`, `PersistType` listener classification, focused tests, and current-contract docs if static search finds active references.
- Out of scope: Strong ID generation shape, entity constructor shape, factory payload shape, `IdAllocator` or `IdStrategyRegistry` public API design, database schema changes, generator input changes, repository read API redesign, transaction propagation redesign, and cap4k dirty checking.
- `persist(entity)` means `PersistIntent.UPDATE`.
- Factory-created aggregates must call `persist(entity, PersistIntent.CREATE)`.
- Repository reads with `persist=true` must register update intent; repository reads with `persist=false` must not enter the UoW write set.
- Manual new entity creation outside a factory must call `persist(entity, PersistIntent.CREATE)`.
- `remove(entity)` remains the only public delete registration method; do not add public `persist(entity, REMOVE)`.
- `PersistType` remains post-flush listener classification and must not become the pre-flush source of truth.
- Do not keep `persistIfNotExist(entity)` in production or active test contracts.
- Do not use database existence queries to choose create versus update for the main UoW path.
- Keep `UnitOfWorkInterceptor` method signatures unchanged unless the spec is revised first.
- Do not add dependency installations.
- Commits are allowed only in this worktree on branch `codex/uow-persist-intent-phase1`; never commit on `master`.

---

## Current Baseline Evidence

- Worktree created at `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\uow-persist-intent-phase1`.
- Copied specs are untracked in the worktree and remain present on `master`.
- Baseline command passed: `.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.aggregate.impl.DefaultAggregateFactorySupervisorTest" --no-daemon`.
- Baseline command passed: `.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest" --tests "com.only4.cap4k.ddd.domain.repo.impl.DefaultRepositorySupervisorTest" --no-daemon`.

---

## File Structure

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/PersistIntent.kt`
  - New public enum for pre-flush UoW write intent.
  - Owns only `CREATE` and `UPDATE`.

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/UnitOfWork.kt`
  - Owns the public UoW contract.
  - After this slice it exposes `persist(entity, intent = UPDATE)`, `remove`, and `save`; it no longer exposes `persistIfNotExist`.

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt`
  - Forwards the new `persist(entity, intent)` signature to `UnitOfWork.instance`.
  - Removes `persistIfNotExist` forwarding.

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/impl/DefaultAggregateFactorySupervisor.kt`
  - Registers factory-created aggregate instances as `PersistIntent.CREATE`.

- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt`
  - Keeps repository read APIs unchanged.
  - Converts `persist=true` registration sites from method references to explicit `PersistIntent.UPDATE` calls.

- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt`
  - Keeps compatibility save-time ID assignment for manually annotated `@ApplicationSideId`.
  - Adds a way for update intent to assign IDs only to owned relation objects, not to the update root.

- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
  - Owns pending change registration, transaction wrapping, JPA operation selection, application-side ID preparation, and listener classification.
  - After this slice it is driven by explicit pending intent.

- `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/impl/DefaultAggregateFactorySupervisorTest.kt`
  - Verifies factory-created aggregates register `PersistIntent.CREATE`.

- `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediatorTest.kt`
  - New focused forwarding test for `DefaultMediator.persist(entity, intent)`.

- `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt`
  - Verifies `persist=true` loads register `PersistIntent.UPDATE`.

- `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`
  - Main unit test surface for pending-change merge rules and JPA operation selection.

- `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupportTest.kt`
  - Adds direct coverage for assigning child IDs without assigning an update-root ID.

- `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/ApplicationSideIdJpaRuntimeTest.kt`
  - Migrates manual new-root persistence to `PersistIntent.CREATE`.
  - Adds runtime coverage for adding a new application-side-ID child during update intent.

- `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt`
  - Migrates helper-created new roots to `PersistIntent.CREATE`.
  - Leaves loaded aggregate update flows on default `persist(entity)`.

---

### Task 1: Core UoW Contract And Factory/Mediator Forwarding

**Files:**
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/PersistIntent.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/UnitOfWork.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/impl/DefaultAggregateFactorySupervisor.kt`
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/impl/DefaultAggregateFactorySupervisorTest.kt`
- Create: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediatorTest.kt`

**Interfaces:**
- Consumes: existing `UnitOfWork`, `DefaultMediator`, `DefaultAggregateFactorySupervisor`.
- Produces: `enum class PersistIntent { CREATE, UPDATE }`.
- Produces: `fun persist(entity: Any, intent: PersistIntent = PersistIntent.UPDATE)` on `UnitOfWork` and `Mediator`.
- Produces: no production `persistIfNotExist(entity)` API in `ddd-core`.

- [ ] **Step 1: Write failing factory and mediator tests**

In `DefaultAggregateFactorySupervisorTest.kt`, add the import:

```kotlin
import com.only4.cap4k.ddd.core.application.PersistIntent
```

Change the create-path verifications to assert create intent:

```kotlin
verify { unitOfWork.persist(result, PersistIntent.CREATE) }
```

Change the multi-factory verifications:

```kotlin
verify { unitOfWork.persist(result1, PersistIntent.CREATE) }
verify { unitOfWork.persist(result2, PersistIntent.CREATE) }
```

Change the failure setup:

```kotlin
every { unitOfWork.persist(any(), PersistIntent.CREATE) } throws RuntimeException("Database error")
```

Keep the no-match verifications compiling against the new signature:

```kotlin
verify(exactly = 0) { unitOfWork.persist(any(), any()) }
```

Create `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediatorTest.kt`:

```kotlin
package com.only4.cap4k.ddd.core.impl

import com.only4.cap4k.ddd.core.application.PersistIntent
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.UnitOfWorkSupport
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Propagation

class DefaultMediatorTest {
    @Test
    fun `persist forwards entity and intent to configured unit of work`() {
        val unitOfWork = RecordingUnitOfWork()
        UnitOfWorkSupport.configure(unitOfWork)
        val entity = Any()

        DefaultMediator().persist(entity, PersistIntent.CREATE)

        assertSame(entity, unitOfWork.persistedEntity)
        assertEquals(PersistIntent.CREATE, unitOfWork.persistedIntent)
    }

    private class RecordingUnitOfWork : UnitOfWork {
        var persistedEntity: Any? = null
        var persistedIntent: PersistIntent? = null

        override fun persist(entity: Any, intent: PersistIntent) {
            persistedEntity = entity
            persistedIntent = intent
        }

        override fun remove(entity: Any) = Unit

        override fun save(propagation: Propagation) = Unit
    }
}
```

- [ ] **Step 2: Run the tests and confirm the expected compile failure**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.aggregate.impl.DefaultAggregateFactorySupervisorTest" --tests "com.only4.cap4k.ddd.core.impl.DefaultMediatorTest" --no-daemon
```

Expected: FAIL because `PersistIntent` and the two-argument `persist` contract do not exist yet.

- [ ] **Step 3: Add the core API**

Create `PersistIntent.kt`:

```kotlin
package com.only4.cap4k.ddd.core.application

enum class PersistIntent {
    CREATE,
    UPDATE,
}
```

In `UnitOfWork.kt`, replace the public methods before `remove` with:

```kotlin
    /**
     * 提交实体持久化意图。
     *
     * 默认意图为 UPDATE。工厂创建的新聚合应显式传入 PersistIntent.CREATE。
     *
     * @param entity 需要持久化的实体对象
     * @param intent 持久化意图
     * @throws IllegalArgumentException 当实体对象无效时
     */
    fun persist(entity: Any, intent: PersistIntent = PersistIntent.UPDATE)
```

Delete the `persistIfNotExist(entity)` declaration and its KDoc from `UnitOfWork.kt`.

- [ ] **Step 4: Update DefaultMediator forwarding**

In `DefaultMediator.kt`, add the import:

```kotlin
import com.only4.cap4k.ddd.core.application.PersistIntent
```

Replace the UoW persist forwarding block with:

```kotlin
    override fun persist(entity: Any, intent: PersistIntent) {
        UnitOfWork.instance.persist(entity, intent)
    }
```

Delete this block:

```kotlin
    override fun persistIfNotExist(entity: Any): Boolean =
        UnitOfWork.instance.persistIfNotExist(entity)
```

- [ ] **Step 5: Update factory registration**

In `DefaultAggregateFactorySupervisor.kt`, add the import:

```kotlin
import com.only4.cap4k.ddd.core.application.PersistIntent
```

Replace:

```kotlin
        unitOfWork.persist(instance)
```

with:

```kotlin
        unitOfWork.persist(instance, PersistIntent.CREATE)
```

- [ ] **Step 6: Run core tests**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.aggregate.impl.DefaultAggregateFactorySupervisorTest" --tests "com.only4.cap4k.ddd.core.impl.DefaultMediatorTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Static check core API removal**

Run:

```powershell
rg -n "persistIfNotExist" ddd-core/src/main
```

Expected: no matches.

- [ ] **Step 8: Commit Task 1 in the worktree**

Run:

```powershell
git status --short
git add ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/PersistIntent.kt `
        ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/UnitOfWork.kt `
        ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt `
        ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/impl/DefaultAggregateFactorySupervisor.kt `
        ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/impl/DefaultAggregateFactorySupervisorTest.kt `
        ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediatorTest.kt
git commit -m "feat: add explicit uow persist intent contract"
```

Expected: commit created on `codex/uow-persist-intent-phase1`; `git branch --show-current` prints `codex/uow-persist-intent-phase1`.

---

---

### Task 2: Repository Update Registration

**Files:**
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt`

**Interfaces:**
- Consumes: `UnitOfWork.persist(entity, PersistIntent.UPDATE)` from Task 1.
- Produces: repository `persist=true` calls that explicitly declare update intent.
- Produces: no change to repository read defaults or detach behavior.

- [ ] **Step 1: Write failing repository supervisor tests**

In `DefaultRepositorySupervisorTest.kt`, add:

```kotlin
import com.only4.cap4k.ddd.core.application.PersistIntent
```

Change the update-path verifications from:

```kotlin
verify { mockUnitOfWork.persist(expectedEntity) }
```

to:

```kotlin
verify { mockUnitOfWork.persist(expectedEntity, PersistIntent.UPDATE) }
```

Do the same for the list case:

```kotlin
expectedEntities.forEach { entity ->
    verify { mockUnitOfWork.persist(entity, PersistIntent.UPDATE) }
}
```

Add an explicit negative assertion on the default read-only path so the test proves no write registration happens:

```kotlin
verify(exactly = 0) { mockUnitOfWork.persist(any(), any()) }
```

- [ ] **Step 2: Run the repository tests and confirm they fail before implementation**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.domain.repo.impl.DefaultRepositorySupervisorTest" --no-daemon
```

Expected: FAIL because `PersistIntent` is not yet wired through the repository supervisor call sites.

- [ ] **Step 3: Implement explicit update registration**

In `DefaultRepositorySupervisor.kt`, add the import:

```kotlin
import com.only4.cap4k.ddd.core.application.PersistIntent
```

Replace every `if (persist) unitOfWork.persist(...)` site with explicit update intent. A compact helper is fine:

```kotlin
private fun registerUpdate(entity: Any) {
    unitOfWork.persist(entity, PersistIntent.UPDATE)
}
```

Then use it at the call sites:

```kotlin
.also { if (persist) it.forEach(::registerUpdate) }
```

and:

```kotlin
?.also { if (persist) registerUpdate(it) }
```

Keep `persist=false` paths untouched so validation-only reads still stay out of the UoW write set.

- [ ] **Step 4: Run the repository tests**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.domain.repo.impl.DefaultRepositorySupervisorTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit Task 2 in the worktree**

Run:

```powershell
git status --short
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt `
        ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt
git commit -m "feat: register repository updates with explicit intent"
```

Expected: commit created on `codex/uow-persist-intent-phase1`.

---

---

### Task 3: Application-Side ID Compatibility Helper For Update Paths

**Files:**
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupportTest.kt`

**Interfaces:**
- Consumes: existing `assignMissingIds(root)` create-path compatibility behavior.
- Produces: a second helper for owned-relation-only assignment during update intent.
- Produces: preserved root ID on update paths when the root already carries a non-default application-side ID.

- [ ] **Step 1: Write a failing owned-child assignment test**

In `JpaApplicationSideIdSupportTest.kt`, add a test that proves update preparation does not mutate the root ID while still assigning owned child IDs:

```kotlin
@Test
fun `assigns owned child ids without changing root during update prep`() {
    val rootId = UUID.fromString("018f0000-0000-7000-8000-000000000099")
    val root = RootEntity(id = rootId)
    root.children += ChildEntity()

    support.assignMissingIdsToOwnedRelations(root)

    assertEquals(rootId, root.id)
    assertEquals(UUID(1L, 2L), root.children.single().id)
}
```

The test should fail to compile until the new helper exists.

- [ ] **Step 2: Run the support test and confirm the missing helper failure**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaApplicationSideIdSupportTest" --no-daemon
```

Expected: FAIL because `assignMissingIdsToOwnedRelations(...)` does not exist yet.

- [ ] **Step 3: Add the update-path helper**

In `JpaApplicationSideIdSupport.kt`, keep `assignMissingIds(root)` unchanged for create paths and add:

```kotlin
fun assignMissingIdsToOwnedRelations(root: Any) {
    assignMissingIdsToOwnedRelations(root, Collections.newSetFromMap(IdentityHashMap()))
}

private fun assignMissingIdsToOwnedRelations(entity: Any, visited: MutableSet<Any>) {
    if (!visited.add(entity)) return

    ownedRelationValues(entity).forEach { related ->
        when (related) {
            is Iterable<*> -> related.filterNotNull().forEach { assignMissingIdsToOwnedRelations(it, visited) }
            else -> assignMissingIdsToOwnedRelations(related, visited)
        }
    }
}
```

Do not assign the root's own ID in this helper.

- [ ] **Step 4: Run the support test**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaApplicationSideIdSupportTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit Task 3 in the worktree**

Run:

```powershell
git status --short
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt `
        ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupportTest.kt
git commit -m "feat: preserve root ids on uow update preparation"
```

Expected: commit created on `codex/uow-persist-intent-phase1`.

---

### Task 4: JpaUnitOfWork Pending Intent Model And Save Flow

**Files:**
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`

**Interfaces:**
- Consumes: `PersistIntent` from Task 1.
- Consumes: `JpaApplicationSideIdSupport.assignMissingIdsToOwnedRelations(root)` from Task 3.
- Produces: object-identity pending-change registration for create, update, and remove.
- Produces: JPA save flow driven by pending intent, not existence queries.
- Produces: listener classification from final applied intent.

- [ ] **Step 1: Write failing UoW intent tests**

In `JpaUnitOfWorkTest.kt`, add imports:

```kotlin
import com.only4.cap4k.ddd.core.application.PersistIntent
import com.only4.cap4k.ddd.core.domain.repo.PersistType
```

Replace the old `testPersistIfNotExistWhenNotExists` test with create intent coverage:

```kotlin
@Test
@DisplayName("CREATE intent should persist a new entity and report CREATE")
fun createIntentShouldPersistAndReportCreate() {
    val entity = TestEntity(null, "new")
    every { mockEntityInfo.isNew(entity) } returns true

    jpaUnitOfWork.persist(entity, PersistIntent.CREATE)
    jpaUnitOfWork.save()

    verify { entityManager.persist(entity) }
    verify { entityManager.flush() }
    verify { entityManager.refresh(entity) }
    verify { persistListenerManager.onChange(entity, PersistType.CREATE) }
    verify(exactly = 0) { entityManager.merge(entity) }
}
```

Add default-update coverage:

```kotlin
@Test
@DisplayName("default persist should merge a detached existing entity and report UPDATE")
fun defaultPersistShouldMergeDetachedExistingEntityAndReportUpdate() {
    val entity = TestEntity(1L, "existing")
    every { mockEntityInfo.isNew(entity) } returns false
    every { mockEntityInfo.getId(entity) } returns 1L
    every { entityManager.contains(entity) } returns false

    jpaUnitOfWork.persist(entity)
    jpaUnitOfWork.save()

    verify { entityManager.merge(entity) }
    verify { persistListenerManager.onChange(entity, PersistType.UPDATE) }
    verify(exactly = 0) { entityManager.persist(entity) }
}
```

Add managed update coverage:

```kotlin
@Test
@DisplayName("managed update intent should report UPDATE without explicit merge")
fun managedUpdateIntentShouldReportUpdateWithoutExplicitMerge() {
    val entity = TestEntity(1L, "managed")
    every { mockEntityInfo.isNew(entity) } returns false
    every { mockEntityInfo.getId(entity) } returns 1L
    every { entityManager.contains(entity) } returns true

    jpaUnitOfWork.persist(entity)
    jpaUnitOfWork.save()

    verify(exactly = 0) { entityManager.merge(entity) }
    verify(exactly = 0) { entityManager.persist(entity) }
    verify { persistListenerManager.onChange(entity, PersistType.UPDATE) }
}
```

Add same-instance merge rule coverage:

```kotlin
@Test
@DisplayName("same instance CREATE then default persist remains CREATE")
fun sameInstanceCreateThenDefaultPersistRemainsCreate() {
    val entity = TestEntity(null, "created-then-mutated")
    every { mockEntityInfo.isNew(entity) } returns true

    jpaUnitOfWork.persist(entity, PersistIntent.CREATE)
    jpaUnitOfWork.persist(entity)
    jpaUnitOfWork.save()

    verify { entityManager.persist(entity) }
    verify { persistListenerManager.onChange(entity, PersistType.CREATE) }
    verify(exactly = 0) { entityManager.merge(entity) }
}
```

Add create-then-remove cancellation coverage:

```kotlin
@Test
@DisplayName("same instance CREATE then remove should cancel pending change")
fun sameInstanceCreateThenRemoveShouldCancelPendingChange() {
    val entity = TestEntity(null, "cancelled")

    jpaUnitOfWork.persist(entity, PersistIntent.CREATE)
    jpaUnitOfWork.remove(entity)
    jpaUnitOfWork.save()

    verify(exactly = 0) { entityManager.persist(any()) }
    verify(exactly = 0) { entityManager.merge(any()) }
    verify(exactly = 0) { entityManager.remove(any()) }
    verify(exactly = 0) { entityManager.flush() }
    verify(exactly = 0) { persistListenerManager.onChange(any(), any()) }
}
```

Add invalid same-instance transitions:

```kotlin
@Test
@DisplayName("same instance UPDATE then CREATE should fail fast")
fun sameInstanceUpdateThenCreateShouldFailFast() {
    val entity = TestEntity(1L, "existing")

    jpaUnitOfWork.persist(entity)

    val error = assertThrows(IllegalStateException::class.java) {
        jpaUnitOfWork.persist(entity, PersistIntent.CREATE)
    }

    assertTrue(error.message!!.contains("UPDATE cannot become CREATE"))
}

@Test
@DisplayName("same instance REMOVE then UPDATE should fail fast")
fun sameInstanceRemoveThenUpdateShouldFailFast() {
    val entity = TestEntity(1L, "removed")

    jpaUnitOfWork.remove(entity)

    val error = assertThrows(IllegalStateException::class.java) {
        jpaUnitOfWork.persist(entity)
    }

    assertTrue(error.message!!.contains("REMOVE cannot become UPDATE"))
}
```

Add application-side ID create classification without existence lookup:

```kotlin
@Test
@DisplayName("CREATE intent with preassigned application-side id should not query existence")
fun createIntentWithPreassignedApplicationSideIdShouldNotQueryExistence() {
    val entity = ApplicationSideLongEntity(id = 100L, name = "new")
    every { mockEntityInfo.isNew(entity) } returns false
    every { mockEntityInfo.getId(entity) } returns 100L

    jpaUnitOfWork.persist(entity, PersistIntent.CREATE)
    jpaUnitOfWork.save()

    verify { entityManager.persist(entity) }
    verify { persistListenerManager.onChange(entity, PersistType.CREATE) }
    verify(exactly = 0) { entityManager.find(ApplicationSideLongEntity::class.java, any()) }
    verify(exactly = 0) { entityManager.merge(entity) }
}
```

Add same-identity conflict coverage:

```kotlin
@Test
@DisplayName("different instances with same identity should fail before flush")
fun differentInstancesWithSameIdentityShouldFailBeforeFlush() {
    val first = TestEntity(7L, "first")
    val second = TestEntity(7L, "second")
    every { mockEntityInfo.isNew(first) } returns false
    every { mockEntityInfo.isNew(second) } returns false
    every { mockEntityInfo.getId(first) } returns 7L
    every { mockEntityInfo.getId(second) } returns 7L

    jpaUnitOfWork.persist(first)
    jpaUnitOfWork.remove(second)

    val error = assertThrows(IllegalStateException::class.java) {
        jpaUnitOfWork.save()
    }

    assertTrue(error.message!!.contains("conflicting UnitOfWork registrations"))
    verify(exactly = 0) { entityManager.flush() }
}
```

- [ ] **Step 2: Run the UoW test and confirm the expected failures**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest" --no-daemon
```

Expected: FAIL because `JpaUnitOfWork` still has the old one-argument `persist`, `persistIfNotExist`, separate pending sets, and existence-query operation selection.

- [ ] **Step 3: Add pending-change registration model**

In `JpaUnitOfWork.kt`, add the `PersistIntent` import:

```kotlin
import com.only4.cap4k.ddd.core.application.PersistIntent
```

Replace the companion pending state with:

```kotlin
private val pendingChangesThreadLocal = ThreadLocal.withInitial { PendingChangeSet() }
private val processingEntitiesThreadLocal = ThreadLocal.withInitial { LinkedHashSet<Any>() }
```

Update `reset()`:

```kotlin
@JvmStatic
fun reset() {
    pendingChangesThreadLocal.remove()
    processingEntitiesThreadLocal.remove()
}
```

Add these private types in `JpaUnitOfWork.kt`:

```kotlin
private enum class UnitOfWorkIntent {
    CREATE,
    UPDATE,
    REMOVE,
}

private data class PendingChange(
    val entity: Any,
    val intent: UnitOfWorkIntent,
)

private class ObjectIdentityKey(private val entity: Any) {
    override fun equals(other: Any?): Boolean =
        other is ObjectIdentityKey && entity === other.entity

    override fun hashCode(): Int = System.identityHashCode(entity)
}

private class PendingChangeSet {
    private val entries = LinkedHashMap<ObjectIdentityKey, PendingChange>()

    fun persist(entity: Any, intent: PersistIntent) {
        val key = ObjectIdentityKey(entity)
        val next = intent.toUnitOfWorkIntent()
        val current = entries[key]
        entries[key] = when (current?.intent) {
            null -> PendingChange(entity, next)
            UnitOfWorkIntent.CREATE -> PendingChange(entity, UnitOfWorkIntent.CREATE)
            UnitOfWorkIntent.UPDATE -> when (next) {
                UnitOfWorkIntent.UPDATE -> PendingChange(entity, UnitOfWorkIntent.UPDATE)
                UnitOfWorkIntent.CREATE -> error("UoW intent conflict: UPDATE cannot become CREATE for the same instance")
                UnitOfWorkIntent.REMOVE -> error("persist cannot register REMOVE intent")
            }
            UnitOfWorkIntent.REMOVE -> error("UoW intent conflict: REMOVE cannot become ${next.name} for the same instance")
        }
    }

    fun remove(entity: Any) {
        val key = ObjectIdentityKey(entity)
        val current = entries[key]
        when (current?.intent) {
            null -> entries[key] = PendingChange(entity, UnitOfWorkIntent.REMOVE)
            UnitOfWorkIntent.CREATE -> entries.remove(key)
            UnitOfWorkIntent.UPDATE -> entries[key] = PendingChange(entity, UnitOfWorkIntent.REMOVE)
            UnitOfWorkIntent.REMOVE -> Unit
        }
    }

    fun drain(): List<PendingChange> {
        val changes = entries.values.toList()
        entries.clear()
        return changes
    }
}

private fun PersistIntent.toUnitOfWorkIntent(): UnitOfWorkIntent = when (this) {
    PersistIntent.CREATE -> UnitOfWorkIntent.CREATE
    PersistIntent.UPDATE -> UnitOfWorkIntent.UPDATE
}
```

Replace the public registration methods with:

```kotlin
override fun persist(entity: Any, intent: PersistIntent) {
    pendingChangesThreadLocal.get().persist(entity, intent)
}

override fun remove(entity: Any) {
    pendingChangesThreadLocal.get().remove(entity)
}
```

Delete `override fun persistIfNotExist(entity: Any): Boolean`.

- [ ] **Step 4: Replace save flow with intent-driven dispatch**

Add these private helper structures:

```kotlin
private data class SaveInput(
    val changes: List<PendingChange>,
    val persistedEntities: Set<Any>,
    val removedEntities: Set<Any>,
    val processedEntities: Set<Any>,
)

private data class FlushResult(
    val created: LinkedHashSet<Any> = LinkedHashSet(),
    val updated: LinkedHashSet<Any> = LinkedHashSet(),
    val deleted: LinkedHashSet<Any> = LinkedHashSet(),
    val refreshList: MutableList<Any> = mutableListOf(),
    var needsFlush: Boolean = false,
)

private data class EntityIdentity(
    val entityType: Class<*>,
    val id: Any,
)
```

Replace `save(propagation)` with this structure:

```kotlin
override fun save(propagation: Propagation) {
    val currentProcessedEntitySet = LinkedHashSet<Any>()
    val pendingChanges = pendingChangesThreadLocal.get().drain()
    pendingChanges.forEach { pushProcessingEntity(it.entity, currentProcessedEntitySet) }

    val persistEntitySet = pendingChanges
        .filter { it.intent == UnitOfWorkIntent.CREATE || it.intent == UnitOfWorkIntent.UPDATE }
        .mapTo(LinkedHashSet()) { it.entity }
    val deleteEntitySet = pendingChanges
        .filter { it.intent == UnitOfWorkIntent.REMOVE }
        .mapTo(LinkedHashSet()) { it.entity }

    prepareApplicationSideIds(pendingChanges)
    validateSameIdentityConflicts(pendingChanges)
    uowInterceptors.forEach { it.beforeTransaction(persistEntitySet, deleteEntitySet) }

    try {
        save(
            SaveInput(
                changes = pendingChanges,
                persistedEntities = persistEntitySet,
                removedEntities = deleteEntitySet,
                processedEntities = currentProcessedEntitySet,
            ),
            propagation,
        ) { input ->
            val results = FlushResult()
            uowInterceptors.forEach { it.preInTransaction(input.persistedEntities, input.removedEntities) }

            input.changes.forEach { change ->
                when (change.intent) {
                    UnitOfWorkIntent.CREATE -> applyCreate(change.entity, results)
                    UnitOfWorkIntent.UPDATE -> applyUpdate(change.entity, results)
                    UnitOfWorkIntent.REMOVE -> applyRemove(change.entity, results)
                }
            }

            if (results.needsFlush) {
                entityManager.flush()
                results.refreshList.forEach { entityManager.refresh(it) }
                onEntitiesFlushed(results.created, results.updated, results.deleted)
            }

            buildSet {
                addAll(input.persistedEntities)
                addAll(input.removedEntities)
                addAll(input.processedEntities)
            }.let { allEntities ->
                uowInterceptors.forEach { it.postEntitiesPersisted(allEntities) }
                uowInterceptors.forEach { it.postInTransaction(input.persistedEntities, input.removedEntities) }
            }
        }

        uowInterceptors.forEach { it.afterTransaction(persistEntitySet, deleteEntitySet) }
    } finally {
        popProcessingEntities(currentProcessedEntitySet)
    }
}
```

Add the ID preparation and conflict helpers:

```kotlin
private fun prepareApplicationSideIds(changes: List<PendingChange>) {
    changes.forEach { change ->
        when (change.intent) {
            UnitOfWorkIntent.CREATE -> applicationSideIdSupport.assignMissingIds(change.entity)
            UnitOfWorkIntent.UPDATE -> applicationSideIdSupport.assignMissingIdsToOwnedRelations(change.entity)
            UnitOfWorkIntent.REMOVE -> Unit
        }
    }
}

private fun validateSameIdentityConflicts(changes: List<PendingChange>) {
    val identities = LinkedHashMap<EntityIdentity, PendingChange>()
    changes.forEach { change ->
        val identity = identityOf(change.entity) ?: return@forEach
        val previous = identities.putIfAbsent(identity, change)
        if (previous != null && previous.entity !== change.entity) {
            error(
                "conflicting UnitOfWork registrations for ${identity.entityType.name} id ${identity.id}: " +
                    "${previous.intent} and ${change.intent}"
            )
        }
    }
}

private fun identityOf(entity: Any): EntityIdentity? {
    applicationSideIdSupport.findApplicationSideId(entity)?.let { member ->
        if (applicationSideIdSupport.isDefaultId(member, entity)) return null
        val id = member.get(entity) ?: return null
        return EntityIdentity(member.ownerType, id)
    }

    val entityInformation = getEntityInformation(entity.javaClass)
    if (entityInformation.isNew(entity)) return null
    val id = entityInformation.getId(entity) ?: return null
    return EntityIdentity(entity.javaClass, id)
}
```

Add the JPA operation helpers:

```kotlin
private fun applyCreate(entity: Any, results: FlushResult) {
    validateCreateApplicationSideId(entity)
    if (!entityManager.contains(entity)) {
        entityManager.persist(entity)
    }
    if (applicationSideIdSupport.findApplicationSideId(entity) == null &&
        getEntityInformation(entity.javaClass).isNew(entity)
    ) {
        results.refreshList.add(entity)
    }
    results.created.add(entity)
    results.needsFlush = true
}

private fun applyUpdate(entity: Any, results: FlushResult) {
    validateUpdateRootIdentified(entity)
    if (!entityManager.contains(entity)) {
        entityManager.merge(entity)
    }
    results.updated.add(entity)
    results.needsFlush = true
}

private fun applyRemove(entity: Any, results: FlushResult) {
    when {
        entityManager.contains(entity) -> entityManager.remove(entity)
        else -> entityManager.merge(entity).also { merged ->
            entityManager.remove(merged)
        }
    }
    results.deleted.add(entity)
    results.needsFlush = true
}

private fun validateCreateApplicationSideId(entity: Any) {
    applicationSideIdSupport.findApplicationSideId(entity)?.let { member ->
        check(!applicationSideIdSupport.isDefaultId(member, entity)) {
            "Application-side ID remains default after assignment: " +
                "${member.ownerType.name}.${member.field.name}"
        }
    }
}

private fun validateUpdateRootIdentified(entity: Any) {
    applicationSideIdSupport.findApplicationSideId(entity)?.let { member ->
        check(!applicationSideIdSupport.isDefaultId(member, entity)) {
            "Update-intent application-side ID is default: ${member.ownerType.name}.${member.field.name}"
        }
        return
    }

    check(!getEntityInformation(entity.javaClass).isNew(entity)) {
        "Update-intent entity appears new: ${entity.javaClass.name}"
    }
}
```

Do not call `entityManager.find(...)` anywhere in this new operation selection path.

- [ ] **Step 5: Remove stale JPA scan and existence-query code**

Delete these old members from `JpaUnitOfWork.kt`:

```kotlin
private fun isExists(entity: Any): Boolean
protected open fun persistenceContextEntities(): List<Any>
```

Delete the commented persistence-context scan block in `save`.

Remove imports that become unused after deletion:

```kotlin
import org.hibernate.engine.spi.SessionImplementor
import org.slf4j.LoggerFactory
```

In `JpaUnitOfWorkTest.kt`, remove now-dead test-only scaffolding:

```kotlin
private val overridePersistenceContextEntities: Boolean = true
var persistenceContextEntityProvider: () -> List<Any> = { emptyList() }
override fun persistenceContextEntities(): List<Any>
fun testPersistenceContextEntities()
```

Delete tests that only covered the deleted scan helper:

```kotlin
testPersistenceContextEntitiesSessionClosed()
testPersistenceContextEntitiesException()
realPersistenceContextLookupUnitOfWork(...)
entityManagerProxy(...)
closedSessionImplementor()
```

Remove now-unused imports:

```kotlin
import org.hibernate.engine.spi.SessionImplementor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
```

- [ ] **Step 6: Run focused UoW tests**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Static check removed old semantics**

Run:

```powershell
rg -n "persistIfNotExist|persistenceContextEntities|entityManager\\.find\\(" ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt
```

Expected: no matches.

- [ ] **Step 8: Commit Task 4 in the worktree**

Run:

```powershell
git status --short
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt `
        ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt
git commit -m "feat: drive jpa uow save from explicit intent"
```

Expected: commit created on `codex/uow-persist-intent-phase1`.

---

---

### Task 5: Runtime Test Migration For Explicit Create Intent

**Files:**
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/ApplicationSideIdJpaRuntimeTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt`

**Interfaces:**
- Consumes: `PersistIntent.CREATE` for manually constructed new roots.
- Consumes: default `persist(entity)` as update intent for loaded existing roots.
- Produces: runtime evidence that application-side ID create paths still insert, and update paths can add children without replacing the root ID.

- [ ] **Step 1: Migrate ApplicationSideId runtime create calls**

In `ApplicationSideIdJpaRuntimeTest.kt`, add:

```kotlin
import com.only4.cap4k.ddd.core.application.PersistIntent
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
```

Add the transaction manager field:

```kotlin
@Autowired
private lateinit var transactionManager: PlatformTransactionManager
```

In `default uuid root and child ids are assigned before persist`, replace:

```kotlin
unitOfWork.persist(root)
```

with:

```kotlin
unitOfWork.persist(root, PersistIntent.CREATE)
```

In `preassigned uuid root id is preserved and inserted`, replace:

```kotlin
unitOfWork.persist(root)
```

with:

```kotlin
unitOfWork.persist(root, PersistIntent.CREATE)
```

Add a runtime update test:

```kotlin
@Test
fun `update intent assigns id to new owned child without replacing root id`() {
    val zeroUuid = UUID(0L, 0L)
    val root = RuntimeUuidRoot(name = "update-root")
    unitOfWork.persist(root, PersistIntent.CREATE)
    unitOfWork.save()
    val rootId = root.id
    JpaUnitOfWork.reset()

    TransactionTemplate(transactionManager).execute {
        val loaded = rootRepository.findById(rootId).orElseThrow()
        loaded.children.add(RuntimeUuidChild(name = "added-on-update"))
        unitOfWork.persist(loaded)
        unitOfWork.save()
        null
    }

    val childId = requireNotNull(
        jdbcTemplate.queryForObject(
            "select `id` from `runtime_uuid_child` where `name` = ?",
            UUID::class.java,
            "added-on-update",
        )
    )

    assertEquals(rootId, rootRepository.findById(rootId).orElseThrow().id)
    assertNotEquals(zeroUuid, childId)
    assertEquals(1, countRows("select count(*) from `runtime_uuid_child` where `root_id` = ?", rootId))
}
```

This test intentionally uses default `unitOfWork.persist(loaded)` after loading an existing aggregate; that is the update path.

- [ ] **Step 2: Migrate aggregate runtime create helpers**

In `AggregateJpaRuntimeDefectReproductionTest.kt`, add:

```kotlin
import com.only4.cap4k.ddd.core.application.PersistIntent
```

In `preassignedApplicationSideIdIsPreservedForNewRoot`, replace:

```kotlin
unitOfWork.persist(root)
```

with:

```kotlin
unitOfWork.persist(root, PersistIntent.CREATE)
```

In `readOnlyScalarFkCanCoexistWithReadOnlyInverseManyToOneOnTheSameColumn`, replace the new-root registration:

```kotlin
unitOfWork.persist(root)
```

with:

```kotlin
unitOfWork.persist(root, PersistIntent.CREATE)
```

Change the new-root helper methods:

```kotlin
private fun saveRoot(root: RuntimeRoot): RuntimeRoot {
    unitOfWork.persist(root, PersistIntent.CREATE)
    unitOfWork.save()
    return root
}

private fun saveReverseRoot(root: RuntimeReverseRoot): RuntimeReverseRoot {
    unitOfWork.persist(root, PersistIntent.CREATE)
    unitOfWork.save()
    return root
}

private fun saveSafeReverseRoot(root: RuntimeSafeReverseRoot): RuntimeSafeReverseRoot {
    unitOfWork.persist(root, PersistIntent.CREATE)
    unitOfWork.save()
    return root
}
```

Do not change loaded aggregate update calls such as:

```kotlin
unitOfWork.persist(loaded)
```

Those calls are now the intended default `PersistIntent.UPDATE` path.

- [ ] **Step 3: Run focused runtime tests**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.ApplicationSideIdJpaRuntimeTest" --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 4: Static scan runtime UoW call sites**

Run:

```powershell
rg -n "unitOfWork\\.persist\\(" cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime
```

Expected:

```text
ApplicationSideIdJpaRuntimeTest.kt: unitOfWork.persist(root, PersistIntent.CREATE) for manually created new roots
ApplicationSideIdJpaRuntimeTest.kt: unitOfWork.persist(loaded) only for loaded existing aggregate update
AggregateJpaRuntimeDefectReproductionTest.kt: unitOfWork.persist(root, PersistIntent.CREATE) in new-root helpers or direct new-root setup
AggregateJpaRuntimeDefectReproductionTest.kt: unitOfWork.persist(loaded) only in loaded existing aggregate update tests
```

- [ ] **Step 5: Commit Task 5 in the worktree**

Run:

```powershell
git status --short
git add cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/ApplicationSideIdJpaRuntimeTest.kt `
        cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt
git commit -m "test: migrate runtime create paths to explicit intent"
```

Expected: commit created on `codex/uow-persist-intent-phase1`.

---

---

### Task 6: Final Contract Cleanup And Verification

**Files:**
- Modify only files surfaced by the static checks in this task.
- Expected production files already covered by Tasks 1-5.
- Expected copied specs: `docs/superpowers/specs/2026-07-22-cap4k-identity-roadmap-design.md`
- Expected copied specs: `docs/superpowers/specs/2026-07-22-cap4k-uow-persist-intent-design.md`

**Interfaces:**
- Consumes: completed Tasks 1-5.
- Produces: final evidence that Phase 1 implementation matches the spec within the checked scope.

- [ ] **Step 1: Confirm no production `persistIfNotExist` remains**

Run:

```powershell
rg -n "persistIfNotExist" ddd-core/src/main ddd-domain-repo-jpa/src/main cap4k-ddd-starter/src/main
```

Expected: no matches.

- [ ] **Step 2: Confirm JpaUnitOfWork no longer uses existence-query operation selection**

Run:

```powershell
rg -n "isExists|entityManager\\.find\\(|persistenceContextEntities|reentrantSafeEntityEntries" ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt
```

Expected: no matches.

- [ ] **Step 3: Confirm no commented persistence-context scan remains**

Run:

```powershell
rg -n "//\\s*persistenceContextEntities|reentrantSafeEntityEntries|persistenceContext" ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt
```

Expected: no matches.

- [ ] **Step 4: Confirm active tests no longer call removed API**

Run:

```powershell
rg -n "persistIfNotExist" ddd-core/src/test ddd-domain-repo-jpa/src/test cap4k-ddd-starter/src/test
```

Expected: no matches.

- [ ] **Step 5: Run core and JPA module tests**

Run:

```powershell
.\gradlew.bat :ddd-core:test :ddd-domain-repo-jpa:test --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Run focused starter runtime tests**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.ApplicationSideIdJpaRuntimeTest" --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Confirm copied specs are tracked in the worktree and still present on master**

Run in the worktree:

```powershell
Test-Path 'docs/superpowers/specs/2026-07-22-cap4k-identity-roadmap-design.md'
Test-Path 'docs/superpowers/specs/2026-07-22-cap4k-uow-persist-intent-design.md'
git status --short docs/superpowers/specs
```

Expected: both `Test-Path` commands print `True`; `git status` lists the two copied spec files as tracked or staged/modified in this branch.

Run in the master checkout:

```powershell
git -C 'C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k' status --short docs/superpowers/specs
Test-Path 'C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\docs\superpowers\specs\2026-07-22-cap4k-identity-roadmap-design.md'
Test-Path 'C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\docs\superpowers\specs\2026-07-22-cap4k-uow-persist-intent-design.md'
```

Expected: master still has its two spec files; no implementation files changed on master.

- [ ] **Step 8: Review final diff**

Run:

```powershell
git diff --stat HEAD
git diff -- docs/superpowers/specs docs/superpowers/plans ddd-core ddd-domain-repo-jpa cap4k-ddd-starter
```

Expected: diff is limited to Phase 1 UoW intent implementation, tests, copied specs, and this plan.

- [ ] **Step 9: Commit final verification fixes if any**

If Steps 1-8 required cleanup edits, run:

```powershell
git status --short
git add docs/superpowers/specs docs/superpowers/plans ddd-core ddd-domain-repo-jpa cap4k-ddd-starter
git commit -m "test: verify uow persist intent phase one"
```

Expected: commit created on `codex/uow-persist-intent-phase1` only when there are verification cleanup edits. If there are no cleanup edits, do not create an empty commit.

---
