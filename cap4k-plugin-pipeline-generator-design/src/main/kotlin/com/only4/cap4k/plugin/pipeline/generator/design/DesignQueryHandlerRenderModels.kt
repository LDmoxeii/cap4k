package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.QueryModel

internal data class DesignQueryHandlerResponseFieldModel(
    val name: String,
)

internal data class DesignQueryHandlerRenderModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val queryTypeName: String,
    val imports: List<String>,
    val responseFields: List<DesignQueryHandlerResponseFieldModel>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "typeName" to typeName,
        "description" to description,
        "queryTypeName" to queryTypeName,
        "imports" to imports,
        "responseFields" to responseFields,
    )
}

internal object DesignQueryHandlerRenderModelFactory {
    fun create(basePackage: String, query: QueryModel): DesignQueryHandlerRenderModel {
        return DesignQueryHandlerRenderModel(
            packageName = "$basePackage.adapter.queries.${query.packageName}",
            typeName = "${query.typeName}Handler",
            description = query.description,
            queryTypeName = query.typeName,
            imports = listOf("$basePackage.application.queries.${query.packageName}.${query.typeName}"),
            responseFields = query.responseFields
                .asSequence()
                .filterNot { it.name.contains('.') }
                .map { DesignQueryHandlerResponseFieldModel(it.name) }
                .toList(),
        )
    }
}
