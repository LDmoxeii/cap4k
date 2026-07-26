package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.AggregateIdStorageKind
import com.only4.cap4k.plugin.pipeline.api.AggregateSoftDeletePolicy
import com.only4.cap4k.plugin.pipeline.api.SoftDeleteActiveSentinel
import com.only4.cap4k.plugin.pipeline.api.SoftDeleteTombstoneStrategy
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AggregateSoftDeleteRenderingTest {

    @Test
    fun resolverRecognizesSupportedJdbcUrlsCaseInsensitively() {
        val cases = listOf(
            "JDBC:MYSQL://localhost/demo" to AggregateSqlDialect.MYSQL,
            "jdbc:MaRiAdB://localhost/demo" to AggregateSqlDialect.MARIADB,
            "JDBC:H2:mem:demo" to AggregateSqlDialect.H2,
            "jdbc:h2:mem:demo;mode=MySQL;DATABASE_TO_UPPER=false" to AggregateSqlDialect.H2_MYSQL,
            "JDBC:POSTGRESQL://localhost/demo" to AggregateSqlDialect.POSTGRESQL,
        )

        cases.forEach { (jdbcUrl, expected) ->
            assertEquals(expected, AggregateSqlDialectResolver.resolve(jdbcUrl), jdbcUrl)
        }
    }

    @Test
    fun resolverTreatsH2MysqlModeAsSemicolonDelimitedSettingOnly() {
        assertAll(
            {
                assertEquals(
                    AggregateSqlDialect.H2_MYSQL,
                    AggregateSqlDialectResolver.resolve(
                        "jdbc:h2:mem:demo;DATABASE_TO_UPPER=false;MODE=MySQL;DB_CLOSE_DELAY=-1"
                    ),
                )
            },
            {
                assertEquals(
                    AggregateSqlDialect.H2,
                    AggregateSqlDialectResolver.resolve("jdbc:h2:mem:mode=mysql"),
                )
            },
            {
                assertEquals(
                    AggregateSqlDialect.H2,
                    AggregateSqlDialectResolver.resolve("jdbc:h2:mem:demo;SOME_MODE=MySQL;DB_CLOSE_DELAY=-1"),
                )
            },
            {
                assertEquals(
                    AggregateSqlDialect.H2,
                    AggregateSqlDialectResolver.resolve("jdbc:h2:mem:demo;MODE=MySQLish;DB_CLOSE_DELAY=-1"),
                )
            },
        )
    }

    @Test
    fun resolverRejectsBlankAndUnsupportedUrlsWithSupportedList() {
        listOf("", "   ", "jdbc:oracle:thin:@localhost:1521:xe").forEach { jdbcUrl ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                AggregateSqlDialectResolver.resolve(jdbcUrl)
            }

            assertAll(
                { assertTrue(error.message!!.contains("jdbc:mysql:"), error.message) },
                { assertTrue(error.message!!.contains("jdbc:mariadb:"), error.message) },
                { assertTrue(error.message!!.contains("jdbc:h2:"), error.message) },
                { assertTrue(error.message!!.contains("jdbc:postgresql:"), error.message) },
            )
        }
    }

    @Test
    fun renderQuotesAndEscapesBacktickIdentifiers() {
        val rendered = AggregateSoftDeleteRendering.render(
            policy = policy(AggregateIdStorageKind.INTEGRAL, SoftDeleteActiveSentinel.ZERO, "d\u0060e"),
            dialect = AggregateSqlDialect.MYSQL,
            tableName = "a\u0060b",
            idColumnName = "i\u0060d",
            versionColumnName = null,
            deletedKotlinType = "Long",
        )

        assertEquals("\u0060d\u0060\u0060e\u0060 = 0", rendered.whereClause)
        assertEquals(
            "update \u0060a\u0060\u0060b\u0060 set \u0060d\u0060\u0060e\u0060 = \u0060i\u0060\u0060d\u0060 where \u0060i\u0060\u0060d\u0060 = ?",
            rendered.sqlDelete,
        )
    }

    @Test
    fun renderQuotesAndEscapesDoubleQuoteIdentifiers() {
        val rendered = AggregateSoftDeleteRendering.render(
            policy = policy(AggregateIdStorageKind.INTEGRAL, SoftDeleteActiveSentinel.ZERO, "d\"e"),
            dialect = AggregateSqlDialect.POSTGRESQL,
            tableName = "a\"b",
            idColumnName = "i\"d",
            versionColumnName = null,
            deletedKotlinType = "Long",
        )

        assertEquals("\"d\"\"e\" = 0", rendered.whereClause)
        assertEquals(
            "update \"a\"\"b\" set \"d\"\"e\" = \"i\"\"d\" where \"i\"\"d\" = ?",
            rendered.sqlDelete,
        )
    }

    @Test
    fun renderIntegralZeroSqlLiteralAndKotlinInitializers() {
        val expectedInitializers = mapOf(
            "Byte" to "0",
            "Short" to "0",
            "Int" to "0",
            "Long" to "0L",
        )

        expectedInitializers.forEach { (deletedKotlinType, expectedInitializer) ->
            val rendered = AggregateSoftDeleteRendering.render(
                policy = policy(AggregateIdStorageKind.INTEGRAL, SoftDeleteActiveSentinel.ZERO),
                dialect = AggregateSqlDialect.H2,
                tableName = "sample",
                idColumnName = "id",
                versionColumnName = null,
                deletedKotlinType = deletedKotlinType,
            )

            assertEquals("0", rendered.activeSqlLiteral, deletedKotlinType)
            assertEquals(expectedInitializer, rendered.propertyInitializer, deletedKotlinType)
        }
    }

    @Test
    fun renderCharacterZeroAndNilUuidLiteralsAndInitializers() {
        val zero = AggregateSoftDeleteRendering.render(
            policy = policy(AggregateIdStorageKind.CHARACTER, SoftDeleteActiveSentinel.ZERO),
            dialect = AggregateSqlDialect.MARIADB,
            tableName = "sample",
            idColumnName = "id",
            versionColumnName = null,
            deletedKotlinType = "String",
        )
        val nil = AggregateSoftDeleteRendering.render(
            policy = policy(AggregateIdStorageKind.CHARACTER, SoftDeleteActiveSentinel.NIL_UUID),
            dialect = AggregateSqlDialect.POSTGRESQL,
            tableName = "sample",
            idColumnName = "id",
            versionColumnName = null,
            deletedKotlinType = "String",
        )

        assertAll(
            { assertEquals("'0'", zero.activeSqlLiteral) },
            { assertEquals("\"0\"", zero.propertyInitializer) },
            { assertEquals("'00000000-0000-0000-0000-000000000000'", nil.activeSqlLiteral) },
            { assertEquals("\"00000000-0000-0000-0000-000000000000\"", nil.propertyInitializer) },
        )
    }

    @Test
    fun renderNativeUuidUsesExplicitCastOnSupportedDialects() {
        listOf(
            AggregateSqlDialect.H2,
            AggregateSqlDialect.H2_MYSQL,
            AggregateSqlDialect.POSTGRESQL,
        ).forEach { dialect ->
            val rendered = AggregateSoftDeleteRendering.render(
                policy = policy(AggregateIdStorageKind.NATIVE_UUID, SoftDeleteActiveSentinel.NIL_UUID),
                dialect = dialect,
                tableName = "sample",
                idColumnName = "id",
                versionColumnName = null,
                deletedKotlinType = "UUID",
            )

            assertEquals("CAST('00000000-0000-0000-0000-000000000000' AS UUID)", rendered.activeSqlLiteral)
            assertEquals("UUID(0L, 0L)", rendered.propertyInitializer)
        }
    }

    @Test
    fun renderRejectsNativeUuidOnMysqlAndMariadb() {
        listOf(AggregateSqlDialect.MYSQL, AggregateSqlDialect.MARIADB).forEach { dialect ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                AggregateSoftDeleteRendering.render(
                    policy = policy(AggregateIdStorageKind.NATIVE_UUID, SoftDeleteActiveSentinel.NIL_UUID),
                    dialect = dialect,
                    tableName = "sample",
                    idColumnName = "id",
                    versionColumnName = null,
                    deletedKotlinType = "UUID",
                )
            }

            assertTrue(error.message!!.contains(dialect.name), error.message)
        }
    }

    @Test
    fun renderRejectsUnsupportedStorageSentinelAndKotlinTypeCombinations() {
        val cases = listOf(
            Triple(AggregateIdStorageKind.INTEGRAL, SoftDeleteActiveSentinel.NIL_UUID, "Long"),
            Triple(AggregateIdStorageKind.CHARACTER, SoftDeleteActiveSentinel.ZERO, "Long"),
            Triple(AggregateIdStorageKind.CHARACTER, SoftDeleteActiveSentinel.NIL_UUID, "UUID"),
            Triple(AggregateIdStorageKind.NATIVE_UUID, SoftDeleteActiveSentinel.ZERO, "UUID"),
            Triple(AggregateIdStorageKind.INTEGRAL, SoftDeleteActiveSentinel.ZERO, "String"),
        )

        cases.forEach { (storageKind, sentinel, deletedKotlinType) ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                AggregateSoftDeleteRendering.render(
                    policy = policy(storageKind, sentinel),
                    dialect = AggregateSqlDialect.H2,
                    tableName = "sample",
                    idColumnName = "id",
                    versionColumnName = null,
                    deletedKotlinType = deletedKotlinType,
                )
            }

            assertTrue(error.message!!.contains(storageKind.name), error.message)
            assertTrue(error.message!!.contains(sentinel.name), error.message)
            assertTrue(error.message!!.contains(deletedKotlinType), error.message)
        }
    }

    @Test
    fun renderVersionlessSqlDeleteUsesIdPlaceholder() {
        val rendered = AggregateSoftDeleteRendering.render(
            policy = policy(AggregateIdStorageKind.INTEGRAL, SoftDeleteActiveSentinel.ZERO),
            dialect = AggregateSqlDialect.H2,
            tableName = "Video_Post",
            idColumnName = "Video_Post_ID",
            versionColumnName = null,
            deletedKotlinType = "Long",
        )

        assertEquals(
            "update \"Video_Post\" set \"deleted\" = \"Video_Post_ID\" where \"Video_Post_ID\" = ?",
            rendered.sqlDelete,
        )
    }

    @Test
    fun renderVersionedSqlDeleteKeepsIdThenVersionPlaceholderOrder() {
        val rendered = AggregateSoftDeleteRendering.render(
            policy = policy(AggregateIdStorageKind.CHARACTER, SoftDeleteActiveSentinel.NIL_UUID),
            dialect = AggregateSqlDialect.POSTGRESQL,
            tableName = "Video_Post",
            idColumnName = "Video_Post_ID",
            versionColumnName = "Lock_Version",
            deletedKotlinType = "String",
        )

        assertEquals(
            "update \"Video_Post\" set \"deleted\" = \"Video_Post_ID\" where \"Video_Post_ID\" = ? and \"Lock_Version\" = ?",
            rendered.sqlDelete,
        )
    }

    private fun policy(
        storageKind: AggregateIdStorageKind,
        activeSentinel: SoftDeleteActiveSentinel,
        columnName: String = "deleted",
    ): AggregateSoftDeletePolicy =
        AggregateSoftDeletePolicy(
            fieldName = "deleted",
            columnName = columnName,
            storageKind = storageKind,
            activeSentinel = activeSentinel,
            tombstoneStrategy = SoftDeleteTombstoneStrategy.SELF_ID,
        )
}
