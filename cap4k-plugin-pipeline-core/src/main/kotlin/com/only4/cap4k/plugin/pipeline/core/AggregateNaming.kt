package com.only4.cap4k.plugin.pipeline.core

import java.util.Locale

internal object AggregateNaming {
    fun entityName(tableName: String): String =
        tableName.split("_")
            .filter { it.isNotBlank() }
            .joinToString("") { part ->
                if (part.isUppercaseToken()) {
                    part.lowercase(Locale.ROOT).replaceFirstChar { character -> character.uppercase(Locale.ROOT) }
                } else {
                    part.replaceFirstChar { character -> character.uppercase(Locale.ROOT) }
                }
            }

    fun schemaName(tableName: String): String = "S${entityName(tableName)}"

    fun repositoryName(tableName: String): String = "${entityName(tableName)}Repository"

    fun tableSegment(tableName: String): String = tableName.lowercase(Locale.ROOT)

    private fun String.isUppercaseToken(): Boolean =
        any(Char::isLetter) && filter(Char::isLetter).all(Char::isUpperCase)
}
