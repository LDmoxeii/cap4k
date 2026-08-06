package com.only4.cap4k.ddd.core.application.command

import java.time.LocalDateTime

interface CommandManager {
    fun resume(command: CommandRecord, minNextTryTime: LocalDateTime)
    fun retry(id: String)
    fun getByNextTryTime(maxNextTryTime: LocalDateTime, limit: Int): List<CommandRecord>

    companion object {
        @JvmStatic
        val instance: CommandManager
            get() = ReliableCommandSupervisorSupport.manager
    }
}
