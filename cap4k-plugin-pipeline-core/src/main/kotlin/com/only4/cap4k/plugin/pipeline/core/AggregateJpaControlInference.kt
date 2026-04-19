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
        basePackage: String,
    ): List<AggregateEntityJpaModel> {
        val sharedEnumsByType = buildSharedEnumFqns(
            definitions = sharedEnums,
            basePackage = basePackage,
        )
        val localEnumOwnership = buildLocalEnumOwnership(entities)
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
                            hasLocalEnumOwner = LocalEnumOwnerKey(
                                ownerPackageName = entity.packageName,
                                typeBinding = column.typeBinding.orEmpty(),
                            ) in localEnumOwnership,
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
        hasLocalEnumOwner: Boolean,
        sharedEnumsByType: Map<String, String>,
    ): String? {
        val normalizedTypeBinding = typeBinding?.takeIf { it.isNotBlank() } ?: return null
        val sharedEnumFqn = sharedEnumsByType[normalizedTypeBinding]

        return when {
            sharedEnumFqn != null && hasLocalEnumOwner -> throw IllegalArgumentException(
                "ambiguous enum ownership for $normalizedTypeBinding: " +
                    "matches both shared enum and local enum in $ownerPackageName"
            )
            sharedEnumFqn != null -> sharedEnumFqn
            hasLocalEnumOwner -> buildLocalEnumFqn(ownerPackageName, normalizedTypeBinding)
            else -> null
        }
    }

    private fun buildLocalEnumOwnership(entities: List<EntityModel>): Set<LocalEnumOwnerKey> {
        val owners = linkedMapOf<LocalEnumOwnerKey, List<com.only4.cap4k.plugin.pipeline.api.EnumItemModel>>()

        entities.forEach { entity ->
            entity.fields.forEach { field ->
                val typeBinding = field.typeBinding?.takeIf { it.isNotBlank() } ?: return@forEach
                if (field.enumItems.isEmpty()) return@forEach

                val key = LocalEnumOwnerKey(
                    ownerPackageName = entity.packageName,
                    typeBinding = typeBinding,
                )
                val previous = owners[key]
                if (previous != null && previous != field.enumItems) {
                    throw IllegalArgumentException("conflicting local enum definition for ${buildLocalEnumFqn(entity.packageName, typeBinding)}")
                }
                owners.putIfAbsent(key, field.enumItems)
            }
        }

        return owners.keys
    }

    private fun buildSharedEnumFqns(
        definitions: List<SharedEnumDefinition>,
        basePackage: String,
    ): Map<String, String> {
        val sharedEnumBasePackage = sharedEnumBasePackage(basePackage)
        return definitions.associate { definition ->
            val packageName = resolveSharedEnumPackageName(
                packageName = definition.packageName,
                sharedEnumBasePackage = sharedEnumBasePackage,
            )
            val fqn = if (packageName.isBlank()) {
                definition.typeName
            } else {
                "$packageName.${definition.typeName}"
            }
            definition.typeName to fqn
        }
    }

    private fun resolveSharedEnumPackageName(packageName: String, sharedEnumBasePackage: String?): String {
        val trimmed = packageName.trim()
        if (trimmed.isBlank()) {
            return sharedEnumBasePackage?.let { "$it.shared.enums" }.orEmpty()
        }
        if ('.' in trimmed) {
            return trimmed
        }
        if (sharedEnumBasePackage.isNullOrBlank()) {
            return "$trimmed.enums"
        }
        return "$sharedEnumBasePackage.$trimmed.enums"
    }

    private fun buildLocalEnumFqn(ownerPackageName: String, typeName: String): String {
        if (ownerPackageName.isBlank()) {
            return "enums.$typeName"
        }
        return "$ownerPackageName.enums.$typeName"
    }

    private fun sharedEnumBasePackage(basePackage: String): String? {
        val trimmed = basePackage.trim()
        if (trimmed.isBlank()) {
            return null
        }
        return "$trimmed.domain"
    }
}

private data class LocalEnumOwnerKey(
    val ownerPackageName: String,
    val typeBinding: String,
)
