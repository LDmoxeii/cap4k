package com.only4.cap4k.ddd.core.application.command

/** Transaction hook supplied by a reliable Command persistence provider. */
interface ReliableCommandTransaction {
    fun requireActive()

    fun afterCommit(action: () -> Unit)
}
