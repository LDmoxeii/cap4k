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

    private val intLiteralPattern = Regex("""-?\d+""")
    private val longLiteralPattern = Regex("""-?\d+[lL]?""")
    private val doubleLiteralPattern = Regex("""-?(?:(?:\d+\.\d*|\.\d+)(?:[eE][+-]?\d+)?|\d+[eE][+-]?\d+)""")
    private val floatLiteralPattern = Regex("""-?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?[fF]""")

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

        val normalizedType = renderedType.removeSuffix("?").trim()
        if (normalizedType in builtInScalarTypes && isConstantExpression(value)) {
            throw IllegalArgumentException("invalid default value for field $fieldName: unsupported default value expression: $value")
        }

        return when (normalizedType) {
            "String" -> normalizeString(value)
            "Int" -> normalizeInt(value, fieldName)
            "Long" -> normalizeLong(value, fieldName)
            "Double" -> normalizeDouble(value, fieldName)
            "Float" -> normalizeFloat(value, fieldName)
            "Boolean" -> normalizeBoolean(value, fieldName)
            else -> normalizeExpression(value, normalizedType, fieldName)
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
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '$' -> append("\\$")
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
        val parsedValue = value.removeSuffix("l").removeSuffix("L")
        require(parsedValue.toLongOrNull() != null) {
            "invalid default value for field $fieldName: $value is not a valid Long literal"
        }
        return if (value.endsWith("L")) value else value.removeSuffix("l") + "L"
    }

    private fun normalizeInt(value: String, fieldName: String): String {
        require(intLiteralPattern.matches(value)) {
            "invalid default value for field $fieldName: $value is not a valid Int literal"
        }
        require(value.toIntOrNull() != null) {
            "invalid default value for field $fieldName: $value is not a valid Int literal"
        }
        return value
    }

    private fun normalizeDouble(value: String, fieldName: String): String {
        require(doubleLiteralPattern.matches(value)) {
            "invalid default value for field $fieldName: $value is not a valid Double literal"
        }
        return value
    }

    private fun normalizeFloat(value: String, fieldName: String): String {
        require(floatLiteralPattern.matches(value)) {
            "invalid default value for field $fieldName: $value is not a valid Float literal"
        }
        return value
    }

    private fun normalizeBoolean(value: String, fieldName: String): String {
        require(value == "true" || value == "false") {
            "invalid default value for field $fieldName: Boolean defaults must be true or false"
        }
        return value
    }

    private fun normalizeExpression(value: String, normalizedType: String, fieldName: String): String {
        if (value in supportedEmptyCollections) {
            require(isCompatibleEmptyCollection(value, normalizedType)) {
                "invalid default value for field $fieldName: $value is not compatible with $normalizedType"
            }
            return value
        }
        if (isConstantExpression(value)) {
            if (isCollectionLikeType(normalizedType)) {
                throw IllegalArgumentException("invalid default value for field $fieldName: unsupported default value expression: $value")
            }
            return value
        }
        throw IllegalArgumentException("invalid default value for field $fieldName: unsupported default value expression: $value")
    }

    private fun isConstantExpression(value: String): Boolean = constantExpressionPattern.matches(value)

    private fun isCompatibleEmptyCollection(value: String, normalizedType: String): Boolean {
        val rawType = rawTypeOf(normalizedType)
        return when (value) {
            "emptyList()" -> rawType in setOf("List", "Collection", "Iterable")
            "emptySet()" -> rawType in setOf("Set", "Collection", "Iterable")
            "mutableListOf()" -> rawType in setOf("MutableList", "MutableCollection", "List", "Collection", "Iterable")
            "mutableSetOf()" -> rawType in setOf("MutableSet", "MutableCollection", "Set", "Collection", "Iterable")
            else -> false
        }
    }

    private fun isCollectionLikeType(normalizedType: String): Boolean = rawTypeOf(normalizedType) in collectionLikeTypes

    private fun rawTypeOf(normalizedType: String): String = normalizedType.substringBefore("<").substringAfterLast(".").trim()

    private val builtInScalarTypes = setOf("String", "Int", "Long", "Double", "Float", "Boolean")
    private val collectionLikeTypes = setOf("List", "MutableList", "Set", "MutableSet", "Collection", "MutableCollection", "Iterable")
}
