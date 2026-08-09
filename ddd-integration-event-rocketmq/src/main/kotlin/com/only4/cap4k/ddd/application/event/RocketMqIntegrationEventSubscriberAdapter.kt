package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventDeliveryMetadata
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.deliveryContext
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint
import com.only4.cap4k.ddd.core.share.misc.resolvePlaceholderWithCache
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus
import org.apache.rocketmq.client.exception.MQClientException
import org.apache.rocketmq.common.consumer.ConsumeFromWhere
import org.apache.rocketmq.common.message.MessageExt
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils
import org.springframework.core.env.Environment
import org.springframework.messaging.support.GenericMessage

/** RocketMQ inbound adapter for the shared Integration Event envelope. */
class RocketMqIntegrationEventSubscriberAdapter(
    private val eventHandlerDispatcher: EventHandlerDispatcher,
    private val eventMessageInterceptors: List<EventMessageInterceptor>,
    private val rocketMqIntegrationEventConfigure: RocketMqIntegrationEventConfigure?,
    private val environment: Environment,
    private val eventTypeCatalog: EventTypeCatalog,
    private val applicationName: String,
    private val defaultNameSrv: String,
    private val msgCharset: String,
    private val executionContextCodecRegistry: ExecutionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
    private val executionContextScopeManager: ExecutionContextScopeManager = ExecutionContextScopeManager {
        AutoCloseable { }
    },
    private val reliableEventDeliveryContextScopeManager: ReliableEventDeliveryContextScopeManager,
    private val envelopeCodec: IntegrationEventEnvelopeCodec = IntegrationEventEnvelopeCodec(),
) {
    companion object {
        private val log = LoggerFactory.getLogger(RocketMqIntegrationEventSubscriberAdapter::class.java)
    }

    private val mqPushConsumers by lazy {
        eventTypeCatalog.integrationEventTypes()
            .filter { cls ->
                val integrationEvent = cls.getAnnotation(IntegrationEvent::class.java)
                integrationEvent.value.isNotBlank() &&
                    !IntegrationEvent.NONE_SUBSCRIBER.equals(integrationEvent.subscriber, ignoreCase = true)
            }
            .mapNotNull { integrationEventClass ->
                val consumer = rocketMqIntegrationEventConfigure?.get(integrationEventClass)
                    ?: createDefaultConsumer(integrationEventClass)
                try {
                    if (consumer is DefaultMQPushConsumer && consumer.messageListener == null) {
                        consumer.registerMessageListener { msgs: List<MessageExt>, context: ConsumeConcurrentlyContext ->
                            onMessage(integrationEventClass, msgs, context)
                        }
                    }
                    consumer.start()
                    consumer
                } catch (e: MQClientException) {
                    log.error("集成事件消息监听启动失败", e)
                    null
                }
            }
    }

    private val orderedEventMessageInterceptors by lazy {
        eventMessageInterceptors.sortedBy { interceptor ->
            OrderUtils.getOrder(interceptor.javaClass, Ordered.LOWEST_PRECEDENCE)
        }
    }

    fun init() {
        mqPushConsumers
    }

    fun shutdown() {
        log.info("集成事件消息监听退出...")
        mqPushConsumers.forEach { consumer ->
            runCatching { consumer.shutdown() }
                .onFailure { log.error("集成事件消息监听退出异常", it) }
        }
    }

    fun createDefaultConsumer(integrationEventClass: Class<*>): DefaultMQPushConsumer {
        val annotation = integrationEventClass.getAnnotation(IntegrationEvent::class.java)
        val target = resolvePlaceholderWithCache(annotation.value, environment)
        val (topic, tag) = parseTarget(target)
        val subscriber = subscriberIdentity(integrationEventClass)
        return DefaultMQPushConsumer().apply {
            consumerGroup = getTopicConsumerGroup(topic, subscriber)
            consumeFromWhere = ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET
            instanceName = applicationName
            namesrvAddr = getTopicNamesrvAddr(topic, defaultNameSrv)
            unitName = integrationEventClass.simpleName
            try {
                subscribe(topic, tag)
            } catch (e: MQClientException) {
                log.error("集成事件消息监听订阅失败", e)
            }
        }
    }

    private fun parseTarget(target: String): Pair<String, String> = target.split(':', limit = 2).let { parts ->
        if (parts.size == 2) parts[0] to parts[1] else target to ""
    }

    private fun onMessage(
        integrationEventClass: Class<*>,
        msgs: List<MessageExt>,
        context: ConsumeConcurrentlyContext,
    ): ConsumeConcurrentlyStatus = runCatching {
        msgs.forEach { msg ->
            log.info("集成事件消费，msgId=${msg.msgId}")
            val envelope = envelopeCodec.decode(String(msg.body, charset(msgCharset)))
            val eventPayload = envelopeCodec.payloadJson(envelope, integrationEventClass)
            val deliveryContext = envelope.deliveryContext(
                IntegrationEventDeliveryMetadata(
                    subscriberIdentity = subscriberIdentity(integrationEventClass),
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
        log.error("集成事件消息消费异常", ex)
        ConsumeConcurrentlyStatus.RECONSUME_LATER
    }

    private fun processWithInterceptors(
        msg: MessageExt,
        eventPayload: Any,
        deliveryContext: ReliableEventDeliveryContext,
    ) {
        val message = GenericMessage(
            eventPayload,
            com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor.ModifiableMessageHeaders(
                msg.properties.toMutableMap()
            ),
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

    private fun subscriberIdentity(eventClass: Class<*>): String {
        val configured = eventClass.getAnnotation(IntegrationEvent::class.java).subscriber
        if (configured.isBlank()) return applicationName
        return resolvePlaceholderWithCache(configured, environment)
            .takeIf(String::isNotBlank)
            ?: applicationName
    }

    private fun getTopicConsumerGroup(topic: String, defaultVal: String): String =
        resolvePlaceholderWithCache(
            "\${rocketmq.$topic.consumer.group:${defaultVal.takeIf { it.isNotBlank() } ?: "$topic-4-$applicationName"}}",
            environment,
        )

    private fun getTopicNamesrvAddr(topic: String, defaultVal: String): String =
        resolvePlaceholderWithCache(
            "\${rocketmq.$topic.name-server:${defaultVal.takeIf { it.isNotBlank() } ?: defaultNameSrv}}",
            environment,
        )
}
