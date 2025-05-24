package com.only4.core.application.event

/**
 * 事件管理器配置
 *
 * @author binking338
 * @date 2024/8/26
 */
object IntegrationEventSupervisorSupport {
    lateinit var instance: IntegrationEventSupervisor

    lateinit var manager: IntegrationEventManager


    /**
     * 配置事件管理器
     *
     * @param integrationEventSupervisor [IntegrationEventSupervisor]
     */
    fun configure(integrationEventSupervisor: IntegrationEventSupervisor) {
        instance = integrationEventSupervisor
    }

    /**
     * 配置事件管理器
     *
     * @param integrationEventManager [IntegrationEventManager]
     */
    fun configure(integrationEventManager: IntegrationEventManager) {
        manager = integrationEventManager
    }
}
