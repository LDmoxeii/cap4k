package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateFetchType
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import java.util.Locale

internal object AggregateRelationInference {
    private data class Endpoint(
        val entityName: String,
        val packageName: String,
    )

    private data class RelationCollisionKey(
        val ownerEntityName: String,
        val fieldName: String,
    )

    private val underscoredRelationIdSuffixRegex = Regex("_id$", RegexOption.IGNORE_CASE)
    private val tableTokenSplitRegex = Regex("_+|(?<=[a-z0-9])(?=[A-Z])")

    fun fromTables(
        basePackage: String,
        tables: List<DbTableSnapshot>,
        skippedTableNames: Set<String> = emptySet(),
        outOfScopeTableNames: Set<String> = emptySet(),
    ): List<AggregateRelationModel> {
        val allowedMissingTableNames = (skippedTableNames + outOfScopeTableNames).map(::tableKey).toSet()
        val entityLookup = tables.associateBy(
            keySelector = { tableKey(it.tableName) },
            valueTransform = { table ->
                Endpoint(
                    entityName = AggregateNaming.entityName(table.tableName),
                    packageName = "${basePackage}.domain.aggregates.${AggregateNaming.tableSegment(table.tableName)}",
                )
            }
        )
        val entityFieldNamesByEntity = tables.associate { table ->
            AggregateNaming.entityName(table.tableName) to table.columns
                .map { it.name }
                .toSet()
        }

        val parentChildRelations = tables
            .filter { it.parentTable != null }
            .mapNotNull { child ->
                val parentTable = requireNotNull(child.parentTable)
                val parentKey = tableKey(parentTable)
                val parent = entityLookup[parentKey]
                if (parent == null && parentKey in allowedMissingTableNames) {
                    return@mapNotNull null
                }
                val resolvedParent = requireNotNull(parent) {
                    "unknown parent table: ${child.parentTable}"
                }
                val target = requireNotNull(entityLookup[tableKey(child.tableName)]) {
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
                    nullable = false,
                )
            }

        val explicitRelations = tables.flatMap { table ->
            val owner = requireNotNull(entityLookup[tableKey(table.tableName)]) {
                "unknown owner table: ${table.tableName}"
            }
            table.columns
                .mapNotNull { column ->
                    val referenceTable = column.referenceTable ?: return@mapNotNull null
                    val relationType = resolveRelationType(column.explicitRelationType)
                    val referenceKey = tableKey(referenceTable)
                    val target = entityLookup[referenceKey]
                    if (target == null && referenceKey in allowedMissingTableNames) {
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
                        nullable = column.nullable,
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
            relation.fieldName in entityFieldNamesByEntity.getValue(relation.ownerEntityName)
        }?.let { relation ->
            throw IllegalArgumentException(
                "aggregate relation field collides with entity field: ${relation.ownerEntityName}.${relation.fieldName} -> ${relation.targetEntityName} [${relation.relationType}]"
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
        return tokensToLowerCamel(stemTokens.dropLast(1) + RelationInflector.pluralizeStable(stemTokens.last()))
    }

    private fun relationFieldName(columnName: String, targetEntityName: String): String {
        val stem = stripRelationIdSuffix(columnName)
        return if (stem.isNotBlank()) {
            if (stem.contains('_')) {
                stem.split("_")
                    .joinToString("") { part ->
                        upperFirst(part.lowercase(Locale.ROOT))
                    }
                    .let(::lowerFirst)
            } else if (stem.all { !it.isLetter() || it.isUpperCase() }) {
                stem.lowercase(Locale.ROOT)
            } else {
                lowerFirst(stem)
            }
        } else {
            lowerFirst(targetEntityName)
        }
    }

    private fun tableNameTokens(tableName: String): List<String> =
        tableName
            .split(tableTokenSplitRegex)
            .filter { it.isNotBlank() }
            .map { it.lowercase(Locale.ROOT) }

    private fun tokensToLowerCamel(tokens: List<String>): String {
        return tokens.mapIndexed { index, token ->
            if (index == 0) {
                token
            } else {
                upperFirst(token)
            }
        }.joinToString("")
    }

    private fun stripRelationIdSuffix(columnName: String): String {
        return when {
            underscoredRelationIdSuffixRegex.containsMatchIn(columnName) ->
                columnName.replaceFirst(underscoredRelationIdSuffixRegex, "")
            columnName.endsWith("Id") || columnName.endsWith("ID") ->
                columnName.dropLast(2)
            else -> columnName
        }
    }

    private fun tableKey(tableName: String): String = tableName.lowercase(Locale.ROOT)

    private fun lowerFirst(value: String): String =
        if (value.isEmpty()) value else value.substring(0, 1).lowercase(Locale.ROOT) + value.substring(1)

    private fun upperFirst(value: String): String =
        if (value.isEmpty()) value else value.substring(0, 1).uppercase(Locale.ROOT) + value.substring(1)

    private object RelationInflector {
        private data class Rule(
            val regex: Regex,
            val replacement: String,
        )

        private val plurals = listOf(
            rule("(quiz)$", "$1zes"),
            rule("(ox)$", "$1es"),
            rule("([m|l])ouse$", "$1ice"),
            rule("(matr|vert|ind)ix|ex$", "$1ices"),
            rule("(x|ch|ss|sh)$", "$1es"),
            rule("([^aeiouy]|qu)ies$", "$1y"),
            rule("([^aeiouy]|qu)y$", "$1ies"),
            rule("(hive)$", "$1s"),
            rule("(?:([^f])fe|([lr])f)$", "$1$2ves"),
            rule("sis$", "ses"),
            rule("([ti])um$", "$1a"),
            rule("(buffal|tomat)o$", "$1oes"),
            rule("(bu)s$", "$1es"),
            rule("(alias|status)$", "$1es"),
            rule("(octop|vir)us$", "$1i"),
            rule("(ax|test)is$", "$1es"),
            rule("s$", "s"),
            rule("$", "s"),
        )

        private val uncountables = setOf(
            "equipment",
            "information",
            "rice",
            "money",
            "species",
            "series",
            "fish",
            "sheep",
        )

        fun pluralizeStable(word: String): String {
            return if (word.lowercase(Locale.ROOT) in uncountables || looksPlural(word)) {
                word
            } else {
                pluralize(word)
            }
        }

        private fun pluralize(word: String): String =
            if (word.lowercase(Locale.ROOT) in uncountables) {
                word
            } else {
                applyFirstRule(word, plurals)
            }

        private fun applyFirstRule(word: String, rules: List<Rule>): String {
            for (rule in rules) {
                if (rule.regex.containsMatchIn(word)) {
                    return word.replace(rule.regex, rule.replacement)
                }
            }
            return word
        }

        private fun rule(pattern: String, replacement: String): Rule =
            Rule(pattern.toRegex(RegexOption.IGNORE_CASE), replacement)

        private fun looksPlural(word: String): Boolean {
            val normalizedWord = word.lowercase(Locale.ROOT)
            return normalizedWord.endsWith("ies") ||
                normalizedWord.endsWith("ses") ||
                normalizedWord.endsWith("xes") ||
                normalizedWord.endsWith("zes") ||
                normalizedWord.endsWith("ches") ||
                normalizedWord.endsWith("shes") ||
                (normalizedWord.endsWith("s") &&
                    !normalizedWord.endsWith("ss") &&
                    !normalizedWord.endsWith("us"))
        }
    }

}
