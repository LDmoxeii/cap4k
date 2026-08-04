package com.only4.cap4k.ddd.application.event

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.parser.Feature
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint
import com.only4.cap4k.ddd.core.share.misc.resolvePlaceholderWithCache
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4K_EVENT_ID
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4K_EXECUTION_CONTEXT
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4K_TIMESTAMP
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
import java.time.Instant

/**
 * 自动监听集成事件对应的RocketMQ
 *
 * @author binking338
 * @date 2023-02-28
 */
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

    /**
     * 获取排序后的事件消息拦截器
     * 基于 [org.springframework.core.annotation.Order]
     */
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
        if (mqPushConsumers.isEmpty()) {
            return
        }

        mqPushConsumers.forEach { mqPushConsumer ->
            try {
                mqPushConsumer.shutdown()
            } catch (ex: Exception) {
                log.error("集成事件消息监听退出异常", ex)
            }
        }
    }

    fun createDefaultConsumer(integrationEventClass: Class<*>): DefaultMQPushConsumer {
        val integrationEvent = integrationEventClass.getAnnotation(IntegrationEvent::class.java)

        val target = resolvePlaceholderWithCache(integrationEvent.value, environment)
        val (topic, tag) = parseTarget(target)

        val subscriber = resolvePlaceholderWithCache(integrationEvent.subscriber, environment)

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
        if (parts.size == 2) {
            parts[0] to parts[1]
        } else {
            target to ""
        }
    }

    private fun onMessage(
        integrationEventClass: Class<*>,
        msgs: List<MessageExt>,
        context: ConsumeConcurrentlyContext
    ): ConsumeConcurrentlyStatus = runCatching {
        msgs.forEach { msg ->
            log.info("集成事件消费，msgId=${msg.msgId}")
            val eventPayload = msg.parseEventPayload(integrationEventClass)
            val deliveryContext = msg.reliableDeliveryContext(eventPayload)
            val executionContext = executionContextCodecRegistry.decodeExternal(
                IntegrationEventExecutionContextEnvelope.decode(
                    msg.properties[HEADER_KEY_CAP4K_EXECUTION_CONTEXT],
                ),
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

    private fun MessageExt.parseEventPayload(integrationEventClass: Class<*>): Any {
        val strMsg = String(this.body, charset(msgCharset))
        return JSON.parseObject(strMsg, integrationEventClass, Feature.SupportNonPublicField)
    }

    private fun processWithInterceptors(
        msg: MessageExt,
        eventPayload: Any,
        deliveryContext: ReliableEventDeliveryContext,
    ) {
        val message = GenericMessage(
            eventPayload,
            EventMessageInterceptor.ModifiableMessageHeaders(msg.properties.toMutableMap())
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

    private fun MessageExt.reliableDeliveryContext(eventPayload: Any): ReliableEventDeliveryContext {
        val eventId = properties[HEADER_KEY_CAP4K_EVENT_ID]?.takeIf { it.isNotBlank() }
            ?: error("RocketMQ integration event cap4k-event-id must be nonblank")
        val rawTimestamp = properties[HEADER_KEY_CAP4K_TIMESTAMP]
            ?: error("RocketMQ integration event cap4k-timestamp is required")
        val epochMillis = rawTimestamp.toLongOrNull()
            ?: error("RocketMQ integration event cap4k-timestamp must be epoch millis")
        require(rawTimestamp == epochMillis.toString()) {
            "RocketMQ integration event cap4k-timestamp must use canonical epoch millis"
        }
        require(reconsumeTimes >= 0) { "RocketMQ integration event reconsumeTimes must not be negative" }
        val attempt = Math.addExact(reconsumeTimes, 1)
        return ReliableEventDeliveryContext(
            eventId = eventId,
            eventName = eventPayload.javaClass.simpleName,
            publishedAt = Instant.ofEpochMilli(epochMillis),
            attempt = attempt,
            redeliveryHint = if (reconsumeTimes == 0) {
                ReliableEventRedeliveryHint.FIRST
            } else {
                ReliableEventRedeliveryHint.REDELIVERED
            },
        )
    }

    private fun getTopicConsumerGroup(topic: String, defaultVal: String): String =
        resolvePlaceholderWithCache(
            "\${rocketmq.$topic.consumer.group:${defaultVal.takeIf { it.isNotBlank() } ?: "$topic-4-$applicationName"}}",
            environment
        )

    private fun getTopicNamesrvAddr(topic: String, defaultVal: String): String =
        resolvePlaceholderWithCache(
            "\${rocketmq.$topic.name-server:${defaultVal.takeIf { it.isNotBlank() } ?: defaultNameSrv}}",
            environment
        )
}
