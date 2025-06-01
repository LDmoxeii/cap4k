package com.only4.cap4k.ddd.core.application.event

/**
 * 集成事件管理器配置支持类
 * 用于配置和管理集成事件的监督者和管理器实例
 * 提供全局访问点，支持在应用启动时进行配置
 *
 * @author binking338
 * @date 2024/8/26
 */
object IntegrationEventSupervisorSupport {
    /**
     * 集成事件监督者实例
     * 负责监督和管理集成事件的生命周期
     */
    lateinit var instance: IntegrationEventSupervisor

    /**
     * 集成事件管理器实例
     * 负责管理集成事件的发布和存储
     */
    lateinit var manager: IntegrationEventManager

    /**
     * 配置集成事件监督者
     * 在应用启动时调用此方法进行配置
     *
     * @param integrationEventSupervisor 集成事件监督者实例
     * @throws IllegalStateException 如果实例已经被初始化
     */
    fun configure(integrationEventSupervisor: IntegrationEventSupervisor) {
        instance = integrationEventSupervisor
    }

    /**
     * 配置集成事件管理器
     * 在应用启动时调用此方法进行配置
     *
     * @param integrationEventManager 集成事件管理器实例
     * @throws IllegalStateException 如果实例已经被初始化
     */
    fun configure(integrationEventManager: IntegrationEventManager) {
        manager = integrationEventManager
    }
}
