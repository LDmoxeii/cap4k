# Cap4k Aggregate Jpa Runtime Defect Reproduction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Do not use subagents unless the user explicitly asks for subagent mode. Steps use checkbox (`- [ ]`) syntax for tracking.
> **Status:** Implemented on 2026-04-29. This is now a historical execution plan; concrete runtime results are recorded in `docs/superpowers/specs/2026-04-21-cap4k-aggregate-persistence-runtime-verification-hardening-design.md`.

Implementation note: the final fixture disables the Snowflake worker-table auto configuration and provides a real test `SnowflakeIdGenerator` bean, because this slice is about aggregate JPA behavior rather than the distributed worker-id table. The fixture also sets `spring.jpa.open-in-view=false` and `hibernate.enable_lazy_load_no_trans=false` to match the intended production persistence boundary.

**Goal:** Add runtime reproduction coverage for the three aggregate JPA defects exposed by `only-danmuku`: preassignable application-side IDs, command transaction boundary with lazy aggregate relations, and three-level aggregate whole-save behavior.

**Architecture:** Add one focused Spring Boot + H2 runtime fixture under `cap4k-ddd-starter` so the tests exercise real Spring Data JPA, cap4k repository registration, `JpaUnitOfWork`, and `DefaultRequestSupervisor`. This slice is reproduction-only: tests classify current behavior as either supported or a known defect, and no production behavior is changed.

**Tech Stack:** Kotlin, JUnit 5, Spring Boot Test, Spring Data JPA, H2, Hibernate, cap4k `RequestSupervisor`, cap4k `RepositorySupervisor`, cap4k `JpaUnitOfWork`.

---

## Scope Lock

Allowed in this slice:

- Add runtime fixture tests under `cap4k-ddd-starter/src/test/kotlin`.
- Use real Spring Boot, H2, Spring Data JPA, cap4k repository supervisor, request supervisor, and unit of work.
- Keep the suite green by explicitly classifying current known defects.
- Update the aggregate runtime spec only with concrete reproduction notes discovered by the tests.

Not allowed in this slice:

- No production code changes.
- No `RequestSupervisor` split.
- No command transaction wrapper.
- No production `@Transactional` additions.
- No global fetch strategy change from `LAZY` to `EAGER`.
- No `JpaUnitOfWork` merge/snapshot fix.
- No ID strategy registry implementation.
- No generated aggregate template changes.
- No broad persistence-context scanning re-enable.

## Characterization Rule

The implementation must not leave CI red. Each scenario uses this rule:

- If current runtime satisfies the desired contract, assert the desired contract directly.
- If current runtime violates the desired contract, the test may still pass only when the failure is explicitly classified as the expected known defect.
- If the runtime fails with an unclassified exception, the test fails.

Use this helper in the test file:

```kotlin
private enum class RuntimeClassification {
    SUPPORTED,
    KNOWN_DEFECT
}

private fun classifyRuntimeBehavior(
    label: String,
    desiredContract: () -> Unit,
    knownDefect: (Throwable) -> Boolean
): RuntimeClassification {
    val result = runCatching(desiredContract)
    if (result.isSuccess) return RuntimeClassification.SUPPORTED

    val failure = result.exceptionOrNull()!!
    assertTrue(
        knownDefect(failure),
        "$label failed with an unclassified exception: ${failure::class.java.name}: ${failure.message}"
    )
    return RuntimeClassification.KNOWN_DEFECT
}

private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return true
        current = current.cause
    }
    return false
}
```

## Files

- Create: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt`
- Possibly modify: `docs/superpowers/specs/2026-04-21-cap4k-aggregate-persistence-runtime-verification-hardening-design.md`
- Do not modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- Do not modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/impl/DefaultRequestSupervisor.kt`

## Task 1: Add Runtime Test Fixture

**Files:**

- Create: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt`

- [ ] **Step 1: Create the package directory**

Run from `cap4k`:

```powershell
New-Item -ItemType Directory -Force "cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime"
```

- [ ] **Step 2: Create the test class shell**

Create `AggregateJpaRuntimeDefectReproductionTest.kt` with this structure:

```kotlin
package com.only4.cap4k.ddd.runtime

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.RequestSupervisor
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor
import com.only4.cap4k.ddd.domain.repo.AbstractJpaRepository
import com.only4.cap4k.ddd.domain.repo.JpaPredicate
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.LazyInitializationException
import org.hibernate.annotations.GenericGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

private const val SNOWFLAKE_GENERATOR = "com.only4.cap4k.ddd.domain.distributed.SnowflakeIdentifierGenerator"

/**
 * Runtime characterization for aggregate JPA behavior.
 *
 * This fixture intentionally does not repair production behavior.
 * It records whether current cap4k runtime supports or violates:
 * - preassignable application-side IDs
 * - command handler lazy aggregate access
 * - root-only three-level aggregate whole-save behavior
 */
@SpringBootTest(classes = [AggregateJpaRuntimeDefectReproductionTest.RuntimeTestApplication::class])
@TestPropertySource(
    properties = [
        "cap4k.application.name=aggregate-jpa-runtime-defect-test",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.url=jdbc:h2:mem:aggregate-jpa-runtime-defect;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "logging.level.com.only4.cap4k.ddd=WARN",
        "logging.level.org.hibernate=WARN",
        "cap4k.ddd.domain.event.enable=true",
        "cap4k.ddd.domain.event.event-scan-package=com.only4.cap4k.ddd.runtime",
        "cap4k.ddd.application.request.enable=true",
        "cap4k.ddd.application.saga.enable=false",
        "cap4k.ddd.application.event.http.enable=false",
        "cap4k.ddd.application.event.rabbitmq.enable=false",
        "cap4k.ddd.application.event.rocketmq.enable=false",
        "cap4k.ddd.domain.distributed.snowflake.enable=true",
        "cap4k.ddd.domain.distributed.snowflake.worker-id=1",
        "cap4k.ddd.domain.distributed.snowflake.datacenter-id=1",
        "cap4k.ddd.application.distributed.locker.enable=true",
        "cap4k.ddd.application.distributed.locker.timeout-seconds=30"
    ]
)
@DisplayName("Aggregate JPA runtime defect reproduction")
class AggregateJpaRuntimeDefectReproductionTest {

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var unitOfWork: UnitOfWork
    @Autowired private lateinit var rootJpaRepository: RuntimeRootJpaRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.update("delete from `runtime_grandchild`")
        jdbcTemplate.update("delete from `runtime_child`")
        jdbcTemplate.update("delete from `runtime_root`")
        JpaUnitOfWork.reset()
    }

    @Test
    @DisplayName("fixture boots with real cap4k runtime beans")
    fun fixtureBootsWithRealRuntimeBeans() {
        assertNotNull(unitOfWork)
        assertNotNull(RequestSupervisor.instance)
        assertNotNull(RepositorySupervisor.instance)
    }

    private fun saveRoot(root: RuntimeRoot): RuntimeRoot {
        unitOfWork.persist(root)
        unitOfWork.save()
        return root
    }

    private fun newThreeLevelRoot(name: String): RuntimeRoot =
        RuntimeRoot(name = name).apply {
            children.add(RuntimeChild(name = "$name-child-a").apply {
                grandchildren.add(RuntimeGrandchild(name = "$name-grandchild-a1"))
                grandchildren.add(RuntimeGrandchild(name = "$name-grandchild-a2"))
            })
            children.add(RuntimeChild(name = "$name-child-b").apply {
                grandchildren.add(RuntimeGrandchild(name = "$name-grandchild-b1"))
                grandchildren.add(RuntimeGrandchild(name = "$name-grandchild-b2"))
            })
        }

    private enum class RuntimeClassification {
        SUPPORTED,
        KNOWN_DEFECT
    }

    private fun classifyRuntimeBehavior(
        label: String,
        desiredContract: () -> Unit,
        knownDefect: (Throwable) -> Boolean
    ): RuntimeClassification {
        val result = runCatching(desiredContract)
        if (result.isSuccess) return RuntimeClassification.SUPPORTED

        val failure = result.exceptionOrNull()!!
        assertTrue(
            knownDefect(failure),
            "$label failed with an unclassified exception: ${failure::class.java.name}: ${failure.message}"
        )
        return RuntimeClassification.KNOWN_DEFECT
    }

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) return true
            current = current.cause
        }
        return false
    }

    @SpringBootApplication
    @ComponentScan(basePackages = ["com.only4.cap4k.ddd", "com.only4.cap4k.ddd.runtime"])
    @EntityScan(basePackages = ["com.only4.cap4k.ddd", "com.only4.cap4k.ddd.runtime"])
    @EnableJpaRepositories(basePackages = ["com.only4.cap4k.ddd", "com.only4.cap4k.ddd.runtime"])
    class RuntimeTestApplication
}
```

- [ ] **Step 3: Append runtime entities, repositories, and command fixture**

Append these top-level declarations after the test class:

```kotlin
@Entity
@Table(name = "`runtime_root`")
open class RuntimeRoot(id: Long = 0L, name: String = "") {
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "`root_id`", nullable = false)
    open var children: MutableList<RuntimeChild> = mutableListOf()

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
@Table(name = "`runtime_child`")
open class RuntimeChild(id: Long = 0L, name: String = "") {
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "`child_id`", nullable = false)
    open var grandchildren: MutableList<RuntimeGrandchild> = mutableListOf()

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
@Table(name = "`runtime_grandchild`")
open class RuntimeGrandchild(id: Long = 0L, name: String = "") {
    @Id
    @GeneratedValue(generator = SNOWFLAKE_GENERATOR)
    @GenericGenerator(name = SNOWFLAKE_GENERATOR, strategy = SNOWFLAKE_GENERATOR)
    @Column(name = "`id`", insertable = false, updatable = false)
    open var id: Long = id
        protected set

    @Column(name = "`name`", nullable = false)
    open var name: String = name
}

interface RuntimeRootJpaRepository :
    JpaRepository<RuntimeRoot, Long>,
    JpaSpecificationExecutor<RuntimeRoot>

@Repository
class RuntimeRootRepository(
    rootJpaRepository: RuntimeRootJpaRepository
) : AbstractJpaRepository<RuntimeRoot, Long>(rootJpaRepository, rootJpaRepository)

data class CountRuntimeRootChildrenRequest(
    val rootId: Long
) : RequestParam<CountRuntimeRootChildrenResponse>

data class CountRuntimeRootChildrenResponse(
    val childCount: Int
)

@Component
class CountRuntimeRootChildrenCommand : Command<CountRuntimeRootChildrenRequest, CountRuntimeRootChildrenResponse> {
    override fun exec(request: CountRuntimeRootChildrenRequest): CountRuntimeRootChildrenResponse {
        val root = RepositorySupervisor.instance.findOne(
            JpaPredicate.byId(RuntimeRoot::class.java, request.rootId)
        ) ?: error("RuntimeRoot not found: ${request.rootId}")

        return CountRuntimeRootChildrenResponse(root.children.size)
    }
}
```

- [ ] **Step 4: Verify fixture boot**

Run from `cap4k`:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest.fixtureBootsWithRealRuntimeBeans"
```

Expected:

- The fixture compiles.
- The Spring context boots.
- If it fails, fix only test wiring or test properties.

## Task 2: Characterize Preassignable Application-Side ID Behavior

**Files:**

- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt`

- [ ] **Step 1: Add omitted-ID characterization**

Add this method inside `AggregateJpaRuntimeDefectReproductionTest`:

```kotlin
@Test
@DisplayName("application-side generated id is assigned when root id is omitted")
fun applicationSideGeneratedIdIsAssignedWhenRootIdIsOmitted() {
    val classification = classifyRuntimeBehavior(
        label = "omitted application-side generated id",
        desiredContract = {
            val root = saveRoot(RuntimeRoot(name = "omitted-id"))
            assertNotEquals(0L, root.id, "A root created without an id should receive a generated id")
            assertTrue(rootJpaRepository.existsById(root.id), "The generated id should point to a row")
        },
        knownDefect = { failure ->
            failure.hasCause<org.hibernate.id.IdentifierGenerationException>() ||
                failure.hasCause<jakarta.persistence.PersistenceException>() ||
                failure is AssertionError
        }
    )

    assertTrue(
        classification == RuntimeClassification.SUPPORTED ||
            classification == RuntimeClassification.KNOWN_DEFECT
    )
}
```

- [ ] **Step 2: Add preassigned-ID characterization**

Add this method inside `AggregateJpaRuntimeDefectReproductionTest`:

```kotlin
@Test
@DisplayName("preassigned application-side id is preserved for a new root")
fun preassignedApplicationSideIdIsPreservedForNewRoot() {
    val preassignedId = 9_001_001L

    val classification = classifyRuntimeBehavior(
        label = "preassigned application-side generated id",
        desiredContract = {
            val root = RuntimeRoot(id = preassignedId, name = "preassigned-id")
            saveRoot(root)
            assertTrue(rootJpaRepository.existsById(preassignedId), "A preassigned id should be inserted")
            assertEquals(preassignedId, rootJpaRepository.findById(preassignedId).orElseThrow().id)
        },
        knownDefect = { failure ->
            failure.hasCause<org.hibernate.PersistentObjectException>() ||
                failure.hasCause<org.hibernate.id.IdentifierGenerationException>() ||
                failure.hasCause<jakarta.persistence.PersistenceException>() ||
                failure is AssertionError
        }
    )

    assertTrue(
        classification == RuntimeClassification.SUPPORTED ||
            classification == RuntimeClassification.KNOWN_DEFECT
    )
}
```

- [ ] **Step 3: Run ID characterization**

Run from `cap4k`:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest.applicationSideGeneratedIdIsAssignedWhenRootIdIsOmitted" --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest.preassignedApplicationSideIdIsPreservedForNewRoot"
```

Expected:

- Both tests pass as `SUPPORTED` or classified `KNOWN_DEFECT`.
- If either test fails with an unclassified exception, classify that exception only when it directly represents current ID behavior.
- Do not change ID generator or unit-of-work production code.

## Task 3: Characterize Command Transaction Boundary And Lazy Loading

**Files:**

- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt`

- [ ] **Step 1: Add command lazy-loading characterization**

Add this method inside `AggregateJpaRuntimeDefectReproductionTest`:

```kotlin
@Test
@DisplayName("command handler repository load can access lazy aggregate children")
fun commandHandlerRepositoryLoadCanAccessLazyAggregateChildren() {
    val root = saveRoot(RuntimeRoot(name = "lazy-command").apply {
        children.add(RuntimeChild(name = "lazy-command-child"))
    })
    JpaUnitOfWork.reset()

    val classification = classifyRuntimeBehavior(
        label = "command handler lazy aggregate access",
        desiredContract = {
            val response = RequestSupervisor.instance.send(CountRuntimeRootChildrenRequest(root.id))
            assertEquals(1, response.childCount)
        },
        knownDefect = { failure ->
            failure.hasCause<LazyInitializationException>() ||
                failure is AssertionError
        }
    )

    assertTrue(
        classification == RuntimeClassification.SUPPORTED ||
            classification == RuntimeClassification.KNOWN_DEFECT
    )
}
```

- [ ] **Step 2: Add controlled transaction contrast test**

Add this method inside `AggregateJpaRuntimeDefectReproductionTest`:

```kotlin
@Test
@DisplayName("controlled transaction can access lazy aggregate children")
fun controlledTransactionCanAccessLazyAggregateChildren() {
    val root = saveRoot(RuntimeRoot(name = "lazy-controlled").apply {
        children.add(RuntimeChild(name = "lazy-controlled-child"))
    })
    JpaUnitOfWork.reset()

    val childCount = TransactionTemplate(transactionManager).execute {
        val loaded = rootJpaRepository.findById(root.id).orElseThrow()
        loaded.children.size
    }

    assertEquals(1, childCount, "The same mapping should work inside a transaction")
}
```

- [ ] **Step 3: Run lazy-loading characterization**

Run from `cap4k`:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest.commandHandlerRepositoryLoadCanAccessLazyAggregateChildren" --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest.controlledTransactionCanAccessLazyAggregateChildren"
```

Expected:

- Controlled transaction test passes.
- Command handler test passes as `SUPPORTED` or classified `KNOWN_DEFECT`.
- If controlled transaction fails, fix fixture mapping before continuing.
- Do not add command transaction behavior in this task.

## Task 4: Characterize Three-Level Aggregate Whole-Save Behavior

**Files:**

- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt`

- [ ] **Step 1: Add root-only create characterization**

Add this method inside `AggregateJpaRuntimeDefectReproductionTest`:

```kotlin
@Test
@DisplayName("root-only save persists children and grandchildren")
fun rootOnlySavePersistsChildrenAndGrandchildren() {
    val classification = classifyRuntimeBehavior(
        label = "three-level root-only create save",
        desiredContract = {
            val root = saveRoot(newThreeLevelRoot("create-graph"))
            assertNotEquals(0L, root.id)
            assertEquals(1, jdbcTemplate.queryForObject("select count(*) from `runtime_root`", Int::class.java))
            assertEquals(2, jdbcTemplate.queryForObject("select count(*) from `runtime_child`", Int::class.java))
            assertEquals(4, jdbcTemplate.queryForObject("select count(*) from `runtime_grandchild`", Int::class.java))
        },
        knownDefect = { failure ->
            failure.hasCause<jakarta.persistence.PersistenceException>() ||
                failure.hasCause<org.hibernate.HibernateException>() ||
                failure is AssertionError
        }
    )

    assertTrue(
        classification == RuntimeClassification.SUPPORTED ||
            classification == RuntimeClassification.KNOWN_DEFECT
    )
}
```

- [ ] **Step 2: Add managed scalar update characterization**

Add this method inside `AggregateJpaRuntimeDefectReproductionTest`:

```kotlin
@Test
@DisplayName("managed three-level graph updates child and grandchild scalar fields")
fun managedThreeLevelGraphUpdatesChildAndGrandchildScalarFields() {
    val root = saveRoot(newThreeLevelRoot("update-graph"))
    JpaUnitOfWork.reset()

    val classification = classifyRuntimeBehavior(
        label = "three-level managed scalar update",
        desiredContract = {
            TransactionTemplate(transactionManager).execute {
                val loaded = rootJpaRepository.findById(root.id).orElseThrow()
                loaded.children.first().name = "updated-child"
                loaded.children.first().grandchildren.first().name = "updated-grandchild"
                unitOfWork.persist(loaded)
                unitOfWork.save()
            }
            assertEquals(1, jdbcTemplate.queryForObject("select count(*) from `runtime_child` where `name` = 'updated-child'", Int::class.java))
            assertEquals(1, jdbcTemplate.queryForObject("select count(*) from `runtime_grandchild` where `name` = 'updated-grandchild'", Int::class.java))
        },
        knownDefect = { failure ->
            failure.hasCause<jakarta.persistence.PersistenceException>() ||
                failure.hasCause<org.hibernate.HibernateException>() ||
                failure is AssertionError
        }
    )

    assertTrue(
        classification == RuntimeClassification.SUPPORTED ||
            classification == RuntimeClassification.KNOWN_DEFECT
    )
}
```

- [ ] **Step 3: Add orphan-removal characterization**

Add these methods inside `AggregateJpaRuntimeDefectReproductionTest`:

```kotlin
@Test
@DisplayName("managed child collection removes one grandchild through orphan removal")
fun managedChildCollectionRemovesOneGrandchildThroughOrphanRemoval() {
    val root = saveRoot(newThreeLevelRoot("remove-grandchild"))
    JpaUnitOfWork.reset()

    val classification = classifyRuntimeBehavior(
        label = "three-level managed grandchild orphan removal",
        desiredContract = {
            TransactionTemplate(transactionManager).execute {
                val loaded = rootJpaRepository.findById(root.id).orElseThrow()
                loaded.children.first().grandchildren.removeAt(0)
                unitOfWork.persist(loaded)
                unitOfWork.save()
            }
            assertEquals(3, jdbcTemplate.queryForObject("select count(*) from `runtime_grandchild`", Int::class.java))
        },
        knownDefect = { failure ->
            failure.hasCause<jakarta.persistence.PersistenceException>() ||
                failure.hasCause<org.hibernate.HibernateException>() ||
                failure is AssertionError
        }
    )

    assertTrue(
        classification == RuntimeClassification.SUPPORTED ||
            classification == RuntimeClassification.KNOWN_DEFECT
    )
}

@Test
@DisplayName("managed root collection removes one child and descendants through orphan removal")
fun managedRootCollectionRemovesOneChildAndDescendantsThroughOrphanRemoval() {
    val root = saveRoot(newThreeLevelRoot("remove-child"))
    JpaUnitOfWork.reset()

    val classification = classifyRuntimeBehavior(
        label = "three-level managed child orphan removal",
        desiredContract = {
            TransactionTemplate(transactionManager).execute {
                val loaded = rootJpaRepository.findById(root.id).orElseThrow()
                loaded.children.removeAt(0)
                unitOfWork.persist(loaded)
                unitOfWork.save()
            }
            assertEquals(1, jdbcTemplate.queryForObject("select count(*) from `runtime_child`", Int::class.java))
            assertEquals(2, jdbcTemplate.queryForObject("select count(*) from `runtime_grandchild`", Int::class.java))
        },
        knownDefect = { failure ->
            failure.hasCause<jakarta.persistence.PersistenceException>() ||
                failure.hasCause<org.hibernate.HibernateException>() ||
                failure is AssertionError
        }
    )

    assertTrue(
        classification == RuntimeClassification.SUPPORTED ||
            classification == RuntimeClassification.KNOWN_DEFECT
    )
}
```

- [ ] **Step 4: Add clear-and-readd characterization**

Add this method inside `AggregateJpaRuntimeDefectReproductionTest`:

```kotlin
@Test
@DisplayName("managed grandchild collection supports clear and re-add")
fun managedGrandchildCollectionSupportsClearAndReAdd() {
    val root = saveRoot(newThreeLevelRoot("clear-readd"))
    JpaUnitOfWork.reset()

    val classification = classifyRuntimeBehavior(
        label = "three-level managed clear and re-add",
        desiredContract = {
            TransactionTemplate(transactionManager).execute {
                val loaded = rootJpaRepository.findById(root.id).orElseThrow()
                val firstChild = loaded.children.first()
                firstChild.grandchildren.clear()
                firstChild.grandchildren.add(RuntimeGrandchild(name = "clear-readd-new-grandchild"))
                unitOfWork.persist(loaded)
                unitOfWork.save()
            }
            assertEquals(3, jdbcTemplate.queryForObject("select count(*) from `runtime_grandchild`", Int::class.java))
            assertEquals(1, jdbcTemplate.queryForObject("select count(*) from `runtime_grandchild` where `name` = 'clear-readd-new-grandchild'", Int::class.java))
        },
        knownDefect = { failure ->
            failure.hasCause<jakarta.persistence.PersistenceException>() ||
                failure.hasCause<org.hibernate.HibernateException>() ||
                failure is AssertionError
        }
    )

    assertTrue(
        classification == RuntimeClassification.SUPPORTED ||
            classification == RuntimeClassification.KNOWN_DEFECT
    )
}
```

- [ ] **Step 5: Run three-level characterization**

Run from `cap4k`:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest.rootOnlySavePersistsChildrenAndGrandchildren" --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest.managedThreeLevelGraphUpdatesChildAndGrandchildScalarFields" --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest.managedChildCollectionRemovesOneGrandchildThroughOrphanRemoval" --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest.managedRootCollectionRemovesOneChildAndDescendantsThroughOrphanRemoval" --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest.managedGrandchildCollectionSupportsClearAndReAdd"
```

Expected:

- Every scenario passes as `SUPPORTED` or classified `KNOWN_DEFECT`.
- Do not add child repositories.
- Do not call `unitOfWork.persist(child)` or `unitOfWork.persist(grandchild)`.

## Task 5: Verify And Record Classification

**Files:**

- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt`
- Possibly modify: `docs/superpowers/specs/2026-04-21-cap4k-aggregate-persistence-runtime-verification-hardening-design.md`

- [ ] **Step 1: Run the full reproduction fixture**

Run from `cap4k` with a long timeout budget:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest"
```

Expected:

- The test class compiles.
- The test class passes.
- Any scenario that violates the desired contract is explicitly classified as `KNOWN_DEFECT`.
- Any unclassified exception fails the test.

- [ ] **Step 2: Run adjacent unit-of-work regression tests**

Run from `cap4k`:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest"
```

Expected:

- Existing `JpaUnitOfWorkTest` still passes.

- [ ] **Step 3: Update the spec with concrete reproduction notes**

Only update `docs/superpowers/specs/2026-04-21-cap4k-aggregate-persistence-runtime-verification-hardening-design.md` if the test results change a stated assumption. Use exact scenario names.

For a supported scenario, add:

```markdown
Runtime reproduction note: the H2 fixture currently supports `<scenario name>`; no repair task should be opened for this behavior unless a real-project fixture contradicts it.
```

For a known defect, add:

```markdown
Runtime reproduction note: the H2 fixture classifies `<scenario name>` as a current known defect; a repair plan must preserve the fixture and replace the characterization assertion with the desired contract assertion.
```

- [ ] **Step 4: Confirm diff boundaries**

Run from `cap4k`:

```powershell
git diff -- cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt docs/superpowers/specs/2026-04-21-cap4k-aggregate-persistence-runtime-verification-hardening-design.md
```

Expected:

- Diff contains only the runtime test fixture and concrete spec notes.
- No production file appears in the diff.
- No generated template appears in the diff.

## Final Verification

Run from `cap4k`:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest"
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest"
git status --short
```

Expected:

- Targeted runtime reproduction fixture passes.
- Existing unit-of-work tests pass.
- `git status --short` shows only the new test file and concrete spec note changes.

## Handoff Criteria

This reproduction-only slice is complete when:

- Preassignable application-side ID behavior is represented by executable characterization tests.
- Command handler lazy aggregate access through current `RequestSupervisor` is represented by executable characterization tests.
- Three-level root-only save, managed scalar update, grandchild orphan removal, child orphan removal, and clear/re-add behavior are represented by executable characterization tests.
- No production behavior has been changed.
- Every current runtime failure is classified, not silently ignored.
- The next repair plan can choose a fix based on observed test classification rather than speculation.
