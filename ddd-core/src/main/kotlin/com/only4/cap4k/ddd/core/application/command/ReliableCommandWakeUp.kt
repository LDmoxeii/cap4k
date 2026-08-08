package com.only4.cap4k.ddd.core.application.command

import java.time.LocalDateTime

/**
 * Narrow provider bridge used after a reliable Command registration commits.
 *
 * The implementation may only wake the private reliable execution worker. It
 * does not own Command state and is not a public scheduler or task API.
 */
fun interface ReliableCommandWakeUp {
    fun wakeUp(scheduleAt: LocalDateTime)
}
