package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyControl
import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyKind
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import java.util.Locale

internal object AggregateIdPolicyResolver {
    private const val UUID7 = "uuid7"
    private const val SNOWFLAKE_LONG = "snowflake-long"
    private const val IDENTITY = "identity"
    private const val DATABASE_IDENTITY = "database-identity"

    fun normalizeStrategy(raw: String): String {
        val normalized = raw.trim().lowercase(Locale.ROOT)
        return when (normalized) {
            DATABASE_IDENTITY -> IDENTITY
            else -> normalized
        }
    }

    fun resolveKind(strategy: String): AggregateIdPolicyKind {
        return when (normalizeStrategy(strategy)) {
            IDENTITY -> AggregateIdPolicyKind.DATABASE_SIDE
            UUID7, SNOWFLAKE_LONG -> AggregateIdPolicyKind.APPLICATION_SIDE
            else -> throw IllegalArgumentException("unknown ID strategy: ${normalizeStrategy(strategy)}")
        }
    }

    fun toControl(entity: EntityModel, strategy: String): AggregateIdPolicyControl {
        val normalizedStrategy = normalizeStrategy(strategy)
        return AggregateIdPolicyControl(
            entityName = entity.name,
            entityPackageName = entity.packageName,
            tableName = entity.tableName,
            idFieldName = entity.idField.name,
            idFieldType = entity.idField.type,
            strategy = normalizedStrategy,
            kind = resolveKind(normalizedStrategy),
        )
    }

    fun validateType(
        config: ProjectConfig,
        entity: EntityModel,
        strategy: String,
    ) {
        val normalizedStrategy = normalizeStrategy(strategy)
        val idType = entity.idField.type
        val valid = when (normalizedStrategy) {
            UUID7 -> idType in UuidTypes
            SNOWFLAKE_LONG -> idType in LongTypes
            IDENTITY -> idType in DatabaseIdentityTypes
            else -> throw IllegalArgumentException("unknown ID strategy: $normalizedStrategy")
        }

        require(valid) {
            "ID strategy $normalizedStrategy cannot be applied to aggregate ${entityKey(config, entity)} id field ${entity.idField.name}: generated ID type is $idType"
        }
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
