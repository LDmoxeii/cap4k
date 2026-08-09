package com.only4.cap4k.ddd.core.domain.event

import com.only4.cap4k.ddd.core.application.context.ExecutionContextElement
import java.time.Instant

/**
 * Read-only metadata for one reliable event delivery.
 *
 * The hint is deliberately non-authoritative and does not imply inbox deduplication
 * or exactly-once handling.
 */
data class ReliableEventDeliveryContext(
    val eventId: String,
    val eventName: String,
    val publishedAt: Instant,
    val attempt: Int?,
    val redeliveryHint: ReliableEventRedeliveryHint,
    /** Stable destination/subscription identity supplied by the inbound adapter. */
    val subscriberIdentity: String? = null,
) : ExecutionContextElement {
    init {
        require(eventId.isNotBlank()) { "Reliable event ID must not be blank" }
        require(eventName.isNotBlank()) { "Reliable event name must not be blank" }
        require(attempt == null || attempt > 0) { "Reliable event delivery attempt must be positive when present" }
        require(subscriberIdentity == null || subscriberIdentity.isNotBlank()) {
            "Reliable event subscriber identity must not be blank when present"
        }
    }
}

/** A non-authoritative indication; only [ReliableEventDeliveryContext.attempt] may be exact. */
enum class ReliableEventRedeliveryHint {
    UNKNOWN,
    FIRST,
    REDELIVERED,
}

interface ReliableEventDeliveryContextAccessor {
    fun currentOrNull(): ReliableEventDeliveryContext?

    fun current(): ReliableEventDeliveryContext = currentOrNull()
        ?: error("No reliable event delivery context is active")
}

/** Framework infrastructure for delimiting reliable handler dispatch. */
interface ReliableEventDeliveryContextScopeManager {
    fun install(context: ReliableEventDeliveryContext): AutoCloseable

    fun suppress(): AutoCloseable
}
