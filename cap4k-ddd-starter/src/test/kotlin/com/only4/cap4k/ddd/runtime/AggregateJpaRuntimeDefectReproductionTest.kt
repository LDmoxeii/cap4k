package com.only4.cap4k.ddd.runtime

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.RequestSupervisor
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.domain.repo.AggregateLoadPlan
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor
import com.only4.cap4k.ddd.domain.distributed.SnowflakeIdentifierGenerator
import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeIdGenerator
import com.only4.cap4k.ddd.domain.repo.AbstractJpaRepository
import com.only4.cap4k.ddd.domain.repo.JpaPredicate
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.HibernateException
import org.hibernate.LazyInitializationException
import org.hibernate.PersistentObjectException
import org.hibernate.annotations.GenericGenerator
import org.hibernate.id.IdentifierGenerationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
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
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.enable_lazy_load_no_trans=false",
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
        "cap4k.ddd.application.request.schedule.add-partition-enable=false",
        "cap4k.ddd.application.saga.schedule.add-partition-enable=false",
        "cap4k.ddd.domain.event.schedule.add-partition-enable=false",
        "cap4k.ddd.distributed.id-generator.snowflake.enable=false",
        "cap4k.ddd.application.distributed.locker.enable=true",
        "cap4k.ddd.application.distributed.locker.timeout-seconds=30"
    ]
)
@DisplayName("Aggregate JPA runtime defect reproduction")
class AggregateJpaRuntimeDefectReproductionTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    @Qualifier("jpaUnitOfWork")
    private lateinit var unitOfWork: UnitOfWork

    @Autowired
    private lateinit var rootJpaRepository: RuntimeRootJpaRepository

    @Autowired
    private lateinit var reverseChildJpaRepository: RuntimeReverseChildJpaRepository

    @Autowired
    private lateinit var reverseGrandchildJpaRepository: RuntimeReverseGrandchildJpaRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.update("delete from `runtime_safe_reverse_grandchild`")
        jdbcTemplate.update("delete from `runtime_safe_reverse_child`")
        jdbcTemplate.update("delete from `runtime_safe_reverse_root`")
        jdbcTemplate.update("delete from `runtime_reverse_grandchild`")
        jdbcTemplate.update("delete from `runtime_reverse_child`")
        jdbcTemplate.update("delete from `runtime_reverse_root`")
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
                failure.hasCause<IdentifierGenerationException>() ||
                    failure.hasCause<jakarta.persistence.PersistenceException>() ||
                    failure is AssertionError
            }
        )

        assertSupported(classification)
    }

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
                failure.hasCause<PersistentObjectException>() ||
                    failure.hasCause<IdentifierGenerationException>() ||
                    failure.hasCause<jakarta.persistence.PersistenceException>() ||
                    failure is AssertionError
            }
        )

        assertKnownDefect(classification)
    }

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

        assertKnownDefect(classification)
    }

    @Test
    @DisplayName("transactional request scope can access lazy aggregate children")
    fun transactionalRequestScopeCanAccessLazyAggregateChildren() {
        val root = saveRoot(RuntimeRoot(name = "lazy-transactional-request").apply {
            children.add(RuntimeChild(name = "lazy-transactional-request-child"))
        })
        JpaUnitOfWork.reset()

        val response = requireNotNull(TransactionTemplate(transactionManager).execute {
            RequestSupervisor.instance.send(CountRuntimeRootChildrenRequest(root.id))
        })

        assertEquals(1, response.childCount)
    }

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

    @Test
    @DisplayName("controlled transaction can access lazy aggregate children")
    fun controlledTransactionCanAccessLazyAggregateChildren() {
        val root = saveRoot(RuntimeRoot(name = "lazy-controlled").apply {
            children.add(RuntimeChild(name = "lazy-controlled-child"))
        })
        JpaUnitOfWork.reset()

        val childCount = requireNotNull(TransactionTemplate(transactionManager).execute {
            val loaded = rootJpaRepository.findById(root.id).orElseThrow()
            loaded.children.size
        })

        assertEquals(1, childCount, "The same mapping should work inside a transaction")
    }

    @Test
    @DisplayName("root-only save persists children and grandchildren")
    fun rootOnlySavePersistsChildrenAndGrandchildren() {
        val classification = classifyRuntimeBehavior(
            label = "three-level root-only create save",
            desiredContract = {
                val root = saveRoot(newThreeLevelRoot("create-graph"))
                assertNotEquals(0L, root.id)
                assertEquals(1, countRows("select count(*) from `runtime_root`"))
                assertEquals(2, countRows("select count(*) from `runtime_child`"))
                assertEquals(4, countRows("select count(*) from `runtime_grandchild`"))
            },
            knownDefect = { failure ->
                failure.hasCause<jakarta.persistence.PersistenceException>() ||
                    failure.hasCause<HibernateException>() ||
                    failure is AssertionError
            }
        )

        assertSupported(classification)
    }

    @Test
    @DisplayName("root-only save binds generated parent ids to nested descendants")
    fun rootOnlySaveBindsGeneratedParentIdsToNestedDescendants() {
        val classification = classifyRuntimeBehavior(
            label = "three-level generated parent id binding",
            desiredContract = {
                val root = saveRoot(newThreeLevelRoot("generated-parent-binding"))
                assertNotEquals(0L, root.id)

                val childIds = queryLongs(
                    "select `id` from `runtime_child` where `root_id` = ? order by `name`",
                    root.id
                )
                assertEquals(2, childIds.size)
                assertTrue(childIds.all { it != 0L }, "Every child should receive a generated id")
                assertEquals(2, countRows("select count(*) from `runtime_child` where `root_id` = ${root.id}"))

                childIds.forEach { childId ->
                    assertEquals(
                        2,
                        countRows("select count(*) from `runtime_grandchild` where `child_id` = $childId"),
                        "Every child should own two grandchildren through its generated id"
                    )
                }
                assertEquals(
                    4,
                    countRows(
                        "select count(*) from `runtime_grandchild` where `child_id` in (${childIds.joinToString()})"
                    )
                )
            },
            knownDefect = { failure ->
                failure.hasCause<jakarta.persistence.PersistenceException>() ||
                    failure.hasCause<HibernateException>() ||
                    failure is AssertionError
            }
        )

        assertSupported(classification)
    }

    @Test
    @DisplayName("reverse eager navigation from child to parent is supported")
    fun reverseEagerNavigationFromChildToParentIsSupported() {
        val classification = classifyRuntimeBehavior(
            label = "direct reverse eager child to parent navigation",
            desiredContract = {
                val root = saveReverseRoot(
                    RuntimeReverseRoot(name = "reverse-child-parent").apply {
                        children.add(RuntimeReverseChild(name = "reverse-child-parent-child"))
                    }
                )
                assertNotEquals(0L, root.id)

                val childIds = queryLongs(
                    "select `id` from `runtime_reverse_child` where `root_id` = ? order by `name`",
                    root.id
                )
                assertEquals(1, childIds.size)
                JpaUnitOfWork.reset()

                val loadedChild = reverseChildJpaRepository.findById(childIds.single()).orElseThrow()
                val loadedRoot = loadedChild.root ?: error("Reverse child should resolve its parent root")

                assertEquals(root.id, loadedRoot.id)
            },
            knownDefect = { failure ->
                failure.hasCause<jakarta.persistence.PersistenceException>() ||
                    failure.hasCause<HibernateException>() ||
                    failure is AssertionError
            }
        )

        assertSupported(classification)
    }

    /*
     * This is not a lazy-loading or isolation problem. The failure happens while
     * JpaUnitOfWork.save() refreshes the newly persisted root after flush().
     *
     * Direct Root -> Child -> Root eager reverse navigation is supported by the
     * contrast test above. The known defect starts with the nested refresh graph:
     *
     * Root -> Child -> Grandchild -> Child -> Root
     *
     * The forward one-to-many collections own the join columns, while the reverse
     * many-to-one associations are read-only eager navigations over those same
     * columns. CascadeType.ALL includes refresh, so refreshing the new root walks
     * the nested eager cycle and Hibernate reports FetchNotFoundException for the
     * root id while resolving that graph.
     */
    @Test
    @DisplayName("reverse eager navigation on nested entities is a known defect")
    fun reverseEagerNavigationOnNestedEntitiesIsKnownDefect() {
        val classification = classifyRuntimeBehavior(
            label = "three-level reverse eager navigation",
            desiredContract = {
                val root = saveReverseRoot(newThreeLevelReverseRoot("reverse-eager"))
                assertNotEquals(0L, root.id)

                val grandchildIds = queryLongs(
                    "select `id` from `runtime_reverse_grandchild` order by `name`"
                )
                assertEquals(4, grandchildIds.size)
                JpaUnitOfWork.reset()

                val loadedGrandchild = reverseGrandchildJpaRepository.findById(grandchildIds.first()).orElseThrow()
                val loadedChild = loadedGrandchild.child ?: error("Reverse grandchild should resolve its parent child")
                val loadedRoot = loadedChild.root ?: error("Reverse child should resolve its parent root")

                assertEquals(root.id, loadedRoot.id)
            },
            knownDefect = { failure ->
                failure.hasCause<jakarta.persistence.PersistenceException>() ||
                    failure.hasCause<HibernateException>() ||
                    failure is AssertionError
            }
        )

        assertKnownDefect(classification)
    }

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
                assertEquals(
                    1,
                    countRows("select count(*) from `runtime_child` where `name` = 'updated-child'")
                )
                assertEquals(
                    1,
                    countRows("select count(*) from `runtime_grandchild` where `name` = 'updated-grandchild'")
                )
            },
            knownDefect = { failure ->
                failure.hasCause<jakarta.persistence.PersistenceException>() ||
                    failure.hasCause<HibernateException>() ||
                    failure is AssertionError
            }
        )

        assertSupported(classification)
    }

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
                assertEquals(
                    3,
                    countRows("select count(*) from `runtime_grandchild`")
                )
            },
            knownDefect = { failure ->
                failure.hasCause<jakarta.persistence.PersistenceException>() ||
                    failure.hasCause<HibernateException>() ||
                    failure is AssertionError
            }
        )

        assertSupported(classification)
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
                assertEquals(
                    1,
                    countRows("select count(*) from `runtime_child`")
                )
                assertEquals(
                    2,
                    countRows("select count(*) from `runtime_grandchild`")
                )
            },
            knownDefect = { failure ->
                failure.hasCause<jakarta.persistence.PersistenceException>() ||
                    failure.hasCause<HibernateException>() ||
                    failure is AssertionError
            }
        )

        assertSupported(classification)
    }

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
                assertEquals(
                    3,
                    countRows("select count(*) from `runtime_grandchild`")
                )
                assertEquals(
                    1,
                    countRows("select count(*) from `runtime_grandchild` where `name` = 'clear-readd-new-grandchild'")
                )
            },
            knownDefect = { failure ->
                failure.hasCause<jakarta.persistence.PersistenceException>() ||
                    failure.hasCause<HibernateException>() ||
                    failure is AssertionError
            }
        )

        assertSupported(classification)
    }

    private fun saveRoot(root: RuntimeRoot): RuntimeRoot {
        unitOfWork.persist(root)
        unitOfWork.save()
        return root
    }

    private fun saveReverseRoot(root: RuntimeReverseRoot): RuntimeReverseRoot {
        unitOfWork.persist(root)
        unitOfWork.save()
        return root
    }

    private fun saveSafeReverseRoot(root: RuntimeSafeReverseRoot): RuntimeSafeReverseRoot {
        unitOfWork.persist(root)
        unitOfWork.save()
        return root
    }

    private fun countRows(sql: String): Int =
        requireNotNull(jdbcTemplate.queryForObject(sql, Int::class.java))

    private fun queryLongs(sql: String, vararg args: Any): List<Long> =
        jdbcTemplate.queryForList(sql, Long::class.java, *args).map { it.toLong() }

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

    private fun newThreeLevelReverseRoot(name: String): RuntimeReverseRoot =
        RuntimeReverseRoot(name = name).apply {
            children.add(RuntimeReverseChild(name = "$name-child-a").apply {
                grandchildren.add(RuntimeReverseGrandchild(name = "$name-grandchild-a1"))
                grandchildren.add(RuntimeReverseGrandchild(name = "$name-grandchild-a2"))
            })
            children.add(RuntimeReverseChild(name = "$name-child-b").apply {
                grandchildren.add(RuntimeReverseGrandchild(name = "$name-grandchild-b1"))
                grandchildren.add(RuntimeReverseGrandchild(name = "$name-grandchild-b2"))
            })
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

    private fun assertSupported(classification: RuntimeClassification) {
        assertEquals(RuntimeClassification.SUPPORTED, classification)
    }

    private fun assertKnownDefect(classification: RuntimeClassification) {
        assertEquals(RuntimeClassification.KNOWN_DEFECT, classification)
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

    private enum class RuntimeClassification {
        SUPPORTED,
        KNOWN_DEFECT
    }

    @SpringBootApplication
    @ComponentScan(basePackages = ["com.only4.cap4k.ddd", "com.only4.cap4k.ddd.runtime"])
    @EntityScan(basePackages = ["com.only4.cap4k.ddd", "com.only4.cap4k.ddd.runtime"])
    @EnableJpaRepositories(basePackages = ["com.only4.cap4k.ddd", "com.only4.cap4k.ddd.runtime"])
    class RuntimeTestApplication {
        @Bean
        fun snowflakeIdGenerator(): SnowflakeIdGenerator =
            SnowflakeIdGenerator(workerId = 1L, datacenterId = 1L)
                .also(SnowflakeIdentifierGenerator::configure)
    }
}

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

@Entity
@Table(name = "`runtime_reverse_root`")
open class RuntimeReverseRoot(id: Long = 0L, name: String = "") {
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "`root_id`", nullable = false)
    open var children: MutableList<RuntimeReverseChild> = mutableListOf()

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
@Table(name = "`runtime_reverse_child`")
open class RuntimeReverseChild(id: Long = 0L, name: String = "") {
    @ManyToOne(cascade = [], fetch = FetchType.EAGER)
    @JoinColumn(name = "`root_id`", nullable = false, insertable = false, updatable = false)
    open var root: RuntimeReverseRoot? = null

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "`child_id`", nullable = false)
    open var grandchildren: MutableList<RuntimeReverseGrandchild> = mutableListOf()

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
@Table(name = "`runtime_reverse_grandchild`")
open class RuntimeReverseGrandchild(id: Long = 0L, name: String = "") {
    @ManyToOne(cascade = [], fetch = FetchType.EAGER)
    @JoinColumn(name = "`child_id`", nullable = false, insertable = false, updatable = false)
    open var child: RuntimeReverseChild? = null

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

interface RuntimeRootJpaRepository :
    JpaRepository<RuntimeRoot, Long>,
    JpaSpecificationExecutor<RuntimeRoot>

interface RuntimeReverseChildJpaRepository : JpaRepository<RuntimeReverseChild, Long>

interface RuntimeReverseGrandchildJpaRepository : JpaRepository<RuntimeReverseGrandchild, Long>

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

data class CountRuntimeRootChildrenWholeLoadRequest(
    val rootId: Long
) : RequestParam<CountRuntimeRootChildrenResponse>

@Component
class CountRuntimeRootChildrenCommand : Command<CountRuntimeRootChildrenRequest, CountRuntimeRootChildrenResponse> {
    override fun exec(request: CountRuntimeRootChildrenRequest): CountRuntimeRootChildrenResponse {
        val root = RepositorySupervisor.instance.findOne(
            JpaPredicate.byId(RuntimeRoot::class.java, request.rootId)
        ) ?: error("RuntimeRoot not found: ${request.rootId}")

        return CountRuntimeRootChildrenResponse(root.children.size)
    }
}

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
