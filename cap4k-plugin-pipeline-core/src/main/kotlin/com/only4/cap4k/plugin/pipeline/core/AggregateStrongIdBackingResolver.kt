package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbIdStrategy
import java.sql.Types
import java.util.Locale

internal data class ResolvedStrongIdBacking(
    val valueType: String,
    val columnLength: Int?,
)

internal object AggregateStrongIdBackingResolver {
    private val characterJdbcTypes = setOf(
        Types.CHAR,
        Types.VARCHAR,
        Types.LONGVARCHAR,
        Types.NCHAR,
        Types.NVARCHAR,
        Types.LONGNVARCHAR,
    )

    fun resolve(tableName: String, column: DbColumnSnapshot): ResolvedStrongIdBacking {
        val strategy = requireNotNull(column.idStrategy) {
            "missing application-side ID strategy for $tableName.${column.name}"
        }
        val jdbcType = requireNotNull(column.jdbcType) {
            "missing jdbcType for application-side ID $tableName.${column.name}"
        }
        val path = "$tableName.${column.name}"

        return when (strategy) {
            DbIdStrategy.UUID7 -> resolveUuid7(path, column, jdbcType)
            DbIdStrategy.SNOWFLAKE -> resolveSnowflake(path, column, jdbcType)
            DbIdStrategy.DB_IDENTITY ->
                error("database identity $path does not have an application-side Strong ID backing")
        }
    }

    private fun resolveUuid7(
        path: String,
        column: DbColumnSnapshot,
        jdbcType: Int,
    ): ResolvedStrongIdBacking = when {
        jdbcType in characterJdbcTypes -> {
            require(column.kotlinType == "String" || column.kotlinType == "kotlin.String") {
                "uuid7 character storage $path must map to String, got ${column.kotlinType}"
            }
            val size = requireNotNull(column.columnSize) {
                "uuid7 character storage $path requires columnSize"
            }
            require(size >= 36) { "uuid7 character storage $path requires capacity >= 36, got $size" }
            ResolvedStrongIdBacking("String", size)
        }

        isNativeUuid(column, jdbcType) -> {
            require(column.kotlinType == "UUID" || column.kotlinType == "java.util.UUID") {
                "native UUID storage $path must map to UUID, got ${column.kotlinType}"
            }
            ResolvedStrongIdBacking("UUID", null)
        }

        else -> unsupported(path, column)
    }

    private fun resolveSnowflake(
        path: String,
        column: DbColumnSnapshot,
        jdbcType: Int,
    ): ResolvedStrongIdBacking = when {
        jdbcType in characterJdbcTypes -> {
            require(column.kotlinType == "String" || column.kotlinType == "kotlin.String") {
                "snowflake character storage $path must map to String, got ${column.kotlinType}"
            }
            val size = requireNotNull(column.columnSize) {
                "snowflake character storage $path requires columnSize"
            }
            require(size >= 19) { "snowflake character storage $path requires capacity >= 19, got $size" }
            ResolvedStrongIdBacking("String", size)
        }

        jdbcType == Types.BIGINT -> {
            require(column.kotlinType == "Long" || column.kotlinType == "kotlin.Long") {
                "snowflake BIGINT storage $path must map to Long, got ${column.kotlinType}"
            }
            ResolvedStrongIdBacking("Long", null)
        }

        else -> unsupported(path, column)
    }

    private fun isNativeUuid(column: DbColumnSnapshot, jdbcType: Int): Boolean =
        jdbcType in setOf(Types.OTHER, Types.BINARY) &&
            column.dbType.trim().lowercase(Locale.ROOT) == "uuid"

    private fun unsupported(path: String, column: DbColumnSnapshot): Nothing =
        throw IllegalArgumentException(
            "unsupported ${column.idStrategy} storage for $path: " +
                "jdbcType=${column.jdbcType}, dbType=${column.dbType}, " +
                "kotlinType=${column.kotlinType}, columnSize=${column.columnSize}"
        )
}
