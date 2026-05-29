package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel

internal fun DesignBlockModel.buildingBlockContext(
    family: String,
    variant: String = selection(family)?.variant.orEmpty(),
): Map<String, Any?> = mapOf(
    "tag" to tag,
    "name" to name,
    "packageName" to packageName,
    "description" to description,
    "descriptionKotlinStringLiteral" to description.toKotlinStringLiteral(),
    "aggregates" to aggregates,
    "eventName" to eventName,
    "family" to family,
    "variant" to variant,
)
