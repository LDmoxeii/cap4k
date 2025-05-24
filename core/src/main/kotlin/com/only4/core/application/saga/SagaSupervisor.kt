package com.only4.core.application.saga

import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration

/**
 * Saga控制器
 *
 * @author binking338
 * @date 2024/10/12
 */
interface SagaSupervisor {
    /**
     * 执行Saga流程
     *
     * @param request   请求参数
     * @param <REQUEST> 请求参数类型
    </REQUEST> */
    fun <RESPONSE, REQUEST : SagaParam<RESPONSE>> send(request: REQUEST): RESPONSE

    /**
     * 异步执行Saga流程
     *
     * @param request
     * @param <REQUEST>
     * @param <RESPONSE> 响应参数类型
     * @return Saga ID
    </RESPONSE></REQUEST> */
    fun <RESPONSE, REQUEST : SagaParam<RESPONSE>> async(request: REQUEST): String {
        return schedule(request, LocalDateTime.now())
    }

    /**
     * 延迟执行请求
     *
     * @param request    请求参数
     * @param schedule   计划时间
     * @param <REQUEST>  请求参数类型
     * @param <RESPONSE> 响应参数类型
     * @return 请求ID
    </RESPONSE></REQUEST> */
    fun <RESPONSE, REQUEST : SagaParam<RESPONSE>> schedule(
        request: REQUEST,
        schedule: LocalDateTime,
        delay: Duration = Duration.ZERO
    ): String

    /**
     * 获取Saga结果
     *
     * @param id  Saga ID
     * @param <R>
     * @return 请求结果
    </R> */
    fun <R> result(id: String): R

    /**
     * 获取Saga结果
     *
     * @param requestId    请求ID
     * @param requestClass 请求参数类型
     * @param <REQUEST>    请求参数类型
     * @param <RESPONSE>   响应参数类型
     * @return 请求结果
    </RESPONSE></REQUEST> */
    @Suppress("UNCHECKED_CAST")
    fun <RESPONSE, REQUEST : SagaParam<RESPONSE>> result(
        requestId: String,
        requestClass: Class<REQUEST> = Any::class.java as Class<REQUEST>
    ): Optional<RESPONSE>

    companion object {
        val instance: SagaSupervisor
            /**
             * 获取请求管理器
             *
             * @return 请求管理器
             */
            get() = SagaSupervisorSupport.instance
    }
}
