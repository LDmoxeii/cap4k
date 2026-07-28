package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptor
import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.EventInterceptor
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils

class DefaultDomainEventInterceptorManager(
    eventInterceptors: List<EventInterceptor>,
) : DomainEventInterceptorManager {
    override val orderedDomainEventInterceptors: Set<DomainEventInterceptor> = eventInterceptors
        .filterIsInstance<DomainEventInterceptor>()
        .sortedBy { OrderUtils.getOrder(it.javaClass, Ordered.LOWEST_PRECEDENCE) }
        .toCollection(LinkedHashSet())

    override val orderedEventInterceptors4DomainEvent: Set<EventInterceptor> = eventInterceptors
        .filter { it is DomainEventInterceptor || it !is com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptor }
        .sortedBy { OrderUtils.getOrder(it.javaClass, Ordered.LOWEST_PRECEDENCE) }
        .toCollection(LinkedHashSet())
}
