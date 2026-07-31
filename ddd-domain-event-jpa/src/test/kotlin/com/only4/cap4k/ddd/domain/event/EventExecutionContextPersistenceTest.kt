package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextDecodingException
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import com.only4.cap4k.ddd.domain.event.persistence.ArchivedEvent
import com.only4.cap4k.ddd.domain.event.persistence.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.LocalDateTime

class EventExecutionContextPersistenceTest {
    @Test
    fun `payload and context are stored separately and archive preserves the original envelope`() {
        val context = listOf(EncodedExecutionContextElement("trace", 1, "trace-9"))
        val record = EventRecordImpl().apply {
            init(
                TestEvent("created"),
                "service",
                LocalDateTime.now(),
                Duration.ofMinutes(5),
                3,
                context,
            )
        }

        assertEquals(context, record.executionContext)
        assertNotEquals(record.event.data, record.event.executionContext)

        val archived = ArchivedEvent().archiveFrom(record.event)
        assertEquals(record.event.executionContext, archived.executionContext)
    }

    @Test
    fun `legacy null context decodes as empty`() {
        val record = EventRecordImpl().apply {
            resume(Event(executionContext = null))
        }

        assertEquals(emptyList<EncodedExecutionContextElement>(), record.executionContext)
    }

    @Test
    fun `malformed persisted envelope fails before event delivery`() {
        val record = EventRecordImpl().apply {
            resume(Event(executionContext = "not-json"))
        }

        assertThrows<ExecutionContextDecodingException> { record.executionContext }
    }

    @DomainEvent("test.context.event")
    private data class TestEvent(val value: String)
}
