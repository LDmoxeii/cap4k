package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.AggregateIdStorageKind
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SoftDeleteActiveSentinel
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssembler
import com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlanner
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRenderer
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PresetTemplateResolver
import com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProvider
import java.sql.DriverManager
import java.sql.Types
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class H2NarrowIntegralMetadataIntegrationTest {
    @Test
    fun `real H2 TINYINT metadata reaches aggregate generation`() {
        assertNarrowIntegralPipeline(
            NarrowIntegralFixture(
                databaseName = "cap4k_narrow_tinyint",
                tableName = "TinyRecord",
                sqlType = "TINYINT",
                jdbcType = Types.TINYINT,
                physicalBits = 8,
            )
        )
    }

    @Test
    fun `real H2 SMALLINT metadata reaches aggregate generation`() {
        assertNarrowIntegralPipeline(
            NarrowIntegralFixture(
                databaseName = "cap4k_narrow_smallint",
                tableName = "SmallRecord",
                sqlType = "SMALLINT",
                jdbcType = Types.SMALLINT,
                physicalBits = 16,
            )
        )
    }

    private fun assertNarrowIntegralPipeline(fixture: NarrowIntegralFixture) {
        val url = "jdbc:h2:mem:${fixture.databaseName};DB_CLOSE_DELAY=-1"
        createFixture(url, fixture)
        val config = projectConfig(url, fixture.tableName)

        val snapshot = DbSchemaSourceProvider().collect(config)
        val table = snapshot.tables.single()
        val idColumn = table.columns.single { it.name == "Id" }
        val deletedColumn = table.columns.single { it.name == "Deleted" }
        val evidence = listOf(idColumn, deletedColumn).joinToString(separator = "; ") { column ->
            "${table.tableName}.${column.name}[jdbcType=${column.jdbcType}, dbType=${column.dbType}, " +
                "kotlinType=${column.kotlinType}, columnSize=${column.columnSize}, default=${column.defaultValue}]"
        }

        assertAll(
            { assertEquals(fixture.tableName, table.tableName, evidence) },
            { assertEquals(fixture.jdbcType, idColumn.jdbcType, evidence) },
            { assertEquals(fixture.jdbcType, deletedColumn.jdbcType, evidence) },
            { assertEquals(fixture.sqlType, idColumn.dbType, evidence) },
            { assertEquals(fixture.sqlType, deletedColumn.dbType, evidence) },
            { assertEquals("Int", idColumn.kotlinType, evidence) },
            { assertEquals("Int", deletedColumn.kotlinType, evidence) },
            { assertEquals(fixture.physicalBits, idColumn.columnSize, evidence) },
            { assertEquals(fixture.physicalBits, deletedColumn.columnSize, evidence) },
            { assertEquals("identifier.database-identity", idColumn.managedPolicyKey, evidence) },
            { assertEquals("soft-delete", deletedColumn.managedPolicyKey, evidence) },
            { assertEquals("0", deletedColumn.defaultValue, evidence) },
        )

        val canonical = DefaultCanonicalAssembler().assemble(config, listOf(snapshot)).model
        val providerControl = canonical.aggregatePersistenceProviderControls.single {
            it.tableName == fixture.tableName
        }
        val policy = requireNotNull(providerControl.softDelete)
        assertAll(
            { assertEquals(AggregateIdStorageKind.INTEGRAL, policy.storageKind, evidence) },
            { assertEquals(SoftDeleteActiveSentinel.ZERO, policy.activeSentinel, evidence) },
            { assertEquals("Deleted", policy.columnName, evidence) },
        )

        val planItems = AggregateArtifactPlanner().plan(config, canonical)
        val entityPlan = planItems.single {
            it.templateId == "aggregate/entity.kt.peb" && it.context["typeName"] == fixture.tableName
        }
        @Suppress("UNCHECKED_CAST")
        val scalarFields = entityPlan.context.getValue("scalarFields") as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val constructorFields = entityPlan.context.getValue("constructorFields") as List<Map<String, Any?>>
        val idField = scalarFields.single { it["name"] == "id" }
        val deletedField = scalarFields.single { it["name"] == "deleted" }

        assertAll(
            { assertEquals("Int", idField["fieldType"], evidence) },
            { assertEquals("IDENTITY", idField["generatedValueStrategy"], evidence) },
            { assertEquals("Int", deletedField["fieldType"], evidence) },
            { assertEquals("0", deletedField["propertyInitializer"], evidence) },
            { assertEquals(false, deletedField["strongId"], evidence) },
            { assertFalse(constructorFields.any { it["name"] == "deleted" }, evidence) },
        )

        val renderedEntity = PebbleArtifactRenderer(
            PresetTemplateResolver("ddd-default", emptyList())
        ).render(planItems, config).single { it.outputPath == entityPlan.outputPath }.content
        val constructorBlock = renderedEntity
            .substringAfter("class ${fixture.tableName} internal constructor(")
            .substringBefore(") {")

        assertAll(
            { assertTrue(renderedEntity.contains("var deleted: Int = 0"), renderedEntity) },
            { assertTrue(renderedEntity.contains("@SQLDelete"), renderedEntity) },
            { assertTrue(renderedEntity.contains("@Where"), renderedEntity) },
            { assertFalse(constructorBlock.contains("deleted"), constructorBlock) },
        )
    }

    private fun createFixture(url: String, fixture: NarrowIntegralFixture) {
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("drop table if exists \"${fixture.tableName}\"")
                statement.execute(
                    """
                    create table "${fixture.tableName}" (
                        "Id" ${fixture.sqlType} primary key,
                        "Deleted" ${fixture.sqlType} not null default 0,
                        "DisplayName" varchar(64) not null
                    )
                    """.trimIndent()
                )
                statement.execute(
                    "comment on column \"${fixture.tableName}\".\"Id\" is '@Managed=identifier.database-identity;'"
                )
                statement.execute(
                    "comment on column \"${fixture.tableName}\".\"Deleted\" is '@Managed=soft-delete;'"
                )
            }
        }
    }

    private fun projectConfig(url: String, tableName: String): ProjectConfig = ProjectConfig(
        basePackage = "com.acme.narrow",
        layout = ProjectLayout.MULTI_MODULE,
        modules = mapOf(
            "domain" to "demo-domain",
            "application" to "demo-application",
            "adapter" to "demo-adapter",
        ),
        sources = mapOf(
            "db" to SourceConfig(
                options = mapOf(
                    "url" to url,
                    "username" to "sa",
                    "password" to "",
                    "schema" to "PUBLIC",
                    "includeTables" to listOf(tableName),
                    "excludeTables" to emptyList<String>(),
                )
            )
        ),
        generators = mapOf(
            "aggregate" to GeneratorConfig(
                options = mapOf(
                )
            )
        ),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )

    private data class NarrowIntegralFixture(
        val databaseName: String,
        val tableName: String,
        val sqlType: String,
        val jdbcType: Int,
        val physicalBits: Int,
    )
}
