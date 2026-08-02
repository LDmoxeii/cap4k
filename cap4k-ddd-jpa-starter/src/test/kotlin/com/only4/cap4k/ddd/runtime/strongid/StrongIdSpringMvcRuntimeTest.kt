package com.only4.cap4k.ddd.runtime.strongid

import com.only4.cap4k.ddd.core.domain.id.StrongId
import com.only4.cap4k.ddd.core.domain.id.StrongIds
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.Serializable

private const val MVC_UUID7_TEXT = "019c0000-0000-7000-8000-000000000001"
private const val MVC_UUID4_TEXT = "550e8400-e29b-41d4-a716-446655440000"
private const val MVC_SNOWFLAKE_TEXT = "7288198123456789012"

@WebMvcTest(controllers = [StrongIdSpringMvcRuntimeTest.StrongIdController::class])
@ContextConfiguration(
    classes = [
        StrongIdSpringMvcRuntimeTest.TestApplication::class,
        StrongIdSpringMvcRuntimeTest.StrongIdController::class,
    ],
)
class StrongIdSpringMvcRuntimeTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `default conversion service recognizes generated strong id string factories`() {
        val conversionService = DefaultConversionService()

        assertAll(
            { assertTrue(conversionService.canConvert(String::class.java, UuidTextId::class.java)) },
            { assertTrue(conversionService.canConvert(String::class.java, UuidNativeId::class.java)) },
            { assertTrue(conversionService.canConvert(String::class.java, SnowflakeTextId::class.java)) },
            { assertTrue(conversionService.canConvert(String::class.java, SnowflakeLongId::class.java)) },
            { assertTrue(conversionService.canConvert(String::class.java, MvcAuthorId::class.java)) },
            { assertTrue(conversionService.canConvert(String::class.java, MvcContentItemId::class.java)) },
            { assertEquals(UuidTextId.parse(MVC_UUID7_TEXT), conversionService.convert(MVC_UUID7_TEXT, UuidTextId::class.java)) },
            { assertEquals(UuidNativeId.parse(MVC_UUID7_TEXT), conversionService.convert(MVC_UUID7_TEXT, UuidNativeId::class.java)) },
            {
                assertEquals(
                    SnowflakeTextId.parse(MVC_SNOWFLAKE_TEXT),
                    conversionService.convert(MVC_SNOWFLAKE_TEXT, SnowflakeTextId::class.java),
                )
            },
            {
                assertEquals(
                    SnowflakeLongId.parse(MVC_SNOWFLAKE_TEXT),
                    conversionService.convert(MVC_SNOWFLAKE_TEXT, SnowflakeLongId::class.java),
                )
            },
            { assertEquals(MvcAuthorId.parse(MVC_UUID7_TEXT), conversionService.convert(MVC_UUID7_TEXT, MvcAuthorId::class.java)) },
            {
                assertEquals(
                    MvcContentItemId.parse(MVC_UUID7_TEXT),
                    conversionService.convert(MVC_UUID7_TEXT, MvcContentItemId::class.java),
                )
            },
        )
    }

    @Test
    fun `mvc binds path variables for four strong id backings`() {
        mockMvc.perform(get("/strong-id/path/uuid-text/$MVC_UUID7_TEXT"))
            .andExpect(status().isOk)
            .andExpect(content().string(MVC_UUID7_TEXT))

        mockMvc.perform(get("/strong-id/path/uuid-native/$MVC_UUID7_TEXT"))
            .andExpect(status().isOk)
            .andExpect(content().string(MVC_UUID7_TEXT))

        mockMvc.perform(get("/strong-id/path/snowflake-text/$MVC_SNOWFLAKE_TEXT"))
            .andExpect(status().isOk)
            .andExpect(content().string(MVC_SNOWFLAKE_TEXT))

        mockMvc.perform(get("/strong-id/path/snowflake-long/$MVC_SNOWFLAKE_TEXT"))
            .andExpect(status().isOk)
            .andExpect(content().string(MVC_SNOWFLAKE_TEXT))
    }

    @Test
    fun `mvc binds query parameters for aggregate and non root strong ids`() {
        mockMvc.perform(
            get("/strong-id/query/matrix")
                .param("uuidText", MVC_UUID7_TEXT)
                .param("uuidNative", MVC_UUID7_TEXT)
                .param("snowflakeText", MVC_SNOWFLAKE_TEXT)
                .param("snowflakeLong", MVC_SNOWFLAKE_TEXT),
        )
            .andExpect(status().isOk)
            .andExpect(content().string("$MVC_UUID7_TEXT|$MVC_UUID7_TEXT|$MVC_SNOWFLAKE_TEXT|$MVC_SNOWFLAKE_TEXT"))

        mockMvc.perform(
            get("/strong-id/query/non-root")
                .param("authorId", MVC_UUID7_TEXT)
                .param("contentItemId", MVC_UUID7_TEXT),
        )
            .andExpect(status().isOk)
            .andExpect(content().string("$MVC_UUID7_TEXT|$MVC_UUID7_TEXT"))
    }

    @Test
    fun `mvc rejects invalid strong id path and query input with semantic diagnostics`() {
        val invalidPath = mockMvc.perform(get("/strong-id/path/uuid-text/$MVC_UUID4_TEXT"))
            .andExpect(status().isBadRequest)
            .andReturn()

        assertNotNull(invalidPath.resolvedException)
        assertTrue(
            invalidPath.resolvedException.causeMessages().any { it.contains("UuidTextId must be a UUIDv7 value") },
        )

        val invalidQuery = mockMvc.perform(
            get("/strong-id/query/matrix")
                .param("uuidText", MVC_UUID7_TEXT)
                .param("uuidNative", MVC_UUID7_TEXT)
                .param("snowflakeText", "01")
                .param("snowflakeLong", MVC_SNOWFLAKE_TEXT),
        )
            .andExpect(status().isBadRequest)
            .andReturn()

        assertNotNull(invalidQuery.resolvedException)
        assertTrue(
            invalidQuery.resolvedException.causeMessages()
                .any { it.contains("SnowflakeTextId must be a positive canonical Snowflake value") },
        )
    }

    private fun Throwable?.causeMessages(): List<String> =
        generateSequence(this) { it?.cause }.mapNotNull { it?.message }.toList()

    @SpringBootApplication
    open class TestApplication

    @RestController
    class StrongIdController {
        @GetMapping("/strong-id/path/uuid-text/{id}")
        fun uuidText(@PathVariable id: UuidTextId): String = id.value

        @GetMapping("/strong-id/path/uuid-native/{id}")
        fun uuidNative(@PathVariable id: UuidNativeId): String = id.value.toString()

        @GetMapping("/strong-id/path/snowflake-text/{id}")
        fun snowflakeText(@PathVariable id: SnowflakeTextId): String = id.value

        @GetMapping("/strong-id/path/snowflake-long/{id}")
        fun snowflakeLong(@PathVariable id: SnowflakeLongId): String = id.value.toString()

        @GetMapping("/strong-id/query/matrix")
        fun matrix(
            @RequestParam uuidText: UuidTextId,
            @RequestParam uuidNative: UuidNativeId,
            @RequestParam snowflakeText: SnowflakeTextId,
            @RequestParam snowflakeLong: SnowflakeLongId,
        ): String = listOf(uuidText, uuidNative, snowflakeText, snowflakeLong).joinToString("|") { it.toString() }

        @GetMapping("/strong-id/query/non-root")
        fun nonRoot(
            @RequestParam authorId: MvcAuthorId,
            @RequestParam contentItemId: MvcContentItemId,
        ): String = listOf(authorId, contentItemId).joinToString("|") { it.toString() }
    }
}

@Embeddable
class MvcAuthorId protected constructor() : StrongId<String>, Serializable {
    @Column(name = "value", nullable = false, updatable = false, length = 36)
    override lateinit var value: String
        protected set

    private constructor(value: String) : this() {
        this.value = value
    }

    override fun toString(): String = value

    companion object {
        fun of(value: String): MvcAuthorId =
            MvcAuthorId(StrongIds.requireUuidV7(value, "MvcAuthorId"))

        fun parse(value: String): MvcAuthorId = of(value)

        @JvmStatic
        fun from(value: String): MvcAuthorId = parse(value)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is MvcAuthorId && value == other.value)

    override fun hashCode(): Int = value.hashCode()
}

@Embeddable
class MvcContentItemId protected constructor() : StrongId<String>, Serializable {
    @Column(name = "value", nullable = false, updatable = false, length = 36)
    override lateinit var value: String
        protected set

    private constructor(value: String) : this() {
        this.value = value
    }

    override fun toString(): String = value

    companion object {
        fun of(value: String): MvcContentItemId =
            MvcContentItemId(StrongIds.requireUuidV7(value, "MvcContentItemId"))

        fun parse(value: String): MvcContentItemId = of(value)

        @JvmStatic
        fun from(value: String): MvcContentItemId = parse(value)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is MvcContentItemId && value == other.value)

    override fun hashCode(): Int = value.hashCode()
}
