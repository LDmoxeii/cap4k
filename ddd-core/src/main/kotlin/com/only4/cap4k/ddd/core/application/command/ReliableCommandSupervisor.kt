package com.only4.cap4k.ddd.core.application.command

import java.time.LocalDateTime

/** Optional provider for durable asynchronous Command execution. */
interface ReliableCommandSupervisor {
    fun <COMMAND : Command<RESULT>, RESULT : Any> schedule(
        command: COMMAND,
        schedule: LocalDateTime,
    ): String

    fun <RESULT : Any> result(commandId: String): RESULT?

    companion object {
        @JvmStatic
        val instance: ReliableCommandSupervisor
            get() = ReliableCommandSupervisorSupport.instance
    }
}
