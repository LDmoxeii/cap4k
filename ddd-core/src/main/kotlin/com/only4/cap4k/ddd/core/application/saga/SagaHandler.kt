package com.only4.cap4k.ddd.core.application.saga

import com.only4.cap4k.ddd.core.application.RequestHandler
import com.only4.cap4k.ddd.core.application.RequestParam

/**
 * SagaHandler 基类
 *
 * @author LD_moxeii
 * @date 2025/07/26
 */
interface SagaHandler<REQUEST : SagaParam<RESPONSE>, RESPONSE : Any> : RequestHandler<REQUEST, RESPONSE> {

    /**
     * 执行流程子环节
     *
     * @param subCode 子流程代码
     * @param request 请求参数
     * @return 子流程执行结果
     */
    fun <SUB_REQUEST : RequestParam<SUB_RESPONSE>, SUB_RESPONSE : Any> execProcess(
        subCode: String,
        request: SUB_REQUEST
    ): SUB_RESPONSE = SagaProcessSupervisor.instance.sendProcess(subCode, request)

    /**
     * 执行可补偿的流程子环节
     *
     * @param processCode 子流程代码
     * @param request 正向请求参数
     * @param compensationCode 补偿流程代码
     * @param compensationRequest 根据正向结果构造补偿请求
     * @return 子流程执行结果
     */
    fun <SUB_REQUEST : RequestParam<SUB_RESPONSE>, SUB_RESPONSE : Any, COMPENSATION_REQUEST : RequestParam<*>> execCompensableProcess(
        processCode: String,
        request: SUB_REQUEST,
        compensationCode: String,
        compensationRequest: (SUB_RESPONSE) -> COMPENSATION_REQUEST
    ): SUB_RESPONSE = SagaProcessSupervisor.instance.sendCompensableProcess(
        processCode = processCode,
        request = request,
        compensationCode = compensationCode,
        compensationRequest = compensationRequest
    )

    /**
     * 显式请求补偿，当前Saga将终止正向执行并进入补偿
     *
     * @param code   补偿请求代码
     * @param reason 补偿原因
     */
    fun requestCompensation(code: String, reason: String): Nothing =
        SagaProcessSupervisor.instance.requestCompensation(code, reason)

}
