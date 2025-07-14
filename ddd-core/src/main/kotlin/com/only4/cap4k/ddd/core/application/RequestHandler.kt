package com.only4.cap4k.ddd.core.application

/**
 * 请求处理器接口
 * 用于处理各种类型的请求并返回相应的结果
 * 支持命令、查询和Saga事务等不同类型的请求处理
 *
 * @param RESPONSE 响应结果类型，必须是具体类型
 * @param REQUEST 请求参数类型，必须继承自RequestParam
 * @author binking338
 * @date 2024/8/24
 */
interface RequestHandler<REQUEST : RequestParam<RESPONSE>, RESPONSE> {
    /**
     * 执行请求处理
     * 根据请求参数执行相应的业务逻辑并返回处理结果
     *
     * @param request 请求参数
     * @return 处理结果
     * @throws Exception 当处理过程中发生异常时
     */
    fun exec(request: REQUEST): RESPONSE
}
