package com.only4.cap4k.ddd.core.domain.event

import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextPropagation
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultReliableEventDeliveryContextManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.concurrent.Callable

class ReliableEventDeliveryContextTest {
    private val executionContexts = DefaultExecutionContextManager()
    private val deliveryContexts = DefaultReliableEventDeliveryContextManager(executionContexts, executionContexts)

    @Test
    fun `strict and nullable access distinguish active delivery from absence`() {
        assertNull(deliveryContexts.currentOrNull())
        assertThrows<IllegalStateException> { deliveryContexts.current() }

        val context = context("event-1")
        deliveryContexts.install(context).use {
            assertSame(context, deliveryContexts.current())
        }

        assertNull(deliveryContexts.currentOrNull())
    }

    @Test
    fun `nested suppression hides and then restores reliable delivery`() {
        val outer = context("event-outer")

        deliveryContexts.install(outer).use {
            deliveryContexts.suppress().use {
                assertNull(deliveryContexts.currentOrNull())
            }
            assertSame(outer, deliveryContexts.current())
        }

        assertNull(deliveryContexts.currentOrNull())
    }

    @Test
    fun `local context propagates to managed workers but has no transport codec`() {
        val context = context("event-async")
        val propagation = ExecutionContextPropagation(executionContexts, executionContexts)
        val codecRegistry = ExecutionContextCodecRegistry(emptyList())
        lateinit var captured: Callable<ReliableEventDeliveryContext?>

        deliveryContexts.install(context).use {
            captured = propagation.wrap(Callable { deliveryContexts.currentOrNull() })
            assertEquals(
                emptyList<Any>(),
                codecRegistry.encode(executionContexts.current(), ExecutionContextBoundary.INTEGRATION_EVENT),
            )
        }

        assertNull(deliveryContexts.currentOrNull())
        assertEquals(context, captured.call())
        assertNull(deliveryContexts.currentOrNull())
    }

    @Test
    fun `context rejects ambiguous required metadata and nonpositive exact attempts`() {
        assertThrows<IllegalArgumentException> { context("") }
        assertThrows<IllegalArgumentException> { context("event", eventName = "") }
        assertThrows<IllegalArgumentException> { context("event", attempt = 0) }
    }

    private fun context(
        eventId: String,
        eventName: String = "OrderCreated",
        attempt: Int? = 1,
    ): ReliableEventDeliveryContext = ReliableEventDeliveryContext(
        eventId = eventId,
        eventName = eventName,
        publishedAt = Instant.parse("2026-08-04T00:00:00Z"),
        attempt = attempt,
        redeliveryHint = ReliableEventRedeliveryHint.FIRST,
    )
}
