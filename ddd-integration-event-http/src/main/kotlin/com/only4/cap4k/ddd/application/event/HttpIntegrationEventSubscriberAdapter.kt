package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.application.event.capabilities.IntegrationEventHttpSubscribeCapability
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventDeliveryMetadata
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.deliveryContext
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint
import com.only4.cap4k.ddd.core.share.misc.resolvePlaceholderWithCache
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils
import org.springframework.core.env.Environment
import org.springframework.messaging.support.GenericMessage

/** HTTP inbound adapter for the shared Integration Event envelope. */
class HttpIntegrationEventSubscriberAdapter(
    private val eventHandlerDispatcher: EventHandlerDispatcher,
    private val eventMessageInterceptors: List<EventMessageInterceptor>,
    private val httpIntegrationEventSubscriberRegister: HttpIntegrationEventSubscriberRegister,
    private val environment: Environment,
    private val eventTypeCatalog: EventTypeCatalog,
    private val applicationName: String,
    private val httpBaseUrl: String,
    private val httpSubscribePath: String,
    private val httpConsumePath: String,
    private val executionContextCodecRegistry: ExecutionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
    private val executionContextScopeManager: ExecutionContextScopeManager = ExecutionContextScopeManager {
        AutoCloseable { }
    },
    private val reliableEventDeliveryContextScopeManager: ReliableEventDeliveryContextScopeManager,
    private val envelopeCodec: IntegrationEventEnvelopeCodec = IntegrationEventEnvelopeCodec(),
) {
    private val log = LoggerFactory.getLogger(HttpIntegrationEventSubscriberAdapter::class.java)
    private val eventPayloadClassMap = mutableMapOf<String, Class<*>>()
    private val subscriberIdentityMap = mutableMapOf<String, String>()

    private val orderedEventMessageInterceptors by lazy {
        eventMessageInterceptors.sortedBy { interceptor ->
            OrderUtils.getOrder(interceptor.javaClass, Ordered.LOWEST_PRECEDENCE)
        }
    }

    fun init() {
        eventTypeCatalog.integrationEventTypes()
            .filter { cls ->
                val integrationEvent = cls.getAnnotation(IntegrationEvent::class.java)
                integrationEvent.value.isNotBlank() &&
                    !IntegrationEvent.NONE_SUBSCRIBER.equals(integrationEvent.subscriber, ignoreCase = true)
            }
            .map { cls -> createEventRegistration(cls) }
            .forEach(::registerEvent)
    }

    private fun createEventRegistration(eventClass: Class<*>): EventRegistration {
        val annotation = eventClass.getAnnotation(IntegrationEvent::class.java)
        val isRemote = annotation.value.contains("@")
        val targetAndUrl = annotation.value.split("@")
        val target = resolvePlaceholderWithCache(targetAndUrl[0], environment)
        val subscriber = annotation.subscriber.ifBlank { applicationName }
            .let { resolvePlaceholderWithCache(it, environment) }
        val registerUrl = if (isRemote && targetAndUrl.size > 1) {
            targetAndUrl[1]
        } else {
            httpBaseUrl + httpSubscribePath
        }
        return EventRegistration(
            eventClass = eventClass,
            wireEventType = annotation.value,
            target = target,
            subscriber = subscriber,
            isRemote = isRemote,
            registerUrl = registerUrl,
            callbackUrl = httpBaseUrl + httpConsumePath,
        )
    }

    private fun registerEvent(registration: EventRegistration) {
        if (!registration.isRemote) {
            httpIntegrationEventSubscriberRegister.subscribe(
                registration.target,
                registration.subscriber,
                registration.callbackUrl,
            )
        } else {
            Mediator.capabilities.call(
                IntegrationEventHttpSubscribeCapability.Request(
                    url = registration.registerUrl,
                    event = registration.target,
                    subscriber = registration.subscriber,
                    callbackUrl = registration.callbackUrl,
                )
            )
        }
        setOf(registration.wireEventType, registration.target).forEach { eventType ->
            eventPayloadClassMap[eventType] = registration.eventClass
            subscriberIdentityMap[eventType] = registration.subscriber
        }
    }

    /** Consumes the canonical envelope body. */
    fun consume(
        envelopeJson: String,
        headers: Map<String, Any> = emptyMap(),
    ): Boolean = runCatching {
        val envelope = envelopeCodec.decode(envelopeJson)
        consumeDecoded(envelope, headers)
    }.onFailure { ex ->
        log.error("集成事件消费失败", ex)
    }.getOrDefault(false)

    private fun consumeDecoded(envelope: IntegrationEventEnvelope, headers: Map<String, Any>): Boolean {
        val integrationEventClass = eventPayloadClassMap[envelope.eventType]
            ?: return logAndReturnFailure("未找到事件类型映射", envelope.eventType)
        val eventPayload = envelopeCodec.payloadJson(envelope, integrationEventClass)
        val subscriberIdentity = subscriberIdentityMap[envelope.eventType]
            ?: applicationName
        val deliveryContext = envelope.deliveryContext(
            IntegrationEventDeliveryMetadata(
                subscriberIdentity = subscriberIdentity,
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
        return true
    }

    private fun logAndReturnFailure(reason: String, eventName: String): Boolean {
        log.error("集成事件消费失败 - $reason, event: $eventName")
        return false
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

    private data class EventRegistration(
        val eventClass: Class<*>,
        val wireEventType: String,
        val target: String,
        val subscriber: String,
        val isRemote: Boolean,
        val registerUrl: String,
        val callbackUrl: String,
    )

    data class OperationResponse<T : Any>(
        val success: Boolean = false,
        val message: String? = null,
        val data: T? = null,
    )
}
