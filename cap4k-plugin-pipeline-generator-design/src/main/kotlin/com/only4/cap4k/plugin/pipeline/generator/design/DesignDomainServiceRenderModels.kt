package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel

internal data class DesignDomainServiceRenderModel(
    val packageName: String,
    val name: String,
    val description: String?,
    val aggregates: List<String>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "name" to name,
        "description" to description,
        "aggregates" to aggregates,
    )
}

internal object DesignDomainServiceRenderModelFactory {
    fun create(packageName: String, block: DesignBlockModel): DesignDomainServiceRenderModel =
        DesignDomainServiceRenderModel(
            packageName = packageName,
            name = block.name,
            description = block.description,
            aggregates = block.aggregates,
        )
}
