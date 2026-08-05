package com.only4.cap4k.ddd.core.application.capability

import com.only4.cap4k.ddd.core.ProviderSlot

object CapabilitySupervisorSupport {
    private val slot = ProviderSlot<CapabilitySupervisor>("capabilities", "cap4k-ddd-core-starter")

    val instance: CapabilitySupervisor
        get() = slot.get()

    fun configure(supervisor: CapabilitySupervisor) {
        slot.configure(supervisor)
    }

    fun release(supervisor: CapabilitySupervisor) {
        slot.release(supervisor)
    }
}
