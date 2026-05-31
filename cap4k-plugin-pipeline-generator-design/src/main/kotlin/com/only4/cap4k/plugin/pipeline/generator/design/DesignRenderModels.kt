package com.only4.cap4k.plugin.pipeline.generator.design

internal data class DesignRenderFieldModel(
    val name: String,
    val renderedType: String,
    val nullable: Boolean = false,
    // Kotlin-ready right-hand-side expression, not raw design input.
    val defaultValue: String? = null,
) {
    val type: String
        get() = renderedType
}

internal data class DesignRenderNestedTypeModel(
    val name: String,
    val fields: List<DesignRenderFieldModel>,
)

internal data class DesignRenderModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val descriptionText: String,
    val descriptionCommentText: String,
    val descriptionKotlinStringLiteral: String,
    val aggregateName: String?,
    val imports: List<String>,
    val fields: List<DesignRenderFieldModel>,
    val resultFields: List<DesignRenderFieldModel>,
    val nestedTypes: List<DesignRenderNestedTypeModel>,
    val resultNestedTypes: List<DesignRenderNestedTypeModel>,
    val pageRequest: Boolean = false,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "typeName" to typeName,
        "description" to description,
        "descriptionText" to descriptionText,
        "descriptionCommentText" to descriptionCommentText,
        "descriptionKotlinStringLiteral" to descriptionKotlinStringLiteral,
        "aggregateName" to aggregateName,
        "imports" to imports,
        "fields" to fields,
        "resultFields" to resultFields,
        "nestedTypes" to nestedTypes,
        "resultNestedTypes" to resultNestedTypes,
        "pageRequest" to pageRequest,
    )
}
