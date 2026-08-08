package com.only4.cap4k.ddd.core.domain.event

import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import com.only4.cap4k.ddd.core.share.ReliableFailureFacts
import org.springframework.messaging.Message
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/**
 * 事件记录
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface EventRecord {
    /**
     * 初始化事件
     * @param payload
     * @param svcName
     * @param scheduleAt
     * @param expireAfter
     * @param retryTimes
     */
    fun init(
        payload: Any,
        svcName: String,
        scheduleAt: LocalDateTime,
        expireAfter: Duration,
        retryTimes: Int,
        executionContext: Collection<EncodedExecutionContextElement> = emptyList(),
    )

    /**
     * 获取事件ID
     * @return
     */
    val id: String

    /**
     * 获取事件类型
     * @return
     */
    val type: String

    /**
     * 获取事件消息体
     * @return
     */
    val payload: Any

    /** Safe structured facts for the latest failed reliable delivery attempt. */
    val failure: ReliableFailureFacts?

    /** Encoded origin attribution retained unchanged by the durable record. */
    val executionContext: List<EncodedExecutionContextElement>

    /** Immutable instant at which this reliable event was first registered for publication. */
    val publishedAt: Instant

    /** Exact positive delivery attempt owned by this claimed reliable record. */
    val deliveryAttempt: Int?

    /**
     * 获取计划发送时间
     * @return
     */
    val scheduleTime: LocalDateTime

    /**
     * 标记是否持久化
     * @param persist
     */
    fun markPersist(persist: Boolean)

    /**
     * 是否持久化
     * @return
     */
    val isPersist: Boolean

    /**
     * 创建消息
     * @return
     */
    val message: Message<Any>

}
