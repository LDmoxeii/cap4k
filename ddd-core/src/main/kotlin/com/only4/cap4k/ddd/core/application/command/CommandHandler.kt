package com.only4.cap4k.ddd.core.application.command

/**
 * Handles one concrete [Command] type in the current thread.
 */
fun interface CommandHandler<COMMAND : Command<RESULT>, RESULT : Any> {
    fun handle(command: COMMAND): RESULT
}
