package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.share.Constants
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.ReliableFailureFacts
import com.only4.cap4k.ddd.core.share.ReliableFailureOperation
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.only4.cap4k.ddd.domain.event.persistence.Event
import com.only4.cap4k.ddd.domain.event.persistence.TestEvent
import com.only4.cap4k.ddd.domain.event.persistence.UserCreatedEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class EventRecordImplTest {
    private val scheduleAt = LocalDateTime.of(2026, 8, 8, 10, 30)

    @Test
    fun `persistence initialization does not consume a delivery attempt`() {
        val record = newRecord(TestEvent("test", 12345))

        assertEquals(0, record.event.triedTimes)
        assertEquals(scheduleAt, record.event.nextTryTime)
        assertThrows(IllegalStateException::class.java) { record.deliveryAttempt }
    }

    @Test
    fun `claimed carrier exposes the current positive delivery attempt`() {
        val record = newRecord(TestEvent("test", 12345))
        record.event.triedTimes = 1

        assertEquals(1, record.deliveryAttempt)
    }

    @Test
    fun `message headers describe a persisted Domain Event`() {
        val record = newRecord(TestEvent("test", 12345)).apply {
            event.triedTimes = 1
            markPersist(true)
        }

        val headers = record.message.headers

        assertEquals(record.id, headers[Constants.HEADER_KEY_CAP4K_EVENT_ID])
        assertEquals(
            Constants.HEADER_VALUE_CAP4K_EVENT_TYPE_DOMAIN,
            headers[Constants.HEADER_KEY_CAP4K_EVENT_TYPE],
        )
        assertEquals(true, headers[Constants.HEADER_KEY_CAP4K_PERSIST])
        assertEquals(record.publishedAt.toEpochMilli(), headers[Constants.HEADER_KEY_CAP4K_TIMESTAMP])
    }

    @Test
    fun `message headers distinguish an Integration Event`() {
        val record = newRecord(UserCreatedEvent("user-1", "name", "mail@example.com"))

        assertEquals(
            Constants.HEADER_VALUE_CAP4K_EVENT_TYPE_INTEGRATION,
            record.message.headers[Constants.HEADER_KEY_CAP4K_EVENT_TYPE],
        )
    }

    @Test
    fun `persistence initialization rejects a blank Integration Event name`() {
        val failure = assertThrows(DomainException::class.java) {
            newRecord(BlankNamedIntegrationEvent("invalid"))
        }

        assertTrue(failure.message.orEmpty().contains("non-blank event name"))
    }

    @Test
    fun `resume rebinds the carrier without changing persistence ownership`() {
        val record = EventRecordImpl().apply { markPersist(true) }
        val event = Event().init(
            payload = TestEvent("stored", 1),
            svcName = "test-service",
            scheduleAt = scheduleAt,
            expireAfter = Duration.ofHours(1),
            retryTimes = 3,
        )

        record.resume(event)

        assertEquals(event, record.event)
        assertEquals(event.eventUuid, record.id)
        assertEquals(event.payload, record.payload)
        assertTrue(record.isPersist)
    }

    @Test
    fun `failure projection exposes only structured persisted facts`() {
        val record = newRecord(TestEvent("test", 12345))
        val facts = ReliableFailureFacts.capture(
            operation = ReliableFailureOperation.EVENT_DELIVERY,
            throwable = IllegalStateException("business-secret"),
            occurredAt = scheduleAt.plusMinutes(1),
            attempt = 1,
            correlationId = record.id,
            retryable = true,
        )
        record.event.failureFactsJson = RuntimeJson.write(facts)

        assertEquals(facts, record.failure)
        assertFalse(record.event.failureFactsJson!!.contains("business-secret"))
    }

    @IntegrationEvent("   ")
    private data class BlankNamedIntegrationEvent(val value: String)

    private fun newRecord(payload: Any): EventRecordImpl = EventRecordImpl().apply {
        init(
            payload = payload,
            svcName = "test-service",
            scheduleAt = scheduleAt,
            expireAfter = Duration.ofHours(1),
            retryTimes = 3,
            executionContext = emptyList(),
        )
    }
}
