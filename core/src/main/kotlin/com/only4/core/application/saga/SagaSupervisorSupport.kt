package com.only4.core.application.saga

/**
 * Saga 管理器配置
 *
 * @author binking338
 * @date 2024/10/12
 */
object SagaSupervisorSupport {
    lateinit var instance: SagaSupervisor
    lateinit var sagaProcessSupervisor: SagaProcessSupervisor
    lateinit var sagaManager: SagaManager

    /**
     * 配置 Saga 管理器
     *
     * @param sagaSupervisor
     */
    fun configure(sagaSupervisor: SagaSupervisor) {
        instance = sagaSupervisor
    }

    /**
     * 配置 Saga 子执行器
     *
     * @param sagaProcessSupervisor
     */
    fun configure(sagaProcessSupervisor: SagaProcessSupervisor) {
        SagaSupervisorSupport.sagaProcessSupervisor = sagaProcessSupervisor
    }

    /**
     * 配置 Saga 管理器
     *
     * @param sagaManager
     */
    fun configure(sagaManager: SagaManager) {
        SagaSupervisorSupport.sagaManager = sagaManager
    }
}
