package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventDeliveryMetadata
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.deliveryContext
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils
import org.springframework.messaging.support.GenericMessage

/** HTTP inbound adapter for the shared Integration Event envelope. */
class HttpIntegrationEventSubscriberAdapter(
    private val eventHandlerDispatcher: EventHandlerDispatcher,
    private val eventMessageInterceptors: List<EventMessageInterceptor>,
    private val eventTypeCatalog: InboundIntegrationEventRegistrationView,
    private val executionContextCodecRegistry: ExecutionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
    private val executionContextScopeManager: ExecutionContextScopeManager = ExecutionContextScopeManager {
        AutoCloseable { }
    },
    private val reliableEventDeliveryContextScopeManager: ReliableEventDeliveryContextScopeManager,
    private val envelopeCodec: IntegrationEventEnvelopeCodec = IntegrationEventEnvelopeCodec(),
) {
    private val log = LoggerFactory.getLogger(HttpIntegrationEventSubscriberAdapter::class.java)
    private val eventPayloadClassMap = eventTypeCatalog.integrationEventTypesByName()

    private val orderedEventMessageInterceptors by lazy {
        eventMessageInterceptors.sortedBy { interceptor ->
            OrderUtils.getOrder(interceptor.javaClass, Ordered.LOWEST_PRECEDENCE)
        }
    }

    /** Consumes the canonical envelope body. */
    fun consume(
        envelopeJson: String,
        headers: Map<String, Any> = emptyMap(),
    ): HttpIntegrationEventConsumeResult {
        val envelope = try {
            envelopeCodec.decode(envelopeJson)
        } catch (failure: Throwable) {
            return failed(HttpIntegrationEventConsumeCategory.MALFORMED_ENVELOPE, failure = failure)
        }
        return consumeDecoded(envelope, headers)
    }

    private fun consumeDecoded(
        envelope: IntegrationEventEnvelope,
        headers: Map<String, Any>,
    ): HttpIntegrationEventConsumeResult {
        val integrationEventClass = eventPayloadClassMap[envelope.eventType]
            ?: return failed(HttpIntegrationEventConsumeCategory.UNKNOWN_EVENT, envelope.eventType)
        return try {
            val eventPayload = envelopeCodec.payloadJson(envelope, integrationEventClass)
            val deliveryContext = envelope.deliveryContext(
                IntegrationEventDeliveryMetadata(
                    redeliveryHint = ReliableEventRedeliveryHint.UNKNOWN,
                )
            )
            val executionContext = executionContextCodecRegistry.decodeExternal(
                envelope.executionContext,
                ExecutionContextBoundary.INTEGRATION_EVENT,
            )
            executionContextScopeManager.install(executionContext).use {
                processEventWithInterceptors(eventPayload, headers, deliveryContext)
            }
            HttpIntegrationEventConsumeResult(HttpIntegrationEventConsumeCategory.SUCCESS)
        } catch (failure: Throwable) {
            failed(HttpIntegrationEventConsumeCategory.DELIVERY_FAILED, envelope.eventType, failure)
        }
    }

    private fun failed(
        category: HttpIntegrationEventConsumeCategory,
        eventName: String? = null,
        failure: Throwable? = null,
    ): HttpIntegrationEventConsumeResult {
        log.error(
            "Integration Event HTTP consume failed: category={}, eventName={}, failureType={}",
            category,
            eventName ?: "unknown",
            failure?.javaClass?.name ?: "none",
        )
        return HttpIntegrationEventConsumeResult(category)
    }

    private fun processEventWithInterceptors(
        eventPayload: Any,
        headers: Map<String, Any>,
        deliveryContext: ReliableEventDeliveryContext,
    ) {
        if (orderedEventMessageInterceptors.isEmpty()) {
            dispatchWithDeliveryContext(deliveryContext, eventPayload)
            return
        }
        val message = GenericMessage(
            eventPayload,
            EventMessageInterceptor.ModifiableMessageHeaders(headers),
        )
        reliableEventDeliveryContextScopeManager.suppress().use {
            orderedEventMessageInterceptors.forEach { it.preSubscribe(message) }
        }
        dispatchWithDeliveryContext(deliveryContext, message.payload)
        reliableEventDeliveryContextScopeManager.suppress().use {
            orderedEventMessageInterceptors.forEach { it.postSubscribe(message) }
        }
    }

    private fun dispatchWithDeliveryContext(
        deliveryContext: ReliableEventDeliveryContext,
        eventPayload: Any,
    ) {
        reliableEventDeliveryContextScopeManager.install(deliveryContext).use {
            eventHandlerDispatcher.dispatch(eventPayload)
        }
    }

    data class OperationResponse<T : Any>(
        val success: Boolean = false,
        val message: String? = null,
        val data: T? = null,
    )
}

data class HttpIntegrationEventConsumeResult(
    val category: HttpIntegrationEventConsumeCategory,
) {
    val success: Boolean
        get() = category == HttpIntegrationEventConsumeCategory.SUCCESS
}

enum class HttpIntegrationEventConsumeCategory {
    SUCCESS,
    MALFORMED_ENVELOPE,
    UNKNOWN_EVENT,
    DELIVERY_FAILED,
}
