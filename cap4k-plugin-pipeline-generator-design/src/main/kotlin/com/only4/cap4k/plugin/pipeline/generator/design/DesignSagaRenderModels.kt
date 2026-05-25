package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.FieldModel
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
    fun create(saga: SagaModel): DesignSagaRenderModel {
        val requestTypes = saga.requestFields.map { renderType(it.type, it.nullable) }
        val responseTypes = saga.responseFields.map { renderType(it.type, it.nullable) }
        return DesignSagaRenderModel(
            packageName = saga.packageName,
            name = saga.name,
            description = saga.description,
            requestFields = saga.requestFields.zip(requestTypes).map { (field, type) ->
                field.toRenderField(type.renderedType)
            },
            responseFields = saga.responseFields.zip(responseTypes).map { (field, type) ->
                field.toRenderField(type.renderedType)
            },
            imports = (requestTypes + responseTypes).flatMap { it.imports }.distinct().sorted(),
        )
    }

    private fun FieldModel.toRenderField(renderedType: String): DesignRenderFieldModel =
        DesignRenderFieldModel(
            name = name,
            renderedType = renderedType,
            nullable = nullable,
            defaultValue = DefaultValueFormatter.format(
                rawDefaultValue = defaultValue,
                renderedType = renderedType,
                nullable = nullable,
                fieldName = name,
            ),
        )

    private fun renderType(rawType: String, nullable: Boolean): RenderedSagaType {
        val trimmed = rawType.trim()
        val rendered = if (trimmed.contains('.') && !trimmed.contains('<')) {
            trimmed.substringAfterLast('.')
        } else {
            trimmed
        }.let { type ->
            if (nullable && !type.endsWith("?")) "$type?" else type
        }
        val imports = if (trimmed.contains('.') && !trimmed.contains('<')) {
            listOf(trimmed.removeSuffix("?"))
        } else {
            emptyList()
        }
        return RenderedSagaType(renderedType = rendered, imports = imports)
    }

    private data class RenderedSagaType(
        val renderedType: String,
        val imports: List<String>,
    )
}
