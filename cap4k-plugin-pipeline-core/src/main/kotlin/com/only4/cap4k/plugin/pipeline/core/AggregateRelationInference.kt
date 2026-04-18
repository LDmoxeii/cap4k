package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateFetchType
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot

internal object AggregateRelationInference {
    private data class Endpoint(
        val entityName: String,
        val packageName: String,
    )

    private data class RelationCollisionKey(
        val ownerEntityName: String,
        val fieldName: String,
    )

    private val relationIdSuffixRegex = Regex("(?i)_?id$")
    private val tableTokenSplitRegex = Regex("_+|(?<=[a-z0-9])(?=[A-Z])")
    private val vowels = setOf('a', 'e', 'i', 'o', 'u')

    fun fromTables(
        basePackage: String,
        tables: List<DbTableSnapshot>,
        skippedTableNames: Set<String> = emptySet(),
        outOfScopeTableNames: Set<String> = emptySet(),
    ): List<AggregateRelationModel> {
        val allowedMissingTableNames = skippedTableNames + outOfScopeTableNames
        val entityLookup = tables.associateBy(
            keySelector = { it.tableName.lowercase() },
            valueTransform = { table ->
                Endpoint(
                    entityName = AggregateNaming.entityName(table.tableName),
                    packageName = "${basePackage}.domain.aggregates.${AggregateNaming.tableSegment(table.tableName)}",
                )
            }
        )
        val scalarFieldNamesByEntity = tables.associate { table ->
            AggregateNaming.entityName(table.tableName) to table.columns.map { it.name }.toSet()
        }

        val parentChildRelations = tables
            .filter { it.parentTable != null }
            .mapNotNull { child ->
                val parentTable = requireNotNull(child.parentTable)
                val parent = entityLookup[parentTable.lowercase()]
                if (parent == null && parentTable.lowercase() in allowedMissingTableNames) {
                    return@mapNotNull null
                }
                val resolvedParent = requireNotNull(parent) {
                    "unknown parent table: ${child.parentTable}"
                }
                val target = requireNotNull(entityLookup[child.tableName.lowercase()]) {
                    "unknown child table: ${child.tableName}"
                }
                val parentAnchorColumns = child.columns
                    .filter { it.referenceTable?.equals(parentTable, ignoreCase = true) == true }
                    .sortedBy { it.name }
                parentAnchorColumns
                    .firstOrNull { it.explicitRelationType != null && it.explicitRelationType != "MANY_TO_ONE" }
                    ?.let { column ->
                        throw IllegalArgumentException(
                            "parent reference relation type must be MANY_TO_ONE in first slice: ${child.tableName}.${column.name} -> $parentTable = ${column.explicitRelationType}"
                        )
                    }
                val joinColumns = parentAnchorColumns
                    .map { it.name }
                    .sorted()
                val joinColumn = when (joinColumns.size) {
                    0 -> throw IllegalArgumentException("missing parent reference column for table: ${child.tableName}")
                    1 -> joinColumns.single()
                    else -> throw IllegalArgumentException(
                        "ambiguous parent reference columns for table ${child.tableName} -> $parentTable: ${joinColumns.joinToString(", ")}"
                    )
                }
                AggregateRelationModel(
                    ownerEntityName = resolvedParent.entityName,
                    ownerEntityPackageName = resolvedParent.packageName,
                    fieldName = parentChildFieldName(parentTable, child.tableName),
                    targetEntityName = target.entityName,
                    targetEntityPackageName = target.packageName,
                    relationType = AggregateRelationType.ONE_TO_MANY,
                    joinColumn = joinColumn,
                    fetchType = AggregateFetchType.LAZY,
                )
            }

        val explicitRelations = tables.flatMap { table ->
            val owner = requireNotNull(entityLookup[table.tableName.lowercase()]) {
                "unknown owner table: ${table.tableName}"
            }
            table.columns
                .mapNotNull { column ->
                    val referenceTable = column.referenceTable ?: return@mapNotNull null
                    val relationType = resolveRelationType(column.explicitRelationType)
                    val target = entityLookup[referenceTable.lowercase()]
                    if (target == null && referenceTable.lowercase() in allowedMissingTableNames) {
                        return@mapNotNull null
                    }
                    val resolvedTarget = requireNotNull(target) {
                        "unknown reference table: ${column.referenceTable}"
                    }
                    AggregateRelationModel(
                        ownerEntityName = owner.entityName,
                        ownerEntityPackageName = owner.packageName,
                        fieldName = relationFieldName(column.name, resolvedTarget.entityName),
                        targetEntityName = resolvedTarget.entityName,
                        targetEntityPackageName = resolvedTarget.packageName,
                        relationType = relationType,
                        joinColumn = column.name,
                        fetchType = if (column.lazy == true) AggregateFetchType.LAZY else AggregateFetchType.EAGER,
                    )
                }
        }

        val relations = parentChildRelations + explicitRelations
        val collision = relations
            .groupBy { RelationCollisionKey(it.ownerEntityName, it.fieldName) }
            .entries
            .firstOrNull { (_, candidates) -> candidates.size > 1 }

        if (collision != null) {
            val (key, candidates) = collision
            val targets = candidates
                .map { "${it.targetEntityName} [${it.relationType}]" }
                .distinct()
                .sorted()
                .joinToString(", ")
            throw IllegalArgumentException(
                "aggregate relation field collision: ${key.ownerEntityName}.${key.fieldName} -> $targets"
            )
        }

        relations.firstOrNull { relation ->
            relation.fieldName in scalarFieldNamesByEntity.getValue(relation.ownerEntityName)
        }?.let { relation ->
            throw IllegalArgumentException(
                "aggregate relation field collides with scalar field: ${relation.ownerEntityName}.${relation.fieldName} -> ${relation.targetEntityName} [${relation.relationType}]"
            )
        }

        return relations
    }

    private fun resolveRelationType(explicitRelationType: String?): AggregateRelationType {
        return when (explicitRelationType) {
            "ONE_TO_ONE" -> AggregateRelationType.ONE_TO_ONE
            null, "MANY_TO_ONE" -> AggregateRelationType.MANY_TO_ONE
            else -> throw IllegalArgumentException("unsupported aggregate relation type in first slice: $explicitRelationType")
        }
    }

    private fun parentChildFieldName(parentTableName: String, childTableName: String): String {
        val parentTokens = tableNameTokens(parentTableName)
        val childTokens = tableNameTokens(childTableName)
        val stemTokens = if (
            childTokens.size > parentTokens.size &&
            childTokens.take(parentTokens.size) == parentTokens
        ) {
            childTokens.drop(parentTokens.size)
        } else {
            childTokens
        }
        return tokensToLowerCamel(stemTokens.dropLast(1) + pluralizeToken(stemTokens.last()))
    }

    private fun relationFieldName(columnName: String, targetEntityName: String): String {
        val stem = columnName.replaceFirst(relationIdSuffixRegex, "")
        return if (stem.isNotBlank()) {
            if (stem.contains('_')) {
                stem.split("_")
                    .joinToString("") { part ->
                        part.lowercase().replaceFirstChar { it.titlecase() }
                    }
                    .replaceFirstChar { it.lowercase() }
            } else if (stem.all { !it.isLetter() || it.isUpperCase() }) {
                stem.lowercase()
            } else {
                stem.replaceFirstChar { it.lowercase() }
            }
        } else {
            targetEntityName.replaceFirstChar { it.lowercase() }
        }
    }

    private fun tableNameTokens(tableName: String): List<String> =
        tableName
            .split(tableTokenSplitRegex)
            .filter { it.isNotBlank() }
            .map { it.lowercase() }

    private fun pluralizeToken(token: String): String =
        if (looksPlural(token)) {
            token
        } else if (
            token.length > 1 &&
            token.endsWith("y") &&
            token[token.lastIndex - 1] !in vowels
        ) {
            token.dropLast(1) + "ies"
        } else {
            token + "s"
        }

    private fun looksPlural(token: String): Boolean =
        token.endsWith("ies") ||
            token.endsWith("ses") ||
            token.endsWith("xes") ||
            token.endsWith("zes") ||
            token.endsWith("ches") ||
            token.endsWith("shes") ||
            (token.endsWith("s") && !token.endsWith("ss") && !token.endsWith("us"))

    private fun tokensToLowerCamel(tokens: List<String>): String {
        return tokens.mapIndexed { index, token ->
            if (index == 0) {
                token
            } else {
                token.replaceFirstChar { it.titlecase() }
            }
        }.joinToString("")
    }

}
