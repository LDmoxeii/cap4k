package com.only4.cap4k.plugin.pipeline.generator.aggregate

internal fun aggregateTypeImports(vararg types: String?): List<String> =
    types
        .filterNotNull()
        .flatMap { type -> aggregateTypeImports(type) }
        .distinct()

internal fun aggregateTypeImports(type: String): List<String> {
    val normalized = type.removeSuffix("?").trim()
    return when (normalized.substringBefore("<").substringAfterLast(".")) {
        "UUID" -> listOf("java.util.UUID")
        else -> emptyList()
    }
}
