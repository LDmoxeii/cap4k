package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel

internal fun DesignBlockModel.buildingBlockContext(
    family: String,
    variant: String = selection(family)?.variant.orEmpty(),
): Map<String, Any?> = mapOf(
    "tag" to tag,
    "tagKotlinStringLiteral" to tag.toKotlinStringLiteral(),
    "name" to name,
    "nameKotlinStringLiteral" to name.toKotlinStringLiteral(),
    "packageName" to packageName,
    "packageNameKotlinStringLiteral" to packageName.toKotlinStringLiteral(),
    "description" to description,
    "descriptionKotlinStringLiteral" to description.toKotlinStringLiteral(),
    "aggregates" to aggregates,
    "aggregateKotlinStringLiterals" to aggregates.map { it.toKotlinStringLiteral() },
    "eventName" to eventName,
    "eventNameKotlinStringLiteral" to eventName.toKotlinStringLiteral(),
    "family" to family,
    "familyKotlinStringLiteral" to family.toKotlinStringLiteral(),
    "variant" to variant,
    "variantKotlinStringLiteral" to variant.toKotlinStringLiteral(),
)
