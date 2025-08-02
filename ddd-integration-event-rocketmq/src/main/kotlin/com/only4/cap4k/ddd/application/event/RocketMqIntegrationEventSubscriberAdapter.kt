package com.only4.cap4k.ddd.application.event

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.parser.Feature
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventSubscriberManager
import com.only4.cap4k.ddd.core.share.misc.findIntegrationEventClasses
import com.only4.cap4k.ddd.core.share.misc.resolvePlaceholderWithCache
import org.apache.commons.lang3.StringUtils
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
import org.apache.rocketmq.client.consumer.MQPushConsumer
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
        val consumers = mutableListOf<MQPushConsumer>()
        val classes = findIntegrationEventClasses(scanPath)

        classes.filter { cls ->
            val integrationEvent = cls.getAnnotation(IntegrationEvent::class.java)
            integrationEvent != null &&
                    StringUtils.isNotEmpty(integrationEvent.value) &&
                    !IntegrationEvent.NONE_SUBSCRIBER.equals(integrationEvent.subscriber, ignoreCase = true)
        }.forEach { integrationEventClass ->
            val mqPushConsumer = rocketMqIntegrationEventConfigure?.get(integrationEventClass)
                ?: createDefaultConsumer(integrationEventClass)

            mqPushConsumer?.let { consumer ->
                try {
                    if (consumer is DefaultMQPushConsumer && consumer.messageListener == null) {
                        consumer.registerMessageListener { msgs: List<MessageExt>, context: ConsumeConcurrentlyContext ->
                            onMessage(integrationEventClass, msgs, context)
                        }
                    }
                    consumer.start()
                    consumers.add(consumer)
                } catch (e: MQClientException) {
                    log.error("集成事件消息监听启动失败", e)
                }
            }
        }
        consumers
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
        // 触发lazy初始化
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

    fun createDefaultConsumer(integrationEventClass: Class<*>): DefaultMQPushConsumer? {
        val integrationEvent = integrationEventClass.getAnnotation(IntegrationEvent::class.java)
            ?: return null

        if (StringUtils.isBlank(integrationEvent.value) ||
            IntegrationEvent.NONE_SUBSCRIBER.equals(integrationEvent.subscriber, ignoreCase = true)
        ) {
            // 不是集成事件, 或显式标明无订阅
            return null
        }

        val target = resolvePlaceholderWithCache(integrationEvent.value, environment)
        val subscriber = resolvePlaceholderWithCache(integrationEvent.subscriber, environment)
        val topic = if (target.lastIndexOf(':') > 0) {
            target.substring(0, target.lastIndexOf(':'))
        } else {
            target
        }
        val tag = if (target.lastIndexOf(':') > 0) {
            target.substring(target.lastIndexOf(':') + 1)
        } else {
            ""
        }

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

    private fun onMessage(
        integrationEventClass: Class<out Any>,
        msgs: List<MessageExt>,
        context: ConsumeConcurrentlyContext
    ): ConsumeConcurrentlyStatus {
        return try {
            msgs.forEach { msg ->
                log.info("集成事件消费，msgId=${msg.msgId}")
                val strMsg = String(msg.body, charset(msgCharset))
                val eventPayload = JSON.parseObject(strMsg, integrationEventClass, Feature.SupportNonPublicField)

                if (orderedEventMessageInterceptors.isEmpty()) {
                    eventSubscriberManager.dispatch(eventPayload)
                } else {
                    val headers = mutableMapOf<String, Any>().apply {
                        putAll(msg.properties)
                    }
                    val message =
                        GenericMessage(eventPayload, EventMessageInterceptor.ModifiableMessageHeaders(headers))

                    orderedEventMessageInterceptors.forEach { interceptor ->
                        interceptor.preSubscribe(message)
                    }

                    // 拦截器可能修改消息，重新赋值
                    val modifiedEventPayload = message.payload
                    eventSubscriberManager.dispatch(modifiedEventPayload)

                    orderedEventMessageInterceptors.forEach { interceptor ->
                        interceptor.postSubscribe(message)
                    }
                }
            }
            ConsumeConcurrentlyStatus.CONSUME_SUCCESS
        } catch (ex: Exception) {
            log.error("集成事件消息消费异常", ex)
            ConsumeConcurrentlyStatus.RECONSUME_LATER
        }
    }

    private fun getTopicConsumerGroup(topic: String, defaultVal: String): String {
        val actualDefaultVal = defaultVal.takeIf { StringUtils.isNotBlank(it) }
            ?: "$topic-4-$applicationName"
        return resolvePlaceholderWithCache(
            "\${rocketmq.$topic.consumer.group:$actualDefaultVal}",
            environment
        )
    }

    private fun getTopicNamesrvAddr(topic: String, defaultVal: String): String {
        val actualDefaultVal = defaultVal.takeIf { StringUtils.isNotBlank(it) } ?: defaultNameSrv
        return resolvePlaceholderWithCache(
            "\${rocketmq.$topic.name-server:$actualDefaultVal}",
            environment
        )
    }
}
