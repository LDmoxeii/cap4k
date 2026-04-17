package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class FactoryArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val domainRoot = requireRelativeModule(config, "domain")

        return model.entities.map { entity ->
            val entityTypeFqn = if (entity.packageName.isBlank()) {
                entity.name
            } else {
                "${entity.packageName}.${entity.name}"
            }

            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/factory.kt.peb",
                outputPath = "$domainRoot/src/main/kotlin/${entity.packageName.replace(".", "/")}/factory/${entity.name}Factory.kt",
                context = mapOf(
                    "packageName" to "${entity.packageName}.factory",
                    "typeName" to "${entity.name}Factory",
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
