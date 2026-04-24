package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class RepositoryArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val adapterRoot = requireRelativeModule(config, "adapter")
        val entityByName = model.entities
            .groupBy { it.name }
            .mapValues { (_, entities) -> entities.singleOrNull() }

        return model.repositories.map { repository ->
            val entity = entityByName[repository.entityName]
            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "adapter",
                templateId = "aggregate/repository.kt.peb",
                outputPath = "$adapterRoot/src/main/kotlin/${repository.packageName.replace(".", "/")}/${repository.name}.kt",
                context = mapOf(
                    "packageName" to repository.packageName,
                    "typeName" to repository.name,
                    "entityName" to repository.entityName,
                    "entityTypeFqn" to entity?.let { buildEntityFqn(it.packageName, repository.entityName) }.orEmpty(),
                    "aggregateName" to repository.entityName,
                    "idType" to repository.idType,
                    "supportQuerydsl" to false,
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }

    private fun buildEntityFqn(packageName: String, entityName: String): String =
        if (packageName.isBlank()) {
            entityName
        } else {
            "$packageName.$entityName"
        }
}
