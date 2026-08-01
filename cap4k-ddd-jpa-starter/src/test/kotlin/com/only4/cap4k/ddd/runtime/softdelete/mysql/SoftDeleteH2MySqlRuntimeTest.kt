package com.only4.cap4k.ddd.runtime.softdelete.mysql

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.ddd.core.domain.aggregate.impl.DefaultAggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.id.StrongId
import com.only4.cap4k.ddd.core.domain.id.StrongIds
import com.only4.cap4k.ddd.core.domain.event.DomainEventManager
import com.only4.cap4k.ddd.core.domain.managed.DefaultManagedEntityAdmissionCoordinator
import com.only4.cap4k.ddd.core.domain.managed.DefaultManagedFieldRegistry
import com.only4.cap4k.ddd.core.domain.managed.ManagedEntityAdmissionCoordinator
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
import java.util.UUID
import kotlin.reflect.KClass

private const val MYSQL_NIL_UUID_TEXT = "00000000-0000-0000-0000-000000000000"

@DataJpaTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:soft-delete-mysql-runtime;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate=WARN",
    ]
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SoftDeleteH2MySqlRuntimeTest.TestConfig::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SoftDeleteH2MySqlRuntimeTest {
    @Autowired
    private lateinit var snowflakeLongRepository: MySqlSnowflakeLongRepository

    @Autowired
    private lateinit var snowflakeStringRepository: MySqlSnowflakeStringRepository

    @Autowired
    private lateinit var nativeUuidRepository: MySqlNativeUuidRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var unitOfWork: JpaUnitOfWork

    @Autowired
    private lateinit var admissionCoordinator: ManagedEntityAdmissionCoordinator

    @BeforeEach
    fun resetUnitOfWork() {
        JpaUnitOfWork.reset()
    }

    @Test
    fun `Snowflake Long Strong ID binds BIGINT SQLDelete placeholder and stores deleted as id value`() {
        lateinit var entity: MySqlSnowflakeLongEntity
        unitOfWork.execute {
            entity = factorySupervisor().create(MySqlSnowflakeLongFactory.Payload(name = "snowflake-long"))
            assertTrue(entity.hasAssignedId())
            assertEquals(0L, entity.deleted)
        }
        val id = entity.id
        entityManager.clear()

        assertEquals("BIGINT", columnType("mysql_snowflake_long", "id"))
        assertEquals("BIGINT", columnType("mysql_snowflake_long", "deleted"))
        val activeRow = snowflakeLongRow(id.value)
        assertTrue(activeRow.id is Long)
        assertTrue(activeRow.deleted is Long)
        assertEquals(id.value, activeRow.id)
        assertEquals(0L, activeRow.deleted)

        unitOfWork.execute {
            unitOfWork.registerDelete(snowflakeLongRepository.findById(id).orElseThrow())
        }
        entityManager.clear()

        assertTrue(snowflakeLongRepository.findAll().isEmpty())
        val deletedRow = snowflakeLongRow(id.value)
        assertTrue(deletedRow.id is Long)
        assertTrue(deletedRow.deleted is Long)
        assertEquals(id.value, deletedRow.id)
        assertEquals(id.value, deletedRow.deleted)
    }

    @Test
    fun `Snowflake String lifecycle executes character ZERO sentinel and preserves physical row`() {
        lateinit var entity: MySqlSnowflakeStringEntity
        unitOfWork.execute {
            entity = factorySupervisor().create(MySqlSnowflakeStringFactory.Payload(name = "snowflake-string"))
            assertTrue(entity.hasAssignedId())
            assertEquals("0", entity.deleted)
        }
        val id = entity.id
        entityManager.clear()

        assertEquals(
            StringPhysicalRow(id = id.value, deleted = "0"),
            snowflakeStringRow(id.value),
        )

        unitOfWork.execute {
            unitOfWork.registerDelete(snowflakeStringRepository.findById(id).orElseThrow())
        }
        entityManager.clear()

        assertTrue(snowflakeStringRepository.findAll().isEmpty())
        assertEquals(
            StringPhysicalRow(id = id.value, deleted = id.value),
            snowflakeStringRow(id.value),
        )
    }

    @Test
    fun `native UUID lifecycle executes explicit CAST predicate and preserves physical row`() {
        lateinit var entity: MySqlNativeUuidEntity
        unitOfWork.execute {
            entity = factorySupervisor().create(MySqlNativeUuidFactory.Payload(name = "native-uuid"))
            assertTrue(entity.hasAssignedId())
            assertEquals(UUID(0L, 0L), entity.deleted)
        }
        val id = entity.id
        entityManager.clear()

        assertEquals(1L, nativeActiveRowCount())
        assertEquals(
            NativeUuidPhysicalRow(id = id.value, deleted = UUID(0L, 0L)),
            nativeUuidRow(id.value),
        )

        unitOfWork.execute {
            unitOfWork.registerDelete(nativeUuidRepository.findById(id).orElseThrow())
        }
        entityManager.clear()

        assertTrue(nativeUuidRepository.findAll().isEmpty())
        assertEquals(0L, nativeActiveRowCount())
        assertEquals(
            NativeUuidPhysicalRow(id = id.value, deleted = id.value),
            nativeUuidRow(id.value),
        )
    }

    private fun factorySupervisor(): DefaultAggregateFactorySupervisor =
        DefaultAggregateFactorySupervisor(
            factories = listOf(
                MySqlSnowflakeLongFactory(),
                MySqlSnowflakeStringFactory(),
                MySqlNativeUuidFactory(),
            ),
            persistenceIntents = unitOfWork,
            invocationScopeAccessor = InvocationScopeAccessor { InvocationKind.COMMAND },
            managedEntityAdmissionCoordinator = admissionCoordinator,
        ).apply { init() }

    private fun columnType(tableName: String, columnName: String): String =
        jdbcTemplate.queryForObject(
            """select DATA_TYPE from INFORMATION_SCHEMA.COLUMNS where TABLE_SCHEMA = 'PUBLIC' and TABLE_NAME = ? and COLUMN_NAME = ?""",
            String::class.java,
            tableName,
            columnName,
        ) ?: error("column metadata not found for $tableName.$columnName")

    private fun snowflakeLongRow(id: Long): LongPhysicalRow =
        jdbcTemplate.queryForObject(
            """select `id`, `deleted` from `mysql_snowflake_long` where `id` = ?""",
            { resultSet, _ ->
                LongPhysicalRow(
                    id = resultSet.getObject("id"),
                    deleted = resultSet.getObject("deleted"),
                )
            },
            id,
        ) ?: error("Snowflake Long row was not retained")

    private fun snowflakeStringRow(id: String): StringPhysicalRow =
        jdbcTemplate.queryForObject(
            """select `id`, `deleted` from `mysql_snowflake_string` where `id` = ?""",
            { resultSet, _ ->
                StringPhysicalRow(
                    id = resultSet.getString("id"),
                    deleted = resultSet.getString("deleted"),
                )
            },
            id,
        ) ?: error("Snowflake String row was not retained")

    private fun nativeUuidRow(id: UUID): NativeUuidPhysicalRow =
        jdbcTemplate.queryForObject(
            """select `id`, `deleted` from `mysql_uuid_native` where `id` = ?""",
            { resultSet, _ ->
                NativeUuidPhysicalRow(
                    id = resultSet.getObject("id", UUID::class.java),
                    deleted = resultSet.getObject("deleted", UUID::class.java),
                )
            },
            id,
        ) ?: error("native UUID row was not retained")

    private fun nativeActiveRowCount(): Long =
        jdbcTemplate.queryForObject(
            """select count(*) from `mysql_uuid_native` where `deleted` = CAST('$MYSQL_NIL_UUID_TEXT' AS UUID)""",
            Long::class.java,
        ) ?: error("native UUID active predicate did not return a count")

    @SpringBootApplication
    @EntityScan(basePackageClasses = [MySqlSnowflakeLongEntity::class])
    @EnableJpaRepositories(basePackageClasses = [MySqlSnowflakeLongRepository::class])
    class TestApplication

    class TestConfig {
        @Bean
        fun domainEventManager(): DomainEventManager = object : DomainEventManager {
            override fun release(entities: Set<Any>) = Unit
        }

        @Bean
        fun managedFieldCatalog(): ManagedFieldCatalog = MySqlSoftDeleteManagedFieldCatalog()

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
        ): JpaUnitOfWork = JpaUnitOfWork(
            domainEventManager = domainEventManager,
            managedFieldRegistry = managedFieldRegistry,
            managedEntityAdmissionCoordinator = managedEntityAdmissionCoordinator,
        )
    }
}

private data class LongPhysicalRow(
    val id: Any,
    val deleted: Any,
)

private data class StringPhysicalRow(
    val id: String,
    val deleted: String,
)

private data class NativeUuidPhysicalRow(
    val id: UUID,
    val deleted: UUID,
)

private class MySqlSnowflakeLongFactory :
    AggregateFactory<MySqlSnowflakeLongFactory.Payload, MySqlSnowflakeLongEntity> {
    override fun create(entityPayload: Payload): MySqlSnowflakeLongEntity =
        MySqlSnowflakeLongEntity.unassigned(entityPayload.name)

    data class Payload(
        val name: String,
    ) : AggregatePayload<MySqlSnowflakeLongEntity>
}

private class MySqlSnowflakeStringFactory :
    AggregateFactory<MySqlSnowflakeStringFactory.Payload, MySqlSnowflakeStringEntity> {
    override fun create(entityPayload: Payload): MySqlSnowflakeStringEntity =
        MySqlSnowflakeStringEntity.unassigned(entityPayload.name)

    data class Payload(
        val name: String,
    ) : AggregatePayload<MySqlSnowflakeStringEntity>
}

private class MySqlNativeUuidFactory :
    AggregateFactory<MySqlNativeUuidFactory.Payload, MySqlNativeUuidEntity> {
    override fun create(entityPayload: Payload): MySqlNativeUuidEntity =
        MySqlNativeUuidEntity.unassigned(entityPayload.name)

    data class Payload(
        val name: String,
    ) : AggregatePayload<MySqlNativeUuidEntity>
}

private class MySqlSoftDeleteManagedFieldCatalog : ManagedFieldCatalog {
    private var longSequence = 0L
    private var stringSequence = 0L
    private var uuidSequence = 0L

    override val bindings: List<ManagedFieldBinding> = listOf(
        identifierBinding(MySqlSnowflakeLongEntity::class, MySqlSnowflakeLongId::class, "identifier.snowflake") {
            MySqlSnowflakeLongId.of(7_288_198_123_456_789_000L + ++longSequence)
        },
        identifierBinding(MySqlSnowflakeStringEntity::class, MySqlSnowflakeStringId::class, "identifier.snowflake") {
            MySqlSnowflakeStringId.of((7_388_198_123_456_789_000L + ++stringSequence).toString())
        },
        identifierBinding(MySqlNativeUuidEntity::class, MySqlNativeUuidId::class, "identifier.uuid7") {
            MySqlNativeUuidId.parse(
                "019c0000-0000-7000-8004-${(++uuidSequence).toString(16).padStart(12, '0')}"
            )
        },
    )

    private fun <ID : Any> identifierBinding(
        entityType: KClass<*>,
        idType: KClass<ID>,
        qualifier: String,
        allocate: () -> ID,
    ): ManagedFieldBinding = ManagedFieldBinding(
        entityType = entityType,
        fieldName = "id",
        persistencePropertyName = "id",
        columnName = "id",
        targetType = idType,
        nullable = false,
        policyKey = qualifier,
        role = ManagedFieldRole.IDENTIFIER,
        explicitValue = ManagedExplicitValuePolicy.PRESERVE_IF_VALID,
        lifecycles = setOf(ManagedFieldLifecycle.ENTITY_ADMISSION),
        handlerQualifier = qualifier,
        handlerSlot = null,
        semanticValueType = idType,
        valueAdapterQualifier = null,
        persistence = PersistenceParticipation(
            insert = ManagedValueAuthority.FRAMEWORK,
            update = ManagedValueAuthority.NONE,
        ),
        runtimeSupport = ManagedFieldRuntimeSupport.ApplicationIdentifier(
            isAbsent = { it == null },
            allocateTarget = allocate,
            validateTarget = { value -> require(idType.isInstance(value)) },
        )
    )
}

@Embeddable
class MySqlSnowflakeLongId protected constructor() : StrongId<Long>, Serializable {
    @Column(name = "value", nullable = false, updatable = false)
    override var value: Long = 0L
        protected set

    private constructor(value: Long) : this() {
        this.value = value
    }

    companion object {
        fun of(value: Long): MySqlSnowflakeLongId =
            MySqlSnowflakeLongId(StrongIds.requireSnowflake(value, "MySqlSnowflakeLongId"))
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is MySqlSnowflakeLongId && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value.toString()
}

@Embeddable
class MySqlSnowflakeStringId protected constructor() : StrongId<String>, Serializable {
    @Column(name = "value", nullable = false, updatable = false, length = 19)
    override lateinit var value: String
        protected set

    private constructor(value: String) : this() {
        this.value = value
    }

    companion object {
        fun of(value: String): MySqlSnowflakeStringId =
            MySqlSnowflakeStringId(StrongIds.requireSnowflake(value, "MySqlSnowflakeStringId"))
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is MySqlSnowflakeStringId && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

@Embeddable
class MySqlNativeUuidId protected constructor() : StrongId<UUID>, Serializable {
    @Column(name = "value", nullable = false, updatable = false)
    override lateinit var value: UUID
        protected set

    private constructor(value: UUID) : this() {
        this.value = value
    }

    companion object {
        fun of(value: UUID): MySqlNativeUuidId =
            MySqlNativeUuidId(StrongIds.requireUuidV7(value, "MySqlNativeUuidId"))

        fun parse(value: String): MySqlNativeUuidId =
            of(UUID.fromString(StrongIds.requireUuidV7(value, "MySqlNativeUuidId")))
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is MySqlNativeUuidId && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value.toString()
}

@Entity
@Table(name = "`mysql_snowflake_long`")
@SQLDelete(
    sql = "update `mysql_snowflake_long` set `deleted` = `id` where `id` = ?"
)
@Where(clause = "`deleted` = 0")
open class MySqlSnowflakeLongEntity protected constructor() {
    @EmbeddedId
    @AttributeOverride(
        name = "value",
        column = Column(name = "`id`", nullable = false, updatable = false),
    )
    open lateinit var id: MySqlSnowflakeLongId
        protected set

    @Column(name = "`name`", nullable = false)
    open lateinit var name: String
        protected set

    @Column(name = "`deleted`", nullable = false)
    open var deleted: Long = 0L
        protected set

    internal constructor(name: String) : this() {
        this.name = name
    }

    fun hasAssignedId(): Boolean = this::id.isInitialized

    companion object {
        fun unassigned(name: String): MySqlSnowflakeLongEntity = MySqlSnowflakeLongEntity(name)
    }
}

@Entity
@Table(name = "`mysql_snowflake_string`")
@SQLDelete(
    sql = "update `mysql_snowflake_string` set `deleted` = `id` where `id` = ?"
)
@Where(clause = "`deleted` = '0'")
open class MySqlSnowflakeStringEntity protected constructor() {
    @EmbeddedId
    @AttributeOverride(
        name = "value",
        column = Column(name = "`id`", nullable = false, updatable = false, length = 19),
    )
    open lateinit var id: MySqlSnowflakeStringId
        protected set

    @Column(name = "`name`", nullable = false)
    open lateinit var name: String
        protected set

    @Column(name = "`deleted`", nullable = false, length = 19)
    open var deleted: String = "0"
        protected set

    internal constructor(name: String) : this() {
        this.name = name
    }

    fun hasAssignedId(): Boolean = this::id.isInitialized

    companion object {
        fun unassigned(name: String): MySqlSnowflakeStringEntity = MySqlSnowflakeStringEntity(name)
    }
}

@Entity
@Table(name = "`mysql_uuid_native`")
@SQLDelete(
    sql = "update `mysql_uuid_native` set `deleted` = `id` where `id` = ?"
)
@Where(
    clause = "`deleted` = CAST('00000000-0000-0000-0000-000000000000' AS UUID)"
)
open class MySqlNativeUuidEntity protected constructor() {
    @EmbeddedId
    @AttributeOverride(
        name = "value",
        column = Column(name = "`id`", nullable = false, updatable = false),
    )
    open lateinit var id: MySqlNativeUuidId
        protected set

    @Column(name = "`name`", nullable = false)
    open lateinit var name: String
        protected set

    @Column(name = "`deleted`", nullable = false)
    open var deleted: UUID = UUID(0L, 0L)
        protected set

    internal constructor(name: String) : this() {
        this.name = name
    }

    fun hasAssignedId(): Boolean = this::id.isInitialized

    companion object {
        fun unassigned(name: String): MySqlNativeUuidEntity = MySqlNativeUuidEntity(name)
    }
}

interface MySqlSnowflakeLongRepository :
    JpaRepository<MySqlSnowflakeLongEntity, MySqlSnowflakeLongId>

interface MySqlSnowflakeStringRepository :
    JpaRepository<MySqlSnowflakeStringEntity, MySqlSnowflakeStringId>

interface MySqlNativeUuidRepository :
    JpaRepository<MySqlNativeUuidEntity, MySqlNativeUuidId>
