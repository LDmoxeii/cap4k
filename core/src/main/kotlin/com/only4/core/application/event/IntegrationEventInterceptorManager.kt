package com.only4.core.application.event

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
    val orderedIntegrationEventInterceptors: Set<Any>

    /**
     *
     * 拦截器基于 [org.springframework.core.annotation.Order] 排序
     * @return
     */
    val orderedEventInterceptors4IntegrationEvent: Set<Any>
}
