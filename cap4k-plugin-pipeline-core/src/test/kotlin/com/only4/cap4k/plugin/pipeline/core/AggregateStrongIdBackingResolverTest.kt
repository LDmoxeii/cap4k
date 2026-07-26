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
        assertThrows(IllegalArgumentException::class.java) {
            resolve(DbIdStrategy.UUID7, Types.VARCHAR, "VARCHAR", "String", 35)
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolve(DbIdStrategy.SNOWFLAKE, Types.VARCHAR, "VARCHAR", "String", 18)
        }
    }

    @Test
    fun `rejects crossed and guessed storage`() {
        val unsupported = listOf(
            UnsupportedCase(DbIdStrategy.UUID7, Types.BIGINT, "BIGINT", "Long", 64),
            UnsupportedCase(DbIdStrategy.UUID7, Types.OTHER, "jsonb", "String", 36),
            UnsupportedCase(DbIdStrategy.SNOWFLAKE, Types.INTEGER, "INTEGER", "Int", 32),
            UnsupportedCase(DbIdStrategy.SNOWFLAKE, Types.NUMERIC, "NUMERIC", "java.math.BigDecimal", 19),
            UnsupportedCase(DbIdStrategy.SNOWFLAKE, Types.OTHER, "UUID", "java.util.UUID", 16),
        )

        unsupported.forEach { case ->
            assertThrows(IllegalArgumentException::class.java) {
                resolve(
                    strategy = case.strategy,
                    jdbcType = case.jdbcType,
                    dbType = case.dbType,
                    kotlinType = case.kotlinType,
                    columnSize = case.columnSize,
                )
            }
        }
    }

    private data class UnsupportedCase(
        val strategy: DbIdStrategy,
        val jdbcType: Int,
        val dbType: String,
        val kotlinType: String,
        val columnSize: Int,
    )

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
