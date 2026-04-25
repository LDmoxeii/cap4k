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
    val queryTypeFqn: String,
    val imports: List<String>,
    val responseFields: List<DesignQueryHandlerResponseFieldModel>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "typeName" to typeName,
        "description" to description,
        "queryTypeName" to queryTypeName,
        "queryTypeFqn" to queryTypeFqn,
        "imports" to imports,
        "responseFields" to responseFields,
    )
}

internal object DesignQueryHandlerRenderModelFactory {
    fun create(packageName: String, queryType: String, query: QueryModel): DesignQueryHandlerRenderModel {
        return DesignQueryHandlerRenderModel(
            packageName = packageName,
            typeName = "${query.typeName}Handler",
            description = query.description,
            queryTypeName = query.typeName,
            queryTypeFqn = queryType,
            imports = listOf(queryType),
            responseFields = query.responseFields
                .asSequence()
                .filterNot { it.name.contains('.') }
                .map { DesignQueryHandlerResponseFieldModel(it.name) }
                .toList(),
        )
    }
}
