package com.only4.cap4k.plugin.pipeline.generator.design

internal object DefaultValueFormatter {
    private val supportedEmptyCollections = setOf(
        "emptyList()",
        "emptySet()",
        "mutableListOf()",
        "mutableSetOf()",
    )

    private val constantExpressionPattern = Regex(
        """(?:[A-Za-z_][A-Za-z0-9_]*\.)+[A-Z][A-Za-z0-9_]*""",
    )

    private val longLiteralPattern = Regex("""-?\d+[lL]?""")

    fun format(
        rawDefaultValue: String?,
        renderedType: String,
        nullable: Boolean,
        fieldName: String,
    ): String? {
        val value = rawDefaultValue?.trim().orEmpty()
        if (value.isBlank()) {
            return null
        }
        if (value == "null") {
            require(nullable) {
                "invalid default value for field $fieldName: null is only allowed for nullable fields"
            }
            return value
        }

        if (value in supportedEmptyCollections || isConstantExpression(value)) {
            return value
        }

        return when (renderedType.removeSuffix("?").trim()) {
            "String" -> normalizeString(value)
            "Long" -> normalizeLong(value, fieldName)
            "Boolean" -> normalizeBoolean(value, fieldName)
            else -> normalizeExpression(value, fieldName)
        }
    }

    private fun normalizeString(value: String): String {
        if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
            return value
        }
        return buildString {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    else -> append(ch)
                }
            }
            append('"')
        }
    }

    private fun normalizeLong(value: String, fieldName: String): String {
        require(longLiteralPattern.matches(value)) {
            "invalid default value for field $fieldName: $value is not a valid Long literal"
        }
        return if (value.endsWith("L")) value else value.removeSuffix("l") + "L"
    }

    private fun normalizeBoolean(value: String, fieldName: String): String {
        require(value == "true" || value == "false") {
            "invalid default value for field $fieldName: Boolean defaults must be true or false"
        }
        return value
    }

    private fun normalizeExpression(value: String, fieldName: String): String {
        throw IllegalArgumentException("invalid default value for field $fieldName: unsupported default value expression: $value")
    }

    private fun isConstantExpression(value: String): Boolean = constantExpressionPattern.matches(value)
}
