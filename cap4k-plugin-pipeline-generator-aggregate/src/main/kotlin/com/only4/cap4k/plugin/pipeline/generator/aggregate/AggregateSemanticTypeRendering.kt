package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity
import com.only4.cap4k.plugin.pipeline.api.SemanticArrayTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinType
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticListTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticMapTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticSetTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition
import com.only4.cap4k.plugin.pipeline.api.SemanticValueField

internal data class AggregateSemanticRenderedType(
    val renderedType: String,
    val imports: List<String>,
)

/**
 * Renders already-resolved canonical type trees. This helper deliberately does not parse type strings or resolve
 * short names; [SemanticNamedTypeRef.symbol] is the only authority for named types.
 */
internal class AggregateSemanticTypeRenderer(
    private val currentPackage: String,
    definitions: List<SemanticValueDefinition>,
) {
    private val collidingNamedTypeFqns: Set<String> = definitions
        .flatMap { definition -> definition.fields.flatMap { field -> field.type.namedSymbols() } }
        .groupBy(CanonicalTypeIdentity::simpleName)
        .values
        .filter { identities -> identities.map(CanonicalTypeIdentity::fqn).distinct().size > 1 }
        .flatten()
        .map(CanonicalTypeIdentity::fqn)
        .toSet()

    fun render(field: SemanticValueField): Map<String, Any?> {
        val rendered = render(field.type)
        return mapOf(
            "name" to field.name,
            "renderedType" to rendered.renderedType,
            "typeImports" to rendered.imports,
            "defaultValue" to field.defaultValue?.kotlinExpression,
            "sourcePath" to field.sourcePath,
        )
    }

    fun render(type: SemanticTypeRef): AggregateSemanticRenderedType {
        val rendered = when (type) {
            is SemanticBuiltinTypeRef -> AggregateSemanticRenderedType(
                renderedType = type.kind.kotlinName,
                imports = emptyList(),
            )

            is SemanticNamedTypeRef -> renderNamed(type.symbol)

            is SemanticArrayTypeRef -> renderContainer("Array", listOf(type.elementType))
            is SemanticListTypeRef -> renderContainer("List", listOf(type.elementType))
            is SemanticSetTypeRef -> renderContainer("Set", listOf(type.elementType))
            is SemanticMapTypeRef -> renderContainer("Map", listOf(type.keyType, type.valueType))
        }
        return if (type.nullable) {
            rendered.copy(renderedType = "${rendered.renderedType}?")
        } else {
            rendered
        }
    }

    private fun renderContainer(
        containerName: String,
        arguments: List<SemanticTypeRef>,
    ): AggregateSemanticRenderedType {
        val renderedArguments = arguments.map(::render)
        return AggregateSemanticRenderedType(
            renderedType = "$containerName<${renderedArguments.joinToString(", ") { it.renderedType }}>",
            imports = renderedArguments.flatMap(AggregateSemanticRenderedType::imports).distinct(),
        )
    }

    private fun renderNamed(identity: CanonicalTypeIdentity): AggregateSemanticRenderedType {
        if (identity.fqn in collidingNamedTypeFqns) {
            return AggregateSemanticRenderedType(identity.fqn, emptyList())
        }
        if (identity.packageName == currentPackage) {
            return AggregateSemanticRenderedType(identity.typePath.joinToString("."), emptyList())
        }
        if (identity.packageName == "kotlin" || identity.packageName == "java.lang") {
            return AggregateSemanticRenderedType(identity.typePath.joinToString("."), emptyList())
        }
        return AggregateSemanticRenderedType(
            renderedType = identity.simpleName,
            imports = listOf(identity.fqn),
        )
    }
}

internal fun SemanticTypeRef.namedSymbols(): List<CanonicalTypeIdentity> = when (this) {
    is SemanticBuiltinTypeRef -> emptyList()
    is SemanticNamedTypeRef -> listOf(symbol)
    is SemanticArrayTypeRef -> elementType.namedSymbols()
    is SemanticListTypeRef -> elementType.namedSymbols()
    is SemanticSetTypeRef -> elementType.namedSymbols()
    is SemanticMapTypeRef -> keyType.namedSymbols() + valueType.namedSymbols()
}

private val SemanticBuiltinType.kotlinName: String
    get() = when (this) {
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
