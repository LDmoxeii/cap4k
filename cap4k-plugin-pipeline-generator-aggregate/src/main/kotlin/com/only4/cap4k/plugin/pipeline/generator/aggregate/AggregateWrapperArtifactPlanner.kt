package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class AggregateWrapperArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val domainRoot = requireRelativeModule(config, "domain")
        val derivedTypeReferences = AggregateDerivedTypeReferences.from(model)

        return model.entities.map { entity ->
            val typeName = "Agg${entity.name}"
            val relativePath = "${entity.packageName.replace(".", "/")}/$typeName.kt"

            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/wrapper.kt.peb",
                outputPath = "$domainRoot/src/main/kotlin/$relativePath",
                context = mapOf(
                    "packageName" to entity.packageName,
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
