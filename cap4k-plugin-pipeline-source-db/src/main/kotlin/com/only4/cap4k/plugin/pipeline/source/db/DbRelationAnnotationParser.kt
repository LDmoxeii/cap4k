package com.only4.cap4k.plugin.pipeline.source.db

import java.util.Locale

internal class DbRelationAnnotationParser {
    fun parseTable(comment: String): TableRelationMetadata {
        val annotations = parseAnnotations(comment)
        val parentTable = resolveAnnotationValue(
            annotations = annotations,
            aliases = setOf("PARENT", "P"),
            conflictMessage = "conflicting @Parent/@P annotations on the same table comment.",
        )
        val valueObject = annotations.any { it.key in VALUE_OBJECT_ALIASES }
        val aggregateRoot = resolveBooleanAnnotationValue(
            annotations = annotations,
            aliases = setOf("AGGREGATEROOT", "ROOT", "R"),
            conflictMessage = "conflicting @AggregateRoot/@Root/@R annotations on the same table comment.",
            invalidMessagePrefix = "invalid @AggregateRoot/@Root/@R boolean value: ",
        )
            ?: (parentTable == null && !valueObject)
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
            ),
            countHint = resolveAnnotationValue(
                annotations = annotations,
                aliases = setOf("COUNT", "C"),
                conflictMessage = "conflicting @Count/@C annotations on the same column comment.",
            ),
        )
    }

    private fun parseAnnotations(comment: String): List<RelationParsedAnnotation> {
        return ANNOTATION_PATTERN.findAll(comment)
            .map { match ->
                RelationParsedAnnotation(
                    key = match.groupValues[1].uppercase(Locale.ROOT),
                    value = match.groupValues.getOrElse(3) { "" }.trim(),
                    range = match.range,
                )
            }
            .toList()
    }

    private fun resolveAnnotationValue(
        annotations: List<RelationParsedAnnotation>,
        aliases: Set<String>,
        conflictMessage: String,
    ): String? {
        val values = annotations
            .asSequence()
            .filter { it.key in aliases }
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
    ): Boolean? {
        val values = annotations
            .asSequence()
            .filter { it.key in aliases }
            .map { it.value }
            .distinct()
            .toList()
        if (values.isEmpty()) {
            return null
        }

        val invalidValue = values.firstOrNull { it.toBooleanStrictOrNull() == null }
        require(invalidValue == null) { invalidMessagePrefix + invalidValue }

        val booleans = values
            .map { it.toBooleanStrict() }
            .distinct()
        require(booleans.size <= 1) { conflictMessage }
        return booleans.single()
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
)

private data class RelationParsedAnnotation(
    val key: String,
    val value: String,
    val range: IntRange,
)
