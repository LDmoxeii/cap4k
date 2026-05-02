package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class UniqueQueryArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val plannedSelections = AggregateUniqueConstraintPlanning.from(model)
        if (plannedSelections.isEmpty()) return emptyList()

        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        return plannedSelections.flatMap { (entity, selections) ->
            val packageKey = aggregatePackageKey(config, entity.packageName)
            val packageName = artifactLayout.aggregateUniqueQueryPackage(packageKey)
            selections.map { selection ->
                val imports = aggregateTypeImports(selection.idType) +
                    selection.requestProps.flatMap { field -> aggregateTypeImports(field.type) }
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
                        "uniquePhysicalName" to selection.physicalName,
                        "uniqueNormalizedName" to selection.normalizedName,
                        "uniqueResolvedSuffix" to selection.suffix,
                        "uniqueSelectedBusinessFields" to selection.requestProps.map { it.name },
                        "uniqueFilteredControlFields" to selection.filteredControlFields.map { it.name },
                        "imports" to imports.distinct(),
                    ),
                )
            }
        }
    }
}
