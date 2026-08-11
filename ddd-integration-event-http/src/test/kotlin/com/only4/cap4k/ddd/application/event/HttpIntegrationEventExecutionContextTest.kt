package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElementCodec
import com.only4.cap4k.ddd.core.application.context.ExecutionContextKey
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultReliableEventDeliveryContextManager
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class HttpIntegrationEventExecutionContextTest {
    @Test
    fun `consumer installs external context before dispatch and clears it afterwards`() {
        val contextManager = DefaultExecutionContextManager()
        val codecRegistry = ExecutionContextCodecRegistry(listOf(TransportContextCodec))
        val subscriberManager = mockk<EventHandlerDispatcher>()
        var observedContext: TransportContext? = null
        every { subscriberManager.dispatch(any()) } answers {
            observedContext = contextManager.current()[TransportContextKey]
        }
        val adapter = HttpIntegrationEventSubscriberAdapter(
            eventHandlerDispatcher = subscriberManager,
            eventMessageInterceptors = emptyList(),
            eventTypeCatalog = SingleEventCatalog,
            executionContextCodecRegistry = codecRegistry,
            executionContextScopeManager = contextManager,
            reliableEventDeliveryContextScopeManager = DefaultReliableEventDeliveryContextManager(contextManager, contextManager),
        )
        val envelope = IntegrationEventEnvelopeCodec().encode(
            IntegrationEventEnvelope(
                eventId = "event-123",
                eventType = "context.transport.event",
                originService = "test-source",
                publishedAt = Instant.parse("2026-08-04T00:00:00Z"),
                deliveryAttempt = null,
                executionContext = codecRegistry.encode(
                    originSnapshot(),
                    ExecutionContextBoundary.INTEGRATION_EVENT,
                ),
                payloadJson = RuntimeJson.write(ContextTransportEvent("payload")),
            )
        )

        val consumed = adapter.consume(envelope)

        assertTrue(consumed.success)
        assertEquals(TransportContext("origin"), observedContext)
        assertTrue(contextManager.current().isEmpty)
    }

    private object SingleEventCatalog : InboundIntegrationEventRegistrationView {
        override fun integrationEventTypes(): Set<Class<*>> = setOf(ContextTransportEvent::class.java)
    }

    private fun originSnapshot(): ExecutionContextSnapshot = ExecutionContextSnapshot.builder()
        .put(TransportContextKey, TransportContext("origin"))
        .build()
}

@IntegrationEvent("context.transport.event")
internal data class ContextTransportEvent(val value: String)

private data class TransportContext(val value: String) : ExecutionContextElement

private val TransportContextKey = ExecutionContextKey("transport-context", TransportContext::class.java)

private object TransportContextCodec : ExecutionContextElementCodec<TransportContext> {
    override val key: ExecutionContextKey<TransportContext> = TransportContextKey
    override val version: Int = 1
    override val boundaries: Set<ExecutionContextBoundary> = setOf(ExecutionContextBoundary.INTEGRATION_EVENT)
    override fun encode(element: TransportContext): String = element.value
    override fun decode(value: String): TransportContext = TransportContext(value)
}
