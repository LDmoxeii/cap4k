package com.only4.cap4k.plugin.pipeline.source.db

import java.util.Locale

internal object DbTableAnnotationParser {
    private val annotationPattern = Regex("@([A-Za-z]+)(=([^;]*))?;?")
    private val tableAliases = setOf("PARENT", "P", "AGGREGATEROOT", "ROOT", "R", "VALUEOBJECT", "VO", "IGNORE", "I")
    private val providerAliases = setOf("DYNAMICINSERT", "DYNAMICUPDATE")
    private val multiSpacePattern = Regex("\\s{2,}")

    fun parse(comment: String): DbTableAnnotationParseResult {
        val annotations = annotationPattern.findAll(comment)
            .map { match ->
                ParsedTableAnnotation(
                    key = match.groupValues[1].uppercase(Locale.ROOT),
                    value = match.groupValues.getOrElse(3) { "" }.trim(),
                    range = match.range,
                    hasExplicitValue = match.groups[2] != null,
                )
            }
            .toList()

        rejectLegacyIdGeneratorAnnotation(annotations)
        rejectLegacySoftDeleteColumnAnnotation(annotations)

        val parentTable = resolveAnnotationValue(
            annotations = annotations,
            aliases = setOf("PARENT", "P"),
            conflictMessage = "conflicting @Parent/@P annotations on the same table comment.",
            blankValueMessage = "blank @Parent/@P value is not allowed.",
            missingValueMessage = "missing value for @Parent/@P annotation.",
        )
        val valueObject = resolvePresenceAnnotation(
            annotations = annotations,
            aliases = setOf("VALUEOBJECT", "VO"),
            invalidValueMessage = "invalid @ValueObject/@VO annotation: explicit values are not supported.",
        )
        val ignored = resolvePresenceAnnotation(
            annotations = annotations,
            aliases = setOf("IGNORE", "I"),
            invalidValueMessage = "invalid @Ignore/@I annotation: explicit values are not supported.",
        )
        val aggregateRootAnnotation = resolveBooleanAnnotationValue(
            annotations = annotations,
            aliases = setOf("AGGREGATEROOT", "ROOT", "R"),
            conflictMessage = "conflicting @AggregateRoot/@Root/@R annotations on the same table comment.",
            invalidMessagePrefix = "invalid @AggregateRoot/@Root/@R boolean value: ",
        )
        require(!(parentTable != null && aggregateRootAnnotation.explicit && aggregateRootAnnotation.value == true)) {
            "conflicting table relation annotations: @Parent/@P cannot be combined with @AggregateRoot=true."
        }
        val dynamicInsert = resolveProviderBooleanAnnotation(
            annotations = annotations,
            key = "DYNAMICINSERT",
            annotationName = "DynamicInsert",
        )
        val dynamicUpdate = resolveProviderBooleanAnnotation(
            annotations = annotations,
            key = "DYNAMICUPDATE",
            annotationName = "DynamicUpdate",
        )
        return DbTableAnnotationParseResult(
            parentTable = parentTable,
            aggregateRoot = aggregateRootAnnotation.value ?: (parentTable == null),
            valueObject = valueObject,
            ignored = ignored,
            dynamicInsert = dynamicInsert,
            dynamicUpdate = dynamicUpdate,
            cleanedComment = stripRecognizedAnnotations(comment, tableAliases + providerAliases),
        )
    }

    private fun rejectLegacyIdGeneratorAnnotation(annotations: List<ParsedTableAnnotation>) {
        annotations.firstOrNull { it.key == "IDGENERATOR" }?.let {
            throw IllegalArgumentException(
                "unsupported table annotation @IdGenerator: use @GeneratedValue on the ID column instead"
            )
        }
        annotations.firstOrNull { it.key == "IG" }?.let {
            throw IllegalArgumentException(
                "unsupported table annotation @IG: use @GeneratedValue on the ID column instead"
            )
        }
    }

    private fun rejectLegacySoftDeleteColumnAnnotation(annotations: List<ParsedTableAnnotation>) {
        annotations.firstOrNull { it.key == "SOFTDELETECOLUMN" }?.let {
            throw IllegalArgumentException(
                "unsupported table annotation @SoftDeleteColumn: use @Deleted marker on the delete column instead"
            )
        }
    }

    private fun resolveAnnotationValue(
        annotations: List<ParsedTableAnnotation>,
        aliases: Set<String>,
        conflictMessage: String,
        blankValueMessage: String,
        missingValueMessage: String,
    ): String? {
        val matchingAnnotations = annotations.filter { it.key in aliases }
        require(matchingAnnotations.none { !it.hasExplicitValue }) { missingValueMessage }
        require(matchingAnnotations.none { it.hasExplicitValue && it.value.isBlank() }) { blankValueMessage }

        val values = matchingAnnotations
            .map { it.value }
            .filter { it.isNotBlank() }
            .distinct()
        require(values.size <= 1) { conflictMessage }
        return values.singleOrNull()
    }

    private fun resolvePresenceAnnotation(
        annotations: List<ParsedTableAnnotation>,
        aliases: Set<String>,
        invalidValueMessage: String,
    ): Boolean {
        val matchingAnnotations = annotations.filter { it.key in aliases }
        require(matchingAnnotations.none { it.hasExplicitValue }) { invalidValueMessage }
        return matchingAnnotations.isNotEmpty()
    }

    private fun resolveProviderBooleanAnnotation(
        annotations: List<ParsedTableAnnotation>,
        key: String,
        annotationName: String,
    ): Boolean? {
        val values = annotations
            .filter { it.key == key }
            .map { it.value }
            .distinct()
        if (values.isEmpty()) {
            return null
        }

        val invalidValue = values.firstOrNull { it.toBooleanStrictOrNull() == null }
        require(invalidValue == null) { "invalid @$annotationName value: $invalidValue" }

        val booleans = values.map { it.toBooleanStrict() }.distinct()
        require(booleans.size <= 1) { "conflicting @$annotationName annotations on the same table comment." }
        return booleans.single()
    }

    private fun resolveBooleanAnnotationValue(
        annotations: List<ParsedTableAnnotation>,
        aliases: Set<String>,
        conflictMessage: String,
        invalidMessagePrefix: String,
    ): ResolvedTableBooleanAnnotation {
        val values = annotations
            .filter { it.key in aliases }
            .map { it.value }
            .distinct()
        if (values.isEmpty()) {
            return ResolvedTableBooleanAnnotation()
        }

        val invalidValue = values.firstOrNull { it.toBooleanStrictOrNull() == null }
        require(invalidValue == null) { invalidMessagePrefix + invalidValue }

        val booleans = values.map { it.toBooleanStrict() }.distinct()
        require(booleans.size <= 1) { conflictMessage }
        return ResolvedTableBooleanAnnotation(
            value = booleans.single(),
            explicit = true,
        )
    }

    private fun stripRecognizedAnnotations(comment: String, aliases: Set<String>): String {
        if (comment.isBlank()) {
            return ""
        }

        val cleaned = buildString {
            var cursor = 0
            for (annotation in annotationPattern.findAll(comment).map { match ->
                ParsedTableAnnotation(
                    key = match.groupValues[1].uppercase(Locale.ROOT),
                    value = match.groupValues.getOrElse(3) { "" }.trim(),
                    range = match.range,
                    hasExplicitValue = match.groups[2] != null,
                )
            }) {
                append(comment, cursor, annotation.range.first)
                if (annotation.key !in aliases) {
                    append(comment, annotation.range.first, annotation.range.last + 1)
                }
                cursor = annotation.range.last + 1
            }
            append(comment, cursor, comment.length)
        }
        return cleaned.replace(multiSpacePattern, " ").trim()
    }
}

internal data class DbTableAnnotationParseResult(
    val parentTable: String? = null,
    val aggregateRoot: Boolean = true,
    val valueObject: Boolean = false,
    val ignored: Boolean = false,
    val dynamicInsert: Boolean? = null,
    val dynamicUpdate: Boolean? = null,
    val cleanedComment: String = "",
)

private data class ParsedTableAnnotation(
    val key: String,
    val value: String,
    val range: IntRange,
    val hasExplicitValue: Boolean,
)

private data class ResolvedTableBooleanAnnotation(
    val value: Boolean? = null,
    val explicit: Boolean = false,
)
