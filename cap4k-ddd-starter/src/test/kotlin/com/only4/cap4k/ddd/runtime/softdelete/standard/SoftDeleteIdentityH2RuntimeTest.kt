package com.only4.cap4k.ddd.runtime.softdelete.standard

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.Where
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate

@DataJpaTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:soft-delete-identity-runtime;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate=WARN",
    ]
)
class SoftDeleteIdentityH2RuntimeTest {
    @Autowired
    private lateinit var repository: SoftDeleteIdentityRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `versioned identity lifecycle preserves the row and replaces deleted with id`() {
        val entity = SoftDeleteIdentity("standard-h2")

        assertEquals(0L, entity.deleted)

        val saved = repository.saveAndFlush(entity)
        val id = requireNotNull(saved.id)
        assertEquals(
            IdentityPhysicalRow(id = id, deleted = 0L, version = saved.version),
            physicalRow(id),
        )

        repository.delete(saved)
        repository.flush()
        entityManager.clear()

        assertFalse(repository.findById(id).isPresent)
        assertEquals(
            IdentityPhysicalRow(id = id, deleted = id, version = saved.version),
            physicalRow(id),
        )
    }

    private fun physicalRow(id: Long): IdentityPhysicalRow =
        jdbcTemplate.queryForObject(
            """select "ID", "DELETED", "VERSION" from "SOFT_DELETE_IDENTITY" where "ID" = ?""",
            { resultSet, _ ->
                IdentityPhysicalRow(
                    id = resultSet.getLong("ID"),
                    deleted = resultSet.getLong("DELETED"),
                    version = resultSet.getLong("VERSION"),
                )
            },
            id,
        ) ?: error("soft-delete identity row was not retained")

    @SpringBootApplication
    @EntityScan(basePackageClasses = [SoftDeleteIdentity::class])
    @EnableJpaRepositories(basePackageClasses = [SoftDeleteIdentityRepository::class])
    class TestApplication
}

private data class IdentityPhysicalRow(
    val id: Long,
    val deleted: Long,
    val version: Long,
)

@Entity
@Table(name = "soft_delete_identity")
@SQLDelete(
    sql = "update \"SOFT_DELETE_IDENTITY\" set \"DELETED\" = \"ID\" where \"ID\" = ? and \"VERSION\" = ?"
)
@Where(clause = "\"DELETED\" = 0")
open class SoftDeleteIdentity protected constructor() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    open var id: Long? = null
        protected set

    @Column(name = "name", nullable = false)
    open lateinit var name: String
        protected set

    @Version
    @Column(name = "version", nullable = false)
    open var version: Long = 0L
        protected set

    @Column(name = "deleted", nullable = false)
    open var deleted: Long = 0L
        protected set

    internal constructor(name: String) : this() {
        this.name = name
    }
}

interface SoftDeleteIdentityRepository : JpaRepository<SoftDeleteIdentity, Long>
