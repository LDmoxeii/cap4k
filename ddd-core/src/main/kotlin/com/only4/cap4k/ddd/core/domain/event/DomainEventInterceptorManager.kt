package com.only4.cap4k.ddd.core.domain.event

/**
 * 领域事件拦截器管理器
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface DomainEventInterceptorManager {
    /**
     * 拦截器基于 [org.springframework.core.annotation.Order] 排序
     * @return
     */
    val orderedDomainEventInterceptors: Set<DomainEventInterceptor>

    /**
     *
     * 拦截器基于 [org.springframework.core.annotation.Order] 排序
     * @return
     */
    val orderedEventInterceptors4DomainEvent: Set<EventInterceptor>
}
