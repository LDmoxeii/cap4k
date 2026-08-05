package com.only4.cap4k.ddd.core.application.command

import java.time.LocalDateTime

/** Optional provider for durable asynchronous Command execution. */
interface ReliableCommandSupervisor {
    fun <COMMAND : Command<RESULT>, RESULT : Any> schedule(
        command: COMMAND,
        schedule: LocalDateTime,
    ): String

    companion object {
        @JvmStatic
        val instance: ReliableCommandSupervisor
            get() = ReliableCommandSupervisorSupport.instance
    }
}
