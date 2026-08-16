package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.SemanticArrayTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinType
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticListTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticMapTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticSetTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition
import com.only4.cap4k.plugin.pipeline.api.SemanticValueEnvelope
import com.only4.cap4k.plugin.pipeline.api.SemanticValueField

/**
 * Projects already-resolved semantic values into template render models.
 *
 * Type parsing, nested-path compilation, PageData recognition, short-name resolution, and default
 * compilation belong to pipeline-core. This layer only chooses imports versus explicit FQNs.
 */
internal object DesignPayloadRenderModelFactory {
    fun createForCommandBlock(
        packageName: String,
        block: DesignBlockModel,
    ): DesignRenderModel = createForBlock(
        packageName = packageName,
        typeName = block.commandTypeName(),
        description = block.description,
        request = block.requireRequest(),
        response = block.response,
    )

    fun createForQueryBlock(
        packageName: String,
        block: DesignBlockModel,
        pageRequest: Boolean,
    ): DesignRenderModel = createForBlock(
        packageName = packageName,
        typeName = block.queryTypeName(),
        description = block.description,
        request = block.requireRequest(),
        response = block.response,
        pageRequest = pageRequest,
    )

    fun createForCapabilityBlock(
        packageName: String,
        block: DesignBlockModel,
    ): DesignRenderModel = createForBlock(
        packageName = packageName,
        typeName = block.capabilityTypeName(),
        description = block.description,
        request = block.requireRequest(),
        response = block.response,
    )

    fun createForEndpointBlock(packageName: String, block: DesignBlockModel): DesignRenderModel = createForBlock(
        packageName, block.endpointTypeName(), block.description, block.requireRequest(), block.response
    )

    fun createForDomainEventBlock(
        packageName: String,
        block: DesignBlockModel,
    ): DesignRenderModel = createForBlock(
        packageName = packageName,
        typeName = block.domainEventTypeName(),
        description = block.description,
        request = block.requireRequest(),
        response = null,
    )

    fun createForIntegrationEventBlock(
        packageName: String,
        block: DesignBlockModel,
    ): DesignRenderModel = createForBlock(
        packageName = packageName,
        typeName = block.integrationEventTypeName(),
        description = block.description,
        request = block.requireRequest(),
        response = null,
    )

    private fun createForBlock(
        packageName: String,
        typeName: String,
        description: String,
        request: SemanticValueDefinition,
        response: SemanticValueDefinition?,
        pageRequest: Boolean = false,
    ): DesignRenderModel {
        val definitions = listOfNotNull(request, response)
        val renderer = SemanticTypeRenderer(
            packageName = packageName,
            definitions = definitions,
            outerDeclarationName = typeName,
        )
        val requestProjection = renderer.project(request)
        val responseProjection = response?.let(renderer::project) ?: RenderProjection.empty()
        return DesignRenderModel(
            packageName = packageName,
            typeName = typeName,
            description = description,
            descriptionText = description,
            descriptionCommentText = description.toKDocCommentText(),
            descriptionKotlinStringLiteral = description.toKotlinStringLiteral(),
            imports = (requestProjection.imports + responseProjection.imports).distinct().sorted(),
            fields = requestProjection.fields,
            resultFields = responseProjection.fields,
            nestedTypes = requestProjection.nestedTypes,
            resultNestedTypes = responseProjection.nestedTypes,
            pageRequest = pageRequest,
        )
    }
}

private fun DesignBlockModel.requireRequest(): SemanticValueDefinition =
    requireNotNull(request) { "design block $tag $name is missing its canonical request" }

private class SemanticTypeRenderer(
    private val packageName: String,
    definitions: List<SemanticValueDefinition>,
    outerDeclarationName: String,
) {
    private val nestedDefinitions = definitions.flatMap { definition ->
        flattenNestedDefinitions(definition) +
            ((definition.envelope as? SemanticValueEnvelope.Page)?.itemDefinition?.let(::flattenEnvelopeDefinitions)
                ?: emptyList())
    }
    private val localIdentities = (nestedDefinitions.map { it.identity.fqn } + "$packageName.$outerDeclarationName").toSet()
    private val localSimpleNames = (nestedDefinitions.map { it.identity.simpleName } + outerDeclarationName).toSet()
    private val namedSymbols = definitions.flatMap(::collectNamedSymbols)
    private val collidingSimpleNames = namedSymbols
        .groupBy { it.simpleName }
        .filterValues { identities -> identities.map { it.fqn }.distinct().size > 1 }
        .keys + localSimpleNames

    fun project(definition: SemanticValueDefinition): RenderProjection {
        val imports = linkedSetOf<String>()
        val fields = definition.fields.map { field -> renderField(field, imports) }.toMutableList()
        val nestedTypes = flattenNestedDefinitions(definition).map { nested ->
            DesignRenderNestedTypeModel(
                name = nested.identity.simpleName,
                fields = nested.fields.map { field -> renderField(field, imports) },
            )
        }.toMutableList()
        when (val envelope = definition.envelope) {
            null -> Unit
            is SemanticValueEnvelope.Page -> {
                imports += PAGE_DATA_FQN
                val itemName = envelope.itemDefinition.identity.simpleName
                fields += DesignRenderFieldModel(
                    name = "page",
                    renderedType = "PageData<$itemName>",
                )
                flattenEnvelopeDefinitions(envelope.itemDefinition).forEach { nested ->
                    nestedTypes += DesignRenderNestedTypeModel(
                        name = nested.identity.simpleName,
                        fields = nested.fields.map { field -> renderField(field, imports) },
                    )
                }
            }
        }
        nestedTypes
            .groupBy { it.name }
            .entries
            .firstOrNull { (_, definitions) -> definitions.size > 1 }
            ?.let { (name, _) ->
                throw IllegalArgumentException(
                    "semantic value ${definition.identity.fqn} has colliding flattened nested type $name",
                )
            }
        return RenderProjection(
            fields = fields,
            nestedTypes = nestedTypes,
            imports = imports.toList(),
        )
    }

    private fun renderField(
        field: SemanticValueField,
        imports: MutableSet<String>,
    ): DesignRenderFieldModel = DesignRenderFieldModel(
        name = field.name,
        renderedType = renderType(field.type, imports),
        nullable = field.type.nullable,
        defaultValue = field.defaultValue?.kotlinExpression,
    )

    private fun renderType(
        type: SemanticTypeRef,
        imports: MutableSet<String>,
    ): String {
        val rendered = when (type) {
            is SemanticBuiltinTypeRef -> builtinTypeName(type.kind)
            is SemanticNamedTypeRef -> renderNamed(type.symbol, imports)
            is SemanticListTypeRef -> "List<${renderType(type.elementType, imports)}>"
            is SemanticSetTypeRef -> "Set<${renderType(type.elementType, imports)}>"
            is SemanticArrayTypeRef -> "Array<${renderType(type.elementType, imports)}>"
            is SemanticMapTypeRef -> "Map<${renderType(type.keyType, imports)}, ${renderType(type.valueType, imports)}>"
        }
        return rendered + if (type.nullable) "?" else ""
    }

    private fun renderNamed(
        identity: CanonicalTypeIdentity,
        imports: MutableSet<String>,
    ): String {
        if (identity.fqn in localIdentities) return identity.simpleName
        if (identity.simpleName in collidingSimpleNames) return identity.fqn
        if (identity.packageName == packageName && identity.typePath.size == 1) return identity.simpleName
        imports += identity.fqn
        return identity.simpleName
    }

    private fun builtinTypeName(type: SemanticBuiltinType): String = when (type) {
        SemanticBuiltinType.ANY -> "Any"
        SemanticBuiltinType.BOOLEAN -> "Boolean"
        SemanticBuiltinType.BYTE -> "Byte"
        SemanticBuiltinType.CHAR -> "Char"
        SemanticBuiltinType.DOUBLE -> "Double"
        SemanticBuiltinType.FLOAT -> "Float"
        SemanticBuiltinType.INT -> "Int"
        SemanticBuiltinType.LONG -> "Long"
        SemanticBuiltinType.NOTHING -> "Nothing"
        SemanticBuiltinType.NUMBER -> "Number"
        SemanticBuiltinType.SHORT -> "Short"
        SemanticBuiltinType.STRING -> "String"
        SemanticBuiltinType.UNIT -> "Unit"
    }

    private fun collectNamedSymbols(definition: SemanticValueDefinition): List<CanonicalTypeIdentity> = buildList {
        definition.fields.forEach { field -> addAll(collectNamedSymbols(field.type)) }
        definition.nestedDefinitions.forEach { nested -> addAll(collectNamedSymbols(nested)) }
        (definition.envelope as? SemanticValueEnvelope.Page)?.itemDefinition?.let { item ->
            addAll(collectNamedSymbols(item))
        }
    }

    private fun collectNamedSymbols(type: SemanticTypeRef): List<CanonicalTypeIdentity> = when (type) {
        is SemanticBuiltinTypeRef -> emptyList()
        is SemanticNamedTypeRef -> listOf(type.symbol)
        is SemanticListTypeRef -> collectNamedSymbols(type.elementType)
        is SemanticSetTypeRef -> collectNamedSymbols(type.elementType)
        is SemanticArrayTypeRef -> collectNamedSymbols(type.elementType)
        is SemanticMapTypeRef -> collectNamedSymbols(type.keyType) + collectNamedSymbols(type.valueType)
    }

    private fun flattenNestedDefinitions(definition: SemanticValueDefinition): List<SemanticValueDefinition> =
        definition.nestedDefinitions.flatMap { nested -> listOf(nested) + flattenNestedDefinitions(nested) }

    private fun flattenEnvelopeDefinitions(item: SemanticValueDefinition): List<SemanticValueDefinition> =
        listOf(item) + flattenNestedDefinitions(item)
}

private data class RenderProjection(
    val fields: List<DesignRenderFieldModel>,
    val nestedTypes: List<DesignRenderNestedTypeModel>,
    val imports: List<String>,
) {
    companion object {
        fun empty(): RenderProjection = RenderProjection(emptyList(), emptyList(), emptyList())
    }
}

private const val PAGE_DATA_FQN = "com.only4.cap4k.ddd.core.share.PageData"
