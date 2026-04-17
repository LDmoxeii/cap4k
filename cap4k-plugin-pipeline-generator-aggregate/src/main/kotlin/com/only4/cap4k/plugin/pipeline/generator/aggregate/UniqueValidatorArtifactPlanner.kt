package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class UniqueValidatorArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val plannedSelections = model.entities.map { entity ->
            entity to AggregateUniqueConstraintPlanning.from(entity)
        }.filter { (_, selections) -> selections.isNotEmpty() }
        if (plannedSelections.isEmpty()) return emptyList()

        val applicationRoot = requireRelativeModule(config, "application")
        return plannedSelections.flatMap { (entity, selections) ->
            selections.map { selection ->
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "application",
                    templateId = "aggregate/unique_validator.kt.peb",
                    outputPath = "$applicationRoot/src/main/kotlin/${config.basePackage.replace(".", "/")}/application/validators/${entity.tableName}/unique/${selection.validatorTypeName}.kt",
                    context = mapOf(
                        "packageName" to "${config.basePackage}.application.validators.${entity.tableName}.unique",
                        "typeName" to selection.validatorTypeName,
                        "queryTypeName" to selection.queryTypeName,
                        "queryTypeFqn" to "${config.basePackage}.application.queries.${entity.tableName}.unique.${selection.queryTypeName}",
                        "requestProps" to selection.requestProps,
                        "idType" to selection.idType,
                        "excludeIdParamName" to selection.excludeIdParamName,
                        "entityName" to entity.name,
                    ),
                    conflictPolicy = config.templates.conflictPolicy,
                )
            }
        }
    }
}
