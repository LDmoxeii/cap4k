package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElementCodec
import com.only4.cap4k.ddd.core.application.context.ExecutionContextKey
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultReliableEventDeliveryContextManager
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
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import java.time.Instant
import java.util.Date

class RabbitMqIntegrationEventExecutionContextTest {
    @Test
    fun `consumer installs external context before dispatch and clears it afterwards`() {
        val contextManager = DefaultExecutionContextManager()
        val codecRegistry = ExecutionContextCodecRegistry(listOf(TransportContextCodec))
        val reliableContextManager = DefaultReliableEventDeliveryContextManager(contextManager, contextManager)
        val subscriberManager = mockk<EventHandlerDispatcher>()
        var observedContext: TransportContext? = null
        var observedDeliveryContext: ReliableEventDeliveryContext? = null
        every { subscriberManager.dispatch(any()) } answers {
            observedContext = contextManager.current()[TransportContextKey]
            observedDeliveryContext = reliableContextManager.currentOrNull()
        }
        val adapter = RabbitMqIntegrationEventSubscriberAdapter(
            eventHandlerDispatcher = subscriberManager,
            eventMessageInterceptors = emptyList(),
            rabbitListenerContainerFactory = mockk<SimpleRabbitListenerContainerFactory>(),
            connectionFactory = mockk<ConnectionFactory>(),
            amqpAdmin = mockk<AmqpAdmin>(),
            routeResolver = IntegrationEventRouteResolver { RabbitMqIntegrationEventRoute("context", "event") },
            topologyManager = mockk<RabbitMqTopologyManager>(relaxed = true),
            stateReporter = mockk<RuntimeProviderStateReporter>(relaxed = true),
            eventTypeCatalog = EmptyEventCatalog,
            applicationName = "test-app",
            executionContextCodecRegistry = codecRegistry,
            executionContextScopeManager = contextManager,
            reliableEventDeliveryContextScopeManager = reliableContextManager,
        )
        val publishedAt = Instant.parse("2026-01-01T00:00:00.123Z")
        val properties = MessageProperties().apply {
            deliveryTag = 17L
            messageId = "message-17"
            timestamp = Date.from(publishedAt)
            redelivered = false
        }
        val message = Message(
            IntegrationEventEnvelopeCodec().encode(
                IntegrationEventEnvelope(
                    eventId = "message-17",
                    eventType = "context.transport.event",
                    originService = "test-source",
                    publishedAt = publishedAt,
                    deliveryAttempt = null,
                    executionContext = codecRegistry.encode(
                        originSnapshot(),
                        ExecutionContextBoundary.INTEGRATION_EVENT,
                    ),
                    payloadJson = RuntimeJson.write(ContextTransportEvent("payload")),
                )
            ).toByteArray(),
            properties,
        )
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
        assertEquals(
            ReliableEventDeliveryContext(
                eventId = "message-17",
                eventName = "context.transport.event",
                publishedAt = publishedAt,
                attempt = null,
                redeliveryHint = ReliableEventRedeliveryHint.FIRST,
            ),
            observedDeliveryContext,
        )
        assertTrue(contextManager.current().isEmpty)
        assertEquals(null, reliableContextManager.currentOrNull())
        verify(exactly = 1) { channel.basicAck(17L, false) }
    }

    @Test
    fun `reliable context is suppressed for interceptors and cleared after failure`() {
        val contextManager = DefaultExecutionContextManager()
        val reliableContextManager = DefaultReliableEventDeliveryContextManager(contextManager, contextManager)
        val interceptorObservations = mutableListOf<ReliableEventDeliveryContext?>()
        val interceptor = object : EventMessageInterceptor {
            override fun initPublish(message: org.springframework.messaging.Message<*>) = Unit
            override fun prePublish(message: org.springframework.messaging.Message<*>) = Unit
            override fun postPublish(message: org.springframework.messaging.Message<*>) = Unit
            override fun preSubscribe(message: org.springframework.messaging.Message<*>) {
                interceptorObservations += reliableContextManager.currentOrNull()
            }
            override fun postSubscribe(message: org.springframework.messaging.Message<*>) {
                interceptorObservations += reliableContextManager.currentOrNull()
            }
        }
        val subscriberManager = mockk<EventHandlerDispatcher>()
        every { subscriberManager.dispatch(any()) } throws IllegalStateException("handler failure")
        val adapter = RabbitMqIntegrationEventSubscriberAdapter(
            eventHandlerDispatcher = subscriberManager,
            eventMessageInterceptors = listOf(interceptor),
            rabbitListenerContainerFactory = mockk<SimpleRabbitListenerContainerFactory>(),
            connectionFactory = mockk<ConnectionFactory>(),
            amqpAdmin = mockk<AmqpAdmin>(),
            routeResolver = IntegrationEventRouteResolver { RabbitMqIntegrationEventRoute("context", "event") },
            topologyManager = mockk<RabbitMqTopologyManager>(relaxed = true),
            stateReporter = mockk<RuntimeProviderStateReporter>(relaxed = true),
            eventTypeCatalog = EmptyEventCatalog,
            applicationName = "test-app",
            executionContextScopeManager = contextManager,
            reliableEventDeliveryContextScopeManager = reliableContextManager,
        )
        val properties = MessageProperties().apply {
            deliveryTag = 18L
            messageId = "message-18"
            timestamp = Date.from(Instant.EPOCH)
            redelivered = true
        }
        val message = Message(
            IntegrationEventEnvelopeCodec().encode(
                IntegrationEventEnvelope(
                    eventId = "message-18",
                    eventType = "context.transport.event",
                    originService = "test-source",
                    publishedAt = Instant.EPOCH,
                    deliveryAttempt = null,
                    executionContext = emptyList(),
                    payloadJson = RuntimeJson.write(ContextTransportEvent("payload")),
                )
            ).toByteArray(),
            properties,
        )
        val channel = mockk<Channel> {
            every { basicReject(18L, true) } just runs
        }
        val method = adapter.javaClass.getDeclaredMethod(
            "onMessage",
            Class::class.java,
            Message::class.java,
            Channel::class.java,
        ).apply { isAccessible = true }

        method.invoke(adapter, ContextTransportEvent::class.java, message, channel)

        assertEquals(listOf(null), interceptorObservations)
        assertEquals(null, reliableContextManager.currentOrNull())
        assertTrue(contextManager.current().isEmpty)
        verify(exactly = 1) { channel.basicReject(18L, true) }
    }

    private object EmptyEventCatalog : InboundIntegrationEventRegistrationView {
        override fun integrationEventTypes(): Set<Class<*>> = emptySet()
    }

    private fun originSnapshot(): ExecutionContextSnapshot = ExecutionContextSnapshot.builder()
        .put(TransportContextKey, TransportContext("origin"))
        .build()
}

@com.only4.cap4k.contract.IntegrationEvent("context.transport.event")
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
