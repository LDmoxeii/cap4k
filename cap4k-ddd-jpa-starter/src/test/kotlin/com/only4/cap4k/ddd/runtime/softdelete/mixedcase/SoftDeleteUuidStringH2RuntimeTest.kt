package com.only4.cap4k.ddd.runtime.softdelete.mixedcase

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.ddd.core.domain.aggregate.impl.DefaultAggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.event.DomainEventManager
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdAccessor
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdCatalog
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedGeneratedOwnIdRegistry
import com.only4.cap4k.ddd.core.domain.id.StrongId
import com.only4.cap4k.ddd.core.domain.id.StrongIds
import com.only4.cap4k.ddd.core.domain.id.readInitializedOrNull
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.Table
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.Where
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.io.Serializable

private const val NIL_UUID_TEXT = "00000000-0000-0000-0000-000000000000"

@DataJpaTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:soft-delete-uuid-string-runtime;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate=WARN",
    ]
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SoftDeleteUuidStringH2RuntimeTest.TestConfig::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SoftDeleteUuidStringH2RuntimeTest {
    @Autowired
    private lateinit var repository: MixedCaseUuidStringRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var unitOfWork: JpaUnitOfWork

    @BeforeEach
    fun resetUnitOfWork() {
        JpaUnitOfWork.reset()
    }

    @Test
    fun `quoted mixed case UUID7 string lifecycle assigns id before save and soft deletes physically`() {
        val factorySupervisor = DefaultAggregateFactorySupervisor(
            factories = listOf(MixedCaseUuidStringFactory()),
            persistenceIntents = unitOfWork,
            invocationScopeAccessor = InvocationScopeAccessor { InvocationKind.COMMAND },
        ).apply { init() }

        lateinit var entity: MixedCaseUuidStringEntity
        unitOfWork.execute {
            entity = factorySupervisor.create(
                MixedCaseUuidStringFactory.Payload(name = "mixed-case-h2")
            )
            assertTrue(entity.hasAssignedId())
        }
        val id = entity.id
        assertEquals(NIL_UUID_TEXT, entity.deleted)
        entityManager.clear()

        assertEquals(
            MixedCasePhysicalRow(id = id.value, deleted = NIL_UUID_TEXT),
            physicalRow(id.value),
        )

        unitOfWork.execute {
            unitOfWork.registerDelete(repository.findById(id).orElseThrow())
        }
        entityManager.clear()

        assertTrue(repository.findAll().isEmpty())
        assertEquals(
            MixedCasePhysicalRow(id = id.value, deleted = id.value),
            physicalRow(id.value),
        )
    }

    private fun physicalRow(id: String): MixedCasePhysicalRow =
        jdbcTemplate.queryForObject(
            """select "StrongId", "DeletedMarker" from "MixedCaseUuidString" where "StrongId" = ?""",
            { resultSet, _ ->
                MixedCasePhysicalRow(
                    id = resultSet.getString("StrongId"),
                    deleted = resultSet.getString("DeletedMarker"),
                )
            },
            id,
        ) ?: error("quoted mixed-case UUID string row was not retained")

    @SpringBootApplication
    @EntityScan(basePackageClasses = [MixedCaseUuidStringEntity::class])
    @EnableJpaRepositories(basePackageClasses = [MixedCaseUuidStringRepository::class])
    class TestApplication

    class TestConfig {
        @Bean
        fun domainEventManager(): DomainEventManager = object : DomainEventManager {
            override fun release(entities: Set<Any>) = Unit
        }

        @Bean
        fun generatedOwnIdCatalog(): GeneratedOwnIdCatalog = MixedCaseUuidStringGeneratedOwnIdCatalog()

        @Bean
        fun generatedOwnIdRegistry(catalog: GeneratedOwnIdCatalog): GeneratedOwnIdRegistry =
            MapBackedGeneratedOwnIdRegistry(listOf(catalog))

        @Bean
        fun jpaUnitOfWork(
            domainEventManager: DomainEventManager,
            generatedOwnIdRegistry: GeneratedOwnIdRegistry,
        ): JpaUnitOfWork = JpaUnitOfWork(
            domainEventManager = domainEventManager,
            generatedOwnIdRegistry = generatedOwnIdRegistry,
        )
    }
}

private data class MixedCasePhysicalRow(
    val id: String,
    val deleted: String,
)

private class MixedCaseUuidStringFactory :
    AggregateFactory<MixedCaseUuidStringFactory.Payload, MixedCaseUuidStringEntity> {
    override fun create(entityPayload: Payload): MixedCaseUuidStringEntity =
        MixedCaseUuidStringEntity.unassigned(entityPayload.name)

    data class Payload(
        val name: String,
    ) : AggregatePayload<MixedCaseUuidStringEntity>
}

private class MixedCaseUuidStringGeneratedOwnIdCatalog : GeneratedOwnIdCatalog {
    override val accessors: List<GeneratedOwnIdAccessor<*, *>> = listOf(
        MixedCaseUuidStringGeneratedOwnIdAccessor()
    )
}

private class MixedCaseUuidStringGeneratedOwnIdAccessor :
    GeneratedOwnIdAccessor<MixedCaseUuidStringEntity, MixedCaseUuidStringId> {
    override val entityType = MixedCaseUuidStringEntity::class
    override val label = "MixedCaseUuidStringEntity.id"
    private val idField = MixedCaseUuidStringEntity::class.java.getDeclaredField("id").apply {
        isAccessible = true
    }
    private var sequence = 0L

    override fun current(entity: MixedCaseUuidStringEntity): MixedCaseUuidStringId? =
        readInitializedOrNull { entity.id }

    override fun assign(entity: MixedCaseUuidStringEntity, id: MixedCaseUuidStringId) {
        idField.set(entity, id)
    }

    override fun next(): MixedCaseUuidStringId =
        MixedCaseUuidStringId.parse(
            "019c0000-0000-7000-8003-${(++sequence).toString(16).padStart(12, '0')}"
        )
}

@Embeddable
class MixedCaseUuidStringId protected constructor() : StrongId<String>, Serializable {
    @Column(name = "value", nullable = false, updatable = false, length = 36)
    override lateinit var value: String
        protected set

    private constructor(value: String) : this() {
        this.value = value
    }

    companion object {
        fun of(value: String): MixedCaseUuidStringId =
            MixedCaseUuidStringId(StrongIds.requireUuidV7(value, "MixedCaseUuidStringId"))

        fun parse(value: String): MixedCaseUuidStringId = of(value)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is MixedCaseUuidStringId && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

@Entity
@Table(name = "\"MixedCaseUuidString\"")
@SQLDelete(
    sql = "update \"MixedCaseUuidString\" set \"DeletedMarker\" = \"StrongId\" where \"StrongId\" = ?"
)
@Where(clause = "\"DeletedMarker\" = '00000000-0000-0000-0000-000000000000'")
open class MixedCaseUuidStringEntity protected constructor() {
    @EmbeddedId
    @AttributeOverride(
        name = "value",
        column = Column(name = "\"StrongId\"", nullable = false, updatable = false, length = 36),
    )
    open lateinit var id: MixedCaseUuidStringId
        protected set

    @Column(name = "\"DisplayName\"", nullable = false)
    open lateinit var name: String
        protected set

    @Column(name = "\"DeletedMarker\"", nullable = false, length = 36)
    open var deleted: String = NIL_UUID_TEXT
        protected set

    internal constructor(name: String) : this() {
        this.name = name
    }

    fun hasAssignedId(): Boolean = this::id.isInitialized

    companion object {
        fun unassigned(name: String): MixedCaseUuidStringEntity = MixedCaseUuidStringEntity(name)
    }
}

interface MixedCaseUuidStringRepository :
    JpaRepository<MixedCaseUuidStringEntity, MixedCaseUuidStringId>
