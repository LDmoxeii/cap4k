package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublishCompletion
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.misc.resolvePlaceholderWithCache
import org.apache.rocketmq.client.producer.SendCallback
import org.apache.rocketmq.client.producer.SendResult
import org.apache.rocketmq.spring.core.RocketMQTemplate
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.messaging.Message
import org.springframework.messaging.support.GenericMessage

/** RocketMQ adapter for the shared Integration Event envelope. */
class RocketMqIntegrationEventPublisher(
    private val rocketMQTemplate: RocketMQTemplate,
    private val environment: Environment,
    private val envelopeCodec: IntegrationEventEnvelopeCodec = IntegrationEventEnvelopeCodec(),
) : IntegrationEventPublisher {

    companion object {
        private val log = LoggerFactory.getLogger(RocketMqIntegrationEventPublisher::class.java)
    }

    override fun publish(
        event: EventRecord,
        envelope: IntegrationEventEnvelope,
        publishCallback: IntegrationEventPublisher.PublishCallback,
    ) {
        val completion = IntegrationEventPublishCompletion(event, publishCallback)
        try {
            val message: Message<Any> = GenericMessage(envelopeCodec.encode(envelope))
            publishMessage(event, message, completion)
        } catch (throwable: Throwable) {
            log.error("集成事件发布失败: ${event.id}", throwable)
            completion.failure(throwable)
        }
    }

    private fun publishMessage(
        event: EventRecord,
        message: Message<Any>,
        completion: IntegrationEventPublishCompletion,
    ) {
        try {
            val destination = resolvePlaceholderWithCache(event.type, environment)
            if (destination.isBlank()) {
                throw DomainException("集成事件发布失败: ${event.id} 缺失topic")
            }
            rocketMQTemplate.asyncSend(
                destination,
                message,
                IntegrationEventSendCallback(event, completion),
            )
        } catch (ex: Exception) {
            log.error("集成事件发布失败: ${event.id}", ex)
            completion.failure(ex)
        }
    }

    class IntegrationEventSendCallback(
        private val event: EventRecord,
        private val completion: IntegrationEventPublishCompletion,
    ) : SendCallback {
        companion object {
            private val log = LoggerFactory.getLogger(IntegrationEventSendCallback::class.java)
        }

        override fun onSuccess(sendResult: SendResult) {
            log.info("集成事件发送成功, ${event.id} msgId=${sendResult.msgId}")
            completion.success()
        }

        override fun onException(throwable: Throwable) {
            log.error("集成事件发送失败, ${event.id}", throwable)
            completion.failure(throwable)
        }
    }
}
