package com.only4.cap4k.ddd.core.application.event

import com.only4.cap4k.ddd.core.domain.event.EventInterceptor
import java.time.LocalDateTime

/**
 * 集成事件拦截器接口
 * 用于在集成事件的生命周期中进行拦截和处理
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface IntegrationEventInterceptor : EventInterceptor {
    /**
     * 事件附加时的回调
     * 当事件被附加到事务时调用
     *
     * @param eventPayload 事件负载
     * @param schedule 计划执行时间
     */
    fun onAttach(eventPayload: Any, schedule: LocalDateTime)

    /**
     * 事件解除附加时的回调
     * 当事件从事务中解除附加时调用
     *
     * @param eventPayload 事件负载
     */
    fun onDetach(eventPayload: Any)
}
