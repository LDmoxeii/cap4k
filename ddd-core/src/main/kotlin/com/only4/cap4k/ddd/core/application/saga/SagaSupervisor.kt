package com.only4.cap4k.ddd.core.application.saga

import java.time.Duration
import java.time.LocalDateTime

/**
 * Saga控制器
 *
 * @author LD_moxeii
 * @date 2025/07/26
 */
interface SagaSupervisor {

    companion object {
        /**
         * 获取请求管理器
         *
         * @return 请求管理器实例
         */
        val instance: SagaSupervisor
            get() = SagaSupervisorSupport.instance
    }

    /**
     * 执行Saga流程
     *
     * @param request 请求参数
     * @return 处理结果
     */
    fun <REQUEST : SagaParam<RESPONSE>, RESPONSE> send(request: REQUEST): RESPONSE

    /**
     * 异步执行Saga流程
     *
     * @param request 请求参数
     * @return Saga ID
     */
    fun <REQUEST : SagaParam<RESPONSE>, RESPONSE> async(request: REQUEST): String {
        return schedule(request, LocalDateTime.now())
    }

    /**
     * 延迟执行请求
     *
     * @param request    请求参数
     * @param schedule   计划时间
     * @return 请求ID
     */
    fun <REQUEST : SagaParam<RESPONSE>, RESPONSE> schedule(
        request: REQUEST,
        schedule: LocalDateTime
    ): String

    /**
     * 延迟执行请求
     *
     * @param request 请求参数
     * @param delay   延迟时间
     * @return 请求ID
     */
    fun <REQUEST : SagaParam<RESPONSE>, RESPONSE> delay(request: REQUEST, delay: Duration): String {
        return schedule(request, LocalDateTime.now().plus(delay))
    }

    /**
     * 获取Saga结果
     *
     * @param id Saga ID
     * @return 请求结果
     */
    fun <R> result(id: String): R?

    /**
     * 获取Saga结果
     *
     * @param requestId    请求ID
     * @param requestClass 请求参数类型
     * @return 请求结果
     */
    fun <REQUEST : SagaParam<RESPONSE>, RESPONSE> result(
        requestId: String,
        requestClass: Class<REQUEST>
    ): RESPONSE? {
        val r = result<Any>(requestId)

        @Suppress("UNCHECKED_CAST")
        val response = r as? RESPONSE
        if (r != null && response == null) {
            throw IllegalArgumentException("request response type mismatch")
        }
        return response
    }
}
