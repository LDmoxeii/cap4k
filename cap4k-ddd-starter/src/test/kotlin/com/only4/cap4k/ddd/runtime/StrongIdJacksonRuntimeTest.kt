package com.only4.cap4k.ddd.runtime

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.only4.cap4k.ddd.core.domain.id.StrongId
import com.only4.cap4k.ddd.core.domain.id.StrongIds
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.Serializable

class StrongIdJacksonRuntimeTest {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @Test
    fun `jackson serializes strong id as string`() {
        val id = StrongContentId.new()

        val json = objectMapper.writeValueAsString(Payload(id))

        assertEquals("""{"id":"${id.value}"}""", json)
    }

    @Test
    fun `jackson deserializes strong id through validation`() {
        val id = StrongContentId.new()

        val payload = objectMapper.readValue("""{"id":"${id.value}"}""", Payload::class.java)

        assertEquals(id, payload.id)
    }

    @Test
    fun `jackson rejects non uuid v7 strong id`() {
        assertThrows(Exception::class.java) {
            objectMapper.readValue(
                """{"id":"550e8400-e29b-41d4-a716-446655440000"}""",
                Payload::class.java,
            )
        }
    }

    data class Payload(val id: StrongContentId)

    @Embeddable
    class StrongContentId protected constructor() : StrongId, Serializable {
        @Column(name = "`id`", nullable = false, updatable = false, length = 36)
        override lateinit var value: String
            protected set

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        constructor(value: String) : this() {
            this.value = StrongIds.requireUuidV7(value, "StrongContentId")
        }

        companion object {
            fun new(): StrongContentId = StrongContentId(StrongIds.newUuidV7String())

            fun parse(value: String): StrongContentId = StrongContentId(value)
        }

        @com.fasterxml.jackson.annotation.JsonValue
        fun jsonValue(): String = value

        override fun equals(other: Any?): Boolean =
            this === other || (other is StrongContentId && value == other.value)

        override fun hashCode(): Int = value.hashCode()

        override fun toString(): String = value
    }
}
