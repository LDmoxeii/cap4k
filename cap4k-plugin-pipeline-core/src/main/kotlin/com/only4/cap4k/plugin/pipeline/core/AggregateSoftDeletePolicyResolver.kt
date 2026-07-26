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
        val idResolution = resolveEndpoint(
            table = table,
            label = "ID",
            columnName = resolvedPolicy.id.columnName,
            unresolvedColumnName = "<unresolved-id-column>",
        )
        val deletedResolution = resolveEndpoint(
            table = table,
            label = "deleted",
            columnName = deleted.columnName,
            unresolvedColumnName = "<unresolved-deleted-column>",
        )
        val strategy = resolvedPolicy.id.strategy
        if (!idResolution.isResolved || !deletedResolution.isResolved) {
            reject(
                idPath = idResolution.path,
                deletedPath = deletedResolution.path,
                strategy = strategy,
                idStorage = idResolution.storageEvidence,
                deletedStorage = deletedResolution.storageEvidence,
                evidence = "unresolved or unsupported physical storage",
                failures = listOfNotNull(idResolution.failure, deletedResolution.failure),
            )
        }
        val idColumn = checkNotNull(idResolution.column)
        val deletedColumn = checkNotNull(deletedResolution.column)
        val idStorage = checkNotNull(idResolution.storage)
        val deletedStorage = checkNotNull(deletedResolution.storage)
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
        val deletedFieldName = deleted.fieldName ?: reject(
            table = table,
            idColumn = idColumn,
            deletedColumn = deletedColumn,
            strategy = strategy,
            idStorage = idStorageDescription,
            deletedStorage = deletedStorageDescription,
            evidence = "fieldName=null at semantic policy publication",
        )

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

    private data class EndpointResolution(
        val path: String,
        val column: DbColumnSnapshot?,
        val storage: ResolvedAggregateIdStorage?,
        val storageEvidence: String,
        val failure: IllegalArgumentException? = null,
    ) {
        val isResolved: Boolean
            get() = column != null && storage != null
    }

    private fun resolveEndpoint(
        table: DbTableSnapshot,
        label: String,
        columnName: String?,
        unresolvedColumnName: String,
    ): EndpointResolution {
        val path = "${table.tableName}.${columnName ?: unresolvedColumnName}"
        if (columnName == null) {
            return EndpointResolution(
                path = path,
                column = null,
                storage = null,
                storageEvidence = "unresolved[columnName=null]",
            )
        }
        val column = table.columns.firstOrNull { it.name == columnName }
            ?: return EndpointResolution(
                path = path,
                column = null,
                storage = null,
                storageEvidence = "unresolved[missing physical $label column]",
            )
        return try {
            val storage = AggregateIdStorageCatalog.resolve(table.tableName, column)
            EndpointResolution(
                path = path,
                column = column,
                storage = storage,
                storageEvidence = describeStorage(storage, column),
            )
        } catch (error: IllegalArgumentException) {
            EndpointResolution(
                path = path,
                column = column,
                storage = null,
                storageEvidence = "unsupported${physicalEvidence(column)}; reason=${error.message}",
                failure = error,
            )
        }
    }

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
    ): Nothing = reject(
        idPath = "${table.tableName}.${idColumn.name}",
        deletedPath = "${table.tableName}.${deletedColumn.name}",
        strategy = strategy,
        idStorage = idStorage,
        deletedStorage = deletedStorage,
        evidence = evidence,
    )

    private fun reject(
        idPath: String,
        deletedPath: String,
        strategy: String,
        idStorage: String,
        deletedStorage: String,
        evidence: String,
        failures: List<IllegalArgumentException> = emptyList(),
    ): Nothing {
        val error = IllegalArgumentException(
            "soft delete policy rejected for $deletedPath: " +
                "id=$idPath, strategy=$strategy, idStorage=$idStorage, " +
                "deleted=$deletedPath, deletedStorage=$deletedStorage, evidence=$evidence",
            failures.firstOrNull(),
        )
        failures.drop(1).forEach(error::addSuppressed)
        throw error
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
