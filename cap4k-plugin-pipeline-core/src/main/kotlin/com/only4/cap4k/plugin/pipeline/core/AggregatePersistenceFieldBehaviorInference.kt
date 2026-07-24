package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceFieldControl
import com.only4.cap4k.plugin.pipeline.api.DbIdStrategy
import com.only4.cap4k.plugin.pipeline.api.DbManagedRole
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
                val generatedValueStrategy = column.idStrategy?.toPersistenceStrategy()
                val version = (column.managedRole == DbManagedRole.VERSION).takeIf { it }
                val hasExplicitControl = generatedValueStrategy != null || version != null
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
                    generatedValueStrategy = generatedValueStrategy,
                    version = version,
                )
            }
        }
    }

    private fun DbIdStrategy.toPersistenceStrategy(): String? = when (this) {
        DbIdStrategy.DB_IDENTITY -> "IDENTITY"
        DbIdStrategy.UUID7 -> null
    }
}
