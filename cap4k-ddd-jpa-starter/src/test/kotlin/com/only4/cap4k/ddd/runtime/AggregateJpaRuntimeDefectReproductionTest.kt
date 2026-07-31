package com.only4.cap4k.ddd.runtime

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.CommandUnitOfWorkCoordinator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.core.application.command.CommandSupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList
import com.only4.cap4k.ddd.core.domain.id.BuiltInIdentifierStrategies
import com.only4.cap4k.ddd.core.domain.id.IdentifierCapability
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategy
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor
import com.only4.cap4k.ddd.domain.distributed.SnowflakeIdentifierGenerator
import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeIdGenerator
import com.only4.cap4k.ddd.domain.repo.AbstractJpaRepository
import com.only4.cap4k.ddd.domain.repo.JpaPredicate
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.ConstraintMode
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.PersistenceContext
import jakarta.persistence.Table
import jakarta.persistence.Transient
import jakarta.persistence.Version
import org.hibernate.HibernateException
import org.hibernate.PersistentObjectException
import org.hibernate.Session
import org.hibernate.annotations.GenericGenerator
import org.hibernate.id.IdentifierGenerationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
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
import kotlin.reflect.KClass

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
        "logging.level.org.hibernate=WARN"
    ]
)
@DisplayName("Aggregate JPA runtime defect reproduction")
class AggregateJpaRuntimeDefectReproductionTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    @Qualifier("jpaUnitOfWork")
    private lateinit var unitOfWork: JpaUnitOfWork

    @Autowired
    private lateinit var rootJpaRepository: RuntimeRootJpaRepository

    @Autowired
    @Qualifier("defaultCommandSupervisor")
    private lateinit var commandSupervisor: CommandSupervisor

    @Autowired
    @Qualifier("defaultRepositorySupervisor")
    private lateinit var repositorySupervisor: RepositorySupervisor

    @Autowired
    private lateinit var reverseChildJpaRepository: RuntimeReverseChildJpaRepository

    @Autowired
    private lateinit var reverseGrandchildJpaRepository: RuntimeReverseGrandchildJpaRepository

    @Autowired
    private lateinit var fkMirrorChildJpaRepository: RuntimeFkMirrorChildJpaRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.update("delete from `runtime_entrusted_grandchild`")
        jdbcTemplate.update("delete from `runtime_entrusted_child`")
        jdbcTemplate.update("delete from `runtime_entrusted_root`")
        jdbcTemplate.update("delete from `runtime_safe_reverse_grandchild`")
        jdbcTemplate.update("delete from `runtime_safe_reverse_child`")
        jdbcTemplate.update("delete from `runtime_safe_reverse_root`")
        jdbcTemplate.update("delete from `runtime_reverse_grandchild`")
        jdbcTemplate.update("delete from `runtime_reverse_child`")
        jdbcTemplate.update("delete from `runtime_reverse_root`")
        jdbcTemplate.update("delete from `runtime_fk_mirror_child`")
        jdbcTemplate.update("delete from `runtime_fk_mirror_root`")
        jdbcTemplate.update("delete from `runtime_grandchild`")
        jdbcTemplate.update("delete from `runtime_child`")
        jdbcTemplate.update("delete from `runtime_root`")
        JpaUnitOfWork.reset()
    }

    @Test
    @DisplayName("fixture boots with real cap4k runtime beans")
    fun fixtureBootsWithRealRuntimeBeans() {
        assertNotNull(unitOfWork)
        assertNotNull(commandSupervisor)
        assertNotNull(repositorySupervisor)
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
    @DisplayName("command handler repository load can access lazy aggregate children")
    fun commandHandlerRepositoryLoadCanAccessLazyAggregateChildren() {
        val root = saveRoot(RuntimeRoot(name = "lazy-command").apply {
            children.add(RuntimeChild(name = "lazy-command-child"))
        })
        JpaUnitOfWork.reset()

        val response = commandSupervisor.send(CountRuntimeRootChildrenCommand(root.id))

        assertEquals(1, response.childCount)
    }

    @Test
    @DisplayName("command Unit of Work rejects adoption of an unrelated external transaction")
    fun commandUnitOfWorkRejectsExternalTransactionAdoption() {
        val root = saveRoot(RuntimeRoot(name = "lazy-transactional-request").apply {
            children.add(RuntimeChild(name = "lazy-transactional-request-child"))
        })
        JpaUnitOfWork.reset()

        assertThrows(IllegalStateException::class.java) {
            TransactionTemplate(transactionManager).execute {
                commandSupervisor.send(CountRuntimeRootChildrenCommand(root.id))
            }
        }
    }

    @Test
    @DisplayName("Mediator Command observes repository load and completes write Unit of Work")
    fun mediatorCommandObservesRepositoryLoad() {
        val root = saveRoot(RuntimeRoot(name = "command-auto-enroll"))
        JpaUnitOfWork.reset()

        Mediator.commands.send(RenameRuntimeRootCommand(root.id, "command-auto-enroll-updated"))

        assertEquals(
            1,
            countRows("select count(*) from `runtime_root` where `id` = ? and `name` = ?", root.id, "command-auto-enroll-updated"),
        )
    }

    @Test
    @DisplayName("nested Mediator Command reuses the same physical Hibernate Session")
    fun nestedMediatorCommandReusesCurrentUnitOfWork() {
        val root = saveRoot(RuntimeRoot(name = "nested-command"))
        JpaUnitOfWork.reset()

        val observation = Mediator.commands.send(
            NestedRenameRuntimeRootCommand(root.id, "nested-command-updated")
        )

        assertTrue(observation.outerActive)
        assertTrue(observation.innerActive)
        assertEquals(observation.beforeSessionIdentity, observation.innerSessionIdentity)
        assertEquals(observation.beforeSessionIdentity, observation.afterSessionIdentity)
        assertEquals(
            1,
            countRows("select count(*) from `runtime_root` where `id` = ? and `name` = ?", root.id, "nested-command-updated"),
        )
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
                    failure.hasCause<HibernateException>()
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
                    failure.hasCause<HibernateException>()
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
                    failure.hasCause<HibernateException>()
            }
        )

        assertSupported(classification)
    }

    @Test
    @DisplayName("read only scalar fk can coexist with read only inverse many to one on the same column")
    fun readOnlyScalarFkCanCoexistWithReadOnlyInverseManyToOneOnTheSameColumn() {
        val classification = classifyRuntimeBehavior(
            label = "read only scalar fk plus inverse many to one",
            desiredContract = {
                val root = RuntimeFkMirrorRoot(name = "fk-mirror-root").apply {
                    children.add(RuntimeFkMirrorChild(name = "fk-mirror-child"))
                }
                unitOfWork.execute {
                    unitOfWork.registerNew(root)
                }
                assertNotEquals(0L, root.id)
                val childId = queryLong("select `id` from `runtime_fk_mirror_child` where `name` = ?", "fk-mirror-child")
                JpaUnitOfWork.reset()

                val loadedChild = fkMirrorChildJpaRepository.findById(childId).orElseThrow()
                assertEquals(root.id, loadedChild.rootId)
                assertNotNull(loadedChild.root)
                assertEquals(root.id, loadedChild.root!!.id)
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
    @DisplayName("reverse eager navigation on nested entities remains a known defect with create intent")
    fun reverseEagerNavigationOnNestedEntitiesRemainsAKnownDefectWithCreateIntent() {
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
                    failure.hasCause<HibernateException>()
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
                unitOfWork.execute {
                    val loaded = rootJpaRepository.findById(root.id).orElseThrow()
                    loaded.children.first().name = "updated-child"
                    loaded.children.first().grandchildren.first().name = "updated-grandchild"
                    unitOfWork.observeRepositoryLoad(loaded)
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
    @DisplayName("detached root deletion fails before flush")
    fun detachedRootDeletionFailsBeforeFlush() {
        val root = saveRoot(RuntimeRoot(name = "proxy-conflict"))
        JpaUnitOfWork.reset()

        val error = assertThrows(IllegalStateException::class.java) {
            unitOfWork.execute {
                val proxy = rootJpaRepository.getReferenceById(root.id)
                val detached = RuntimeRoot(id = root.id, name = "detached-conflict")
                unitOfWork.observeRepositoryLoad(proxy)
                unitOfWork.registerDelete(detached)
            }
        }

        assertTrue(error.message!!.contains("currently managed root"))
        assertEquals(1, countRows("select count(*) from `runtime_root` where `id` = ?", root.id))
    }

    @Test
    @DisplayName("managed child collection removes one grandchild through orphan removal")
    fun managedChildCollectionRemovesOneGrandchildThroughOrphanRemoval() {
        val root = saveRoot(newThreeLevelRoot("remove-grandchild"))
        JpaUnitOfWork.reset()

        val classification = classifyRuntimeBehavior(
            label = "three-level managed grandchild orphan removal",
            desiredContract = {
                unitOfWork.execute {
                    val loaded = rootJpaRepository.findById(root.id).orElseThrow()
                    loaded.children.first().grandchildren.removeAt(0)
                    unitOfWork.observeRepositoryLoad(loaded)
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
                unitOfWork.execute {
                    val loaded = rootJpaRepository.findById(root.id).orElseThrow()
                    loaded.children.removeAt(0)
                    unitOfWork.observeRepositoryLoad(loaded)
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
                unitOfWork.execute {
                    val loaded = rootJpaRepository.findById(root.id).orElseThrow()
                    val firstChild = loaded.children.first()
                    firstChild.grandchildren.clear()
                    firstChild.grandchildren.add(RuntimeGrandchild(name = "clear-readd-new-grandchild"))
                    unitOfWork.observeRepositoryLoad(loaded)
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

    @Test
    @DisplayName("root-only create completes entrusted identity version and forward joins")
    fun rootOnlyCreateCompletesEntrustedIdentityVersionAndForwardJoins() {
        val root = newEntrustedRoot("entrusted-create")
        val children = root.children.toList()
        val grandchildrenByChild = children.associateWith { it.grandchildren.toList() }

        unitOfWork.execute {
            unitOfWork.registerNew(root)
        }

        val rootId = checkNotNull(root.id)
        assertNotNull(root.version)
        children.forEach { child ->
            val childId = checkNotNull(child.id)
            assertNotNull(child.version)
            assertEquals(
                rootId,
                queryLong("select `root_id` from `runtime_entrusted_child` where `id` = ?", childId)
            )
            grandchildrenByChild.getValue(child).forEach { grandchild ->
                val grandchildId = checkNotNull(grandchild.id)
                assertNotNull(grandchild.version)
                assertEquals(
                    childId,
                    queryLong(
                        "select `child_id` from `runtime_entrusted_grandchild` where `id` = ?",
                        grandchildId
                    )
                )
            }
        }
        assertEquals(0, importedKeyCount("runtime_entrusted_child"))
        assertEquals(0, importedKeyCount("runtime_entrusted_grandchild"))
        assertEquals(
            listOf(
                "entrusted-create-grandchild-a1",
                "entrusted-create-grandchild-a2",
                "entrusted-create-grandchild-b1"
            ),
            queryEntrustedGrandchildren(rootId).map { it.name }.sorted()
        )
    }

    @Test
    @DisplayName("existing root save completes a newly attached entrusted child")
    fun existingRootSaveCompletesANewlyAttachedEntrustedChild() {
        val root = newEntrustedRoot("entrusted-existing")
        unitOfWork.execute {
            unitOfWork.registerNew(root)
        }
        val rootId = checkNotNull(root.id)
        JpaUnitOfWork.reset()

        lateinit var newChild: RuntimeEntrustedChild
        lateinit var newGrandchild: RuntimeEntrustedGrandchild
        unitOfWork.execute {
            val loaded = entityManager.find(RuntimeEntrustedRoot::class.java, rootId)
            newGrandchild = RuntimeEntrustedGrandchild("entrusted-existing-grandchild-new")
            newChild = RuntimeEntrustedChild("entrusted-existing-child-new").apply {
                grandchildren.add(newGrandchild)
            }
            loaded.children.add(newChild)

            unitOfWork.observeRepositoryLoad(loaded)
        }

        val childId = checkNotNull(newChild.id)
        assertNotNull(newChild.version)
        assertNotNull(newGrandchild.id)
        assertNotNull(newGrandchild.version)
        assertEquals(
            rootId,
            queryLong("select `root_id` from `runtime_entrusted_child` where `id` = ?", childId)
        )
        assertEquals(
            childId,
            queryLong(
                "select `child_id` from `runtime_entrusted_grandchild` where `id` = ?",
                checkNotNull(newGrandchild.id)
            )
        )
    }

    @Test
    @DisplayName("owned child scalar update advances child version without forcing root version")
    fun ownedChildScalarUpdateDoesNotForceRootVersion() {
        val root = newEntrustedRoot("child-version-boundary")
        val child = root.children.first()
        unitOfWork.execute {
            unitOfWork.registerNew(root)
        }
        val rootId = checkNotNull(root.id)
        val childId = checkNotNull(child.id)
        val rootVersion = checkNotNull(root.version)
        val childVersion = checkNotNull(child.version)
        JpaUnitOfWork.reset()

        unitOfWork.execute {
            val loaded = entityManager.find(RuntimeEntrustedRoot::class.java, rootId)
            loaded.children.first { it.id == childId }.rename("child-version-boundary-updated")
            unitOfWork.observeRepositoryLoad(loaded)
        }

        assertEquals(
            rootVersion,
            queryLong("select `version` from `runtime_entrusted_root` where `id` = ?", rootId),
        )
        assertTrue(
            queryLong("select `version` from `runtime_entrusted_child` where `id` = ?", childId) > childVersion,
        )
    }

    @Test
    @DisplayName("outer rollback leaves provider-assigned state unallocated and removes rows")
    fun outerRollbackLeavesProviderAssignedStateUnallocatedAndRemovesRows() {
        val root = newEntrustedRoot("entrusted-rollback")
        val children = root.children.toList()
        val grandchildren = children.flatMap { it.grandchildren }

        val failure = assertThrows(IllegalStateException::class.java) {
            unitOfWork.execute {
                unitOfWork.registerNew(root)

                assertNull(root.id)
                assertNull(root.version)
                children.forEach { child ->
                    assertNull(child.id)
                    assertNull(child.version)
                }
                grandchildren.forEach { grandchild ->
                    assertNull(grandchild.id)
                    assertNull(grandchild.version)
                }
                throw IllegalStateException("rollback entrusted graph")
            }
        }

        assertEquals("rollback entrusted graph", failure.message)
        assertNull(root.id)
        assertNull(root.version)
        assertTrue(children.all { it.id == null && it.version == null })
        assertTrue(grandchildren.all { it.id == null && it.version == null })
        assertEquals(
            0,
            countRows("select count(*) from `runtime_entrusted_root` where `name` = ?", "entrusted-rollback")
        )
        assertEquals(
            0,
            countRows("select count(*) from `runtime_entrusted_child` where `name` like ?", "entrusted-rollback%")
        )
        assertEquals(
            0,
            countRows("select count(*) from `runtime_entrusted_grandchild` where `name` like ?", "entrusted-rollback%")
        )
    }

    private fun saveRoot(root: RuntimeRoot): RuntimeRoot {
        unitOfWork.execute {
            unitOfWork.registerNew(root)
        }
        return root
    }

    private fun saveReverseRoot(root: RuntimeReverseRoot): RuntimeReverseRoot {
        unitOfWork.execute {
            unitOfWork.registerNew(root)
        }
        return root
    }

    private fun saveSafeReverseRoot(root: RuntimeSafeReverseRoot): RuntimeSafeReverseRoot {
        unitOfWork.execute {
            unitOfWork.registerNew(root)
        }
        return root
    }

    private fun countRows(sql: String, vararg args: Any): Int =
        requireNotNull(jdbcTemplate.queryForObject(sql, Int::class.java, *args))

    private fun queryLong(sql: String, vararg args: Any): Long =
        requireNotNull(jdbcTemplate.queryForObject(sql, Long::class.java, *args))

    private fun queryLongs(sql: String, vararg args: Any): List<Long> =
        jdbcTemplate.queryForList(sql, Long::class.java, *args).map { it.toLong() }

    private fun importedKeyCount(tableName: String): Int =
        requireNotNull(jdbcTemplate.dataSource).connection.use { connection ->
            connection.metaData.getTables(null, null, tableName, null).use { tables ->
                check(tables.next()) { "JDBC metadata did not find table $tableName" }
            }
            connection.metaData.getImportedKeys(null, null, tableName).use { importedKeys ->
                var count = 0
                while (importedKeys.next()) count++
                count
            }
        }

    private fun queryEntrustedGrandchildren(rootId: Long): List<RuntimeEntrustedGrandchild> =
        requireNotNull(TransactionTemplate(transactionManager).execute {
            val builder = entityManager.criteriaBuilder
            val query = builder.createQuery(RuntimeEntrustedGrandchild::class.java)
            val root = query.from(RuntimeEntrustedRoot::class.java)
            val children = root.join<RuntimeEntrustedRoot, RuntimeEntrustedChild>("_children")
            val grandchildren = children.join<RuntimeEntrustedChild, RuntimeEntrustedGrandchild>("_grandchildren")
            query.select(grandchildren).where(builder.equal(root.get<Long>("id"), rootId))
            entityManager.createQuery(query).resultList
        })

    private fun newEntrustedRoot(name: String): RuntimeEntrustedRoot =
        RuntimeEntrustedRoot(name).apply {
            children.add(RuntimeEntrustedChild("$name-child-a").apply {
                grandchildren.add(RuntimeEntrustedGrandchild("$name-grandchild-a1"))
                grandchildren.add(RuntimeEntrustedGrandchild("$name-grandchild-a2"))
            })
            children.add(RuntimeEntrustedChild("$name-child-b").apply {
                grandchildren.add(RuntimeEntrustedGrandchild("$name-grandchild-b1"))
            })
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

        @Bean
        fun snowflakeIdentifierStrategy(
            snowflakeIdGenerator: SnowflakeIdGenerator
        ): IdentifierStrategy = TestSnowflakeIdentifierStrategy(snowflakeIdGenerator)

        @Bean
        fun runtimeRootFactory(): AggregateFactory<RuntimeRootPayload, RuntimeRoot> = RuntimeRootFactory()
    }
}

data class RuntimeRootPayload(
    val name: String,
) : AggregatePayload<RuntimeRoot>

class RuntimeRootFactory : AggregateFactory<RuntimeRootPayload, RuntimeRoot> {
    override fun create(entityPayload: RuntimeRootPayload): RuntimeRoot = RuntimeRoot(name = entityPayload.name)
}

private class TestSnowflakeIdentifierStrategy(
    private val snowflakeIdGenerator: SnowflakeIdGenerator,
) : IdentifierStrategy {
    override val name: String = BuiltInIdentifierStrategies.SNOWFLAKE
    override val capabilities: Set<IdentifierCapability> =
        setOf(IdentifierCapability.ENTITY_ID_PREASSIGNMENT)

    override fun supports(type: KClass<*>): Boolean =
        type == Long::class || type == String::class

    override fun <T : Any> next(type: KClass<T>): T {
        require(supports(type))
        val value: Any = when (type) {
            Long::class -> snowflakeIdGenerator.nextId()
            String::class -> snowflakeIdGenerator.nextId().toString()
            else -> error("unsupported")
        }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean =
        value == null || value == 0L || value == "" || value == "0"
}

@Entity
@Table(name = "`runtime_entrusted_root`")
open class RuntimeEntrustedRoot protected constructor() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`id`", nullable = false)
    open var id: Long? = null
        protected set

    @Version
    @Column(name = "`version`", nullable = false)
    open var version: Long? = null
        protected set

    @Column(name = "`name`", nullable = false)
    open lateinit var name: String
        protected set

    @OneToMany(
        cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE],
        fetch = FetchType.LAZY,
        orphanRemoval = true
    )
    @JoinColumn(
        name = "`root_id`",
        nullable = false,
        foreignKey = ForeignKey(value = ConstraintMode.NO_CONSTRAINT)
    )
    private var _children: MutableList<RuntimeEntrustedChild> = mutableListOf()

    @get:Transient
    val children: OwnedEntityList<RuntimeEntrustedChild>
        get() = OwnedEntityList.of(_children, RuntimeEntrustedChild::class, "RuntimeEntrustedRoot.children")

    constructor(name: String) : this() {
        this.name = name
    }

}

@Entity
@Table(name = "`runtime_entrusted_child`")
open class RuntimeEntrustedChild protected constructor() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`id`", nullable = false)
    open var id: Long? = null
        protected set

    @Version
    @Column(name = "`version`", nullable = false)
    open var version: Long? = null
        protected set

    @Column(name = "`name`", nullable = false)
    open lateinit var name: String
        protected set

    @OneToMany(
        cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE],
        fetch = FetchType.LAZY,
        orphanRemoval = true
    )
    @JoinColumn(
        name = "`child_id`",
        nullable = false,
        foreignKey = ForeignKey(value = ConstraintMode.NO_CONSTRAINT)
    )
    private var _grandchildren: MutableList<RuntimeEntrustedGrandchild> = mutableListOf()

    @get:Transient
    val grandchildren: OwnedEntityList<RuntimeEntrustedGrandchild>
        get() = OwnedEntityList.of(
            _grandchildren,
            RuntimeEntrustedGrandchild::class,
            "RuntimeEntrustedChild.grandchildren"
        )

    constructor(name: String) : this() {
        this.name = name
    }

    fun rename(name: String) {
        this.name = name
    }
}

@Entity
@Table(name = "`runtime_entrusted_grandchild`")
open class RuntimeEntrustedGrandchild protected constructor() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`id`", nullable = false)
    open var id: Long? = null
        protected set

    @Version
    @Column(name = "`version`", nullable = false)
    open var version: Long? = null
        protected set

    @Column(name = "`name`", nullable = false)
    open lateinit var name: String
        protected set

    constructor(name: String) : this() {
        this.name = name
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

@Entity
@Table(name = "`runtime_fk_mirror_root`")
open class RuntimeFkMirrorRoot(id: Long = 0L, name: String = "") {
    @OneToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE], fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "`root_id`", nullable = false)
    open var children: MutableList<RuntimeFkMirrorChild> = mutableListOf()

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
@Table(name = "`runtime_fk_mirror_child`")
open class RuntimeFkMirrorChild(id: Long = 0L, rootId: Long = 0L, name: String = "") {
    @ManyToOne(cascade = [], fetch = FetchType.LAZY)
    @JoinColumn(name = "`root_id`", nullable = false, insertable = false, updatable = false)
    open var root: RuntimeFkMirrorRoot? = null

    @Id
    @GeneratedValue(generator = SNOWFLAKE_GENERATOR)
    @GenericGenerator(name = SNOWFLAKE_GENERATOR, strategy = SNOWFLAKE_GENERATOR)
    @Column(name = "`id`", insertable = false, updatable = false)
    open var id: Long = id
        protected set

    @Column(name = "`root_id`", insertable = false, updatable = false)
    open var rootId: Long = rootId
        protected set

    @Column(name = "`name`", nullable = false)
    open var name: String = name
}

interface RuntimeRootJpaRepository :
    JpaRepository<RuntimeRoot, Long>,
    JpaSpecificationExecutor<RuntimeRoot>

interface RuntimeReverseChildJpaRepository : JpaRepository<RuntimeReverseChild, Long>

interface RuntimeReverseGrandchildJpaRepository : JpaRepository<RuntimeReverseGrandchild, Long>

interface RuntimeFkMirrorChildJpaRepository : JpaRepository<RuntimeFkMirrorChild, Long>

@Repository
class RuntimeRootRepository(
    rootJpaRepository: RuntimeRootJpaRepository
) : AbstractJpaRepository<RuntimeRoot, Long>(rootJpaRepository, rootJpaRepository)

data class CountRuntimeRootChildrenCommand(
    val rootId: Long
) : Command<CountRuntimeRootChildrenResponse>

data class CountRuntimeRootChildrenResponse(
    val childCount: Int
)

@Component
class CountRuntimeRootChildrenCommandHandler(
    @param:Qualifier("defaultRepositorySupervisor")
    private val repositorySupervisor: RepositorySupervisor
) : CommandHandler<CountRuntimeRootChildrenCommand, CountRuntimeRootChildrenResponse> {
    override fun handle(command: CountRuntimeRootChildrenCommand): CountRuntimeRootChildrenResponse {
        val root = repositorySupervisor.findOne(
            JpaPredicate.byId(RuntimeRoot::class.java, command.rootId)
        ) ?: error("RuntimeRoot not found: ${command.rootId}")

        return CountRuntimeRootChildrenResponse(root.children.size)
    }
}

data class RenameRuntimeRootCommand(
    val rootId: Long,
    val name: String,
) : Command<RenameRuntimeRootResult>

data class RenameRuntimeRootResult(
    val active: Boolean,
    val sessionIdentity: Int,
)

@Component
class RenameRuntimeRootCommandHandler(
    @param:Qualifier("defaultRepositorySupervisor")
    private val repositorySupervisor: RepositorySupervisor,
    private val unitOfWork: CommandUnitOfWorkCoordinator,
    private val entityManager: EntityManager,
) : CommandHandler<RenameRuntimeRootCommand, RenameRuntimeRootResult> {
    override fun handle(command: RenameRuntimeRootCommand): RenameRuntimeRootResult {
        val root = repositorySupervisor.findOne(
            JpaPredicate.byId(RuntimeRoot::class.java, command.rootId)
        ) ?: error("RuntimeRoot not found: ${command.rootId}")
        root.name = command.name
        return RenameRuntimeRootResult(
            active = unitOfWork.active,
            sessionIdentity = System.identityHashCode(entityManager.unwrap(Session::class.java)),
        )
    }
}

data class NestedRenameRuntimeRootCommand(
    val rootId: Long,
    val name: String,
) : Command<NestedRenameRuntimeRootResult>

data class NestedRenameRuntimeRootResult(
    val outerActive: Boolean,
    val innerActive: Boolean,
    val beforeSessionIdentity: Int,
    val innerSessionIdentity: Int,
    val afterSessionIdentity: Int,
)

@Component
class NestedRenameRuntimeRootCommandHandler(
    private val unitOfWork: CommandUnitOfWorkCoordinator,
    private val entityManager: EntityManager,
) : CommandHandler<NestedRenameRuntimeRootCommand, NestedRenameRuntimeRootResult> {
    override fun handle(command: NestedRenameRuntimeRootCommand): NestedRenameRuntimeRootResult {
        val before = System.identityHashCode(entityManager.unwrap(Session::class.java))
        val inner = Mediator.commands.send(RenameRuntimeRootCommand(command.rootId, command.name))
        val after = System.identityHashCode(entityManager.unwrap(Session::class.java))
        return NestedRenameRuntimeRootResult(
            outerActive = unitOfWork.active,
            innerActive = inner.active,
            beforeSessionIdentity = before,
            innerSessionIdentity = inner.sessionIdentity,
            afterSessionIdentity = after,
        )
    }
}
