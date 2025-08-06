package com.only4.cap4k.ddd.application.event

import com.alibaba.fastjson.JSON
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.misc.createFixedThreadPool
import com.only4.cap4k.ddd.core.share.misc.resolvePlaceholderWithCache
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessagePostProcessor
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.core.env.Environment
import java.util.concurrent.ExecutorService

/**
 * 基于RabbitMq的集成事件发布器
 *
 * @author LD_moxeii
 * @date 2025/07/31
 */
class RabbitMqIntegrationEventPublisher(
    private val rabbitTemplate: RabbitTemplate,
    private val connectionFactory: ConnectionFactory,
    private val environment: Environment,
    private val threadPoolSize: Int,
    private val threadFactoryClassName: String = "",
    private val autoDeclareExchange: Boolean = false,
    private val defaultExchangeType: String = "direct"
) : IntegrationEventPublisher {

    companion object {
        private val log = LoggerFactory.getLogger(RabbitMqIntegrationEventPublisher::class.java)
    }

    private val executorService: ExecutorService by lazy {
        createFixedThreadPool(
            threadPoolSize,
            threadFactoryClassName,
            this::class.java.classLoader
        )
    }

    fun init() {
        executorService
    }

    override fun publish(event: EventRecord, publishCallback: IntegrationEventPublisher.PublishCallback) {
        try {
            // 事件的主题
            val destination = resolvePlaceholderWithCache(event.type, environment)

            if (destination.isBlank()) {
                throw DomainException("集成事件发布失败: ${event.id} 缺失topic")
            }

            val (exchange, tag) = parseDestination(destination)
            val message = JSON.toJSONString(event.message)

            if (autoDeclareExchange) {
                tryDeclareExchange(exchange, defaultExchangeType)
            }

            // MQ消息通道
            executorService.execute {
                rabbitTemplate.convertAndSend(
                    exchange,
                    tag,
                    message,
                    IntegrationEventSendCallback(event, publishCallback)
                )
            }
        } catch (ex: Exception) {
            log.error("集成事件发布失败: ${event.id}", ex)
        }
    }

    private fun parseDestination(destination: String): Pair<String, String> =
        destination.split(":", limit = 2).let { parts ->
            if (parts.size == 2) {
                parts[0] to parts[1]
            } else {
                destination to ""
            }
        }

    private fun tryDeclareExchange(exchange: String, exchangeType: String) {
        try {
            connectionFactory.createConnection().use { connection ->
                connection.createChannel(false).use { channel ->
                    channel.exchangeDeclare(exchange, exchangeType, true, false, null)
                }
            }
        } catch (e: Exception) {
            log.error("创建消息交换机失败", e)
            throw RuntimeException(e)
        }
    }

    /**
     * 集成事件发送回调处理器
     */
    class IntegrationEventSendCallback(
        private val event: EventRecord,
        private val publishCallback: IntegrationEventPublisher.PublishCallback
    ) : MessagePostProcessor {

        companion object {
            private val log = LoggerFactory.getLogger(IntegrationEventSendCallback::class.java)
        }

        override fun postProcessMessage(message: Message): Message {
            log.info("集成事件发送成功, ${event.id}")
            message.messageProperties.messageId = event.id

            try {
                publishCallback.onSuccess(event)
            } catch (throwable: Throwable) {
                log.error("回调失败（事件发送成功）", throwable)
                publishCallback.onException(event, throwable)
            }

            return message
        }
    }
}
