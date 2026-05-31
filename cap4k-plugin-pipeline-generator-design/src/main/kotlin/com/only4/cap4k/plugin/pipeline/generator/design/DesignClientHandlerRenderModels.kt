package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel

internal data class DesignClientHandlerRenderModel(
    val packageName: String,
    val typeName: String,
    val clientTypeName: String,
    val imports: List<String>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "typeName" to typeName,
        "clientTypeName" to clientTypeName,
        "imports" to imports,
    )
}

internal object DesignClientHandlerRenderModelFactory {
    fun create(packageName: String, clientType: String, block: DesignBlockModel): DesignClientHandlerRenderModel {
        val clientTypeName = block.clientTypeName()
        return DesignClientHandlerRenderModel(
            packageName = packageName,
            typeName = "${clientTypeName}Handler",
            clientTypeName = clientTypeName,
            imports = listOf(clientType),
        )
    }
}
