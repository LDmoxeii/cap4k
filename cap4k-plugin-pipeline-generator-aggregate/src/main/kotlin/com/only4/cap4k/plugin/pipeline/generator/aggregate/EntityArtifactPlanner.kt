package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class EntityArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val domainRoot = requireRelativeModule(config, "domain")
        val planning = AggregateEnumPlanning.from(model, config.basePackage, config.typeRegistry)

        return model.entities.map { entity ->
            val entityJpa = model.aggregateEntityJpa.singleOrNull {
                it.entityName == entity.name && it.entityPackageName == entity.packageName
            }
            val scalarJpaByField = entityJpa?.columns.orEmpty().associateBy { it.fieldName }
            val controlsByField = model.aggregatePersistenceFieldControls
                .filter { it.entityName == entity.name && it.entityPackageName == entity.packageName }
                .associateBy { it.fieldName }
            val relationPlan = AggregateRelationPlanning.planFor(entity, model.aggregateRelations)
            val relationJoinColumns = relationPlan.relationFields
                .filter {
                    when (it["relationType"]) {
                        AggregateRelationType.MANY_TO_ONE.name,
                        AggregateRelationType.ONE_TO_ONE.name,
                        -> true
                        else -> false
                    }
                }
                .mapNotNull { it["joinColumn"] as? String }
                .toSet()
            val scalarFields = entity.fields
                .mapNotNull { field ->
                    val jpa = requireNotNull(scalarJpaByField[field.name]) {
                        "missing aggregate JPA metadata for ${entity.packageName}.${entity.name}.${field.name}"
                    }
                    if (jpa.columnName in relationJoinColumns) {
                        null
                    } else {
                        val control = controlsByField[field.name]
                        val fieldType = planning.resolveFieldType(entity.packageName, field)
                        mapOf(
                            "fieldName" to field.name,
                            "fieldType" to fieldType,
                            "name" to field.name,
                            "type" to fieldType,
                            "nullable" to field.nullable,
                            "defaultValue" to field.defaultValue,
                            "typeBinding" to field.typeBinding,
                            "enumItems" to field.enumItems,
                            "columnName" to jpa.columnName,
                            "isId" to jpa.isId,
                            "converterTypeRef" to jpa.converterTypeFqn,
                            "generatedValueStrategy" to control?.generatedValueStrategy,
                            "isVersion" to (control?.version == true),
                            "insertable" to when {
                                control?.insertable != null -> control.insertable
                                control?.updatable != null -> true
                                else -> null
                            },
                            "updatable" to when {
                                control?.updatable != null -> control.updatable
                                control?.insertable != null -> true
                                else -> null
                            },
                        )
                    }
                }
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
                    "entityJpa" to mapOf(
                        "entityEnabled" to (entityJpa?.entityEnabled ?: true),
                        "tableName" to (entityJpa?.tableName ?: entity.tableName),
                    ),
                    "idField" to entity.idField,
                    "hasConverterFields" to scalarFields.any { it["converterTypeRef"] != null },
                    "hasGeneratedValueFields" to scalarFields.any {
                        it["isId"] == true && it["generatedValueStrategy"] == "IDENTITY"
                    },
                    "hasVersionFields" to scalarFields.any { it["isVersion"] == true },
                    "jpaImports" to relationPlan.jpaImports,
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
