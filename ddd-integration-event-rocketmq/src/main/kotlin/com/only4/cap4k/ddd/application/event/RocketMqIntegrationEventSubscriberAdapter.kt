package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventDeliveryMetadata
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.application.event.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.event.RuntimeProviderStateRegistry
import com.only4.cap4k.ddd.core.application.event.deliveryContext
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus
import org.apache.rocketmq.client.exception.MQClientException
import org.apache.rocketmq.common.consumer.ConsumeFromWhere
import org.apache.rocketmq.common.message.MessageExt
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils
import org.springframework.messaging.support.GenericMessage

/** RocketMQ inbound adapter for the shared Integration Event envelope. */
class RocketMqIntegrationEventSubscriberAdapter(
    private val eventHandlerDispatcher: EventHandlerDispatcher,
    private val eventMessageInterceptors: List<EventMessageInterceptor>,
    private val routeResolver: IntegrationEventRouteResolver<RocketMqIntegrationEventRoute>,
    private val consumerGroupResolver: RocketMqConsumerGroupResolver,
    private val eventTypeCatalog: InboundIntegrationEventRegistrationView,
    private val applicationName: String,
    private val defaultNameSrv: String,
    private val msgCharset: String,
    private val providerStateRegistry: RuntimeProviderStateRegistry,
    private val executionContextCodecRegistry: ExecutionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
    private val executionContextScopeManager: ExecutionContextScopeManager = ExecutionContextScopeManager {
        AutoCloseable { }
    },
    private val reliableEventDeliveryContextScopeManager: ReliableEventDeliveryContextScopeManager,
    private val envelopeCodec: IntegrationEventEnvelopeCodec = IntegrationEventEnvelopeCodec(),
) {
    private data class Subscription(
        val eventName: String,
        val payloadType: Class<*>,
        val route: RocketMqIntegrationEventRoute,
        val consumerGroup: String,
    )

    private val mqPushConsumers by lazy { enrollConsumers() }

    private val orderedEventMessageInterceptors by lazy {
        eventMessageInterceptors.sortedBy { interceptor ->
            OrderUtils.getOrder(interceptor.javaClass, Ordered.LOWEST_PRECEDENCE)
        }
    }

    fun init() {
        mqPushConsumers
    }

    fun shutdown() {
        log.info("RocketMQ Integration Event consumers stopping")
        mqPushConsumers.forEach { consumer ->
            runCatching { consumer.shutdown() }
                .onFailure { log.error("RocketMQ Integration Event consumer shutdown failed", it) }
        }
    }

    private fun enrollConsumers(): List<DefaultMQPushConsumer> {
        val subscriptions = eventTypeCatalog.integrationEventTypesByName()
            .map { (eventName, payloadType) ->
                Subscription(
                    eventName = eventName,
                    payloadType = payloadType,
                    route = routeResolver.resolve(eventName),
                    consumerGroup = consumerGroupResolver.resolve(applicationName, eventName),
                )
            }

        val consumers = subscriptions.map(::createConsumer)
        return consumers.mapNotNull { (subscription, consumer) ->
            try {
                consumer.registerMessageListener { msgs: List<MessageExt>, context: ConsumeConcurrentlyContext ->
                    onMessage(subscription.payloadType, msgs, context)
                }
                consumer.start()
                consumer
            } catch (ex: MQClientException) {
                providerStateRegistry.report(
                    RocketMqIntegrationEventPublisher.PROVIDER_IDENTITY,
                    RuntimeProviderState.DEGRADED,
                    "consumer-start-failed",
                )
                log.error(
                    "RocketMQ Integration Event consumer start failed: eventName={}, topic={}, group={}",
                    subscription.eventName,
                    subscription.route.topic,
                    subscription.consumerGroup,
                    ex,
                )
                runCatching { consumer.shutdown() }
                null
            }
        }
    }

    private fun createConsumer(subscription: Subscription): Pair<Subscription, DefaultMQPushConsumer> {
        val consumer = DefaultMQPushConsumer(subscription.consumerGroup).apply {
            consumeFromWhere = ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET
            instanceName = applicationName
            if (defaultNameSrv.isNotBlank()) namesrvAddr = defaultNameSrv
            unitName = subscription.payloadType.simpleName
            subscribe(subscription.route.topic, subscription.route.tag)
        }
        return subscription to consumer
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onMessage(
        integrationEventClass: Class<*>,
        msgs: List<MessageExt>,
        context: ConsumeConcurrentlyContext,
    ): ConsumeConcurrentlyStatus = runCatching {
        providerStateRegistry.report(
            RocketMqIntegrationEventPublisher.PROVIDER_IDENTITY,
            RuntimeProviderState.HEALTHY,
            "consumer-delivery",
        )
        msgs.forEach { msg ->
            log.info("RocketMQ Integration Event received: msgId={}", msg.msgId)
            val envelope = envelopeCodec.decode(String(msg.body, charset(msgCharset)))
            val eventPayload = envelopeCodec.payloadJson(envelope, integrationEventClass)
            val deliveryContext = envelope.deliveryContext(
                IntegrationEventDeliveryMetadata(
                    providerDeliveryAttempt = msg.reconsumeTimes + 1,
                    redeliveryHint = if (msg.reconsumeTimes == 0) {
                        ReliableEventRedeliveryHint.FIRST
                    } else {
                        ReliableEventRedeliveryHint.REDELIVERED
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
        }
        ConsumeConcurrentlyStatus.CONSUME_SUCCESS
    }.getOrElse { ex ->
        log.error("RocketMQ Integration Event consumption failed", ex)
        ConsumeConcurrentlyStatus.RECONSUME_LATER
    }

    private fun processWithInterceptors(
        msg: MessageExt,
        eventPayload: Any,
        deliveryContext: ReliableEventDeliveryContext,
    ) {
        val message = GenericMessage(
            eventPayload,
            EventMessageInterceptor.ModifiableMessageHeaders(msg.properties.toMutableMap()),
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

    companion object {
        private val log = LoggerFactory.getLogger(RocketMqIntegrationEventSubscriberAdapter::class.java)
    }
}
