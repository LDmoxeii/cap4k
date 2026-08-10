package com.only4.cap4k.ddd.core.application.event

import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint
import java.time.Instant

/**
 * Transport-neutral Integration Event representation.
 *
 * The payload is deliberately retained as JSON text. A transport must not persist or
 * reconstruct a persistence-bound Aggregate/Entity instance as part of this envelope.
 */
data class IntegrationEventEnvelope(
    val eventId: String,
    val eventType: String,
    val originService: String,
    val publishedAt: Instant,
    val deliveryAttempt: Int?,
    val executionContext: List<EncodedExecutionContextElement>,
    val payloadJson: String,
) {
    init {
        require(eventId.isNotBlank()) { "Integration Event envelope eventId must not be blank" }
        require(eventType.isNotBlank()) { "Integration Event envelope eventType must not be blank" }
        require(originService.isNotBlank()) {
            "Integration Event envelope originService must not be blank"
        }
        require(deliveryAttempt == null || deliveryAttempt > 0) {
            "Integration Event envelope deliveryAttempt must be positive when present"
        }
        require(payloadJson.isNotBlank()) { "Integration Event envelope payloadJson must not be blank" }
    }
}

/** Provider-owned metadata that accompanies an inbound envelope. */
data class IntegrationEventDeliveryMetadata(
    /** Exact provider attempt, used only when the sender envelope has no reliable attempt. */
    val providerDeliveryAttempt: Int? = null,
    val redeliveryHint: ReliableEventRedeliveryHint = ReliableEventRedeliveryHint.UNKNOWN,
) {
    init {
        require(providerDeliveryAttempt == null || providerDeliveryAttempt > 0) {
            "Integration Event providerDeliveryAttempt must be positive when present"
        }
    }
}

/** Creates the common reliable delivery context without guessing provider facts. */
fun IntegrationEventEnvelope.deliveryContext(
    metadata: IntegrationEventDeliveryMetadata,
): ReliableEventDeliveryContext = ReliableEventDeliveryContext(
    eventId = eventId,
    eventName = eventType,
    publishedAt = publishedAt,
    attempt = deliveryAttempt ?: metadata.providerDeliveryAttempt,
    redeliveryHint = metadata.redeliveryHint,
)
