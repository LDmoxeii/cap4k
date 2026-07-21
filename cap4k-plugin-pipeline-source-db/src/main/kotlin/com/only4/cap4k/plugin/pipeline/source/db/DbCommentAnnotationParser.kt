package com.only4.cap4k.plugin.pipeline.source.db

internal data class ParsedDbCommentAnnotation(
    val rawName: String,
    val value: String,
    val range: IntRange,
    val hasExplicitValue: Boolean,
)

internal object DbCommentAnnotationParser {
    private val annotationPattern = Regex("@([A-Za-z]+)(=([^;]*))?;?")
    private val multiSpacePattern = Regex("\\s{2,}")

    fun parse(comment: String): List<ParsedDbCommentAnnotation> =
        annotationPattern.findAll(comment)
            .map { match ->
                ParsedDbCommentAnnotation(
                    rawName = match.groupValues[1],
                    value = match.groupValues.getOrElse(3) { "" }.trim(),
                    range = match.range,
                    hasExplicitValue = match.groups[2] != null,
                )
            }
            .toList()

    fun strip(comment: String, supportedNames: Set<String>): String {
        if (comment.isBlank()) {
            return ""
        }

        val cleaned = buildString {
            var cursor = 0
            for (annotation in parse(comment)) {
                append(comment, cursor, annotation.range.first)
                if (annotation.rawName !in supportedNames) {
                    append(comment, annotation.range.first, annotation.range.last + 1)
                }
                cursor = annotation.range.last + 1
            }
            append(comment, cursor, comment.length)
        }
        return cleaned.replace(multiSpacePattern, " ").trim()
    }
}
