package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ClientModel

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
    fun create(packageName: String, clientType: String, client: ClientModel): DesignClientHandlerRenderModel {
        return DesignClientHandlerRenderModel(
            packageName = packageName,
            typeName = "${client.typeName}Handler",
            clientTypeName = client.typeName,
            imports = listOf(clientType),
        )
    }
}
