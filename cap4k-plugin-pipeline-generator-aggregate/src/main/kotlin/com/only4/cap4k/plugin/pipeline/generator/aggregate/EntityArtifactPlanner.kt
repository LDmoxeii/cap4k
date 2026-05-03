package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyKind
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import java.util.Locale

internal class EntityArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val planning = AggregateEnumPlanning.from(model, artifactLayout, config.typeRegistry)
        val defaultProjector = AggregateEntityDefaultProjector()
        val identifierQuoteStyle = resolveIdentifierQuoteStyle(config)

        return model.entities.map { entity ->
            val entityJpa = model.aggregateEntityJpa.singleOrNull {
                it.entityName == entity.name && it.entityPackageName == entity.packageName
            }
            val resolvedPolicy = model.aggregateSpecialFieldResolvedPolicies.singleOrNull {
                it.entityName == entity.name && it.entityPackageName == entity.packageName
            }
            val scalarJpaByField = entityJpa?.columns.orEmpty().associateBy { it.fieldName }
            val controlsByField = model.aggregatePersistenceFieldControls
                .filter { it.entityName == entity.name && it.entityPackageName == entity.packageName }
                .associateBy { it.fieldName }
            val managedByField = resolvedPolicy?.managedFields.orEmpty().associateBy { it.fieldName }
            val idPolicyControl = model.aggregateIdPolicyControls.firstOrNull {
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
                    identifierQuoteStyle = identifierQuoteStyle,
                )
            }
            val softDeleteWhereClause = providerControl?.softDeleteColumn?.let { column ->
                "${quoteIdentifier(column, identifierQuoteStyle)} = 0"
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
                        val idPolicyApplies = jpa.isId && idPolicyControl?.idFieldName == field.name
                        val applicationSideIdStrategy = if (
                            idPolicyApplies &&
                            idPolicyControl.kind == AggregateIdPolicyKind.APPLICATION_SIDE
                        ) {
                            idPolicyControl.strategy
                        } else {
                            null
                        }
                        val generatedValueStrategy = if (
                            idPolicyApplies &&
                            idPolicyControl.kind == AggregateIdPolicyKind.DATABASE_SIDE
                        ) {
                            "IDENTITY"
                        } else {
                            control?.generatedValueStrategy
                        }
                        val defaultValue = when (applicationSideIdStrategy) {
                            "uuid7" -> uuid7SentinelDefault(fieldType)
                            "snowflake-long" -> "0L"
                            else -> defaultProjector.project(
                                fieldPath = "${entity.packageName}.${entity.name}.${field.name}",
                                fieldType = fieldType,
                                nullable = field.nullable,
                                rawDefaultValue = field.defaultValue,
                                enumItems = planning.resolveEnumItems(entity.packageName, field),
                            )
                        }
                        val insertable = when {
                            control?.insertable != null -> control.insertable
                            control?.updatable != null -> true
                            applicationSideIdStrategy != null -> true
                            else -> null
                        }
                        val updatable = when {
                            applicationSideIdStrategy != null -> false
                            control?.updatable != null -> control.updatable
                            control?.insertable != null -> true
                            else -> null
                        }
                        val writePolicy = when {
                            jpa.isId && resolvedPolicy != null -> resolvedPolicy.id.writePolicy.name
                            control?.version == true && resolvedPolicy != null -> resolvedPolicy.version.writePolicy.name
                            managedByField[field.name] != null -> managedByField.getValue(field.name).writePolicy.name
                            else -> "READ_WRITE"
                        }
                        mapOf(
                            "fieldName" to field.name,
                            "fieldType" to fieldType,
                            "name" to field.name,
                            "type" to fieldType,
                            "nullable" to field.nullable,
                            "defaultValue" to defaultValue,
                            "typeBinding" to field.typeBinding,
                            "enumItems" to field.enumItems,
                            "columnName" to jpa.columnName,
                            "isId" to jpa.isId,
                            "converterTypeRef" to jpa.converterTypeFqn,
                            "converterClassRef" to jpa.converterClassFqn,
                            "generatedValueStrategy" to generatedValueStrategy,
                            "applicationSideIdStrategy" to applicationSideIdStrategy,
                            "isVersion" to (control?.version == true),
                            "writePolicy" to writePolicy,
                            "insertable" to insertable,
                            "updatable" to updatable,
                        )
                    }
                }
            val scalarImports = if (
                scalarFields.any {
                    it["defaultValue"] == "UUID(0L, 0L)"
                }
            ) {
                relationPlan.imports + "java.util.UUID"
            } else {
                relationPlan.imports
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
                    "hasApplicationSideIdFields" to scalarFields.any { it["applicationSideIdStrategy"] != null },
                    "hasVersionFields" to scalarFields.any { it["isVersion"] == true },
                    "dynamicInsert" to (providerControl?.dynamicInsert == true),
                    "dynamicUpdate" to (providerControl?.dynamicUpdate == true),
                    "softDeleteSql" to softDeleteSql,
                    "softDeleteWhereClause" to softDeleteWhereClause,
                    "jpaImports" to relationPlan.jpaImports,
                    "imports" to scalarImports.distinct(),
                    "fields" to scalarFields,
                    "scalarFields" to scalarFields,
                    "relationFields" to relationPlan.relationFields,
                ),
            )
        }
    }

    private fun uuid7SentinelDefault(fieldType: String): String =
        if (fieldType.removeSuffix("?") == "java.util.UUID") {
            "java.util.UUID(0L, 0L)"
        } else {
            "UUID(0L, 0L)"
        }

    private fun buildSoftDeleteSql(
        tableName: String,
        softDeleteColumn: String,
        idColumnName: String,
        versionColumnName: String?,
        identifierQuoteStyle: IdentifierQuoteStyle,
    ): String =
        if (versionColumnName != null) {
            "update ${quoteIdentifier(tableName, identifierQuoteStyle)} set ${quoteIdentifier(softDeleteColumn, identifierQuoteStyle)} = 1 where ${quoteIdentifier(idColumnName, identifierQuoteStyle)} = ? and ${quoteIdentifier(versionColumnName, identifierQuoteStyle)} = ?"
        } else {
            "update ${quoteIdentifier(tableName, identifierQuoteStyle)} set ${quoteIdentifier(softDeleteColumn, identifierQuoteStyle)} = 1 where ${quoteIdentifier(idColumnName, identifierQuoteStyle)} = ?"
        }

    private fun resolveIdentifierQuoteStyle(config: ProjectConfig): IdentifierQuoteStyle {
        val dbUrl = config.sources["db"]
            ?.options
            ?.get("url")
            ?.toString()
            ?.lowercase(Locale.ROOT)
            ?: return IdentifierQuoteStyle.DOUBLE_QUOTE

        return when {
            dbUrl.startsWith("jdbc:mysql:") -> IdentifierQuoteStyle.BACKTICK
            dbUrl.startsWith("jdbc:mariadb:") -> IdentifierQuoteStyle.BACKTICK
            dbUrl.startsWith("jdbc:h2:") && dbUrl.contains("mode=mysql") -> IdentifierQuoteStyle.BACKTICK
            else -> IdentifierQuoteStyle.DOUBLE_QUOTE
        }
    }

    private fun quoteIdentifier(value: String, style: IdentifierQuoteStyle): String =
        when (style) {
            IdentifierQuoteStyle.DOUBLE_QUOTE -> "\"$value\""
            IdentifierQuoteStyle.BACKTICK -> "`$value`"
        }
}

private enum class IdentifierQuoteStyle {
    DOUBLE_QUOTE,
    BACKTICK,
}
