package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventDeliveryMetadata
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.application.event.deliveryContext
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ShutdownSignalException
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpException
import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.Connection
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.connection.ConnectionListener
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils
import org.springframework.messaging.Message
import org.springframework.messaging.support.GenericMessage
import java.time.Duration

/** RabbitMQ inbound adapter for the shared Integration Event envelope. */
class RabbitMqIntegrationEventSubscriberAdapter(
    private val eventHandlerDispatcher: EventHandlerDispatcher,
    private val eventMessageInterceptors: List<EventMessageInterceptor>,
    private val rabbitListenerContainerFactory: SimpleRabbitListenerContainerFactory,
    private val connectionFactory: ConnectionFactory,
    private val amqpAdmin: AmqpAdmin,
    private val routeResolver: IntegrationEventRouteResolver<RabbitMqIntegrationEventRoute>,
    private val topologyManager: RabbitMqTopologyManager,
    private val stateReporter: RuntimeProviderStateReporter,
    private val eventTypeCatalog: InboundIntegrationEventRegistrationView,
    private val applicationName: String,
    private val msgCharset: String = "UTF-8",
    private val recoveryInterval: Duration = Duration.ofSeconds(5),
    private val executionContextCodecRegistry: ExecutionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
    private val executionContextScopeManager: ExecutionContextScopeManager = ExecutionContextScopeManager {
        AutoCloseable { }
    },
    private val reliableEventDeliveryContextScopeManager: ReliableEventDeliveryContextScopeManager,
    private val envelopeCodec: IntegrationEventEnvelopeCodec = IntegrationEventEnvelopeCodec(),
) {
    private val connectionListener = object : ConnectionListener {
        override fun onCreate(connection: Connection) {
            stateReporter.report(RuntimeProviderState.RECOVERING, "connection-created")
            topologyManager.declareAll()
            val listenersReady = simpleMessageListenerContainers
                .filterNot(SimpleMessageListenerContainer::isRunning)
                .map(::start)
                .all { it }
            if (listenersReady) {
                stateReporter.report(RuntimeProviderState.HEALTHY, "connection-ready")
            }
        }

        override fun onClose(connection: Connection) {
            stateReporter.report(RuntimeProviderState.RECOVERING, "connection-closed")
        }

        override fun onShutDown(signal: ShutdownSignalException) {
            stateReporter.report(RuntimeProviderState.DEGRADED, "connection-shutdown")
        }

        override fun onFailed(exception: Exception) {
            stateReporter.report(RuntimeProviderState.DEGRADED, "connection-failed")
        }
    }

    private val simpleMessageListenerContainers by lazy {
        eventTypeCatalog.integrationEventTypesByName()
            .map { (eventName, integrationEventClass) ->
                val route = routeResolver.resolve(eventName)
                val queue = RabbitMqQueueIdentity.derive(applicationName, eventName)
                topologyManager.register(route, queue)
                createDefaultConsumer(integrationEventClass, queue)
            }
    }

    private val orderedEventMessageInterceptors: List<EventMessageInterceptor> by lazy {
        eventMessageInterceptors.sortedBy { interceptor ->
            OrderUtils.getOrder(interceptor.javaClass, Ordered.LOWEST_PRECEDENCE)
        }
    }

    fun init() {
        require(applicationName.isNotBlank()) { "RabbitMQ application name must not be blank" }
        require(!recoveryInterval.isNegative && !recoveryInterval.isZero) {
            "RabbitMQ listener recovery interval must be positive"
        }
        val containers = simpleMessageListenerContainers
        connectionFactory.addConnectionListener(connectionListener)
        stateReporter.report(RuntimeProviderState.RECOVERING, "subscriber-enrolled")
        if (containers.isEmpty()) {
            stateReporter.report(RuntimeProviderState.HEALTHY, "no-inbound-subscriptions")
        } else {
            containers.forEach(::start)
        }
    }

    fun shutdown() {
        connectionFactory.removeConnectionListener(connectionListener)
        simpleMessageListenerContainers.forEach { container ->
            runCatching { container.shutdown() }
                .onFailure {
                    log.warn(
                        "RabbitMQ Integration Event listener shutdown failed: exceptionType={}",
                        it::class.java.name,
                    )
                }
        }
    }

    fun createDefaultConsumer(
        integrationEventClass: Class<*>,
        queue: String,
    ): SimpleMessageListenerContainer = rabbitListenerContainerFactory.createListenerContainer().apply {
        setQueueNames(queue)
        acknowledgeMode = AcknowledgeMode.MANUAL
        setAmqpAdmin(amqpAdmin)
        setAutoDeclare(true)
        setMissingQueuesFatal(false)
        setMismatchedQueuesFatal(true)
        setRecoveryInterval(recoveryInterval.toMillis())
        setFailedDeclarationRetryInterval(recoveryInterval.toMillis())
        messageListener = ChannelAwareMessageListener { message, channel ->
            onMessage(integrationEventClass, message, requireNotNull(channel))
        }
    }

    private fun start(container: SimpleMessageListenerContainer): Boolean {
        if (container.isRunning) return true
        try {
            container.start()
            return true
        } catch (failure: AmqpException) {
            if (!RabbitMqFailureClassifier.isTemporaryUnavailability(failure)) throw failure
            stateReporter.report(RuntimeProviderState.DEGRADED, "listener-start-failed")
            log.debug(
                "RabbitMQ Integration Event listener start deferred: queues={}, exceptionType={}",
                container.queueNames.toList(),
                failure::class.java.name,
            )
            return false
        }
    }

    private fun processWithInterceptors(
        msg: org.springframework.amqp.core.Message,
        eventPayload: Any,
        deliveryContext: ReliableEventDeliveryContext,
    ) {
        val message: Message<Any> = GenericMessage(
            eventPayload,
            EventMessageInterceptor.ModifiableMessageHeaders(msg.messageProperties.headers),
        )
        reliableEventDeliveryContextScopeManager.suppress().use {
            orderedEventMessageInterceptors.forEach { it.preSubscribe(message) }
        }
        dispatch(message.payload, deliveryContext)
        reliableEventDeliveryContextScopeManager.suppress().use {
            orderedEventMessageInterceptors.forEach { it.postSubscribe(message) }
        }
    }

    private fun dispatch(eventPayload: Any, deliveryContext: ReliableEventDeliveryContext) {
        reliableEventDeliveryContextScopeManager.install(deliveryContext).use {
            eventHandlerDispatcher.dispatch(eventPayload)
        }
    }

    private fun onMessage(
        integrationEventClass: Class<*>,
        msg: org.springframework.amqp.core.Message,
        channel: Channel,
    ) = runCatching {
        val envelope = envelopeCodec.decode(String(msg.body, charset(msgCharset)))
        val eventPayload = envelopeCodec.payloadJson(envelope, integrationEventClass)
        val deliveryContext = envelope.deliveryContext(
            IntegrationEventDeliveryMetadata(
                redeliveryHint = when (msg.messageProperties.redelivered) {
                    true -> ReliableEventRedeliveryHint.REDELIVERED
                    false -> ReliableEventRedeliveryHint.FIRST
                    null -> ReliableEventRedeliveryHint.UNKNOWN
                },
            )
        )
        val executionContext = executionContextCodecRegistry.decodeExternal(
            envelope.executionContext,
            ExecutionContextBoundary.INTEGRATION_EVENT,
        )
        executionContextScopeManager.install(executionContext).use {
            if (orderedEventMessageInterceptors.isEmpty()) {
                dispatch(eventPayload, deliveryContext)
            } else {
                processWithInterceptors(msg, eventPayload, deliveryContext)
            }
        }
        channel.basicAck(msg.messageProperties.deliveryTag, false)
        stateReporter.report(RuntimeProviderState.HEALTHY, "consumer-ack")
    }.getOrElse { failure ->
        stateReporter.report(RuntimeProviderState.DEGRADED, "consumer-delivery-failed")
        log.warn(
            "RabbitMQ Integration Event consume failed: messageId={}, exceptionType={}",
            msg.messageProperties.messageId,
            failure::class.java.name,
        )
        channel.basicReject(msg.messageProperties.deliveryTag, true)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(RabbitMqIntegrationEventSubscriberAdapter::class.java)
    }
}
