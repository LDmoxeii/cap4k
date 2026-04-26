package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateColumnJpaModel
import com.only4.cap4k.plugin.pipeline.api.AggregateEntityJpaModel
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryConverterKind
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryEntry
import java.util.Locale

internal object AggregateJpaControlInference {
    fun fromModel(
        entities: List<EntityModel>,
        schema: DbSchemaSnapshot?,
        sharedEnums: List<SharedEnumDefinition>,
        typeRegistry: Map<String, TypeRegistryEntry>,
        artifactLayout: ArtifactLayoutResolver,
    ): List<AggregateEntityJpaModel> {
        val sharedEnumsByType = buildSharedEnumFqns(
            definitions = sharedEnums,
            artifactLayout = artifactLayout,
        )
        sharedEnumsByType.keys.firstOrNull { it in typeRegistry }?.let { typeName ->
            throw IllegalArgumentException(
                "ambiguous type binding for $typeName: matches both shared enum and general type registry"
            )
        }
        val localEnumOwnership = buildLocalEnumOwnership(entities)
        val tableByName = schema?.tables?.associateBy { it.tableName.lowercase(Locale.ROOT) }.orEmpty()

        return entities.map { entity ->
            val table = requireNotNull(tableByName[entity.tableName.lowercase(Locale.ROOT)]) {
                "missing db table snapshot for entity ${entity.name}"
            }
            val columnByName = table.columns.associateBy { it.name.lowercase(Locale.ROOT) }
            val primaryKeyColumnNames = table.primaryKey.map { it.lowercase(Locale.ROOT) }.toSet()

            AggregateEntityJpaModel(
                entityName = entity.name,
                entityPackageName = entity.packageName,
                entityEnabled = true,
                tableName = entity.tableName,
                columns = entity.fields.map { field ->
                    val fieldColumnName = field.columnName ?: field.name
                    val column = requireNotNull(columnByName[fieldColumnName.lowercase(Locale.ROOT)]) {
                        "missing db column snapshot for field ${entity.name}.${field.name}"
                    }
                    val converter = resolveConverterBinding(
                        typeBinding = column.typeBinding,
                        ownerPackageName = entity.packageName,
                        hasLocalEnumOwner = LocalEnumOwnerKey(
                            ownerPackageName = entity.packageName,
                            typeBinding = column.typeBinding.orEmpty(),
                        ) in localEnumOwnership,
                        sharedEnumsByType = sharedEnumsByType,
                        typeRegistry = typeRegistry,
                    )
                    AggregateColumnJpaModel(
                        fieldName = field.name,
                        columnName = column.name,
                        isId = column.name.lowercase(Locale.ROOT) in primaryKeyColumnNames,
                        converterTypeFqn = converter?.typeFqn,
                        converterClassFqn = converter?.converterClassFqn,
                    )
                },
            )
        }
    }

    private fun resolveConverterBinding(
        typeBinding: String?,
        ownerPackageName: String,
        hasLocalEnumOwner: Boolean,
        sharedEnumsByType: Map<String, String>,
        typeRegistry: Map<String, TypeRegistryEntry>,
    ): ConverterBinding? {
        val normalizedTypeBinding = typeBinding?.takeIf { it.isNotBlank() } ?: return null
        val sharedEnumFqn = sharedEnumsByType[normalizedTypeBinding]
        val registryEntry = typeRegistry[normalizedTypeBinding]

        return when {
            sharedEnumFqn != null && hasLocalEnumOwner -> throw IllegalArgumentException(
                "ambiguous enum ownership for $normalizedTypeBinding: " +
                    "matches both shared enum and local enum in $ownerPackageName"
            )
            registryEntry != null && hasLocalEnumOwner -> throw IllegalArgumentException(
                "ambiguous enum ownership for $normalizedTypeBinding: " +
                    "matches both local enum in $ownerPackageName and general type registry"
            )
            sharedEnumFqn != null -> ConverterBinding(sharedEnumFqn, "$sharedEnumFqn.Converter")
            hasLocalEnumOwner -> buildLocalEnumFqn(ownerPackageName, normalizedTypeBinding)
                .let { ConverterBinding(it, "$it.Converter") }
            registryEntry != null -> registryEntry.toConverterBinding()
            isFqn(normalizedTypeBinding) -> ConverterBinding(
                typeFqn = normalizedTypeBinding,
                converterClassFqn = "$normalizedTypeBinding.Converter",
            )
            normalizedTypeBinding in builtInTypeNames -> null
            else -> throw IllegalArgumentException(
                "unresolved type binding for $ownerPackageName.$normalizedTypeBinding: " +
                    "expected enum manifest, type registry, FQN, or built-in type"
            )
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
        artifactLayout: ArtifactLayoutResolver,
    ): Map<String, String> {
        return definitions.associate { definition ->
            val packageName = resolveSharedEnumPackageName(
                packageName = definition.packageName,
                artifactLayout = artifactLayout,
            )
            val fqn = if (packageName.isBlank()) {
                definition.typeName
            } else {
                "$packageName.${definition.typeName}"
            }
            definition.typeName to fqn
        }
    }

    private fun resolveSharedEnumPackageName(packageName: String, artifactLayout: ArtifactLayoutResolver): String {
        val trimmed = packageName.trim()
        if ('.' in trimmed) {
            return trimmed
        }
        return artifactLayout.aggregateSharedEnumPackage(trimmed)
    }

    private fun buildLocalEnumFqn(ownerPackageName: String, typeName: String): String {
        if (ownerPackageName.isBlank()) {
            return "enums.$typeName"
        }
        return "$ownerPackageName.enums.$typeName"
    }

    private fun TypeRegistryEntry.toConverterBinding(): ConverterBinding? =
        when (converter.kind) {
            TypeRegistryConverterKind.NONE -> null
            TypeRegistryConverterKind.NESTED -> ConverterBinding(fqn, "$fqn.Converter")
            TypeRegistryConverterKind.EXPLICIT -> ConverterBinding(
                typeFqn = fqn,
                converterClassFqn = requireNotNull(converter.fqn) {
                    "explicit converter FQN is required for $fqn"
                },
            )
        }

    private fun isFqn(value: String): Boolean =
        '.' in value

    private val builtInTypeNames = setOf(
        "Any",
        "Array",
        "Boolean",
        "Byte",
        "Char",
        "Collection",
        "Double",
        "Float",
        "Int",
        "Iterable",
        "List",
        "Long",
        "Map",
        "MutableCollection",
        "MutableIterable",
        "MutableList",
        "MutableMap",
        "MutableSet",
        "Nothing",
        "Number",
        "Pair",
        "Sequence",
        "Set",
        "Short",
        "String",
        "Triple",
        "Unit",
    )

}

private data class ConverterBinding(
    val typeFqn: String,
    val converterClassFqn: String,
)

private data class LocalEnumOwnerKey(
    val ownerPackageName: String,
    val typeBinding: String,
)
