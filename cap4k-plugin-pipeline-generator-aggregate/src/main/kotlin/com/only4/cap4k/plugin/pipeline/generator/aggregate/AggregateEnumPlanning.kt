package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition

internal class AggregateEnumPlanning private constructor(
    private val sharedEnumFqns: Map<String, String>,
    private val localEnumFqns: Map<LocalEnumKey, String>,
    private val typeRegistry: Map<String, String>,
) {
    fun resolveFieldType(typeName: String, enumItems: List<EnumItemModel>): String {
        if ('.' in typeName) {
            return typeName
        }
        if (enumItems.isNotEmpty()) {
            val localFqn = localEnumFqns[LocalEnumKey(typeBinding = typeName, enumItems = enumItems)]
            if (localFqn != null) {
                return localFqn
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
        val typeName = field.typeBinding?.takeIf { it.isNotBlank() } ?: field.type
        return resolveFieldType(typeName, field.enumItems)
    }

    companion object {
        fun from(model: CanonicalModel, typeRegistry: Map<String, String>): AggregateEnumPlanning {
            val aggregateBasePackage = model.entities
                .asSequence()
                .mapNotNull(::aggregateBasePackage)
                .firstOrNull()
            val sharedEnumFqns = buildSharedEnumFqns(model.sharedEnums, aggregateBasePackage)
            sharedEnumFqns.keys.firstOrNull { it in typeRegistry }?.let { typeName ->
                throw IllegalArgumentException(
                    "ambiguous type binding for $typeName: matches both shared enum and general type registry"
                )
            }
            val localEnumFqns = buildLocalEnumFqns(model.entities)
            return AggregateEnumPlanning(
                sharedEnumFqns = sharedEnumFqns,
                localEnumFqns = localEnumFqns,
                typeRegistry = typeRegistry,
            )
        }

        private fun buildSharedEnumFqns(
            definitions: List<SharedEnumDefinition>,
            aggregateBasePackage: String?,
        ): Map<String, String> {
            val grouped = definitions.groupBy { it.typeName.trim() }
            grouped.entries.firstOrNull { it.value.size > 1 }?.key?.let { duplicated ->
                throw IllegalArgumentException("duplicate shared enum definition: $duplicated")
            }
            return grouped.mapValues { (_, values) ->
                val definition = values.single()
                val packageName = resolveSharedEnumPackageName(definition.packageName, aggregateBasePackage)
                if (packageName.isBlank()) {
                    definition.typeName
                } else {
                    "$packageName.${definition.typeName}"
                }
            }
        }

        private fun resolveSharedEnumPackageName(packageName: String, aggregateBasePackage: String?): String {
            val trimmed = packageName.trim()
            if (trimmed.isBlank()) {
                return aggregateBasePackage?.let { "$it.shared.enums" }.orEmpty()
            }
            if ('.' in trimmed) {
                return trimmed
            }
            if (aggregateBasePackage.isNullOrBlank()) {
                return "$trimmed.enums"
            }
            return "$aggregateBasePackage.$trimmed.enums"
        }

        private fun buildLocalEnumFqns(entities: List<EntityModel>): Map<LocalEnumKey, String> {
            val grouped = entities
                .flatMap { entity ->
                    entity.fields.mapNotNull { field ->
                        val typeBinding = field.typeBinding?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        if (field.enumItems.isEmpty()) {
                            return@mapNotNull null
                        }
                        LocalEnumKey(typeBinding = typeBinding, enumItems = field.enumItems) to
                            buildLocalEnumFqn(entity, typeBinding)
                    }
                }
                .groupBy({ it.first }, { it.second })

            grouped.entries.firstOrNull { it.value.distinct().size > 1 }?.let { entry ->
                throw IllegalArgumentException("ambiguous local enum binding for ${entry.key.typeBinding}")
            }
            return grouped.mapValues { (_, values) -> values.first() }
        }

        private fun buildLocalEnumFqn(entity: EntityModel, typeName: String): String =
            if (entity.packageName.isBlank()) {
                "enums.$typeName"
            } else {
                "${entity.packageName}.enums.$typeName"
            }

        private fun aggregateBasePackage(entity: EntityModel): String? {
            val marker = ".aggregates."
            val packageName = entity.packageName
            val markerIndex = packageName.indexOf(marker)
            if (markerIndex >= 0) {
                return packageName.substring(0, markerIndex)
            }
            return packageName.substringBeforeLast('.', missingDelimiterValue = "").ifBlank { null }
        }
    }
}

private data class LocalEnumKey(
    val typeBinding: String,
    val enumItems: List<EnumItemModel>,
)
