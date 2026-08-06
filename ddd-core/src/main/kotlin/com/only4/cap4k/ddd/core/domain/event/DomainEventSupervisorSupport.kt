package com.only4.cap4k.ddd.core.domain.event

import com.only4.cap4k.ddd.core.ProviderSlot

/**
 * 领域事件管理器配置
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
object DomainEventSupervisorSupport {
    private val supervisorSlot = ProviderSlot<DomainEventSupervisor>("domain-events", "cap4k-ddd-core-starter")
    private val managerSlot = ProviderSlot<DomainEventManager>("domain-event-manager", "cap4k-ddd-core-starter")

    val instance: DomainEventSupervisor
        get() = supervisorSlot.get()

    val manager: DomainEventManager
        get() = managerSlot.get()

    /**
     * 配置领域事件管理器
     * @param domainEventSupervisor [DomainEventSupervisor]
     */
    fun configure(domainEventSupervisor: DomainEventSupervisor) {
        supervisorSlot.configure(domainEventSupervisor)
    }

    fun release(domainEventSupervisor: DomainEventSupervisor) {
        supervisorSlot.release(domainEventSupervisor)
    }

    /**
     * 配置领域事件发布管理器
     * @param domainEventManager [DomainEventManager]
     */
    fun configure(domainEventManager: DomainEventManager) {
        managerSlot.configure(domainEventManager)
    }

    fun release(domainEventManager: DomainEventManager) {
        managerSlot.release(domainEventManager)
    }

    /**
     * for entity import static
     *
     * @return
     */
    fun events(): DomainEventSupervisor = instance

}
