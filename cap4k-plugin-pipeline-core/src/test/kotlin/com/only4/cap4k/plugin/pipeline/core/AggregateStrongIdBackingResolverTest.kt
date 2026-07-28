package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbIdStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Types

class AggregateStrongIdBackingResolverTest {
    @Test
    fun `resolves the four supported storage nearest combinations`() {
        assertEquals(
            ResolvedStrongIdBacking("String", 36),
            resolve(DbIdStrategy.UUID7, Types.VARCHAR, "VARCHAR", "String", 36),
        )
        assertEquals(
            ResolvedStrongIdBacking("UUID", null),
            resolve(DbIdStrategy.UUID7, Types.OTHER, "UUID", "java.util.UUID", 16),
        )
        assertEquals(
            ResolvedStrongIdBacking("String", 19),
            resolve(DbIdStrategy.SNOWFLAKE, Types.VARCHAR, "VARCHAR", "String", 19),
        )
        assertEquals(
            ResolvedStrongIdBacking("Long", null),
            resolve(DbIdStrategy.SNOWFLAKE, Types.BIGINT, "BIGINT", "Long", 64),
        )
        assertEquals(
            ResolvedStrongIdBacking("Long", null),
            resolve(DbIdStrategy.SNOWFLAKE, Types.BIGINT, "BIGINT", "kotlin.Long", 64),
        )
    }

    @Test
    fun `rejects missing capacity for character storage`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            resolve(DbIdStrategy.UUID7, Types.VARCHAR, "VARCHAR", "String", null)
        }
        assertTrue(error.message!!.contains("columnSize"))
    }

    @Test
    fun `rejects undersized character storage`() {
        val uuid7Error = assertThrows(IllegalArgumentException::class.java) {
            resolve(DbIdStrategy.UUID7, Types.VARCHAR, "VARCHAR", "String", 35)
        }
        assertDiagnostic(uuid7Error, DbIdStrategy.UUID7)

        val snowflakeError = assertThrows(IllegalArgumentException::class.java) {
            resolve(DbIdStrategy.SNOWFLAKE, Types.VARCHAR, "VARCHAR", "String", 18)
        }
        assertDiagnostic(snowflakeError, DbIdStrategy.SNOWFLAKE)
    }

    @Test
    fun `rejects crossed and guessed storage with strategy path diagnostics`() {
        val unsupported = listOf(
            UnsupportedCase(DbIdStrategy.UUID7, Types.BIGINT, "BIGINT", "Long", 64),
            UnsupportedCase(DbIdStrategy.UUID7, Types.OTHER, "jsonb", "String", 36),
            UnsupportedCase(DbIdStrategy.SNOWFLAKE, Types.INTEGER, "INTEGER", "Int", 32),
            UnsupportedCase(DbIdStrategy.SNOWFLAKE, Types.NUMERIC, "NUMERIC", "java.math.BigDecimal", 19),
            UnsupportedCase(DbIdStrategy.SNOWFLAKE, Types.OTHER, "UUID", "java.util.UUID", 16),
        )

        unsupported.forEach { case ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                resolve(
                    strategy = case.strategy,
                    jdbcType = case.jdbcType,
                    dbType = case.dbType,
                    kotlinType = case.kotlinType,
                    columnSize = case.columnSize,
                )
            }
            assertDiagnostic(error, case.strategy)
        }
    }

    @Test
    fun `rejects Snowflake storage that is unsigned or not signed 64 bit Long`() {
        val unsupported = listOf(
            UnsupportedCase(DbIdStrategy.SNOWFLAKE, Types.BIGINT, "BIGINT UNSIGNED", "Long", 64),
            UnsupportedCase(DbIdStrategy.SNOWFLAKE, Types.INTEGER, "INTEGER", "Int", 32),
        )

        unsupported.forEach { case ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                resolve(
                    strategy = case.strategy,
                    jdbcType = case.jdbcType,
                    dbType = case.dbType,
                    kotlinType = case.kotlinType,
                    columnSize = case.columnSize,
                )
            }
            assertDiagnostic(error, DbIdStrategy.SNOWFLAKE)
        }
    }

    @Test
    fun `surfaces shared catalog diagnostics when JDBC evidence is missing`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            resolve(DbIdStrategy.UUID7, null, "VARCHAR", "String", 36)
        }

        assertDiagnostic(error, DbIdStrategy.UUID7)
        assertTrue(error.message!!.contains("missing jdbcType"))
        assertTrue(error.message!!.contains("unsupported aggregate ID storage"))
    }

    private data class UnsupportedCase(
        val strategy: DbIdStrategy,
        val jdbcType: Int,
        val dbType: String,
        val kotlinType: String,
        val columnSize: Int,
    )

    private fun assertDiagnostic(error: IllegalArgumentException, strategy: DbIdStrategy) {
        assertTrue(error.message!!.contains(strategy.name))
        assertTrue(error.message!!.contains("orders.id"))
    }

    private fun resolve(
        strategy: DbIdStrategy,
        jdbcType: Int?,
        dbType: String,
        kotlinType: String,
        columnSize: Int?,
    ): ResolvedStrongIdBacking = AggregateStrongIdBackingResolver.resolve(
        tableName = "orders",
        column = DbColumnSnapshot(
            name = "id",
            dbType = dbType,
            kotlinType = kotlinType,
            nullable = false,
            isPrimaryKey = true,
            idStrategy = strategy,
            jdbcType = jdbcType,
            columnSize = columnSize,
        ),
    )
}
