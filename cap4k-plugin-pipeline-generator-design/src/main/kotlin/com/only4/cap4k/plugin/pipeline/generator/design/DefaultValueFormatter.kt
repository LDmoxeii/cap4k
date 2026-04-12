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
<<<<<<< HEAD
    private val doubleLiteralPattern = Regex("""-?\d+\.\d+(?:[dD])?""")
    private val floatLiteralPattern = Regex("""-?\d+(?:\.\d+)?[fF]""")
    private val longLiteralPattern = Regex("""-?\d+[lL]?""")
=======
    private val longLiteralPattern = Regex("""-?\d+[lL]?""")
    private val doubleLiteralPattern = Regex("""-?(?:(?:\d+\.\d*|\.\d+)(?:[eE][+-]?\d+)?|\d+[eE][+-]?\d+)""")
    private val floatLiteralPattern = Regex("""-?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?[fF]""")
>>>>>>> feat/design-default-value

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

<<<<<<< HEAD
        return when (renderedType.removeSuffix("?").trim()) {
            "String" -> normalizeString(value)
            "Int" -> normalizeInt(value, fieldName)
            "Double" -> normalizeDouble(value, fieldName)
            "Float" -> normalizeFloat(value, fieldName)
            "Long" -> normalizeLong(value, fieldName)
            "Boolean" -> normalizeBoolean(value, fieldName)
            else -> normalizeExpression(value, renderedType.removeSuffix("?").trim(), fieldName)
        }
    }

    private fun normalizeString(value: String): String {
        if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
            return value
        }
        return "\"$value\""
=======
        val normalizedType = renderedType.removeSuffix("?").trim()
        if (normalizedType in builtInScalarTypes && isConstantExpression(value)) {
            throw IllegalArgumentException("invalid default value for field $fieldName: unsupported default value expression: $value")
        }

        return when (normalizedType) {
            "String" -> normalizeString(value, fieldName)
            "Int" -> normalizeInt(value, fieldName)
            "Long" -> normalizeLong(value, fieldName)
            "Double" -> normalizeDouble(value, fieldName)
            "Float" -> normalizeFloat(value, fieldName)
            "Boolean" -> normalizeBoolean(value, fieldName)
            else -> normalizeExpression(value, normalizedType, fieldName)
        }
    }

    private fun normalizeString(value: String, fieldName: String): String {
        if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
            require(isValidQuotedStringLiteral(value)) {
                "invalid default value for field $fieldName: invalid String literal: $value"
            }
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
>>>>>>> feat/design-default-value
    }

    private fun normalizeLong(value: String, fieldName: String): String {
        require(longLiteralPattern.matches(value)) {
            "invalid default value for field $fieldName: $value is not a valid Long literal"
        }
<<<<<<< HEAD
=======
        val parsedValue = value.removeSuffix("l").removeSuffix("L")
        require(parsedValue.toLongOrNull() != null) {
            "invalid default value for field $fieldName: $value is not a valid Long literal"
        }
>>>>>>> feat/design-default-value
        return if (value.endsWith("L")) value else value.removeSuffix("l") + "L"
    }

    private fun normalizeInt(value: String, fieldName: String): String {
        require(intLiteralPattern.matches(value)) {
            "invalid default value for field $fieldName: $value is not a valid Int literal"
        }
<<<<<<< HEAD
=======
        require(value.toIntOrNull() != null) {
            "invalid default value for field $fieldName: $value is not a valid Int literal"
        }
>>>>>>> feat/design-default-value
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

<<<<<<< HEAD
    private fun normalizeExpression(value: String, renderedType: String, fieldName: String): String {
        if (value in supportedEmptyCollections) {
            require(isCompatibleEmptyCollection(value, renderedType)) {
                "invalid default value for field $fieldName: $value is incompatible with rendered type $renderedType"
            }
            return value
        }
        if (constantExpressionPattern.matches(value)) {
=======
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
            require(isCompatibleConstantExpression(value, normalizedType)) {
                "invalid default value for field $fieldName: unsupported default value expression: $value"
            }
>>>>>>> feat/design-default-value
            return value
        }
        throw IllegalArgumentException("invalid default value for field $fieldName: unsupported default value expression: $value")
    }

<<<<<<< HEAD
    private fun isCompatibleEmptyCollection(value: String, renderedType: String): Boolean {
        return when (value) {
            "emptyList()" -> collectionBaseName(renderedType) == "List"
            "emptySet()" -> collectionBaseName(renderedType) == "Set"
            "mutableListOf()" -> collectionBaseName(renderedType) == "List" || collectionBaseName(renderedType) == "MutableList"
            "mutableSetOf()" -> collectionBaseName(renderedType) == "Set" || collectionBaseName(renderedType) == "MutableSet"
=======
    private fun isConstantExpression(value: String): Boolean = constantExpressionPattern.matches(value)

    private fun isCompatibleConstantExpression(value: String, normalizedType: String): Boolean {
        val ownerType = value.substringBeforeLast(".")
        val renderedType = normalizedType.substringBefore("<").trim()
        if (renderedType.contains(".")) {
            return ownerType == renderedType
        }
        return ownerType == renderedType || ownerType.substringAfterLast(".") == renderedType
    }

    private fun isCompatibleEmptyCollection(value: String, normalizedType: String): Boolean {
        val rawType = rawTypeOf(normalizedType)
        return when (value) {
            "emptyList()" -> rawType in setOf("List", "Collection", "Iterable")
            "emptySet()" -> rawType in setOf("Set", "Collection", "Iterable")
            "mutableListOf()" -> rawType in setOf("MutableList", "MutableCollection", "List", "Collection", "Iterable")
            "mutableSetOf()" -> rawType in setOf("MutableSet", "MutableCollection", "Set", "Collection", "Iterable")
>>>>>>> feat/design-default-value
            else -> false
        }
    }

<<<<<<< HEAD
    private fun collectionBaseName(renderedType: String): String = renderedType.substringBefore('<').trim()
=======
    private fun isCollectionLikeType(normalizedType: String): Boolean = rawTypeOf(normalizedType) in collectionLikeTypes

    private fun rawTypeOf(normalizedType: String): String = normalizedType.substringBefore("<").substringAfterLast(".").trim()

    private fun isValidQuotedStringLiteral(value: String): Boolean {
        var index = 1
        while (index < value.length - 1) {
            val current = value[index]
            if (current == '"') {
                return false
            }
            if (current == '\n' || current == '\r') {
                return false
            }
            if (current == '\\') {
                if (index + 1 >= value.length - 1) {
                    return false
                }
                val escape = value[index + 1]
                if (escape == 'u') {
                    if (index + 5 >= value.length) {
                        return false
                    }
                    if (!value.substring(index + 2, index + 6).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                        return false
                    }
                    index += 6
                    continue
                }
                if (escape !in supportedStringEscapes) {
                    return false
                }
                index += 2
                continue
            }
            index++
        }
        return true
    }

    private val builtInScalarTypes = setOf("String", "Int", "Long", "Double", "Float", "Boolean")
    private val collectionLikeTypes = setOf("List", "MutableList", "Set", "MutableSet", "Collection", "MutableCollection", "Iterable")
    private val supportedStringEscapes = setOf('\\', '"', '\'', 'b', 'n', 'r', 't', '$')
>>>>>>> feat/design-default-value
}
