package com.only4.cap4k.ddd.core.application.event

import com.fasterxml.jackson.databind.JsonNode
import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import com.only4.cap4k.ddd.core.share.json.RuntimeExecutionContextJson
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import com.only4.cap4k.ddd.core.domain.event.impl.DomainEventPayloadValidator
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import java.time.Instant

/**
 * Jackson-backed codec for the one Integration Event wire representation.
 *
 * It owns envelope validation and payload safety only. Routing, acknowledgement, retry,
 * subscriber discovery, and provider-specific metadata remain outside this codec.
 */
class IntegrationEventEnvelopeCodec {
    fun envelope(event: EventRecord): IntegrationEventEnvelope {
        val annotation = event.payload.javaClass.getAnnotation(IntegrationEvent::class.java)
            ?: throw IntegrationEventEnvelopeEncodingException(
                "Integration Event envelope requires an @IntegrationEvent payload"
            )
        require(annotation.value.isNotBlank()) {
            "Integration Event envelope event type must not be blank"
        }

        try {
            DomainEventPayloadValidator.validate(event.payload)
        } catch (_: Exception) {
            throw IntegrationEventEnvelopeEncodingException(
                "Unsafe Integration Event payload for eventId=${event.id}, eventType=${event.type}"
            )
        }

        val context = normalizeExecutionContext(event.executionContext)
        val payloadJson = try {
            RuntimeJson.write(event.payload)
        } catch (_: Exception) {
            throw IntegrationEventEnvelopeEncodingException(
                "Unable to encode Integration Event payload for eventId=${event.id}, eventType=${event.type}"
            )
        }
        return IntegrationEventEnvelope(
            eventId = event.id,
            eventType = event.type,
            originService = event.originService,
            publishedAt = event.publishedAt,
            deliveryAttempt = event.deliveryAttempt,
            executionContext = context,
            payloadJson = payloadJson,
        )
    }

    fun encode(event: EventRecord): String = encode(envelope(event))
    fun encode(envelope: IntegrationEventEnvelope): String = RuntimeJson.write(
        WireEnvelope(
            eventId = envelope.eventId,
            eventType = envelope.eventType,
            originService = envelope.originService,
            publishedAt = envelope.publishedAt.toString(),
            deliveryAttempt = envelope.deliveryAttempt,
            executionContext = normalizeExecutionContext(envelope.executionContext),
            payloadJson = envelope.payloadJson,
        )
    )

    fun decode(json: String): IntegrationEventEnvelope {
        val root = try {
            RuntimeJson.tree(json)
        } catch (_: Exception) {
            throw IntegrationEventEnvelopeDecodingException("Malformed Integration Event envelope")
        }
        if (!root.isObject) {
            throw IntegrationEventEnvelopeDecodingException("Integration Event envelope must be a JSON object")
        }

        val eventId = requiredText(root, "eventId")
        val eventType = requiredText(root, "eventType")
        val originService = requiredText(root, "originService")
        val publishedAt = parseInstant(root, eventId, eventType)
        val deliveryAttempt = parseAttempt(root, eventId, eventType)
        val executionContext = parseExecutionContext(root, eventId, eventType)
        val payloadJson = requiredText(root, "payloadJson", eventId, eventType)
        try {
            val payloadNode = RuntimeJson.tree(payloadJson)
            require(!payloadNode.isNull) { "payloadJson must not be null" }
        } catch (_: Exception) {
            throw IntegrationEventEnvelopeDecodingException(
                "Malformed Integration Event payload for eventId=$eventId, eventType=$eventType"
            )
        }

        return try {
            IntegrationEventEnvelope(
                eventId = eventId,
                eventType = eventType,
                originService = originService,
                publishedAt = publishedAt,
                deliveryAttempt = deliveryAttempt,
                executionContext = executionContext,
                payloadJson = payloadJson,
            )
        } catch (_: Exception) {
            throw IntegrationEventEnvelopeDecodingException(
                "Invalid Integration Event envelope metadata for eventId=$eventId, eventType=$eventType"
            )
        }
    }

    /** Resolves payload type through the caller-owned catalog and keeps the entity boundary intact. */
    fun payloadJson(envelope: IntegrationEventEnvelope, eventClass: Class<*>): Any {
        val annotation = eventClass.getAnnotation(IntegrationEvent::class.java)
            ?: throw IntegrationEventEnvelopeDecodingException(
                "Payload type is not an @IntegrationEvent for eventId=${envelope.eventId}, " +
                    "eventType=${envelope.eventType}"
            )
        if (annotation.value.isBlank()) {
            throw IntegrationEventEnvelopeDecodingException(
                "Payload event type is blank for eventId=${envelope.eventId}, eventType=${envelope.eventType}"
            )
        }
        if (annotation.value != envelope.eventType) {
            throw IntegrationEventEnvelopeDecodingException(
                "Payload event type does not match envelope for eventId=${envelope.eventId}, " +
                    "eventType=${envelope.eventType}"
            )
        }
        val payload = try {
            RuntimeJson.read(envelope.payloadJson, eventClass)
        } catch (_: Exception) {
            throw IntegrationEventEnvelopeDecodingException(
                "Unable to decode Integration Event payload for eventId=${envelope.eventId}, " +
                    "eventType=${envelope.eventType}"
            )
        }
        try {
            DomainEventPayloadValidator.validate(payload)
        } catch (_: Exception) {
            throw IntegrationEventEnvelopeDecodingException(
                "Unsafe Integration Event payload for eventId=${envelope.eventId}, " +
                    "eventType=${envelope.eventType}"
            )
        }
        return payload
    }

    private fun normalizeExecutionContext(
        elements: Collection<EncodedExecutionContextElement>,
    ): List<EncodedExecutionContextElement> {
        val encoded = RuntimeExecutionContextJson.encode(elements, "Integration Event envelope executionContext")
        return RuntimeExecutionContextJson.decode(encoded, "Integration Event envelope executionContext")
    }

    private fun parseExecutionContext(
        root: JsonNode,
        eventId: String,
        eventType: String,
    ): List<EncodedExecutionContextElement> {
        val value = root.get("executionContext")
            ?: throw IntegrationEventEnvelopeDecodingException(
                "Missing Integration Event executionContext for eventId=$eventId, eventType=$eventType"
            )
        return try {
            RuntimeExecutionContextJson.decode(
                value.toString(),
                "Integration Event envelope executionContext",
            )
        } catch (_: Exception) {
            throw IntegrationEventEnvelopeDecodingException(
                "Malformed Integration Event executionContext for eventId=$eventId, eventType=$eventType"
            )
        }
    }

    private fun parseInstant(root: JsonNode, eventId: String, eventType: String): Instant {
        val raw = requiredText(root, "publishedAt", eventId, eventType)
        return try {
            Instant.parse(raw)
        } catch (_: Exception) {
            throw IntegrationEventEnvelopeDecodingException(
                "Invalid Integration Event publishedAt for eventId=$eventId, eventType=$eventType"
            )
        }
    }

    private fun parseAttempt(root: JsonNode, eventId: String, eventType: String): Int? {
        val value = root.get("deliveryAttempt") ?: return null
        if (value.isNull) return null
        val attempt = value.takeIf { it.isIntegralNumber }?.intValue()
            ?: throw IntegrationEventEnvelopeDecodingException(
                "Invalid Integration Event deliveryAttempt for eventId=$eventId, eventType=$eventType"
            )
        if (attempt <= 0) {
            throw IntegrationEventEnvelopeDecodingException(
                "Invalid Integration Event deliveryAttempt for eventId=$eventId, eventType=$eventType"
            )
        }
        return attempt
    }

    private fun requiredText(
        root: JsonNode,
        name: String,
        eventId: String? = null,
        eventType: String? = null,
    ): String {
        val value = root.get(name)?.takeIf { it.isTextual }?.textValue()?.takeIf { it.isNotBlank() }
            ?: throw IntegrationEventEnvelopeDecodingException(
                "Missing or blank Integration Event $name" +
                    eventId?.let { ", eventId=$it" }.orEmpty() +
                    eventType?.let { ", eventType=$it" }.orEmpty()
            )
        return value
    }

    private data class WireEnvelope(
        val eventId: String,
        val eventType: String,
        val originService: String,
        val publishedAt: String,
        val deliveryAttempt: Int?,
        val executionContext: List<EncodedExecutionContextElement>,
        val payloadJson: String,
    )
}

class IntegrationEventEnvelopeEncodingException(message: String) : IllegalArgumentException(message)

class IntegrationEventEnvelopeDecodingException(message: String) : IllegalArgumentException(message)
