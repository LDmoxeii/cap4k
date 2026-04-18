package com.only4.cap4k.plugin.pipeline.source.db

import java.util.Locale

internal class DbRelationAnnotationParser {
    fun parseTable(comment: String): TableRelationMetadata {
        val annotations = parseAnnotations(comment)
        val parentTable = annotations["Parent"] ?: annotations["P"]
        val valueObject = annotations.containsKey("ValueObject") || annotations.containsKey("VO")
        val aggregateRoot = (annotations["AggregateRoot"] ?: annotations["Root"] ?: annotations["R"])
            ?.toBooleanStrictOrNull()
            ?: (parentTable == null && !valueObject)
        return TableRelationMetadata(
            parentTable = parentTable,
            aggregateRoot = aggregateRoot,
            valueObject = valueObject,
        )
    }

    fun parseColumn(comment: String): ColumnRelationMetadata {
        val annotations = parseAnnotations(comment)
        val relationValue = annotations["Relation"] ?: annotations["Rel"]
        val relation = relationValue?.uppercase(Locale.ROOT)
        require(relation == null || relation in SUPPORTED_RELATION_TYPES) {
            "unsupported relation type in first slice: $relationValue"
        }

        return ColumnRelationMetadata(
            referenceTable = annotations["Reference"] ?: annotations["Ref"],
            explicitRelationType = when (relation) {
                "ONE_TO_ONE", "1:1", "ONETOONE" -> "ONE_TO_ONE"
                "MANY_TO_ONE", "*:1", "MANYTOONE" -> "MANY_TO_ONE"
                else -> null
            },
            lazy = (annotations["Lazy"] ?: annotations["L"])?.toBooleanStrictOrNull(),
            countHint = annotations["Count"] ?: annotations["C"],
        )
    }

    private fun parseAnnotations(comment: String): Map<String, String> {
        return ANNOTATION_PATTERN.findAll(comment)
            .associate { match ->
                val key = match.groupValues[1]
                val value = match.groupValues.getOrElse(3) { "" }.trim()
                key to value
            }
    }

    private companion object {
        private val ANNOTATION_PATTERN = Regex("@([A-Za-z]+)(=([^;]*))?;?")
        private val SUPPORTED_RELATION_TYPES =
            setOf("MANY_TO_ONE", "ONE_TO_ONE", "1:1", "*:1", "MANYTOONE", "ONETOONE")
    }
}

internal data class TableRelationMetadata(
    val parentTable: String? = null,
    val aggregateRoot: Boolean = true,
    val valueObject: Boolean = false,
)

internal data class ColumnRelationMetadata(
    val referenceTable: String? = null,
    val explicitRelationType: String? = null,
    val lazy: Boolean? = null,
    val countHint: String? = null,
)
