package com.only4.cap4k.ddd.core.application.command

import com.only4.cap4k.ddd.core.ProviderSlot

object ReliableCommandSupervisorSupport {
    private val supervisorSlot = ProviderSlot<ReliableCommandSupervisor>(
        "reliable-commands",
        "cap4k-ddd-command-jpa-starter",
    )
    private val managerSlot = ProviderSlot<CommandManager>(
        "command-manager",
        "cap4k-ddd-command-jpa-starter",
    )

    val instance: ReliableCommandSupervisor
        get() = supervisorSlot.get()

    val manager: CommandManager
        get() = managerSlot.get()

    fun configure(supervisor: ReliableCommandSupervisor) = supervisorSlot.configure(supervisor)

    fun configure(manager: CommandManager) = managerSlot.configure(manager)
}
