package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ValidatorModel
import com.only4.cap4k.plugin.pipeline.generator.design.types.DesignSymbolRegistry
import com.only4.cap4k.plugin.pipeline.generator.design.types.ImportResolver
import com.only4.cap4k.plugin.pipeline.generator.design.types.ImportResolver.UnknownShortTypeFailure
import com.only4.cap4k.plugin.pipeline.generator.design.types.SymbolIdentity

internal data class DesignValidatorRenderModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val descriptionCommentText: String,
    val message: String,
    val messageLiteral: String,
    val targets: List<String>,
    val valueType: String,
    val parameters: List<DesignValidatorParameterRenderModel>,
    val imports: List<String>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "typeName" to typeName,
        "description" to description,
        "descriptionCommentText" to descriptionCommentText,
        "message" to message,
        "messageLiteral" to messageLiteral,
        "targets" to targets,
        "valueType" to valueType,
        "parameters" to parameters,
        "imports" to imports,
    )
}

internal data class DesignValidatorParameterRenderModel(
    val name: String,
    val type: String,
    val defaultValueLiteral: String?,
)

internal object DesignValidatorRenderModelFactory {
    private val builtInTypeNames = setOf(
        "Any",
        "Array",
        "Boolean",
        "Byte",
        "Char",
        "Collection",
        "Double",
        "Float",
        "Int",
        "Iterable",
        "List",
        "Long",
        "Map",
        "MutableCollection",
        "MutableIterable",
        "MutableList",
        "MutableMap",
        "MutableSet",
        "Nothing",
        "Number",
        "Pair",
        "Sequence",
        "Set",
        "Short",
        "String",
        "Triple",
        "Unit",
    )

    fun create(
        packageName: String,
        validator: ValidatorModel,
        typeRegistry: Map<String, String> = emptyMap(),
    ): DesignValidatorRenderModel {
        val symbolRegistry = typeRegistry.toSymbolRegistry()
        val valueType = resolveType(validator.valueType, symbolRegistry)
        val parameters = validator.parameters.map { parameter ->
            val parameterType = resolveType(parameter.type, symbolRegistry)
            ValidatorParameterResolution(
                renderModel = DesignValidatorParameterRenderModel(
                    name = parameter.name,
                    type = parameterType.renderedText,
                    defaultValueLiteral = parameter.defaultValue?.let { value ->
                        renderDefaultValue(parameterType.renderedText, value)
                    },
                ),
                imports = parameterType.imports,
            )
        }

        return DesignValidatorRenderModel(
            packageName = packageName,
            typeName = validator.typeName,
            description = validator.description,
            descriptionCommentText = validator.description.toKDocCommentText(),
            message = validator.message,
            messageLiteral = validator.message.toKotlinStringLiteral(),
            targets = validator.targets,
            valueType = valueType.renderedText,
            parameters = parameters.map { it.renderModel },
            imports = (valueType.imports + parameters.flatMap { it.imports }).distinct().sorted(),
        )
    }

    private fun Map<String, String>.toSymbolRegistry(): DesignSymbolRegistry {
        val registry = DesignSymbolRegistry()
        forEach { (simpleName, fqcn) ->
            registry.register(
                SymbolIdentity(
                    packageName = fqcn.substringBeforeLast('.', missingDelimiterValue = ""),
                    typeName = simpleName,
                    source = "project-type-registry",
                ),
            )
        }
        return registry
    }

    private fun resolveType(type: String, symbolRegistry: DesignSymbolRegistry): ValidatorTypeResolution {
        val resolvedType = resolveDesignType(DesignTypeParser.parse(type))
        return try {
            val plan = ImportResolver.plan(
                types = listOf(resolvedType),
                symbolRegistry = symbolRegistry,
            )
            ValidatorTypeResolution(
                renderedText = plan.renderedTypes.single().renderedText,
                imports = plan.imports,
            )
        } catch (ex: UnknownShortTypeFailure) {
            ValidatorTypeResolution(renderedText = type, imports = emptyList())
        }
    }

    private fun resolveDesignType(type: DesignTypeModel): DesignResolvedTypeModel {
        val rawText = type.tokenText
        val simpleName = rawText.substringAfterLast('.')
        val kind = when {
            rawText in builtInTypeNames -> DesignResolvedTypeKind.BUILTIN
            rawText.contains('.') -> DesignResolvedTypeKind.EXPLICIT_FQCN
            else -> DesignResolvedTypeKind.UNRESOLVED
        }

        return DesignResolvedTypeModel(
            kind = kind,
            rawText = rawText,
            simpleName = simpleName,
            nullable = type.nullable,
            arguments = type.arguments.map(::resolveDesignType),
            importCandidates = when (kind) {
                DesignResolvedTypeKind.EXPLICIT_FQCN -> setOf(rawText)
                else -> emptySet()
            },
        )
    }

    private fun renderDefaultValue(type: String, value: String): String =
        when (type) {
            "String" -> value.toKotlinStringLiteral()
            "Int" -> value.toIntLiteral()
            "Long" -> value.toLongLiteral()
            "Boolean" -> value.toBooleanLiteral()
            else -> error("unsupported validator parameter type: $type")
        }

    private fun String.toIntLiteral(): String {
        val value = trim()
        require(intLiteralPattern.matches(value) && value.toIntOrNull() != null) {
            "invalid validator parameter Int defaultValue: $this"
        }
        return value
    }

    private fun String.toLongLiteral(): String {
        val value = trim()
        require(longLiteralPattern.matches(value)) {
            "invalid validator parameter Long defaultValue: $this"
        }
        val numeric = value.removeSuffix("l").removeSuffix("L")
        require(numeric.toLongOrNull() != null) {
            "invalid validator parameter Long defaultValue: $this"
        }
        return numeric + "L"
    }

    private fun String.toBooleanLiteral(): String {
        val value = trim()
        require(value == "true" || value == "false") {
            "invalid validator parameter Boolean defaultValue: $this"
        }
        return value
    }

    private fun String.toKotlinStringLiteral(): String = buildString {
        append('"')
        this@toKotlinStringLiteral.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '$' -> {
                    append('\\')
                    append('$')
                }
                else -> {
                    if (character.code in 0x00..0x1F) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
        append('"')
    }

    private val intLiteralPattern = Regex("""-?\d+""")
    private val longLiteralPattern = Regex("""-?\d+[lL]?""")

    private data class ValidatorTypeResolution(
        val renderedText: String,
        val imports: List<String>,
    )

    private data class ValidatorParameterResolution(
        val renderModel: DesignValidatorParameterRenderModel,
        val imports: List<String>,
    )
}
