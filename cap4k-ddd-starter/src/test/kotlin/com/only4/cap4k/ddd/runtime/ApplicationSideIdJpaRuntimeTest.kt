package com.only4.cap4k.ddd.runtime

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.domain.id.ApplicationSideId
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import java.util.UUID

@SpringBootTest(classes = [ApplicationSideIdJpaRuntimeTest.RuntimeTestApplication::class])
@TestPropertySource(
    properties = [
        "cap4k.application.name=application-side-id-jpa-runtime-test",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.url=jdbc:h2:mem:application-side-id-jpa-runtime;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
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
class ApplicationSideIdJpaRuntimeTest {
    @Autowired
    @Qualifier("jpaUnitOfWork")
    private lateinit var unitOfWork: UnitOfWork

    @Autowired
    private lateinit var rootRepository: RuntimeUuidRootRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.update("delete from `runtime_uuid_child`")
        jdbcTemplate.update("delete from `runtime_uuid_root`")
        JpaUnitOfWork.reset()
    }

    @Test
    fun `default uuid root and child ids are assigned before persist`() {
        val zeroUuid = UUID(0L, 0L)
        val root = RuntimeUuidRoot(name = "root-with-default-id").apply {
            children.add(RuntimeUuidChild(name = "child-with-default-id"))
        }

        unitOfWork.persist(root)
        unitOfWork.save()

        assertNotEquals(zeroUuid, root.id)
        assertEquals(7, root.id.version())
        assertEquals(1, root.children.size)
        assertNotEquals(zeroUuid, root.children.single().id)
        assertEquals(7, root.children.single().id.version())
        assertEquals(1, countRows("select count(*) from `runtime_uuid_root` where `id` = ?", root.id))
        assertEquals(1, countRows("select count(*) from `runtime_uuid_child` where `id` = ?", root.children.single().id))
        assertEquals(1, countRows("select count(*) from `runtime_uuid_child` where `root_id` = ?", root.id))
    }

    @Test
    fun `preassigned uuid root id is preserved and inserted`() {
        val preassignedId = UUID.fromString("018f0000-0000-7000-8000-000000000001")
        val root = RuntimeUuidRoot(id = preassignedId, name = "preassigned-root-id")

        unitOfWork.persist(root)
        unitOfWork.save()

        assertEquals(preassignedId, root.id)
        assertEquals(preassignedId, rootRepository.findById(preassignedId).orElseThrow().id)
    }

    private fun countRows(sql: String, vararg args: Any): Int =
        requireNotNull(jdbcTemplate.queryForObject(sql, Int::class.java, *args))

    @SpringBootApplication(scanBasePackages = ["com.only4.cap4k.ddd"])
    @EntityScan(basePackageClasses = [RuntimeUuidRoot::class])
    @EnableJpaRepositories(basePackageClasses = [RuntimeUuidRootRepository::class])
    class RuntimeTestApplication
}

@Entity
@Table(name = "`runtime_uuid_root`")
open class RuntimeUuidRoot(id: UUID = UUID(0L, 0L), name: String = "") {
    @OneToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE], fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "`root_id`", nullable = false)
    open var children: MutableList<RuntimeUuidChild> = mutableListOf()

    @Id
    @ApplicationSideId(strategy = "uuid7")
    @Column(name = "`id`", nullable = false, updatable = false)
    open var id: UUID = id
        protected set

    @Column(name = "`name`", nullable = false)
    open var name: String = name
}

@Entity
@Table(name = "`runtime_uuid_child`")
open class RuntimeUuidChild(id: UUID = UUID(0L, 0L), name: String = "") {
    @Id
    @ApplicationSideId(strategy = "uuid7")
    @Column(name = "`id`", nullable = false, updatable = false)
    open var id: UUID = id
        protected set

    @Column(name = "`name`", nullable = false)
    open var name: String = name
}

interface RuntimeUuidRootRepository : JpaRepository<RuntimeUuidRoot, UUID>
