package com.only4.cap4k.ddd.core.application.saga

/**
 * Saga 管理器配置
 *
 * @author LD_moxeii
 * @date 2025/07/26
 */
object SagaSupervisorSupport {

    lateinit var instance: SagaSupervisor

    lateinit var sagaProcessSupervisor: SagaProcessSupervisor

    lateinit var sagaManager: SagaManager

    /**
     * 配置 Saga 管理器
     *
     * @param sagaSupervisor Saga管理器实例
     */
    fun configure(sagaSupervisor: SagaSupervisor) {
        instance = sagaSupervisor
    }

    /**
     * 配置 Saga 子执行器
     *
     * @param sagaProcessSupervisor Saga子执行器实例
     */
    fun configure(sagaProcessSupervisor: SagaProcessSupervisor) {
        this.sagaProcessSupervisor = sagaProcessSupervisor
    }

    /**
     * 配置 Saga 管理器
     *
     * @param sagaManager Saga管理器实例
     */
    fun configure(sagaManager: SagaManager) {
        this.sagaManager = sagaManager
    }
}
