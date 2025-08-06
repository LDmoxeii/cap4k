package com.only4.cap4k.ddd.application.event

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.parser.Feature
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventSubscriberManager
import com.only4.cap4k.ddd.core.share.misc.findIntegrationEventClasses
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

/**
 * 自动监听集成事件对应的RocketMQ
 *
 * @author binking338
 * @date 2023-02-28
 */
class RocketMqIntegrationEventSubscriberAdapter(
    private val eventSubscriberManager: EventSubscriberManager,
    private val eventMessageInterceptors: List<EventMessageInterceptor>,
    private val rocketMqIntegrationEventConfigure: RocketMqIntegrationEventConfigure?,
    private val environment: Environment,
    private val scanPath: String,
    private val applicationName: String,
    private val defaultNameSrv: String,
    private val msgCharset: String
) {

    companion object {
        private val log = LoggerFactory.getLogger(RocketMqIntegrationEventSubscriberAdapter::class.java)
    }

    private val mqPushConsumers by lazy {
        findIntegrationEventClasses(scanPath)
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

            if (orderedEventMessageInterceptors.isEmpty()) {
                eventSubscriberManager.dispatch(eventPayload)
            } else {
                processWithInterceptors(msg, eventPayload)
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

    private fun processWithInterceptors(msg: MessageExt, eventPayload: Any) {
        val message = GenericMessage(
            eventPayload,
            EventMessageInterceptor.ModifiableMessageHeaders(msg.properties.toMutableMap())
        )

        orderedEventMessageInterceptors.forEach { it.preSubscribe(message) }
        eventSubscriberManager.dispatch(message.payload)
        orderedEventMessageInterceptors.forEach { it.postSubscribe(message) }
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
