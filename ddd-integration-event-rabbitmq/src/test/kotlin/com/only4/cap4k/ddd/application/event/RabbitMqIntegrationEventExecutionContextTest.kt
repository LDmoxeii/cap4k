package com.only4.cap4k.ddd.application.event

import com.alibaba.fastjson.JSON
import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElementCodec
import com.only4.cap4k.ddd.core.application.context.ExecutionContextKey
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.domain.event.EventSubscriberManager
import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4K_EXECUTION_CONTEXT
import com.rabbitmq.client.Channel
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.core.env.Environment

class RabbitMqIntegrationEventExecutionContextTest {
    @Test
    fun `consumer installs external context before dispatch and clears it afterwards`() {
        val contextManager = DefaultExecutionContextManager()
        val codecRegistry = ExecutionContextCodecRegistry(listOf(TransportContextCodec))
        val subscriberManager = mockk<EventSubscriberManager>()
        var observedContext: TransportContext? = null
        every { subscriberManager.dispatch(any()) } answers {
            observedContext = contextManager.current()[TransportContextKey]
        }
        val adapter = RabbitMqIntegrationEventSubscriberAdapter(
            eventSubscriberManager = subscriberManager,
            eventMessageInterceptors = emptyList(),
            rabbitMqIntegrationEventConfigure = null,
            rabbitListenerContainerFactory = mockk<SimpleRabbitListenerContainerFactory>(),
            connectionFactory = mockk<ConnectionFactory>(),
            environment = mockk<Environment>(),
            eventTypeCatalog = EmptyEventCatalog,
            applicationName = "test-app",
            executionContextCodecRegistry = codecRegistry,
            executionContextScopeManager = contextManager,
        )
        val envelope = IntegrationEventExecutionContextEnvelope.encode(
            codecRegistry.encode(originSnapshot(), ExecutionContextBoundary.INTEGRATION_EVENT),
        )
        val properties = MessageProperties().apply {
            deliveryTag = 17L
            messageId = "message-17"
            setHeader(HEADER_KEY_CAP4K_EXECUTION_CONTEXT, envelope)
        }
        val message = Message(JSON.toJSONString(ContextTransportEvent("payload")).toByteArray(), properties)
        val channel = mockk<Channel> {
            every { basicAck(17L, false) } just runs
        }

        val method = adapter.javaClass.getDeclaredMethod(
            "onMessage",
            Class::class.java,
            Message::class.java,
            Channel::class.java,
        ).apply { isAccessible = true }
        method.invoke(adapter, ContextTransportEvent::class.java, message, channel)

        assertEquals(TransportContext("origin"), observedContext)
        assertTrue(contextManager.current().isEmpty)
        verify(exactly = 1) { channel.basicAck(17L, false) }
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
