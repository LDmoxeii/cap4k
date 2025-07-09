package com.only4.cap4k.ddd.core.application.saga

import jakarta.validation.ConstraintViolationException
import java.time.Duration
import java.time.LocalDateTime

/**
 * Saga事务流程控制器接口
 * 负责管理和控制Saga事务流程的执行
 * 提供同步执行、异步执行和延迟执行等核心功能
 *
 * @author binking338
 * @date 2024/10/12
 */
interface SagaSupervisor {
    /**
     * 同步执行Saga事务流程
     * 立即执行并等待结果返回
     *
     * @param request 请求参数
     * @return 执行结果
     * @throws ConstraintViolationException 当请求参数验证失败时
     */
    fun <RESPONSE, REQUEST : SagaParam<RESPONSE>> send(request: REQUEST): RESPONSE

    /**
     * 异步执行Saga事务流程
     * 立即调度执行，不等待结果
     *
     * @param request 请求参数
     * @return Saga事务ID
     * @throws ConstraintViolationException 当请求参数验证失败时
     */
    fun <RESPONSE, REQUEST : SagaParam<RESPONSE>> async(request: REQUEST): String =
        schedule(request, LocalDateTime.now())

    /**
     * 延迟执行Saga事务流程
     * 在指定时间执行事务流程
     *
     * @param request 请求参数
     * @param schedule 计划执行时间
     * @return Saga事务ID
     * @throws ConstraintViolationException 当请求参数验证失败时
     */
    fun <RESPONSE, REQUEST : SagaParam<RESPONSE>> schedule(
        request: REQUEST,
        schedule: LocalDateTime,
    ): String

    fun <RESPONSE, REQUEST : SagaParam<RESPONSE>> schedule(
        request: REQUEST,
        delay: Duration,
    ): String = schedule(request, LocalDateTime.now().plus(delay))

    /**
     * 获取Saga事务执行结果
     *
     * @param id Saga事务ID
     * @return 执行结果，如果不存在则返回null
     */
    fun <R> result(id: String): R?

    /**
     * 获取Saga事务执行结果
     * 支持指定请求类型的结果获取
     *
     * @param requestId Saga事务ID
     * @param requestClass 请求参数类型
     * @return 执行结果，如果不存在则返回null
     * @throws IllegalArgumentException 当结果类型不匹配时
     */
    @Suppress("UNCHECKED_CAST")
    fun <RESPONSE, REQUEST : SagaParam<RESPONSE>> result(
        requestId: String,
        requestClass: Class<REQUEST> = Any::class.java as Class<REQUEST>
    ): RESPONSE {
        val r = result<Any>(requestId)
        val response = r as RESPONSE?
        if (r != null && response == null) {
            throw IllegalArgumentException("request response type mismatch")
        }
        return response!!
    }

    companion object {
        /**
         * 获取Saga事务流程控制器实例
         *
         * @return Saga事务流程控制器实例
         */
        val instance: SagaSupervisor
            get() = SagaSupervisorSupport.instance
    }
}
