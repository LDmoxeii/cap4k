package com.only4.cap4k.ddd.core.domain.event.impl

class EventDispatchException(
    val eventPayloadClass: Class<*>,
    val diagnosticContext: EventRuntimeScope?,
    val failures: List<EventSubscriberFailure>,
) : RuntimeException(
    buildMessage(eventPayloadClass, failures),
    failures.firstOrNull()?.cause,
) {
    init {
        failures.drop(1).forEach { addSuppressed(it.cause) }
    }

    companion object {
        private fun buildMessage(
            eventPayloadClass: Class<*>,
            failures: List<EventSubscriberFailure>
        ): String {
            val subscriberClasses = failures.joinToString(", ") { it.subscriberClass.name }
            return "Event dispatch failed for ${eventPayloadClass.name}; failing subscribers: $subscriberClasses"
        }
    }
}
