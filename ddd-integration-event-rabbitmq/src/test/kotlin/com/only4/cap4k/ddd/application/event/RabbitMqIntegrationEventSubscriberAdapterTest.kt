package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.contract.IntegrationEvent
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.rabbitmq.client.Channel
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.amqp.AmqpConnectException
import org.springframework.amqp.AmqpException
import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.connection.ConnectionListener
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import java.time.Duration
import java.time.Instant
import java.net.ConnectException

class RabbitMqIntegrationEventSubscriberAdapterTest {
    private val dispatcher = mockk<EventHandlerDispatcher>()
    private val containerFactory = mockk<SimpleRabbitListenerContainerFactory>()
    private val connectionFactory = mockk<ConnectionFactory>(relaxed = true)
    private val amqpAdmin = mockk<AmqpAdmin>(relaxed = true)
    private val topologyManager = mockk<RabbitMqTopologyManager>(relaxed = true)
    private val stateReporter = mockk<RuntimeProviderStateReporter>(relaxed = true)
    private val route = RabbitMqIntegrationEventRoute("content", "published")
    private var installedContext: ReliableEventDeliveryContext? = null
    private val deliveryContexts = object : ReliableEventDeliveryContextScopeManager {
        override fun install(context: ReliableEventDeliveryContext): AutoCloseable {
            installedContext = context
            return AutoCloseable { }
        }

        override fun suppress(): AutoCloseable = AutoCloseable { }
    }

    @BeforeEach
    fun setUp() {
        installedContext = null
    }

    @AfterEach
    fun tearDown() = clearAllMocks()

    @Test
    fun `enrollment resolves explicit route and owns one stable queue`() {
        val container = mockk<SimpleMessageListenerContainer>(relaxed = true)
        every { container.isRunning } returns false
        every { containerFactory.createListenerContainer() } returns container
        val adapter = adapter(SingleEventCatalog)
        val expectedQueue = RabbitMqQueueIdentity.derive("media-worker", "content.published")

        adapter.init()

        verify(exactly = 1) { topologyManager.register(route, expectedQueue) }
        verify(exactly = 1) { container.setQueueNames(expectedQueue) }
        verify(exactly = 1) { container.acknowledgeMode = AcknowledgeMode.MANUAL }
        verify(exactly = 1) { container.start() }
        verifyOrder {
            topologyManager.register(route, expectedQueue)
            connectionFactory.addConnectionListener(any())
            container.start()
        }
    }

    @Test
    fun `temporary listener connection failure degrades without failing enrollment`() {
        val container = mockk<SimpleMessageListenerContainer>(relaxed = true)
        every { container.isRunning } returns false
        every { container.start() } throws AmqpConnectException(ConnectException("offline"))
        every { containerFactory.createListenerContainer() } returns container

        assertDoesNotThrow { adapter(SingleEventCatalog).init() }

        verify { stateReporter.report(RuntimeProviderState.DEGRADED, "listener-start-failed", any()) }
    }

    @Test
    fun `connection recovery redeclares topology and starts a previously unavailable listener`() {
        val connectionListener = slot<ConnectionListener>()
        every { connectionFactory.addConnectionListener(capture(connectionListener)) } just runs
        val container = mockk<SimpleMessageListenerContainer>(relaxed = true)
        var running = false
        var starts = 0
        val recoveryOrder = mutableListOf<String>()
        every { container.isRunning } answers { running }
        every { container.start() } answers {
            starts += 1
            recoveryOrder += "start-$starts"
            if (starts == 1) {
                throw AmqpConnectException(ConnectException("offline"))
            }
            running = true
        }
        every { containerFactory.createListenerContainer() } returns container
        val delegate = RecordingReporter()
        val coordinator = RabbitMqProviderStateCoordinator(delegate)
        coordinator.publisher.report(RuntimeProviderState.HEALTHY, "publisher-ready")
        val expectedQueue = RabbitMqQueueIdentity.derive("media-worker", "content.published")
        every { topologyManager.register(route, expectedQueue) } answers {
            coordinator.topology.report(RuntimeProviderState.HEALTHY, "topology-declared")
        }
        every { topologyManager.declareAll() } answers {
            recoveryOrder += "declare-all"
            coordinator.topology.report(RuntimeProviderState.HEALTHY, "topology-redeclared")
        }
        val adapter = adapter(
            catalog = SingleEventCatalog,
            topologyManager = topologyManager,
            stateReporter = coordinator.subscriber,
        )

        adapter.init()
        assertEquals(RuntimeProviderState.DEGRADED, delegate.lastState)

        connectionListener.captured.onCreate(mockk())

        assertEquals(listOf("start-1", "declare-all", "start-2"), recoveryOrder)
        assertEquals(RuntimeProviderState.HEALTHY, delegate.lastState)
        assertEquals("subscriber:connection-ready", delegate.lastCategory)
        verify(exactly = 1) { topologyManager.declareAll() }
        verify(exactly = 2) { container.start() }
    }

    @Test
    fun `connection recovery leaves an already running listener to Spring AMQP`() {
        val connectionListener = slot<ConnectionListener>()
        every { connectionFactory.addConnectionListener(capture(connectionListener)) } just runs
        val container = mockk<SimpleMessageListenerContainer>(relaxed = true)
        every { container.isRunning } returns true
        every { containerFactory.createListenerContainer() } returns container
        val adapter = adapter(SingleEventCatalog)

        adapter.init()
        connectionListener.captured.onCreate(mockk())

        verify(exactly = 1) { topologyManager.declareAll() }
        verify(exactly = 0) { container.start() }
        verify { stateReporter.report(RuntimeProviderState.HEALTHY, "connection-ready", any()) }
    }

    @Test
    fun `deterministic listener startup failure is not swallowed`() {
        val container = mockk<SimpleMessageListenerContainer>(relaxed = true)
        val failure = AmqpException("mismatched queue")
        every { container.isRunning } returns false
        every { container.start() } throws failure
        every { containerFactory.createListenerContainer() } returns container

        assertEquals(failure, assertThrows<AmqpException> { adapter(SingleEventCatalog).init() })
    }

    @Test
    fun `successful local completion acknowledges after dispatch`() {
        every { dispatcher.dispatch(any()) } just runs
        val channel = mockk<Channel>()
        every { channel.basicAck(17L, false) } just runs

        invokeOnMessage(adapter(), message(deliveryTag = 17L, redelivered = false), channel)

        verifyOrder {
            dispatcher.dispatch(TestEventPayload("payload"))
            channel.basicAck(17L, false)
        }
        assertEquals("event-1", installedContext?.eventId)
        assertEquals("content.published", installedContext?.eventName)
        assertEquals(2, installedContext?.attempt)
        assertEquals(ReliableEventRedeliveryHint.FIRST, installedContext?.redeliveryHint)
    }

    @Test
    fun `handler failure rejects for requeue and never acknowledges`() {
        every { dispatcher.dispatch(any()) } throws IllegalStateException("handler failed")
        val channel = mockk<Channel>()
        every { channel.basicReject(18L, true) } just runs

        invokeOnMessage(adapter(), message(deliveryTag = 18L, redelivered = true), channel)

        verify(exactly = 0) { channel.basicAck(any(), any()) }
        verify(exactly = 1) { channel.basicReject(18L, true) }
        assertEquals(ReliableEventRedeliveryHint.REDELIVERED, installedContext?.redeliveryHint)
    }

    @Test
    fun `malformed envelope rejects without dispatch`() {
        val channel = mockk<Channel>()
        every { channel.basicReject(19L, true) } just runs
        val properties = MessageProperties().apply {
            deliveryTag = 19L
            messageId = "event-1"
        }

        invokeOnMessage(adapter(), Message("secret malformed body".toByteArray(), properties), channel)

        verify(exactly = 0) { dispatcher.dispatch(any()) }
        verify(exactly = 1) { channel.basicReject(19L, true) }
    }

    @Test
    fun `connection lifecycle reports recovering and degraded facts`() {
        val listener = slot<ConnectionListener>()
        every { connectionFactory.addConnectionListener(capture(listener)) } just runs
        adapter().init()

        listener.captured.onFailed(IllegalStateException("offline"))
        listener.captured.onClose(mockk())

        verify { stateReporter.report(RuntimeProviderState.DEGRADED, "connection-failed", any()) }
        verify { stateReporter.report(RuntimeProviderState.RECOVERING, "connection-closed", any()) }
    }

    private fun adapter(
        catalog: InboundIntegrationEventRegistrationView = EmptyEventCatalog,
        topologyManager: RabbitMqTopologyManager = this.topologyManager,
        stateReporter: RuntimeProviderStateReporter = this.stateReporter,
    ): RabbitMqIntegrationEventSubscriberAdapter = RabbitMqIntegrationEventSubscriberAdapter(
        eventHandlerDispatcher = dispatcher,
        eventMessageInterceptors = emptyList(),
        rabbitListenerContainerFactory = containerFactory,
        connectionFactory = connectionFactory,
        amqpAdmin = amqpAdmin,
        routeResolver = IntegrationEventRouteResolver { route },
        topologyManager = topologyManager,
        stateReporter = stateReporter,
        eventTypeCatalog = catalog,
        applicationName = "media-worker",
        recoveryInterval = Duration.ofMillis(10),
        reliableEventDeliveryContextScopeManager = deliveryContexts,
    )

    private class RecordingReporter : RuntimeProviderStateReporter {
        override val providerId: String = "integration-event-transport.rabbitmq"
        var lastState: RuntimeProviderState? = null
        var lastCategory: String? = null

        override fun report(state: RuntimeProviderState, category: String?, observedAt: Instant) {
            lastState = state
            lastCategory = category
        }

        override fun close() = Unit
    }

    private fun invokeOnMessage(adapter: RabbitMqIntegrationEventSubscriberAdapter, message: Message, channel: Channel) {
        adapter.javaClass.getDeclaredMethod(
            "onMessage",
            Class::class.java,
            Message::class.java,
            Channel::class.java,
        ).apply { isAccessible = true }
            .invoke(adapter, TestEventPayload::class.java, message, channel)
    }

    private fun message(deliveryTag: Long, redelivered: Boolean): Message {
        val properties = MessageProperties().apply {
            this.deliveryTag = deliveryTag
            messageId = "event-1"
            this.redelivered = redelivered
        }
        val envelope = IntegrationEventEnvelope(
            eventId = "event-1",
            eventType = "content.published",
            originService = "content-service",
            publishedAt = Instant.parse("2026-08-10T00:00:00Z"),
            deliveryAttempt = 2,
            executionContext = emptyList(),
            payloadJson = RuntimeJson.write(TestEventPayload("payload")),
        )
        return Message(IntegrationEventEnvelopeCodec().encode(envelope).toByteArray(), properties)
    }

    private object EmptyEventCatalog : InboundIntegrationEventRegistrationView {
        override fun integrationEventTypes(): Set<Class<*>> = emptySet()
    }

    private object SingleEventCatalog : InboundIntegrationEventRegistrationView {
        override fun integrationEventTypes(): Set<Class<*>> = setOf(TestEventPayload::class.java)
    }

    @IntegrationEvent("content.published")
    private data class TestEventPayload(val value: String)
}
