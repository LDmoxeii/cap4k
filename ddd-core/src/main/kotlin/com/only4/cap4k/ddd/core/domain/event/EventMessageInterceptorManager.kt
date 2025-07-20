package com.only4.cap4k.ddd.core.domain.event

/**
 * 事件消息拦截器管理器
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface EventMessageInterceptorManager {
    /**
     * 拦截器基于 [org.springframework.core.annotation.Order] 排序
     * @return
     */
    val orderedEventMessageInterceptors: Set<EventMessageInterceptor>
}
