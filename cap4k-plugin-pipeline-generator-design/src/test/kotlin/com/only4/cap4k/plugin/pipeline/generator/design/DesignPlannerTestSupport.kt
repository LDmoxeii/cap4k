package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel

internal fun designBlock(
    tag: String,
    family: String,
    variant: String = "",
    packageName: String = "order",
    name: String,
    description: String = name,
    aggregates: List<String> = emptyList(),
    eventName: String = "",
    persist: Boolean? = null,
    fields: List<FieldModel> = emptyList(),
    resultFields: List<FieldModel> = emptyList(),
): DesignBlockModel = DesignBlockModel(
    tag = tag,
    packageName = packageName,
    name = name,
    description = description,
    aggregates = aggregates,
    eventName = eventName,
    persist = persist,
    artifacts = listOf(ArtifactSelectionModel(family, variant)),
    fields = fields,
    resultFields = resultFields,
)

internal fun queryBlock(
    packageName: String = "order.read",
    family: String = "query",
    variant: String = "",
    name: String = "FindOrder",
    fields: List<FieldModel> = emptyList(),
    resultFields: List<FieldModel> = emptyList(),
): DesignBlockModel = designBlock(
    tag = "query",
    family = family,
    variant = variant,
    packageName = packageName,
    name = name,
    description = "find order",
    aggregates = listOf("Order"),
    fields = fields,
    resultFields = resultFields,
)
