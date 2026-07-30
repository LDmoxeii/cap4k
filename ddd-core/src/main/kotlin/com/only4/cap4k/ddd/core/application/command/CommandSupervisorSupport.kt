package com.only4.cap4k.ddd.core.application.command

import com.only4.cap4k.ddd.core.ProviderSlot

object CommandSupervisorSupport {
    private val slot = ProviderSlot<CommandSupervisor>("commands", "cap4k-ddd-core-starter")

    val instance: CommandSupervisor
        get() = slot.get()

    fun configure(supervisor: CommandSupervisor) {
        slot.configure(supervisor)
    }
}
