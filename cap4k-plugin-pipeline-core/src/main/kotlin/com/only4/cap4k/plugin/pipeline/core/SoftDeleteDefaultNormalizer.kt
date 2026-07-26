package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateIdStorageKind
import com.only4.cap4k.plugin.pipeline.api.SoftDeleteActiveSentinel

internal object SoftDeleteDefaultNormalizer {
    private const val NIL_UUID = "00000000-0000-0000-0000-000000000000"

    private val integralTargets = setOf("TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT")
    private val characterTargets = setOf(
        "CHAR",
        "CHARACTER",
        "CHARACTER VARYING",
        "VARCHAR",
        "LONGVARCHAR",
        "NCHAR",
        "NVARCHAR",
        "LONGNVARCHAR",
    )

    fun normalize(
        rawDefaultValue: String,
        storageKind: AggregateIdStorageKind,
    ): SoftDeleteActiveSentinel? {
        var expression = rawDefaultValue.trim()
        if (expression.isEmpty() || !isStructurallyValid(expression)) return null

        while (isWholeOuterPair(expression)) {
            expression = expression.substring(1, expression.lastIndex).trim()
            if (expression.isEmpty()) return null
        }

        val postfixCasts = findTopLevelPostfixCasts(expression)
        if (postfixCasts.size > 1) return null
        val consumedPostfixCast = postfixCasts.size == 1
        if (consumedPostfixCast) {
            val castAt = postfixCasts.single()
            val target = normalizeTarget(expression.substring(castAt + 2)) ?: return null
            if (!targetAgreesWithStorage(target, storageKind)) return null
            expression = expression.substring(0, castAt).trim()
            if (expression.isEmpty()) return null
        }

        if (consumedPostfixCast && (startsWithKeyword(expression, "CAST") || startsWithKeyword(expression, "UUID"))) {
            return null
        }

        expression = when {
            startsWithKeyword(expression, "CAST") ->
                unwrapStandardCast(expression, storageKind) ?: return null

            startsWithKeyword(expression, "UUID") ->
                unwrapTypedUuidLiteral(expression, storageKind) ?: return null

            else -> expression
        }

        val literal = unwrapSqlLiteral(expression)
        if (expression.startsWith('\'') && literal == null) return null
        val value = literal ?: expression

        return when {
            value == "0" && storageKind != AggregateIdStorageKind.NATIVE_UUID ->
                SoftDeleteActiveSentinel.ZERO

            literal != null && value.equals(NIL_UUID, ignoreCase = true) &&
                storageKind != AggregateIdStorageKind.INTEGRAL ->
                SoftDeleteActiveSentinel.NIL_UUID

            else -> null
        }
    }

    private fun unwrapStandardCast(
        expression: String,
        storageKind: AggregateIdStorageKind,
    ): String? {
        var openParenthesis = "CAST".length
        while (openParenthesis < expression.length && expression[openParenthesis].isWhitespace()) {
            openParenthesis++
        }
        if (openParenthesis >= expression.length || expression[openParenthesis] != '(') return null

        val parenthesized = expression.substring(openParenthesis)
        if (!isWholeOuterPair(parenthesized)) return null
        val body = parenthesized.substring(1, parenthesized.lastIndex)
        val separators = findTopLevelKeyword(body, "AS")
        if (separators.size != 1) return null

        val separator = separators.single()
        val value = body.substring(0, separator).trim()
        val target = normalizeTarget(body.substring(separator + "AS".length)) ?: return null
        if (value.isEmpty() || !targetAgreesWithStorage(target, storageKind)) return null
        return value
    }

    private fun unwrapTypedUuidLiteral(
        expression: String,
        storageKind: AggregateIdStorageKind,
    ): String? {
        if (storageKind != AggregateIdStorageKind.NATIVE_UUID) return null
        val keywordEnd = "UUID".length
        if (expression.length <= keywordEnd || !expression[keywordEnd].isWhitespace()) return null
        val literal = expression.substring(keywordEnd).trim()
        if (unwrapSqlLiteral(literal) == null) return null
        return literal
    }

    private fun targetAgreesWithStorage(target: String, storageKind: AggregateIdStorageKind): Boolean =
        when (storageKind) {
            AggregateIdStorageKind.INTEGRAL -> target in integralTargets
            AggregateIdStorageKind.CHARACTER -> target in characterTargets
            AggregateIdStorageKind.NATIVE_UUID -> target == "UUID"
        }

    private fun normalizeTarget(rawTarget: String): String? {
        val target = rawTarget.trim()
        if (target.isEmpty() || target.any { !it.isLetter() && !it.isWhitespace() }) return null

        return buildString {
            var pendingSpace = false
            target.forEach { character ->
                if (character.isWhitespace()) {
                    pendingSpace = isNotEmpty()
                } else {
                    if (pendingSpace) append(' ')
                    append(character.uppercaseChar())
                    pendingSpace = false
                }
            }
        }
    }

    private fun unwrapSqlLiteral(expression: String): String? {
        if (expression.length < 2 || expression.first() != '\'') return null

        val value = StringBuilder()
        var index = 1
        while (index < expression.length) {
            val character = expression[index]
            if (character != '\'') {
                value.append(character)
                index++
                continue
            }
            if (index + 1 < expression.length && expression[index + 1] == '\'') {
                value.append('\'')
                index += 2
                continue
            }
            return if (index == expression.lastIndex) value.toString() else null
        }
        return null
    }

    private fun findTopLevelPostfixCasts(expression: String): List<Int> {
        val positions = mutableListOf<Int>()
        scanTopLevel(expression) { index ->
            if (expression[index] == ':' && index + 1 < expression.length && expression[index + 1] == ':') {
                positions += index
                2
            } else {
                1
            }
        }
        return positions
    }

    private fun findTopLevelKeyword(expression: String, keyword: String): List<Int> {
        val positions = mutableListOf<Int>()
        scanTopLevel(expression) { index ->
            val end = index + keyword.length
            if (
                index > 0 && end < expression.length &&
                expression[index - 1].isWhitespace() && expression[end].isWhitespace() &&
                expression.regionMatches(index, keyword, 0, keyword.length, ignoreCase = true)
            ) {
                positions += index
                keyword.length
            } else {
                1
            }
        }
        return positions
    }

    private inline fun scanTopLevel(expression: String, visit: (Int) -> Int) {
        var depth = 0
        var inQuote = false
        var index = 0
        while (index < expression.length) {
            val character = expression[index]
            if (character == '\'') {
                if (inQuote && index + 1 < expression.length && expression[index + 1] == '\'') {
                    index += 2
                    continue
                }
                inQuote = !inQuote
                index++
                continue
            }
            if (!inQuote) {
                when (character) {
                    '(' -> depth++
                    ')' -> depth--
                    else -> if (depth == 0) {
                        index += visit(index)
                        continue
                    }
                }
            }
            index++
        }
    }

    private fun startsWithKeyword(expression: String, keyword: String): Boolean {
        if (!expression.startsWith(keyword, ignoreCase = true)) return false
        if (expression.length == keyword.length) return true
        return expression[keyword.length].isWhitespace() || expression[keyword.length] == '('
    }

    private fun isWholeOuterPair(expression: String): Boolean {
        if (expression.length < 2 || expression.first() != '(' || expression.last() != ')') return false

        var depth = 0
        var inQuote = false
        var index = 0
        while (index < expression.length) {
            val character = expression[index]
            if (character == '\'') {
                if (inQuote && index + 1 < expression.length && expression[index + 1] == '\'') {
                    index += 2
                    continue
                }
                inQuote = !inQuote
            } else if (!inQuote) {
                when (character) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0 && index != expression.lastIndex) return false
                    }
                }
            }
            index++
        }
        return depth == 0 && !inQuote
    }

    private fun isStructurallyValid(expression: String): Boolean {
        var depth = 0
        var inQuote = false
        var index = 0
        while (index < expression.length) {
            val character = expression[index]
            if (character == '\'') {
                if (inQuote && index + 1 < expression.length && expression[index + 1] == '\'') {
                    index += 2
                    continue
                }
                inQuote = !inQuote
            } else if (!inQuote) {
                when (character) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth < 0) return false
                    }
                }
            }
            index++
        }
        return depth == 0 && !inQuote
    }
}
