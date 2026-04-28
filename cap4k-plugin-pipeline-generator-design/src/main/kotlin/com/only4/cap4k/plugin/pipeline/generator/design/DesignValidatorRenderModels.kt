package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ValidatorModel

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
    fun create(packageName: String, validator: ValidatorModel): DesignValidatorRenderModel {
        return DesignValidatorRenderModel(
            packageName = packageName,
            typeName = validator.typeName,
            description = validator.description,
            descriptionCommentText = validator.description.toKDocCommentText(),
            message = validator.message,
            messageLiteral = validator.message.toKotlinStringLiteral(),
            targets = validator.targets,
            valueType = validator.valueType,
            parameters = validator.parameters.map { parameter ->
                DesignValidatorParameterRenderModel(
                    name = parameter.name,
                    type = parameter.type,
                    defaultValueLiteral = parameter.defaultValue?.let { value ->
                        renderDefaultValue(parameter.type, value)
                    },
                )
            },
            imports = emptyList(),
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
}
