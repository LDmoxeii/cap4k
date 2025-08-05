package com.only4.cap4k.ddd.core.application.saga

import com.only4.cap4k.ddd.core.application.RequestParam

/**
 * Saga子环节执行器
 *
 * @author LD_moxeii
 * @date 2025/07/26
 */
interface SagaProcessSupervisor {

    companion object {
        /**
         * 获取Saga子环节执行管理器
         *
         * @return Saga子环节执行管理器实例
         */
        val instance: SagaProcessSupervisor
            get() = SagaSupervisorSupport.sagaProcessSupervisor

    }

    /**
     * 执行Saga子环节
     *
     * @param processCode Saga子环节标识
     * @param request     请求参数
     * @return 处理结果
     */
    fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> sendProcess(
        processCode: String,
        request: REQUEST
    ): RESPONSE?
}
