package com.only4.core.application.saga

/**
 * Saga管理器配置支持类
 * 用于配置和管理Saga相关的组件实例
 * 提供全局访问点，支持在应用启动时进行配置
 *
 * @author binking338
 * @date 2024/10/12
 */
object SagaSupervisorSupport {
    /**
     * Saga监督者实例
     * 负责管理和控制Saga事务流程
     */
    lateinit var instance: SagaSupervisor

    /**
     * Saga子环节执行器实例
     * 负责执行Saga事务流程中的子环节
     */
    lateinit var sagaProcessSupervisor: SagaProcessSupervisor

    /**
     * Saga管理器实例
     * 负责管理Saga事务流程的执行、重试和归档
     */
    lateinit var sagaManager: SagaManager

    /**
     * 配置Saga监督者
     * 在应用启动时调用此方法进行配置
     *
     * @param sagaSupervisor Saga监督者实例
     * @throws IllegalStateException 如果实例已经被初始化
     */
    fun configure(sagaSupervisor: SagaSupervisor) {
        instance = sagaSupervisor
    }

    /**
     * 配置Saga子环节执行器
     * 在应用启动时调用此方法进行配置
     *
     * @param sagaProcessSupervisor Saga子环节执行器实例
     * @throws IllegalStateException 如果实例已经被初始化
     */
    fun configure(sagaProcessSupervisor: SagaProcessSupervisor) {
        SagaSupervisorSupport.sagaProcessSupervisor = sagaProcessSupervisor
    }

    /**
     * 配置Saga管理器
     * 在应用启动时调用此方法进行配置
     *
     * @param sagaManager Saga管理器实例
     * @throws IllegalStateException 如果实例已经被初始化
     */
    fun configure(sagaManager: SagaManager) {
        SagaSupervisorSupport.sagaManager = sagaManager
    }
}
