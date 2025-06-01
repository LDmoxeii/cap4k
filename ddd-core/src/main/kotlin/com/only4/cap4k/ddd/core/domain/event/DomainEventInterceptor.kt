package com.only4.cap4k.ddd.core.domain.event

import java.time.LocalDateTime

/**
 * 领域事件拦截器
 *
 * @author binking338
 * @date 2024/8/27
 */
interface DomainEventInterceptor : EventInterceptor {
    /**
     * 附加
     * @param eventPayload
     * @param entity
     * @param schedule
     */
    fun onAttach(eventPayload: Any, entity: Any, schedule: LocalDateTime)

    /**
     * 解除附加
     * @param eventPayload
     * @param entity
     */
    fun onDetach(eventPayload: Any, entity: Any)
}
