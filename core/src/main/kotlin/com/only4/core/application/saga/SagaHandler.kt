package com.only4.core.application.saga

import com.only4.core.application.RequestHandler
import com.only4.core.application.RequestParam

/**
 * SagaHandler 基类
 *
 * @author binking338
 * @date 2024/10/12
 */
interface SagaHandler<RESPONSE : Any, REQUEST : SagaParam<RESPONSE>> :
    RequestHandler<RESPONSE, REQUEST> {
    /**
     * 执行流程子环节
     *
     * @param subCode
     * @param request
     * @param <SUB_REQUEST>
     * @param <SUB_RESPONSE>
     * @return
    </SUB_RESPONSE></SUB_REQUEST> */
    fun <SUB_RESPONSE : Any, SUB_REQUEST : RequestParam<SUB_RESPONSE>> execProcess(
        subCode: String,
        request: SUB_REQUEST
    ): SUB_RESPONSE {
        return SagaProcessSupervisor.instance
            .sendProcess(subCode, request)
    }
}
