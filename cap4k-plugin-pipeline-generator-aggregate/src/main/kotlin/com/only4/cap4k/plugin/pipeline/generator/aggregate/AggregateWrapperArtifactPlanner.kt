package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class AggregateWrapperArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val domainRoot = requireRelativeModule(config, "domain")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val derivedTypeReferences = AggregateDerivedTypeReferences.from(model)

        return model.entities.map { entity ->
            val typeName = "Agg${entity.name}"
            val packageName = artifactLayout.aggregateWrapperPackage(entity.packageName)

            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/wrapper.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(domainRoot, packageName, typeName),
                context = mapOf(
                    "packageName" to packageName,
                    "typeName" to typeName,
                    "entityName" to entity.name,
                    "entityTypeFqn" to derivedTypeReferences.entityFqn(entity),
                    "factoryTypeName" to "${entity.name}Factory",
                    "factoryTypeFqn" to derivedTypeReferences.factoryFqn(entity),
                    "idType" to entity.idField.type,
                    "comment" to entity.comment,
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
