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
        @JvmStatic
        val instance: SagaProcessSupervisor by lazy { SagaSupervisorSupport.sagaProcessSupervisor }

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
    ): RESPONSE

    /**
     * 执行可补偿的Saga子环节
     *
     * @param processCode 子流程标识
     * @param request 正向请求参数
     * @param compensationCode 补偿流程标识
     * @param compensationRequest 根据正向结果构造补偿请求
     * @return 处理结果
     */
    fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any, COMPENSATION_REQUEST : RequestParam<*>> sendCompensableProcess(
        processCode: String,
        request: REQUEST,
        compensationCode: String,
        compensationRequest: (RESPONSE) -> COMPENSATION_REQUEST
    ): RESPONSE

    /**
     * 请求当前Saga进入补偿流程
     *
     * @param code 补偿代码
     * @param reason 补偿原因
     */
    fun requestCompensation(code: String, reason: String): Nothing
}
