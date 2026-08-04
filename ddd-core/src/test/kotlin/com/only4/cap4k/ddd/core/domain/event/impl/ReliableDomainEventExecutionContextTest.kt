package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElementCodec
import com.only4.cap4k.ddd.core.application.context.ExecutionContextKey
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint
import com.only4.cap4k.ddd.core.share.Constants
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.messaging.support.GenericMessage
import java.time.Instant

class ReliableDomainEventExecutionContextTest {
    @Test
    fun `reliable event handler observes producer and delivery contexts and worker scope is cleared`() {
        val contextManager = DefaultExecutionContextManager()
        val codecRegistry = ExecutionContextCodecRegistry(listOf(TestContextCodec))
        val deliveryContextManager = DefaultReliableEventDeliveryContextManager(contextManager, contextManager)
        val eventHandlerDispatcher = mockk<EventHandlerDispatcher>()
        var observedContext: TestContext? = null
        var observedDeliveryContext: ReliableEventDeliveryContext? = null
        every { eventHandlerDispatcher.dispatch(any()) } answers {
            observedContext = contextManager.current()[TestContextKey]
            observedDeliveryContext = deliveryContextManager.currentOrNull()
        }
        val event = mockk<EventRecord>()
        every { event.executionContext } returns listOf(
            EncodedExecutionContextElement("test-context", 1, "origin-actor"),
        )
        every { event.payload } returns "event"
        every { event.id } returns "event-1"
        every { event.type } returns "test.event"
        every { event.publishedAt } returns Instant.parse("2026-08-04T00:00:00Z")
        every { event.deliveryAttempt } returns 2
        every { event.message } returns GenericMessage(
            "event",
            mapOf(
                Constants.HEADER_KEY_CAP4K_EVENT_TYPE to Constants.HEADER_VALUE_CAP4K_EVENT_TYPE_DOMAIN,
                Constants.HEADER_KEY_CAP4K_PERSIST to false,
            ),
        )
        every { event.endDelivery(any()) } just runs

        val publisher = TestEventPublisher(
            eventHandlerDispatcher = eventHandlerDispatcher,
            eventRecordRepository = mockk(relaxed = true),
            eventMessageInterceptorManager = mockk {
                every { orderedEventMessageInterceptors } returns emptySet()
            },
            domainEventInterceptorManager = mockk {
                every { orderedEventInterceptors4DomainEvent } returns emptySet()
            },
            integrationEventInterceptorManager = mockk(relaxed = true),
            integrationEventManager = mockk(relaxed = true),
            integrationEventPublisherCallback = mockk(relaxed = true),
            contextManager = contextManager,
            codecRegistry = codecRegistry,
            deliveryContextManager = deliveryContextManager,
        )

        publisher.publishDomain(event)

        assertEquals(TestContext("origin-actor"), observedContext)
        assertEquals(
            ReliableEventDeliveryContext(
                eventId = "event-1",
                eventName = "test.event",
                publishedAt = Instant.parse("2026-08-04T00:00:00Z"),
                attempt = 2,
                redeliveryHint = ReliableEventRedeliveryHint.REDELIVERED,
            ),
            observedDeliveryContext,
        )
        assertTrue(contextManager.current().isEmpty)
        assertNull(deliveryContextManager.currentOrNull())
    }

    private class TestEventPublisher(
        eventHandlerDispatcher: EventHandlerDispatcher,
        eventRecordRepository: EventRecordRepository,
        eventMessageInterceptorManager: EventMessageInterceptorManager,
        domainEventInterceptorManager: DomainEventInterceptorManager,
        integrationEventInterceptorManager: IntegrationEventInterceptorManager,
        integrationEventManager: IntegrationEventManager,
        integrationEventPublisherCallback: IntegrationEventPublisher.PublishCallback,
        contextManager: DefaultExecutionContextManager,
        codecRegistry: ExecutionContextCodecRegistry,
        deliveryContextManager: ReliableEventDeliveryContextScopeManager,
    ) : DefaultEventPublisher(
        eventHandlerDispatcher = eventHandlerDispatcher,
        integrationEventPublishers = emptyList(),
        eventRecordRepository = eventRecordRepository,
        eventMessageInterceptorManager = eventMessageInterceptorManager,
        domainEventInterceptorManager = domainEventInterceptorManager,
        integrationEventInterceptorManager = integrationEventInterceptorManager,
        integrationEventManager = integrationEventManager,
        integrationEventPublisherCallback = integrationEventPublisherCallback,
        threadPoolSize = 1,
        executionContextScopeManager = contextManager,
        executionContextCodecRegistry = codecRegistry,
        reliableEventDeliveryContextScopeManager = deliveryContextManager,
    ) {
        fun publishDomain(event: EventRecord) = internalPublish4DomainEvent(event)
    }

    private data class TestContext(val actor: String) : ExecutionContextElement

    private companion object {
        val TestContextKey = ExecutionContextKey("test-context", TestContext::class.java)

        val TestContextCodec = object : ExecutionContextElementCodec<TestContext> {
            override val key: ExecutionContextKey<TestContext> = TestContextKey
            override val version: Int = 1
            override val boundaries: Set<ExecutionContextBoundary> =
                setOf(ExecutionContextBoundary.RELIABLE_DOMAIN_EVENT)

            override fun encode(element: TestContext): String = element.actor

            override fun decode(value: String): TestContext = TestContext(value)
        }
    }
}
