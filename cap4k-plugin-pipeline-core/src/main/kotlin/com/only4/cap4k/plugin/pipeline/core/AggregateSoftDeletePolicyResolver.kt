package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateSoftDeletePolicy
import com.only4.cap4k.plugin.pipeline.api.AggregateIdStorageKind
import com.only4.cap4k.plugin.pipeline.api.AggregateSpecialFieldResolvedPolicy
import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.SoftDeleteActiveSentinel
import com.only4.cap4k.plugin.pipeline.api.SoftDeleteTombstoneStrategy

internal object AggregateSoftDeletePolicyResolver {
    fun resolve(
        table: DbTableSnapshot,
        resolvedPolicy: AggregateSpecialFieldResolvedPolicy,
    ): AggregateSoftDeletePolicy? {
        val deleted = resolvedPolicy.deleted.takeIf { it.enabled } ?: return null
        val deletedColumnName = requireNotNull(deleted.columnName) {
            "missing soft delete column for table ${table.tableName}"
        }
        val deletedFieldName = requireNotNull(deleted.fieldName) {
            "missing soft delete field for table ${table.tableName}"
        }
        val idColumn = table.columns.firstOrNull {
            it.name == resolvedPolicy.id.columnName
        } ?: throw IllegalArgumentException(
            "missing id column ${resolvedPolicy.id.columnName} for soft delete table ${table.tableName}"
        )
        val deletedColumn = table.columns.firstOrNull {
            it.name == deletedColumnName
        } ?: throw IllegalArgumentException(
            "missing soft delete column $deletedColumnName for table ${table.tableName}"
        )

        val idResolution = runCatching { AggregateIdStorageCatalog.resolve(table.tableName, idColumn) }
        val deletedResolution = runCatching { AggregateIdStorageCatalog.resolve(table.tableName, deletedColumn) }
        val strategy = resolvedPolicy.id.strategy
        if (idResolution.isFailure || deletedResolution.isFailure) {
            reject(
                table = table,
                idColumn = idColumn,
                deletedColumn = deletedColumn,
                strategy = strategy,
                idStorage = describeResolution(idResolution, idColumn),
                deletedStorage = describeResolution(deletedResolution, deletedColumn),
                evidence = "unsupported physical storage",
                cause = idResolution.exceptionOrNull() ?: deletedResolution.exceptionOrNull(),
            )
        }
        val idStorage = idResolution.getOrThrow()
        val deletedStorage = deletedResolution.getOrThrow()
        val idStorageDescription = describeStorage(idStorage, idColumn)
        val deletedStorageDescription = describeStorage(deletedStorage, deletedColumn)
        val activeSentinel = when (strategy) {
            IDENTITY, SNOWFLAKE -> SoftDeleteActiveSentinel.ZERO
            UUID7 -> SoftDeleteActiveSentinel.NIL_UUID
            else -> reject(
                table = table,
                idColumn = idColumn,
                deletedColumn = deletedColumn,
                strategy = strategy,
                idStorage = idStorageDescription,
                deletedStorage = deletedStorageDescription,
                evidence = "accepted strategies are identity, uuid7, snowflake",
            )
        }

        validateStrategyStorage(
            table = table,
            idColumn = idColumn,
            deletedColumn = deletedColumn,
            strategy = strategy,
            idStorage = idStorage,
            idStorageDescription = idStorageDescription,
            deletedStorageDescription = deletedStorageDescription,
        )
        validateSelfIdAssignment(
            table = table,
            idColumn = idColumn,
            deletedColumn = deletedColumn,
            strategy = strategy,
            idStorage = idStorage,
            deletedStorage = deletedStorage,
            idStorageDescription = idStorageDescription,
            deletedStorageDescription = deletedStorageDescription,
        )
        if (deletedColumn.nullable) {
            reject(
                table = table,
                idColumn = idColumn,
                deletedColumn = deletedColumn,
                strategy = strategy,
                idStorage = idStorageDescription,
                deletedStorage = deletedStorageDescription,
                evidence = "deleted marker requires nullable=false, got nullable=true",
            )
        }
        val normalizedDefault = deletedColumn.defaultValue?.let {
            SoftDeleteDefaultNormalizer.normalize(it, storageKind(deletedStorage))
        }
        if (normalizedDefault != activeSentinel) {
            reject(
                table = table,
                idColumn = idColumn,
                deletedColumn = deletedColumn,
                strategy = strategy,
                idStorage = idStorageDescription,
                deletedStorage = deletedStorageDescription,
                evidence = "defaultValue=${deletedColumn.defaultValue}, " +
                    "normalizedDefault=$normalizedDefault, expectedSentinel=$activeSentinel",
            )
        }

        return AggregateSoftDeletePolicy(
            fieldName = deletedFieldName,
            columnName = deletedColumn.name,
            storageKind = storageKind(deletedStorage),
            activeSentinel = activeSentinel,
            tombstoneStrategy = SoftDeleteTombstoneStrategy.SELF_ID,
        )
    }

    private fun validateStrategyStorage(
        table: DbTableSnapshot,
        idColumn: DbColumnSnapshot,
        deletedColumn: DbColumnSnapshot,
        strategy: String,
        idStorage: ResolvedAggregateIdStorage,
        idStorageDescription: String,
        deletedStorageDescription: String,
    ) {
        val rejectedEvidence = when (strategy) {
            IDENTITY -> when {
                idStorage !is ResolvedAggregateIdStorage.Integral ->
                    "identity SELF_ID storage requires integral ID storage"

                idStorage.kotlinType !in IDENTITY_KOTLIN_TYPES ->
                    "identity SELF_ID storage requires existing identity Kotlin support"

                else -> null
            }

            SNOWFLAKE -> when (idStorage) {
                is ResolvedAggregateIdStorage.Integral -> if (
                    idStorage.bits == 64 &&
                    !idStorage.unsigned &&
                    idStorage.kotlinType in LONG_KOTLIN_TYPES
                ) {
                    null
                } else {
                    "snowflake integral ID storage requires signed 64-bit Long"
                }

                is ResolvedAggregateIdStorage.Character -> if (idStorage.capacity >= SNOWFLAKE_TEXT_CAPACITY) {
                    null
                } else {
                    "snowflake String ID storage requires capacity >= $SNOWFLAKE_TEXT_CAPACITY"
                }

                is ResolvedAggregateIdStorage.NativeUuid ->
                    "snowflake ID storage must be signed 64-bit Long or String"
            }

            UUID7 -> when (idStorage) {
                is ResolvedAggregateIdStorage.Character -> if (idStorage.capacity >= UUID_TEXT_CAPACITY) {
                    null
                } else {
                    "uuid7 String ID storage requires capacity >= $UUID_TEXT_CAPACITY"
                }

                is ResolvedAggregateIdStorage.NativeUuid -> null
                is ResolvedAggregateIdStorage.Integral -> "uuid7 ID storage must be String or UUID"
            }

            else -> error("strategy was validated before storage support")
        }
        if (rejectedEvidence != null) {
            reject(
                table = table,
                idColumn = idColumn,
                deletedColumn = deletedColumn,
                strategy = strategy,
                idStorage = idStorageDescription,
                deletedStorage = deletedStorageDescription,
                evidence = rejectedEvidence,
            )
        }
    }

    private fun validateSelfIdAssignment(
        table: DbTableSnapshot,
        idColumn: DbColumnSnapshot,
        deletedColumn: DbColumnSnapshot,
        strategy: String,
        idStorage: ResolvedAggregateIdStorage,
        deletedStorage: ResolvedAggregateIdStorage,
        idStorageDescription: String,
        deletedStorageDescription: String,
    ) {
        if (storageKind(idStorage) != storageKind(deletedStorage)) {
            reject(
                table = table,
                idColumn = idColumn,
                deletedColumn = deletedColumn,
                strategy = strategy,
                idStorage = idStorageDescription,
                deletedStorage = deletedStorageDescription,
                evidence = "SELF_ID requires the same storage kind without casts or converters",
            )
        }

        val rejectedEvidence = when {
            idStorage is ResolvedAggregateIdStorage.Integral &&
                deletedStorage is ResolvedAggregateIdStorage.Integral &&
                !canAssignIntegral(idStorage, deletedStorage) ->
                "SELF_ID integral assignment range is insufficient"

            idStorage is ResolvedAggregateIdStorage.Character &&
                deletedStorage is ResolvedAggregateIdStorage.Character &&
                deletedStorage.capacity < idStorage.capacity ->
                "SELF_ID character assignment requires deleted capacity >= id capacity"

            else -> null
        }
        if (rejectedEvidence != null) {
            reject(
                table = table,
                idColumn = idColumn,
                deletedColumn = deletedColumn,
                strategy = strategy,
                idStorage = idStorageDescription,
                deletedStorage = deletedStorageDescription,
                evidence = rejectedEvidence,
            )
        }
    }

    private fun canAssignIntegral(
        source: ResolvedAggregateIdStorage.Integral,
        target: ResolvedAggregateIdStorage.Integral,
    ): Boolean = when {
        source.unsigned == target.unsigned -> target.bits >= source.bits
        !source.unsigned && target.unsigned -> false
        else -> target.bits > source.bits
    }

    private fun storageKind(storage: ResolvedAggregateIdStorage): AggregateIdStorageKind = when (storage) {
        is ResolvedAggregateIdStorage.Integral -> AggregateIdStorageKind.INTEGRAL
        is ResolvedAggregateIdStorage.Character -> AggregateIdStorageKind.CHARACTER
        is ResolvedAggregateIdStorage.NativeUuid -> AggregateIdStorageKind.NATIVE_UUID
    }

    private fun describeResolution(
        resolution: Result<ResolvedAggregateIdStorage>,
        column: DbColumnSnapshot,
    ): String = resolution.fold(
        onSuccess = { describeStorage(it, column) },
        onFailure = { error -> "unsupported${physicalEvidence(column)}; reason=${error.message}" },
    )

    private fun describeStorage(
        storage: ResolvedAggregateIdStorage,
        column: DbColumnSnapshot,
    ): String = "$storage${physicalEvidence(column)}"

    private fun physicalEvidence(column: DbColumnSnapshot): String =
        "[jdbcType=${column.jdbcType}, dbType=${column.dbType}, " +
            "kotlinType=${column.kotlinType}, columnSize=${column.columnSize}]"

    private fun reject(
        table: DbTableSnapshot,
        idColumn: DbColumnSnapshot,
        deletedColumn: DbColumnSnapshot,
        strategy: String,
        idStorage: String,
        deletedStorage: String,
        evidence: String,
        cause: Throwable? = null,
    ): Nothing {
        throw IllegalArgumentException(
            "soft delete policy rejected for ${table.tableName}.${deletedColumn.name}: " +
                "id=${table.tableName}.${idColumn.name}, strategy=$strategy, " +
                "idStorage=$idStorage, deletedStorage=$deletedStorage, evidence=$evidence",
            cause,
        )
    }

    private const val IDENTITY = "identity"
    private const val UUID7 = "uuid7"
    private const val SNOWFLAKE = "snowflake"
    private const val UUID_TEXT_CAPACITY = 36
    private const val SNOWFLAKE_TEXT_CAPACITY = 19

    private val IDENTITY_KOTLIN_TYPES = setOf(
        "Short",
        "kotlin.Short",
        "Int",
        "kotlin.Int",
        "Long",
        "kotlin.Long",
    )

    private val LONG_KOTLIN_TYPES = setOf(
        "Long",
        "kotlin.Long",
    )
}
