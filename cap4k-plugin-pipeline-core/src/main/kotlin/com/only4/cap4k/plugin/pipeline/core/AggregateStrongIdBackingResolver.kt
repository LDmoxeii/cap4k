package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbIdStrategy

internal data class ResolvedStrongIdBacking(
    val valueType: String,
    val columnLength: Int?,
)

internal object AggregateStrongIdBackingResolver {
    fun resolve(tableName: String, column: DbColumnSnapshot): ResolvedStrongIdBacking {
        val strategy = requireNotNull(column.idStrategy) {
            "missing application-side ID strategy for $tableName.${column.name}"
        }
        val path = "$tableName.${column.name}"
        val storage = try {
            AggregateIdStorageCatalog.resolve(tableName, column)
        } catch (error: IllegalArgumentException) {
            val evidence = if (column.jdbcType == null) {
                "missing jdbcType; ${error.message}"
            } else {
                error.message
            }
            throw IllegalArgumentException("unsupported ${strategy.name} storage for $path: $evidence", error)
        }

        return when (strategy) {
            DbIdStrategy.UUID7 -> resolveUuid7(path, storage, column)
            DbIdStrategy.SNOWFLAKE -> resolveSnowflake(path, storage, column)
            DbIdStrategy.DB_IDENTITY ->
                error("database identity $path does not have an application-side Strong ID backing")
        }
    }

    private fun resolveUuid7(
        path: String,
        storage: ResolvedAggregateIdStorage,
        column: DbColumnSnapshot,
    ): ResolvedStrongIdBacking = when (storage) {
        is ResolvedAggregateIdStorage.Character -> {
            require(storage.capacity >= 36) {
                "UUID7 character storage $path requires capacity >= 36, got ${storage.capacity}"
            }
            ResolvedStrongIdBacking("String", storage.capacity)
        }

        is ResolvedAggregateIdStorage.NativeUuid -> ResolvedStrongIdBacking("UUID", null)
        is ResolvedAggregateIdStorage.Integral -> unsupported(column.idStrategy, path, column)
    }

    private fun resolveSnowflake(
        path: String,
        storage: ResolvedAggregateIdStorage,
        column: DbColumnSnapshot,
    ): ResolvedStrongIdBacking = when (storage) {
        is ResolvedAggregateIdStorage.Character -> {
            require(storage.capacity >= 19) {
                "SNOWFLAKE character storage $path requires capacity >= 19, got ${storage.capacity}"
            }
            ResolvedStrongIdBacking("String", storage.capacity)
        }

        is ResolvedAggregateIdStorage.Integral -> {
            require(
                storage.bits == 64 &&
                    !storage.unsigned &&
                    storage.kotlinType in setOf("Long", "kotlin.Long"),
            ) {
                "SNOWFLAKE storage $path requires signed 64-bit Long, got " +
                    "bits=${storage.bits}, unsigned=${storage.unsigned}, kotlinType=${storage.kotlinType}"
            }
            ResolvedStrongIdBacking("Long", null)
        }

        is ResolvedAggregateIdStorage.NativeUuid -> unsupported(column.idStrategy, path, column)
    }

    private fun unsupported(strategy: DbIdStrategy?, path: String, column: DbColumnSnapshot): Nothing =
        throw IllegalArgumentException(
            "unsupported ${strategy?.name} storage for $path: " +
                "jdbcType=${column.jdbcType}, dbType=${column.dbType}, " +
                "kotlinType=${column.kotlinType}, columnSize=${column.columnSize}",
        )
}
