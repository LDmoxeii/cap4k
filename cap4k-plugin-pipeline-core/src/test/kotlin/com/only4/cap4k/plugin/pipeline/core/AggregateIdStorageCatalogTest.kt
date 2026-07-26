package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Types

class AggregateIdStorageCatalogTest {
    @Test
    fun `classifies integral storage from the JDBC integral family and finite vendor types`() {
        val cases = listOf(
            IntegralCase(Types.TINYINT, "TINYINT", "Byte", 8, false),
            IntegralCase(Types.TINYINT, "TINYINT", "kotlin.Byte", 8, false),
            IntegralCase(Types.SMALLINT, "SMALLINT", "Short", 16, false),
            IntegralCase(Types.SMALLINT, "SMALLINT", "kotlin.Short", 16, false),
            IntegralCase(Types.INTEGER, "MEDIUMINT", "Int", 24, false),
            IntegralCase(Types.INTEGER, "INT(11) UNSIGNED", "kotlin.Int", 32, true),
            IntegralCase(Types.INTEGER, " integer ( 11 )   unsigned ", "Int", 32, true),
            IntegralCase(Types.BIGINT, "BIGINT", "Long", 64, false),
            IntegralCase(Types.BIGINT, "BIGINT", "kotlin.Long", 64, false),
        )

        cases.forEach { case ->
            assertEquals(
                ResolvedAggregateIdStorage.Integral(
                    bits = case.bits,
                    unsigned = case.unsigned,
                    kotlinType = case.kotlinType,
                ),
                resolve(case.jdbcType, case.dbType, case.kotlinType),
                "${case.jdbcType}/${case.dbType}/${case.kotlinType}",
            )
        }
    }

    @Test
    fun `classifies each JDBC character family with its exact positive capacity`() {
        val cases = listOf(
            CharacterCase(Types.CHAR, "CHAR", "String", 1),
            CharacterCase(Types.VARCHAR, "VARCHAR", "kotlin.String", 36),
            CharacterCase(Types.LONGVARCHAR, "TEXT", "String", 255),
            CharacterCase(Types.NCHAR, "NCHAR", "kotlin.String", 2),
            CharacterCase(Types.NVARCHAR, "NVARCHAR", "String", 64),
            CharacterCase(Types.LONGNVARCHAR, "NTEXT", "kotlin.String", 512),
        )

        cases.forEach { case ->
            assertEquals(
                ResolvedAggregateIdStorage.Character(
                    capacity = case.columnSize,
                    kotlinType = case.kotlinType,
                ),
                resolve(case.jdbcType, case.dbType, case.kotlinType, case.columnSize),
                "${case.jdbcType}/${case.dbType}/${case.kotlinType}",
            )
        }
    }

    @Test
    fun `classifies native UUID storage from the permitted JDBC and vendor evidence`() {
        val cases = listOf(
            NativeUuidCase(Types.OTHER, "UUID", "UUID"),
            NativeUuidCase(Types.BINARY, " uuid ", "java.util.UUID"),
        )

        cases.forEach { case ->
            assertEquals(
                ResolvedAggregateIdStorage.NativeUuid(case.kotlinType),
                resolve(case.jdbcType, case.dbType, case.kotlinType, 16),
                "${case.jdbcType}/${case.dbType}/${case.kotlinType}",
            )
        }
    }

    @Test
    fun `rejects unsupported missing and contradictory physical storage evidence`() {
        val cases = listOf(
            RejectedCase(Types.BINARY, "BINARY(16)", "java.util.UUID", 16),
            RejectedCase(Types.DECIMAL, "DECIMAL(19, 0)", "Long", 19),
            RejectedCase(Types.NUMERIC, "NUMERIC(19, 0)", "Long", 19),
            RejectedCase(Types.FLOAT, "FLOAT", "Double", 24),
            RejectedCase(Types.REAL, "REAL", "Float", 24),
            RejectedCase(Types.DOUBLE, "DOUBLE", "Double", 53),
            RejectedCase(Types.OTHER, "jsonb", "String", 255),
            RejectedCase(null, "BIGINT", "Long", 64),
            RejectedCase(Types.VARCHAR, "VARCHAR", "String", null),
            RejectedCase(Types.VARCHAR, "VARCHAR", "String", 0),
            RejectedCase(Types.VARCHAR, "VARCHAR", "String", -1),
            RejectedCase(Types.VARCHAR, "INT", "Int", 32),
            RejectedCase(Types.INTEGER, "INT", "String", 32),
            RejectedCase(Types.VARCHAR, "VARCHAR", "Int", 32),
            RejectedCase(Types.OTHER, "uuid", "String", 16),
            RejectedCase(Types.INTEGER, "INT(11) ZEROFILL", "Int", 32),
        )

        cases.forEach { case ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                resolve(case.jdbcType, case.dbType, case.kotlinType, case.columnSize)
            }
            val message = error.message!!
            assertTrue(message.contains("sample.id"), message)
            assertTrue(message.contains("jdbcType=${case.jdbcType}"), message)
            assertTrue(message.contains("dbType=${case.dbType}"), message)
            assertTrue(message.contains("kotlinType=${case.kotlinType}"), message)
            assertTrue(message.contains("columnSize=${case.columnSize}"), message)
        }
    }

    private data class IntegralCase(
        val jdbcType: Int,
        val dbType: String,
        val kotlinType: String,
        val bits: Int,
        val unsigned: Boolean,
    )

    private data class CharacterCase(
        val jdbcType: Int,
        val dbType: String,
        val kotlinType: String,
        val columnSize: Int,
    )

    private data class NativeUuidCase(
        val jdbcType: Int,
        val dbType: String,
        val kotlinType: String,
    )

    private data class RejectedCase(
        val jdbcType: Int?,
        val dbType: String,
        val kotlinType: String,
        val columnSize: Int?,
    )

    private fun resolve(
        jdbcType: Int?,
        dbType: String,
        kotlinType: String,
        columnSize: Int? = null,
    ): ResolvedAggregateIdStorage = AggregateIdStorageCatalog.resolve(
        tableName = "sample",
        column = DbColumnSnapshot(
            name = "id",
            dbType = dbType,
            kotlinType = kotlinType,
            nullable = false,
            isPrimaryKey = true,
            jdbcType = jdbcType,
            columnSize = columnSize,
        ),
    )
}
