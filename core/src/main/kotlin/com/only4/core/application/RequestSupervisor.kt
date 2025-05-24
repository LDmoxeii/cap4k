package com.only4.core.application

import java.time.Duration
import java.time.LocalDateTime
import java.util.*

/**
 * 请求管理器
 *
 * @author binking338
 * @date 2024/8/24
 */
interface RequestSupervisor {
    /**
     * 执行请求
     *
     * @param request    请求参数
     * @param <REQUEST>  请求参数类型
     * @param <RESPONSE> 响应参数类型
    </RESPONSE></REQUEST> */
    fun <RESPONSE, REQUEST : RequestParam<RESPONSE>> send(request: REQUEST): RESPONSE

    /**
     * 异步执行请求
     *
     * @param request    请求参数
     * @param <REQUEST>  请求参数类型
     * @param <RESPONSE> 响应参数类型
     * @return 请求ID
    </RESPONSE></REQUEST> */
    fun <RESPONSE, REQUEST : RequestParam<RESPONSE>> async(request: REQUEST): String {
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
    fun <RESPONSE, REQUEST : RequestParam<RESPONSE>> schedule(
        request: REQUEST,
        schedule: LocalDateTime
    ): String

    /**
     * 延迟执行请求
     *
     * @param request    请求参数
     * @param delay      延迟时间
     * @param <REQUEST>  请求参数类型
     * @param <RESPONSE> 响应参数类型
     * @return 请求ID
    </RESPONSE></REQUEST> */
    fun <RESPONSE, REQUEST : RequestParam<RESPONSE>> delay(
        request: REQUEST,
        delay: Duration
    ): String {
        return schedule(request, LocalDateTime.now().plus(delay))
    }

    /**
     * 获取请求结果
     *
     * @param requestId    请求ID
     * @param requestClass 请求参数类型
     * @param <REQUEST>    请求参数类型
     * @param <RESPONSE>   响应参数类型
     * @return 请求结果
    </RESPONSE></REQUEST> */
    @Suppress("UNCHECKED_CAST")
    fun <RESPONSE, REQUEST : RequestParam<RESPONSE>> result(
        requestId: String,
        requestClass: Class<REQUEST> = Any::class.java as Class<REQUEST>
    ): Optional<RESPONSE>

    companion object {
        val instance: RequestSupervisor
            /**
             * 获取请求管理器
             *
             * @return 请求管理器
             */
            get() = RequestSupervisorSupport.instance
    }
}
