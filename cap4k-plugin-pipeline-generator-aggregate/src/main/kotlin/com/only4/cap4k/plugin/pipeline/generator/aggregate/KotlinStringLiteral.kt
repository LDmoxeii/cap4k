package com.only4.cap4k.plugin.pipeline.generator.aggregate

internal fun String.toKotlinStringLiteral(): String {
    val escaped = buildString {
        this@toKotlinStringLiteral.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '$' -> append("\\$")
                else -> {
                    if (char.code in 0x00..0x1F) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
    }
    return "\"$escaped\""
}
