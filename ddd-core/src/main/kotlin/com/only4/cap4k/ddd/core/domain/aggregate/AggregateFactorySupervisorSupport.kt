package com.only4.cap4k.ddd.core.domain.aggregate

import com.only4.cap4k.ddd.core.CapabilitySlot

/**
 * 聚合工厂管理器配置
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
object AggregateFactorySupervisorSupport {
    private val slot = CapabilitySlot<AggregateFactorySupervisor>("factories", "cap4k-ddd-jpa-starter")

    val instance: AggregateFactorySupervisor
        get() = slot.get()

    fun configure(aggregateFactorySupervisor: AggregateFactorySupervisor) {
        slot.configure(aggregateFactorySupervisor)
    }
}
