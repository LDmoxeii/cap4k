package com.only4.core.application.saga

import com.only4.core.application.RequestParam
import java.time.Duration
import java.time.LocalDateTime

/**
 * Saga记录接口
 * 用于记录和管理Saga事务流程的执行状态和结果
 * 提供完整的Saga流程生命周期管理功能
 *
 * @author binking338
 * @date 2024/10/12
 */
interface SagaRecord {
    /**
     * 初始化Saga记录
     * 设置Saga流程的基本信息和执行参数
     *
     * @param sagaParam Saga流程参数
     * @param svcName 服务名称
     * @param sagaType Saga类型
     * @param scheduleAt 计划执行时间
     * @param expireAfter 过期时间
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
     * Saga记录的唯一标识
     */
    val id: String

    /**
     * Saga流程的执行参数
     */
    val param: SagaParam<*>

    /**
     * 获取Saga流程执行结果
     *
     * @return 执行结果
     */
    fun <R> getResult(): R

    /**
     * Saga流程子环节开始执行
     * 记录子环节开始执行的时间和参数
     *
     * @param now 当前时间
     * @param processCode 子环节代码
     * @param param 子环节参数
     */
    fun beginSagaProcess(
        now: LocalDateTime,
        processCode: String,
        param: RequestParam<*>
    )

    /**
     * Saga流程子环节执行完成
     * 记录子环节执行完成的时间和结果
     *
     * @param now 当前时间
     * @param processCode 子环节代码
     * @param result 执行结果
     */
    fun endSagaProcess(now: LocalDateTime, processCode: String, result: Any)

    /**
     * 记录Saga流程子环节执行异常
     * 保存子环节执行过程中发生的异常信息
     *
     * @param now 当前时间
     * @param processCode 子环节代码
     * @param throwable 异常信息
     */
    fun sagaProcessOccurredException(now: LocalDateTime, processCode: String, throwable: Throwable)

    /**
     * 检查Saga流程子环节是否已执行
     *
     * @param processCode 子环节代码
     * @return 是否已执行
     */
    fun isSagaProcessExecuted(processCode: String): Boolean

    /**
     * 获取Saga流程子环节执行结果
     *
     * @param processCode 子环节代码
     * @return 执行结果
     */
    fun <R> getSagaProcessResult(processCode: String): R

    /**
     * 计划执行时间
     */
    val scheduleTime: LocalDateTime

    /**
     * 下次重试时间
     */
    val nextTryTime: LocalDateTime

    /**
     * 检查Saga流程是否有效
     * 有效状态包括：初始状态或执行中等待确认结果
     */
    val isValid: Boolean

    /**
     * 检查Saga流程是否失效
     * 失效状态表示流程未执行完成
     */
    val isInvalid: Boolean

    /**
     * 检查Saga流程是否正在执行
     */
    val isExecuting: Boolean

    /**
     * 检查Saga流程是否已完成
     */
    val isExecuted: Boolean

    /**
     * 开始执行Saga流程
     * 记录流程开始执行的时间
     *
     * @param now 当前时间
     * @return 是否成功开始执行
     */
    fun beginSaga(now: LocalDateTime): Boolean

    /**
     * 取消执行Saga流程
     * 记录流程取消执行的时间
     *
     * @param now 当前时间
     * @return 是否成功取消执行
     */
    fun cancelSaga(now: LocalDateTime): Boolean

    /**
     * 完成Saga流程执行
     * 记录流程执行完成的时间和结果
     *
     * @param now 当前时间
     * @param result 执行结果
     */
    fun endSaga(now: LocalDateTime, result: Any)

    /**
     * 记录Saga流程执行异常
     * 保存流程执行过程中发生的异常信息
     *
     * @param now 当前时间
     * @param throwable 异常信息
     */
    fun occurredException(now: LocalDateTime, throwable: Throwable)
}
