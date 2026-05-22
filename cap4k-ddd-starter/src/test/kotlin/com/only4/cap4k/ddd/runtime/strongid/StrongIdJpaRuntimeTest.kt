package com.only4.cap4k.ddd.runtime.strongid

import com.only4.cap4k.ddd.core.domain.id.StrongId
import com.only4.cap4k.ddd.core.domain.id.StrongIds
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import java.io.Serializable

@DataJpaTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:strong-id-jpa-runtime;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate=WARN",
    ]
)
class StrongIdJpaRuntimeTest {
    @Autowired
    private lateinit var repository: StrongIdJpaRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `hibernate persists and loads entity by strong id`() {
        val id = StrongContentId.new()

        repository.saveAndFlush(StrongContent(id = id, title = "content"))
        val loaded = repository.findById(id).orElseThrow()
        val persistedId = jdbcTemplate.queryForObject(
            """select "id" from "strong_content" where "title" = ?""",
            String::class.java,
            "content",
        )

        assertEquals(id, loaded.id)
        assertEquals("content", loaded.title)
        assertEquals(id.value, persistedId)
    }

    @SpringBootApplication
    @EntityScan(basePackageClasses = [StrongContent::class])
    @EnableJpaRepositories(basePackageClasses = [StrongIdJpaRepository::class])
    class TestApplication
}

@Embeddable
class StrongContentId protected constructor() : StrongId, Serializable {
    @Column(name = "`id`", nullable = false, updatable = false, length = 36)
    override lateinit var value: String
        protected set

    constructor(value: String) : this() {
        this.value = StrongIds.requireUuidV7(value, "StrongContentId")
    }

    companion object {
        fun new(): StrongContentId = StrongContentId(StrongIds.newUuidV7String())

        fun parse(value: String): StrongContentId = StrongContentId(value)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is StrongContentId && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

@Entity
@Table(name = "`strong_content`")
open class StrongContent protected constructor() {
    @EmbeddedId
    open lateinit var id: StrongContentId
        protected set

    @Column(name = "`title`", nullable = false)
    open lateinit var title: String
        protected set

    constructor(id: StrongContentId, title: String) : this() {
        this.id = id
        this.title = title
    }
}

interface StrongIdJpaRepository : JpaRepository<StrongContent, StrongContentId>
