package com.only4.cap4k.plugin.pipeline.core

internal object AggregateNaming {
    fun entityName(tableName: String): String =
        tableName.split("_")
            .filter { it.isNotBlank() }
            .joinToString("") { part -> part.replaceFirstChar { character -> character.uppercase() } }

    fun schemaName(tableName: String): String = "S${entityName(tableName)}"

    fun repositoryName(tableName: String): String = "${entityName(tableName)}Repository"

    fun tableSegment(tableName: String): String = tableName.lowercase()
}
