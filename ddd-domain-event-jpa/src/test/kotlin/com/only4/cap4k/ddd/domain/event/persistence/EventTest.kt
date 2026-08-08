package com.only4.cap4k.ddd.domain.event.persistence

import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.LocalDateTime

class EventTest {
    private val createdAt = LocalDateTime.of(2025, 1, 15, 10, 30)

    @Test
    fun `initializes an unclaimed domain event carrier`() {
        val payload = TestEvent("test message", 123456789L)
        val event = Event().init(payload, "test-service", createdAt, Duration.ofMinutes(30), 3)

        assertNotNull(event.eventUuid)
        assertEquals("test-service", event.svcName)
        assertEquals("test.event", event.eventType)
        assertEquals(createdAt, event.createAt)
        assertEquals(createdAt.plusMinutes(30), event.expireAt)
        assertEquals(Event.EventState.INIT, event.eventState)
        assertEquals(3, event.tryTimes)
        assertEquals(0, event.triedTimes)
        assertEquals(createdAt, event.lastTryTime)
        assertEquals(createdAt, event.nextTryTime)
        assertSame(payload, event.payload)
        assertEquals(payload::class.java.name, event.dataType)
        assertTrue(event.data.orEmpty().contains("test message"))
    }

    @Test
    fun `captures integration retry policy from annotation`() {
        val event = Event().init(
            PaymentProcessedEvent("payment789", 100.0, "completed"),
            "payment-service",
            createdAt,
            Duration.ofMinutes(10),
            2,
        )

        assertEquals(5, event.tryTimes)
        assertEquals(createdAt.plusMinutes(30), event.expireAt)
        assertTrue(event.retryPolicy.contains("retryLimit"))
        assertFalse(event.retryPolicy.contains("payment789"))
    }

    @Test
    fun `rejects unannotated and persistence-bound payloads`() {
        assertThrows<DomainException> {
            Event().init(SimpleEvent("simple", "value"), "service", createdAt, Duration.ofMinutes(5), 1)
        }

        val failure = assertThrows<DomainException> {
            Event().init(
                EntityBackedEvent(PersistentEntity("entity-1")),
                "service",
                createdAt,
                Duration.ofMinutes(5),
                1,
            )
        }
        assertTrue(failure.message.orEmpty().contains("persistent Entity reference"))
        assertFalse(failure.message.orEmpty().contains("entity-1"))
    }

    @Test
    fun `loads and caches payload from persisted json`() {
        val event = Event(
            data = RuntimeJson.write(UserCreatedEvent("user123", "john", "john@test.com")),
            dataType = UserCreatedEvent::class.java.name,
        )

        val first = event.payload
        val second = event.payload

        assertNotNull(first)
        assertSame(first, second)
        assertEquals("user123", (first as UserCreatedEvent).userId)
    }

    @Test
    fun `keeps state enum database mapping stable`() {
        val converter = Event.EventState.Converter()
        assertEquals(0, converter.convertToDatabaseColumn(Event.EventState.INIT))
        assertEquals(-1, converter.convertToDatabaseColumn(Event.EventState.DELIVERING))
        assertEquals(Event.EventState.DELIVERED, converter.convertToEntityAttribute(1))
        assertEquals(Event.EventState.EXCEPTION, converter.convertToEntityAttribute(-9))
        assertTrue(Event.EventState.entries.contains(Event.EventState.EXHAUSTED))
    }

    @Test
    fun `renders only safe diagnostic fields`() {
        val event = Event().init(TestEvent("secret-payload", 12345), "test-service", createdAt, Duration.ofMinutes(10), 3)
        val result = event.toString()

        assertTrue(result.contains("eventUuid"))
        assertTrue(result.contains("test-service"))
        assertTrue(result.contains("state=init"))
        assertFalse(result.contains("secret-payload"))
        assertFalse(result.contains("data="))
    }
}
