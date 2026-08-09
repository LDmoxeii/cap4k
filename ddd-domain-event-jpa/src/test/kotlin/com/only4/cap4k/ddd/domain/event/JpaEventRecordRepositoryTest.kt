package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.domain.event.persistence.Event
import com.only4.cap4k.ddd.domain.event.persistence.EventJpaRepository
import com.only4.cap4k.ddd.domain.event.persistence.TestEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.jpa.domain.Specification
import java.time.Duration
import java.time.LocalDateTime
import java.util.Optional

class JpaEventRecordRepositoryTest {
    private val records = mockk<EventJpaRepository>()
    private val repository = JpaEventRecordRepository(records)
    private val scheduleAt = LocalDateTime.of(2026, 8, 8, 10, 30)

    @Test
    fun `create returns a fresh JPA carrier`() {
        val first = repository.create()
        val second = repository.create()

        assertTrue(first is EventRecordImpl)
        assertTrue(second is EventRecordImpl)
        assertNotSame(first, second)
    }

    @Test
    fun `save persists and rebinds the returned entity`() {
        val record = EventRecordImpl().apply {
            init(
                payload = TestEvent("test", 12345),
                svcName = "test-service",
                scheduleAt = scheduleAt,
                expireAfter = Duration.ofHours(1),
                retryTimes = 3,
                executionContext = emptyList(),
            )
        }
        val original = record.event
        val saved = Event().apply { eventUuid = "saved-event" }
        every { records.save(original) } returns saved

        repository.save(record)

        verify(exactly = 1) { records.save(original) }
        assertSame(saved, record.event)
    }

    @Test
    fun `getById hydrates the persisted carrier`() {
        val stored = Event().init(
            payload = TestEvent("stored", 1),
            svcName = "test-service",
            scheduleAt = scheduleAt,
            expireAfter = Duration.ofHours(1),
            retryTimes = 3,
        )
        every { records.findOne(any<Specification<Event>>()) } returns Optional.of(stored)

        val result = repository.getById(stored.eventUuid)

        assertTrue(result is EventRecordImpl)
        assertSame(stored, (result as EventRecordImpl).event)
        assertEquals(stored.eventUuid, result.id)
    }

    @Test
    fun `getById reports an absent record`() {
        every { records.findOne(any<Specification<Event>>()) } returns Optional.empty()

        val failure = assertThrows<DomainException> { repository.getById("missing") }

        assertEquals("EventRecord not found", failure.message)
    }
}
