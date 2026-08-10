package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElementCodec
import com.only4.cap4k.ddd.core.application.context.ExecutionContextKey
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultReliableEventDeliveryContextManager
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
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
        val reliableContextManager = DefaultReliableEventDeliveryContextManager(contextManager, contextManager)
        val subscriberManager = mockk<EventHandlerDispatcher>()
        var observedContext: TransportContext? = null
        var observedDeliveryContext: ReliableEventDeliveryContext? = null
        every { subscriberManager.dispatch(any()) } answers {
            observedContext = contextManager.current()[TransportContextKey]
            observedDeliveryContext = reliableContextManager.currentOrNull()
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
            reliableEventDeliveryContextScopeManager = reliableContextManager,
        )
        val message = mockk<MessageExt> {
            every { msgId } returns "message-23"
            every { body } returns canonicalBody(
                id = "message-23",
                payload = ContextTransportEvent("payload"),
                publishedAt = java.time.Instant.ofEpochMilli(1767225600123L),
                executionContext = codecRegistry.encode(originSnapshot(), ExecutionContextBoundary.INTEGRATION_EVENT),
            )
            every { properties } returns emptyMap()
            every { reconsumeTimes } returns 2
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
        assertEquals(
            ReliableEventDeliveryContext(
                eventId = "message-23",
                eventName = "context.transport.event",
                publishedAt = java.time.Instant.ofEpochMilli(1767225600123L),
                attempt = 3,
                redeliveryHint = ReliableEventRedeliveryHint.REDELIVERED,
            ),
            observedDeliveryContext,
        )
        assertTrue(contextManager.current().isEmpty)
        assertEquals(null, reliableContextManager.currentOrNull())
    }

    @Test
    fun `batch deliveries use independent contexts and clear after each dispatch`() {
        val contextManager = DefaultExecutionContextManager()
        val reliableContextManager = DefaultReliableEventDeliveryContextManager(contextManager, contextManager)
        val observed = mutableListOf<ReliableEventDeliveryContext>()
        val subscriberManager = mockk<EventHandlerDispatcher>()
        every { subscriberManager.dispatch(any()) } answers {
            observed += requireNotNull(reliableContextManager.currentOrNull())
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
            executionContextScopeManager = contextManager,
            reliableEventDeliveryContextScopeManager = reliableContextManager,
        )
        fun message(id: String, timestamp: String, reconsumeTimes: Int) = mockk<MessageExt> {
            every { msgId } returns id
            every { body } returns canonicalBody(
                id = id,
                payload = ContextTransportEvent(id),
                publishedAt = java.time.Instant.ofEpochMilli(timestamp.toLong()),
            )
            every { properties } returns emptyMap()
            every { this@mockk.reconsumeTimes } returns reconsumeTimes
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
            listOf(message("first", "1000", 0), message("second", "2000", 1)),
            mockk<ConsumeConcurrentlyContext>(),
        )

        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS, result)
        assertEquals(
            listOf(
                ReliableEventDeliveryContext(
                    "first", "context.transport.event", java.time.Instant.ofEpochMilli(1000), 1,
                    ReliableEventRedeliveryHint.FIRST,
                ),
                ReliableEventDeliveryContext(
                    "second", "context.transport.event", java.time.Instant.ofEpochMilli(2000), 2,
                    ReliableEventRedeliveryHint.REDELIVERED,
                ),
            ),
            observed,
        )
        assertEquals(null, reliableContextManager.currentOrNull())
        assertTrue(contextManager.current().isEmpty)
    }

    @Test
    fun `failed delivery clears context before reconsuming`() {
        val contextManager = DefaultExecutionContextManager()
        val reliableContextManager = DefaultReliableEventDeliveryContextManager(contextManager, contextManager)
        val subscriberManager = mockk<EventHandlerDispatcher>()
        every { subscriberManager.dispatch(any()) } throws IllegalStateException("handler failure")
        val adapter = RocketMqIntegrationEventSubscriberAdapter(
            eventHandlerDispatcher = subscriberManager,
            eventMessageInterceptors = emptyList(),
            rocketMqIntegrationEventConfigure = null,
            environment = mockk<Environment>(),
            eventTypeCatalog = EmptyEventCatalog,
            applicationName = "test-app",
            defaultNameSrv = "localhost:9876",
            msgCharset = "UTF-8",
            executionContextScopeManager = contextManager,
            reliableEventDeliveryContextScopeManager = reliableContextManager,
        )
        val message = mockk<MessageExt> {
            every { msgId } returns "failed"
            every { body } returns canonicalBody(
                id = "failed",
                payload = ContextTransportEvent("failed"),
                publishedAt = java.time.Instant.ofEpochMilli(1000),
            )
            every { properties } returns emptyMap()
            every { reconsumeTimes } returns 4
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

        assertEquals(ConsumeConcurrentlyStatus.RECONSUME_LATER, result)
        assertEquals(null, reliableContextManager.currentOrNull())
        assertTrue(contextManager.current().isEmpty)
    }

    @Test
    fun `noncanonical timestamp is rejected before handler dispatch`() {
        val contextManager = DefaultExecutionContextManager()
        val reliableContextManager = DefaultReliableEventDeliveryContextManager(contextManager, contextManager)
        val subscriberManager = mockk<EventHandlerDispatcher>(relaxed = true)
        val adapter = RocketMqIntegrationEventSubscriberAdapter(
            eventHandlerDispatcher = subscriberManager,
            eventMessageInterceptors = emptyList(),
            rocketMqIntegrationEventConfigure = null,
            environment = mockk<Environment>(),
            eventTypeCatalog = EmptyEventCatalog,
            applicationName = "test-app",
            defaultNameSrv = "localhost:9876",
            msgCharset = "UTF-8",
            executionContextScopeManager = contextManager,
            reliableEventDeliveryContextScopeManager = reliableContextManager,
        )
        val message = mockk<MessageExt> {
            every { msgId } returns "noncanonical"
            every { body } returns """
                {"eventId":"noncanonical","eventType":"context.transport.event","originService":"test-source",
                 "publishedAt":"01000","deliveryAttempt":null,"executionContext":[],
                 "payloadJson":"{\"value\":\"payload\"}"}
            """.trimIndent().toByteArray()
            every { properties } returns emptyMap()
            every { reconsumeTimes } returns 0
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

        assertEquals(ConsumeConcurrentlyStatus.RECONSUME_LATER, result)
        verify(exactly = 0) { subscriberManager.dispatch(any()) }
        assertEquals(null, reliableContextManager.currentOrNull())
        assertTrue(contextManager.current().isEmpty)
    }

    private object EmptyEventCatalog : InboundIntegrationEventRegistrationView {
        override fun integrationEventTypes(): Set<Class<*>> = emptySet()
    }

    private fun originSnapshot(): ExecutionContextSnapshot = ExecutionContextSnapshot.builder()
        .put(TransportContextKey, TransportContext("origin"))
        .build()

    private fun canonicalBody(
        id: String,
        payload: ContextTransportEvent,
        publishedAt: java.time.Instant,
        executionContext: List<com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement> = emptyList(),
    ): ByteArray = IntegrationEventEnvelopeCodec().encode(
        IntegrationEventEnvelope(
            eventId = id,
            eventType = "context.transport.event",
            originService = "test-source",
            publishedAt = publishedAt,
            deliveryAttempt = null,
            executionContext = executionContext,
            payloadJson = RuntimeJson.write(payload),
        )
    ).toByteArray()
}

@com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent("context.transport.event")
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
