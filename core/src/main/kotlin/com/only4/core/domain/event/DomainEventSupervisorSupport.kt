package com.only4.core.domain.event

/**
 * 领域事件管理器配置
 *
 * @author binking338
 * @date 2024/8/24
 */
object DomainEventSupervisorSupport {
    lateinit var instance: DomainEventSupervisor
    lateinit var manager: DomainEventManager

    /**
     * 配置领域事件管理器
     * @param domainEventSupervisor [DomainEventSupervisor]
     */
    fun configure(domainEventSupervisor: DomainEventSupervisor) {
        instance = domainEventSupervisor
    }

    /**
     * 配置领域事件发布管理器
     * @param domainEventManager [DomainEventManager]
     */
    fun configure(domainEventManager: DomainEventManager) {
        manager = domainEventManager
    }

    /**
     * for entity import static
     *
     * @return
     */
    fun events(): DomainEventSupervisor {
        return instance
    }
}
