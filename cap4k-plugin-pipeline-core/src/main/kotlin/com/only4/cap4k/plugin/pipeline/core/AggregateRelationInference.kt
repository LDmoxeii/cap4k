package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateCascadeType
import com.only4.cap4k.plugin.pipeline.api.AggregateFetchType
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationCardinality
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationPersistenceShape
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

    private data class ScalarFields(
        val tableName: String,
        val columnNamesByFieldName: Map<String, String>,
    )

    private data class OwnedRelationFieldNames(
        val collectionName: String,
        val singleName: String,
    )

    private val tableTokenSplitRegex = Regex("_+|(?<=[a-z0-9])(?=[A-Z])")
    private val fieldTokenSplitRegex = Regex("(?<=[a-z0-9])(?=[A-Z])|[^A-Za-z0-9]+")

    fun fromTables(
        artifactLayout: ArtifactLayoutResolver,
        tables: List<DbTableSnapshot>,
        skippedTableNames: Set<String> = emptySet(),
        outOfScopeTableNames: Set<String> = emptySet(),
    ): List<AggregateRelationModel> {
        val tablesByName = tables.associateBy { tableKey(it.tableName) }
        val entityLookup = tables.associateBy(
            keySelector = { tableKey(it.tableName) },
            valueTransform = { table ->
                val aggregateOwnerTable = resolveAggregateOwnerTable(table, tablesByName)
                Endpoint(
                    entityName = AggregateNaming.entityName(table.tableName),
                    packageName = artifactLayout.aggregateEntityPackage(
                        AggregateNaming.tableSegment(aggregateOwnerTable.tableName)
                    ),
                )
            }
        )
        val scalarFieldsByEntity = tables.associate { table ->
            AggregateNaming.entityName(table.tableName) to ScalarFields(
                tableName = table.tableName,
                columnNamesByFieldName = table.columns
                    .associate { column -> lowerCamelIdentifier(column.name) to column.name },
            )
        }

        val parentBindings = OwnedParentBindingResolver.resolve(
            tables = tables,
            skippedTableNames = skippedTableNames,
            outOfScopeTableNames = outOfScopeTableNames,
        )

        val parentChildRelations = parentBindings
            .map { binding ->
                val child = binding.childTable
                val parentTable = binding.parentTable
                val parentKey = tableKey(parentTable)
                val resolvedParent = requireNotNull(entityLookup[parentKey]) {
                    "unknown parent table: ${child.parentTable}"
                }
                val target = requireNotNull(entityLookup[tableKey(child.tableName)]) {
                    "unknown child table: ${child.tableName}"
                }
                val cardinality = OwnedRelationCardinalityInference.infer(binding)
                val fieldNames = parentChildFieldNames(parentTable, child.tableName)
                AggregateRelationModel(
                    ownerEntityName = resolvedParent.entityName,
                    ownerEntityPackageName = resolvedParent.packageName,
                    fieldName = fieldNames.collectionName,
                    targetEntityName = target.entityName,
                    targetEntityPackageName = target.packageName,
                    relationType = AggregateRelationType.ONE_TO_MANY,
                    joinColumn = binding.parentRefColumn.name,
                    fetchType = AggregateFetchType.LAZY,
                    nullable = false,
                    cascadeTypes = listOf(
                        AggregateCascadeType.PERSIST,
                        AggregateCascadeType.MERGE,
                        AggregateCascadeType.REMOVE,
                    ),
                    orphanRemoval = true,
                    joinColumnNullable = false,
                    owned = true,
                    parentRefColumn = binding.parentRefColumn.name,
                    ownedCardinality = cardinality,
                    persistenceShape = OwnedRelationPersistenceShape.ONE_TO_MANY_JOIN_COLUMN,
                    backingCollectionName = fieldNames.collectionName,
                    singleAccessorName = if (cardinality == OwnedRelationCardinality.ONE) fieldNames.singleName else null,
                )
            }

        val relations = parentChildRelations
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

        relations.firstNotNullOfOrNull { relation ->
            val scalarFields = scalarFieldsByEntity.getValue(relation.ownerEntityName)
            val columnName = scalarFields.columnNamesByFieldName[relation.fieldName] ?: return@firstNotNullOfOrNull null
            relation to (scalarFields to columnName)
        }?.let { (relation, collision) ->
            val (scalarFields, columnName) = collision
            if (columnName == relation.fieldName) {
                throw IllegalArgumentException(
                    "aggregate relation field collides with entity field: ${relation.ownerEntityName}.${relation.fieldName} -> ${relation.targetEntityName} [${relation.relationType}]"
                )
            }
            throw IllegalArgumentException(
                "aggregate relation field name ${relation.fieldName} conflicts with scalar field on table ${scalarFields.tableName}: ${relation.ownerEntityName}.${relation.fieldName} -> ${relation.targetEntityName} [${relation.relationType}]"
            )
        }

        validateOwnedOneSingleAccessorCollisions(
            relations = relations,
            scalarFieldsByEntity = scalarFieldsByEntity,
        )

        return relations
    }

    private fun parentChildFieldNames(parentTableName: String, childTableName: String): OwnedRelationFieldNames {
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
        val nonEmptyStemTokens = stemTokens.ifEmpty { childTokens }
        val singleName = tokensToLowerCamel(nonEmptyStemTokens)
        val collectionName = tokensToLowerCamel(
            nonEmptyStemTokens.dropLast(1) + RelationInflector.pluralizeStable(nonEmptyStemTokens.last())
        )
        return OwnedRelationFieldNames(
            collectionName = collectionName,
            singleName = singleName,
        )
    }

    private fun validateOwnedOneSingleAccessorCollisions(
        relations: List<AggregateRelationModel>,
        scalarFieldsByEntity: Map<String, ScalarFields>,
    ) {
        val relationFieldNamesByOwner = relations
            .groupBy { it.ownerEntityName }
            .mapValues { (_, ownerRelations) -> ownerRelations.map { it.fieldName }.toSet() }

        val duplicateSingleAccessor = relations
            .filter { it.ownedCardinality == OwnedRelationCardinality.ONE }
            .mapNotNull { relation -> relation.singleAccessorName?.let { relation to it } }
            .groupBy { (relation, singleAccessorName) -> relation.ownerEntityName to singleAccessorName }
            .entries
            .firstOrNull { (_, candidates) -> candidates.size > 1 }
        if (duplicateSingleAccessor != null) {
            val (_, candidates) = duplicateSingleAccessor
            val first = candidates.first().first
            val accessor = candidates.first().second
            val targets = candidates.joinToString(", ") { it.first.targetEntityName }
            throw IllegalArgumentException(
                "owned one relation single accessor collision: ${first.ownerEntityName}.$accessor -> $targets"
            )
        }

        relations
            .filter { it.ownedCardinality == OwnedRelationCardinality.ONE }
            .forEach { relation ->
                val singleAccessorName = relation.singleAccessorName ?: return@forEach
                val scalarFields = scalarFieldsByEntity.getValue(relation.ownerEntityName)
                if (singleAccessorName in scalarFields.columnNamesByFieldName.keys) {
                    throw IllegalArgumentException(
                        "owned one relation single accessor collides with scalar field: " +
                            "${relation.ownerEntityName}.$singleAccessorName -> ${relation.targetEntityName}"
                    )
                }
                if (singleAccessorName in relationFieldNamesByOwner.getValue(relation.ownerEntityName)) {
                    throw IllegalArgumentException(
                        "owned one relation single accessor collides with relation field: " +
                            "${relation.ownerEntityName}.$singleAccessorName -> ${relation.targetEntityName}"
                    )
                }
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

    private fun tableKey(tableName: String): String = tableName.lowercase(Locale.ROOT)

    private fun resolveAggregateOwnerTable(
        table: DbTableSnapshot,
        tablesByName: Map<String, DbTableSnapshot>,
    ): DbTableSnapshot {
        val visited = mutableSetOf<String>()
        var current = table
        while (true) {
            val currentKey = tableKey(current.tableName)
            if (!visited.add(currentKey)) {
                return table
            }
            if (current.aggregateRoot) {
                return current
            }

            val parentKey = current.parentTable?.let(::tableKey) ?: return current
            current = tablesByName[parentKey] ?: return current
        }
    }

    private fun lowerCamelIdentifier(value: String): String {
        val parts = value.trim()
            .split(fieldTokenSplitRegex)
            .filter { it.isNotEmpty() }
        if (parts.isEmpty()) return value

        val head = parts.first().lowercase(Locale.ROOT)
        val tail = parts.drop(1).joinToString("") { token ->
            token.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
        return head + tail
    }

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
