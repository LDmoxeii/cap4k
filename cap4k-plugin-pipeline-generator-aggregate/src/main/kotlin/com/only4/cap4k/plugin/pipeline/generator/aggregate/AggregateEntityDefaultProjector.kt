package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.EnumItemModel

internal class AggregateEntityDefaultProjector {
    fun project(
        fieldPath: String,
        fieldType: String,
        nullable: Boolean,
        rawDefaultValue: String?,
        enumItems: List<EnumItemModel>,
    ): String? {
        val normalized = rawDefaultValue?.let(::normalizeDefaultLiteral)
        if (normalized == null) return if (nullable) "null" else null
        if (normalized.equals("null", ignoreCase = true)) {
            require(nullable) {
                "database default NULL cannot be projected to non-null aggregate field $fieldPath"
            }
            return "null"
        }
        if (isSqlExpression(normalized)) return null
        if (enumItems.isNotEmpty()) return projectEnumDefault(fieldPath, fieldType, normalized, enumItems)
        return projectScalarDefault(fieldPath, fieldType, normalized)
    }

    private fun projectEnumDefault(
        fieldPath: String,
        fieldType: String,
        normalized: String,
        enumItems: List<EnumItemModel>,
    ): String {
        val numericValue = normalized.unquoteSqlString().unwrapEnumValueOrNull()
            ?: normalized.unwrapEnumValueOrNull()
            ?: throw IllegalArgumentException(
                "aggregate enum field $fieldPath default $normalized is not numeric; enum defaults must use numeric values"
            )
        require(enumItems.any { it.value == numericValue }) {
            "aggregate enum field $fieldPath default $numericValue does not match any enum item value"
        }
        return "$fieldType.valueOf($numericValue)"
    }

    private fun projectScalarDefault(fieldPath: String, fieldType: String, normalized: String): String? {
        val shortType = fieldType.substringAfterLast('.').removeSuffix("?")
        return when (shortType) {
            "Boolean" -> when {
                normalized.equals("true", ignoreCase = true) || normalized == "1" -> "true"
                normalized.equals("false", ignoreCase = true) || normalized == "0" -> "false"
                else -> throw IllegalArgumentException(
                    "aggregate field $fieldPath default $normalized cannot be projected to Boolean"
                )
            }
            "String" -> normalized.unquoteSqlString()?.let { quoteKotlinString(it) }
            "Byte", "Short", "Int" -> normalized.unquoteSqlString().unwrapIntegerLiteralOrNull()
                ?: normalized.unwrapIntegerLiteralOrNull()
                ?: throw IllegalArgumentException(
                    "aggregate field $fieldPath default $normalized cannot be projected to $shortType"
                )
            "Long" -> {
                val value = normalized.unquoteSqlString().unwrapIntegerLiteralOrNull()
                    ?: normalized.unwrapIntegerLiteralOrNull()
                    ?: throw IllegalArgumentException(
                        "aggregate field $fieldPath default $normalized cannot be projected to Long"
                    )
                "${value}L"
            }
            "Float" -> {
                val rawValue = normalized.unquoteSqlString() ?: normalized
                normalizedFloatingLiteral(rawValue, suffix = "f")
                    ?: throw IllegalArgumentException(
                        "aggregate field $fieldPath default $normalized cannot be projected to Float"
                    )
            }
            "Double" -> {
                val rawValue = normalized.unquoteSqlString() ?: normalized
                normalizedFloatingLiteral(rawValue)
                    ?: throw IllegalArgumentException(
                        "aggregate field $fieldPath default $normalized cannot be projected to Double"
                    )
            }
            else -> null
        }
    }

    private fun normalizeDefaultLiteral(rawValue: String): String {
        var value = rawValue.trim()
        while (value.length >= 2 && value.first() == '(' && value.last() == ')') {
            value = value.substring(1, value.lastIndex).trim()
        }
        return value
    }

    private fun isSqlExpression(value: String): Boolean {
        val upper = value.uppercase()
        return upper in setOf("CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME") || upper.endsWith("()")
    }

    private fun String?.unwrapIntegerLiteralOrNull(): String? =
        this?.takeIf { it.matches(Regex("""[-+]?\d+""")) }

    private fun String?.unwrapEnumValueOrNull(): Int? =
        this?.takeIf { it.matches(Regex("""[-+]?\d+""")) }?.toInt()

    private fun normalizedFloatingLiteral(rawValue: String, suffix: String? = null): String? {
        val value = if (suffix != null && rawValue.endsWith(suffix, ignoreCase = true)) {
            rawValue.dropLast(suffix.length)
        } else {
            rawValue
        }
        if (!value.matches(Regex("""[-+]?(\d+(\.\d*)?|\.\d+)"""))) return null
        val sign = value.takeIf { it.startsWith("-") || it.startsWith("+") }?.take(1).orEmpty()
        val unsigned = value.removePrefix("-").removePrefix("+")
        val withLeadingDigit = if (unsigned.startsWith(".")) "0$unsigned" else unsigned
        val normalized = when {
            withLeadingDigit.endsWith(".") -> "${withLeadingDigit}0"
            suffix == null && !withLeadingDigit.contains(".") -> "$withLeadingDigit.0"
            else -> withLeadingDigit
        }
        return sign + normalized + (suffix ?: "")
    }

    private fun String.unquoteSqlString(): String? =
        when {
            length >= 2 && first() == '\'' && last() == '\'' -> substring(1, lastIndex).replace("''", "'")
            length >= 2 && first() == '"' && last() == '"' -> substring(1, lastIndex).replace("\"\"", "\"")
            else -> null
        }

    private fun quoteKotlinString(value: String): String =
        buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '$' -> append("\\\$")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
}
