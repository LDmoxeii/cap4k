package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.RepositoryModel
import com.only4.cap4k.plugin.pipeline.api.SchemaModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class UniqueQueryHandlerArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val plannedSelections = model.entities.map { entity ->
            entity to AggregateUniqueConstraintPlanning.from(entity)
        }.filter { (_, selections) -> selections.isNotEmpty() }
        if (plannedSelections.isEmpty()) return emptyList()

        val adapterRoot = requireRelativeModule(config, "adapter")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        return plannedSelections.flatMap { (entity, selections) ->
            val tableSegment = aggregateTableSegment(entity.tableName)
            val packageName = artifactLayout.aggregateUniqueQueryHandlerPackage(tableSegment)
            val queryPackageName = artifactLayout.aggregateUniqueQueryPackage(tableSegment)
            val repository = requireUniqueRepository(
                handlerTypeName = "unique query handler",
                entityName = entity.name,
                repositories = model.repositories.filter { it.entityName == entity.name },
            )
            val schema = requireUniqueSchema(
                handlerTypeName = "unique query handler",
                entityName = entity.name,
                schemas = model.schemas.filter { it.entityName == entity.name },
            )
            selections.map { selection ->
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "adapter",
                    templateId = "aggregate/unique_query_handler.kt.peb",
                    outputPath = artifactLayout.kotlinSourcePath(adapterRoot, packageName, selection.queryHandlerTypeName),
                    context = mapOf(
                        "packageName" to packageName,
                        "typeName" to selection.queryHandlerTypeName,
                        "queryTypeName" to selection.queryTypeName,
                        "queryTypeFqn" to "$queryPackageName.${selection.queryTypeName}",
                        "repositoryTypeName" to repository.name,
                        "repositoryTypeFqn" to "${repository.packageName}.${repository.name}",
                        "schemaTypeName" to schema.name,
                        "schemaTypeFqn" to "${schema.packageName}.${schema.name}",
                        "entityTypeName" to entity.name,
                        "entityTypeFqn" to "${entity.packageName}.${entity.name}",
                        "whereProps" to selection.requestProps.map { it.name },
                        "idPropName" to entity.idField.name,
                        "excludeIdParamName" to selection.excludeIdParamName,
                    ),
                    conflictPolicy = config.templates.conflictPolicy,
                )
            }
        }
    }

    private fun requireUniqueRepository(
        handlerTypeName: String,
        entityName: String,
        repositories: List<RepositoryModel>,
    ): RepositoryModel {
        if (repositories.size != 1) {
            error("$handlerTypeName for entity $entityName requires exactly one repository, but found ${repositories.size}")
        }
        return repositories.single()
    }

    private fun requireUniqueSchema(
        handlerTypeName: String,
        entityName: String,
        schemas: List<SchemaModel>,
    ): SchemaModel {
        if (schemas.size != 1) {
            error("$handlerTypeName for entity $entityName requires exactly one schema, but found ${schemas.size}")
        }
        return schemas.single()
    }
}
