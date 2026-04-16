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
    val descriptionKotlinStringLiteral: String,
    val aggregateName: String?,
    val imports: List<String>,
    val requestFields: List<DesignRenderFieldModel>,
    val responseFields: List<DesignRenderFieldModel>,
    val requestNestedTypes: List<DesignRenderNestedTypeModel>,
    val responseNestedTypes: List<DesignRenderNestedTypeModel>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "typeName" to typeName,
        "description" to description,
        "descriptionText" to descriptionText,
        "descriptionKotlinStringLiteral" to descriptionKotlinStringLiteral,
        "aggregateName" to aggregateName,
        "imports" to imports,
        "requestFields" to requestFields,
        "responseFields" to responseFields,
        "requestNestedTypes" to requestNestedTypes,
        "responseNestedTypes" to responseNestedTypes,
    )
}
