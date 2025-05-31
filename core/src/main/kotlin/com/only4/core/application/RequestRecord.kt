package com.only4.core.application

import java.time.Duration
import java.time.LocalDateTime

/**
 * Request记录
 *
 * @author binking338
 * @date 2025/5/15
 */
interface RequestRecord {
    /**
     * 初始化Request
     *
     * @param requestParam
     * @param svcName
     * @param requestType
     * @param scheduleAt
     * @param expireAfter
     * @param retryTimes
     */
    fun init(
        requestParam: RequestParam<*>,
        svcName: String,
        requestType: String,
        scheduleAt: LocalDateTime,
        expireAfter: Duration,
        retryTimes: Int
    )

    /**
     * 获取Request ID
     *
     * @return
     */
    val id: String

    /**
     * 获取Request流程执行参数
     *
     * @return
     */
    val param: RequestParam<*>

    /**
     * 获取Request流程执行结果
     *
     * @return
     */
    fun <R : Any> getResult(): R

    /**
     * 获取计划执行时间
     * @return
     */
    val scheduleTime: LocalDateTime

    /**
     * 获取下次重试时间
     * @return
     */
    val nextTryTime: LocalDateTime

    /**
     * Request流程是否有效（初始或执行中等待确认结果）
     * @return
     */
    val isValid: Boolean

    /**
     * Request流程是否失效（未执行完成）
     * @return
     */
    val isInvalid: Boolean

    /**
     * Request流程是否正在执行
     * @return
     */
    val isExecuting: Boolean

    /**
     * Request流程是否已完成
     * @return
     */
    val isExecuted: Boolean

    /**
     * Request流程开始执行
     * @param now
     * @return
     */
    fun beginRequest(now: LocalDateTime): Boolean

    /**
     * Request流程取消执行
     * @param now
     * @return
     */
    fun cancelRequest(now: LocalDateTime): Boolean

    /**
     * Request流程执行完成
     * @param now
     * @param result
     * @return
     */
    fun endRequest(now: LocalDateTime, result: Any)

    /**
     * Request流程发生异常
     * @param now
     * @param throwable
     * @return
     */
    fun occurredException(now: LocalDateTime, throwable: Throwable)
}
