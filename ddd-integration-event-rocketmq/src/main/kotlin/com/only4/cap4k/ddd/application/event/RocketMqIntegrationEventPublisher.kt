package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublishCompletion
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.application.event.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.event.RuntimeProviderStateRegistry
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import org.apache.rocketmq.client.producer.SendCallback
import org.apache.rocketmq.client.producer.SendResult
import org.apache.rocketmq.client.producer.SendStatus
import org.apache.rocketmq.spring.core.RocketMQTemplate
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.support.GenericMessage

/** RocketMQ adapter for the shared Integration Event envelope. */
class RocketMqIntegrationEventPublisher(
    private val rocketMQTemplate: RocketMQTemplate,
    private val routeResolver: IntegrationEventRouteResolver<RocketMqIntegrationEventRoute>,
    private val deliveryTimeoutMillis: Long,
    private val providerStateRegistry: RuntimeProviderStateRegistry,
    private val envelopeCodec: IntegrationEventEnvelopeCodec = IntegrationEventEnvelopeCodec(),
) : IntegrationEventPublisher {
    init {
        require(deliveryTimeoutMillis > 0) { "RocketMQ delivery timeout must be positive" }
        providerStateRegistry.report(PROVIDER_IDENTITY, RuntimeProviderState.RECOVERING, "awaiting-evidence")
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
        } catch (throwable: Throwable) {
            log.error("Integration Event publish preparation failed: eventId={}", event.id, throwable)
            completion.failure(throwable)
        }
    }

    private fun publishMessage(
        event: EventRecord,
        route: RocketMqIntegrationEventRoute,
        message: Message<Any>,
        completion: IntegrationEventPublishCompletion,
    ) {
        if (providerStateRegistry.state(PROVIDER_IDENTITY)?.state == RuntimeProviderState.DEGRADED) {
            providerStateRegistry.report(PROVIDER_IDENTITY, RuntimeProviderState.RECOVERING, "publish-attempt")
        }
        try {
            rocketMQTemplate.asyncSend(
                route.destination,
                message,
                IntegrationEventSendCallback(event, route, completion, providerStateRegistry),
                deliveryTimeoutMillis,
            )
        } catch (throwable: Throwable) {
            terminalFailure(event, throwable, "send-exception", completion, providerStateRegistry)
        }
    }

    class IntegrationEventSendCallback(
        private val event: EventRecord,
        private val route: RocketMqIntegrationEventRoute,
        private val completion: IntegrationEventPublishCompletion,
        private val providerStateRegistry: RuntimeProviderStateRegistry,
    ) : SendCallback {
        override fun onSuccess(sendResult: SendResult?) {
            val status = sendResult?.sendStatus
            if (status == SendStatus.SEND_OK) {
                if (completion.success()) {
                    providerStateRegistry.report(PROVIDER_IDENTITY, RuntimeProviderState.HEALTHY, "send-ok")
                    log.info(
                        "Integration Event handed to RocketMQ: eventId={}, topic={}, tag={}, msgId={}",
                        event.id,
                        route.topic,
                        route.tag,
                        sendResult.msgId,
                    )
                }
                return
            }

            terminalFailure(
                event,
                RocketMqPublishResultException(route, status),
                "send-status-${status?.name ?: "MISSING"}",
                completion,
                providerStateRegistry,
            )
        }

        override fun onException(throwable: Throwable?) {
            terminalFailure(
                event,
                throwable ?: IllegalStateException("RocketMQ send callback reported a missing failure"),
                "send-exception",
                completion,
                providerStateRegistry,
            )
        }
    }

    companion object {
        const val PROVIDER_IDENTITY = "integration-event:rocketmq"
        private val log = LoggerFactory.getLogger(RocketMqIntegrationEventPublisher::class.java)

        private fun terminalFailure(
            event: EventRecord,
            throwable: Throwable,
            category: String,
            completion: IntegrationEventPublishCompletion,
            providerStateRegistry: RuntimeProviderStateRegistry,
        ) {
            if (!completion.failure(throwable)) return
            providerStateRegistry.report(PROVIDER_IDENTITY, RuntimeProviderState.DEGRADED, category)
            log.error("RocketMQ Integration Event handoff failed: eventId={}, category={}", event.id, category, throwable)
        }
    }
}

class RocketMqPublishResultException(
    route: RocketMqIntegrationEventRoute,
    status: SendStatus?,
) : IllegalStateException(
    "RocketMQ did not confirm handoff for topic=${route.topic}, tag=${route.tag}, status=${status?.name ?: "MISSING"}",
)
