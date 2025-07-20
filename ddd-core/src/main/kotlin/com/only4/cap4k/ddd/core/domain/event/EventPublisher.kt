package com.only4.cap4k.ddd.core.domain.event

import java.time.LocalDateTime

/**
 * 事件发布接口
 *
 * @author LD_moxeii
 * @date 2025/07/20
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
