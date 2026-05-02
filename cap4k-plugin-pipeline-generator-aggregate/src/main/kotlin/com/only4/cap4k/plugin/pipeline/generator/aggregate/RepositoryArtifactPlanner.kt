package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class RepositoryArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val entitiesByName = model.entities
            .groupBy { it.name }

        return model.repositories.map { repository ->
            val entity = requireUniqueRepositoryEntity(
                repositoryName = repository.name,
                entityName = repository.entityName,
                entities = entitiesByName[repository.entityName].orEmpty(),
            )
            generatedKotlinArtifact(
                config = config,
                artifactLayout = artifactLayout,
                moduleRole = "adapter",
                templateId = "aggregate/repository.kt.peb",
                packageName = repository.packageName,
                typeName = repository.name,
                context = mapOf(
                    "packageName" to repository.packageName,
                    "typeName" to repository.name,
                    "entityName" to repository.entityName,
                    "entityTypeFqn" to entity?.let { buildEntityFqn(it.packageName, repository.entityName) }.orEmpty(),
                    "aggregateName" to repository.entityName,
                    "idType" to repository.idType,
                    "supportQuerydsl" to false,
                    "imports" to aggregateTypeImports(repository.idType),
                ),
            )
        }
    }

    private fun requireUniqueRepositoryEntity(
        repositoryName: String,
        entityName: String,
        entities: List<EntityModel>,
    ): EntityModel {
        if (entities.size != 1) {
            error("repository $repositoryName requires exactly one entity named $entityName, but found ${entities.size}")
        }
        return entities.single()
    }

    private fun buildEntityFqn(packageName: String, entityName: String): String =
        if (packageName.isBlank()) {
            entityName
        } else {
            "$packageName.$entityName"
        }
}
