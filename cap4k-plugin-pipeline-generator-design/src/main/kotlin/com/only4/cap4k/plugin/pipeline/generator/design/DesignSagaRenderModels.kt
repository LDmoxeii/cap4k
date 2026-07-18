package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.generator.design.types.DesignSymbolRegistry

internal data class DesignSagaRenderModel(
    val packageName: String,
    val name: String,
    val description: String?,
    val fields: List<DesignRenderFieldModel>,
    val resultFields: List<DesignRenderFieldModel>,
    val imports: List<String>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "name" to name,
        "description" to description,
        "fields" to fields,
        "resultFields" to resultFields,
        "imports" to imports,
    )
}

internal object DesignSagaRenderModelFactory {
    fun create(
        packageName: String,
        block: DesignBlockModel,
        symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
    ): DesignSagaRenderModel {
        val renderModel = DesignPayloadRenderModelFactory.createForSagaBlock(
            packageName = packageName,
            block = block,
            symbolRegistry = symbolRegistry,
        )
        return DesignSagaRenderModel(
            packageName = renderModel.packageName,
            name = renderModel.typeName,
            description = block.description,
            fields = renderModel.fields,
            resultFields = renderModel.resultFields,
            imports = renderModel.imports,
        )
    }
}
