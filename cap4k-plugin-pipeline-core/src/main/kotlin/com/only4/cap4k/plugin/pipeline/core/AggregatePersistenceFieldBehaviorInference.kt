package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceFieldControl
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import java.util.Locale

internal object AggregatePersistenceFieldBehaviorInference {
    fun infer(entities: List<EntityModel>, schema: DbSchemaSnapshot?): List<AggregatePersistenceFieldControl> {
        if (schema == null) {
            return emptyList()
        }

        val tableByName = schema.tables.associateBy { it.tableName.lowercase(Locale.ROOT) }

        return entities.flatMap { entity ->
            val table = tableByName[entity.tableName.lowercase(Locale.ROOT)] ?: return@flatMap emptyList()
            table.columns.mapNotNull { column ->
                val hasExplicitControl =
                    column.generatedValueStrategy != null ||
                        column.version != null ||
                        column.insertable != null ||
                        column.updatable != null
                if (!hasExplicitControl) {
                    return@mapNotNull null
                }

                AggregatePersistenceFieldControl(
                    entityName = entity.name,
                    fieldName = toLowerCamelCase(column.name),
                    columnName = column.name,
                    generatedValueStrategy = column.generatedValueStrategy,
                    version = column.version,
                    insertable = column.insertable,
                    updatable = column.updatable,
                )
            }
        }
    }

    private fun toLowerCamelCase(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            return value
        }
        if ('_' in trimmed) {
            return trimmed
                .split('_')
                .filter { it.isNotBlank() }
                .mapIndexed { index, part ->
                    val normalized = part.lowercase(Locale.ROOT)
                    if (index == 0) {
                        normalized
                    } else {
                        normalized.replaceFirstChar { it.titlecase(Locale.ROOT) }
                    }
                }
                .joinToString("")
        }
        if (trimmed.all { !it.isLetter() || it.isUpperCase() }) {
            return trimmed.lowercase(Locale.ROOT)
        }
        return trimmed.replaceFirstChar { it.lowercase(Locale.ROOT) }
    }
}
