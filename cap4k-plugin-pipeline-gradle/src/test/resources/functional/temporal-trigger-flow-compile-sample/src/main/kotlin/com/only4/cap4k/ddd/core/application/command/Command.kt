package com.only4.cap4k.ddd.core.application.command

interface Command<R : Any>

interface CommandSupervisor {
    fun <R : Any> send(command: Command<R>): R
}
