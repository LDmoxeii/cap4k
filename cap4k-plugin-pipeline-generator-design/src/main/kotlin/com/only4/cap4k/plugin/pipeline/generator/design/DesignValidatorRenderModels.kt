package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ValidatorModel

internal data class DesignValidatorRenderModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val valueType: String,
    val imports: List<String>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "typeName" to typeName,
        "description" to description,
        "valueType" to valueType,
        "imports" to imports,
    )
}

internal object DesignValidatorRenderModelFactory {
    fun create(basePackage: String, validator: ValidatorModel): DesignValidatorRenderModel {
        return DesignValidatorRenderModel(
            packageName = "$basePackage.application.validators.${validator.packageName}",
            typeName = validator.typeName,
            description = validator.description,
            valueType = validator.valueType,
            imports = emptyList(),
        )
    }
}
