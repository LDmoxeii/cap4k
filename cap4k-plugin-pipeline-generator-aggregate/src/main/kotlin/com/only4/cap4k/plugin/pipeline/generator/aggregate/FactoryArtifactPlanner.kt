package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class FactoryArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val domainRoot = requireRelativeModule(config, "domain")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val derivedTypeReferences = AggregateDerivedTypeReferences.from(model)

        return model.entities.filter { it.aggregateRoot }.map { entity ->
            val entityTypeFqn = derivedTypeReferences.entityFqn(entity)
            val packageName = artifactLayout.aggregateFactoryPackage(entity.packageName)
            val typeName = "${entity.name}Factory"

            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/factory.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(domainRoot, packageName, typeName),
                context = mapOf(
                    "packageName" to packageName,
                    "typeName" to typeName,
                    "payloadTypeName" to "Payload",
                    "entityName" to entity.name,
                    "entityTypeFqn" to entityTypeFqn,
                    "aggregateName" to entity.name,
                    "comment" to entity.comment,
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
