package com.only4.cap4k.ddd.core.domain.event

import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent

/** A stable-name projection over Integration Event payload types. */
interface IntegrationEventTypeView {
    fun integrationEventTypes(): Set<Class<*>>

    /**
     * Resolves the unique local payload type for each stable event name.
     * Duplicate names mapped to different payload classes are rejected instead
     * of allowing provider-specific last-write-wins behavior.
     */
    fun integrationEventTypesByName(): Map<String, Class<*>> {
        val result = linkedMapOf<String, Class<*>>()
        integrationEventTypes()
            .sortedBy { it.name }
            .forEach { payloadType ->
                val annotation = requireNotNull(payloadType.getAnnotation(IntegrationEvent::class.java)) {
                    "Integration Event payload '${payloadType.name}' must declare @IntegrationEvent"
                }
                val eventName = annotation.value
                require(eventName.isNotBlank()) {
                    "Integration Event payload '${payloadType.name}' must declare a non-blank event name"
                }
                val previous = result.putIfAbsent(eventName, payloadType)
                check(previous == null || previous == payloadType) {
                    "Integration Event '$eventName' resolves to multiple payload types: " +
                        "${previous?.name}, ${payloadType.name}"
                }
            }
        return result
    }
}

/** Event payload types declared by the active application model without classpath scanning. */
interface EventTypeCatalog : IntegrationEventTypeView

/**
 * Catalog payload types that also have at least one valid local synchronous Integration Event Handler.
 * Transport providers use this view as their only inbound registration source.
 */
interface InboundIntegrationEventRegistrationView : IntegrationEventTypeView
