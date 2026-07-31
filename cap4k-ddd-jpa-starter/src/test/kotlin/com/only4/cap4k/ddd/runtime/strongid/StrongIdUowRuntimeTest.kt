package com.only4.cap4k.ddd.runtime.strongid

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.application.JpaPersistenceAuditEnricher
import com.only4.cap4k.ddd.application.JpaEntityChange
import com.only4.cap4k.ddd.application.JpaEntityChangeType
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.ddd.core.domain.aggregate.impl.DefaultAggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.event.DomainEventManager
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdAccessor
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdCatalog
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedGeneratedOwnIdRegistry
import com.only4.cap4k.ddd.core.domain.id.readInitializedOrNull
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
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
import org.springframework.core.Ordered
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
    lateinit var auditEnricher: RecordingJpaAuditEnricher

    @Autowired
    lateinit var domainEvents: RecordingDomainEventManager

    @Autowired
    lateinit var auditInvocationLog: AuditInvocationLog

    @BeforeEach
    fun reset() {
        JpaUnitOfWork.reset()
        auditEnricher.reset()
        domainEvents.reset()
        auditInvocationLog.entries.clear()
    }

    @Test
    fun `outer execute completes and persists generated root and child ids`() {
        val content = StrongContent.unassigned("uow-create").also {
            it.items += StrongContentItem.unassigned("first-child")
            it.items += StrongContentItem.unassigned("second-child")
        }
        var expectedItemIds: List<StrongContentItemId> = emptyList()

        unitOfWork.execute {
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
            factories = listOf(StrongContentFactory()),
            persistenceIntents = unitOfWork,
            invocationScopeAccessor = InvocationScopeAccessor { InvocationKind.COMMAND },
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
    fun `existing root synchronization completes newly attached owned child`() {
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
    fun `audit enrichment runs each enricher across all aggregates before the next enricher`() {
        val first = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.parse("019c0000-0000-7000-8000-000000000032"),
                title = "audit-order-first",
                authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000033"),
                mediaProcessingTaskId = null,
            )
        )
        val second = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.parse("019c0000-0000-7000-8000-000000000034"),
                title = "audit-order-second",
                authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000035"),
                mediaProcessingTaskId = null,
            )
        )
        entityManager.clear()
        auditInvocationLog.entries.clear()

        unitOfWork.execute {
            val firstLoaded = repository.findById(first.id).orElseThrow()
            val secondLoaded = repository.findById(second.id).orElseThrow()
            unitOfWork.observeRepositoryLoad(firstLoaded)
            unitOfWork.observeRepositoryLoad(secondLoaded)
            firstLoaded.rename("audit-order-first-changed")
            secondLoaded.rename("audit-order-second-changed")
        }

        assertEquals(
            listOf(
                "first:${first.id}",
                "first:${second.id}",
                "second:${first.id}",
                "second:${second.id}",
            ),
            auditInvocationLog.entries,
        )
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

    @SpringBootApplication
    @EntityScan(basePackageClasses = [StrongContent::class])
    @EnableJpaRepositories(basePackageClasses = [StrongIdJpaRepository::class])
    class TestApplication

    class TestConfig {
        @Bean
        fun domainEventManager(): RecordingDomainEventManager = RecordingDomainEventManager()

        @Bean
        fun generatedOwnIdCatalog(): GeneratedOwnIdCatalog = object : GeneratedOwnIdCatalog {
            override val accessors: List<GeneratedOwnIdAccessor<*, *>> = listOf(
                StrongContentGeneratedOwnIdAccessor(),
                StrongContentItemGeneratedOwnIdAccessor(),
            )
        }

        @Bean
        fun generatedOwnIdRegistry(catalog: GeneratedOwnIdCatalog): GeneratedOwnIdRegistry =
            MapBackedGeneratedOwnIdRegistry(listOf(catalog))

        @Bean
        fun jpaUnitOfWork(
            domainEventManager: DomainEventManager,
            generatedOwnIdRegistry: GeneratedOwnIdRegistry,
            auditEnrichers: List<JpaPersistenceAuditEnricher>,
        ): JpaUnitOfWork = JpaUnitOfWork(
            domainEventManager = domainEventManager,
            generatedOwnIdRegistry = generatedOwnIdRegistry,
            auditEnrichers = auditEnrichers,
        )

        @Bean
        fun auditEnricher(): RecordingJpaAuditEnricher = RecordingJpaAuditEnricher()

        @Bean
        fun auditInvocationLog(): AuditInvocationLog = AuditInvocationLog()

        @Bean
        fun firstOrderedAuditEnricher(log: AuditInvocationLog): FirstOrderedAuditEnricher =
            FirstOrderedAuditEnricher(log)

        @Bean
        fun secondOrderedAuditEnricher(log: AuditInvocationLog): SecondOrderedAuditEnricher =
            SecondOrderedAuditEnricher(log)
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

class RecordingJpaAuditEnricher : JpaPersistenceAuditEnricher {
    val seen = mutableListOf<JpaEntityChange>()
    val timestamps = mutableListOf<java.time.Instant>()
    val entitiesById = mutableMapOf<StrongContentItemId, StrongContentItem>()

    override fun enrich(
        changeSet: com.only4.cap4k.ddd.application.JpaAggregateChange,
        context: com.only4.cap4k.ddd.application.JpaPersistenceAuditContext,
    ) {
        val candidates = changeSet.entityChanges
        seen += candidates
        timestamps += context.auditTime
        candidates.map { it.entity }.filterIsInstance<StrongContentItem>().forEach { item ->
            if (item.hasAssignedId()) entitiesById[item.id] = item
        }
        candidates
            .filter { it.type == JpaEntityChangeType.UPDATE }
            .map { it.entity }
            .filterIsInstance<StrongContent>()
            .filter { it.title == "audit-business-change" || it.title == "changed-in-before-commit" }
            .forEach { it.rename("${it.title}|audit:${context.auditTime.toEpochMilli()}") }
    }

    fun reset() {
        seen.clear()
        timestamps.clear()
        entitiesById.clear()
    }
}

class AuditInvocationLog {
    val entries = mutableListOf<String>()
}

class FirstOrderedAuditEnricher(
    private val log: AuditInvocationLog,
) : JpaPersistenceAuditEnricher, Ordered {
    override fun getOrder(): Int = 100

    override fun enrich(
        changeSet: com.only4.cap4k.ddd.application.JpaAggregateChange,
        context: com.only4.cap4k.ddd.application.JpaPersistenceAuditContext,
    ) {
        (changeSet.root as? StrongContent)?.let { root -> log.entries += "first:${root.id}" }
    }
}

class SecondOrderedAuditEnricher(
    private val log: AuditInvocationLog,
) : JpaPersistenceAuditEnricher, Ordered {
    override fun getOrder(): Int = 200

    override fun enrich(
        changeSet: com.only4.cap4k.ddd.application.JpaAggregateChange,
        context: com.only4.cap4k.ddd.application.JpaPersistenceAuditContext,
    ) {
        (changeSet.root as? StrongContent)?.let { root -> log.entries += "second:${root.id}" }
    }
}

private class StrongContentFactory : AggregateFactory<StrongContentFactory.Payload, StrongContent> {
    override fun create(entityPayload: Payload): StrongContent =
        StrongContent.unassigned(entityPayload.title).also { content ->
            entityPayload.itemLabels.forEach { label ->
                content.items += StrongContentItem.unassigned(label)
            }
        }

    data class Payload(
        val title: String,
        val itemLabels: List<String>,
    ) : AggregatePayload<StrongContent>
}

private class StrongContentGeneratedOwnIdAccessor : GeneratedOwnIdAccessor<StrongContent, StrongContentId> {
    override val entityType = StrongContent::class
    override val label = "StrongContent.id"
    private val idField = StrongContent::class.java.getDeclaredField("id").apply { isAccessible = true }
    private var sequence = 0L

    override fun current(entity: StrongContent): StrongContentId? = readInitializedOrNull { entity.id }

    override fun assign(entity: StrongContent, id: StrongContentId) {
        idField.set(entity, id)
    }

    override fun next(): StrongContentId =
        StrongContentId.parse("019c0000-0000-7000-8001-${(++sequence).toString(16).padStart(12, '0')}")
}

private class StrongContentItemGeneratedOwnIdAccessor :
    GeneratedOwnIdAccessor<StrongContentItem, StrongContentItemId> {
    override val entityType = StrongContentItem::class
    override val label = "StrongContentItem.id"
    private val idField = StrongContentItem::class.java.getDeclaredField("id").apply { isAccessible = true }
    private var sequence = 0L

    override fun current(entity: StrongContentItem): StrongContentItemId? = readInitializedOrNull { entity.id }

    override fun assign(entity: StrongContentItem, id: StrongContentItemId) {
        idField.set(entity, id)
    }

    override fun next(): StrongContentItemId =
        StrongContentItemId.parse("019c0000-0000-7000-8002-${(++sequence).toString(16).padStart(12, '0')}")
}
