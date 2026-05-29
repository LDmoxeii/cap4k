package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.DomainEventModel

internal fun domainEventBuildingBlockContext(
    event: DomainEventModel,
    packageName: String,
): Map<String, Any?> = mapOf(
    "tag" to "domain_event",
    "name" to event.typeName,
    "packageName" to packageName,
    "description" to event.description,
    "aggregates" to listOf(event.aggregateName),
    "eventName" to "",
    "family" to "domain-event",
    "variant" to "",
)
