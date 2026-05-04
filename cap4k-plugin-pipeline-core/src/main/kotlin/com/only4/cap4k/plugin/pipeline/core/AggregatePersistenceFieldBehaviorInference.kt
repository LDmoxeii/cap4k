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
            val fieldNameByColumnName = entity.fields.associateBy(
                keySelector = { (it.columnName ?: it.name).lowercase(Locale.ROOT) },
                valueTransform = { it.name },
            )
            table.columns.mapNotNull { column ->
                val hasExplicitControl =
                    column.generatedValueStrategy != null ||
                        column.version != null ||
                        column.managed != null ||
                        column.exposed != null ||
                        column.insertable != null ||
                        column.updatable != null
                if (!hasExplicitControl) {
                    return@mapNotNull null
                }

                AggregatePersistenceFieldControl(
                    entityName = entity.name,
                    entityPackageName = entity.packageName,
                    fieldName = requireNotNull(fieldNameByColumnName[column.name.lowercase(Locale.ROOT)]) {
                        "missing canonical entity field identity for ${entity.name}.${column.name}"
                    },
                    columnName = column.name,
                    generatedValueStrategy = column.generatedValueStrategy,
                    version = column.version,
                    insertable = column.insertable,
                    updatable = column.updatable,
                )
            }
        }
    }
}
