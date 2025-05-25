package com.only4.core.application

/**
 * 请求接口
 *
 * @param <REQUEST>  请求参数
 * @param <RESPONSE> 返回结果
 * @author binking338
 * @date 2024/8/24
</RESPONSE></REQUEST> */
interface RequestHandler<out RESPONSE : Any, in REQUEST : RequestParam<@UnsafeVariance RESPONSE>> {
    /**
     * 执行请求
     *
     * @param request 请求参数
     * @return 返回结果
     */
    fun exec(request: REQUEST): RESPONSE
}
