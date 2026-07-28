package com.only4.cap4k.ddd.core.domain.event

/** Event payload types discovered from the active application model without classpath scanning. */
interface EventTypeCatalog {
    fun integrationEventTypes(): Set<Class<*>>
}
