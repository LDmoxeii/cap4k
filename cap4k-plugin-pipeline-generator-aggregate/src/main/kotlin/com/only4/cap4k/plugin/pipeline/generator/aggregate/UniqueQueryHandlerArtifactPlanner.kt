package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class UniqueQueryHandlerArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val plannedSelections = model.entities.map { entity ->
            entity to AggregateUniqueConstraintPlanning.from(entity)
        }.filter { (_, selections) -> selections.isNotEmpty() }
        if (plannedSelections.isEmpty()) return emptyList()

        val adapterRoot = requireRelativeModule(config, "adapter")
        return plannedSelections.flatMap { (entity, selections) ->
            val tableSegment = aggregateTableSegment(entity.tableName)
            selections.map { selection ->
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "adapter",
                    templateId = "aggregate/unique_query_handler.kt.peb",
                    outputPath = "$adapterRoot/src/main/kotlin/${config.basePackage.replace(".", "/")}/adapter/queries/$tableSegment/unique/${selection.queryHandlerTypeName}.kt",
                    context = mapOf(
                        "packageName" to "${config.basePackage}.adapter.queries.$tableSegment.unique",
                        "typeName" to selection.queryHandlerTypeName,
                        "queryTypeName" to selection.queryTypeName,
                        "queryTypeFqn" to "${config.basePackage}.application.queries.$tableSegment.unique.${selection.queryTypeName}",
                    ),
                    conflictPolicy = config.templates.conflictPolicy,
                )
            }
        }
    }
}
