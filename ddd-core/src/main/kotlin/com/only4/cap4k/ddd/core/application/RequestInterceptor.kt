package com.only4.cap4k.ddd.core.application

/**
 * 请求拦截器接口
 * 用于在请求处理前后进行拦截和处理
 * 支持请求参数验证、日志记录、性能监控等功能
 *
 * @param RESPONSE 响应结果类型，必须是具体类型
 * @param REQUEST 请求参数类型，必须继承自RequestParam
 * @author binking338
 * @date 2024/9/1
 */
interface RequestInterceptor<RESPONSE, REQUEST : RequestParam<RESPONSE>> {
    /**
     * 请求处理前的拦截方法
     * 在请求执行前调用，可用于参数验证、权限检查等
     *
     * @param request 请求参数
     * @throws Exception 当拦截处理过程中发生异常时
     */
    fun preRequest(request: REQUEST)

    /**
     * 请求处理后的拦截方法
     * 在请求执行后调用，可用于结果处理、日志记录等
     *
     * @param request 请求参数
     * @param response 处理结果
     * @throws Exception 当拦截处理过程中发生异常时
     */
    fun postRequest(request: REQUEST, response: RESPONSE)
}
