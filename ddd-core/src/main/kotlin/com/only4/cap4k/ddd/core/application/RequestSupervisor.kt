package com.only4.cap4k.ddd.core.application

import jakarta.validation.ConstraintViolationException
import java.time.Duration
import java.time.LocalDateTime

/**
 * 请求监督者接口
 * 负责管理和执行请求，支持同步、异步和延迟执行
 * 提供请求结果查询和状态管理功能
 *
 * @author binking338
 * @date 2024/8/24
 */
interface RequestSupervisor {
    /**
     * 同步执行请求
     * 立即执行请求并返回结果
     *
     * @param request 请求参数
     * @return 请求执行结果
     * @throws ConstraintViolationException 当请求参数验证失败时
     * @throws IllegalStateException 当请求执行失败时
     */
    fun <REQUEST : RequestParam<RESPONSE>, RESPONSE> send(request: REQUEST): RESPONSE

    /**
     * 异步执行请求
     * 立即将请求加入执行队列
     *
     * @param request 请求参数
     * @return 请求ID，用于后续查询结果
     * @throws ConstraintViolationException 当请求参数验证失败时
     */
    fun <REQUEST : RequestParam<RESPONSE>, RESPONSE> async(request: REQUEST): String =
        schedule(request, LocalDateTime.now())

    /**
     * 定时执行请求
     * 在指定时间执行请求
     *
     * @param request 请求参数
     * @param schedule 计划执行时间
     * @return 请求ID，用于后续查询结果
     * @throws ConstraintViolationException 当请求参数验证失败时
     */
    fun <REQUEST : RequestParam<RESPONSE>, RESPONSE> schedule(
        request: REQUEST,
        schedule: LocalDateTime
    ): String

    /**
     * 延迟执行请求
     * 在指定延迟时间后执行请求
     *
     * @param request 请求参数
     * @param delay 延迟时间
     * @return 请求ID，用于后续查询结果
     * @throws ConstraintViolationException 当请求参数验证失败时
     */
    fun <REQUEST : RequestParam<RESPONSE>, RESPONSE> delay(
        request: REQUEST,
        delay: Duration
    ): String = schedule(request, LocalDateTime.now().plus(delay))

    /**
     * 获取请求执行结果
     *
     * @param requestId 请求ID
     * @return 请求执行结果，如果请求未完成则返回空
     */
    fun <REQUEST : RequestParam<RESPONSE>, RESPONSE> result(requestId: String): RESPONSE

    companion object {
        /**
         * 获取请求监督者实例
         * 通过RequestSupervisorSupport获取全局唯一的请求监督者实例
         *
         * @return 请求监督者实例
         */
        val instance: RequestSupervisor
            get() = RequestSupervisorSupport.instance
    }
}
