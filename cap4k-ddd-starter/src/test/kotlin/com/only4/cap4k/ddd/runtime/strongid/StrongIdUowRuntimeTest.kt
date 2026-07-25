package com.only4.cap4k.ddd.runtime.strongid

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.core.application.PersistIntent
import com.only4.cap4k.ddd.core.application.UnitOfWorkInterceptor
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdAccessor
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdCatalog
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedGeneratedOwnIdRegistry
import com.only4.cap4k.ddd.core.domain.id.readInitializedOrNull
import com.only4.cap4k.ddd.core.domain.repo.AggregateLoadPlan
import com.only4.cap4k.ddd.core.domain.repo.PersistListenerManager
import com.only4.cap4k.ddd.core.domain.repo.PersistType
import jakarta.persistence.EntityManager
import org.hibernate.Hibernate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
class StrongIdUowRuntimeTest {
    @Autowired
    lateinit var entityManager: EntityManager

    @Autowired
    lateinit var repository: StrongIdJpaRepository

    @Autowired
    lateinit var persistListenerManager: RecordingPersistListenerManager

    @Autowired
    lateinit var unitOfWork: JpaUnitOfWork

    @BeforeEach
    fun reset() {
        JpaUnitOfWork.reset()
        JpaUnitOfWork.fixAopWrapper(unitOfWork)
        persistListenerManager.clear()
    }

    @Test
    fun `CREATE persist completes and reloads generated root and children`() {
        val content = StrongContent.unassigned("uow-create").also {
            it.items += StrongContentItem.unassigned("first-child")
            it.items += StrongContentItem.unassigned("second-child")
        }

        unitOfWork.persist(content, PersistIntent.CREATE)

        assertTrue(content.hasAssignedId())
        assertTrue(content.items.all { it.hasAssignedId() })
        val assignedRootId = content.id
        val assignedChildIds = content.items.map { it.id }

        unitOfWork.save()
        entityManager.clear()
        val loaded = repository.findById(assignedRootId).orElseThrow()
        assertEquals(assignedRootId, loaded.id)
        assertEquals(assignedChildIds, loaded.items.map { it.id })
    }

    @Test
    fun `existing enrollment completes new owned child before flush`() {
        val content = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.parse("019c0000-0000-7000-8000-000000000002"),
                title = "existing-root",
                authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000003"),
                mediaProcessingTaskId = null,
            )
        )
        entityManager.clear()
        val loaded = repository.findById(content.id).orElseThrow()
        unitOfWork.observeRepositoryLoad(loaded, AggregateLoadPlan.WHOLE_AGGREGATE)
        loaded.items += StrongContentItem.unassigned("new-child")

        unitOfWork.persist(loaded)
        unitOfWork.save()

        assertTrue(loaded.items.single().hasAssignedId())
    }

    @Test
    fun `existing enrollment accepts uninitialized strong id proxy`() {
        val content = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.parse("019c0000-0000-7000-8000-000000000002"),
                title = "proxy-root",
                authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000003"),
                mediaProcessingTaskId = null,
            )
        )
        entityManager.clear()
        val reference = repository.getReferenceById(content.id)
        assertFalse(Hibernate.isInitialized(reference))

        unitOfWork.persist(reference)
        unitOfWork.save()

        assertTrue(repository.findById(content.id).isPresent)
    }

    @Test
    fun `existing enrollment completes owned child reached through initialized proxy implementation`() {
        val content = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.parse("019c0000-0000-7000-8000-000000000002"),
                title = "initialized-proxy-root",
                authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000003"),
                mediaProcessingTaskId = null,
            )
        )
        entityManager.clear()
        val reference = repository.getReferenceById(content.id)
        Hibernate.initialize(reference)
        val implementation = Hibernate.unproxy(reference) as StrongContent
        val child = StrongContentItem.unassigned("proxy-child")
        implementation.items += child

        unitOfWork.persist(reference)

        assertTrue(child.hasAssignedId())
        unitOfWork.save()
        assertTrue(repository.findById(content.id).orElseThrow().items.any { it.id == child.id })
    }

    @Test
    fun `clean existing enrollment does not emit update listener`() {
        val content = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.parse("019c0000-0000-7000-8000-000000000002"),
                title = "clean-root",
                authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000003"),
                mediaProcessingTaskId = null,
            )
        )
        entityManager.clear()
        val loaded = repository.findById(content.id).orElseThrow()
        unitOfWork.observeRepositoryLoad(loaded, AggregateLoadPlan.WHOLE_AGGREGATE)

        unitOfWork.persist(loaded)
        unitOfWork.save()

        assertFalse(
            persistListenerManager.changes.any { (entity, type) ->
                entity === loaded && type == PersistType.UPDATE
            }
        )
    }

    @Test
    fun `clean detached existing enrollment does not emit update listener`() {
        val content = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.parse("019c0000-0000-7000-8000-000000000002"),
                title = "clean-detached-root",
                authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000003"),
                mediaProcessingTaskId = null,
            )
        )
        entityManager.clear()
        val loaded = repository.findById(content.id).orElseThrow()
        unitOfWork.observeRepositoryLoad(loaded, AggregateLoadPlan.WHOLE_AGGREGATE)
        entityManager.detach(loaded)

        unitOfWork.persist(loaded)
        unitOfWork.save()

        assertFalse(
            persistListenerManager.changes.any { (_, type) -> type == PersistType.UPDATE }
        )
    }

    @Test
    fun `dirty loaded existing enrollment emits update listener`() {
        val content = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.parse("019c0000-0000-7000-8000-000000000002"),
                title = "original-title",
                authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000003"),
                mediaProcessingTaskId = null,
            )
        )
        entityManager.clear()
        val loaded = repository.findById(content.id).orElseThrow()
        unitOfWork.observeRepositoryLoad(loaded, AggregateLoadPlan.WHOLE_AGGREGATE)
        StrongContent::class.java.getDeclaredField("title").apply {
            isAccessible = true
            set(loaded, "changed-title")
        }

        unitOfWork.persist(loaded)
        unitOfWork.save()

        assertTrue(
            persistListenerManager.changes.any { (entity, type) ->
                entity === loaded && type == PersistType.UPDATE
            }
        )
    }

    @Test
    fun `dirty managed proxy emits update listener for initialized implementation`() {
        val content = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.parse("019c0000-0000-7000-8000-000000000002"),
                title = "proxy-original-title",
                authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000003"),
                mediaProcessingTaskId = null,
            )
        )
        entityManager.clear()
        val reference = repository.getReferenceById(content.id)
        val implementation = Hibernate.unproxy(reference) as StrongContent
        StrongContent::class.java.getDeclaredField("title").apply {
            isAccessible = true
            set(implementation, "proxy-changed-title")
        }

        unitOfWork.persist(reference)
        unitOfWork.save()

        assertTrue(
            persistListenerManager.changes.any { (entity, type) ->
                entity === implementation && type == PersistType.UPDATE
            }
        )
    }

    @Test
    fun `dirty detached existing enrollment emits update listener for managed merge result`() {
        val content = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.parse("019c0000-0000-7000-8000-000000000002"),
                title = "detached-original-title",
                authorId = StrongAuthorId("019c0000-0000-7000-8000-000000000003"),
                mediaProcessingTaskId = null,
            )
        )
        entityManager.clear()
        val loaded = repository.findById(content.id).orElseThrow()
        unitOfWork.observeRepositoryLoad(loaded, AggregateLoadPlan.WHOLE_AGGREGATE)
        StrongContent::class.java.getDeclaredField("title").apply {
            isAccessible = true
            set(loaded, "detached-changed-title")
        }
        entityManager.detach(loaded)

        unitOfWork.persist(loaded)
        unitOfWork.save()

        val updated = persistListenerManager.changes.single { (_, type) -> type == PersistType.UPDATE }.first
        assertFalse(updated === loaded)
        assertEquals(loaded.id, (updated as StrongContent).id)
    }

    @SpringBootApplication
    @EntityScan(basePackageClasses = [StrongContent::class])
    @EnableJpaRepositories(basePackageClasses = [StrongIdJpaRepository::class])
    class TestApplication

    class TestConfig {
        @Bean
        fun persistListenerManager(): RecordingPersistListenerManager = RecordingPersistListenerManager()

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
            persistListenerManager: PersistListenerManager,
            generatedOwnIdRegistry: GeneratedOwnIdRegistry,
        ): JpaUnitOfWork = JpaUnitOfWork(
            emptyList<UnitOfWorkInterceptor>(),
            persistListenerManager,
            true,
            generatedOwnIdRegistry,
        )
    }
}

private class StrongContentGeneratedOwnIdAccessor : GeneratedOwnIdAccessor<StrongContent, StrongContentId> {
    override val entityType = StrongContent::class
    override val label = "StrongContent.id"
    private val idField = StrongContent::class.java.getDeclaredField("id").apply { isAccessible = true }
    private var sequence = 0L

    override fun current(entity: StrongContent): StrongContentId? =
        readInitializedOrNull { entity.id }

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

    override fun current(entity: StrongContentItem): StrongContentItemId? =
        readInitializedOrNull { entity.id }

    override fun assign(entity: StrongContentItem, id: StrongContentItemId) {
        idField.set(entity, id)
    }

    override fun next(): StrongContentItemId =
        StrongContentItemId.parse("019c0000-0000-7000-8002-${(++sequence).toString(16).padStart(12, '0')}")
}

class RecordingPersistListenerManager : PersistListenerManager {
    val changes: MutableList<Pair<Any, PersistType>> = mutableListOf()

    override fun <Entity : Any> onChange(aggregate: Entity, type: PersistType) {
        changes += aggregate to type
    }

    fun clear() {
        changes.clear()
    }
}
