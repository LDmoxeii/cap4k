package com.only4.cap4k.ddd.core.impl

import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.*
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils

/**
 * 默认事件拦截器管理器实现
 *
 * 使用延迟初始化和缓存机制管理各种事件拦截器的有序集合
 *
 * @param eventMessageInterceptors 事件消息拦截器列表
 * @param eventInterceptors 事件拦截器列表
 *
 * @author LD_moxeii
 * @date 2025/07/22
 */
class DefaultEventInterceptorManager(
    private val eventMessageInterceptors: List<EventMessageInterceptor>,
    private val eventInterceptors: List<EventInterceptor>
) : EventMessageInterceptorManager, DomainEventInterceptorManager, IntegrationEventInterceptorManager {

    // 使用延迟委托进行线程安全的单例初始化
    override val orderedEventMessageInterceptors: Set<EventMessageInterceptor> by lazy {
        eventMessageInterceptors
            .sortedBy { OrderUtils.getOrder(it::class.java, Ordered.LOWEST_PRECEDENCE) }
            .toLinkedSet()
    }

    override val orderedDomainEventInterceptors: Set<DomainEventInterceptor> by lazy {
        eventInterceptors
            .filterIsInstance<DomainEventInterceptor>()
            .sortedBy { OrderUtils.getOrder(it::class.java, Ordered.LOWEST_PRECEDENCE) }
            .toLinkedSet()
    }

    override val orderedEventInterceptors4DomainEvent: Set<EventInterceptor> by lazy {
        eventInterceptors
            .filter { it is DomainEventInterceptor || it !is IntegrationEventInterceptor }
            .sortedBy { OrderUtils.getOrder(it::class.java, Ordered.LOWEST_PRECEDENCE) }
            .toLinkedSet()
    }

    override val orderedIntegrationEventInterceptors: Set<IntegrationEventInterceptor> by lazy {
        eventInterceptors
            .filterIsInstance<IntegrationEventInterceptor>()
            .sortedBy { OrderUtils.getOrder(it::class.java, Ordered.LOWEST_PRECEDENCE) }
            .toLinkedSet()
    }

    override val orderedEventInterceptors4IntegrationEvent: Set<EventInterceptor> by lazy {
        eventInterceptors
            .filter { it !is DomainEventInterceptor || it is IntegrationEventInterceptor }
            .sortedBy { OrderUtils.getOrder(it::class.java, Ordered.LOWEST_PRECEDENCE) }
            .toLinkedSet()
    }

    // 扩展函数：将列表转换为LinkedHashSet以保持顺序
    private fun <T> List<T>.toLinkedSet(): Set<T> = toCollection(LinkedHashSet())
}
