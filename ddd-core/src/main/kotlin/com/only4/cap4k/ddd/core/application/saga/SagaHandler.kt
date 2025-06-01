package com.only4.cap4k.ddd.core.application.saga

import com.only4.cap4k.ddd.core.application.RequestHandler
import com.only4.cap4k.ddd.core.application.RequestParam

/**
 * Saga处理器接口
 * 用于处理Saga事务流程中的各个步骤，支持子流程的执行
 *
 * @author binking338
 * @date 2024/10/12
 *
 * @param RESPONSE 响应类型，表示处理结果
 * @param REQUEST 请求类型，必须实现SagaParam接口
 */
interface SagaHandler<RESPONSE : Any, REQUEST : SagaParam<RESPONSE>> :
    RequestHandler<RESPONSE, REQUEST> {
    /**
     * 执行流程子环节
     * 通过SagaProcessSupervisor发送子流程请求并获取结果
     *
     * @param subCode 子流程代码，用于标识要执行的子流程
     * @param request 子流程请求参数
     * @return 子流程执行结果
     *
     * @param SUB_RESPONSE 子流程响应类型
     * @param SUB_REQUEST 子流程请求类型，必须实现RequestParam接口
     */
    fun <SUB_RESPONSE : Any, SUB_REQUEST : RequestParam<SUB_RESPONSE>> execProcess(
        subCode: String,
        request: SUB_REQUEST
    ): SUB_RESPONSE = SagaProcessSupervisor.instance
        .sendProcess(subCode, request)
}
