package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublishCompletion
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
import java.util.Date
import java.util.concurrent.Executor

/** RabbitMQ adapter for the shared Integration Event envelope. */
class RabbitMqIntegrationEventPublisher(
    private val rabbitTemplate: RabbitTemplate,
    private val connectionFactory: ConnectionFactory,
    private val environment: Environment,
    private val threadPoolSize: Int,
    private val threadFactoryClassName: String = "",
    private val autoDeclareExchange: Boolean = false,
    private val defaultExchangeType: String = "direct",
    private val envelopeCodec: IntegrationEventEnvelopeCodec = IntegrationEventEnvelopeCodec(),
    private val executorOverride: Executor? = null,
) : IntegrationEventPublisher {

    companion object {
        private val log = LoggerFactory.getLogger(RabbitMqIntegrationEventPublisher::class.java)
    }

    private val executor: Executor by lazy {
        executorOverride ?: createFixedThreadPool(threadPoolSize, threadFactoryClassName, this::class.java.classLoader)
    }

    fun init() {
        executor
    }

    override fun publish(
        event: EventRecord,
        envelope: IntegrationEventEnvelope,
        publishCallback: IntegrationEventPublisher.PublishCallback,
    ) {
        val completion = IntegrationEventPublishCompletion(event, publishCallback)
        try {
            publishBody(
                event = event,
                body = envelopeCodec.encode(envelope),
                completion = completion,
            )
        } catch (throwable: Throwable) {
            log.error("集成事件发布失败: ${event.id}", throwable)
            completion.failure(throwable)
        }
    }

    private fun publishBody(
        event: EventRecord,
        body: String,
        completion: IntegrationEventPublishCompletion,
    ) {
        try {
            val destination = resolvePlaceholderWithCache(event.type, environment)
            if (destination.isBlank()) {
                throw DomainException("集成事件发布失败: ${event.id} 缺失topic")
            }
            val (exchange, tag) = parseDestination(destination)
            if (autoDeclareExchange) tryDeclareExchange(exchange, defaultExchangeType)

            executor.execute {
                runCatching {
                    rabbitTemplate.convertAndSend(
                        exchange,
                        tag,
                        body,
                        IntegrationEventSendCallback(event),
                    )
                    completion.success()
                }.onFailure { throwable ->
                    log.error("集成事件发布失败: ${event.id}", throwable)
                    completion.failure(throwable)
                }
            }
        } catch (ex: Exception) {
            log.error("集成事件发布失败: ${event.id}", ex)
            completion.failure(ex)
        }
    }

    private fun parseDestination(destination: String): Pair<String, String> =
        destination.split(":", limit = 2).let { parts ->
            if (parts.size == 2) parts[0] to parts[1] else destination to ""
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

    class IntegrationEventSendCallback(
        private val event: EventRecord,
    ) : MessagePostProcessor {
        override fun postProcessMessage(message: Message): Message {
            message.messageProperties.messageId = event.id
            message.messageProperties.timestamp = Date.from(event.publishedAt)
            return message
        }
    }
}
