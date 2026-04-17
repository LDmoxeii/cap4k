package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import java.util.Locale

private val UNIQUE_IDENTIFIER_SPLIT_REGEX = Regex("(?<=[a-z0-9])(?=[A-Z])|[^A-Za-z0-9]+")

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
        val fieldsByNormalizedName = entity.fields.associateBy { field ->
            uniqueLowerCamel(field.name).lowercase(Locale.ROOT)
        }
        val resolvedColumns = columns.map { column ->
            column to (
                fieldsByName[column.lowercase(Locale.ROOT)]
                    ?: fieldsByNormalizedName[uniqueLowerCamel(column).lowercase(Locale.ROOT)]
                )
        }
        val missingColumns = resolvedColumns.filter { (_, field) -> field == null }.map { (column, _) -> column }
        require(missingColumns.isEmpty()) {
            "Unique constraint columns not found in entity ${entity.name}: ${missingColumns.joinToString(", ")}"
        }
        return resolvedColumns.map { (column, field) ->
            requireNotNull(field).copy(name = uniqueLowerCamel(column))
        }
    }
}

private fun uniqueLowerCamel(value: String): String {
    val parts = value.trim()
        .split(UNIQUE_IDENTIFIER_SPLIT_REGEX)
        .filter { it.isNotEmpty() }
    if (parts.isEmpty()) return value

    val head = parts.first().lowercase(Locale.ROOT)
    val tail = parts.drop(1).joinToString(separator = "") { token ->
        token.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
    }
    return head + tail
}

internal fun aggregateTableSegment(tableName: String): String = tableName.lowercase(Locale.ROOT)
