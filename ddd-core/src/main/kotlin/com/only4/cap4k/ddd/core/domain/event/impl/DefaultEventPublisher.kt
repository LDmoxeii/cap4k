package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.ProviderUnavailableException
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4K_EVENT_TYPE
import com.only4.cap4k.ddd.core.share.Constants.HEADER_VALUE_CAP4K_EVENT_TYPE_DOMAIN
import com.only4.cap4k.ddd.core.share.Constants.HEADER_VALUE_CAP4K_EVENT_TYPE_INTEGRATION
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/** Executes one reliable Event attempt that is already owned by the persistence coordinator. */
open class DefaultEventPublisher(
    private val eventHandlerDispatcher: EventHandlerDispatcher,
    private val integrationEventPublishers: List<IntegrationEventPublisher>,
    private val eventMessageInterceptorManager: EventMessageInterceptorManager,
    private val domainEventInterceptorManager: DomainEventInterceptorManager,
    private val integrationEventInterceptorManager: IntegrationEventInterceptorManager,
    private val integrationEventManager: IntegrationEventManager? = null,
    private val executionContextScopeManager: ExecutionContextScopeManager = ExecutionContextScopeManager {
        AutoCloseable { }
    },
    private val executionContextCodecRegistry: ExecutionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
    private val reliableEventDeliveryContextScopeManager: ReliableEventDeliveryContextScopeManager,
) : EventPublisher {

    override fun publish(event: EventRecord, completion: EventPublisher.Completion) {
        val message = event.message
        eventMessageInterceptorManager.orderedEventMessageInterceptors
            .forEach { interceptor -> interceptor.initPublish(message) }

        when (message.headers[HEADER_KEY_CAP4K_EVENT_TYPE] as? String) {
            HEADER_VALUE_CAP4K_EVENT_TYPE_INTEGRATION -> publishIntegrationEvent(event, completion)
            HEADER_VALUE_CAP4K_EVENT_TYPE_DOMAIN, null -> publishDomainEvent(event, completion)
            else -> completion.onFailure(
                event,
                IllegalArgumentException("Unsupported reliable Event type: ${message.headers[HEADER_KEY_CAP4K_EVENT_TYPE]}"),
            )
        }
    }

    private fun publishDomainEvent(event: EventRecord, completion: EventPublisher.Completion) {
        val executionContext = executionContextCodecRegistry.decodeReliable(
            event.executionContext,
            ExecutionContextBoundary.RELIABLE_DOMAIN_EVENT,
        )
        val result = runCatching {
            executionContextScopeManager.install(executionContext).use {
                publishDomainEventInScope(event)
            }
        }
        result.fold(
            onSuccess = { completion.onSuccess(event) },
            onFailure = { throwable ->
                domainEventInterceptorManager.orderedEventInterceptors4DomainEvent
                    .forEach { interceptor -> interceptor.onException(throwable, event) }
                log.error(
                    "Reliable Domain Event delivery failed: eventId={}, failureType={}",
                    event.id,
                    throwable.javaClass.name,
                )
                completion.onFailure(event, throwable)
            },
        )
    }

    private fun publishDomainEventInScope(event: EventRecord) {
        val message = event.message
        domainEventInterceptorManager.orderedEventInterceptors4DomainEvent
            .forEach { interceptor -> interceptor.preRelease(event) }
        eventMessageInterceptorManager.orderedEventMessageInterceptors
            .forEach { interceptor -> interceptor.prePublish(message) }

        val dispatchScope = EventRuntimeContext.push(EventRuntimeScopeType.DOMAIN_DISPATCH)
        var completed = false
        try {
            reliableEventDeliveryContextScopeManager.install(deliveryContext(event)).use {
                eventHandlerDispatcher.dispatch(event.payload)
            }
            if (dispatchScope.integrationAttachments.isNotEmpty()) {
                (integrationEventManager
                    ?: throw ProviderUnavailableException(
                        "integration-event-manager",
                        "a cap4k Integration Event transport starter",
                    )).release()
            }
            eventMessageInterceptorManager.orderedEventMessageInterceptors
                .forEach { interceptor -> interceptor.postPublish(message) }
            domainEventInterceptorManager.orderedEventInterceptors4DomainEvent
                .forEach { interceptor -> interceptor.postRelease(event) }
            completed = true
        } finally {
            if (!completed) EventRuntimeContext.discard(dispatchScope)
            if (EventRuntimeContext.currentOrNull() === dispatchScope) {
                EventRuntimeContext.pop(dispatchScope)
            }
        }
    }

    private fun publishIntegrationEvent(event: EventRecord, completion: EventPublisher.Completion) {
        val resolved = AtomicBoolean(false)
        val providerCallback = object : IntegrationEventPublisher.PublishCallback {
            override fun onSuccess(event: EventRecord) {
                if (!resolved.compareAndSet(false, true)) return
                withIntegrationContext(event) {
                    eventMessageInterceptorManager.orderedEventMessageInterceptors
                        .forEach { interceptor -> interceptor.postPublish(event.message) }
                    integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent
                        .forEach { interceptor -> interceptor.postRelease(event) }
                }
                completion.onSuccess(event)
            }

            override fun onException(event: EventRecord, throwable: Throwable) {
                if (!resolved.compareAndSet(false, true)) return
                withIntegrationContext(event) {
                    integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent
                        .forEach { interceptor -> interceptor.onException(throwable, event) }
                }
                completion.onFailure(event, throwable)
            }
        }

        runCatching {
            val provider = integrationEventPublishers.singleOrNull()
                ?: throw ProviderUnavailableException(
                    "integration-event-publisher",
                    "exactly one cap4k Integration Event transport starter",
                )
            withIntegrationContext(event) {
                integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent
                    .forEach { interceptor -> interceptor.preRelease(event) }
                eventMessageInterceptorManager.orderedEventMessageInterceptors
                    .forEach { interceptor -> interceptor.prePublish(event.message) }
                provider.publish(event, providerCallback)
            }
        }.onFailure { throwable -> providerCallback.onException(event, throwable) }
    }

    private fun <T> withIntegrationContext(event: EventRecord, block: () -> T): T {
        val executionContext = executionContextCodecRegistry.decodeReliable(
            event.executionContext,
            ExecutionContextBoundary.INTEGRATION_EVENT,
        )
        return executionContextScopeManager.install(executionContext).use {
            reliableEventDeliveryContextScopeManager.install(deliveryContext(event)).use {
                block()
            }
        }
    }

    private fun deliveryContext(event: EventRecord): ReliableEventDeliveryContext {
        val attempt = event.deliveryAttempt
        return ReliableEventDeliveryContext(
            eventId = event.id,
            eventName = event.type,
            publishedAt = event.publishedAt,
            attempt = attempt,
            redeliveryHint = when (attempt) {
                null -> ReliableEventRedeliveryHint.UNKNOWN
                1 -> ReliableEventRedeliveryHint.FIRST
                else -> ReliableEventRedeliveryHint.REDELIVERED
            },
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(DefaultEventPublisher::class.java)
    }
}
