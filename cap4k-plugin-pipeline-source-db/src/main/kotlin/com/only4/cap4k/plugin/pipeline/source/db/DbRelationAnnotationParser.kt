package com.only4.cap4k.plugin.pipeline.source.db

import java.util.Locale

internal class DbRelationAnnotationParser {
    fun parseColumn(comment: String): ColumnRelationMetadata {
        val annotations = parseAnnotations(comment)
        val hasRelationAnnotation = annotations.any { it.key in RELATION_ALIASES }
        val hasLazyAnnotation = annotations.any { it.key in LAZY_ALIASES }
        val hasCountAnnotation = annotations.any { it.key in COUNT_ALIASES }
        val relationValue = resolveAnnotationValue(
            annotations = annotations,
            aliases = RELATION_ALIASES,
            conflictMessage = "conflicting @Relation/@Rel annotations on the same column comment.",
            blankValueMessage = "blank @Relation/@Rel value is not allowed.",
            missingValueMessage = "missing value for @Relation/@Rel annotation.",
        )
        val relation = relationValue?.uppercase(Locale.ROOT)
        require(relation == null || relation in SUPPORTED_RELATION_TYPES) {
            "unsupported relation type in first slice: $relationValue"
        }

        val referenceTable = resolveAnnotationValue(
            annotations = annotations,
            aliases = REFERENCE_ALIASES,
            conflictMessage = "conflicting @Reference/@Ref annotations on the same column comment.",
            blankValueMessage = "blank @Reference/@Ref value is not allowed.",
            missingValueMessage = "missing value for @Reference/@Ref annotation.",
        )
        val refAggregate = resolveAnnotationValue(
            annotations = annotations,
            aliases = REF_AGGREGATE_ALIASES,
            conflictMessage = "conflicting @RefAggregate annotations on the same column comment.",
            blankValueMessage = "blank @RefAggregate value is not allowed.",
            missingValueMessage = "missing value for @RefAggregate annotation.",
        )
        val refId = resolveAnnotationValue(
            annotations = annotations,
            aliases = REF_ID_ALIASES,
            conflictMessage = "conflicting @RefId annotations on the same column comment.",
            blankValueMessage = "blank @RefId value is not allowed.",
            missingValueMessage = "missing value for @RefId annotation.",
        )
        require(!(refAggregate != null && referenceTable != null)) {
            "conflicting @RefAggregate and @Reference/@Ref annotations on the same column comment."
        }
        require(!(refAggregate != null && refId != null)) {
            "conflicting @RefAggregate and @RefId annotations on the same column comment."
        }
        val explicitRelationType = when (relation) {
            "ONE_TO_ONE", "1:1", "ONETOONE" -> "ONE_TO_ONE"
            "MANY_TO_ONE", "*:1", "MANYTOONE" -> "MANY_TO_ONE"
            else -> null
        }
        val lazy = resolveBooleanAnnotationValue(
            annotations = annotations,
            aliases = LAZY_ALIASES,
            conflictMessage = "conflicting @Lazy/@L annotations on the same column comment.",
            invalidMessagePrefix = "invalid @Lazy/@L boolean value: ",
        ).value
        val countHint = resolveAnnotationValue(
            annotations = annotations,
            aliases = COUNT_ALIASES,
            conflictMessage = "conflicting @Count/@C annotations on the same column comment.",
            blankValueMessage = "blank @Count/@C value is not allowed.",
            missingValueMessage = "missing value for @Count/@C annotation.",
        )
        require(!(referenceTable == null && hasRelationAnnotation)) {
            "@Relation/@Rel requires @Reference/@Ref on the same column comment."
        }
        require(!(referenceTable == null && hasLazyAnnotation)) {
            "@Lazy/@L requires @Reference/@Ref on the same column comment."
        }
        require(!(referenceTable == null && hasCountAnnotation)) {
            "@Count/@C requires @Reference/@Ref on the same column comment."
        }

        return ColumnRelationMetadata(
            referenceTable = referenceTable,
            explicitRelationType = explicitRelationType,
            lazy = lazy,
            countHint = countHint,
            refAggregate = refAggregate,
            cleanedComment = stripRecognizedAnnotations(comment, COLUMN_RELATION_ALIASES),
        )
    }

    private fun parseAnnotations(comment: String): List<RelationParsedAnnotation> {
        return ANNOTATION_PATTERN.findAll(comment)
            .map { match ->
                RelationParsedAnnotation(
                    key = match.groupValues[1].uppercase(Locale.ROOT),
                    value = match.groupValues.getOrElse(3) { "" }.trim(),
                    range = match.range,
                    hasExplicitValue = match.groups[2] != null,
                )
            }
            .toList()
    }

    private fun resolveAnnotationValue(
        annotations: List<RelationParsedAnnotation>,
        aliases: Set<String>,
        conflictMessage: String,
        blankValueMessage: String,
        missingValueMessage: String,
    ): String? {
        val matchingAnnotations = annotations
            .asSequence()
            .filter { it.key in aliases }
            .toList()
        require(matchingAnnotations.none { !it.hasExplicitValue }) { missingValueMessage }
        require(matchingAnnotations.none { it.hasExplicitValue && it.value.isBlank() }) { blankValueMessage }

        val values = matchingAnnotations
            .asSequence()
            .map { it.value }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        require(values.size <= 1) { conflictMessage }
        return values.singleOrNull()
    }

    private fun resolveBooleanAnnotationValue(
        annotations: List<RelationParsedAnnotation>,
        aliases: Set<String>,
        conflictMessage: String,
        invalidMessagePrefix: String,
    ): ResolvedBooleanAnnotation {
        val values = annotations
            .asSequence()
            .filter { it.key in aliases }
            .map { it.value }
            .distinct()
            .toList()
        if (values.isEmpty()) {
            return ResolvedBooleanAnnotation()
        }

        val invalidValue = values.firstOrNull { it.toBooleanStrictOrNull() == null }
        require(invalidValue == null) { invalidMessagePrefix + invalidValue }

        val booleans = values
            .map { it.toBooleanStrict() }
            .distinct()
        require(booleans.size <= 1) { conflictMessage }
        return ResolvedBooleanAnnotation(
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
            for (annotation in parseAnnotations(comment)) {
                append(comment, cursor, annotation.range.first)
                if (annotation.key !in aliases) {
                    append(comment, annotation.range.first, annotation.range.last + 1)
                }
                cursor = annotation.range.last + 1
            }
            append(comment, cursor, comment.length)
        }
        return cleaned.replace(MULTI_SPACE_PATTERN, " ").trim()
    }

    private companion object {
        private val ANNOTATION_PATTERN = Regex("@([A-Za-z]+)(=([^;]*))?;?")
        private val SUPPORTED_RELATION_TYPES =
            setOf("MANY_TO_ONE", "ONE_TO_ONE", "1:1", "*:1", "MANYTOONE", "ONETOONE")
        private val REFERENCE_ALIASES = setOf("REFERENCE", "REF")
        private val REF_AGGREGATE_ALIASES = setOf("REFAGGREGATE")
        private val REF_ID_ALIASES = setOf("REFID")
        private val RELATION_ALIASES = setOf("RELATION", "REL")
        private val LAZY_ALIASES = setOf("LAZY", "L")
        private val COUNT_ALIASES = setOf("COUNT", "C")
        private val COLUMN_RELATION_ALIASES =
            REFERENCE_ALIASES + REF_AGGREGATE_ALIASES + REF_ID_ALIASES + RELATION_ALIASES + LAZY_ALIASES + COUNT_ALIASES
        private val MULTI_SPACE_PATTERN = Regex("\\s{2,}")
    }
}

internal data class ColumnRelationMetadata(
    val referenceTable: String? = null,
    val explicitRelationType: String? = null,
    val lazy: Boolean? = null,
    val countHint: String? = null,
    val refAggregate: String? = null,
    val cleanedComment: String = "",
)

private data class RelationParsedAnnotation(
    val key: String,
    val value: String,
    val range: IntRange,
    val hasExplicitValue: Boolean,
)

private data class ResolvedBooleanAnnotation(
    val value: Boolean? = null,
    val explicit: Boolean = false,
)
