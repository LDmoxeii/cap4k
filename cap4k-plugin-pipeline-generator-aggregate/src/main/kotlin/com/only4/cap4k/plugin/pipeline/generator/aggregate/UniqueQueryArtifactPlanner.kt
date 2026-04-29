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

        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        return plannedSelections.flatMap { (entity, selections) ->
            val packageKey = aggregatePackageKey(config, entity.packageName)
            val packageName = artifactLayout.aggregateUniqueQueryPackage(packageKey)
            selections.map { selection ->
                generatedKotlinArtifact(
                    config = config,
                    artifactLayout = artifactLayout,
                    moduleRole = "application",
                    templateId = "aggregate/unique_query.kt.peb",
                    packageName = packageName,
                    typeName = selection.queryTypeName,
                    context = mapOf(
                        "packageName" to packageName,
                        "typeName" to selection.queryTypeName,
                        "entityName" to entity.name,
                        "requestProps" to selection.requestProps,
                        "idType" to selection.idType,
                        "excludeIdParamName" to selection.excludeIdParamName,
                    ),
                )
            }
        }
    }
}
