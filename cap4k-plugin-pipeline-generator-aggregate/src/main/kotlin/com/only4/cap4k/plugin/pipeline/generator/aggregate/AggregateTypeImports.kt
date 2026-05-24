package com.only4.cap4k.plugin.pipeline.generator.aggregate

internal fun aggregateTypeImports(vararg types: String?): List<String> =
    types
        .filterNotNull()
        .flatMap { type -> aggregateTypeImports(type) }
        .distinct()

internal fun aggregateTypeImports(type: String): List<String> =
    aggregateRenderedType(type).imports

internal data class AggregateRenderedType(
    val renderedType: String,
    val imports: List<String>,
)

internal fun aggregateRenderedType(type: String): AggregateRenderedType {
    val normalized = type.removeSuffix("?").trim()
    val rawType = normalized.substringBefore("<")
    val genericSuffix = normalized.removePrefix(rawType)
    val shortType = rawType.substringAfterLast(".")
    return when {
        rawType == "UUID" ->
            AggregateRenderedType("UUID", listOf("java.util.UUID"))

        rawType == "java.util.UUID" ->
            AggregateRenderedType(rawType, emptyList())

        "." !in rawType ->
            AggregateRenderedType(normalized, emptyList())

        rawType.startsWith("kotlin.") || rawType.startsWith("java.lang.") ->
            AggregateRenderedType(shortType + genericSuffix, emptyList())

        else ->
            AggregateRenderedType(shortType + genericSuffix, listOf(rawType))
    }
}
