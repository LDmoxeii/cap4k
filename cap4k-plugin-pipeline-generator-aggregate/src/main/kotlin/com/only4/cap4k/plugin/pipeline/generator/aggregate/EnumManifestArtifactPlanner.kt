package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalEnumCatalog
import com.only4.cap4k.plugin.pipeline.api.CanonicalEnumDescriptor
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition

class EnumManifestArtifactPlanner : GeneratorProvider {
    override val id: String = "enum"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        if (model.sharedEnums.isEmpty()) {
            return emptyList()
        }

        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val catalog = AggregateEnumPlanning.from(model, artifactLayout, config.typeRegistry.entries)
        val manifestEnums = ManifestEnumCatalogSelection.from(model, catalog)
        return model.sharedEnums.flatMap { definition ->
            manifestEnums.descriptorsFor(definition).map { descriptor ->
                val packageName = descriptor.packageName()

                generatedKotlinArtifact(
                    config = config,
                    artifactLayout = artifactLayout,
                    moduleRole = "domain",
                    templateId = "aggregate/enum.kt.peb",
                    packageName = packageName,
                    typeName = definition.typeName,
                    context = mapOf(
                        "packageName" to packageName,
                        "typeName" to definition.typeName,
                        "items" to definition.items.map { item ->
                            mapOf(
                                "value" to item.value,
                                "name" to item.name,
                                "description" to item.description,
                            )
                        },
                        "buildingBlock" to mapOf(
                            "tag" to "enum",
                            "tagKotlinStringLiteral" to "enum".toKotlinStringLiteral(),
                            "name" to definition.typeName,
                            "nameKotlinStringLiteral" to definition.typeName.toKotlinStringLiteral(),
                            "packageName" to definition.packageName,
                            "packageNameKotlinStringLiteral" to definition.packageName.toKotlinStringLiteral(),
                            "description" to "",
                            "descriptionKotlinStringLiteral" to "".toKotlinStringLiteral(),
                            "aggregates" to definition.aggregates,
                            "aggregateKotlinStringLiterals" to definition.aggregates.map { it.toKotlinStringLiteral() },
                            "eventName" to "",
                            "eventNameKotlinStringLiteral" to "".toKotlinStringLiteral(),
                            "family" to "enum",
                            "familyKotlinStringLiteral" to "enum".toKotlinStringLiteral(),
                            "variant" to "",
                            "variantKotlinStringLiteral" to "".toKotlinStringLiteral(),
                        ),
                    ),
                    generatorId = id,
                )
            }
        }
    }
}

private class ManifestEnumCatalogSelection(
    private val sharedByTypeName: Map<String, CanonicalEnumDescriptor>,
    private val localByKey: Map<ManifestLocalEnumKey, CanonicalEnumDescriptor>,
    private val entities: List<EntityModel>,
    private val aggregateRootNameByEntity: Map<ManifestEntityKey, String>,
) {
    fun descriptorsFor(definition: SharedEnumDefinition): List<CanonicalEnumDescriptor> =
        if (definition.aggregates.isEmpty()) {
            listOf(
                requireNotNull(sharedByTypeName[definition.typeName]) {
                    "missing shared enum catalog entry for ${definition.typeName}"
                },
            )
        } else {
            localOwnerKeys(definition).map { key ->
                requireNotNull(localByKey[key]) {
                    "missing local enum catalog entry for ${key.ownerPackageName}.${key.typeName}"
                }
            }
        }

    private fun localOwnerKeys(definition: SharedEnumDefinition): List<ManifestLocalEnumKey> {
        val ownerAggregateName = requireNotNull(definition.aggregates.singleOrNull()) {
            "enum ${definition.typeName} may declare at most one aggregate"
        }
        return entities
            .filter { entity -> aggregateRootNameByEntity[entity.key()] == ownerAggregateName }
            .map { entity -> ManifestLocalEnumKey(entity.packageName, definition.typeName) }
            .distinct()
            .ifEmpty { listOf(ManifestLocalEnumKey(ownerAggregateName, definition.typeName)) }
    }

    companion object {
        fun from(
            model: CanonicalModel,
            catalog: CanonicalEnumCatalog,
        ): ManifestEnumCatalogSelection =
            ManifestEnumCatalogSelection(
                sharedByTypeName = catalog.sharedEnums.associateBy { it.typeName },
                localByKey = catalog.localEnums
                    .mapNotNull { descriptor ->
                        descriptor.ownerPackageName?.let { ownerPackageName ->
                            ManifestLocalEnumKey(ownerPackageName, descriptor.typeName) to descriptor
                        }
                    }
                    .toMap(),
                entities = model.entities,
                aggregateRootNameByEntity = buildAggregateRootNameByEntity(model.entities),
            )

        private fun buildAggregateRootNameByEntity(entities: List<EntityModel>): Map<ManifestEntityKey, String> {
            val entitiesByKey = entities.associateBy { it.key() }
            val entitiesByName = entities.groupBy { it.name }
            val resolving = mutableSetOf<ManifestEntityKey>()
            val resolved = linkedMapOf<ManifestEntityKey, String>()

            fun resolve(entity: EntityModel): String {
                val key = entity.key()
                resolved[key]?.let { return it }
                if (!resolving.add(key)) {
                    return entity.name
                }
                val parentEntityName = entity.parentEntityName?.takeIf { it.isNotBlank() }
                val rootName = when {
                    entity.aggregateRoot -> entity.name
                    parentEntityName == null -> entity.name
                    else -> {
                        val parent = entitiesByKey[ManifestEntityKey(entity.packageName, parentEntityName)] ?:
                            entitiesByName[parentEntityName]?.singleOrNull()
                        parent?.let { resolve(it) } ?: entity.name
                    }
                }
                resolving.remove(key)
                resolved[key] = rootName
                return rootName
            }

            entities.forEach { resolve(it) }
            return resolved
        }
    }
}

private data class ManifestLocalEnumKey(
    val ownerPackageName: String,
    val typeName: String,
)

private data class ManifestEntityKey(
    val packageName: String,
    val name: String,
)

private fun EntityModel.key(): ManifestEntityKey = ManifestEntityKey(packageName = packageName, name = name)

private fun CanonicalEnumDescriptor.packageName(): String =
    fqn.substringBeforeLast('.', missingDelimiterValue = "")
