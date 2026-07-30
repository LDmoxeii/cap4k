package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel

internal data class DesignCapabilityHandlerRenderModel(
    val packageName: String,
    val typeName: String,
    val capabilityTypeName: String,
    val imports: List<String>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "typeName" to typeName,
        "capabilityTypeName" to capabilityTypeName,
        "imports" to imports,
    )
}

internal object DesignCapabilityHandlerRenderModelFactory {
    fun create(packageName: String, capabilityType: String, block: DesignBlockModel): DesignCapabilityHandlerRenderModel {
        val capabilityTypeName = block.capabilityTypeName()
        return DesignCapabilityHandlerRenderModel(
            packageName = packageName,
            typeName = "${capabilityTypeName}Handler",
            capabilityTypeName = capabilityTypeName,
            imports = listOf(capabilityType),
        )
    }
}
