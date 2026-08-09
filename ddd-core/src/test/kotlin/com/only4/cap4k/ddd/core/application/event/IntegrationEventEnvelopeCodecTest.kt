package com.only4.cap4k.ddd.core.application.event

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.id.StrongId
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class IntegrationEventEnvelopeCodecTest {
    private val codec = IntegrationEventEnvelopeCodec()

    @Test
    fun `canonical envelope round trips metadata nested null defaults strong ids and context`() {
        val payload = TestIntegrationEvent(
            id = TestStrongId.create("id-1"),
            nested = NestedPayload(values = listOf("b", "a")),
        )
        val event = eventRecord(payload)

        val first = codec.encode(event)
        val second = codec.encode(event)
        val decoded = codec.decode(first)
        val restored = codec.payloadJson(decoded, TestIntegrationEvent::class.java) as TestIntegrationEvent

        assertEquals(first, second)
        assertEquals("event-1", decoded.eventId)
        assertEquals("user.created", decoded.eventType)
        assertEquals("content-service", decoded.originService)
        assertEquals(2, decoded.deliveryAttempt)
        assertEquals(listOf("a", "z"), decoded.executionContext.map { it.name })
        assertEquals(payload, restored)
        assertTrue(decoded.payloadJson.contains("\"nullable\":null"))
        assertTrue(decoded.payloadJson.contains("\"id\":\"id-1\""))
    }

    @Test
    fun `delivery metadata preserves the sender attempt and stable subscriber context`() {
        val envelope = codec.decode(codec.encode(eventRecord(TestIntegrationEvent(
            TestStrongId.create("id-2"),
            NestedPayload(listOf("one")),
        ))))

        val context = envelope.deliveryContext(
            IntegrationEventDeliveryMetadata(
                subscriberIdentity = "media-service",
                redeliveryHint = ReliableEventRedeliveryHint.REDELIVERED,
            )
        )

        assertEquals("event-1", context.eventId)
        assertEquals("user.created", context.eventName)
        assertEquals(2, context.attempt)
        assertEquals("media-service", context.subscriberIdentity)
        assertEquals(ReliableEventRedeliveryHint.REDELIVERED, context.redeliveryHint)
    }

    @Test
    fun `provider attempt is used only when the envelope attempt is unknown`() {
        val envelope = codec.decode(codec.encode(eventRecord(TestIntegrationEvent(
            TestStrongId.create("id-4"),
            NestedPayload(listOf("one")),
        )))).copy(deliveryAttempt = null)

        val context = envelope.deliveryContext(
            IntegrationEventDeliveryMetadata(
                subscriberIdentity = "media-service",
                providerDeliveryAttempt = 3,
            )
        )

        assertEquals(3, context.attempt)
    }

    @Test
    fun `malformed metadata payload and entity facts fail without leaking business payload`() {
        val invalid = """{"eventId":"event-1","eventType":"user.created","originService":"content-service","publishedAt":"bad","deliveryAttempt":0,"executionContext":[],"payloadJson":"secret-payload"}"""
        val failure = assertThrows<IntegrationEventEnvelopeDecodingException> { codec.decode(invalid) }
        assertFalse(failure.message.orEmpty().contains("secret-payload"))

        val entityFailure = assertThrows<IntegrationEventEnvelopeEncodingException> {
            codec.encode(eventRecord(TestIntegrationEvent(
                TestStrongId.create("id-3"),
                NestedPayload(listOf("one"), PersistentEntity("db-1")),
            )))
        }
        assertFalse(entityFailure.message.orEmpty().contains("db-1"))
    }

    private fun eventRecord(payload: TestIntegrationEvent): EventRecord = mockk {
        every { id } returns "event-1"
        every { type } returns "user.created"
        every { originService } returns "content-service"
        every { this@mockk.payload } returns payload
        every { executionContext } returns listOf(
            EncodedExecutionContextElement("z", 1, "two"),
            EncodedExecutionContextElement("a", 2, "one"),
        )
        every { publishedAt } returns Instant.parse("2026-08-08T00:00:00Z")
        every { deliveryAttempt } returns 2
    }

    @IntegrationEvent("user.created")
    data class TestIntegrationEvent(
        val id: TestStrongId,
        val nested: NestedPayload,
        val nullable: String? = null,
        val defaulted: Int = 7,
    )

    data class NestedPayload(
        val values: List<String>,
        val entity: PersistentEntity? = null,
    )

    @jakarta.persistence.Entity
    class PersistentEntity(
        @jakarta.persistence.Id
        val id: String,
    )

    class TestStrongId private constructor(private val raw: String) : StrongId<String> {
        override val value: String get() = raw

        @JsonValue
        fun asJson(): String = raw

        override fun equals(other: Any?): Boolean = other is TestStrongId && raw == other.raw
        override fun hashCode(): Int = raw.hashCode()

        companion object {
            fun create(raw: String): TestStrongId = TestStrongId(raw)

            @JvmStatic
            @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
            fun fromJson(value: String): TestStrongId = TestStrongId(value)
        }
    }
}
