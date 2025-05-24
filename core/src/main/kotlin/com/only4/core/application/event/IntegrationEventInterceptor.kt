package com.only4.core.application.event

import com.only4.core.domain.event.EventInterceptor
import java.time.LocalDateTime

/**
 * 集成事件拦截器
 *
 * @author binking338
 * @date 2024/8/29
 */
interface IntegrationEventInterceptor : EventInterceptor {
    /**
     * 附加
     * @param eventPayload
     * @param schedule
     */
    fun onAttach(eventPayload: Any, schedule: LocalDateTime)

    /**
     * 解除附加
     * @param eventPayload
     */
    fun onDetach(eventPayload: Any)
}
