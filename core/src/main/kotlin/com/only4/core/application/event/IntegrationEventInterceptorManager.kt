package com.only4.core.application.event

import com.only4.core.domain.event.EventInterceptor

/**
 * 集成事件拦截器管理器接口
 * 负责管理和提供集成事件相关的拦截器，支持拦截器的有序获取
 *
 * @author binking338
 * @date 2024/9/12
 */
interface IntegrationEventInterceptorManager {
    /**
     * 获取有序的集成事件拦截器集合
     * 拦截器按照org.springframework.core.annotation.Order注解的优先级排序
     * 优先级值越小，拦截器执行顺序越靠前
     *
     * @return 有序的集成事件拦截器集合
     */
    val orderedIntegrationEventInterceptors: Set<IntegrationEventInterceptor>

    /**
     * 获取有序的事件拦截器集合（用于集成事件）
     * 拦截器按照org.springframework.core.annotation.Order注解的优先级排序
     * 优先级值越小，拦截器执行顺序越靠前
     *
     * @return 有序的事件拦截器集合
     */
    val orderedEventInterceptors4IntegrationEvent: Set<EventInterceptor>
}
