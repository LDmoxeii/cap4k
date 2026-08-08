package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptorManager
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils

class ReliableEventInfrastructure(
    messageInterceptors: List<EventMessageInterceptor>,
    eventInterceptors: List<EventInterceptor>,
) : EventMessageInterceptorManager, IntegrationEventInterceptorManager {
    override val orderedEventMessageInterceptors: Set<EventMessageInterceptor> = messageInterceptors
        .sortedBy { OrderUtils.getOrder(it.javaClass, Ordered.LOWEST_PRECEDENCE) }
        .toCollection(LinkedHashSet())

    override val orderedIntegrationEventInterceptors: Set<IntegrationEventInterceptor> = eventInterceptors
        .filterIsInstance<IntegrationEventInterceptor>()
        .sortedBy { OrderUtils.getOrder(it.javaClass, Ordered.LOWEST_PRECEDENCE) }
        .toCollection(LinkedHashSet())

    override val orderedEventInterceptors4IntegrationEvent: Set<EventInterceptor> = eventInterceptors
        .filter { it !is DomainEventInterceptor || it is IntegrationEventInterceptor }
        .sortedBy { OrderUtils.getOrder(it.javaClass, Ordered.LOWEST_PRECEDENCE) }
        .toCollection(LinkedHashSet())

}
