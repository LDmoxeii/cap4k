package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class EntityArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val planning = AggregateEnumPlanning.from(model, artifactLayout, config.typeRegistry)
        val defaultProjector = AggregateEntityDefaultProjector()

        return model.entities.map { entity ->
            val entityJpa = model.aggregateEntityJpa.singleOrNull {
                it.entityName == entity.name && it.entityPackageName == entity.packageName
            }
            val scalarJpaByField = entityJpa?.columns.orEmpty().associateBy { it.fieldName }
            val controlsByField = model.aggregatePersistenceFieldControls
                .filter { it.entityName == entity.name && it.entityPackageName == entity.packageName }
                .associateBy { it.fieldName }
            val idGeneratorControl = model.aggregateIdGeneratorControls.firstOrNull {
                it.entityName == entity.name && it.entityPackageName == entity.packageName
            }
            val providerControl = model.aggregatePersistenceProviderControls.firstOrNull {
                it.entityName == entity.name && it.entityPackageName == entity.packageName
            }
            val relationPlan = AggregateRelationPlanning.planFor(
                entity = entity,
                relations = model.aggregateRelations,
                inverseRelations = model.aggregateInverseRelations,
            )
            val relationJoinColumns = relationPlan.relationFields
                .filter {
                    when (it["relationType"]) {
                        AggregateRelationType.MANY_TO_ONE.name,
                        AggregateRelationType.ONE_TO_ONE.name,
                        -> it["readOnly"] != true
                        else -> false
                    }
                }
                .mapNotNull { it["joinColumn"] as? String }
                .toSet()
            val idColumnName = providerControl?.let { control ->
                requireNotNull(scalarJpaByField[control.idFieldName]) {
                    "missing aggregate JPA metadata for ${entity.packageName}.${entity.name}.${control.idFieldName}"
                }.columnName
            }
            val versionColumnName = providerControl?.versionFieldName?.let { versionFieldName ->
                requireNotNull(scalarJpaByField[versionFieldName]) {
                    "missing aggregate JPA metadata for ${entity.packageName}.${entity.name}.${versionFieldName}"
                }.columnName
            }
            val softDeleteSql = providerControl?.softDeleteColumn?.let { column ->
                buildSoftDeleteSql(
                    tableName = providerControl.tableName,
                    softDeleteColumn = column,
                    idColumnName = requireNotNull(idColumnName),
                    versionColumnName = versionColumnName,
                )
            }
            val softDeleteWhereClause = providerControl?.softDeleteColumn?.let { column ->
                "\"$column\" = 0"
            }
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
                        val isCustomGeneratorIdField =
                            jpa.isId && idGeneratorControl?.idFieldName == field.name
                        val generatedValueStrategy = if (isCustomGeneratorIdField) {
                            null
                        } else {
                            control?.generatedValueStrategy
                        }
                        val generatedValueGenerator = if (isCustomGeneratorIdField) {
                            idGeneratorControl.entityIdGenerator
                        } else {
                            null
                        }
                        val genericGeneratorName = if (isCustomGeneratorIdField) {
                            idGeneratorControl.entityIdGenerator
                        } else {
                            null
                        }
                        val genericGeneratorStrategy = if (isCustomGeneratorIdField) {
                            idGeneratorControl.entityIdGenerator
                        } else {
                            null
                        }
                        mapOf(
                            "fieldName" to field.name,
                            "fieldType" to fieldType,
                            "name" to field.name,
                            "type" to fieldType,
                            "nullable" to field.nullable,
                            "defaultValue" to defaultProjector.project(
                                fieldPath = "${entity.packageName}.${entity.name}.${field.name}",
                                fieldType = fieldType,
                                nullable = field.nullable,
                                rawDefaultValue = field.defaultValue,
                                enumItems = emptyList(),
                            ),
                            "typeBinding" to field.typeBinding,
                            "enumItems" to field.enumItems,
                            "columnName" to jpa.columnName,
                            "isId" to jpa.isId,
                            "converterTypeRef" to jpa.converterTypeFqn,
                            "converterClassRef" to jpa.converterClassFqn,
                            "generatedValueStrategy" to generatedValueStrategy,
                            "generatedValueGenerator" to generatedValueGenerator,
                            "genericGeneratorName" to genericGeneratorName,
                            "genericGeneratorStrategy" to genericGeneratorStrategy,
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
            generatedKotlinArtifact(
                config = config,
                artifactLayout = artifactLayout,
                moduleRole = "domain",
                templateId = "aggregate/entity.kt.peb",
                packageName = entity.packageName,
                typeName = entity.name,
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
                    "hasConverterFields" to scalarFields.any { it["converterClassRef"] != null },
                    "hasGeneratedValueFields" to scalarFields.any {
                        it["isId"] == true && it["generatedValueStrategy"] == "IDENTITY"
                    },
                    "hasGenericGeneratorFields" to scalarFields.any { it["genericGeneratorName"] != null },
                    "hasVersionFields" to scalarFields.any { it["isVersion"] == true },
                    "dynamicInsert" to (providerControl?.dynamicInsert == true),
                    "dynamicUpdate" to (providerControl?.dynamicUpdate == true),
                    "softDeleteSql" to softDeleteSql,
                    "softDeleteWhereClause" to softDeleteWhereClause,
                    "jpaImports" to relationPlan.jpaImports,
                    "imports" to relationPlan.imports,
                    "fields" to scalarFields,
                    "scalarFields" to scalarFields,
                    "relationFields" to relationPlan.relationFields,
                ),
            )
        }
    }

    private fun buildSoftDeleteSql(
        tableName: String,
        softDeleteColumn: String,
        idColumnName: String,
        versionColumnName: String?,
    ): String =
        if (versionColumnName != null) {
            "update \"$tableName\" set \"$softDeleteColumn\" = 1 where \"$idColumnName\" = ? and \"$versionColumnName\" = ?"
        } else {
            "update \"$tableName\" set \"$softDeleteColumn\" = 1 where \"$idColumnName\" = ?"
        }
}
