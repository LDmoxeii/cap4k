package com.only4.cap4k.plugin.pipeline.generator.common.types

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalEnumCatalog
import com.only4.cap4k.plugin.pipeline.api.CanonicalEnumDescriptor
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import com.only4.cap4k.plugin.pipeline.api.ownerAggregate

object CanonicalTypeSymbolRegistryFactory {
    fun from(
        config: ProjectConfig,
        model: CanonicalModel,
        artifactLayout: ArtifactLayoutResolver,
    ): TypeSymbolRegistry =
        TypeSymbolRegistry().apply {
            config.typeRegistryFqns().forEach { (simpleName, fqn) ->
                register(
                    TypeSymbolIdentity(
                        packageName = fqn.substringBeforeLast('.', missingDelimiterValue = ""),
                        typeName = simpleName,
                        source = PROJECT_TYPE_REGISTRY_SOURCE,
                    )
                )
            }

            model.strongIds.forEach { strongId ->
                register(
                    TypeSymbolIdentity(
                        packageName = strongId.packageName,
                        typeName = strongId.typeName,
                        source = STRONG_ID_SOURCE,
                        ownerAggregateName = strongId.ownerAggregateName,
                    )
                )
            }

            manifestEnumSymbols(model, artifactLayout).forEach(::register)

            model.valueObjects.forEach { valueObject ->
                register(
                    TypeSymbolIdentity(
                        packageName = valueObject.packageName,
                        typeName = valueObject.name,
                        source = MANIFEST_VALUE_OBJECT_SOURCE,
                        ownerAggregateName = valueObject.ownerAggregate,
                        manifestOwned = true,
                        shared = valueObject.aggregates.isEmpty(),
                    )
                )
            }
        }

    private fun manifestEnumSymbols(
        model: CanonicalModel,
        artifactLayout: ArtifactLayoutResolver,
    ): List<TypeSymbolIdentity> {
        val sharedDefinitions = model.sharedEnums.filter { it.aggregates.isEmpty() }
        val localDefinitions = model.sharedEnums.filter { it.aggregates.isNotEmpty() }
        val sharedCatalog = CanonicalEnumCatalog.from(
            model.copy(sharedEnums = sharedDefinitions),
            artifactLayout,
            emptyMap(),
        )
        val localCatalog = CanonicalEnumCatalog.from(
            model.copy(sharedEnums = localDefinitions),
            artifactLayout,
            emptyMap(),
        )
        val selection = ManifestEnumCatalogSelection.from(
            model = model,
            sharedCatalog = sharedCatalog,
            localCatalog = localCatalog,
        )

        return model.sharedEnums.flatMap { definition ->
            val ownerAggregateName = definition.aggregates.singleOrNull()
            val shared = definition.aggregates.isEmpty()
            selection.descriptorsFor(definition).map { descriptor ->
                TypeSymbolIdentity(
                    packageName = descriptor.fqn.substringBeforeLast('.', missingDelimiterValue = ""),
                    typeName = descriptor.typeName,
                    source = MANIFEST_ENUM_SOURCE,
                    ownerAggregateName = ownerAggregateName,
                    manifestOwned = true,
                    shared = shared,
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
                }
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
            sharedCatalog: CanonicalEnumCatalog,
            localCatalog: CanonicalEnumCatalog,
        ): ManifestEnumCatalogSelection =
            ManifestEnumCatalogSelection(
                sharedByTypeName = sharedCatalog.sharedEnums.associateBy { it.typeName },
                localByKey = localCatalog.localEnums
                    .mapNotNull { descriptor ->
                        descriptor.ownerPackageName?.let { ownerPackageName ->
                            ManifestLocalEnumKey(ownerPackageName, descriptor.typeName) to descriptor
                        }
                    }
                    .toMap(),
                entities = model.entities,
                aggregateRootNameByEntity = buildAggregateRootNameByEntity(model.entities),
            )

        private fun buildAggregateRootNameByEntity(
            entities: List<EntityModel>,
        ): Map<ManifestEntityKey, String> {
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
                        val parent = entitiesByKey[ManifestEntityKey(entity.packageName, parentEntityName)]
                            ?: entitiesByName[parentEntityName]?.singleOrNull()
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

private fun EntityModel.key(): ManifestEntityKey =
    ManifestEntityKey(packageName = packageName, name = name)
