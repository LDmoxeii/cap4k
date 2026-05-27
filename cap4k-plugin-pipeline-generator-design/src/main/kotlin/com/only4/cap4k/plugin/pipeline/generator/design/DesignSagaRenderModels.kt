package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.SagaModel

internal data class DesignSagaRenderModel(
    val packageName: String,
    val name: String,
    val description: String?,
    val requestFields: List<DesignRenderFieldModel>,
    val responseFields: List<DesignRenderFieldModel>,
    val imports: List<String>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "name" to name,
        "description" to description,
        "requestFields" to requestFields,
        "responseFields" to responseFields,
        "imports" to imports,
    )
}

internal object DesignSagaRenderModelFactory {
    fun create(
        packageName: String,
        saga: SagaModel,
        typeRegistry: Map<String, String> = emptyMap(),
    ): DesignSagaRenderModel {
        val renderModel = DesignPayloadRenderModelFactory.createForSaga(
            packageName = packageName,
            saga = saga,
            typeRegistry = typeRegistry,
        )
        return DesignSagaRenderModel(
            packageName = renderModel.packageName,
            name = renderModel.typeName,
            description = saga.description,
            requestFields = renderModel.requestFields,
            responseFields = renderModel.responseFields,
            imports = renderModel.imports,
        )
    }
}
