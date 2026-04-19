package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceProviderControl
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import java.util.Locale

internal object AggregatePersistenceProviderInference {
    fun infer(
        entities: List<EntityModel>,
        tables: List<DbTableSnapshot>,
    ): List<AggregatePersistenceProviderControl> {
        val tableByName = tables.associateBy { it.tableName.lowercase(Locale.ROOT) }

        return entities.mapNotNull { entity ->
            val table = tableByName[entity.tableName.lowercase(Locale.ROOT)] ?: return@mapNotNull null
            if (table.dynamicInsert == null && table.dynamicUpdate == null && table.softDeleteColumn == null) {
                return@mapNotNull null
            }

            val columnNames = table.columns.map { it.name.lowercase(Locale.ROOT) }.toSet()
            val softDeleteColumn = table.softDeleteColumn
            if (softDeleteColumn != null) {
                require(softDeleteColumn.lowercase(Locale.ROOT) in columnNames) {
                    "softDeleteColumn $softDeleteColumn does not exist on table ${table.tableName}"
                }
            }

            val fieldNameByColumnName = entity.fields.associateBy(
                keySelector = { it.name.lowercase(Locale.ROOT) },
                valueTransform = { it.name },
            )
            val versionColumns = table.columns.filter { it.version == true }
            require(versionColumns.size <= 1) {
                "multiple explicit version columns found for table ${table.tableName}"
            }
            val versionFieldName = versionColumns
                .singleOrNull()
                ?.let { versionColumn -> fieldNameByColumnName[versionColumn.name.lowercase(Locale.ROOT)] }

            AggregatePersistenceProviderControl(
                entityName = entity.name,
                entityPackageName = entity.packageName,
                tableName = entity.tableName,
                dynamicInsert = table.dynamicInsert,
                dynamicUpdate = table.dynamicUpdate,
                softDeleteColumn = softDeleteColumn,
                idFieldName = entity.idField.name,
                versionFieldName = versionFieldName,
            )
        }
    }
}
