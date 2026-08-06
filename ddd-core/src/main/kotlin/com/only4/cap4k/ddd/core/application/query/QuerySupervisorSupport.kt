package com.only4.cap4k.ddd.core.application.query

import com.only4.cap4k.ddd.core.ProviderSlot

object QuerySupervisorSupport {
    private val slot = ProviderSlot<QuerySupervisor>("queries", "cap4k-ddd-core-starter")

    val instance: QuerySupervisor
        get() = slot.get()

    fun configure(supervisor: QuerySupervisor) {
        slot.configure(supervisor)
    }

    fun release(supervisor: QuerySupervisor) {
        slot.release(supervisor)
    }
}
