package com.only4.cap4k.ddd.core.application.command

/**
 * Category-specific interception for one concrete [Command] type.
 */
interface CommandInterceptor<COMMAND : Command<RESULT>, RESULT : Any> {
    fun beforeCommand(command: COMMAND) = Unit

    fun afterCommand(command: COMMAND, result: RESULT) = Unit
}
