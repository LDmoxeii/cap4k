package com.only4.core.application.saga

import com.only4.core.application.RequestParam

/**
 * Saga子环节执行器接口
 * 负责执行Saga事务流程中的子环节
 * 提供统一的子环节执行入口，确保子环节的正确执行和结果返回
 *
 * @author binking338
 * @date 2024/10/14
 */
interface SagaProcessSupervisor {
    /**
     * 执行Saga子环节
     * 根据子环节标识和请求参数执行对应的子环节，并返回执行结果
     *
     * @param processCode 子环节标识，用于确定要执行的子环节
     * @param request 请求参数，包含子环节执行所需的数据
     * @return 子环节执行结果
     *
     * @param RESPONSE 响应类型，表示子环节执行的结果类型
     * @param REQUEST 请求类型，必须实现RequestParam接口
     */
    fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> sendProcess(
        processCode: String,
        request: REQUEST
    ): RESPONSE

    companion object {
        /**
         * 获取Saga子环节执行器实例
         * 通过SagaSupervisorSupport获取全局唯一的执行器实例
         *
         * @return Saga子环节执行器实例
         */
        val instance: SagaProcessSupervisor
            get() = SagaSupervisorSupport.sagaProcessSupervisor
    }
}
