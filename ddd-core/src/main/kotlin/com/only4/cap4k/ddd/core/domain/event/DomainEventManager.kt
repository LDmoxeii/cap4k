package com.only4.cap4k.ddd.core.domain.event

/**
 * 领域事件发布管理器
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface DomainEventManager {
    /**
     * 发布附加到指定实体以及所有未附加到实体的领域事件
     * @param entities 指定实体集合
     */
    fun release(entities: Set<Any>)

    /** Number of Domain Events still waiting in the active UoW scope. */
    fun pendingCount(): Int = 0

    /** Discard unreleased events attached to one aggregate root. */
    fun discard(entity: Any) = Unit
}
