package com.only4.cap4k.ddd.core.application.event

import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.share.DomainException

/** Shared validation for the stable logical name of an Integration Event payload. */
object IntegrationEventPayloadValidator {
    @JvmStatic
    fun eventName(payload: Any): String = eventName(payload.javaClass)

    @JvmStatic
    fun eventName(payloadType: Class<*>): String {
        val annotation = payloadType.getAnnotation(IntegrationEvent::class.java)
            ?: throw DomainException(
                "Integration Event payload '${payloadType.name}' must declare @IntegrationEvent",
            )
        if (annotation.value.isBlank()) {
            throw DomainException(
                "Integration Event payload '${payloadType.name}' must declare a non-blank event name",
            )
        }
        return annotation.value
    }
}
