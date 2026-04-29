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
                            "defaultValue" to kotlinConstructorDefaultValue(field.defaultValue, fieldType),
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

    private fun kotlinConstructorDefaultValue(rawDefaultValue: String?, fieldType: String): String? {
        val normalized = rawDefaultValue?.trim()?.trimSurroundingParentheses()?.trim() ?: return null
        val shortType = fieldType.substringAfterLast('.').removeSuffix("?")
        return when (shortType) {
            "Boolean" -> when {
                normalized.equals("true", ignoreCase = true) || normalized == "1" -> "true"
                normalized.equals("false", ignoreCase = true) || normalized == "0" -> "false"
                else -> null
            }
            "String" -> normalized.unquoteSqlString()?.let { quoteKotlinString(it) }
            "Byte",
            "Short",
            "Int",
            "Long",
            -> normalized.takeIf { it.matches(Regex("""[-+]?\d+""")) }
            "Float" -> normalizedFloatingLiteral(normalized, suffix = "f")
            "Double" -> normalizedFloatingLiteral(normalized)
            else -> null
        }
    }

    private fun normalizedFloatingLiteral(rawValue: String, suffix: String? = null): String? {
        val value = if (suffix != null && rawValue.endsWith(suffix, ignoreCase = true)) {
            rawValue.dropLast(suffix.length)
        } else {
            rawValue
        }
        if (!value.matches(Regex("""[-+]?(\d+(\.\d*)?|\.\d+)"""))) {
            return null
        }
        val sign = value.takeIf { it.startsWith("-") || it.startsWith("+") }?.take(1).orEmpty()
        val unsigned = value.removePrefix("-").removePrefix("+")
        val withLeadingDigit = if (unsigned.startsWith(".")) {
            "0$unsigned"
        } else {
            unsigned
        }
        val normalized = if (withLeadingDigit.endsWith(".")) {
            "${withLeadingDigit}0"
        } else {
            withLeadingDigit
        }
        return sign + normalized + (suffix ?: "")
    }

    private fun String.trimSurroundingParentheses(): String {
        var value = this
        while (value.length >= 2 && value.first() == '(' && value.last() == ')') {
            value = value.substring(1, value.lastIndex).trim()
        }
        return value
    }

    private fun String.unquoteSqlString(): String? =
        when {
            length >= 2 && first() == '\'' && last() == '\'' -> substring(1, lastIndex).replace("''", "'")
            length >= 2 && first() == '"' && last() == '"' -> substring(1, lastIndex).replace("\"\"", "\"")
            else -> null
        }

    private fun quoteKotlinString(value: String): String =
        buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
}
