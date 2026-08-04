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
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.share.Constants
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.messaging.support.GenericMessage

class ReliableDomainEventExecutionContextTest {
    @Test
    fun `reliable event handler observes producer context and worker scope is cleared`() {
        val contextManager = DefaultExecutionContextManager()
        val codecRegistry = ExecutionContextCodecRegistry(listOf(TestContextCodec))
        val eventHandlerDispatcher = mockk<EventHandlerDispatcher>()
        var observedContext: TestContext? = null
        every { eventHandlerDispatcher.dispatch(any()) } answers {
            observedContext = contextManager.current()[TestContextKey]
        }
        val event = mockk<EventRecord>()
        every { event.executionContext } returns listOf(
            EncodedExecutionContextElement("test-context", 1, "origin-actor"),
        )
        every { event.payload } returns "event"
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
        )

        publisher.publishDomain(event)

        assertEquals(TestContext("origin-actor"), observedContext)
        assertTrue(contextManager.current().isEmpty)
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
