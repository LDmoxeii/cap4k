package com.only4.core.application.saga

import com.only4.core.application.RequestParam

/**
 * Saga子环节执行器
 *
 * @author binking338
 * @date 2024/10/14
 */
interface SagaProcessSupervisor {
    /**
     * 执行Saga子环节
     *
     * @param processCode Saga子环节标识
     * @param request     请求参数
     * @param <REQUEST>   请求参数类型
     * @param <RESPONSE>  响应参数类型
     * @return
    </RESPONSE></REQUEST> */
    fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> sendProcess(
        processCode: String,
        request: REQUEST
    ): RESPONSE

    companion object {
        val instance: SagaProcessSupervisor
            /**
             * 获取Saga子环节执行管理器
             *
             * @return
             */
            get() = SagaSupervisorSupport.sagaProcessSupervisor
    }
}
