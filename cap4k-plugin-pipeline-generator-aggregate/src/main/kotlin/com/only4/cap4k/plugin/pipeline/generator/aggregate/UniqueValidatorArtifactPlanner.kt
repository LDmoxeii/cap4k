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
            val tableSegment = aggregateTableSegment(entity.tableName)
            val entityCamel = entity.name.replaceFirstChar { it.lowercase() }
            selections.map { selection ->
                val requestProps = selection.requestProps.map { field ->
                    val simpleType = uniqueSimpleType(field.type)
                    mapOf(
                        "name" to field.name,
                        "type" to field.type.removeSuffix("?"),
                        "isString" to (simpleType == "String"),
                        "param" to "${field.name}Field",
                        "varName" to "${field.name}Property",
                    )
                }
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "application",
                    templateId = "aggregate/unique_validator.kt.peb",
                    outputPath = "$applicationRoot/src/main/kotlin/${config.basePackage.replace(".", "/")}/application/validators/$tableSegment/unique/${selection.validatorTypeName}.kt",
                    context = mapOf(
                        "packageName" to "${config.basePackage}.application.validators.$tableSegment.unique",
                        "typeName" to selection.validatorTypeName,
                        "queryTypeName" to selection.queryTypeName,
                        "queryTypeFqn" to "${config.basePackage}.application.queries.$tableSegment.unique.${selection.queryTypeName}",
                        "requestProps" to requestProps,
                        "fieldParams" to requestProps.map { prop ->
                            mapOf(
                                "param" to prop["param"],
                                "default" to prop["name"],
                            )
                        },
                        "idType" to selection.idType,
                        "excludeIdParamName" to selection.excludeIdParamName,
                        "entityIdParam" to "${entityCamel}IdField",
                        "entityIdDefault" to "${entityCamel}Id",
                        "entityIdVar" to "${entityCamel}IdProperty",
                        "entityName" to entity.name,
                    ),
                    conflictPolicy = config.templates.conflictPolicy,
                )
            }
        }
    }

    private fun uniqueSimpleType(type: String): String =
        type.removeSuffix("?").substringAfterLast(".")
}
