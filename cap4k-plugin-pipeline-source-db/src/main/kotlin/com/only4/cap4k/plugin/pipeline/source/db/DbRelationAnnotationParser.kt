package com.only4.cap4k.plugin.pipeline.source.db

import java.util.Locale

internal class DbRelationAnnotationParser {
    fun parseTable(comment: String): TableRelationMetadata {
        val annotations = parseAnnotations(comment)
        val parentTable = resolveAnnotationValue(
            annotations = annotations,
            aliases = setOf("PARENT", "P"),
            conflictMessage = "conflicting @Parent/@P annotations on the same table comment.",
            blankValueMessage = "blank @Parent/@P value is not allowed.",
            missingValueMessage = "missing value for @Parent/@P annotation.",
        )
        val valueObject = resolvePresenceAnnotation(
            annotations = annotations,
            aliases = VALUE_OBJECT_ALIASES,
            invalidValueMessage = "invalid @ValueObject/@VO annotation: explicit values are not supported.",
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
        val aggregateRoot = aggregateRootAnnotation.value ?: (parentTable == null && !valueObject)
        return TableRelationMetadata(
            parentTable = parentTable,
            aggregateRoot = aggregateRoot,
            valueObject = valueObject,
            cleanedComment = stripRecognizedAnnotations(comment, TABLE_RELATION_ALIASES),
        )
    }

    fun parseColumn(comment: String): ColumnRelationMetadata {
        val annotations = parseAnnotations(comment)
        val relationValue = resolveAnnotationValue(
            annotations = annotations,
            aliases = setOf("RELATION", "REL"),
            conflictMessage = "conflicting @Relation/@Rel annotations on the same column comment.",
            blankValueMessage = "blank @Relation/@Rel value is not allowed.",
            missingValueMessage = "missing value for @Relation/@Rel annotation.",
        )
        val relation = relationValue?.uppercase(Locale.ROOT)
        require(relation == null || relation in SUPPORTED_RELATION_TYPES) {
            "unsupported relation type in first slice: $relationValue"
        }

        return ColumnRelationMetadata(
            referenceTable = resolveAnnotationValue(
                annotations = annotations,
                aliases = setOf("REFERENCE", "REF"),
                conflictMessage = "conflicting @Reference/@Ref annotations on the same column comment.",
                blankValueMessage = "blank @Reference/@Ref value is not allowed.",
                missingValueMessage = "missing value for @Reference/@Ref annotation.",
            ),
            explicitRelationType = when (relation) {
                "ONE_TO_ONE", "1:1", "ONETOONE" -> "ONE_TO_ONE"
                "MANY_TO_ONE", "*:1", "MANYTOONE" -> "MANY_TO_ONE"
                else -> null
            },
            lazy = resolveBooleanAnnotationValue(
                annotations = annotations,
                aliases = setOf("LAZY", "L"),
                conflictMessage = "conflicting @Lazy/@L annotations on the same column comment.",
                invalidMessagePrefix = "invalid @Lazy/@L boolean value: ",
            ).value,
            countHint = resolveAnnotationValue(
                annotations = annotations,
                aliases = setOf("COUNT", "C"),
                conflictMessage = "conflicting @Count/@C annotations on the same column comment.",
                blankValueMessage = "blank @Count/@C value is not allowed.",
                missingValueMessage = "missing value for @Count/@C annotation.",
            ),
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

    private fun resolvePresenceAnnotation(
        annotations: List<RelationParsedAnnotation>,
        aliases: Set<String>,
        invalidValueMessage: String,
    ): Boolean {
        val matchingAnnotations = annotations.filter { it.key in aliases }
        require(matchingAnnotations.none { it.hasExplicitValue }) { invalidValueMessage }
        return matchingAnnotations.isNotEmpty()
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
        private val VALUE_OBJECT_ALIASES = setOf("VALUEOBJECT", "VO")
        private val TABLE_RELATION_ALIASES = setOf("PARENT", "P", "AGGREGATEROOT", "ROOT", "R", "VALUEOBJECT", "VO")
        private val COLUMN_RELATION_ALIASES = setOf("REFERENCE", "REF", "RELATION", "REL", "LAZY", "L", "COUNT", "C")
        private val MULTI_SPACE_PATTERN = Regex("\\s{2,}")
    }
}

internal data class TableRelationMetadata(
    val parentTable: String? = null,
    val aggregateRoot: Boolean = true,
    val valueObject: Boolean = false,
    val cleanedComment: String = "",
)

internal data class ColumnRelationMetadata(
    val referenceTable: String? = null,
    val explicitRelationType: String? = null,
    val lazy: Boolean? = null,
    val countHint: String? = null,
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
