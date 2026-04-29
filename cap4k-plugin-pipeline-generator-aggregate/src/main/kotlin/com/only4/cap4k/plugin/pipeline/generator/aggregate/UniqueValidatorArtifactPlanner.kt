package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class UniqueValidatorArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val plannedSelections = model.entities.map { entity ->
            entity to AggregateUniqueConstraintPlanning.from(entity)
        }.filter { (_, selections) -> selections.isNotEmpty() }
        if (plannedSelections.isEmpty()) return emptyList()

        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        return plannedSelections.flatMap { (entity, selections) ->
            val packageKey = aggregatePackageKey(config, entity.packageName)
            val packageName = artifactLayout.aggregateUniqueValidatorPackage(packageKey)
            val queryPackageName = artifactLayout.aggregateUniqueQueryPackage(packageKey)
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
                generatedKotlinArtifact(
                    config = config,
                    artifactLayout = artifactLayout,
                    moduleRole = "application",
                    templateId = "aggregate/unique_validator.kt.peb",
                    packageName = packageName,
                    typeName = selection.validatorTypeName,
                    context = mapOf(
                        "packageName" to packageName,
                        "typeName" to selection.validatorTypeName,
                        "queryTypeName" to selection.queryTypeName,
                        "queryTypeFqn" to "$queryPackageName.${selection.queryTypeName}",
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
                )
            }
        }
    }

    private fun uniqueSimpleType(type: String): String =
        type.removeSuffix("?").substringAfterLast(".")
}
