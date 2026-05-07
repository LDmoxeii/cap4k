package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class FactoryArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val derivedTypeReferences = AggregateDerivedTypeReferences.from(model)
        val planning = AggregateEnumPlanning.from(model, artifactLayout, config.typeRegistry)

        return model.entities.filter { it.aggregateRoot }.map { entity ->
            val entityTypeFqn = derivedTypeReferences.entityFqn(entity)
            val packageName = artifactLayout.aggregateFactoryPackage(entity.packageName)
            val typeName = "${entity.name}Factory"
            val resolvedPolicy = model.aggregateSpecialFieldResolvedPolicies.singleOrNull {
                it.entityName == entity.name && it.entityPackageName == entity.packageName
            }
            val payloadFields = resolvedPolicy
                ?.writeSurface
                ?.createAllowedFields
                ?.toSet()
                ?.let { createAllowedFields ->
                    entity.fields
                        .filter { it.name in createAllowedFields }
                        .map { field ->
                            val fieldType = planning.resolveFieldType(entity.packageName, field)
                            mapOf(
                                "name" to field.name,
                                "type" to fieldType,
                                "typeName" to fieldType,
                                "nullable" to field.nullable,
                            )
                        }
                }
                ?: emptyList()

            checkedInKotlinArtifact(
                config = config,
                artifactLayout = artifactLayout,
                moduleRole = "domain",
                templateId = "aggregate/factory.kt.peb",
                packageName = packageName,
                typeName = typeName,
                context = mapOf(
                    "packageName" to packageName,
                    "typeName" to typeName,
                    "payloadTypeName" to "Payload",
                    "payloadMetadataName" to "${entity.name}Payload",
                    "payloadWriteSurfaceResolved" to (resolvedPolicy != null),
                    "payloadFields" to payloadFields,
                    "entityName" to entity.name,
                    "entityTypeFqn" to entityTypeFqn,
                    "aggregateName" to entity.name,
                    "comment" to entity.comment,
                ),
            )
        }
    }
}
