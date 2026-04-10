package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class RepositoryArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val adapterRoot = requireRelativeModule(config, "adapter")

        return model.repositories.map { repository ->
            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "adapter",
                templateId = "aggregate/repository.kt.peb",
                outputPath = "$adapterRoot/src/main/kotlin/${repository.packageName.replace(".", "/")}/${repository.name}.kt",
                context = mapOf(
                    "packageName" to repository.packageName,
                    "typeName" to repository.name,
                    "entityName" to repository.entityName,
                    "idType" to repository.idType,
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
