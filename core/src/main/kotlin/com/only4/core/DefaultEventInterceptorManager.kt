package com.only4.core

import com.only4.core.application.event.IntegrationEventInterceptor
import com.only4.core.application.event.IntegrationEventInterceptorManager
import com.only4.core.domain.event.*
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils

/**
 * 事件拦截器管理器实现
 *
 * @author binking338
 * @date 2024/9/12
 */
class DefaultEventInterceptorManager(
    private val eventMessageInterceptors: List<EventMessageInterceptor>,
    private val eventInterceptors: List<EventInterceptor>
) : EventMessageInterceptorManager, DomainEventInterceptorManager, IntegrationEventInterceptorManager {

    override val orderedEventMessageInterceptors: Set<EventMessageInterceptor> by lazy {
        eventMessageInterceptors
            .sortedBy { OrderUtils.getOrder(it.javaClass, Ordered.LOWEST_PRECEDENCE) }
            .toCollection(LinkedHashSet())
    }

    override val orderedEventInterceptors4DomainEvent: Set<EventInterceptor> by lazy {
        eventInterceptors
            .filter {
                DomainEventInterceptor::class.java.isAssignableFrom(it.javaClass) ||
                        !IntegrationEventInterceptor::class.java.isAssignableFrom(it.javaClass)
            }
            .sortedBy { OrderUtils.getOrder(it.javaClass, Ordered.LOWEST_PRECEDENCE) }
            .toCollection(LinkedHashSet())
    }

    override val orderedEventInterceptors4IntegrationEvent: Set<EventInterceptor> by lazy {
        eventInterceptors
            .filter {
                !DomainEventInterceptor::class.java.isAssignableFrom(it.javaClass) ||
                        IntegrationEventInterceptor::class.java.isAssignableFrom(it.javaClass)
            }
            .sortedBy { OrderUtils.getOrder(it.javaClass, Ordered.LOWEST_PRECEDENCE) }
            .toCollection(LinkedHashSet())
    }

    override val orderedDomainEventInterceptors: Set<DomainEventInterceptor> by lazy {
        eventInterceptors
            .filter { DomainEventInterceptor::class.java.isAssignableFrom(it.javaClass) }
            .map { it as DomainEventInterceptor }
            .sortedBy { OrderUtils.getOrder(it.javaClass, Ordered.LOWEST_PRECEDENCE) }
            .toCollection(LinkedHashSet())
    }

    override val orderedIntegrationEventInterceptors: Set<IntegrationEventInterceptor> by lazy {
        eventInterceptors
            .filter { IntegrationEventInterceptor::class.java.isAssignableFrom(it.javaClass) }
            .map { it as IntegrationEventInterceptor }
            .sortedBy { OrderUtils.getOrder(it.javaClass, Ordered.LOWEST_PRECEDENCE) }
            .toCollection(LinkedHashSet())
    }
}
