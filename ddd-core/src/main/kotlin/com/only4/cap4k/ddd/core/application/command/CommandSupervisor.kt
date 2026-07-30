package com.only4.cap4k.ddd.core.application.command

import jakarta.validation.ConstraintViolationException
import java.time.Duration
import java.time.LocalDateTime

/**
 * Synchronously dispatches local application commands.
 */
interface CommandSupervisor {
    /**
     * @throws ConstraintViolationException when command validation fails
     */
    fun <COMMAND : Command<RESULT>, RESULT : Any> send(command: COMMAND): RESULT

    fun <COMMAND : Command<RESULT>, RESULT : Any> enqueue(command: COMMAND): String =
        schedule(command, LocalDateTime.now())

    fun <COMMAND : Command<RESULT>, RESULT : Any> schedule(
        command: COMMAND,
        schedule: LocalDateTime,
    ): String = ReliableCommandSupervisor.instance.schedule(command, schedule)

    fun <COMMAND : Command<RESULT>, RESULT : Any> delay(command: COMMAND, delay: Duration): String =
        schedule(command, LocalDateTime.now().plus(delay))

    fun <RESULT : Any> result(commandId: String): RESULT? =
        ReliableCommandSupervisor.instance.result(commandId)

    companion object {
        @JvmStatic
        val instance: CommandSupervisor
            get() = CommandSupervisorSupport.instance
    }
}
