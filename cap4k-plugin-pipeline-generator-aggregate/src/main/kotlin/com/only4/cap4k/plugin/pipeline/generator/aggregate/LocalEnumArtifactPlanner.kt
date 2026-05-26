package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ValueObjectScope

internal class LocalEnumArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val aggregateRootNameByEntity = buildAggregateRootNameByEntity(model.entities)
        val candidates = linkedMapOf<LocalEnumCandidateKey, LocalEnumCandidate>()
        model.entities.forEach { entity ->
            entity.fields.forEach { field ->
                val typeBinding = field.typeBinding?.takeIf { it.isNotBlank() } ?: return@forEach
                if (field.enumItems.isEmpty()) return@forEach
                if (
                    model.valueObjects.any { valueObject ->
                        valueObject.name == typeBinding &&
                            when (valueObject.scope) {
                                ValueObjectScope.SHARED -> true
                                ValueObjectScope.AGGREGATE ->
                                    valueObject.aggregate == aggregateRootNameByEntity[entity.key()]
                            }
                    }
                ) return@forEach
                val key = LocalEnumCandidateKey(entity.packageName, typeBinding)
                candidates.putIfAbsent(key, LocalEnumCandidate(entity.packageName, typeBinding, field))
            }
        }

        return candidates.values.map { local ->
            val packageName = artifactLayout.aggregateLocalEnumPackage(local.ownerPackageName)
            val typeName = local.typeBinding

            generatedKotlinArtifact(
                config = config,
                artifactLayout = artifactLayout,
                moduleRole = "domain",
                templateId = "aggregate/enum.kt.peb",
                packageName = packageName,
                typeName = typeName,
                context = mapOf(
                    "packageName" to packageName,
                    "typeName" to typeName,
                    "items" to local.field.enumItems.map { item ->
                        mapOf(
                            "value" to item.value,
                            "name" to item.name,
                            "description" to item.description,
                        )
                    },
                ),
            )
        }
    }

    private fun buildAggregateRootNameByEntity(entities: List<EntityModel>): Map<EntityKey, String> {
        val entitiesByKey = entities.associateBy { it.key() }
        val entitiesByName = entities.groupBy { it.name }
        val resolving = mutableSetOf<EntityKey>()
        val resolved = linkedMapOf<EntityKey, String>()

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
                    val parent = entitiesByKey[EntityKey(entity.packageName, parentEntityName)] ?:
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

private data class LocalEnumCandidateKey(
    val ownerPackageName: String,
    val typeBinding: String,
)

private data class LocalEnumCandidate(
    val ownerPackageName: String,
    val typeBinding: String,
    val field: FieldModel,
)

private data class EntityKey(
    val packageName: String,
    val name: String,
)

private fun EntityModel.key(): EntityKey = EntityKey(packageName = packageName, name = name)
