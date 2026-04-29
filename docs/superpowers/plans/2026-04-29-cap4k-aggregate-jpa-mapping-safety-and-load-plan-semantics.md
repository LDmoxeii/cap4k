# Cap4k Aggregate JPA Mapping Safety And Load-Plan Semantics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove unsafe generated aggregate refresh cascades and add an explicit aggregate load-plan path through the existing mediator/supervisor APIs without implementing command-wide transaction expansion.

**Architecture:** The generator must stop emitting `CascadeType.ALL` for owned aggregate collections and instead emit explicit persistence cascades that exclude `REFRESH`. The runtime API must carry a small `AggregateLoadPlan` enum through `AggregateSupervisor`, `RepositorySupervisor`, `Repository`, and JPA repository implementations. JPA `WHOLE_AGGREGATE` loading should initialize owned `@OneToMany` graphs below the repository boundary so use cases can request whole aggregate loading without making entity mappings globally eager or bypassing the mediator.

**Tech Stack:** Kotlin, Gradle, Spring Data JPA, Hibernate ORM, Pebble templates, JUnit 5, MockK, H2 runtime fixture.

---

## Scope

Implement this slice only:

- Generated aggregate `ONE_TO_MANY` relations use explicit cascades: `PERSIST`, `MERGE`, `REMOVE`.
- Generated aggregate `ONE_TO_MANY` relations no longer use `CascadeType.ALL`.
- Existing inverse/read-only parent navigation remains generated, but this slice does not make it eager by default.
- Add `AggregateLoadPlan.DEFAULT`, `AggregateLoadPlan.MINIMAL`, and `AggregateLoadPlan.WHOLE_AGGREGATE`.
- Propagate `AggregateLoadPlan` through mediator/supervisor/repository calls.
- Implement JPA `WHOLE_AGGREGATE` as owned-collection initialization inside `AbstractJpaRepository`.
- Keep `persist=true` as "register the loaded entity into `UnitOfWork`"; do not make it imply load depth or transaction scope.

Explicitly out of scope:

- Do not implement command-wide transaction expansion.
- Do not redesign `JpaUnitOfWork.save()` commit/after-transaction semantics.
- Do not add named load plans.
- Do not bypass `AggregateSupervisor` / `RepositorySupervisor` with public JPA-specific APIs.
- Do not replace the JPA backend.

## File Structure

Generator-side files:

- Modify `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
  - Add `AggregateCascadeType`.
  - Replace `AggregateRelationModel.cascadeAll` with `cascadeTypes`.
- Modify `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt`
  - Parent-child relations should emit explicit cascade types.
- Modify `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt`
  - Render model should expose `cascadeTypes`, not `cascadeAll`.
- Modify `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
  - Render `cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE]`.
- Modify aggregate planner/core tests that currently assert `cascadeAll`.

Runtime-side files:

- Create `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/AggregateLoadPlan.kt`
  - Define the public load-plan enum.
- Modify `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/Repository.kt`
  - Add `loadPlan` parameters to read methods.
- Modify `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/RepositorySupervisor.kt`
  - Add `loadPlan` parameters to read methods.
- Modify `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregateSupervisor.kt`
  - Add `loadPlan` parameters to aggregate read methods.
- Modify `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt`
  - Forward load plans through mediator aggregate methods.
- Modify `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt`
  - Forward load plans to concrete repositories.
- Modify `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/AbstractJpaRepository.kt`
  - Apply JPA whole-aggregate initialization.
- Modify `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/aggregate/impl/DefaultAggregateSupervisor.kt`
  - Forward load plans from aggregate supervisor to repository supervisor.
- Modify unit tests in `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt`.
- Modify unit tests in `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/aggregate/impl/DefaultAggregateSupervisorTest.kt`.
- Modify runtime fixture `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt`.

## Task 1: Replace Generator Cascade Boolean With Explicit Cascade Types

**Files:**

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Update the aggregate planner test first**

In `AggregateArtifactPlannerTest`, update the relation-side control test that currently asserts `cascadeAll`.

Expected assertion shape:

```kotlin
val items = relationFields.single { it["name"] == "items" }

assertEquals("ONE_TO_MANY", items["relationType"])
assertEquals(
    listOf("PERSIST", "MERGE", "REMOVE"),
    items["cascadeTypes"]
)
assertEquals(true, items["orphanRemoval"])
assertFalse(items.containsKey("cascadeAll"))
```

Also update the direct-relation test that currently checks `cascadeAll` exists for `MANY_TO_ONE` and `ONE_TO_ONE`. The new expectation is that direct relations expose an empty cascade list:

```kotlin
val author = relationFields.single { it["name"] == "author" }
val coverProfile = relationFields.single { it["name"] == "coverProfile" }

assertEquals(emptyList<String>(), author["cascadeTypes"])
assertEquals(emptyList<String>(), coverProfile["cascadeTypes"])
assertFalse(author.containsKey("cascadeAll"))
assertFalse(coverProfile.containsKey("cascadeAll"))
```

In `PipelinePluginCompileFunctionalTest.kt`, update `aggregate relation generation participates in domain compileKotlin` so generated root entity assertion changes from:

```kotlin
assertTrue(
    generatedRootEntity.contains(
        "@OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)"
    )
)
```

to:

```kotlin
assertTrue(
    generatedRootEntity.contains(
        "@OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE], orphanRemoval = true)"
    )
)
assertFalse(generatedRootEntity.contains("CascadeType.ALL"))
```

- [ ] **Step 2: Run the failing aggregate planner tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

Expected: fail because `cascadeTypes` is not yet in the render model and `cascadeAll` is still present.

- [ ] **Step 3: Add `AggregateCascadeType` and update `AggregateRelationModel`**

In `PipelineModels.kt`, add this enum next to `AggregateFetchType`:

```kotlin
enum class AggregateCascadeType {
    PERSIST,
    MERGE,
    REMOVE,
}
```

Change `AggregateRelationModel` from:

```kotlin
val cascadeAll: Boolean = false,
```

to:

```kotlin
val cascadeTypes: List<AggregateCascadeType> = emptyList(),
```

- [ ] **Step 4: Update relation inference to emit explicit persistence cascades**

In `AggregateRelationInference.kt`, import `AggregateCascadeType` and change parent-child `ONE_TO_MANY` relation construction from:

```kotlin
cascadeAll = true,
```

to:

```kotlin
cascadeTypes = listOf(
    AggregateCascadeType.PERSIST,
    AggregateCascadeType.MERGE,
    AggregateCascadeType.REMOVE,
),
```

Do not add `REFRESH`.

- [ ] **Step 5: Update relation planning render model**

In `AggregateRelationPlanning.kt`, replace owner relation map entry:

```kotlin
"cascadeAll" to relation.cascadeAll,
```

with:

```kotlin
"cascadeTypes" to relation.cascadeTypes.map { it.name },
```

Replace inverse relation entry:

```kotlin
"cascadeAll" to false,
```

with:

```kotlin
"cascadeTypes" to emptyList<String>(),
```

Replace import detection:

```kotlin
val hasCascadeAll = entityRelations.any { it.cascadeAll }
```

with:

```kotlin
val hasCascadeTypes = entityRelations.any { it.cascadeTypes.isNotEmpty() }
```

and use `hasCascadeTypes` when adding `jakarta.persistence.CascadeType`.

- [ ] **Step 6: Update Pebble template cascade rendering**

In `aggregate/entity.kt.peb`, replace:

```peb
@OneToMany(fetch = FetchType.{{ relation.fetchType }}{% if relation.cascadeAll %}, cascade = [CascadeType.ALL]{% endif %}, orphanRemoval = {{ relation.orphanRemoval }})
```

with:

```peb
@OneToMany(fetch = FetchType.{{ relation.fetchType }}{% if relation.cascadeTypes|length > 0 %}, cascade = [{% for cascadeType in relation.cascadeTypes %}CascadeType.{{ cascadeType }}{% if not loop.last %}, {% endif %}{% endfor %}]{% endif %}, orphanRemoval = {{ relation.orphanRemoval }})
```

- [ ] **Step 7: Update remaining tests and compile errors**

Search:

```powershell
rg -n "cascadeAll|CascadeType.ALL" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-renderer-pebble
```

Expected after edits:

- no production usage of `cascadeAll`
- no generated aggregate template usage of `CascadeType.ALL`
- old docs/spec references may remain only if they describe historical behavior

- [ ] **Step 8: Run generator-side verification**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test
```

Expected: all selected tasks pass.

- [ ] **Step 9: Commit generator cascade safety**

Run:

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt `
        cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt `
        cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt `
        cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb `
        cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt `
        cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "fix: remove refresh from generated aggregate cascades"
```

## Task 2: Prove Safe Cascades Avoid The Nested Inverse Refresh Defect

**Files:**

- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt`

- [ ] **Step 1: Add safe-cascade runtime entities**

In `AggregateJpaRuntimeDefectReproductionTest.kt`, keep the existing `RuntimeReverseRoot`, `RuntimeReverseChild`, and `RuntimeReverseGrandchild` classes unchanged because they preserve the known-defect fixture.

Add a second safe-cascade fixture next to those classes:

```kotlin
@Entity
@Table(name = "`runtime_safe_reverse_root`")
open class RuntimeSafeReverseRoot(id: Long = 0L, name: String = "") {
    @OneToMany(
        cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE],
        fetch = FetchType.EAGER,
        orphanRemoval = true
    )
    @JoinColumn(name = "`root_id`", nullable = false)
    open var children: MutableList<RuntimeSafeReverseChild> = mutableListOf()

    @Id
    @GeneratedValue(generator = SNOWFLAKE_GENERATOR)
    @GenericGenerator(name = SNOWFLAKE_GENERATOR, strategy = SNOWFLAKE_GENERATOR)
    @Column(name = "`id`", insertable = false, updatable = false)
    open var id: Long = id
        protected set

    @Column(name = "`name`", nullable = false)
    open var name: String = name
}

@Entity
@Table(name = "`runtime_safe_reverse_child`")
open class RuntimeSafeReverseChild(id: Long = 0L, name: String = "") {
    @ManyToOne(cascade = [], fetch = FetchType.EAGER)
    @JoinColumn(name = "`root_id`", nullable = false, insertable = false, updatable = false)
    open var root: RuntimeSafeReverseRoot? = null

    @OneToMany(
        cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE],
        fetch = FetchType.EAGER,
        orphanRemoval = true
    )
    @JoinColumn(name = "`child_id`", nullable = false)
    open var grandchildren: MutableList<RuntimeSafeReverseGrandchild> = mutableListOf()

    @Id
    @GeneratedValue(generator = SNOWFLAKE_GENERATOR)
    @GenericGenerator(name = SNOWFLAKE_GENERATOR, strategy = SNOWFLAKE_GENERATOR)
    @Column(name = "`id`", insertable = false, updatable = false)
    open var id: Long = id
        protected set

    @Column(name = "`name`", nullable = false)
    open var name: String = name
}

@Entity
@Table(name = "`runtime_safe_reverse_grandchild`")
open class RuntimeSafeReverseGrandchild(id: Long = 0L, name: String = "") {
    @ManyToOne(cascade = [], fetch = FetchType.EAGER)
    @JoinColumn(name = "`child_id`", nullable = false, insertable = false, updatable = false)
    open var child: RuntimeSafeReverseChild? = null

    @Id
    @GeneratedValue(generator = SNOWFLAKE_GENERATOR)
    @GenericGenerator(name = SNOWFLAKE_GENERATOR, strategy = SNOWFLAKE_GENERATOR)
    @Column(name = "`id`", insertable = false, updatable = false)
    open var id: Long = id
        protected set

    @Column(name = "`name`", nullable = false)
    open var name: String = name
}
```

- [ ] **Step 2: Add cleanup rows for safe-cascade tables**

In `cleanDatabase()`, delete safe-cascade rows before existing reverse rows:

```kotlin
jdbcTemplate.update("delete from `runtime_safe_reverse_grandchild`")
jdbcTemplate.update("delete from `runtime_safe_reverse_child`")
jdbcTemplate.update("delete from `runtime_safe_reverse_root`")
```

- [ ] **Step 3: Add helper methods for the safe-cascade graph**

Add helpers next to `saveReverseRoot()` and `newThreeLevelReverseRoot()`:

```kotlin
private fun saveSafeReverseRoot(root: RuntimeSafeReverseRoot): RuntimeSafeReverseRoot {
    unitOfWork.persist(root)
    unitOfWork.save()
    return root
}

private fun newThreeLevelSafeReverseRoot(name: String): RuntimeSafeReverseRoot =
    RuntimeSafeReverseRoot(name = name).apply {
        children.add(RuntimeSafeReverseChild(name = "$name-child-a").apply {
            grandchildren.add(RuntimeSafeReverseGrandchild(name = "$name-grandchild-a1"))
            grandchildren.add(RuntimeSafeReverseGrandchild(name = "$name-grandchild-a2"))
        })
        children.add(RuntimeSafeReverseChild(name = "$name-child-b").apply {
            grandchildren.add(RuntimeSafeReverseGrandchild(name = "$name-grandchild-b1"))
            grandchildren.add(RuntimeSafeReverseGrandchild(name = "$name-grandchild-b2"))
        })
    }
```

- [ ] **Step 4: Add a runtime test proving no-refresh cascades support nested inverse navigation**

Add this test after `reverseEagerNavigationOnNestedEntitiesIsKnownDefect()`:

```kotlin
@Test
@DisplayName("safe cascades support nested inverse eager navigation")
fun safeCascadesSupportNestedInverseEagerNavigation() {
    val classification = classifyRuntimeBehavior(
        label = "safe cascade nested reverse eager navigation",
        desiredContract = {
            val root = saveSafeReverseRoot(newThreeLevelSafeReverseRoot("safe-reverse-eager"))
            assertNotEquals(0L, root.id)
            assertEquals(1, countRows("select count(*) from `runtime_safe_reverse_root`"))
            assertEquals(2, countRows("select count(*) from `runtime_safe_reverse_child`"))
            assertEquals(4, countRows("select count(*) from `runtime_safe_reverse_grandchild`"))
        },
        knownDefect = { failure ->
            failure.hasCause<jakarta.persistence.PersistenceException>() ||
                failure.hasCause<HibernateException>() ||
                failure is AssertionError
        }
    )

    assertSupported(classification)
}
```

- [ ] **Step 5: Run the runtime fixture**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest"
```

Expected:

- existing `reverseEagerNavigationOnNestedEntitiesIsKnownDefect` still passes as a known-defect classification
- new `safeCascadesSupportNestedInverseEagerNavigation` passes as supported

- [ ] **Step 6: Commit runtime cascade proof**

Run:

```powershell
git add cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt
git commit -m "test: prove aggregate cascades work without refresh"
```

## Task 3: Add AggregateLoadPlan To Public Repository And Aggregate APIs

**Files:**

- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/AggregateLoadPlan.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/Repository.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/RepositorySupervisor.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregateSupervisor.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt`

- [ ] **Step 1: Add the public load-plan enum**

Create `AggregateLoadPlan.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.repo

/**
 * Describes how much of an aggregate graph a repository read should prepare.
 */
enum class AggregateLoadPlan {
    /**
     * Existing repository behavior.
     */
    DEFAULT,

    /**
     * Root-only read intent. Implementations must not force owned collections to load.
     */
    MINIMAL,

    /**
     * Command mutation intent. Implementations should prepare owned aggregate entities.
     */
    WHOLE_AGGREGATE,
}
```

- [ ] **Step 2: Update `Repository` read method signatures**

In `Repository.kt`, add `loadPlan: AggregateLoadPlan = AggregateLoadPlan.DEFAULT` to all read methods that currently accept `persist`:

```kotlin
fun find(
    predicate: Predicate<ENTITY>,
    orders: Collection<OrderInfo> = emptyList(),
    persist: Boolean = true,
    loadPlan: AggregateLoadPlan = AggregateLoadPlan.DEFAULT
): List<ENTITY>

fun find(
    predicate: Predicate<ENTITY>,
    pageParam: PageParam,
    persist: Boolean = true,
    loadPlan: AggregateLoadPlan = AggregateLoadPlan.DEFAULT
): List<ENTITY>

fun findOne(
    predicate: Predicate<ENTITY>,
    persist: Boolean = true,
    loadPlan: AggregateLoadPlan = AggregateLoadPlan.DEFAULT
): ENTITY?

fun findFirst(
    predicate: Predicate<ENTITY>,
    orders: Collection<OrderInfo> = emptyList(),
    persist: Boolean = true,
    loadPlan: AggregateLoadPlan = AggregateLoadPlan.DEFAULT
): ENTITY?

fun findPage(
    predicate: Predicate<ENTITY>,
    pageParam: PageParam,
    persist: Boolean = true,
    loadPlan: AggregateLoadPlan = AggregateLoadPlan.DEFAULT
): PageData<ENTITY>
```

Leave `count()` and `exists()` unchanged.

- [ ] **Step 3: Update `RepositorySupervisor` signatures**

In `RepositorySupervisor.kt`, mirror the same `loadPlan` parameter on:

```kotlin
find(predicate, orders, persist, loadPlan)
find(predicate, pageParam, persist, loadPlan)
findOne(predicate, persist, loadPlan)
findFirst(predicate, orders, persist, loadPlan)
findPage(predicate, pageParam, persist, loadPlan)
```

The `vararg orders` convenience methods should continue calling the collection overload with the default load plan.

- [ ] **Step 4: Update `AggregateSupervisor` signatures**

In `AggregateSupervisor.kt`, import `AggregateLoadPlan` and add `loadPlan` to aggregate read methods:

```kotlin
fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> getById(
    id: Id<AGGREGATE, *>,
    persist: Boolean = true,
    loadPlan: AggregateLoadPlan = AggregateLoadPlan.DEFAULT
): AGGREGATE? =
    getByIds(listOf(id), persist, loadPlan).firstOrNull()

fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> getByIds(
    ids: Iterable<Id<AGGREGATE, *>>,
    persist: Boolean = true,
    loadPlan: AggregateLoadPlan = AggregateLoadPlan.DEFAULT
): List<AGGREGATE>

fun <AGGREGATE : Aggregate<*>> find(
    predicate: AggregatePredicate<AGGREGATE, *>,
    orders: Collection<OrderInfo> = emptyList(),
    persist: Boolean = true,
    loadPlan: AggregateLoadPlan = AggregateLoadPlan.DEFAULT
): List<AGGREGATE>

fun <AGGREGATE : Aggregate<*>> findOne(
    predicate: AggregatePredicate<AGGREGATE, *>,
    persist: Boolean = true,
    loadPlan: AggregateLoadPlan = AggregateLoadPlan.DEFAULT
): AGGREGATE?
```

Apply the same pattern to `find(pageParam)`, `findFirst`, and `findPage`.

- [ ] **Step 5: Update `DefaultMediator` forwarding**

In `DefaultMediator.kt`, add `loadPlan` to aggregate read methods and forward it:

```kotlin
override fun <AGGREGATE : Aggregate<*>> findOne(
    predicate: AggregatePredicate<AGGREGATE, *>,
    persist: Boolean,
    loadPlan: AggregateLoadPlan
): AGGREGATE? =
    AggregateSupervisor.instance.findOne(predicate, persist, loadPlan)
```

Apply the same pattern for `getByIds`, `find`, `findFirst`, and `findPage`.

- [ ] **Step 6: Run core compile to expose implementation classes that must be updated**

Run:

```powershell
.\gradlew.bat :ddd-core:compileKotlin
```

Expected: `ddd-core` compiles or exposes only missing imports/signature mismatches in `DefaultMediator`.

## Task 4: Propagate AggregateLoadPlan Through JPA Supervisors

**Files:**

- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/aggregate/impl/DefaultAggregateSupervisor.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/aggregate/impl/DefaultAggregateSupervisorTest.kt`

- [ ] **Step 1: Write repository supervisor pass-through tests**

In `DefaultRepositorySupervisorTest.kt`, import:

```kotlin
import com.only4.cap4k.ddd.core.domain.repo.AggregateLoadPlan
```

Add this test near existing `findOne with persist` coverage:

```kotlin
@Test
@DisplayName("findOne should pass aggregate load plan to repository")
fun `findOne should pass aggregate load plan to repository`() {
    val predicate = TestPredicate()
    val expectedEntity = TestEntity(1L, "test")

    every {
        mockRepository.findOne(predicate, true, AggregateLoadPlan.WHOLE_AGGREGATE)
    } returns expectedEntity

    val result = supervisor.findOne(
        predicate = predicate,
        persist = true,
        loadPlan = AggregateLoadPlan.WHOLE_AGGREGATE
    )

    assertEquals(expectedEntity, result)
    verify { mockRepository.findOne(predicate, true, AggregateLoadPlan.WHOLE_AGGREGATE) }
    verify { mockUnitOfWork.persist(expectedEntity) }
}
```

Add a list variant:

```kotlin
@Test
@DisplayName("find should pass aggregate load plan to repository")
fun `find should pass aggregate load plan to repository`() {
    val predicate = TestPredicate()
    val expectedEntities = listOf(TestEntity(1L, "test1"), TestEntity(2L, "test2"))

    every {
        mockRepository.find(predicate, emptyList(), true, AggregateLoadPlan.WHOLE_AGGREGATE)
    } returns expectedEntities

    val result = supervisor.find(
        predicate = predicate,
        orders = emptyList(),
        persist = true,
        loadPlan = AggregateLoadPlan.WHOLE_AGGREGATE
    )

    assertEquals(expectedEntities, result)
    verify { mockRepository.find(predicate, emptyList(), true, AggregateLoadPlan.WHOLE_AGGREGATE) }
    expectedEntities.forEach { entity -> verify { mockUnitOfWork.persist(entity) } }
}
```

- [ ] **Step 2: Run failing repository supervisor tests**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.domain.repo.impl.DefaultRepositorySupervisorTest"
```

Expected: fail because implementation methods do not yet accept or forward `loadPlan`.

- [ ] **Step 3: Implement load-plan forwarding in `DefaultRepositorySupervisor`**

Update every read override to pass `loadPlan` into the concrete repository.

Example:

```kotlin
override fun <ENTITY : Any> findOne(
    predicate: Predicate<ENTITY>,
    persist: Boolean,
    loadPlan: AggregateLoadPlan
): ENTITY? = repo(reflectEntityClass<ENTITY>(predicate), predicate)
    .findOne(predicate, persist, loadPlan)
    ?.also { if (persist) unitOfWork.persist(it) }
```

Update the list, page-list, first, and page variants with the same pattern.

For `remove()`, keep the existing default behavior:

```kotlin
repo(reflectEntityClass<ENTITY>(predicate), predicate)
    .find(predicate)
    .onEach(unitOfWork::remove)
```

Do not add a load-plan parameter to `remove()` in this slice.

- [ ] **Step 4: Write aggregate supervisor pass-through tests**

In `DefaultAggregateSupervisorTest.kt`, import:

```kotlin
import com.only4.cap4k.ddd.core.domain.repo.AggregateLoadPlan
```

Add this test near existing `findOne` coverage:

```kotlin
@Test
@DisplayName("findOne should pass aggregate load plan to repository supervisor")
fun `findOne should pass aggregate load plan to repository supervisor`() {
    val predicate = mockk<AggregatePredicate<TestAggregate, TestEntity>>()
    val entity = TestEntity(1L, "test")

    every {
        JpaAggregatePredicateSupport.reflectAggregateClass(any<AggregatePredicate<TestAggregate, TestEntity>>())
    } returns TestAggregate::class.java
    every {
        JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<TestAggregate, TestEntity>>())
    } returns mockk()
    every {
        mockRepositorySupervisor.findOne(any<Predicate<TestEntity>>(), true, AggregateLoadPlan.WHOLE_AGGREGATE)
    } returns entity

    val result = supervisor.findOne(
        predicate = predicate,
        persist = true,
        loadPlan = AggregateLoadPlan.WHOLE_AGGREGATE
    )

    assertNotNull(result)
    verify {
        mockRepositorySupervisor.findOne(any<Predicate<TestEntity>>(), true, AggregateLoadPlan.WHOLE_AGGREGATE)
    }
}
```

- [ ] **Step 5: Implement load-plan forwarding in `DefaultAggregateSupervisor`**

Update aggregate read methods so they pass `loadPlan` to `RepositorySupervisor`.

Example:

```kotlin
override fun <AGGREGATE : Aggregate<*>> findOne(
    predicate: AggregatePredicate<AGGREGATE, *>,
    persist: Boolean,
    loadPlan: AggregateLoadPlan
): AGGREGATE? {
    val clazz = JpaAggregatePredicateSupport.reflectAggregateClass(predicate)
    val pred = JpaAggregatePredicateSupport.getPredicate(predicate)
    return repositorySupervisor.findOne(pred, persist, loadPlan)
        ?.let { entity -> newInstance(clazz, entity) }
}
```

Apply the same pattern to `getByIds`, `find`, `findFirst`, and `findPage`.

- [ ] **Step 6: Run JPA supervisor tests**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.domain.repo.impl.DefaultRepositorySupervisorTest" --tests "com.only4.cap4k.ddd.domain.aggregate.impl.DefaultAggregateSupervisorTest"
```

Expected: pass.

- [ ] **Step 7: Commit load-plan API propagation**

Run:

```powershell
git add ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/AggregateLoadPlan.kt `
        ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/Repository.kt `
        ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo/RepositorySupervisor.kt `
        ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/AggregateSupervisor.kt `
        ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt `
        ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt `
        ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/aggregate/impl/DefaultAggregateSupervisor.kt `
        ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt `
        ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/aggregate/impl/DefaultAggregateSupervisorTest.kt
git commit -m "feat: propagate aggregate load plans through repositories"
```

## Task 5: Implement JPA WHOLE_AGGREGATE Owned-Collection Initialization

**Files:**

- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/AbstractJpaRepository.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt`

- [ ] **Step 1: Add a runtime request that uses `WHOLE_AGGREGATE`**

In `AggregateJpaRuntimeDefectReproductionTest.kt`, import:

```kotlin
import com.only4.cap4k.ddd.core.domain.repo.AggregateLoadPlan
```

Add request and handler near `CountRuntimeRootChildrenRequest`:

```kotlin
data class CountRuntimeRootChildrenWholeLoadRequest(
    val rootId: Long
) : RequestParam<CountRuntimeRootChildrenResponse>

@Component
class CountRuntimeRootChildrenWholeLoadCommand :
    Command<CountRuntimeRootChildrenWholeLoadRequest, CountRuntimeRootChildrenResponse> {
    override fun exec(request: CountRuntimeRootChildrenWholeLoadRequest): CountRuntimeRootChildrenResponse {
        val root = RepositorySupervisor.instance.findOne(
            JpaPredicate.byId(RuntimeRoot::class.java, request.rootId),
            persist = true,
            loadPlan = AggregateLoadPlan.WHOLE_AGGREGATE
        ) ?: error("RuntimeRoot not found: ${request.rootId}")

        return CountRuntimeRootChildrenResponse(root.children.size)
    }
}
```

- [ ] **Step 2: Add the failing whole-load runtime test**

Add this test after `commandHandlerRepositoryLoadCanAccessLazyAggregateChildren()`:

```kotlin
@Test
@DisplayName("whole aggregate load plan can access lazy aggregate children without request transaction")
fun wholeAggregateLoadPlanCanAccessLazyAggregateChildrenWithoutRequestTransaction() {
    val root = saveRoot(RuntimeRoot(name = "lazy-whole-load").apply {
        children.add(RuntimeChild(name = "lazy-whole-load-child"))
    })
    JpaUnitOfWork.reset()

    val response = RequestSupervisor.instance.send(CountRuntimeRootChildrenWholeLoadRequest(root.id))

    assertEquals(1, response.childCount)
}
```

- [ ] **Step 3: Run the failing runtime test**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest.wholeAggregateLoadPlanCanAccessLazyAggregateChildrenWithoutRequestTransaction"
```

Expected: fail with `LazyInitializationException` because `WHOLE_AGGREGATE` is not yet implemented in `AbstractJpaRepository`.

- [ ] **Step 4: Implement load-plan application in `AbstractJpaRepository`**

In `AbstractJpaRepository.kt`, import:

```kotlin
import com.only4.cap4k.ddd.core.domain.repo.AggregateLoadPlan
import jakarta.persistence.OneToMany
import org.hibernate.Hibernate
```

Update read method signatures to include `loadPlan`.

After each repository query returns entities, call `applyLoadPlan` before detach:

```kotlin
applyLoadPlan(entities, loadPlan)

if (!persist && entities.isNotEmpty()) {
    entities.forEach { entityManager.detach(it) }
}
```

For single entity reads:

```kotlin
entity?.let { applyLoadPlan(it, loadPlan) }

if (!persist && entity != null) {
    entityManager.detach(entity)
}
```

Add these private helpers immediately before the existing `override fun count(predicate: Predicate<ENTITY>): Long` method:

```kotlin
private fun applyLoadPlan(entities: Iterable<ENTITY>, loadPlan: AggregateLoadPlan) {
    if (loadPlan != AggregateLoadPlan.WHOLE_AGGREGATE) return
    val visited = mutableSetOf<Int>()
    entities.forEach { entity -> initializeOwnedCollections(entity, visited) }
}

private fun applyLoadPlan(entity: ENTITY, loadPlan: AggregateLoadPlan) {
    if (loadPlan != AggregateLoadPlan.WHOLE_AGGREGATE) return
    initializeOwnedCollections(entity, mutableSetOf())
}

private fun initializeOwnedCollections(entity: Any, visited: MutableSet<Int>) {
    val identity = System.identityHashCode(entity)
    if (!visited.add(identity)) return

    for (field in persistentFields(Hibernate.getClass(entity))) {
        val oneToMany = field.getAnnotation(OneToMany::class.java) ?: continue
        if (!oneToMany.orphanRemoval && oneToMany.cascade.isEmpty()) continue

        field.isAccessible = true
        val value = field.get(entity) ?: continue
        Hibernate.initialize(value)

        if (value is Iterable<*>) {
            value.filterNotNull().forEach { child -> initializeOwnedCollections(child, visited) }
        }
    }
}

private fun persistentFields(type: Class<*>): Sequence<java.lang.reflect.Field> =
    generateSequence(type) { current ->
        current.superclass?.takeIf { it != Any::class.java }
    }.flatMap { current ->
        current.declaredFields.asSequence()
    }
```

This intentionally traverses only `@OneToMany`. Do not traverse `@ManyToOne`, because that reintroduces inverse graph cycles.

- [ ] **Step 5: Run the whole-load runtime test**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest.wholeAggregateLoadPlanCanAccessLazyAggregateChildrenWithoutRequestTransaction"
```

Expected: pass.

- [ ] **Step 6: Run the known-defect contrast tests**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest.commandHandlerRepositoryLoadCanAccessLazyAggregateChildren" --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest.wholeAggregateLoadPlanCanAccessLazyAggregateChildrenWithoutRequestTransaction"
```

Expected:

- existing default command path remains known defect
- explicit `WHOLE_AGGREGATE` request path passes

- [ ] **Step 7: Commit JPA load-plan implementation**

Run:

```powershell
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/AbstractJpaRepository.kt `
        cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt
git commit -m "feat: initialize owned collections for whole aggregate loads"
```

## Task 6: Full Verification And Documentation Closure

**Files:**

- Modify: `docs/superpowers/specs/2026-04-21-cap4k-aggregate-persistence-runtime-verification-hardening-design.md`
- Modify: `docs/superpowers/mainline-roadmap.md`

- [ ] **Step 1: Run focused generator and runtime verification**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test `
    :cap4k-plugin-pipeline-core:test `
    :cap4k-plugin-pipeline-generator-aggregate:test `
    :cap4k-plugin-pipeline-renderer-pebble:test `
    :ddd-core:test `
    :ddd-domain-repo-jpa:test `
    :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest"
```

Expected: all tasks pass.

- [ ] **Step 2: Run compile-level aggregate functional coverage**

Run the concrete aggregate compile-functional tests that cover generated aggregate output and generated-source integration:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test `
    --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate relation generation participates in domain compileKotlin" `
    --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate inverse read only relation generation participates in domain compileKotlin with scalar fk preserved" `
    --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate persistence field behavior generation participates in domain compileKotlin" `
    --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate provider specific persistence generation participates in domain compileKotlin" `
    --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate provider persistence generation keeps custom generator and identity compile-safe together"
```

Expected: all selected Gradle plugin tests pass.

- [ ] **Step 3: Update runtime verification spec**

In `docs/superpowers/specs/2026-04-21-cap4k-aggregate-persistence-runtime-verification-hardening-design.md`, update Runtime Reproduction Notes with the implemented outcome:

```markdown
2026-04-29 implementation result: generated parent-child aggregate cascades no longer use `CascadeType.ALL`; they render explicit `PERSIST`, `MERGE`, and `REMOVE`, excluding `REFRESH`. The runtime fixture keeps the old `CascadeType.ALL` nested inverse eager graph as a known-defect contrast and adds a safe-cascade graph that persists successfully without triggering refresh-based `FetchNotFoundException`.

2026-04-29 implementation result: repository and aggregate read APIs now carry `AggregateLoadPlan`. `WHOLE_AGGREGATE` initializes owned `@OneToMany` aggregate collections below the JPA repository boundary, allowing command handlers to request a usable aggregate graph without requiring command-wide transaction expansion or global eager mappings.
```

- [ ] **Step 4: Update roadmap status**

In `docs/superpowers/mainline-roadmap.md`, update `Aggregate JPA mapping safety and load-plan semantics`:

```markdown
Status:

- implementation complete
- verified through generator API/core/aggregate/renderer tests, JPA repository tests, and aggregate runtime fixture
- command-wide transaction expansion remains deferred
```

Keep the note that command transaction-boundary expansion is deferred unless unit-of-work commit semantics are redesigned.

- [ ] **Step 5: Search for forbidden generated cascade output**

Run:

```powershell
rg -n "CascadeType\\.ALL|cascadeAll" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-renderer-pebble
```

Expected:

- no production references
- no aggregate default template references
- test references only if they assert absence or preserve known-defect contrast outside generated output

- [ ] **Step 6: Search for load-plan API propagation gaps**

Run:

```powershell
rg -n "findOne\\(|findPage\\(|findFirst\\(|getByIds\\(|loadPlan" ddd-core/src/main/kotlin ddd-domain-repo-jpa/src/main/kotlin
```

Manually verify:

- every read method with `persist` also accepts `loadPlan`
- `count`, `exists`, and `remove` do not gain load-plan parameters in this slice
- `persist=false` still detaches loaded entities
- `persist=true` still registers loaded entities into `UnitOfWork`

- [ ] **Step 7: Commit documentation closure**

Run:

```powershell
git add docs/superpowers/specs/2026-04-21-cap4k-aggregate-persistence-runtime-verification-hardening-design.md `
        docs/superpowers/mainline-roadmap.md
git commit -m "docs: close aggregate load-plan persistence slice"
```

## Final Verification Gate

- [ ] **Step 1: Run final focused verification**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test `
    :cap4k-plugin-pipeline-core:test `
    :cap4k-plugin-pipeline-generator-aggregate:test `
    :cap4k-plugin-pipeline-renderer-pebble:test `
    :ddd-core:test `
    :ddd-domain-repo-jpa:test `
    :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Check working tree**

Run:

```powershell
git status --short
```

Expected: no uncommitted changes.

- [ ] **Step 3: Prepare final handoff**

Final handoff must include:

- commits created
- verification commands and outcomes
- whether `CascadeType.ALL` remains anywhere in generated aggregate output
- whether command-wide transaction expansion remains deferred
- any skipped Gradle plugin compile-functional tests and the exact reason

## Plan Self-Review

Spec coverage:

- Mapping safety is covered by Task 1 and Task 2.
- Mediator/supervisor load-plan semantics are covered by Task 3 and Task 4.
- JPA provider-specific whole-aggregate loading is covered by Task 5.
- Command-wide transaction expansion is explicitly excluded and preserved as a later architecture slice.
- Backend replacement is explicitly excluded.

Placeholder scan:

- The plan intentionally contains no `TBD`, no `TODO`, and no unbounded "handle edge cases" steps.
- Each code-changing task names exact files and gives the concrete code shape to add or replace.

Type consistency:

- Public enum name is consistently `AggregateLoadPlan`.
- Public values are consistently `DEFAULT`, `MINIMAL`, and `WHOLE_AGGREGATE`.
- Generator cascade enum name is consistently `AggregateCascadeType`.
- Generator render model field name is consistently `cascadeTypes`.
