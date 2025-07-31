package com.only4.cap4k.ddd.core.domain.event

import org.springframework.messaging.Message
import java.time.Duration
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
        retryTimes: Int
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

    /**
     * 获取计划发送时间
     * @return
     */
    val scheduleTime: LocalDateTime

    /**
     * 获取下次重试时间
     * @return
     */
    val nextTryTime: LocalDateTime

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

    /**
     * 是否有效（待发送，或发送中等待确认结果）
     * @return
     */
    val isValid: Boolean

    /**
     * 是否失效（过期等原因未发生成功）
     * @return
     */
    val isInvalid: Boolean

    /**
     * 是否已发送
     * @return
     */
    val isDelivered: Boolean

    /**
     * 开始发送事件
     * @param now
     * @return
     */
    fun beginDelivery(now: LocalDateTime): Boolean

    /**
     * 取消发送
     * @param now
     * @return
     */
    fun cancelDelivery(now: LocalDateTime): Boolean

    /**
     * 确认事件已发出
     * @param now
     */
    fun confirmedDelivery(now: LocalDateTime)

    /**
     * 发生异常
     * @param now
     * @param throwable
     * @return
     */
    fun occurredException(now: LocalDateTime, throwable: Throwable)
}
