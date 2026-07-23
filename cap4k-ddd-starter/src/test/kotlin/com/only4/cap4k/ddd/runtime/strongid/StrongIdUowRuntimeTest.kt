package com.only4.cap4k.ddd.runtime.strongid

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.core.application.PersistIntent
import com.only4.cap4k.ddd.core.application.UnitOfWorkInterceptor
import com.only4.cap4k.ddd.core.domain.repo.AggregateLoadPlan
import com.only4.cap4k.ddd.core.domain.repo.PersistListenerManager
import com.only4.cap4k.ddd.core.domain.repo.PersistType
import jakarta.persistence.EntityManager
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
    fun `create intent completes root strong id before save`() {
        val content = StrongContent.unassigned("uow-create")

        unitOfWork.persist(content, PersistIntent.CREATE)

        assertTrue(content.hasAssignedId())
        unitOfWork.save()
        assertTrue(repository.findById(content.id).isPresent)
    }

    @Test
    fun `existing enrollment completes new owned child before flush`() {
        val content = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.new(),
                title = "existing-root",
                authorId = StrongAuthorId.new(),
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
    fun `clean existing enrollment does not emit update listener`() {
        val content = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.new(),
                title = "clean-root",
                authorId = StrongAuthorId.new(),
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

    @SpringBootApplication
    @EntityScan(basePackageClasses = [StrongContent::class])
    @EnableJpaRepositories(basePackageClasses = [StrongIdJpaRepository::class])
    class TestApplication

    class TestConfig {
        @Bean
        fun persistListenerManager(): RecordingPersistListenerManager = RecordingPersistListenerManager()

        @Bean
        fun jpaUnitOfWork(persistListenerManager: PersistListenerManager): JpaUnitOfWork =
            JpaUnitOfWork(emptyList<UnitOfWorkInterceptor>(), persistListenerManager, true)
    }
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
