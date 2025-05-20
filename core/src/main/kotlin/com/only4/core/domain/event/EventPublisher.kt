package com.only4.core.domain.event

import java.time.LocalDateTime

/**
 * 事件发布接口
 *
 * @author binking338
 * @date 2023/8/5
 */
interface EventPublisher {
    /**
     * 发布事件
     *
     * @param event
     */
    fun publish(event: EventRecord)

    /**
     * 重试事件
     *
     * @param event
     * @param minNextTryTime
     */
    fun retry(event: EventRecord, minNextTryTime: LocalDateTime)
}
