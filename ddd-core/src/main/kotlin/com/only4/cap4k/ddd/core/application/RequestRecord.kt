package com.only4.cap4k.ddd.core.application

import java.time.Duration
import java.time.LocalDateTime

/**
 * 请求记录接口
 * 用于记录和管理请求的完整生命周期
 * 包括初始化、执行、完成和异常处理等状态
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface RequestRecord {
    /**
     * 初始化请求记录
     * 设置请求的基本信息和执行参数
     *
     * @param requestParam 请求参数
     * @param svcName 服务名称
     * @param requestType 请求类型
     * @param scheduleAt 计划执行时间
     * @param expireAfter 过期时间
     * @param retryTimes 重试次数
     * @throws IllegalStateException 当记录已经初始化时
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
     * 请求记录的唯一标识
     */
    val id: String

    /**
     * 获取Request类型
     *
     * @return
     */
    val type: String

    /**
     * 请求参数
     * 包含请求的详细信息和执行参数
     */
    val param: RequestParam<*>

    /**
     * 获取请求执行结果
     * 返回请求处理的结果数据
     *
     * @return 请求处理结果
     * @throws IllegalStateException 当请求未完成或执行失败时
     */
    fun <R : Any> getResult(): R

    /**
     * 计划执行时间
     * 请求的预期执行时间点
     */
    val scheduleTime: LocalDateTime

    /**
     * 下次重试时间
     * 当请求执行失败时，下次重试的时间点
     */
    val nextTryTime: LocalDateTime

    /**
     * 请求是否有效
     * 表示请求处于初始状态或执行中等待确认结果
     */
    val isValid: Boolean

    /**
     * 请求是否失效
     * 表示请求未执行完成且无法继续执行
     */
    val isInvalid: Boolean

    /**
     * 请求是否正在执行
     * 表示请求当前正在处理中
     */
    val isExecuting: Boolean

    /**
     * 请求是否已完成
     * 表示请求已经成功执行完成
     */
    val isExecuted: Boolean

    /**
     * 开始执行请求
     * 将请求状态变更为执行中
     *
     * @param now 当前时间
     * @return 是否成功开始执行
     * @throws IllegalStateException 当请求状态不允许开始执行时
     */
    fun beginRequest(now: LocalDateTime): Boolean

    /**
     * 取消请求执行
     * 将请求状态变更为已取消
     *
     * @param now 当前时间
     * @return 是否成功取消执行
     * @throws IllegalStateException 当请求状态不允许取消时
     */
    fun cancelRequest(now: LocalDateTime): Boolean

    /**
     * 完成请求执行
     * 记录请求执行结果并更新状态
     *
     * @param now 当前时间
     * @param result 执行结果
     * @throws IllegalStateException 当请求状态不允许完成时
     */
    fun endRequest(now: LocalDateTime, result: Any)

    /**
     * 处理请求执行异常
     * 记录异常信息并更新请求状态
     *
     * @param now 当前时间
     * @param throwable 异常信息
     * @throws IllegalStateException 当请求状态不允许处理异常时
     */
    fun occurredException(now: LocalDateTime, throwable: Throwable)
}
