package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4K_EVENT_TYPE
import com.only4.cap4k.ddd.core.share.Constants.HEADER_VALUE_CAP4K_EVENT_TYPE_DOMAIN
import com.only4.cap4k.ddd.core.share.Constants.HEADER_VALUE_CAP4K_EVENT_TYPE_INTEGRATION
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.springframework.messaging.support.GenericMessage
import java.time.Instant

class DefaultEventPublisherTest {
    private val messages = mockk<EventMessageInterceptorManager> {
        every { orderedEventMessageInterceptors } returns emptySet()
    }
    private val domainInterceptors = mockk<DomainEventInterceptorManager> {
        every { orderedDomainEventInterceptors } returns emptySet()
        every { orderedEventInterceptors4DomainEvent } returns emptySet()
    }
    private val integrationInterceptors = mockk<IntegrationEventInterceptorManager> {
        every { orderedIntegrationEventInterceptors } returns emptySet()
        every { orderedEventInterceptors4IntegrationEvent } returns emptySet()
    }
    private val deliveryScopes = TrackingDeliveryScopeManager()

    @Test
    fun `domain completion occurs after synchronous dispatch and context is cleared`() {
        val payload = DomainPayload("one")
        val event = event(payload, HEADER_VALUE_CAP4K_EVENT_TYPE_DOMAIN, attempt = 1)
        val completion = mockk<EventPublisher.Completion>(relaxed = true)
        var dispatched = false
        val publisher = publisher(
            dispatcher = EventHandlerDispatcher { actual ->
                assertSame(payload, actual)
                assertEquals("event-1", deliveryScopes.current?.eventId)
                assertEquals(1, deliveryScopes.current?.attempt)
                dispatched = true
            },
        )

        publisher.publish(event, completion)

        assertEquals(true, dispatched)
        verify(exactly = 1) { completion.onSuccess(event) }
        verify(exactly = 0) { completion.onFailure(any(), any()) }
        assertNull(deliveryScopes.current)
    }

    @Test
    fun `domain failure completes through failure callback and clears context`() {
        val failure = IllegalStateException("handler failed")
        val event = event(DomainPayload("failure"), HEADER_VALUE_CAP4K_EVENT_TYPE_DOMAIN, attempt = 2)
        val completion = mockk<EventPublisher.Completion>(relaxed = true)
        val publisher = publisher(dispatcher = EventHandlerDispatcher { throw failure })

        publisher.publish(event, completion)

        verify(exactly = 1) { completion.onFailure(event, failure) }
        verify(exactly = 0) { completion.onSuccess(any()) }
        assertNull(deliveryScopes.current)
    }

    @Test
    fun `outbound handoff acknowledges only after provider acceptance`() {
        val event = event(IntegrationPayload("outbound"), HEADER_VALUE_CAP4K_EVENT_TYPE_INTEGRATION, attempt = 1)
        val completion = mockk<EventPublisher.Completion>(relaxed = true)
        var callback: IntegrationEventPublisher.PublishCallback? = null
        val provider = object : IntegrationEventPublisher {
            override fun publish(event: EventRecord, envelope: com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope, publishCallback: IntegrationEventPublisher.PublishCallback) {
                assertEquals("event-1", deliveryScopes.current?.eventId)
                callback = publishCallback
            }
        }
        val publisher = publisher(integrationPublishers = listOf(provider))

        publisher.publish(event, completion)
        verify(exactly = 0) { completion.onSuccess(any()) }

        requireNotNull(callback).onSuccess(event)

        verify(exactly = 1) { completion.onSuccess(event) }
        assertNull(deliveryScopes.current)
    }

    @Test
    fun `provider rejection completes through the same failure callback`() {
        val event = event(IntegrationPayload("outbound"), HEADER_VALUE_CAP4K_EVENT_TYPE_INTEGRATION, attempt = 3)
        val completion = mockk<EventPublisher.Completion>(relaxed = true)
        val failure = IllegalArgumentException("rejected")
        val provider = object : IntegrationEventPublisher {
            override fun publish(event: EventRecord, envelope: com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope, publishCallback: IntegrationEventPublisher.PublishCallback) {
                publishCallback.onException(event, failure)
            }
        }

        publisher(integrationPublishers = listOf(provider)).publish(event, completion)

        verify(exactly = 1) { completion.onFailure(event, failure) }
        verify(exactly = 0) { completion.onSuccess(any()) }
        assertNull(deliveryScopes.current)
    }

    private fun publisher(
        dispatcher: EventHandlerDispatcher = EventHandlerDispatcher { },
        integrationPublishers: List<IntegrationEventPublisher> = emptyList(),
    ): DefaultEventPublisher = DefaultEventPublisher(
        eventHandlerDispatcher = dispatcher,
        integrationEventPublishers = integrationPublishers,
        eventMessageInterceptorManager = messages,
        domainEventInterceptorManager = domainInterceptors,
        integrationEventInterceptorManager = integrationInterceptors,
        reliableEventDeliveryContextScopeManager = deliveryScopes,
    )

    private fun event(payload: Any, kind: String, attempt: Int): EventRecord {
        val record = mockk<EventRecord>()
        every { record.id } returns "event-1"
        every { record.type } returns "test.event"
        every { record.originService } returns "test-service"
        every { record.payload } returns payload
        every { record.executionContext } returns emptyList()
        every { record.publishedAt } returns Instant.parse("2026-08-08T00:00:00Z")
        every { record.deliveryAttempt } returns attempt
        every { record.message } returns GenericMessage(payload, mapOf(HEADER_KEY_CAP4K_EVENT_TYPE to kind))
        return record
    }

    private class TrackingDeliveryScopeManager : ReliableEventDeliveryContextScopeManager {
        var current: ReliableEventDeliveryContext? = null
            private set

        override fun install(context: ReliableEventDeliveryContext): AutoCloseable {
            val previous = current
            current = context
            return AutoCloseable { current = previous }
        }

        override fun suppress(): AutoCloseable {
            val previous = current
            current = null
            return AutoCloseable { current = previous }
        }
    }

    data class DomainPayload(val value: String)
    @IntegrationEvent("test.event")
    data class IntegrationPayload(val value: String)
}
