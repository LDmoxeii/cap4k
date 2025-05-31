package com.only4.core.domain.event

/**
 * 事件消息拦截器管理器
 *
 * @author binking338
 * @date 2024/9/12
 */
interface EventMessageInterceptorManager {
    /**
     * 拦截器基于 [org.springframework.core.annotation.Order] 排序
     * @return
     */
    val orderedEventMessageInterceptors: Set<EventMessageInterceptor>
}
