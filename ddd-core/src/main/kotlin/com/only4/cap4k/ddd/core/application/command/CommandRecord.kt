package com.only4.cap4k.ddd.core.application.command

import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
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
        executionContext: Collection<EncodedExecutionContextElement> = emptyList(),
    )

    val id: String
    val type: String
    val command: Command<*>
    val executionContext: List<EncodedExecutionContextElement>
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
