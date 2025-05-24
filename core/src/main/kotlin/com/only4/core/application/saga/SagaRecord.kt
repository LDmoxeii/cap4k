package com.only4.core.application.saga

import com.only4.core.application.RequestParam
import java.time.Duration
import java.time.LocalDateTime

/**
 * Saga记录
 *
 * @author binking338
 * @date 2024/10/12
 */
interface SagaRecord {
    /**
     * 初始化Saga
     * @param sagaParam
     * @param svcName
     * @param sagaType
     * @param scheduleAt
     * @param expireAfter
     * @param retryTimes
     */
    fun init(
        sagaParam: SagaParam<Any>,
        svcName: String,
        sagaType: String,
        scheduleAt: LocalDateTime,
        expireAfter: Duration,
        retryTimes: Int
    )

    /**
     * 获取Saga ID
     * @return
     */
    val id: String

    /**
     * 获取Saga流程执行参数
     * @return
     */
    val param: SagaParam<*>

    /**
     * 获取Saga流程执行结果
     * @return
     */
    fun <R> getResult(): R

    /**
     * Saga流程子环节开始执行
     *
     * @param now
     * @param processCode
     * @param param
     */
    fun beginSagaProcess(
        now: LocalDateTime,
        processCode: String,
        param: RequestParam<Any>
    )

    /**
     * Saga流程子环节执行完成
     *
     * @param now
     * @param processCode
     * @param result
     */
    fun endSagaProcess(now: LocalDateTime, processCode: String, result: Any)

    /**
     * 获取Saga流程子环节发生异常
     *
     * @param now
     * @param processCode
     * @param throwable
     */
    fun sagaProcessOccuredException(now: LocalDateTime, processCode: String, throwable: Throwable)

    /**
     * 获取Saga流程子环节是否已执行
     *
     * @param processCode
     * @return
     */
    fun isSagaProcessExecuted(processCode: String): Boolean

    /**
     * 获取Saga流程子环节执行结果
     *
     * @param processCode
     * @return
     */
    fun <R> getSagaProcessResult(processCode: String): R

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
     * Saga流程是否有效（初始或执行中等待确认结果）
     * @return
     */
    val isValid: Boolean

    /**
     * Saga流程是否失效（未执行完成）
     * @return
     */
    val isInvalid: Boolean

    /**
     * Saga流程是否正在执行
     * @return
     */
    val isExecuting: Boolean

    /**
     * Saga流程是否已完成
     * @return
     */
    val isExecuted: Boolean

    /**
     * Saga流程开始执行
     * @param now
     * @return
     */
    fun beginSaga(now: LocalDateTime): Boolean

    /**
     * Saga流程取消执行
     * @param now
     * @return
     */
    fun cancelSaga(now: LocalDateTime): Boolean

    /**
     * Saga流程执行完成
     * @param now
     * @param result
     * @return
     */
    fun endSaga(now: LocalDateTime, result: Any)

    /**
     * Saga流程发生异常
     * @param now
     * @param throwable
     * @return
     */
    fun occurredException(now: LocalDateTime, throwable: Throwable)
}
