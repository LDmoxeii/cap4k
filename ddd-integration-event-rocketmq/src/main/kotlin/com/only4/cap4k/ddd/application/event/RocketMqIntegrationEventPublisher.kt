package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublishCompletion
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import org.apache.rocketmq.client.producer.SendCallback
import org.apache.rocketmq.client.producer.SendResult
import org.apache.rocketmq.client.producer.SendStatus
import org.apache.rocketmq.spring.core.RocketMQTemplate
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.support.GenericMessage
import java.util.concurrent.atomic.AtomicBoolean

/** RocketMQ adapter for the shared Integration Event envelope. */
class RocketMqIntegrationEventPublisher(
    private val rocketMQTemplate: RocketMQTemplate,
    private val routeResolver: IntegrationEventRouteResolver<RocketMqIntegrationEventRoute>,
    private val deliveryTimeoutMillis: Long,
    private val stateReporter: RuntimeProviderStateReporter,
    private val envelopeCodec: IntegrationEventEnvelopeCodec = IntegrationEventEnvelopeCodec(),
) : IntegrationEventPublisher {
    private val degraded = AtomicBoolean(false)

    fun init() {
        require(deliveryTimeoutMillis > 0) { "RocketMQ delivery timeout must be positive" }
        reportSafely(RuntimeProviderState.RECOVERING, "publisher-enrolled")
    }

    override fun publish(
        event: EventRecord,
        envelope: IntegrationEventEnvelope,
        publishCallback: IntegrationEventPublisher.PublishCallback,
    ) {
        val completion = IntegrationEventPublishCompletion(event, publishCallback)
        try {
            val route = routeResolver.resolve(envelope.eventType)
            val message: Message<Any> = GenericMessage(envelopeCodec.encode(envelope))
            publishMessage(event, route, message, completion)
        } catch (failure: Throwable) {
            completion.failure(failure)
            log.warn(
                "RocketMQ Integration Event publish preparation failed: eventId={}, eventName={}, category={}, exceptionType={}",
                event.id,
                envelope.eventType,
                "publish-preparation-failed",
                failure::class.java.name,
            )
        }
    }

    private fun publishMessage(
        event: EventRecord,
        route: RocketMqIntegrationEventRoute,
        message: Message<Any>,
        completion: IntegrationEventPublishCompletion,
    ) {
        if (degraded.get()) {
            reportSafely(RuntimeProviderState.RECOVERING, "publisher-recovery-attempt")
        }
        try {
            rocketMQTemplate.asyncSend(
                route.destination,
                message,
                IntegrationEventSendCallback(event, completion, this),
                deliveryTimeoutMillis,
            )
        } catch (failure: Throwable) {
            terminalFailure(event, failure, "send-exception", completion, this)
        }
    }

    class IntegrationEventSendCallback(
        private val event: EventRecord,
        private val completion: IntegrationEventPublishCompletion,
        private val publisher: RocketMqIntegrationEventPublisher,
    ) : SendCallback {
        private val terminal = AtomicBoolean(false)

        override fun onSuccess(sendResult: SendResult?) {
            if (!terminal.compareAndSet(false, true)) return
            val status = sendResult?.sendStatus
            if (status == SendStatus.SEND_OK) {
                completion.success()
                publisher.degraded.set(false)
                publisher.reportSafely(RuntimeProviderState.HEALTHY, "publisher-confirm-ack")
                log.info(
                    "RocketMQ Integration Event handed off: eventId={}, eventName={}",
                    event.id,
                    event.type,
                )
                return
            }

            terminalFailure(
                event,
                RocketMqPublishResultException(status),
                "send-status-${status?.name ?: "MISSING"}",
                completion,
                publisher,
            )
        }

        override fun onException(failure: Throwable?) {
            if (!terminal.compareAndSet(false, true)) return
            terminalFailure(
                event,
                failure ?: IllegalStateException("RocketMQ send callback reported a missing failure"),
                "send-exception",
                completion,
                publisher,
            )
        }
    }

    companion object {
        const val PROVIDER_IDENTITY = "integration-event-transport.rocketmq"
        private val log = LoggerFactory.getLogger(RocketMqIntegrationEventPublisher::class.java)

        private fun terminalFailure(
            event: EventRecord,
            failure: Throwable,
            category: String,
            completion: IntegrationEventPublishCompletion,
            publisher: RocketMqIntegrationEventPublisher,
        ) {
            completion.failure(failure)
            publisher.degraded.set(true)
            publisher.reportSafely(RuntimeProviderState.DEGRADED, category)
            log.warn(
                "RocketMQ Integration Event handoff failed: eventId={}, eventName={}, category={}, exceptionType={}",
                event.id,
                event.type,
                category,
                failure::class.java.name,
            )
        }
    }

    private fun reportSafely(state: RuntimeProviderState, category: String) {
        runCatching { stateReporter.report(state, category) }
            .onFailure { failure ->
                log.warn(
                    "RocketMQ Integration Event provider state report failed: providerId={}, state={}, category={}, exceptionType={}",
                    PROVIDER_IDENTITY,
                    state,
                    category,
                    failure::class.java.name,
                )
            }
    }
}

class RocketMqPublishResultException(
    status: SendStatus?,
) : IllegalStateException(
    "RocketMQ did not confirm Integration Event handoff: status=${status?.name ?: "MISSING"}",
)
