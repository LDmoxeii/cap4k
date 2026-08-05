package com.only4.cap4k.ddd.core.share.json

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.only4.cap4k.ddd.core.domain.id.StrongId
import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RuntimeJsonTest {
    @Test
    fun `round trips kotlin defaults null collections nested objects and private constructor`() {
        val original = PrivatePayload.create(
            title = "runtime",
            nested = NestedPayload(listOf("b", "a")),
            tags = mapOf("z" to 2, "a" to 1),
        )

        val json = RuntimeJson.write(original)
        val restored = RuntimeJson.read(json, PrivatePayload::class.java)

        assertEquals(original, restored)
        assertTrue(json.indexOf("\"a\"") < json.indexOf("\"z\""))
        assertTrue(json.contains("\"nullable\":null"))
    }

    @Test
    fun `strong ids remain scalar strings`() {
        val json = RuntimeJson.write(StrongPayload(StrongTestId.create("id-1")))

        assertEquals("{\"id\":\"id-1\"}", json)
        assertEquals(StrongTestId.create("id-1"), RuntimeJson.read(json, StrongPayload::class.java).id)
    }

    @Test
    fun `execution context envelope is deterministic and round trips`() {
        val elements = listOf(
            EncodedExecutionContextElement("z", 1, "two"),
            EncodedExecutionContextElement("a", 2, "one"),
        )

        val json = RuntimeExecutionContextJson.encode(elements, "test envelope")
        val restored = RuntimeExecutionContextJson.decode(json, "test envelope")

        assertEquals("[{\"name\":\"a\",\"version\":2,\"value\":\"one\"},{\"name\":\"z\",\"version\":1,\"value\":\"two\"}]", json)
        assertEquals(elements.sortedBy { it.name }, restored)
    }

    @Test
    fun `decode failures never expose raw business payload`() {
        val rawPayload = "secret-payload-42"

        val failure = assertThrows<RuntimeJsonDecodingException> {
            RuntimeJson.read("{\"value\":\"$rawPayload\"}", StrictPayload::class.java)
        }

        assertTrue(failure.message.orEmpty().contains(StrictPayload::class.java.name))
        assertTrue(!failure.message.orEmpty().contains(rawPayload))
    }

    private data class NestedPayload(val values: List<String>)

    private data class PrivatePayload private constructor(
        val title: String,
        val nested: NestedPayload,
        val tags: Map<String, Int>,
        val nullable: String? = null,
        val defaulted: Int = 7,
    ) {
        companion object {
            fun create(
                title: String,
                nested: NestedPayload,
                tags: Map<String, Int>,
            ) = PrivatePayload(title, nested, tags)
        }
    }

    private data class StrongPayload(val id: StrongTestId)

    private data class StrictPayload(val value: Int)

    private class StrongTestId private constructor(private val raw: String) : StrongId<String> {
        override val value: String get() = raw

        @JsonValue
        fun asJson(): String = raw

        override fun equals(other: Any?): Boolean = other is StrongTestId && raw == other.raw
        override fun hashCode(): Int = raw.hashCode()

        companion object {
            fun create(raw: String): StrongTestId = StrongTestId(raw)

            @JvmStatic
            @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
            fun fromJson(value: String): StrongTestId = StrongTestId(value)
        }
    }
}
