package com.only4.cap4k.ddd.runtime.strongid

import com.only4.cap4k.ddd.core.domain.id.StrongId
import com.only4.cap4k.ddd.core.domain.id.StrongIds
import jakarta.persistence.AttributeOverride
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
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
        val authorId = StrongAuthorId.new()
        val mediaProcessingTaskId = StrongMediaProcessingTaskId.new()

        repository.saveAndFlush(
            StrongContent(
                id = id,
                title = "content",
                authorId = authorId,
                mediaProcessingTaskId = mediaProcessingTaskId,
            )
        )
        val loaded = repository.findById(id).orElseThrow()
        val persistedId = jdbcTemplate.queryForObject(
            """select "id" from "strong_content" where "title" = ?""",
            String::class.java,
            "content",
        )
        val persistedAuthorId = jdbcTemplate.queryForObject(
            """select "author_id" from "strong_content" where "title" = ?""",
            String::class.java,
            "content",
        )
        val persistedMediaProcessingTaskId = jdbcTemplate.queryForObject(
            """select "media_processing_task_id" from "strong_content" where "title" = ?""",
            String::class.java,
            "content",
        )

        assertEquals(id, loaded.id)
        assertEquals("content", loaded.title)
        assertEquals(authorId, loaded.authorId)
        assertEquals(mediaProcessingTaskId, loaded.mediaProcessingTaskId)
        assertEquals(id.value, persistedId)
        assertEquals(authorId.value, persistedAuthorId)
        assertEquals(mediaProcessingTaskId.value, persistedMediaProcessingTaskId)
    }

    @Test
    fun `hibernate persists owned child by strong id and parent fk storage`() {
        val contentId = StrongContentId.new()
        val itemId = StrongContentItemId.new()
        val content = StrongContent(
            id = contentId,
            title = "content-with-item",
            authorId = StrongAuthorId.new(),
            mediaProcessingTaskId = null,
        )
        content.items += StrongContentItem(itemId, "chapter-1")

        repository.saveAndFlush(content)

        val persistedItemId = jdbcTemplate.queryForObject(
            """select "id" from "strong_content_item" where "label" = ?""",
            String::class.java,
            "chapter-1",
        )
        val persistedParentId = jdbcTemplate.queryForObject(
            """select "content_id" from "strong_content_item" where "label" = ?""",
            String::class.java,
            "chapter-1",
        )

        assertEquals(itemId.value, persistedItemId)
        assertEquals(contentId.value, persistedParentId)
    }

    @SpringBootApplication
    @EntityScan(basePackageClasses = [StrongContent::class])
    @EnableJpaRepositories(basePackageClasses = [StrongIdJpaRepository::class])
    class TestApplication
}

@Embeddable
class StrongContentId protected constructor() : StrongId, Serializable {
    @Column(name = "value", nullable = false, updatable = false, length = 36)
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

@Embeddable
class StrongAuthorId protected constructor() : StrongId, Serializable {
    @Column(name = "value", nullable = false, updatable = false, length = 36)
    override lateinit var value: String
        protected set

    constructor(value: String) : this() {
        this.value = StrongIds.requireUuidV7(value, "StrongAuthorId")
    }

    companion object {
        fun new(): StrongAuthorId = StrongAuthorId(StrongIds.newUuidV7String())
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is StrongAuthorId && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

@Embeddable
class StrongMediaProcessingTaskId protected constructor() : StrongId, Serializable {
    @Column(name = "value", nullable = false, updatable = false, length = 36)
    override lateinit var value: String
        protected set

    constructor(value: String) : this() {
        this.value = StrongIds.requireUuidV7(value, "StrongMediaProcessingTaskId")
    }

    companion object {
        fun new(): StrongMediaProcessingTaskId = StrongMediaProcessingTaskId(StrongIds.newUuidV7String())
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is StrongMediaProcessingTaskId && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

@Embeddable
class StrongContentItemId protected constructor() : StrongId, Serializable {
    @Column(name = "value", nullable = false, updatable = false, length = 36)
    override lateinit var value: String
        protected set

    constructor(value: String) : this() {
        this.value = StrongIds.requireUuidV7(value, "StrongContentItemId")
    }

    companion object {
        fun new(): StrongContentItemId = StrongContentItemId(StrongIds.newUuidV7String())

        fun parse(value: String): StrongContentItemId = StrongContentItemId(value)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is StrongContentItemId && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

@Entity
@Table(name = "`strong_content`")
open class StrongContent protected constructor() {
    @EmbeddedId
    @AttributeOverride(name = "value", column = Column(name = "`id`", nullable = false, updatable = false, length = 36))
    open lateinit var id: StrongContentId
        protected set

    @Column(name = "`title`", nullable = false)
    open lateinit var title: String
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "`author_id`", nullable = false, updatable = true, length = 36))
    open lateinit var authorId: StrongAuthorId
        protected set

    @Embedded
    @AttributeOverride(
        name = "value",
        column = Column(name = "`media_processing_task_id`", nullable = true, updatable = true, length = 36),
    )
    open var mediaProcessingTaskId: StrongMediaProcessingTaskId? = null
        protected set

    @OneToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE], orphanRemoval = true)
    @JoinColumn(name = "`content_id`", nullable = false)
    open val items: MutableList<StrongContentItem> = mutableListOf()

    constructor(
        id: StrongContentId,
        title: String,
        authorId: StrongAuthorId,
        mediaProcessingTaskId: StrongMediaProcessingTaskId?,
    ) : this() {
        this.id = id
        this.title = title
        this.authorId = authorId
        this.mediaProcessingTaskId = mediaProcessingTaskId
    }

    companion object {
        fun unassigned(title: String): StrongContent =
            StrongContent().also {
                it.title = title
                it.authorId = StrongAuthorId.new()
            }
    }

    fun hasAssignedId(): Boolean = this::id.isInitialized
}

@Entity
@Table(name = "`strong_content_item`")
open class StrongContentItem protected constructor() {
    @EmbeddedId
    @AttributeOverride(name = "value", column = Column(name = "`id`", nullable = false, updatable = false, length = 36))
    open lateinit var id: StrongContentItemId
        protected set

    @Column(name = "`label`", nullable = false)
    open lateinit var label: String
        protected set

    constructor(id: StrongContentItemId, label: String) : this() {
        this.id = id
        this.label = label
    }

    companion object {
        fun unassigned(label: String): StrongContentItem =
            StrongContentItem().also { it.label = label }
    }

    fun hasAssignedId(): Boolean = this::id.isInitialized
}

interface StrongIdJpaRepository : JpaRepository<StrongContent, StrongContentId>
