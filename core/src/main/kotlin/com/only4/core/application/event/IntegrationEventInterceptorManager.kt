package com.only4.core.application.event

import com.only4.core.domain.event.EventInterceptor


/**
 * 集成事件拦截器管理器
 *
 * @author binking338
 * @date 2024/9/12
 */
interface IntegrationEventInterceptorManager {
    /**
     * 拦截器基于 [org.springframework.core.annotation.Order] 排序
     * @return
     */
    val orderedIntegrationEventInterceptors: Set<IntegrationEventInterceptor>

    /**
     *
     * 拦截器基于 [org.springframework.core.annotation.Order] 排序
     * @return
     */
    val orderedEventInterceptors4IntegrationEvent: Set<EventInterceptor>
}
