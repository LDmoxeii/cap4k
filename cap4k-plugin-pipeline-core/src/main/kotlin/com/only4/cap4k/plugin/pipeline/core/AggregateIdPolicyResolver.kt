package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyControl
import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyKind
import com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceFieldControl
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal object AggregateIdPolicyResolver {
    private const val UUID7 = "uuid7"
    private const val SNOWFLAKE_LONG = "snowflake-long"
    private const val DATABASE_IDENTITY = "database-identity"

    fun resolve(
        config: ProjectConfig,
        entities: List<EntityModel>,
        persistenceFieldControls: List<AggregatePersistenceFieldControl>,
    ): List<AggregateIdPolicyControl> {
        val entitiesByNameAndPackage = entities.associateBy { it.packageName to it.name }
        val identityControls = persistenceFieldControls
            .filter { it.generatedValueStrategy.equals("IDENTITY", ignoreCase = true) }
            .map { Triple(it.entityPackageName, it.entityName, it.fieldName) }
            .toSet()

        return entities.map { entity ->
            val entityKey = entityKey(config, entity)
            val root = aggregateRoot(entity, entitiesByNameAndPackage)
            val rootKey = entityKey(config, root)
            val entityStrategy = config.aggregateIdPolicy.entityStrategies[entityKey]?.trim()?.takeIf { it.isNotBlank() }
            val aggregateStrategy = config.aggregateIdPolicy.aggregateStrategies[rootKey]?.trim()?.takeIf { it.isNotBlank() }
            val configuredStrategy = entityStrategy
                ?: aggregateStrategy
                ?: config.aggregateIdPolicy.defaultStrategy.trim().takeIf { it.isNotBlank() }
                ?: UUID7
            val identity = Triple(entity.packageName, entity.name, entity.idField.name) in identityControls

            val strategy = when {
                identity && entityStrategy == null && aggregateStrategy == null -> DATABASE_IDENTITY
                else -> configuredStrategy
            }

            if (identity && strategy != DATABASE_IDENTITY) {
                throw IllegalArgumentException(
                    "ID strategy $strategy cannot be applied to aggregate $entityKey id field ${entity.idField.name}: identity generation is database-side"
                )
            }

            validateType(
                config = config,
                entity = entity,
                strategy = strategy,
            )

            AggregateIdPolicyControl(
                entityName = entity.name,
                entityPackageName = entity.packageName,
                tableName = entity.tableName,
                idFieldName = entity.idField.name,
                idFieldType = entity.idField.type,
                strategy = strategy,
                kind = when (strategy) {
                    DATABASE_IDENTITY -> AggregateIdPolicyKind.DATABASE_SIDE
                    else -> AggregateIdPolicyKind.APPLICATION_SIDE
                },
            )
        }
    }

    private fun validateType(
        config: ProjectConfig,
        entity: EntityModel,
        strategy: String,
    ) {
        val idType = entity.idField.type
        val valid = when (strategy) {
            UUID7 -> idType in UuidTypes
            SNOWFLAKE_LONG -> idType in LongTypes
            DATABASE_IDENTITY -> idType in DatabaseIdentityTypes
            else -> throw IllegalArgumentException("unknown ID strategy: $strategy")
        }

        require(valid) {
            "ID strategy $strategy cannot be applied to aggregate ${entityKey(config, entity)} id field ${entity.idField.name}: generated ID type is $idType"
        }
    }

    private fun aggregateRoot(
        entity: EntityModel,
        entitiesByNameAndPackage: Map<Pair<String, String>, EntityModel>,
    ): EntityModel {
        val visited = mutableSetOf<Pair<String, String>>()
        var current = entity

        while (current.parentEntityName != null) {
            val currentKey = current.packageName to current.name
            if (!visited.add(currentKey)) {
                return current
            }
            current = entitiesByNameAndPackage[current.packageName to current.parentEntityName] ?: return current
        }

        return current
    }

    private fun entityKey(config: ProjectConfig, entity: EntityModel): String {
        val aggregatePackageRoot = listOf(
            config.basePackage.trim('.'),
            config.artifactLayout.aggregate.packageRoot.trim('.'),
        )
            .filter { it.isNotBlank() }
            .joinToString(".")
        val relativePackage = entity.packageName
            .removePrefix(aggregatePackageRoot)
            .removePrefix(".")

        return listOf(relativePackage, entity.name)
            .filter { it.isNotBlank() }
            .joinToString(".")
    }

    private val UuidTypes = setOf("UUID", "java.util.UUID")
    private val LongTypes = setOf("Long", "kotlin.Long")
    private val DatabaseIdentityTypes = setOf(
        "Long",
        "kotlin.Long",
        "java.lang.Long",
        "Int",
        "kotlin.Int",
        "Integer",
        "java.lang.Integer",
        "Short",
        "kotlin.Short",
        "java.lang.Short",
    )
}
