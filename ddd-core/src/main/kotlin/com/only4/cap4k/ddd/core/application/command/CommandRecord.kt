package com.only4.cap4k.ddd.core.application.command

import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import java.time.Duration
import java.time.LocalDateTime

/** Registration-only carrier for a reliable Command persistence provider. */
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
}
