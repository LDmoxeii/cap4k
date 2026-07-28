package com.only4.cap4k.ddd.core.domain.event

import java.time.LocalDateTime

/**
 * Optional provider for persisted or delayed domain events.
 */
interface ReliableDomainEventProvider {
    fun publish(eventPayload: Any, schedule: LocalDateTime)
}
