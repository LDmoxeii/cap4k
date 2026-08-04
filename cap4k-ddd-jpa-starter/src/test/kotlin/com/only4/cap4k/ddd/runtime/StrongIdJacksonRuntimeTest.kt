package com.only4.cap4k.ddd.runtime

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.only4.cap4k.ddd.core.domain.id.StrongId
import com.only4.cap4k.ddd.core.domain.id.StrongIds
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.Serializable
import java.util.UUID

private const val UUID7_TEXT = "019c0000-0000-7000-8000-000000000001"
private const val UUID4_TEXT = "550e8400-e29b-41d4-a716-446655440000"

class StrongIdJacksonRuntimeTest {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @Test
    fun `jackson serializes UUID7 strong id backings as scalar strings`() {
        val payload = Payload(
            uuidText = UuidTextId.parse(UUID7_TEXT),
            uuidNative = UuidNativeId.parse(UUID7_TEXT),
        )

        val json = objectMapper.writeValueAsString(payload)

        assertEquals(
            """{"uuidText":"$UUID7_TEXT","uuidNative":"$UUID7_TEXT"}""",
            json,
        )
    }

    @Test
    fun `jackson deserializes UUID7 scalar string strong id backings`() {
        val payload = objectMapper.readValue(
            """{"uuidText":"$UUID7_TEXT","uuidNative":"$UUID7_TEXT"}""",
            Payload::class.java,
        )

        assertEquals(UuidTextId.parse(UUID7_TEXT), payload.uuidText)
        assertEquals(UuidNativeId.parse(UUID7_TEXT), payload.uuidNative)
    }

    @Test
    fun `jackson rejects object tokens for every strong id backing`() {
        listOf(
            Pair("""{"uuidText":{"value":"$UUID7_TEXT"}}""", UuidTextPayload::class.java),
            Pair("""{"uuidNative":{"value":"$UUID7_TEXT"}}""", UuidNativePayload::class.java),
        ).forEach { (json, payloadType) ->
            val error = assertThrows(Exception::class.java) {
                objectMapper.readValue(json, payloadType)
            }

            assertTrue(error.causeMessages().any { it.contains("JSON value must be a string") })
        }
    }

    @Test
    fun `jackson rejects semantically invalid textual values for every strong id backing`() {
        assertAll(
            {
                assertSemanticRejection(
                    """{"uuidText":"$UUID4_TEXT"}""",
                    UuidTextPayload::class.java,
                    "UuidTextId must be a UUIDv7 value",
                )
            },
            {
                assertSemanticRejection(
                    """{"uuidNative":"$UUID4_TEXT"}""",
                    UuidNativePayload::class.java,
                    "UuidNativeId must be a UUIDv7 value",
                )
            },
        )
    }

    data class Payload(
        val uuidText: UuidTextId,
        val uuidNative: UuidNativeId,
    )

    data class UuidTextPayload(val uuidText: UuidTextId)
    data class UuidNativePayload(val uuidNative: UuidNativeId)

    private fun Throwable.causeMessages(): List<String> =
        generateSequence(this) { it.cause }.mapNotNull { it.message }.toList()

    private fun <T> assertSemanticRejection(json: String, payloadType: Class<T>, expectedMessage: String) {
        val error = assertThrows(Exception::class.java) {
            objectMapper.readValue(json, payloadType)
        }

        assertTrue(error.causeMessages().any { it.contains(expectedMessage) })
    }
}

@Embeddable
class UuidTextId protected constructor() : StrongId<String>, Serializable {
    @Column(name = "value", nullable = false, updatable = false)
    override lateinit var value: String
        protected set

    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    private constructor(value: String) : this() {
        this.value = value
    }

    @JsonValue
    fun jsonValue(): String = value.toString()

    override fun toString(): String = value.toString()

    companion object {
        fun of(value: String): UuidTextId =
            UuidTextId(StrongIds.requireUuidV7(value, "UuidTextId"))

        fun parse(value: String): UuidTextId = of(value)

        @JvmStatic
        fun from(value: String): UuidTextId = parse(value)

        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun fromJson(value: JsonNode): UuidTextId {
            require(value.isTextual) { "UuidTextId JSON value must be a string" }
            return parse(value.textValue())
        }
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

    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    private constructor(value: UUID) : this() {
        this.value = value
    }

    @JsonValue
    fun jsonValue(): String = value.toString()

    override fun toString(): String = value.toString()

    companion object {
        fun of(value: UUID): UuidNativeId =
            UuidNativeId(StrongIds.requireUuidV7(value, "UuidNativeId"))

        fun parse(value: String): UuidNativeId =
            of(UUID.fromString(StrongIds.requireUuidV7(value, "UuidNativeId")))

        @JvmStatic
        fun from(value: String): UuidNativeId = parse(value)

        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun fromJson(value: JsonNode): UuidNativeId {
            require(value.isTextual) { "UuidNativeId JSON value must be a string" }
            return parse(value.textValue())
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is UuidNativeId && value == other.value)

    override fun hashCode(): Int = value.hashCode()
}
