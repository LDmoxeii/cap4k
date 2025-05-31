package com.only4.core.application

/**
 * 请求拦截器
 *
 * @author binking338
 * @date 2024/9/1
 */
interface RequestInterceptor<RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> {
    /**
     * 请求前
     *
     * @param request
     */
    fun preRequest(request: REQUEST)

    /**
     * 请求后
     *
     * @param request
     * @param response
     */
    fun postRequest(request: REQUEST, response: RESPONSE)
}
