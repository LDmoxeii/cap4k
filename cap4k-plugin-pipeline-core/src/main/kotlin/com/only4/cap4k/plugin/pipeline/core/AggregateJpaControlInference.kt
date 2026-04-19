package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateColumnJpaModel
import com.only4.cap4k.plugin.pipeline.api.AggregateEntityJpaModel
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import java.util.Locale

internal object AggregateJpaControlInference {
    fun fromModel(
        entities: List<EntityModel>,
        schema: DbSchemaSnapshot?,
        sharedEnums: List<SharedEnumDefinition>,
    ): List<AggregateEntityJpaModel> {
        val sharedEnumsByType = sharedEnums.associateBy { it.typeName }
        val tableByName = schema?.tables?.associateBy { it.tableName.lowercase(Locale.ROOT) }.orEmpty()

        return entities.map { entity ->
            val table = requireNotNull(tableByName[entity.tableName.lowercase(Locale.ROOT)]) {
                "missing db table snapshot for entity ${entity.name}"
            }
            val columnByName = table.columns.associateBy { it.name.lowercase(Locale.ROOT) }

            AggregateEntityJpaModel(
                entityName = entity.name,
                entityPackageName = entity.packageName,
                entityEnabled = true,
                tableName = entity.tableName,
                columns = entity.fields.map { field ->
                    val column = requireNotNull(columnByName[field.name.lowercase(Locale.ROOT)]) {
                        "missing db column snapshot for field ${entity.name}.${field.name}"
                    }
                    AggregateColumnJpaModel(
                        fieldName = field.name,
                        columnName = column.name,
                        isId = column.name in table.primaryKey,
                        converterTypeFqn = resolveConverterTypeFqn(
                            typeBinding = column.typeBinding,
                            ownerPackageName = entity.packageName,
                            hasLocalEnumItems = field.enumItems.isNotEmpty(),
                            sharedEnumsByType = sharedEnumsByType,
                        ),
                    )
                },
            )
        }
    }

    private fun resolveConverterTypeFqn(
        typeBinding: String?,
        ownerPackageName: String,
        hasLocalEnumItems: Boolean,
        sharedEnumsByType: Map<String, SharedEnumDefinition>,
    ): String? {
        val normalizedTypeBinding = typeBinding?.takeIf { it.isNotBlank() } ?: return null
        val sharedEnum = sharedEnumsByType[normalizedTypeBinding]

        return when {
            sharedEnum != null && !hasLocalEnumItems -> "${sharedEnum.packageName}.$normalizedTypeBinding"
            sharedEnum == null && hasLocalEnumItems -> "$ownerPackageName.$normalizedTypeBinding"
            else -> null
        }
    }
}
