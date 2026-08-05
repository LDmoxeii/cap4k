package com.only4.cap4k.ddd.core.application.event

import com.only4.cap4k.ddd.core.ProviderSlot

/**
 * 集成事件管理器配置支持类
 * 用于配置和管理集成事件的监督者和管理器实例
 * 提供全局访问点，支持在应用启动时进行配置
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
object IntegrationEventSupervisorSupport {
    /**
     * 集成事件监督者实例
     * 负责监督和管理集成事件的生命周期
     */
    private val supervisorSlot = ProviderSlot<IntegrationEventSupervisor>(
        "events",
        "a cap4k Integration Event transport starter",
    )

    val instance: IntegrationEventSupervisor
        get() = supervisorSlot.get()

    /**
     * 集成事件管理器实例
     * 负责管理集成事件的发布和存储
     */
    private val managerSlot = ProviderSlot<IntegrationEventManager>(
        "integration-event-manager",
        "a cap4k Integration Event transport starter",
    )

    val manager: IntegrationEventManager
        get() = managerSlot.get()

    fun managerOrNull(): IntegrationEventManager? = managerSlot.getOrNull()

    /**
     * 配置集成事件监督者
     * 在应用启动时调用此方法进行配置
     *
     * @param integrationEventSupervisor 集成事件监督者实例
     * @throws IllegalStateException 如果实例已经被初始化
     */
    fun configure(integrationEventSupervisor: IntegrationEventSupervisor) {
        supervisorSlot.configure(integrationEventSupervisor)
    }

    fun release(integrationEventSupervisor: IntegrationEventSupervisor) {
        supervisorSlot.release(integrationEventSupervisor)
    }

    /**
     * 配置集成事件管理器
     * 在应用启动时调用此方法进行配置
     *
     * @param integrationEventManager 集成事件管理器实例
     * @throws IllegalStateException 如果实例已经被初始化
     */
    fun configure(integrationEventManager: IntegrationEventManager) {
        managerSlot.configure(integrationEventManager)
    }

    fun release(integrationEventManager: IntegrationEventManager) {
        managerSlot.release(integrationEventManager)
    }
}
