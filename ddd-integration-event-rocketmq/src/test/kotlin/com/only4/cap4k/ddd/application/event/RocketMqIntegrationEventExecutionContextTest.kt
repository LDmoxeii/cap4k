package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElementCodec
import com.only4.cap4k.ddd.core.application.context.ExecutionContextKey
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultReliableEventDeliveryContextManager
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4K_EVENT_ID
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4K_EXECUTION_CONTEXT
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4K_TIMESTAMP
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
        val envelope = IntegrationEventExecutionContextEnvelope.encode(
            codecRegistry.encode(originSnapshot(), ExecutionContextBoundary.INTEGRATION_EVENT),
        )
        val message = mockk<MessageExt> {
            every { msgId } returns "message-23"
            every { body } returns RuntimeJson.write(ContextTransportEvent("payload")).toByteArray()
            every { properties } returns mapOf(
                HEADER_KEY_CAP4K_EXECUTION_CONTEXT to envelope,
                HEADER_KEY_CAP4K_EVENT_ID to "message-23",
                HEADER_KEY_CAP4K_TIMESTAMP to "1767225600123",
            )
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
                eventName = "ContextTransportEvent",
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
            every { body } returns RuntimeJson.write(ContextTransportEvent(id)).toByteArray()
            every { properties } returns mapOf(
                HEADER_KEY_CAP4K_EVENT_ID to id,
                HEADER_KEY_CAP4K_TIMESTAMP to timestamp,
            )
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
                    "first", "ContextTransportEvent", java.time.Instant.ofEpochMilli(1000), 1,
                    ReliableEventRedeliveryHint.FIRST,
                ),
                ReliableEventDeliveryContext(
                    "second", "ContextTransportEvent", java.time.Instant.ofEpochMilli(2000), 2,
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
            every { body } returns RuntimeJson.write(ContextTransportEvent("failed")).toByteArray()
            every { properties } returns mapOf(
                HEADER_KEY_CAP4K_EVENT_ID to "failed",
                HEADER_KEY_CAP4K_TIMESTAMP to "1000",
            )
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
            every { body } returns RuntimeJson.write(ContextTransportEvent("payload")).toByteArray()
            every { properties } returns mapOf(
                HEADER_KEY_CAP4K_EVENT_ID to "noncanonical",
                HEADER_KEY_CAP4K_TIMESTAMP to "01000",
            )
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
