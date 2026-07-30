package com.only4.cap4k.ddd.core.domain.repo

import com.only4.cap4k.ddd.core.ProviderSlot

/**
 * 仓储管理器配置
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
object RepositorySupervisorSupport {
    private val slot = ProviderSlot<RepositorySupervisor>("repositories", "cap4k-ddd-jpa-starter")

    val instance: RepositorySupervisor
        get() = slot.get()

    fun configure(repositorySupervisor: RepositorySupervisor) {
        slot.configure(repositorySupervisor)
    }
}
