package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class EntityArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val domainRoot = requireRelativeModule(config, "domain")
        val planning = AggregateEnumPlanning.from(model, config.basePackage, config.typeRegistry)

        return model.entities.map { entity ->
            val scalarFields = entity.fields.map { field ->
                mapOf(
                    "name" to field.name,
                    "type" to planning.resolveFieldType(entity.packageName, field),
                    "nullable" to field.nullable,
                    "defaultValue" to field.defaultValue,
                    "typeBinding" to field.typeBinding,
                    "enumItems" to field.enumItems,
                )
            }
            val relationPlan = AggregateRelationPlanning.planFor(entity, model.aggregateRelations)
            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/entity.kt.peb",
                outputPath = "$domainRoot/src/main/kotlin/${entity.packageName.replace(".", "/")}/${entity.name}.kt",
                context = mapOf(
                    "packageName" to entity.packageName,
                    "typeName" to entity.name,
                    "comment" to entity.comment,
                    "tableName" to entity.tableName,
                    "idField" to entity.idField,
                    "imports" to relationPlan.imports,
                    "fields" to scalarFields,
                    "scalarFields" to scalarFields,
                    "relationFields" to relationPlan.relationFields,
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
