package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.AggregateIdStorageKind
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DbIdStrategy
import com.only4.cap4k.plugin.pipeline.api.DbManagedRole
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SoftDeleteActiveSentinel
import com.only4.cap4k.plugin.pipeline.api.SoftDeleteTombstoneStrategy
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssembler
import com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlanner
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRenderer
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PresetTemplateResolver
import com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProvider
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class PostgreSqlSoftDeleteIntegrationTest {
    @Test
    fun `real PostgreSQL proves native UUID soft delete from metadata through executed planner SQL`() {
        val environment = postgresEnvironmentOrSkip()
        val schemaName = "cap4k_soft_delete_${UUID.randomUUID().toString().replace("-", "")}"

        DriverManager.getConnection(environment.url, environment.user, environment.password).use { connection ->
            check(connection.metaData.databaseProductName.equals("PostgreSQL", ignoreCase = true)) {
                "Real PostgreSQL integration connected to ${connection.metaData.databaseProductName} instead of PostgreSQL"
            }
            val originalSchema = connection.schema
            var schemaCreated = false
            runWithCleanup(
                action = {
                    connection.createStatement().use { statement ->
                        statement.execute("create schema \"$schemaName\"")
                    }
                    schemaCreated = true
                    createFixture(connection, schemaName)
                    connection.schema = schemaName

                    assertPipelineAndSqlLifecycle(connection, environment, schemaName)
                },
                cleanup = {
                    if (schemaCreated) {
                        connection.schema = originalSchema ?: "public"
                        connection.createStatement().use { statement ->
                            statement.execute("drop schema if exists \"$schemaName\" cascade")
                        }
                    }
                },
            )
        }
    }

    @Test
    fun `cleanup failure is suppressed when the evidence body already failed`() {
        val bodyFailure = IllegalStateException("body failed")
        val cleanupFailure = IllegalStateException("cleanup failed")

        val thrown = assertThrows(IllegalStateException::class.java) {
            runWithCleanup(
                action = { throw bodyFailure },
                cleanup = { throw cleanupFailure },
            )
        }

        assertSame(bodyFailure, thrown)
        assertEquals(listOf(cleanupFailure), thrown.suppressed.toList())
    }

    @Test
    fun `cleanup failure remains fatal when the evidence body succeeded`() {
        val cleanupFailure = IllegalStateException("cleanup failed")

        val thrown = assertThrows(IllegalStateException::class.java) {
            runWithCleanup(
                action = {},
                cleanup = { throw cleanupFailure },
            )
        }

        assertSame(cleanupFailure, thrown)
    }

    private inline fun <T> runWithCleanup(
        action: () -> T,
        cleanup: () -> Unit,
    ): T {
        var primaryFailure: Throwable? = null
        try {
            return action()
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            try {
                cleanup()
            } catch (cleanupFailure: Throwable) {
                val failure = primaryFailure
                if (failure == null) {
                    throw cleanupFailure
                }
                failure.addSuppressed(cleanupFailure)
            }
        }
    }

    private fun assertPipelineAndSqlLifecycle(
        connection: Connection,
        environment: PostgreSqlEnvironment,
        schemaName: String,
    ) {
        val config = ProjectConfig(
            basePackage = "com.acme.postgres",
            layout = ProjectLayout.MULTI_MODULE,
            modules = mapOf(
                "domain" to "demo-domain",
                "application" to "demo-application",
                "adapter" to "demo-adapter",
            ),
            sources = mapOf(
                "db" to SourceConfig(
                    options = mapOf(
                        "url" to environment.url,
                        "username" to environment.user,
                        "password" to environment.password,
                        "schema" to schemaName,
                        "includeTables" to listOf("PgUuidRecord"),
                        "excludeTables" to emptyList<String>(),
                    )
                )
            ),
            generators = mapOf(
                "aggregate" to GeneratorConfig(
                    options = mapOf(
                        "artifact.factory" to false,
                        "artifact.specification" to false,
                        "artifact.unique" to false,
                    )
                )
            ),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )

        val snapshot = DbSchemaSourceProvider().collect(config)
        val table = snapshot.tables.single()
        val idColumn = table.columns.single { it.name == "Id" }
        val deletedColumn = table.columns.single { it.name == "Deleted" }
        val defaultWrapper = requireNotNull(deletedColumn.defaultValue)

        assertAll(
            { assertEquals("PgUuidRecord", table.tableName) },
            { assertEquals(DbIdStrategy.UUID7, idColumn.idStrategy) },
            { assertEquals(DbManagedRole.DELETED, deletedColumn.managedRole) },
            {
                assertTrue(
                    idColumn.jdbcType in setOf(Types.OTHER, Types.BINARY),
                    "actual PostgreSQL id DATA_TYPE was ${idColumn.jdbcType}",
                )
            },
            {
                assertTrue(
                    deletedColumn.jdbcType in setOf(Types.OTHER, Types.BINARY),
                    "actual PostgreSQL deleted DATA_TYPE was ${deletedColumn.jdbcType}",
                )
            },
            { assertTrue(idColumn.dbType.equals("uuid", ignoreCase = true), idColumn.dbType) },
            { assertTrue(deletedColumn.dbType.equals("uuid", ignoreCase = true), deletedColumn.dbType) },
            { assertEquals("UUID", idColumn.kotlinType) },
            { assertEquals("UUID", deletedColumn.kotlinType) },
            {
                assertTrue(
                    defaultWrapper.contains(NIL_UUID),
                    "actual PostgreSQL COLUMN_DEF wrapper was $defaultWrapper",
                )
            },
        )

        val canonical = DefaultCanonicalAssembler().assemble(config, listOf(snapshot)).model
        val providerControl = canonical.aggregatePersistenceProviderControls.single {
            it.tableName == "PgUuidRecord"
        }
        val policy = requireNotNull(providerControl.softDelete) {
            "PostgreSQL default wrapper did not normalize into a semantic soft-delete policy: $defaultWrapper"
        }
        val strongId = canonical.strongIds.single {
            it.ownerEntityName == "PgUuidRecord" && it.ownerAggregateName == "PgUuidRecord"
        }
        val forbiddenSqlFields = setOf("activeValue", "activePredicateSql", "deleteAssignmentSql")

        assertAll(
            { assertEquals(AggregateIdStorageKind.NATIVE_UUID, policy.storageKind, defaultWrapper) },
            { assertEquals(SoftDeleteActiveSentinel.NIL_UUID, policy.activeSentinel, defaultWrapper) },
            { assertEquals(SoftDeleteTombstoneStrategy.SELF_ID, policy.tombstoneStrategy) },
            { assertEquals("Deleted", policy.columnName) },
            { assertEquals("deleted", policy.fieldName) },
            { assertEquals("PgUuidRecordId", strongId.typeName) },
            { assertEquals("UUID", strongId.valueType) },
            {
                assertTrue(
                    policy.javaClass.declaredFields.none { it.name in forbiddenSqlFields },
                    "canonical soft-delete policy leaked SQL fields: ${policy.javaClass.declaredFields.map { it.name }}",
                )
            },
            { assertFalse(policy.toString().contains("update ", ignoreCase = true), policy.toString()) },
            { assertFalse(policy.toString().contains(" where ", ignoreCase = true), policy.toString()) },
        )

        val planItems = AggregateArtifactPlanner().plan(config, canonical)
        val entityPlan = planItems.single {
            it.templateId == "aggregate/entity.kt.peb" && it.context["typeName"] == "PgUuidRecord"
        }
        val softDeleteSql = requireNotNull(entityPlan.context["softDeleteSql"] as? String)
        val softDeleteWhereClause = requireNotNull(entityPlan.context["softDeleteWhereClause"] as? String)
        val expectedSoftDeleteSql =
            "update \"PgUuidRecord\" set \"Deleted\" = \"Id\" where \"Id\" = ?"
        val expectedWhereClause = "\"Deleted\" = CAST('$NIL_UUID' AS UUID)"

        @Suppress("UNCHECKED_CAST")
        val scalarFields = entityPlan.context.getValue("scalarFields") as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val constructorFields = entityPlan.context.getValue("constructorFields") as List<Map<String, Any?>>
        val idField = scalarFields.single { it["name"] == "id" }
        val deletedField = scalarFields.single { it["name"] == "deleted" }

        assertAll(
            { assertEquals(expectedSoftDeleteSql, softDeleteSql) },
            { assertEquals(expectedWhereClause, softDeleteWhereClause) },
            { assertEquals("PgUuidRecordId", idField["fieldType"]) },
            { assertEquals(true, idField["strongId"]) },
            { assertEquals(true, idField["generatedOwnId"]) },
            { assertEquals("UUID", deletedField["fieldType"]) },
            { assertEquals(false, deletedField["strongId"]) },
            { assertEquals("UUID(0L, 0L)", deletedField["propertyInitializer"]) },
            { assertFalse(constructorFields.any { it["name"] == "deleted" }) },
            { assertFalse(constructorFields.any { it["name"] == "id" }) },
        )

        val renderedEntity = PebbleArtifactRenderer(
            PresetTemplateResolver("ddd-default", emptyList())
        ).render(planItems, config).single { it.outputPath == entityPlan.outputPath }.content
        val constructorBlock = renderedEntity
            .substringAfter("class PgUuidRecord internal constructor(")
            .substringBefore(") {")

        assertAll(
            { assertTrue(renderedEntity.contains("class PgUuidRecord internal constructor("), renderedEntity) },
            { assertFalse(constructorBlock.contains("deleted"), constructorBlock) },
            { assertFalse(constructorBlock.contains("PgUuidRecordId"), constructorBlock) },
            { assertTrue(renderedEntity.contains("lateinit var id: PgUuidRecordId"), renderedEntity) },
            { assertTrue(renderedEntity.contains("var deleted: UUID = UUID(0L, 0L)"), renderedEntity) },
            { assertFalse(renderedEntity.contains("var deleted: PgUuidRecordId"), renderedEntity) },
            { assertTrue(renderedEntity.contains("@SQLDelete(sql = \"update \\\"PgUuidRecord\\\" set \\\"Deleted\\\" = \\\"Id\\\" where \\\"Id\\\" = ?\")"), renderedEntity) },
            { assertTrue(renderedEntity.contains("@Where(clause = \"\\\"Deleted\\\" = CAST('$NIL_UUID' AS UUID)\")"), renderedEntity) },
        )

        val id = UUID.randomUUID()
        insertActiveRow(connection, id)
        assertEquals(1L, activeRowCount(connection, softDeleteWhereClause))
        assertEquals(1L, physicalRowCount(connection))
        assertEquals(PhysicalRow(id, UUID(0L, 0L)), physicalRow(connection, id))

        connection.prepareStatement(softDeleteSql).use { statement ->
            statement.setObject(1, id)
            assertEquals(1, statement.executeUpdate())
        }

        assertEquals(0L, activeRowCount(connection, softDeleteWhereClause))
        assertEquals(1L, physicalRowCount(connection))
        assertEquals(PhysicalRow(id, id), physicalRow(connection, id))
    }

    private fun insertActiveRow(connection: Connection, id: UUID) {
        connection.prepareStatement(
            "insert into \"PgUuidRecord\" (\"Id\", \"DisplayName\") values (?, ?)"
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, "postgresql evidence")
            assertEquals(1, statement.executeUpdate())
        }
    }

    private fun activeRowCount(connection: Connection, whereClause: String): Long =
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "select count(*) from \"PgUuidRecord\" where $whereClause"
            ).use { rows ->
                assertTrue(rows.next())
                rows.getLong(1)
            }
        }

    private fun physicalRowCount(connection: Connection): Long =
        connection.createStatement().use { statement ->
            statement.executeQuery("select count(*) from \"PgUuidRecord\"").use { rows ->
                assertTrue(rows.next())
                rows.getLong(1)
            }
        }

    private fun physicalRow(connection: Connection, id: UUID): PhysicalRow =
        connection.prepareStatement(
            "select \"Id\", \"Deleted\" from \"PgUuidRecord\" where \"Id\" = ?"
        ).use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { rows ->
                assertTrue(rows.next())
                val row = PhysicalRow(
                    id = rows.getObject("Id", UUID::class.java),
                    deleted = rows.getObject("Deleted", UUID::class.java),
                )
                assertFalse(rows.next())
                row
            }
        }

    private fun createFixture(connection: Connection, schemaName: String) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                create table "$schemaName"."PgUuidRecord" (
                    "Id" uuid primary key,
                    "Deleted" uuid not null default '00000000-0000-0000-0000-000000000000',
                    "DisplayName" varchar(128) not null
                )
                """.trimIndent()
            )
            statement.execute(
                "comment on column \"$schemaName\".\"PgUuidRecord\".\"Id\" is '@IdStrategy=uuid7;'"
            )
            statement.execute(
                "comment on column \"$schemaName\".\"PgUuidRecord\".\"Deleted\" is '@Managed=deleted;'"
            )
        }
    }

    private fun postgresEnvironmentOrSkip(): PostgreSqlEnvironment {
        val values = POSTGRES_ENVIRONMENT_VARIABLES.associateWith(System::getenv)
        val missing = POSTGRES_ENVIRONMENT_VARIABLES.filter { values[it].isNullOrBlank() }
        val runningInCi = System.getenv("CI")?.equals("true", ignoreCase = true) == true

        if (missing.size == POSTGRES_ENVIRONMENT_VARIABLES.size && !runningInCi) {
            println(LOCAL_SKIP_MESSAGE)
            assumeTrue(false, LOCAL_SKIP_MESSAGE)
        }

        check(missing.isEmpty()) {
            "Real PostgreSQL integration requires all environment variables; missing: ${missing.joinToString(", ")}"
        }

        val url = requireNotNull(values.getValue(POSTGRES_URL_ENV))
        check(url.startsWith(POSTGRES_JDBC_URL_PREFIX)) {
            "$POSTGRES_URL_ENV must start with $POSTGRES_JDBC_URL_PREFIX for real PostgreSQL evidence"
        }

        return PostgreSqlEnvironment(
            url = url,
            user = requireNotNull(values.getValue(POSTGRES_USER_ENV)),
            password = requireNotNull(values.getValue(POSTGRES_PASSWORD_ENV)),
        )
    }

    private data class PostgreSqlEnvironment(
        val url: String,
        val user: String,
        val password: String,
    )

    private data class PhysicalRow(
        val id: UUID,
        val deleted: UUID,
    )

    private companion object {
        const val POSTGRES_URL_ENV = "CAP4K_TEST_POSTGRES_URL"
        const val POSTGRES_USER_ENV = "CAP4K_TEST_POSTGRES_USER"
        const val POSTGRES_PASSWORD_ENV = "CAP4K_TEST_POSTGRES_PASSWORD"
        const val POSTGRES_JDBC_URL_PREFIX = "jdbc:postgresql:"
        const val NIL_UUID = "00000000-0000-0000-0000-000000000000"
        const val LOCAL_SKIP_MESSAGE =
            "Real PostgreSQL evidence did not run: all CAP4K_TEST_POSTGRES_* variables are absent outside CI."

        val POSTGRES_ENVIRONMENT_VARIABLES = listOf(
            POSTGRES_URL_ENV,
            POSTGRES_USER_ENV,
            POSTGRES_PASSWORD_ENV,
        )
    }
}
