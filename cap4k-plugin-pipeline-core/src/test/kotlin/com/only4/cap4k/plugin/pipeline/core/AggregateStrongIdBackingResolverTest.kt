package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Types

class AggregateStrongIdBackingResolverTest {
    @Test
    fun `resolves the two UUID7 storage nearest combinations`() {
        assertEquals(
            ResolvedStrongIdBacking("String", 36),
            resolve("uuid7", Types.VARCHAR, "VARCHAR", "String", 36),
        )
        assertEquals(
            ResolvedStrongIdBacking("UUID", null),
            resolve("uuid7", Types.OTHER, "UUID", "java.util.UUID", 16),
        )
    }

    @Test
    fun `rejects missing capacity for character storage`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            resolve("uuid7", Types.VARCHAR, "VARCHAR", "String", null)
        }
        assertTrue(error.message!!.contains("columnSize"))
    }

    @Test
    fun `rejects undersized character storage`() {
        val uuid7Error = assertThrows(IllegalArgumentException::class.java) {
            resolve("uuid7", Types.VARCHAR, "VARCHAR", "String", 35)
        }
        assertDiagnostic(uuid7Error, "uuid7")

    }

    @Test
    fun `rejects crossed and guessed storage with strategy path diagnostics`() {
        val unsupported = listOf(
            UnsupportedCase("uuid7", Types.BIGINT, "BIGINT", "Long", 64),
            UnsupportedCase("uuid7", Types.OTHER, "jsonb", "String", 36),
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
    fun `rejects retired snowflake before considering storage`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            resolve("snowflake", Types.BIGINT, "BIGINT", "Long", 64)
        }

        assertTrue(error.message!!.contains("rejected value 'snowflake'"), error.message)
        assertTrue(error.message!!.contains("orders.id"), error.message)
        assertTrue(error.message!!.contains("supported application-side strategy: uuid7"), error.message)
    }

    @Test
    fun `surfaces shared catalog diagnostics when JDBC evidence is missing`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            resolve("uuid7", null, "VARCHAR", "String", 36)
        }

        assertDiagnostic(error, "uuid7")
        assertTrue(error.message!!.contains("missing jdbcType"))
        assertTrue(error.message!!.contains("unsupported aggregate ID storage"))
    }

    private data class UnsupportedCase(
        val strategy: String,
        val jdbcType: Int,
        val dbType: String,
        val kotlinType: String,
        val columnSize: Int,
    )

    private fun assertDiagnostic(error: IllegalArgumentException, strategy: String) {
        assertTrue(error.message!!.contains(strategy.uppercase()))
        assertTrue(error.message!!.contains("orders.id"))
    }

    private fun resolve(
        strategy: String,
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
            jdbcType = jdbcType,
            columnSize = columnSize,
        ),
        strategy = strategy,
    )
}
