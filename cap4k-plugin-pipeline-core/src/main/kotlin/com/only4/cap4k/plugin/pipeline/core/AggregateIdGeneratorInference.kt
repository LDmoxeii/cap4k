package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateIdGeneratorControl
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import java.util.Locale

internal object AggregateIdGeneratorInference {
    fun infer(
        entities: List<EntityModel>,
        tables: List<DbTableSnapshot>,
    ): List<AggregateIdGeneratorControl> {
        val tableByName = tables.associateBy { it.tableName.lowercase(Locale.ROOT) }

        return entities.mapNotNull { entity ->
            if (entity.valueObject) {
                return@mapNotNull null
            }
            val table = tableByName[entity.tableName.lowercase(Locale.ROOT)] ?: return@mapNotNull null
            val entityIdGenerator = table.entityIdGenerator?.trim().orEmpty()
            if (entityIdGenerator.isBlank()) {
                return@mapNotNull null
            }

            AggregateIdGeneratorControl(
                entityName = entity.name,
                entityPackageName = entity.packageName,
                tableName = entity.tableName,
                idFieldName = entity.idField.name,
                entityIdGenerator = entityIdGenerator,
            )
        }
    }
}
