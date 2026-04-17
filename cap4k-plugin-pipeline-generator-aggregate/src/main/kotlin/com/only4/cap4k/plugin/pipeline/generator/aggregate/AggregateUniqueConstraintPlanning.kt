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
        val fieldsByName = entity.fields.associateBy { field ->
            field.name.lowercase(Locale.ROOT)
        }
        val missingColumns = columns.filter { column ->
            fieldsByName[column.lowercase(Locale.ROOT)] == null
        }
        require(missingColumns.isEmpty()) {
            "Unique constraint columns not found in entity ${entity.name}: ${missingColumns.joinToString(", ")}"
        }
        return columns.map { column ->
            fieldsByName.getValue(column.lowercase(Locale.ROOT))
        }
    }
}

internal fun aggregateTableSegment(tableName: String): String = tableName.lowercase(Locale.ROOT)
