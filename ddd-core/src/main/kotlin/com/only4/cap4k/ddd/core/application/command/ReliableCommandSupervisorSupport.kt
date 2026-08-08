package com.only4.cap4k.ddd.core.application.command

import com.only4.cap4k.ddd.core.ProviderSlot

object ReliableCommandSupervisorSupport {
    private val supervisorSlot = ProviderSlot<ReliableCommandSupervisor>(
        "reliable-commands",
        "cap4k-ddd-command-jpa-starter",
    )
    val instance: ReliableCommandSupervisor
        get() = supervisorSlot.get()

    fun configure(supervisor: ReliableCommandSupervisor) = supervisorSlot.configure(supervisor)

    fun release(supervisor: ReliableCommandSupervisor) = supervisorSlot.release(supervisor)
}
