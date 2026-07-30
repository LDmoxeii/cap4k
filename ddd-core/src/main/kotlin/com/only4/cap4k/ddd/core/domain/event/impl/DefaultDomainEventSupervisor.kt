package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.ProviderUnavailableException
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.DomainEventManager
import com.only4.cap4k.ddd.core.domain.event.DomainEventSupervisor
import com.only4.cap4k.ddd.core.domain.event.EventRuntimeContextManager
import com.only4.cap4k.ddd.core.domain.event.EventSubscriberManager
import com.only4.cap4k.ddd.core.domain.event.ReliableDomainEventProvider
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.misc.findMethod
import java.time.LocalDateTime

/**
 * Default local domain-event provider. Immediate non-persistent events are published synchronously.
 */
open class DefaultDomainEventSupervisor(
    private val domainEventInterceptorManager: DomainEventInterceptorManager,
    private val eventSubscriberManager: EventSubscriberManager,
    private val reliableDomainEventProvider: ReliableDomainEventProvider? = null,
    private val integrationEventManager: IntegrationEventManager? = null,
) : DomainEventSupervisor, DomainEventManager {

    companion object {
        @JvmStatic
        fun reset() = EventRuntimeContextManager.reset()
    }

    override fun <DOMAIN_EVENT : Any, ENTITY : Any> attach(
        domainEventPayload: DOMAIN_EVENT,
        entity: ENTITY,
        schedule: LocalDateTime,
    ) {
        validateDomainEvent(domainEventPayload)
        EventRuntimeContext.attachmentScope()
            .attachDomain(entity, EventAttachment.eager(domainEventPayload, schedule))
        domainEventInterceptorManager.orderedDomainEventInterceptors
            .forEach { it.onAttach(domainEventPayload, entity, schedule) }
    }

    override fun <DOMAIN_EVENT : Any, ENTITY : Any> attach(
        entity: ENTITY,
        schedule: LocalDateTime,
        domainEventPayloadSupplier: () -> DOMAIN_EVENT,
    ) {
        EventRuntimeContext.attachmentScope()
            .attachDomain(entity, EventAttachment.lazy(schedule, domainEventPayloadSupplier))
    }

    override fun <DOMAIN_EVENT : Any, ENTITY : Any> detach(domainEventPayload: DOMAIN_EVENT, entity: ENTITY) {
        val eventPayloads = EventRuntimeContext.attachmentScope().domainAttachments[entity] ?: return
        val identityIndex = eventPayloads.indexOfFirst { it.matchesIdentity(domainEventPayload) }
        val removeIndex = if (identityIndex >= 0) identityIndex else eventPayloads.indexOfFirst { it.matches(domainEventPayload) }
        if (removeIndex < 0) return
        eventPayloads.removeAt(removeIndex)
        domainEventInterceptorManager.orderedDomainEventInterceptors
            .forEach { it.onDetach(domainEventPayload, entity) }
    }

    override fun release(entities: Set<Any>) {
        val ambientScope = EventRuntimeContext.currentUnitOfWorkOrNull() ?: EventRuntimeContext.currentOrNull()
        val popAmbient = ambientScope?.type == EventRuntimeScopeType.AMBIENT
        var completed = false
        try {
            val attachments = buildList {
                entities.forEach { entity ->
                    addAll(popEvents(entity))
                    addAll(popSpringDataEvents(entity))
                }
            }
            val now = LocalDateTime.now()
            attachments.forEach { attachment ->
                val eventPayload = attachment.resolve()
                validateDomainEvent(eventPayload)
                if (requiresReliableProvider(eventPayload, attachment.schedule, now)) {
                    reliableProvider().publish(eventPayload, attachment.schedule)
                } else {
                    publishLocal(eventPayload)
                }
            }
            completed = true
        } finally {
            cleanupAmbientScope(ambientScope, popAmbient, completed)
        }
    }

    override fun pendingCount(): Int =
        (EventRuntimeContext.currentUnitOfWorkOrNull() ?: EventRuntimeContext.currentOrNull())
            ?.domainAttachments
            ?.values
            ?.sumOf { it.size }
            ?: 0

    override fun discard(entity: Any) {
        (EventRuntimeContext.currentUnitOfWorkOrNull() ?: EventRuntimeContext.currentOrNull())
            ?.domainAttachments
            ?.remove(entity)
    }

    protected open fun requiresReliableProvider(
        eventPayload: Any,
        schedule: LocalDateTime,
        now: LocalDateTime,
    ): Boolean = eventPayload.javaClass.getAnnotation(DomainEvent::class.java)?.persist == true || schedule.isAfter(now)

    private fun reliableProvider(): ReliableDomainEventProvider = reliableDomainEventProvider
        ?: throw ProviderUnavailableException("reliable-domain-events", "cap4k-ddd-domain-event-jpa-starter")

    private fun publishLocal(eventPayload: Any) {
        val outerScope = EventRuntimeContext.currentOrNull()
        val dispatchScope = EventRuntimeContext.push(EventRuntimeScopeType.DOMAIN_DISPATCH)
        outerScope?.captureListenerMetadata()?.let(dispatchScope::restoreListenerMetadata)
        var completed = false
        try {
            EventRuntimeContext.withCausalFrame("Event:${eventPayload.javaClass.name}") {
                eventSubscriberManager.dispatch(eventPayload)
            }
            if (dispatchScope.integrationAttachments.isNotEmpty()) {
                (integrationEventManager
                    ?: throw ProviderUnavailableException(
                        "integration-event-manager",
                        "a cap4k Integration Event transport starter",
                    )).release()
            }
            completed = true
        } finally {
            if (!completed) EventRuntimeContext.discard(dispatchScope)
            if (EventRuntimeContext.currentOrNull() === dispatchScope) EventRuntimeContext.pop(dispatchScope)
        }
    }

    private fun popEvents(entity: Any): List<EventAttachment<Any>> =
        (EventRuntimeContext.currentUnitOfWorkOrNull() ?: EventRuntimeContext.currentOrNull())
            ?.domainAttachments
            ?.remove(entity)
            ?.toList()
            .orEmpty()

    private fun popSpringDataEvents(entity: Any): List<EventAttachment<Any>> {
        val aggregateRootType = generateSequence(entity.javaClass) { it.superclass }
            .firstOrNull { it.name == "org.springframework.data.domain.AbstractAggregateRoot" }
            ?: return emptyList()
        val domainEventsMethod = findMethod(aggregateRootType, "domainEvents") { it.parameterCount == 0 } ?: return emptyList()
        val clearDomainEventsMethod = findMethod(aggregateRootType, "clearDomainEvents") { it.parameterCount == 0 }
        domainEventsMethod.isAccessible = true
        clearDomainEventsMethod?.isAccessible = true
        val events = (domainEventsMethod.invoke(entity) as? Collection<*>)?.filterNotNull().orEmpty()
        clearDomainEventsMethod?.invoke(entity)
        val now = LocalDateTime.now()
        return events.map { EventAttachment.eager(it, now) }
    }

    private fun validateDomainEvent(eventPayload: Any) {
        if (eventPayload.javaClass.isAnnotationPresent(IntegrationEvent::class.java)) {
            throw DomainException("事件类型不能为集成事件")
        }
        DomainEventPayloadValidator.validate(eventPayload)
    }

    private fun cleanupAmbientScope(scope: EventRuntimeScope?, shouldPop: Boolean, completed: Boolean) {
        if (!shouldPop || scope == null || EventRuntimeContext.currentOrNull() !== scope) return
        if (!completed) {
            EventRuntimeContext.discard(scope)
            EventRuntimeContext.pop(scope)
            return
        }
        if (scope.domainAttachments.isEmpty() && scope.integrationAttachments.isEmpty()) {
            EventRuntimeContext.pop(scope)
        }
    }
}
