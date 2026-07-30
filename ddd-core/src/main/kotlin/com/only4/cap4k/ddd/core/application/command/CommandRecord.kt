package com.only4.cap4k.ddd.core.application.command

import java.time.Duration
import java.time.LocalDateTime

interface CommandRecord {
    fun init(
        command: Command<*>,
        serviceName: String,
        commandType: String,
        scheduleAt: LocalDateTime,
        expireAfter: Duration,
        retryTimes: Int,
    )

    val id: String
    val type: String
    val command: Command<*>
    fun <RESULT : Any> getResult(): RESULT?
    val scheduleTime: LocalDateTime
    val nextTryTime: LocalDateTime
    val isValid: Boolean
    val isInvalid: Boolean
    val isExecuting: Boolean
    val isExecuted: Boolean
    fun beginCommand(now: LocalDateTime): Boolean
    fun cancelCommand(now: LocalDateTime): Boolean
    fun endCommand(now: LocalDateTime, result: Any)
    fun occurredException(now: LocalDateTime, throwable: Throwable)
}
