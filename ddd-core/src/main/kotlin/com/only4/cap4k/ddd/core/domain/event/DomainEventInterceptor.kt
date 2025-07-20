package com.only4.cap4k.ddd.core.domain.event

import java.time.LocalDateTime

/**
 * 领域事件拦截器
 *
 * @author LD_moxeii
 * @date 2025/07/20
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
