package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel

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
    val resultFields: List<DesignQueryHandlerResponseFieldModel>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "typeName" to typeName,
        "description" to description,
        "queryTypeName" to queryTypeName,
        "queryTypeFqn" to queryTypeFqn,
        "imports" to imports,
        "resultFields" to resultFields,
    )
}

internal object DesignQueryHandlerRenderModelFactory {
    fun create(packageName: String, queryType: String, block: DesignBlockModel): DesignQueryHandlerRenderModel {
        val queryTypeName = block.queryTypeName()
        return DesignQueryHandlerRenderModel(
            packageName = packageName,
            typeName = "${queryTypeName}Handler",
            description = block.description,
            queryTypeName = queryTypeName,
            queryTypeFqn = queryType,
            imports = listOf(queryType),
            resultFields = block.resultFields
                .asSequence()
                .filterNot { it.name.contains('.') }
                .map { DesignQueryHandlerResponseFieldModel(it.name) }
                .toList(),
        )
    }
}
