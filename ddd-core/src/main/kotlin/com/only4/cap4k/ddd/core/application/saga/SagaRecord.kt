package com.only4.cap4k.ddd.core.application.saga

import com.only4.cap4k.ddd.core.application.RequestParam
import java.time.Duration
import java.time.LocalDateTime

/**
 * Saga记录
 *
 * @author LD_moxeii
 * @date 2025/07/26
 */
interface SagaRecord {

    /**
     * 初始化Saga
     *
     * @param sagaParam Saga参数
     * @param svcName 服务名称
     * @param sagaType Saga类型
     * @param scheduleAt 计划执行时间
     * @param expireAfter 过期时长
     * @param retryTimes 重试次数
     */
    fun init(
        sagaParam: SagaParam<*>,
        svcName: String,
        sagaType: String,
        scheduleAt: LocalDateTime,
        expireAfter: Duration,
        retryTimes: Int
    )

    /**
     * 获取Saga ID
     *
     * @return Saga ID
     */
    val id: String

    /**
     * 获取Saga类型
     * @return
     */
    val type: String

    /**
     * 获取Saga流程执行参数
     *
     * @return Saga参数
     */
    val param: SagaParam<*>

    /**
     * 获取Saga流程执行结果
     *
     * @return 执行结果
     */
    fun <R : Any> getResult(): R?

    /**
     * Saga流程子环节开始执行
     *
     * @param now 当前时间
     * @param processCode 子流程代码
     * @param param 参数
     */
    fun beginSagaProcess(now: LocalDateTime, processCode: String, param: RequestParam<*>)

    /**
     * Saga流程子环节执行完成
     *
     * @param now 当前时间
     * @param processCode 子流程代码
     * @param result 执行结果
     */
    fun endSagaProcess(now: LocalDateTime, processCode: String, result: Any)

    /**
     * Saga流程子环节发生异常
     *
     * @param now 当前时间
     * @param processCode 子流程代码
     * @param throwable 异常信息
     */
    fun sagaProcessOccurredException(now: LocalDateTime, processCode: String, throwable: Throwable)

    /**
     * 获取Saga流程子环节是否已执行
     *
     * @param processCode 子流程代码
     * @return 是否已执行
     */
    fun isSagaProcessExecuted(processCode: String): Boolean

    /**
     * 获取Saga流程子环节执行结果
     *
     * @param processCode 子流程代码
     * @return 执行结果
     */
    fun <R : Any> getSagaProcessResult(processCode: String): R?

    /**
     * 获取计划执行时间
     *
     * @return 计划执行时间
     */
    val scheduleTime: LocalDateTime

    /**
     * 获取下次重试时间
     *
     * @return 下次重试时间
     */
    val nextTryTime: LocalDateTime

    /**
     * Saga流程是否有效（初始或执行中等待确认结果）
     *
     * @return 是否有效
     */
    val isValid: Boolean

    /**
     * Saga流程是否失效（未执行完成）
     *
     * @return 是否失效
     */
    val isInvalid: Boolean

    /**
     * Saga流程是否正在执行
     *
     * @return 是否正在执行
     */
    val isExecuting: Boolean

    /**
     * Saga流程是否已完成
     *
     * @return 是否已完成
     */
    val isExecuted: Boolean

    /**
     * Saga流程开始执行
     *
     * @param now 当前时间
     * @return 是否成功开始
     */
    fun beginSaga(now: LocalDateTime): Boolean

    /**
     * Saga流程取消执行
     *
     * @param now 当前时间
     * @return 是否成功取消
     */
    fun cancelSaga(now: LocalDateTime): Boolean

    /**
     * Saga流程执行完成
     *
     * @param now 当前时间
     * @param result 执行结果
     */
    fun endSaga(now: LocalDateTime, result: Any)

    /**
     * Saga流程发生异常
     *
     * @param now 当前时间
     * @param throwable 异常信息
     */
    fun occurredException(now: LocalDateTime, throwable: Throwable)
}
