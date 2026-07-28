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
import jakarta.persistence.EntityManager
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import java.io.Serializable
import java.util.UUID

private const val UUID7_TEXT = "019c0000-0000-7000-8000-000000000001"
private const val SNOWFLAKE_TEXT = "7288198123456789012"

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
    private lateinit var repository: StrongIdMatrixRepository

    @Autowired
    private lateinit var legacyRepository: StrongIdJpaRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `hibernate persists reloads and exposes direct strong id backing values`() {
        val id = UuidTextId.parse(UUID7_TEXT)
        val nativeUuid = UuidNativeId.parse(UUID7_TEXT)
        val snowflakeText = SnowflakeTextId.parse(SNOWFLAKE_TEXT)
        val snowflakeLong = SnowflakeLongId.parse(SNOWFLAKE_TEXT)

        repository.save(
            StrongIdMatrix(
                id = id,
                nativeUuid = nativeUuid,
                snowflakeText = snowflakeText,
                snowflakeLong = snowflakeLong,
            )
        )
        entityManager.flush()
        entityManager.clear()

        val loaded = repository.findById(id).orElseThrow()
        val persistedValues = jdbcTemplate.queryForObject(
            """select "id", "native_uuid", "snowflake_text", "snowflake_long" from "strong_id_matrix""""
        ) { resultSet, _ ->
            listOf(
                resultSet.getObject("id"),
                resultSet.getObject("native_uuid"),
                resultSet.getObject("snowflake_text"),
                resultSet.getObject("snowflake_long"),
            )
        } ?: error("strong id matrix row was not persisted")

        assertEquals(id, loaded.id)
        assertEquals(nativeUuid, loaded.nativeUuid)
        assertEquals(snowflakeText, loaded.snowflakeText)
        assertEquals(snowflakeLong, loaded.snowflakeLong)
        assertTrue(persistedValues[0] is String)
        assertTrue(persistedValues[1] is UUID)
        assertTrue(persistedValues[2] is String)
        assertTrue(persistedValues[3] is Long)
        assertEquals(UUID7_TEXT, persistedValues[0])
        assertEquals(UUID.fromString(UUID7_TEXT), persistedValues[1])
        assertEquals(SNOWFLAKE_TEXT, persistedValues[2])
        assertEquals(SNOWFLAKE_TEXT.toLong(), persistedValues[3])
    }

    @Test
    fun `hibernate persists and loads entity by strong id`() {
        val id = StrongContentId.parse("019c0000-0000-7000-8000-000000000002")
        val authorId = StrongAuthorId.parse("019c0000-0000-7000-8000-000000000003")
        val mediaProcessingTaskId = StrongMediaProcessingTaskId.parse("019c0000-0000-7000-8000-000000000004")

        legacyRepository.saveAndFlush(
            StrongContent(
                id = id,
                title = "content",
                authorId = authorId,
                mediaProcessingTaskId = mediaProcessingTaskId,
            )
        )
        val loaded = legacyRepository.findById(id).orElseThrow()
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
        val contentId = StrongContentId.parse("019c0000-0000-7000-8000-000000000002")
        val itemId = StrongContentItemId.parse("019c0000-0000-7000-8000-000000000005")
        val content = StrongContent(
            id = contentId,
            title = "content-with-item",
            authorId = StrongAuthorId.parse("019c0000-0000-7000-8000-000000000003"),
            mediaProcessingTaskId = null,
        )
        content.items += StrongContentItem(itemId, "chapter-1")

        legacyRepository.saveAndFlush(content)

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
    @EntityScan(basePackageClasses = [StrongIdMatrix::class])
    @EnableJpaRepositories(basePackageClasses = [StrongIdMatrixRepository::class])
    class TestApplication
}

@Embeddable
class UuidTextId protected constructor() : StrongId<String>, Serializable {
    @Column(name = "value", nullable = false, updatable = false)
    override lateinit var value: String
        protected set

    private constructor(value: String) : this() {
        this.value = value
    }

    override fun toString(): String = value.toString()

    companion object {
        fun of(value: String): UuidTextId =
            UuidTextId(StrongIds.requireUuidV7(value, "UuidTextId"))

        fun parse(value: String): UuidTextId = of(value)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is UuidTextId && value == other.value)

    override fun hashCode(): Int = value.hashCode()
}

@Embeddable
class UuidNativeId protected constructor() : StrongId<UUID>, Serializable {
    @Column(name = "value", nullable = false, updatable = false)
    override lateinit var value: UUID
        protected set

    private constructor(value: UUID) : this() {
        this.value = value
    }

    override fun toString(): String = value.toString()

    companion object {
        fun of(value: UUID): UuidNativeId =
            UuidNativeId(StrongIds.requireUuidV7(value, "UuidNativeId"))

        fun parse(value: String): UuidNativeId =
            of(UUID.fromString(StrongIds.requireUuidV7(value, "UuidNativeId")))
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is UuidNativeId && value == other.value)

    override fun hashCode(): Int = value.hashCode()
}

@Embeddable
class SnowflakeTextId protected constructor() : StrongId<String>, Serializable {
    @Column(name = "value", nullable = false, updatable = false)
    override lateinit var value: String
        protected set

    private constructor(value: String) : this() {
        this.value = value
    }

    override fun toString(): String = value.toString()

    companion object {
        fun of(value: String): SnowflakeTextId =
            SnowflakeTextId(StrongIds.requireSnowflake(value, "SnowflakeTextId"))

        fun parse(value: String): SnowflakeTextId = of(value)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is SnowflakeTextId && value == other.value)

    override fun hashCode(): Int = value.hashCode()
}

@Embeddable
class SnowflakeLongId protected constructor() : StrongId<Long>, Serializable {
    @Column(name = "value", nullable = false, updatable = false)
    override var value: Long = 0L
        protected set

    private constructor(value: Long) : this() {
        this.value = value
    }

    override fun toString(): String = value.toString()

    companion object {
        fun of(value: Long): SnowflakeLongId =
            SnowflakeLongId(StrongIds.requireSnowflake(value, "SnowflakeLongId"))

        fun parse(value: String): SnowflakeLongId =
            of(StrongIds.requireSnowflake(value, "SnowflakeLongId").toLong())
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is SnowflakeLongId && value == other.value)

    override fun hashCode(): Int = value.hashCode()
}

@Entity
@Table(name = "`strong_id_matrix`")
open class StrongIdMatrix protected constructor() {
    @EmbeddedId
    @AttributeOverride(name = "value", column = Column(name = "`id`", updatable = false, length = 36))
    open lateinit var id: UuidTextId
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "`native_uuid`", updatable = false))
    open lateinit var nativeUuid: UuidNativeId
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "`snowflake_text`", updatable = false, length = 19))
    open lateinit var snowflakeText: SnowflakeTextId
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "`snowflake_long`", updatable = false))
    open lateinit var snowflakeLong: SnowflakeLongId
        protected set

    constructor(
        id: UuidTextId,
        nativeUuid: UuidNativeId,
        snowflakeText: SnowflakeTextId,
        snowflakeLong: SnowflakeLongId,
    ) : this() {
        this.id = id
        this.nativeUuid = nativeUuid
        this.snowflakeText = snowflakeText
        this.snowflakeLong = snowflakeLong
    }
}

interface StrongIdMatrixRepository : JpaRepository<StrongIdMatrix, UuidTextId>

@Embeddable
class StrongContentId protected constructor() : StrongId<String>, Serializable {
    @Column(name = "value", nullable = false, updatable = false, length = 36)
    override lateinit var value: String
        protected set

    constructor(value: String) : this() {
        this.value = StrongIds.requireUuidV7(value, "StrongContentId")
    }

    companion object {
        fun parse(value: String): StrongContentId = StrongContentId(value)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is StrongContentId && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

@Embeddable
class StrongAuthorId protected constructor() : StrongId<String>, Serializable {
    @Column(name = "value", nullable = false, updatable = false, length = 36)
    override lateinit var value: String
        protected set

    constructor(value: String) : this() {
        this.value = StrongIds.requireUuidV7(value, "StrongAuthorId")
    }

    companion object {
        fun parse(value: String): StrongAuthorId = StrongAuthorId(value)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is StrongAuthorId && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

@Embeddable
class StrongMediaProcessingTaskId protected constructor() : StrongId<String>, Serializable {
    @Column(name = "value", nullable = false, updatable = false, length = 36)
    override lateinit var value: String
        protected set

    constructor(value: String) : this() {
        this.value = StrongIds.requireUuidV7(value, "StrongMediaProcessingTaskId")
    }

    companion object {
        fun parse(value: String): StrongMediaProcessingTaskId = StrongMediaProcessingTaskId(value)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is StrongMediaProcessingTaskId && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

@Embeddable
class StrongContentItemId protected constructor() : StrongId<String>, Serializable {
    @Column(name = "value", nullable = false, updatable = false, length = 36)
    override lateinit var value: String
        protected set

    constructor(value: String) : this() {
        this.value = StrongIds.requireUuidV7(value, "StrongContentItemId")
    }

    companion object {
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
                it.authorId = StrongAuthorId.parse("019c0000-0000-7000-8000-000000000003")
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
