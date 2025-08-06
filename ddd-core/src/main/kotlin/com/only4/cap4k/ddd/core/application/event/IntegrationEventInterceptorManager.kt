package com.only4.cap4k.ddd.core.application.event

import com.only4.cap4k.ddd.core.domain.event.EventInterceptor

/**
 * 集成事件拦截器管理器接口
 * 负责管理和提供集成事件相关的拦截器，支持拦截器的有序获取
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface IntegrationEventInterceptorManager {
    /**
     * 获取有序的集成事件拦截器集合
     * 拦截器按照 [org.springframework.core.annotation.Order] 注解的优先级排序
     * 优先级值越小，拦截器执行顺序越靠前
     *
     * @return 有序的集成事件拦截器集合
     */
    val orderedIntegrationEventInterceptors: Set<IntegrationEventInterceptor>

    /**
     * 获取有序的事件拦截器集合（用于集成事件）
     * 拦截器按照 [org.springframework.core.annotation.Order] 注解的优先级排序
     * 优先级值越小，拦截器执行顺序越靠前
     *
     * @return 有序的事件拦截器集合
     */
    val orderedEventInterceptors4IntegrationEvent: Set<EventInterceptor>
}
