package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class UniqueQueryArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val plannedSelections = model.entities.map { entity ->
            entity to AggregateUniqueConstraintPlanning.from(entity)
        }.filter { (_, selections) -> selections.isNotEmpty() }
        if (plannedSelections.isEmpty()) return emptyList()

        val applicationRoot = requireRelativeModule(config, "application")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        return plannedSelections.flatMap { (entity, selections) ->
            val tableSegment = aggregateTableSegment(entity.tableName)
            val packageName = artifactLayout.aggregateUniqueQueryPackage(tableSegment)
            selections.map { selection ->
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "application",
                    templateId = "aggregate/unique_query.kt.peb",
                    outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, selection.queryTypeName),
                    context = mapOf(
                        "packageName" to packageName,
                        "typeName" to selection.queryTypeName,
                        "entityName" to entity.name,
                        "requestProps" to selection.requestProps,
                        "idType" to selection.idType,
                        "excludeIdParamName" to selection.excludeIdParamName,
                    ),
                    conflictPolicy = config.templates.conflictPolicy,
                )
            }
        }
    }
}
