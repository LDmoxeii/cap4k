package com.only4.cap4k.ddd.application.event

import com.alibaba.fastjson.JSON
import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElementCodec
import com.only4.cap4k.ddd.core.application.context.ExecutionContextKey
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4K_EXECUTION_CONTEXT
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus
import org.apache.rocketmq.common.message.MessageExt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.env.Environment

class RocketMqIntegrationEventExecutionContextTest {
    @Test
    fun `consumer installs external context before dispatch and clears it afterwards`() {
        val contextManager = DefaultExecutionContextManager()
        val codecRegistry = ExecutionContextCodecRegistry(listOf(TransportContextCodec))
        val subscriberManager = mockk<EventHandlerDispatcher>()
        var observedContext: TransportContext? = null
        every { subscriberManager.dispatch(any()) } answers {
            observedContext = contextManager.current()[TransportContextKey]
        }
        val adapter = RocketMqIntegrationEventSubscriberAdapter(
            eventHandlerDispatcher = subscriberManager,
            eventMessageInterceptors = emptyList(),
            rocketMqIntegrationEventConfigure = null,
            environment = mockk<Environment>(),
            eventTypeCatalog = EmptyEventCatalog,
            applicationName = "test-app",
            defaultNameSrv = "localhost:9876",
            msgCharset = "UTF-8",
            executionContextCodecRegistry = codecRegistry,
            executionContextScopeManager = contextManager,
        )
        val envelope = IntegrationEventExecutionContextEnvelope.encode(
            codecRegistry.encode(originSnapshot(), ExecutionContextBoundary.INTEGRATION_EVENT),
        )
        val message = mockk<MessageExt> {
            every { msgId } returns "message-23"
            every { body } returns JSON.toJSONString(ContextTransportEvent("payload")).toByteArray()
            every { properties } returns mapOf(HEADER_KEY_CAP4K_EXECUTION_CONTEXT to envelope)
        }

        val method = adapter.javaClass.getDeclaredMethod(
            "onMessage",
            Class::class.java,
            List::class.java,
            ConsumeConcurrentlyContext::class.java,
        ).apply { isAccessible = true }
        val result = method.invoke(
            adapter,
            ContextTransportEvent::class.java,
            listOf(message),
            mockk<ConsumeConcurrentlyContext>(),
        )

        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS, result)
        assertEquals(TransportContext("origin"), observedContext)
        assertTrue(contextManager.current().isEmpty)
    }

    private object EmptyEventCatalog : EventTypeCatalog {
        override fun integrationEventTypes(): Set<Class<*>> = emptySet()
    }

    private fun originSnapshot(): ExecutionContextSnapshot = ExecutionContextSnapshot.builder()
        .put(TransportContextKey, TransportContext("origin"))
        .build()
}

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
