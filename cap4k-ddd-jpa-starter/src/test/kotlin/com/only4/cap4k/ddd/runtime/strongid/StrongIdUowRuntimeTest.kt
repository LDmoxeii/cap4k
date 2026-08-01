package com.only4.cap4k.ddd.runtime.strongid

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.application.JpaManagedFieldSet
import com.only4.cap4k.ddd.application.JpaPersistenceEnricher
import com.only4.cap4k.ddd.application.JpaPersistenceEnrichmentContext
import com.only4.cap4k.ddd.application.JpaEntityChange
import com.only4.cap4k.ddd.application.JpaEntityChangeType
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.ddd.core.domain.aggregate.impl.DefaultAggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.event.DomainEventManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.domain.managed.DefaultManagedFieldRegistry
import com.only4.cap4k.ddd.core.domain.managed.DefaultManagedEntityAdmissionCoordinator
import com.only4.cap4k.ddd.core.domain.managed.ManagedEntityAdmissionCoordinator
import com.only4.cap4k.ddd.core.domain.managed.ManagedEntityAdmissionKind
import com.only4.cap4k.ddd.core.domain.managed.ManagedEntityInitializer
import com.only4.cap4k.ddd.core.domain.managed.ManagedExplicitValuePolicy
import com.only4.cap4k.ddd.core.domain.managed.ManagedFieldBinding
import com.only4.cap4k.ddd.core.domain.managed.ManagedFieldCatalog
import com.only4.cap4k.ddd.core.domain.managed.ManagedFieldLifecycle
import com.only4.cap4k.ddd.core.domain.managed.ManagedFieldRegistry
import com.only4.cap4k.ddd.core.domain.managed.ManagedFieldRole
import com.only4.cap4k.ddd.core.domain.managed.ManagedFieldRuntimeSupport
import com.only4.cap4k.ddd.core.domain.managed.ManagedValueAuthority
import com.only4.cap4k.ddd.core.domain.managed.PersistenceParticipation
import com.only4.cap4k.ddd.core.domain.managed.StandardManagedEntityInitializer
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@DataJpaTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:strong-id-uow-runtime;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate=WARN",
    ]
)
@Import(StrongIdUowRuntimeTest.TestConfig::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StrongIdUowRuntimeTest {
    @Autowired
    lateinit var entityManager: EntityManager

    @Autowired
    lateinit var repository: StrongIdJpaRepository

    @Autowired
    lateinit var unitOfWork: JpaUnitOfWork

    @Autowired
    lateinit var admissionCoordinator: ManagedEntityAdmissionCoordinator

    @Autowired
    lateinit var auditEnricher: RecordingJpaAuditEnricher

    @Autowired
    lateinit var domainEvents: RecordingDomainEventManager

    @BeforeEach
    fun reset() {
        JpaUnitOfWork.reset()
        auditEnricher.reset()
        domainEvents.reset()
    }

    @Test
    fun `outer execute completes and persists generated root and child ids`() {
        val content = StrongContent.unassigned("uow-create").also {
            it.items += StrongContentItem.unassigned("first-child")
            it.items += StrongContentItem.unassigned("second-child")
        }
        var expectedItemIds: List<StrongContentItemId> = emptyList()

        unitOfWork.execute {
            admitGraph(content)
            unitOfWork.registerNew(content)
            assertTrue(content.hasAssignedId())
            assertTrue(content.items.all { it.hasAssignedId() })
            expectedItemIds = content.items.map { it.id }
        }

        entityManager.clear()
        unitOfWork.execute {
            val loaded = repository.findById(content.id).orElseThrow()
            assertEquals(content.id, loaded.id)
            assertEquals(expectedItemIds, loaded.items.map { it.id })
        }
    }

    @Test
    fun `factory participates in active Unit of Work without explicit save`() {
        val factorySupervisor = DefaultAggregateFactorySupervisor(
            factories = listOf(StrongContentFactory(admissionCoordinator)),
            persistenceIntents = unitOfWork,
            invocationScopeAccessor = InvocationScopeAccessor { InvocationKind.COMMAND },
            managedEntityAdmissionCoordinator = admissionCoordinator,
        ).apply { init() }
        lateinit var content: StrongContent
        var expectedItemIds: List<StrongContentItemId> = emptyList()

        unitOfWork.execute {
            content = factorySupervisor.create(
                StrongContentFactory.Payload(
                    title = "factory-create",
                    itemLabels = listOf("first-child", "second-child"),
                )
            )
            assertTrue(content.hasAssignedId())
            assertTrue(content.items.all { it.hasAssignedId() })
            expectedItemIds = content.items.map { it.id }
        }

        entityManager.clear()
        unitOfWork.execute {
            assertEquals(
                expectedItemIds,
                repository.findById(content.id).orElseThrow().items.map { it.id },
            )
        }
    }

    @Test
    fun `create followed by remove before synchronization folds to none and discards events`() {
        val content = StrongContent.unassigned("create-remove-none")

        unitOfWork.execute {
            admitGraph(content)
            unitOfWork.registerNew(content)
            domainEvents.attachFor(content)
            unitOfWork.registerDelete(content)
        }

        entityManager.clear()
        assertFalse(repository.existsById(content.id))
        assertEquals(listOf(content), domainEvents.discarded)
        assertEquals(0, domainEvents.releaseCalls)
    }

    @Test
    fun `context clears after completion and same thread after commit Command reentry fails`() {
        val observations = mutableListOf<String>()
        var afterCommitCommandFailure: Throwable? = null

        unitOfWork.execute {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun beforeCommit(readOnly: Boolean) {
                        observations += "beforeCommit:${unitOfWork.active}"
                    }

                    override fun afterCommit() {
                        observations += "afterCommit:${unitOfWork.active}"
                        afterCommitCommandFailure = runCatching {
                            unitOfWork.execute { Unit }
                        }.exceptionOrNull()
                    }

                    override fun afterCompletion(status: Int) {
                        observations += "afterCompletion:${unitOfWork.active}"
                    }
                },
            )
        }

        assertEquals(
            listOf(
                "beforeCommit:true",
                "afterCommit:true",
                "afterCompletion:false",
            ),
            observations,
        )
        assertTrue(afterCommitCommandFailure is IllegalStateException)
        assertTrue(afterCommitCommandFailure?.message.orEmpty().contains("STABLE"))
        assertFalse(unitOfWork.active)
    }

    @Test
    fun `before commit changes are stabilized and audited by the outer coordinator`() {
        val content = StrongContent(
            id = StrongContentId.parse("019c0000-0000-7000-8000-000000000022"),
            title = "before-commit-original",
            authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000023"),
            mediaProcessingTaskId = null,
        )
        repository.saveAndFlush(content)
        entityManager.clear()

        unitOfWork.execute {
            val loaded = repository.findById(content.id).orElseThrow()
            unitOfWork.observeRepositoryLoad(loaded)
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun beforeCommit(readOnly: Boolean) {
                        loaded.rename("changed-in-before-commit")
                    }
                },
            )
        }

        entityManager.clear()
        assertTrue(
            repository.findById(content.id).orElseThrow().title
                .startsWith("changed-in-before-commit|audit:"),
        )
    }

    @Test
    fun `rollback completion clears Unit of Work context`() {
        val failure = assertThrows(IllegalStateException::class.java) {
            unitOfWork.execute {
                throw IllegalStateException("rollback-cleanup")
            }
        }

        assertEquals("rollback-cleanup", failure.message)
        assertFalse(unitOfWork.active)
    }

    @Test
    fun `synthetic collection backref is governed by topology and not rejected as managed field mutation`() {
        val content = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.parse("019c0000-0000-7000-8000-000000000002"),
                title = "existing-root",
                authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000003"),
                mediaProcessingTaskId = null,
            )
        )
        entityManager.clear()
        lateinit var child: StrongContentItem

        unitOfWork.execute {
            val loaded = repository.findById(content.id).orElseThrow()
            unitOfWork.observeRepositoryLoad(loaded)
            child = StrongContentItem.unassigned("new-child")
            admissionCoordinator.admit(child, ManagedEntityAdmissionKind.OWNED_CHILD)
            loaded.items += child
        }
        assertTrue(child.hasAssignedId())

        entityManager.clear()
        unitOfWork.execute {
            assertTrue(repository.findById(content.id).orElseThrow().items.any { it.id == child.id })
        }
    }

    @Test
    fun `audit enrichment of a dirty candidate is persisted by the same stabilization flush`() {
        val content = StrongContent(
            id = StrongContentId.parse("019c0000-0000-7000-8000-000000000012"),
            title = "audit-before",
            authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000013"),
            mediaProcessingTaskId = null,
        )
        repository.saveAndFlush(content)
        entityManager.clear()

        unitOfWork.execute {
            val loaded = repository.findById(content.id).orElseThrow()
            unitOfWork.observeRepositoryLoad(loaded)
            loaded.rename("audit-business-change")
        }

        entityManager.clear()
        assertTrue(
            repository.findById(content.id).orElseThrow().title
                .startsWith("audit-business-change|audit:"),
        )
    }

    @Test
    fun `clean loaded aggregate does not invoke persistence enrichment`() {
        val content = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.parse("019c0000-0000-7000-8000-000000000042"),
                title = "clean-read",
                authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000043"),
                mediaProcessingTaskId = null,
            )
        )
        entityManager.clear()
        auditEnricher.reset()

        unitOfWork.execute {
            val loaded = repository.findById(content.id).orElseThrow()
            unitOfWork.observeRepositoryLoad(loaded)
            assertEquals("clean-read", loaded.title)
        }

        assertTrue(auditEnricher.seen.isEmpty())
    }

    @Test
    fun `delete-only change exposes no enrichment handles`() {
        val content = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.parse("019c0000-0000-7000-8000-000000000044"),
                title = "delete-only",
                authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000045"),
                mediaProcessingTaskId = null,
            )
        )
        entityManager.clear()
        auditEnricher.reset()

        unitOfWork.execute {
            val loaded = repository.findById(content.id).orElseThrow()
            unitOfWork.observeRepositoryLoad(loaded)
            unitOfWork.registerDelete(loaded)
        }

        assertTrue(auditEnricher.seen.isEmpty())
    }

    @Test
    fun `enricher cannot mutate a provider property outside supplied footprints`() {
        val content = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.parse("019c0000-0000-7000-8000-000000000046"),
                title = "unauthorized-before",
                authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000047"),
                mediaProcessingTaskId = null,
            )
        )
        entityManager.clear()
        auditEnricher.reset()
        auditEnricher.mutateUnauthorized = true

        val failure = assertThrows(IllegalStateException::class.java) {
            unitOfWork.execute {
                val loaded = repository.findById(content.id).orElseThrow()
                unitOfWork.observeRepositoryLoad(loaded)
                loaded.rename("candidate-change")
            }
        }

        assertTrue(failure.message.orEmpty().contains("unauthorized provider properties"))
        assertTrue(failure.message.orEmpty().contains("authorId"))
        assertTrue(failure.message.orEmpty().contains(RecordingJpaAuditEnricher.QUALIFIER))
    }

    @Test
    fun `enricher cannot load and mutate another aggregate outside its supplied footprints`() {
        val candidate = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.parse("019c0000-0000-7000-8000-000000000049"),
                title = "candidate-before",
                authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000050"),
                mediaProcessingTaskId = null,
            )
        )
        val other = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.parse("019c0000-0000-7000-8000-000000000051"),
                title = "other-before",
                authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000052"),
                mediaProcessingTaskId = null,
            )
        )
        entityManager.clear()
        auditEnricher.reset()
        auditEnricher.duringEnrichment = {
            repository.findById(other.id).orElseThrow().rename("other-illicit-change")
        }

        val failure = assertThrows(IllegalStateException::class.java) {
            unitOfWork.execute {
                val loaded = repository.findById(candidate.id).orElseThrow()
                unitOfWork.observeRepositoryLoad(loaded)
                loaded.rename("candidate-change")
            }
        }

        assertTrue(failure.message.orEmpty().contains("unauthorized provider properties"))
        assertTrue(failure.message.orEmpty().contains("title"))
        assertTrue(failure.message.orEmpty().contains("allowed=[]"))
    }

    @Test
    fun `enricher cannot load and mutate another aggregate collection outside its supplied footprints`() {
        val candidate = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.parse("019c0000-0000-7000-8000-000000000053"),
                title = "candidate-collection-before",
                authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000054"),
                mediaProcessingTaskId = null,
            )
        )
        val other = StrongContent(
            id = StrongContentId.parse("019c0000-0000-7000-8000-000000000055"),
            title = "other-collection-before",
            authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000056"),
            mediaProcessingTaskId = null,
        ).also { root ->
            root.items += StrongContentItem(
                StrongContentItemId.parse("019c0000-0000-7000-8000-000000000057"),
                "other-child",
            )
        }
        repository.saveAndFlush(other)
        entityManager.clear()
        auditEnricher.reset()
        auditEnricher.duringEnrichment = {
            repository.findById(other.id).orElseThrow().items.clear()
        }

        val failure = assertThrows(IllegalStateException::class.java) {
            unitOfWork.execute {
                val loaded = repository.findById(candidate.id).orElseThrow()
                unitOfWork.observeRepositoryLoad(loaded)
                loaded.rename("candidate-collection-change")
            }
        }

        assertTrue(failure.message.orEmpty().contains("unauthorized provider properties"))
        assertTrue(failure.message.orEmpty().contains("items"))
        assertTrue(failure.message.orEmpty().contains("allowed=[]"))
    }

    @Test
    fun `child-only enricher handles cannot authorize mutation of a clean aggregate root`() {
        val content = StrongContent.unassigned("guarded-root").also {
            it.items += StrongContentItem.unassigned("guarded-child")
        }
        unitOfWork.execute {
            admitGraph(content)
            unitOfWork.registerNew(content)
        }
        auditEnricher.reset()
        entityManager.clear()
        auditEnricher.mutateUnauthorized = true

        val failure = assertThrows(IllegalStateException::class.java) {
            unitOfWork.execute {
                val loaded = repository.findById(content.id).orElseThrow()
                unitOfWork.observeRepositoryLoad(loaded)
                loaded.items.single().relabel("child-only-change")
            }
        }

        assertTrue(failure.message.orEmpty().contains("unauthorized provider properties"))
        assertTrue(failure.message.orEmpty().contains("authorId"))
        assertTrue(failure.message.orEmpty().contains("allowed=[]"))
    }

    @Test
    fun `final stabilization detects orphan and later child update in one round`() {
        val content = StrongContent.unassigned("baseline-root").also {
            it.items += StrongContentItem.unassigned("baseline-remove")
            it.items += StrongContentItem.unassigned("baseline-update")
        }
        lateinit var removedId: StrongContentItemId
        lateinit var updatedId: StrongContentItemId
        unitOfWork.execute {
            admitGraph(content)
            unitOfWork.registerNew(content)
            removedId = content.items.first().id
            updatedId = content.items.last().id
        }
        auditEnricher.reset()
        entityManager.clear()

        unitOfWork.execute {
            val loaded = repository.findById(content.id).orElseThrow()
            unitOfWork.observeRepositoryLoad(loaded)
            loaded.items.removeAt(0)
            loaded.items.single().relabel("baseline-updated-after-flush")
        }

        entityManager.clear()
        unitOfWork.execute {
            val loaded = repository.findById(content.id).orElseThrow()
            assertFalse(loaded.items.any { it.id == removedId })
            assertEquals("baseline-updated-after-flush", loaded.items.single { it.id == updatedId }.label)
        }
        assertEquals(
            1,
            auditEnricher.seen.count {
                it.entity === auditEnricher.entitiesById[removedId] &&
                    it.type == JpaEntityChangeType.DELETE
            },
            "seen=${auditEnricher.seen.map { candidate -> "${candidate.entity.javaClass.simpleName}:${candidate.type}" }}",
        )
        assertEquals(
            1,
            auditEnricher.seen.count {
                it.entity === auditEnricher.entitiesById[updatedId] &&
                    it.type == JpaEntityChangeType.UPDATE
            },
            "seen=${auditEnricher.seen.map { candidate -> "${candidate.entity.javaClass.simpleName}:${candidate.type}" }}",
        )
        assertEquals(1, auditEnricher.timestamps.distinct().size)
    }

    @Test
    fun `uow rejects a new graph that bypassed admission without allocating ids`() {
        val content = StrongContent.unassigned("bypassed-admission")

        val failure = assertThrows(IllegalStateException::class.java) {
            unitOfWork.execute {
                unitOfWork.registerNew(content)
            }
        }

        assertTrue(failure.message.orEmpty().contains("managed identifier id is absent"))
        assertFalse(content.hasAssignedId())
    }

    private fun admitGraph(content: StrongContent) {
        admissionCoordinator.admit(content, ManagedEntityAdmissionKind.AGGREGATE_ROOT)
        content.items.forEach { child ->
            admissionCoordinator.admit(child, ManagedEntityAdmissionKind.OWNED_CHILD)
        }
    }

    @SpringBootApplication
    @EntityScan(basePackageClasses = [StrongContent::class])
    @EnableJpaRepositories(basePackageClasses = [StrongIdJpaRepository::class])
    class TestApplication

    class TestConfig {
        @Bean
        fun domainEventManager(): RecordingDomainEventManager = RecordingDomainEventManager()

        @Bean
        fun managedFieldCatalog(): ManagedFieldCatalog = object : ManagedFieldCatalog {
            override val bindings: List<ManagedFieldBinding> = listOf(
                identifierBinding(
                    StrongContent::class,
                    StrongContentId::class,
                    "019c0000-0000-7000-8001-",
                    StrongContentId::parse,
                ),
                identifierBinding(
                    StrongContentItem::class,
                    StrongContentItemId::class,
                    "019c0000-0000-7000-8002-",
                    StrongContentItemId::parse,
                ),
                enrichmentBinding(StrongContent::class, "title"),
                enrichmentBinding(StrongContentItem::class, "label"),
                enrichmentBinding(
                    StrongContent::class,
                    "authorId",
                    StrongAuthorId::class,
                    OtherQualifierJpaEnricher.QUALIFIER,
                ),
            )
        }

        @Bean
        fun standardManagedEntityInitializer(): ManagedEntityInitializer = StandardManagedEntityInitializer()

        @Bean
        fun managedFieldRegistry(
            catalog: ManagedFieldCatalog,
            initializers: List<ManagedEntityInitializer>,
        ): ManagedFieldRegistry = DefaultManagedFieldRegistry(listOf(catalog), initializers)

        @Bean
        fun managedEntityAdmissionCoordinator(registry: ManagedFieldRegistry): ManagedEntityAdmissionCoordinator =
            DefaultManagedEntityAdmissionCoordinator(
                registry,
                ExecutionContextAccessor { ExecutionContextSnapshot.EMPTY },
            )

        @Bean
        fun jpaUnitOfWork(
            domainEventManager: DomainEventManager,
            managedFieldRegistry: ManagedFieldRegistry,
            managedEntityAdmissionCoordinator: ManagedEntityAdmissionCoordinator,
            persistenceEnrichers: List<JpaPersistenceEnricher>,
        ): JpaUnitOfWork = JpaUnitOfWork(
            domainEventManager = domainEventManager,
            managedFieldRegistry = managedFieldRegistry,
            managedEntityAdmissionCoordinator = managedEntityAdmissionCoordinator,
            persistenceEnrichers = persistenceEnrichers,
        )

        @Bean
        fun auditEnricher(): RecordingJpaAuditEnricher = RecordingJpaAuditEnricher()

        @Bean
        fun otherQualifierJpaEnricher(): OtherQualifierJpaEnricher = OtherQualifierJpaEnricher()

        private fun enrichmentBinding(
            entityType: kotlin.reflect.KClass<*>,
            fieldName: String,
            targetType: kotlin.reflect.KClass<*> = String::class,
            qualifier: String = RecordingJpaAuditEnricher.QUALIFIER,
        ): ManagedFieldBinding = ManagedFieldBinding(
            entityType = entityType,
            fieldName = fieldName,
            persistencePropertyName = fieldName,
            columnName = fieldName,
            targetType = targetType,
            nullable = false,
            policyKey = "enrichment.test.$fieldName",
            role = ManagedFieldRole.ENRICHMENT,
            explicitValue = ManagedExplicitValuePolicy.OVERWRITE,
            lifecycles = setOf(ManagedFieldLifecycle.PERSISTENCE_ENRICHMENT),
            handlerQualifier = qualifier,
            handlerSlot = null,
            semanticValueType = targetType,
            valueAdapterQualifier = null,
            persistence = PersistenceParticipation(
                ManagedValueAuthority.MANAGED_HANDLER,
                ManagedValueAuthority.MANAGED_HANDLER,
            ),
        )

        private var rootSequence = 0L
        private var childSequence = 0L

        private fun <ID : Any> identifierBinding(
            entityType: kotlin.reflect.KClass<*>,
            idType: kotlin.reflect.KClass<ID>,
            prefix: String,
            parse: (String) -> ID,
        ): ManagedFieldBinding {
            val allocate = {
                val sequence = if (entityType == StrongContent::class) ++rootSequence else ++childSequence
                parse(prefix + sequence.toString(16).padStart(12, '0'))
            }
            return ManagedFieldBinding(
                entityType = entityType,
                fieldName = "id",
                persistencePropertyName = "id",
                columnName = "id",
                targetType = idType,
                nullable = false,
                policyKey = "identifier.uuid7",
                role = ManagedFieldRole.IDENTIFIER,
                explicitValue = ManagedExplicitValuePolicy.PRESERVE_IF_VALID,
                lifecycles = setOf(ManagedFieldLifecycle.ENTITY_ADMISSION),
                handlerQualifier = "identifier.uuid7",
                handlerSlot = null,
                semanticValueType = idType,
                valueAdapterQualifier = null,
                persistence = PersistenceParticipation(
                    ManagedValueAuthority.FRAMEWORK,
                    ManagedValueAuthority.NONE,
                ),
                runtimeSupport = ManagedFieldRuntimeSupport.ApplicationIdentifier(
                    isAbsent = { it == null },
                    allocateTarget = allocate,
                    validateTarget = { value -> require(idType.isInstance(value)) },
                ),
            )
        }
    }
}

class RecordingDomainEventManager : DomainEventManager {
    private val pendingByRoot = java.util.IdentityHashMap<Any, Int>()
    val discarded = mutableListOf<Any>()
    var releaseCalls: Int = 0
        private set

    fun attachFor(root: Any) {
        pendingByRoot[root] = (pendingByRoot[root] ?: 0) + 1
    }

    override fun release(entities: Set<Any>) {
        releaseCalls++
        entities.forEach(pendingByRoot::remove)
    }

    override fun pendingCount(): Int = pendingByRoot.values.sum()

    override fun discard(entity: Any) {
        pendingByRoot.remove(entity)
        discarded += entity
    }

    fun reset() {
        pendingByRoot.clear()
        discarded.clear()
        releaseCalls = 0
    }
}

class RecordingJpaAuditEnricher : JpaPersistenceEnricher {
    override val qualifiers: Set<String> = setOf(QUALIFIER)
    val seen = mutableListOf<JpaEntityChange>()
    val timestamps = mutableListOf<java.time.Instant>()
    val entitiesById = mutableMapOf<StrongContentItemId, StrongContentItem>()
    var mutateUnauthorized: Boolean = false
    var duringEnrichment: (() -> Unit)? = null

    override fun enrich(
        change: com.only4.cap4k.ddd.application.JpaAggregateChange,
        context: JpaPersistenceEnrichmentContext,
        fields: JpaManagedFieldSet,
    ) {
        val candidates = change.entityChanges
        seen += candidates
        timestamps += context.timestamp
        duringEnrichment?.also { callback ->
            duringEnrichment = null
            callback()
        }
        candidates.map { it.entity }.filterIsInstance<StrongContentItem>().forEach { item ->
            if (item.hasAssignedId()) entitiesById[item.id] = item
        }
        if (mutateUnauthorized) {
            (change.root as? StrongContent)?.let { root ->
                StrongContent::class.java.getDeclaredField("authorId").apply { isAccessible = true }
                    .set(root, StrongAuthorId("019c0000-0000-7000-8000-000000000048"))
            }
        }
        fields.filter { it.operation == com.only4.cap4k.ddd.application.JpaManagedOperation.UPDATE }
            .flatMap { it.handles }
            .filter { it.readTarget() == "audit-business-change" || it.readTarget() == "changed-in-before-commit" }
            .forEach { handle ->
                handle.assignSemantic("${handle.readTarget()}|audit:${context.timestamp.toEpochMilli()}")
            }
    }

    fun reset() {
        seen.clear()
        timestamps.clear()
        entitiesById.clear()
        mutateUnauthorized = false
        duringEnrichment = null
    }

    companion object {
        const val QUALIFIER = "enrichment.test"
    }
}

class OtherQualifierJpaEnricher : JpaPersistenceEnricher {
    override val qualifiers: Set<String> = setOf(QUALIFIER)

    override fun enrich(
        change: com.only4.cap4k.ddd.application.JpaAggregateChange,
        context: JpaPersistenceEnrichmentContext,
        fields: JpaManagedFieldSet,
    ) = Unit

    companion object {
        const val QUALIFIER = "enrichment.other"
    }
}

private class StrongContentFactory(
    private val admissionCoordinator: ManagedEntityAdmissionCoordinator,
) : AggregateFactory<StrongContentFactory.Payload, StrongContent> {
    override fun create(entityPayload: Payload): StrongContent =
        StrongContent.unassigned(entityPayload.title).also { content ->
            entityPayload.itemLabels.forEach { label ->
                val child = StrongContentItem.unassigned(label)
                admissionCoordinator.admit(child, ManagedEntityAdmissionKind.OWNED_CHILD)
                content.items += child
            }
        }

    data class Payload(
        val title: String,
        val itemLabels: List<String>,
    ) : AggregatePayload<StrongContent>
}
