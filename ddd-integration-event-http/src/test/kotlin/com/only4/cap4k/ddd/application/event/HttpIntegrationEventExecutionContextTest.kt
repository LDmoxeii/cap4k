package com.only4.cap4k.ddd.application.event

import com.alibaba.fastjson.JSON
import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElementCodec
import com.only4.cap4k.ddd.core.application.context.ExecutionContextKey
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4K_EXECUTION_CONTEXT
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.env.Environment

class HttpIntegrationEventExecutionContextTest {
    @Test
    fun `consumer installs external context before dispatch and clears it afterwards`() {
        val contextManager = DefaultExecutionContextManager()
        val codecRegistry = ExecutionContextCodecRegistry(listOf(TransportContextCodec))
        val subscriberManager = mockk<EventHandlerDispatcher>()
        val subscriberRegister = mockk<HttpIntegrationEventSubscriberRegister>()
        val environment = mockk<Environment>()
        var observedContext: TransportContext? = null
        every { subscriberManager.dispatch(any()) } answers {
            observedContext = contextManager.current()[TransportContextKey]
        }
        every { subscriberRegister.subscribe(any(), any(), any()) } returns true
        every { environment.resolvePlaceholders(any()) } answers { firstArg() }
        val adapter = HttpIntegrationEventSubscriberAdapter(
            eventHandlerDispatcher = subscriberManager,
            eventMessageInterceptors = emptyList(),
            httpIntegrationEventSubscriberRegister = subscriberRegister,
            environment = environment,
            eventTypeCatalog = SingleEventCatalog,
            applicationName = "test-app",
            httpBaseUrl = "http://localhost",
            httpSubscribePath = "/subscribe",
            httpConsumePath = "/consume",
            executionContextCodecRegistry = codecRegistry,
            executionContextScopeManager = contextManager,
        ).apply { init() }
        val envelope = IntegrationEventExecutionContextEnvelope.encode(
            codecRegistry.encode(originSnapshot(), ExecutionContextBoundary.INTEGRATION_EVENT),
        )

        val consumed = adapter.consume(
            event = "context.transport.event",
            payloadJsonStr = JSON.toJSONString(ContextTransportEvent("payload")),
            headers = mapOf(HEADER_KEY_CAP4K_EXECUTION_CONTEXT.uppercase() to envelope),
        )

        assertTrue(consumed)
        assertEquals(TransportContext("origin"), observedContext)
        assertTrue(contextManager.current().isEmpty)
    }

    private object SingleEventCatalog : EventTypeCatalog {
        override fun integrationEventTypes(): Set<Class<*>> = setOf(ContextTransportEvent::class.java)
    }

    private fun originSnapshot(): ExecutionContextSnapshot = ExecutionContextSnapshot.builder()
        .put(TransportContextKey, TransportContext("origin"))
        .build()
}

@IntegrationEvent("context.transport.event", subscriber = "test-subscriber")
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
