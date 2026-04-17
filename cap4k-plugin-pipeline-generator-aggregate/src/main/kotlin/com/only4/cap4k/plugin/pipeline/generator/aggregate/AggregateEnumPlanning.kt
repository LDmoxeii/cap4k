package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition

internal class AggregateEnumPlanning private constructor(
    private val sharedEnumFqns: Map<String, String>,
    private val localEnumFqns: Map<LocalEnumOwnerKey, String>,
    private val typeRegistry: Map<String, String>,
) {
    fun resolveFieldType(typeName: String, enumItems: List<EnumItemModel>): String {
        return resolveFieldType(ownerPackageName = null, typeName = typeName, enumItems = enumItems)
    }

    fun resolveFieldType(ownerPackageName: String?, typeName: String, enumItems: List<EnumItemModel>): String {
        if ('.' in typeName) {
            return typeName
        }
        if (ownerPackageName != null) {
            localEnumFqns[LocalEnumOwnerKey(ownerPackageName = ownerPackageName, typeBinding = typeName)]?.let {
                return it
            }
        }
        val sharedFqn = sharedEnumFqns[typeName]
        if (sharedFqn != null) {
            return sharedFqn
        }
        val mapped = typeRegistry[typeName]
        if (mapped != null) {
            return mapped
        }
        return typeName
    }

    fun resolveFieldType(field: FieldModel): String {
        return resolveFieldType(ownerPackageName = null, field = field)
    }

    fun resolveFieldType(ownerPackageName: String?, field: FieldModel): String {
        val typeName = field.typeBinding?.takeIf { it.isNotBlank() } ?: field.type
        return resolveFieldType(ownerPackageName, typeName, field.enumItems)
    }

    companion object {
        fun from(model: CanonicalModel, basePackage: String, typeRegistry: Map<String, String>): AggregateEnumPlanning {
            val sharedEnumFqns = buildSharedEnumFqns(model.sharedEnums, sharedEnumBasePackage(basePackage))
            sharedEnumFqns.keys.firstOrNull { it in typeRegistry }?.let { typeName ->
                throw IllegalArgumentException(
                    "ambiguous type binding for $typeName: matches both shared enum and general type registry"
                )
            }
            val localEnumFqns = buildLocalEnumFqns(model.entities)
            localEnumFqns.keys.firstOrNull { key -> key.typeBinding in sharedEnumFqns }?.let { key ->
                throw IllegalArgumentException(
                    "ambiguous enum ownership for ${key.typeBinding}: matches both shared enum and local enum in ${key.ownerPackageName}"
                )
            }
            return AggregateEnumPlanning(
                sharedEnumFqns = sharedEnumFqns,
                localEnumFqns = localEnumFqns,
                typeRegistry = typeRegistry,
            )
        }

        private fun buildSharedEnumFqns(
            definitions: List<SharedEnumDefinition>,
            sharedEnumBasePackage: String?,
        ): Map<String, String> {
            val grouped = definitions.groupBy { it.typeName.trim() }
            grouped.entries.firstOrNull { it.value.size > 1 }?.key?.let { duplicated ->
                throw IllegalArgumentException("duplicate shared enum definition: $duplicated")
            }
            return grouped.mapValues { (_, values) ->
                val definition = values.single()
                val packageName = resolveSharedEnumPackageName(definition.packageName, sharedEnumBasePackage)
                if (packageName.isBlank()) {
                    definition.typeName
                } else {
                    "$packageName.${definition.typeName}"
                }
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

        private fun buildLocalEnumFqns(entities: List<EntityModel>): Map<LocalEnumOwnerKey, String> {
            val grouped = entities
                .flatMap { entity ->
                    entity.fields.mapNotNull { field ->
                        val typeBinding = field.typeBinding?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        if (field.enumItems.isEmpty()) {
                            return@mapNotNull null
                        }
                        LocalEnumOwnerKey(
                            ownerPackageName = entity.packageName,
                            typeBinding = typeBinding,
                        ) to LocalEnumDefinition(
                            fqn = buildLocalEnumFqn(entity, typeBinding),
                            enumItems = field.enumItems,
                        )
                    }
                }
                .groupBy({ it.first }, { it.second })

            grouped.entries.firstOrNull { entry ->
                entry.value.map { it.enumItems }.distinct().size > 1
            }?.let { entry ->
                throw IllegalArgumentException("conflicting local enum definition for ${entry.value.first().fqn}")
            }
            return grouped.mapValues { (_, values) -> values.first().fqn }
        }

        private fun buildLocalEnumFqn(entity: EntityModel, typeName: String): String =
            if (entity.packageName.isBlank()) {
                "enums.$typeName"
            } else {
                "${entity.packageName}.enums.$typeName"
            }

        private fun sharedEnumBasePackage(basePackage: String): String? {
            val trimmed = basePackage.trim()
            if (trimmed.isBlank()) {
                return null
            }
            return "$trimmed.domain"
        }
    }
}

private data class LocalEnumOwnerKey(
    val ownerPackageName: String,
    val typeBinding: String,
)

private data class LocalEnumDefinition(
    val fqn: String,
    val enumItems: List<EnumItemModel>,
)
