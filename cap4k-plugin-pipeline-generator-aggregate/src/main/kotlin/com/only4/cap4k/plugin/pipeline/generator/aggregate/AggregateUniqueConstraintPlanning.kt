package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import java.util.Locale

internal data class AggregateUniqueConstraintSelection(
    val suffix: String,
    val requestProps: List<FieldModel>,
    val idType: String,
    val excludeIdParamName: String,
    val queryTypeName: String,
    val queryHandlerTypeName: String,
    val validatorTypeName: String,
)

internal object AggregateUniqueConstraintPlanning {
    fun from(entity: EntityModel): List<AggregateUniqueConstraintSelection> {
        return entity.uniqueConstraints.map { columns ->
            val selectedFields = selectConstraintFields(entity, columns)
            val suffix = selectedFields.joinToString(separator = "") { field ->
                field.name.replaceFirstChar { it.uppercase() }
            }

            AggregateUniqueConstraintSelection(
                suffix = suffix,
                requestProps = selectedFields,
                idType = entity.idField.type,
                excludeIdParamName = "exclude${entity.name}Id",
                queryTypeName = "Unique${entity.name}${suffix}Qry",
                queryHandlerTypeName = "Unique${entity.name}${suffix}QryHandler",
                validatorTypeName = "Unique${entity.name}${suffix}",
            )
        }
    }

    private fun selectConstraintFields(entity: EntityModel, columns: List<String>): List<FieldModel> {
        val columnSet = columns.map { it.lowercase(Locale.ROOT) }.toSet()
        val selected = entity.fields.filter { field ->
            columnSet.contains(field.name.lowercase(Locale.ROOT))
        }
        val selectedNameSet = selected.map { it.name.lowercase(Locale.ROOT) }.toSet()
        val missingColumns = columnSet.filterNot { selectedNameSet.contains(it) }
        require(missingColumns.isEmpty()) {
            "Unique constraint columns not found in entity ${entity.name}: ${missingColumns.joinToString(", ")}"
        }
        return selected
    }
}

internal fun aggregateTableSegment(tableName: String): String = tableName.lowercase(Locale.ROOT)
