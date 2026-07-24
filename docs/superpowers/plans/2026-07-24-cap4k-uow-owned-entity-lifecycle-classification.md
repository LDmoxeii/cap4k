# cap4k UoW Owned Entity Lifecycle Classification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden JPA Unit of Work owned-child lifecycle handling so repository-observed owned children cannot be enrolled as standalone public UoW targets, while valid root-oriented aggregate persistence keeps working.

**Architecture:** Keep the public UoW API root-oriented and keep all JPA/provider-specific lifecycle evidence inside `ddd-domain-repo-jpa`. Extend repository observation baseline with root-versus-child membership facts, then use those facts in `JpaUnitOfWork` for direct child fail-fast and pending root/child duplicate preflight validation. Preserve existing generated Strong ID completion, `@ApplicationSideId` compatibility, repository read semantics, bounded owned traversal, and passive `OwnedEntityList` behavior.

**Tech Stack:** Kotlin, JUnit 5, MockK, Spring Data JPA, Hibernate, Gradle, PowerShell.

## Global Constraints

- `PersistIntent` public values remain exactly `CREATE` and `EXISTING`.
- `UnitOfWork.persist(entity)` remains equivalent to `persist(entity, PersistIntent.EXISTING)`.
- Repository handles aggregate read, access, and load; Unit of Work owns persistence intent, delete intent, commit, save, lifecycle interception, and listener coordination.
- `OwnedEntityList` remains a relation facade only; it must not call UoW, Repository, Mediator, EntityManager, ID generators, or identifier strategies.
- Do not introduce a new annotation or generated metadata marker solely to classify owned children.
- Do not implement a general direct-child classifier for arbitrary entity instances.
- Do not broaden generated owned traversal beyond initialized non-inverse `@OneToMany` fields with `@JoinColumn`, `CascadeType.PERSIST`, `CascadeType.MERGE`, and `orphanRemoval = true`.
- Do not add JPA or Hibernate dependencies to `ddd-core`.
- Do not modify generated entity templates to add child-classifier annotations.
- Do not modify generated Strong ID templates to add `@ApplicationSideId`.
- Keep `@ApplicationSideId` and `IdentifierStrategyRegistry` compatibility behavior intact.
- Do not claim child listener/audit publication unless child result surfaces are deliberately added and tested; this plan does not add that listener surface.
- Repository observation baseline is captured after repository materialization and load-plan application, before user mutation; current `persist=false` reads may observe after detach.
- If any required behavior needs a forbidden change, stop implementation and return to the spec with the smallest concrete blocker and file evidence.

---

## File Structure

No new production source files are required.

- Modify `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaRepositoryObservationBaseline.kt`
  - Responsibility: retain first repository observation and expose internal root/child membership facts for observed objects.
  - New internal API: `isObservedRoot(entity: Any): Boolean`, `isObservedChild(entity: Any): Boolean`, `observedRootFor(entity: Any): Any?`, `observedRootForChild(entity: Any): Any?`.

- Modify `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
  - Responsibility: reject invalid public child enrollment and reject pending root/child duplicate entries before transaction flush.
  - New private helpers: `validateStandaloneEnrollmentTarget(entity: Any, operation: String)` and `validatePendingOwnedChildConflicts(entries: List<UnitOfWorkEntry>)`.

- Modify `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`
  - Responsibility: focused unit coverage for repository observation membership, direct child fail-fast, pending duplicate fail-fast, and valid root-oriented owned child save behavior.

Files intentionally not modified by this plan:

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/PersistIntent.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/UnitOfWork.kt`
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityList.kt`
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedOwnedRelationTraversal.kt`
- `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt`
- Generated templates under `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/`

---

### Task 1: Expose Repository Observation Root/Child Membership

**Files:**
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaRepositoryObservationBaseline.kt`
- Test: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`

**Interfaces:**
- Consumes: existing `JpaRepositoryObservationBaseline.record(root: Any, entries: List<JpaObservedEntity>)` and `JpaUnitOfWork.observeRepositoryLoad(root: Any, loadPlan: AggregateLoadPlan)`.
- Produces:
  - `fun isObservedRoot(entity: Any): Boolean`
  - `fun isObservedChild(entity: Any): Boolean`
  - `fun observedRootFor(entity: Any): Any?`
  - `fun observedRootForChild(entity: Any): Any?`

- [ ] **Step 1: Write the failing membership test**

Add this test immediately after `repositoryObservationRecordsRootAndGeneratedOwnedChildren()` in `JpaUnitOfWorkTest.kt`:

```kotlin
    @Test
    @DisplayName("repository observation distinguishes root from generated owned child")
    fun repositoryObservationShouldDistinguishRootFromGeneratedOwnedChild() {
        val child = ObservedChild(20L)
        val root = ObservedRoot(10L, mutableListOf(child))
        every { mockEntityInfo.isNew(root) } returns false
        every { mockEntityInfo.getId(root) } returns 10L
        every { mockEntityInfo.isNew(child) } returns false
        every { mockEntityInfo.getId(child) } returns 20L

        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)

        val baseline = jpaUnitOfWork.observedRepositoryBaseline()
        assertTrue(baseline.isObservedRoot(root))
        assertFalse(baseline.isObservedChild(root))
        assertNull(baseline.observedRootForChild(root))
        assertTrue(baseline.isObservedChild(child))
        assertSame(root, baseline.observedRootForChild(child))
        assertSame(root, baseline.observedRootFor(child))
    }
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.repositoryObservationShouldDistinguishRootFromGeneratedOwnedChild"
```

Expected: FAIL at Kotlin compilation with unresolved references for `isObservedRoot`, `isObservedChild`, `observedRootForChild`, and `observedRootFor`.

- [ ] **Step 3: Add root/child membership state to the baseline**

In `JpaRepositoryObservationBaseline.kt`, add these fields next to the existing maps:

```kotlin
    private val observedRootKeyByObject = LinkedHashMap<ObjectIdentityKey, ObjectIdentityKey>()
    private val rootObjectByRootKey = LinkedHashMap<ObjectIdentityKey, Any>()
```

Update the existing-root branch inside `record(...)` so aliases of the same observed root remain root aliases and the first root object remains canonical:

```kotlin
        if (existingRootKey != null) {
            rootKeyByObservedObject[rootAliasKey] = existingRootKey
            rootKeyByObservedObject[canonicalRootKey] = existingRootKey
            observedRootKeyByObject[rootAliasKey] = existingRootKey
            observedRootKeyByObject[canonicalRootKey] = existingRootKey
            rootObjectByRootKey.putIfAbsent(
                existingRootKey,
                observedByRoot[existingRootKey]?.firstOrNull()?.entity ?: canonicalRoot,
            )
            observedByRoot[existingRootKey]
                ?.firstOrNull()
                ?.let { observedByObject.putIfAbsent(rootAliasKey, it) }
            return
        }
```

In the new-root branch inside `record(...)`, add root alias registration before iterating `entries`:

```kotlin
        observedByRoot[rootKey] = bucket
        rootKeyByObservedObject[rootAliasKey] = rootKey
        rootKeyByObservedObject[canonicalRootKey] = rootKey
        observedRootKeyByObject[rootAliasKey] = rootKey
        observedRootKeyByObject[canonicalRootKey] = rootKey
        rootObjectByRootKey.putIfAbsent(rootKey, canonicalRoot)
```

Add these methods after `identityFor(entity: Any)`:

```kotlin
    fun isObservedRoot(entity: Any): Boolean =
        observedRootKeyByObject.containsKey(ObjectIdentityKey(entity))

    fun isObservedChild(entity: Any): Boolean {
        val key = ObjectIdentityKey(entity)
        return rootKeyByObservedObject.containsKey(key) && !isObservedRoot(entity)
    }

    fun observedRootFor(entity: Any): Any? =
        rootKeyByObservedObject[ObjectIdentityKey(entity)]?.let(rootObjectByRootKey::get)

    fun observedRootForChild(entity: Any): Any? =
        if (isObservedChild(entity)) observedRootFor(entity) else null
```

Update `clear()`:

```kotlin
    fun clear() {
        observedByRoot.clear()
        rootKeyByObservedObject.clear()
        observedByObject.clear()
        observedIdentities.clear()
        observedRootKeyByObject.clear()
        rootObjectByRootKey.clear()
    }
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run:

```powershell
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.repositoryObservationShouldDistinguishRootFromGeneratedOwnedChild"
```

Expected: PASS.

- [ ] **Step 5: Run the existing baseline regression tests**

Run:

```powershell
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.repositoryObservationRecordsRootAndGeneratedOwnedChildren" --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.repeatedObservationShouldPreserveOriginalBaseline"
```

Expected: PASS.

- [ ] **Step 6: Commit Task 1**

Run:

```powershell
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaRepositoryObservationBaseline.kt ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt
git commit -m "feat: expose repository observation child membership"
```

---

### Task 2: Reject Repository-Observed Child Direct Enrollment

**Files:**
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- Test: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`

**Interfaces:**
- Consumes: `JpaRepositoryObservationBaseline.observedRootForChild(entity: Any): Any?` from Task 1.
- Produces: fail-fast validation for `JpaUnitOfWork.persist(entity, intent)` and `JpaUnitOfWork.remove(entity)` when `entity` is a repository-observed non-root owned child.

- [ ] **Step 1: Write failing direct persist and remove tests**

Add these tests after `repositoryObservationShouldDistinguishRootFromGeneratedOwnedChild()` in `JpaUnitOfWorkTest.kt`:

```kotlin
    @Test
    @DisplayName("persist rejects a repository-observed owned child as a standalone target")
    fun persistShouldRejectRepositoryObservedOwnedChild() {
        val child = ObservedChild(20L)
        val root = ObservedRoot(10L, mutableListOf(child))
        every { mockEntityInfo.isNew(root) } returns false
        every { mockEntityInfo.getId(root) } returns root.id
        every { mockEntityInfo.isNew(child) } returns false
        every { mockEntityInfo.getId(child) } returns child.id
        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)

        val error = assertThrows(IllegalStateException::class.java) {
            jpaUnitOfWork.persist(child)
        }

        assertTrue(error.message!!.contains("persist the aggregate root"))
        assertTrue(error.message!!.contains(ObservedRoot::class.java.name))
        assertTrue(error.message!!.contains(ObservedChild::class.java.name))
        verify(exactly = 0) { entityManager.persist(any()) }
        verify(exactly = 0) { entityManager.merge<Any>(any()) }
        verify(exactly = 0) { entityManager.flush() }
    }

    @Test
    @DisplayName("remove rejects a repository-observed owned child as a standalone target")
    fun removeShouldRejectRepositoryObservedOwnedChild() {
        val child = ObservedChild(20L)
        val root = ObservedRoot(10L, mutableListOf(child))
        every { mockEntityInfo.isNew(root) } returns false
        every { mockEntityInfo.getId(root) } returns root.id
        every { mockEntityInfo.isNew(child) } returns false
        every { mockEntityInfo.getId(child) } returns child.id
        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)

        val error = assertThrows(IllegalStateException::class.java) {
            jpaUnitOfWork.remove(child)
        }

        assertTrue(error.message!!.contains("persist the aggregate root"))
        assertTrue(error.message!!.contains(ObservedRoot::class.java.name))
        assertTrue(error.message!!.contains(ObservedChild::class.java.name))
        verify(exactly = 0) { entityManager.remove(any()) }
        verify(exactly = 0) { entityManager.merge<Any>(any()) }
        verify(exactly = 0) { entityManager.flush() }
    }
```

- [ ] **Step 2: Run the new tests and verify they fail**

Run:

```powershell
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.persistShouldRejectRepositoryObservedOwnedChild" --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.removeShouldRejectRepositoryObservedOwnedChild"
```

Expected: FAIL because the current `persist(child)` and `remove(child)` paths do not throw the required direct-child boundary error.

- [ ] **Step 3: Add standalone enrollment validation**

In `JpaUnitOfWork.kt`, update `persist(...)` and `remove(...)`:

```kotlin
    override fun persist(entity: Any, intent: PersistIntent) {
        validateStandaloneEnrollmentTarget(entity, "persist")
        val entry = pendingEntriesThreadLocal.get().persist(entity, intent)
        completeIdsForEntry(entry)
    }

    override fun remove(entity: Any) {
        validateStandaloneEnrollmentTarget(entity, "remove")
        pendingEntriesThreadLocal.get().remove(entity)
    }
```

Add this private helper near the other validation helpers:

```kotlin
    private fun validateStandaloneEnrollmentTarget(entity: Any, operation: String) {
        val observedRoot = repositoryObservationBaseline.observedRootForChild(entity) ?: return
        error(
            "UnitOfWork.$operation cannot register generated owned child " +
                "${persistentEntityClass(entity).name} as a standalone target; " +
                "persist the aggregate root ${persistentEntityClass(observedRoot).name} instead"
        )
    }
```

- [ ] **Step 4: Run the new tests and verify they pass**

Run:

```powershell
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.persistShouldRejectRepositoryObservedOwnedChild" --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.removeShouldRejectRepositoryObservedOwnedChild"
```

Expected: PASS.

- [ ] **Step 5: Verify valid observed root enrollment still works**

Run:

```powershell
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.defaultPersistShouldEnrollObservedDetachedExistingEntity" --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.existingPersistShouldFillNewOwnedChildStrongIdWithoutReplacingRootId"
```

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

Run:

```powershell
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt
git commit -m "feat: reject observed owned child uow enrollment"
```

---

### Task 3: Reject Pending Root Graph Plus Separate Child Entry Before Flush

**Files:**
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- Test: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`

**Interfaces:**
- Consumes: `JpaGeneratedOwnedRelationTraversal.reachableOwnedEntities(root: Any): List<Any>` and current pending `UnitOfWorkEntry` list.
- Produces: `private fun validatePendingOwnedChildConflicts(entries: List<UnitOfWorkEntry>)` called during `save(...)` before transaction flush.

- [ ] **Step 1: Write the failing duplicate-entry test**

Add this test near the Strong ID owned-child tests in `JpaUnitOfWorkTest.kt`:

```kotlin
    @Test
    @DisplayName("save rejects a pending owned child that is also reachable from a pending root")
    fun saveShouldRejectPendingOwnedChildReachableFromPendingRoot() {
        val root = StrongRootEntity()
        val child = StrongChildEntity()
        root.children += child

        jpaUnitOfWork.persist(root, PersistIntent.CREATE)
        jpaUnitOfWork.persist(child, PersistIntent.CREATE)

        val error = assertThrows(IllegalStateException::class.java) {
            jpaUnitOfWork.save()
        }

        assertTrue(error.message!!.contains("separate public UnitOfWork target"))
        assertTrue(error.message!!.contains("persist the aggregate root"))
        assertTrue(error.message!!.contains(StrongRootEntity::class.java.name))
        assertTrue(error.message!!.contains(StrongChildEntity::class.java.name))
        verify(exactly = 0) { entityManager.persist(any()) }
        verify(exactly = 0) { entityManager.flush() }
    }
```

- [ ] **Step 2: Write the valid root-only regression test**

Add this test after the duplicate-entry test:

```kotlin
    @Test
    @DisplayName("root-only existing enrollment with a new owned child remains valid")
    fun existingRootOnlyEnrollmentWithNewOwnedChildShouldRemainValid() {
        val root = StrongRootEntity().also {
            it.id = TestStrongEntityId("018f0000-0000-7000-8000-000000000088")
        }
        val observedChild = StrongChildEntity().also {
            it.id = TestStrongEntityId("018f0000-0000-7000-8000-000000000087")
        }
        root.children += observedChild
        every { mockEntityInfo.isNew(root) } returns false
        every { mockEntityInfo.getId(root) } returns root.id
        every { mockEntityInfo.isNew(observedChild) } returns false
        every { mockEntityInfo.getId(observedChild) } returns observedChild.id
        jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)

        val newChild = StrongChildEntity()
        root.children += newChild
        jpaUnitOfWork.persist(root)
        jpaUnitOfWork.save()

        assertEquals("018f0000-0000-7000-8000-000000000001", newChild.id.value)
        verify { entityManager.merge(root) }
        verify { entityManager.flush() }
        verify(exactly = 0) { entityManager.persist(newChild) }
    }
```

- [ ] **Step 3: Run the new tests and verify the duplicate test fails**

Run:

```powershell
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.saveShouldRejectPendingOwnedChildReachableFromPendingRoot" --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.existingRootOnlyEnrollmentWithNewOwnedChildShouldRemainValid"
```

Expected: FAIL because the duplicate pending child entry is currently processed as a standalone public target instead of being rejected before flush. The root-only regression may pass before implementation and must pass after implementation.

- [ ] **Step 4: Add pending root/child duplicate validation**

In `JpaUnitOfWork.save(...)`, call the new validator after same-identity conflict validation and before UoW interceptors:

```kotlin
            prepareApplicationSideIds(pendingEntries)
            validateSameIdentityConflicts(pendingEntries)
            validatePendingOwnedChildConflicts(pendingEntries)
            uowInterceptors.forEach { it.beforeTransaction(persistEntitySet, deleteEntitySet) }
```

Add this helper near `validateSameIdentityConflicts(...)`:

```kotlin
    private fun validatePendingOwnedChildConflicts(entries: List<UnitOfWorkEntry>) {
        val rootEntries = entries.filter {
            it.kind == UnitOfWorkEntryKind.CREATE || it.kind == UnitOfWorkEntryKind.EXISTING
        }
        if (rootEntries.isEmpty() || entries.size < 2) return

        rootEntries.forEach { rootEntry ->
            val reachable = ownedRelationTraversal.reachableOwnedEntities(rootEntry.entity)
            val traversalRoot = reachable.firstOrNull() ?: return@forEach
            reachable.asSequence()
                .filterNot { it === traversalRoot }
                .forEach { child ->
                    if (entries.any { it.entity === child }) {
                        error(
                            "UnitOfWork cannot register generated owned child " +
                                "${persistentEntityClass(child).name} as a separate public UnitOfWork target " +
                                "while aggregate root ${persistentEntityClass(rootEntry.entity).name} is pending; " +
                                "persist the aggregate root only"
                        )
                    }
                }
        }
    }
```

- [ ] **Step 5: Run the new tests and verify they pass**

Run:

```powershell
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.saveShouldRejectPendingOwnedChildReachableFromPendingRoot" --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.existingRootOnlyEnrollmentWithNewOwnedChildShouldRemainValid"
```

Expected: PASS.

- [ ] **Step 6: Run existing UoW identity and listener regressions**

Run:

```powershell
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.differentInstancesWithSameIdentityShouldFailBeforeFlush" --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.cleanExistingEntityShouldNotEmitUpdateListener" --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.dirtyExistingEntityShouldEmitUpdateListener"
```

Expected: PASS.

- [ ] **Step 7: Commit Task 3**

Run:

```powershell
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt
git commit -m "feat: reject pending root owned child duplicates"
```

---

### Task 4: Focused Verification And Static Boundary Audit

**Files:**
- Review only: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaRepositoryObservationBaseline.kt`
- Review only: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- Review only: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedOwnedRelationTraversal.kt`
- Review only: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityList.kt`
- Review only: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Review only: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb`

**Interfaces:**
- Consumes: completed Tasks 1-3.
- Produces: evidence that the implementation stayed within the spec boundary and did not regress existing runtime behavior.

- [ ] **Step 1: Run the focused JPA UoW test class**

Run:

```powershell
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest"
```

Expected: PASS.

- [ ] **Step 2: Run repository supervisor focused tests**

Run:

```powershell
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.domain.repo.impl.DefaultRepositorySupervisorTest"
```

Expected: PASS, including default non-persistent reads observing baseline and `persist=true` reads enrolling `PersistIntent.EXISTING`.

- [ ] **Step 3: Run owned relation facade and runtime regressions**

Run:

```powershell
./gradlew :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityListTest"
./gradlew :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.OwnedEntityListJpaRuntimeTest"
```

Expected: PASS. `OwnedEntityList` remains passive and Hibernate still persists, reloads, and orphan-removes through private backing collections.

- [ ] **Step 4: Run generated Strong ID runtime regressions**

Run:

```powershell
./gradlew :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdUowRuntimeTest"
```

Expected: PASS, including clean existing enrollment, dirty existing listener behavior, and generated Strong ID owned-child completion.

- [ ] **Step 5: Run static boundary scans**

Run:

```powershell
rg -n "AggregateElement|OwnedChild|OwnedEntityMarker|AggregateChild" ddd-core ddd-domain-repo-jpa cap4k-plugin-pipeline-renderer-pebble
rg -n "jakarta.persistence|org.hibernate" ddd-core/src/main/kotlin
rg -n "UnitOfWork|Repository|Mediator|EntityManager|IdentifierStrategyRegistry|IdentifierGenerator" ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityList.kt
rg -n "@ApplicationSideId" cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb
```

Expected:

```text
No matches for AggregateElement|OwnedChild|OwnedEntityMarker|AggregateChild.
No matches for jakarta.persistence|org.hibernate under ddd-core/src/main/kotlin.
No matches for UnitOfWork|Repository|Mediator|EntityManager|IdentifierStrategyRegistry|IdentifierGenerator in OwnedEntityList.kt.
No matches for @ApplicationSideId in generated aggregate entity or strong_id templates.
```

- [ ] **Step 6: Confirm traversal scope did not broaden**

Run:

```powershell
rg -n "ManyToOne|ManyToMany|OneToOne|mappedBy|JoinColumn|CascadeType\.PERSIST|CascadeType\.MERGE|orphanRemoval|Hibernate\.isInitialized" ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedOwnedRelationTraversal.kt
```

Expected: output shows only the existing bounded `@OneToMany` traversal criteria: non-inverse `mappedBy`, `@JoinColumn`, `CascadeType.PERSIST`, `CascadeType.MERGE`, `orphanRemoval`, and `Hibernate.isInitialized`; no new traversal of arbitrary `ManyToOne`, `ManyToMany`, or `OneToOne` relations.

- [ ] **Step 7: Inspect final diff for forbidden files and listener overclaim**

Run:

```powershell
git diff -- ddd-core ddd-domain-repo-jpa cap4k-plugin-pipeline-renderer-pebble cap4k-ddd-starter
rg -n "child audit|child listener|owned child listener|owned child audit|fully supported" ddd-domain-repo-jpa/src/main/kotlin ddd-domain-repo-jpa/src/test/kotlin cap4k-ddd-starter/src/test/kotlin
```

Expected: diff is limited to `JpaRepositoryObservationBaseline.kt`, `JpaUnitOfWork.kt`, and focused tests unless a documented runtime test addition was made. Listener/audit scan does not show new claims that child listener result surfaces are fully supported.

---

## Self-Review Notes

- Spec coverage: Task 1 covers root versus non-root observation membership and first-baseline preservation support. Task 2 covers repository-observed direct child `persist` and `remove` fail-fast. Task 3 covers pending root graph plus separate child entry fail-fast and verifies valid root-only new child persistence still works. Task 4 covers focused tests and static constraints from the spec.
- Type consistency: All new APIs are internal Kotlin methods on `JpaRepositoryObservationBaseline` and are consumed only by `JpaUnitOfWork` and same-package tests.
- Boundary consistency: No public `PersistIntent`, `UnitOfWork`, `OwnedEntityList`, generator template, repository API, traversal broadening, or `@ApplicationSideId` compatibility change is planned.
- Verification claim strength: Completion may claim focused local verification only after the listed Gradle commands and static scans actually run and pass.