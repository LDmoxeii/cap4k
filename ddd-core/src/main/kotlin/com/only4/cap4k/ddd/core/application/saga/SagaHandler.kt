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
    ): SUB_RESPONSE? = SagaProcessSupervisor.instance.sendProcess(subCode, request)

}
