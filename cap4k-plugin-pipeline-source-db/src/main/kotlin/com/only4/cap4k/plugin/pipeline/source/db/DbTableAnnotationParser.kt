package com.only4.cap4k.plugin.pipeline.source.db

internal object DbTableAnnotationParser {
    private val supportedTableAnnotations = setOf("Parent", "Ignore")
    private const val supportedTableAnnotationsMessage = "@Parent=<table>, @Ignore"

    fun parse(comment: String): DbTableAnnotationParseResult {
        val annotations = DbCommentAnnotationParser.parse(comment)
        rejectUnsupportedAnnotations(annotations)

        val parentTable = resolveRequiredValue(
            annotations = annotations,
            name = "Parent",
            conflictMessage = "conflicting @Parent annotations on the same table comment.",
            blankValueMessage = "blank @Parent value is not allowed.",
            missingValueMessage = "missing value for @Parent annotation.",
        )
        val ignored = hasMarker(
            annotations = annotations,
            name = "Ignore",
            invalidValueMessage = "invalid @Ignore annotation: explicit values are not supported.",
        )

        return DbTableAnnotationParseResult(
            parentTable = parentTable,
            aggregateRoot = parentTable == null,
            ignored = ignored,
            cleanedComment = DbCommentAnnotationParser.strip(comment, supportedTableAnnotations),
        )
    }

    private fun rejectUnsupportedAnnotations(annotations: List<ParsedDbCommentAnnotation>) {
        val unsupported = annotations.firstOrNull { it.rawName !in supportedTableAnnotations } ?: return
        throw IllegalArgumentException(
            "unsupported table annotation @${unsupported.rawName}. Supported table annotations: " +
                "$supportedTableAnnotationsMessage."
        )
    }

    private fun resolveRequiredValue(
        annotations: List<ParsedDbCommentAnnotation>,
        name: String,
        conflictMessage: String,
        blankValueMessage: String,
        missingValueMessage: String,
    ): String? {
        val matchingAnnotations = annotations.filter { it.rawName == name }
        if (matchingAnnotations.isEmpty()) {
            return null
        }

        require(matchingAnnotations.none { !it.hasExplicitValue }) { missingValueMessage }
        require(matchingAnnotations.none { it.value.isBlank() }) { blankValueMessage }

        val values = matchingAnnotations.map { it.value }.distinct()
        require(values.size <= 1) { conflictMessage }
        return values.single()
    }

    private fun hasMarker(
        annotations: List<ParsedDbCommentAnnotation>,
        name: String,
        invalidValueMessage: String,
    ): Boolean {
        val matchingAnnotations = annotations.filter { it.rawName == name }
        require(matchingAnnotations.none { it.hasExplicitValue }) { invalidValueMessage }
        return matchingAnnotations.isNotEmpty()
    }
}

internal data class DbTableAnnotationParseResult(
    val parentTable: String? = null,
    val aggregateRoot: Boolean = true,
    val ignored: Boolean = false,
    val cleanedComment: String = "",
)
