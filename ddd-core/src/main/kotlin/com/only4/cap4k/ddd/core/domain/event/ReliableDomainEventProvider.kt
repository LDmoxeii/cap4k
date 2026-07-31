package com.only4.cap4k.ddd.core.domain.event

import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import java.time.LocalDateTime

/**
 * Optional provider for persisted or delayed domain events.
 */
interface ReliableDomainEventProvider {
    fun publish(
        eventPayload: Any,
        schedule: LocalDateTime,
        executionContext: ExecutionContextSnapshot,
    )
}
