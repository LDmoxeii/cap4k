package com.only4.cap4k.ddd.application.event

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.parser.Feature
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventSubscriberManager
import com.only4.cap4k.ddd.core.share.misc.findIntegrationEventClasses
import com.only4.cap4k.ddd.core.share.misc.resolvePlaceholderWithCache
import com.rabbitmq.client.Channel
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpException
import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils
import org.springframework.core.env.Environment
import org.springframework.messaging.Message
import org.springframework.messaging.support.GenericMessage

/**
 * 自动监听集成事件对应的RabbitMQ
 *
 * @author LD_moxeii
 * @date 2025/07/31
 */
class RabbitMqIntegrationEventSubscriberAdapter(
    private val eventSubscriberManager: EventSubscriberManager,
    private val eventMessageInterceptors: List<EventMessageInterceptor>,
    private val rabbitMqIntegrationEventConfigure: RabbitMqIntegrationEventConfigure?,
    private val rabbitListenerContainerFactory: SimpleRabbitListenerContainerFactory,
    private val connectionFactory: ConnectionFactory,
    private val environment: Environment,
    private val scanPath: String,
    private val applicationName: String,
    private val msgCharset: String = "UTF-8",
    private val autoDeclareQueue: Boolean = false
) {

    companion object {
        private val log = LoggerFactory.getLogger(RabbitMqIntegrationEventSubscriberAdapter::class.java)
    }

    private val simpleMessageListenerContainers by lazy {
        findIntegrationEventClasses(scanPath)
            .filter { cls ->
                val integrationEvent = cls.getAnnotation(IntegrationEvent::class.java)
                integrationEvent != null &&
                        integrationEvent.value.isNotBlank() &&
                        !IntegrationEvent.NONE_SUBSCRIBER.equals(integrationEvent.subscriber, ignoreCase = true)
            }
            .mapNotNull { integrationEventClass ->
                val container = rabbitMqIntegrationEventConfigure?.get(integrationEventClass)
                    ?: createDefaultConsumer(integrationEventClass)

                try {
                    if (container.messageListener == null) {
                        container.messageListener = ChannelAwareMessageListener { message, channel ->
                            onMessage(integrationEventClass, message, channel!!)
                        }
                    }
                    container.start()
                    container
                } catch (e: AmqpException) {
                    log.error("集成事件消息监听启动失败", e)
                    null
                }
            }
    }

    fun init() {
        simpleMessageListenerContainers
    }

    /**
     * 获取排序后的事件消息拦截器
     * 基于 @Order 注解排序
     */
    private val orderedEventMessageInterceptors: List<EventMessageInterceptor> by lazy {
        eventMessageInterceptors.sortedBy { interceptor ->
            OrderUtils.getOrder(interceptor::class.java, Ordered.LOWEST_PRECEDENCE)
        }
    }

    fun shutdown() {
        log.info("集成事件消息监听退出...")
        if (simpleMessageListenerContainers.isEmpty()) {
            return
        }

        simpleMessageListenerContainers.forEach { container ->
            try {
                container.shutdown()
            } catch (ex: Exception) {
                log.error("集成事件消息监听退出异常", ex)
            }
        }
    }

    fun createDefaultConsumer(integrationEventClass: Class<*>): SimpleMessageListenerContainer {
        val integrationEvent = integrationEventClass.getAnnotation(IntegrationEvent::class.java)

        val target = resolvePlaceholderWithCache(integrationEvent.value, environment)
        val subscriber = resolvePlaceholderWithCache(integrationEvent.subscriber, environment)
        val (exchange, routingKey) = parseTarget(target)
        val queue = getExchangeConsumerQueueName(exchange, subscriber)

        if (autoDeclareQueue) {
            tryDeclareQueue(queue, exchange, routingKey)
        }

        return rabbitListenerContainerFactory.createListenerContainer().apply {
            setQueueNames(queue)
            acknowledgeMode = AcknowledgeMode.MANUAL
        }
    }

    private fun parseTarget(target: String): Pair<String, String> = target.split(':', limit = 2).let { parts ->
        if (parts.size == 2) {
            parts[0] to parts[1]
        } else {
            target to ""
        }
    }

    private fun org.springframework.amqp.core.Message.parseEventPayload(integrationEventClass: Class<*>): Any {
        val strMsg = String(this.body, charset(msgCharset))
        return JSON.parseObject(strMsg, integrationEventClass, Feature.SupportNonPublicField)
    }

    private fun processWithInterceptors(msg: org.springframework.amqp.core.Message, eventPayload: Any) {
        val message: Message<Any> = GenericMessage(
            eventPayload,
            EventMessageInterceptor.ModifiableMessageHeaders(msg.messageProperties.headers)
        )

        orderedEventMessageInterceptors.forEach { it.preSubscribe(message) }
        eventSubscriberManager.dispatch(message.payload)
        orderedEventMessageInterceptors.forEach { it.postSubscribe(message) }
    }

    private fun onMessage(
        integrationEventClass: Class<*>,
        msg: org.springframework.amqp.core.Message,
        channel: Channel
    ) = runCatching {
        log.info("集成事件消费，messageId=${msg.messageProperties.messageId}")
        val eventPayload = msg.parseEventPayload(integrationEventClass)

        if (orderedEventMessageInterceptors.isEmpty()) {
            eventSubscriberManager.dispatch(eventPayload)
        } else {
            processWithInterceptors(msg, eventPayload)
        }

        channel.basicAck(msg.messageProperties.deliveryTag, false)
    }.getOrElse { ex ->
        log.error("集成事件消息消费失败", ex)
        channel.basicReject(msg.messageProperties.deliveryTag, true)
    }

    private fun getExchangeConsumerQueueName(exchange: String, defaultVal: String?): String =
        resolvePlaceholderWithCache(
            "\${rabbitmq.$exchange.consumer.queue:${defaultVal.takeIf { !it.isNullOrBlank() } ?: "$exchange-4-$applicationName"}}",
            environment
        )

    private fun tryDeclareQueue(queue: String, exchange: String, routingKey: String) = runCatching {
        val exchangeType = resolvePlaceholderWithCache(
            "\${rabbitmq.$exchange.type:direct}",
            environment
        )

        connectionFactory.createConnection().use { connection ->
            connection.createChannel(false).use { channel ->
                channel.queueDeclare(queue, true, false, false, null)
                channel.queueBind(queue, exchange, routingKey)
            }
        }
    }.getOrElse { e ->
        log.error("创建消息队列失败", e)
        throw RuntimeException(e)
    }
}
