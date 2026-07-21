package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind
import com.only4.cap4k.plugin.pipeline.api.StrongIdModel

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
            val aggregateName = aggregateRootName(entity, model.entities)
            val strongId = entity?.let { resolveAggregateRootStrongId(model, it) }
            val idType = strongId?.typeName ?: repository.idType
            val idTypeFqn = strongId?.fqn()
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
                    "aggregateElement" to aggregateElementContext(
                        aggregate = aggregateName,
                        name = repository.name,
                        packageName = repository.packageName,
                        description = "",
                        type = "repository",
                    ),
                    "entityName" to repository.entityName,
                    "entityTypeFqn" to entity?.let { buildEntityFqn(it.packageName, repository.entityName) }.orEmpty(),
                    "aggregateName" to aggregateName,
                    "idType" to idType,
                    "idTypeFqn" to idTypeFqn,
                    "supportQuerydsl" to false,
                    "imports" to aggregateTypeImports(idType),
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

    private fun resolveAggregateRootStrongId(
        model: CanonicalModel,
        entity: EntityModel,
    ): StrongIdModel? =
        model.strongIds.singleOrNull {
            it.kind == StrongIdKind.AGGREGATE_ROOT &&
                it.ownerAggregateName == entity.name &&
                it.ownerAggregatePackageName == entity.packageName &&
                it.typeName == entity.idField.type.shortTypeName()
        }

    private fun StrongIdModel.fqn(): String = "${packageName}.${typeName}"

    private fun String.shortTypeName(): String = removeSuffix("?").substringAfterLast('.')
}
