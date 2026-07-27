package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import java.sql.Types
import java.util.Locale

internal sealed interface ResolvedAggregateIdStorage {
    data class Integral(
        val bits: Int,
        val unsigned: Boolean,
        val kotlinType: String,
    ) : ResolvedAggregateIdStorage

    data class Character(
        val capacity: Int,
        val kotlinType: String,
    ) : ResolvedAggregateIdStorage

    data class NativeUuid(
        val kotlinType: String,
    ) : ResolvedAggregateIdStorage
}

internal object AggregateIdStorageCatalog {
    private val integralDbTypePattern = Regex(
        "^\\s*(TINYINT|SMALLINT|MEDIUMINT|INT|INTEGER|BIGINT)\\s*(?:\\(\\s*\\d+\\s*\\))?\\s*(UNSIGNED)?\\s*$",
        RegexOption.IGNORE_CASE,
    )

    fun resolve(tableName: String, column: DbColumnSnapshot): ResolvedAggregateIdStorage {
        val path = "$tableName.${column.name}"
        val jdbcType = column.jdbcType ?: unsupported(path, column)

        return when (jdbcType) {
            Types.TINYINT,
            Types.SMALLINT,
            Types.INTEGER,
            Types.BIGINT,
            -> resolveIntegral(path, column, jdbcType)

            Types.CHAR,
            Types.VARCHAR,
            Types.LONGVARCHAR,
            Types.NCHAR,
            Types.NVARCHAR,
            Types.LONGNVARCHAR,
            -> resolveCharacter(path, column)

            Types.OTHER,
            Types.BINARY,
            -> resolveNativeUuid(path, column)

            else -> unsupported(path, column)
        }
    }

    private fun resolveIntegral(
        path: String,
        column: DbColumnSnapshot,
        jdbcType: Int,
    ): ResolvedAggregateIdStorage.Integral {
        val match = integralDbTypePattern.matchEntire(column.dbType) ?: unsupported(path, column)
        val name = match.groupValues[1].uppercase(Locale.ROOT)
        val unsigned = match.groupValues[2].isNotEmpty()
        val (bits, expectedJdbcType, acceptedKotlinTypes) = when (name) {
            "TINYINT" -> IntegralStorage(8, Types.TINYINT, setOf("Byte", "kotlin.Byte", "Int", "kotlin.Int"))
            "SMALLINT" -> IntegralStorage(16, Types.SMALLINT, setOf("Short", "kotlin.Short", "Int", "kotlin.Int"))
            "MEDIUMINT" -> IntegralStorage(24, Types.INTEGER, setOf("Int", "kotlin.Int"))
            "INT", "INTEGER" -> IntegralStorage(32, Types.INTEGER, setOf("Int", "kotlin.Int"))
            "BIGINT" -> IntegralStorage(64, Types.BIGINT, setOf("Long", "kotlin.Long"))
            else -> unsupported(path, column)
        }

        if (jdbcType != expectedJdbcType || column.kotlinType !in acceptedKotlinTypes) {
            unsupported(path, column)
        }

        return ResolvedAggregateIdStorage.Integral(bits, unsigned, column.kotlinType)
    }

    private fun resolveCharacter(
        path: String,
        column: DbColumnSnapshot,
    ): ResolvedAggregateIdStorage.Character {
        val capacity = column.columnSize
        if (column.kotlinType !in setOf("String", "kotlin.String") || capacity == null || capacity <= 0) {
            unsupported(path, column)
        }

        return ResolvedAggregateIdStorage.Character(capacity, column.kotlinType)
    }

    private fun resolveNativeUuid(
        path: String,
        column: DbColumnSnapshot,
    ): ResolvedAggregateIdStorage.NativeUuid {
        if (
            !column.dbType.equals("uuid", ignoreCase = true) ||
            column.kotlinType !in setOf("UUID", "java.util.UUID")
        ) {
            unsupported(path, column)
        }

        return ResolvedAggregateIdStorage.NativeUuid(column.kotlinType)
    }

    private fun unsupported(path: String, column: DbColumnSnapshot): Nothing =
        throw IllegalArgumentException(
            "unsupported aggregate ID storage for $path: " +
                "jdbcType=${column.jdbcType}, dbType=${column.dbType}, " +
                "kotlinType=${column.kotlinType}, columnSize=${column.columnSize}",
        )

    private data class IntegralStorage(
        val bits: Int,
        val jdbcType: Int,
        val kotlinTypes: Set<String>,
    )
}
