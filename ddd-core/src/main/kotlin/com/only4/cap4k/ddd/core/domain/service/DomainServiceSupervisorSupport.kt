package com.only4.cap4k.ddd.core.domain.service

import com.only4.cap4k.ddd.core.ProviderSlot

/**
 * 领域服务管理
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
object DomainServiceSupervisorSupport {
    private val slot = ProviderSlot<DomainServiceSupervisor>("services", "cap4k-ddd-core-starter")

    val instance: DomainServiceSupervisor
        get() = slot.get()

    fun configure(domainServiceSupervisor: DomainServiceSupervisor) {
        slot.configure(domainServiceSupervisor)
    }

    fun release(domainServiceSupervisor: DomainServiceSupervisor) {
        slot.release(domainServiceSupervisor)
    }
}
