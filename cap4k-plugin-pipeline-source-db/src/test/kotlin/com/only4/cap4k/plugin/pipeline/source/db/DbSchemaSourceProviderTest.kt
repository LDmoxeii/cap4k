package com.only4.cap4k.plugin.pipeline.source.db

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import java.sql.DriverManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DbSchemaSourceProviderTest {

    @Test
    fun `collects normalized table column primary key and unique metadata`() {
        val url = "jdbc:h2:mem:cap4k-db-source;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table video_post (
                        id bigint primary key,
                        slug varchar(128) not null unique,
                        title varchar(255) not null,
                        published boolean default false
                    )
                    """.trimIndent()
                )
            }
        }

        val snapshot = DbSchemaSourceProvider().collect(
            ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = mapOf(
                    "db" to SourceConfig(
                        enabled = true,
                        options = mapOf(
                            "url" to url,
                            "username" to "sa",
                            "password" to "",
                            "schema" to "PUBLIC",
                            "includeTables" to listOf("video_post"),
                            "excludeTables" to emptyList<String>(),
                        )
                    )
                ),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        ) as DbSchemaSnapshot

        val table = snapshot.tables.single()
        assertEquals("VIDEO_POST", table.tableName)
        assertEquals(listOf("ID"), table.primaryKey)
        assertEquals(listOf(listOf("SLUG")), table.uniqueConstraints)
        assertEquals(listOf("ID", "SLUG", "TITLE", "PUBLISHED"), table.columns.map { it.name })
        assertEquals("Long", table.columns.first { it.name == "ID" }.kotlinType)
        assertEquals("Boolean", table.columns.first { it.name == "PUBLISHED" }.kotlinType)
    }

    @Test
    fun `collects quoted mixed case table metadata`() {
        val url = "jdbc:h2:mem:cap4k-db-source-mixed-case;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table "VideoPost" (
                        "Id" bigint primary key,
                        "Slug" varchar(128) not null unique,
                        "Title" varchar(255) not null,
                        "Published" boolean default false
                    )
                    """.trimIndent()
                )
            }
        }

        val snapshot = DbSchemaSourceProvider().collect(
            ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = mapOf(
                    "db" to SourceConfig(
                        enabled = true,
                        options = mapOf(
                            "url" to url,
                            "username" to "sa",
                            "password" to "",
                            "schema" to "PUBLIC",
                            "includeTables" to listOf("VideoPost"),
                            "excludeTables" to emptyList<String>(),
                        )
                    )
                ),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        ) as DbSchemaSnapshot

        val table = snapshot.tables.single()
        assertEquals("VideoPost", table.tableName)
        assertEquals(listOf("Id", "Slug", "Title", "Published"), table.columns.map { it.name })
        assertEquals(listOf("Id"), table.primaryKey)
        assertEquals(listOf(listOf("Slug")), table.uniqueConstraints)
        assertEquals("Long", table.columns.first { it.name == "Id" }.kotlinType)
        assertEquals("Boolean", table.columns.first { it.name == "Published" }.kotlinType)
    }

    @Test
    fun `prefers exact raw table name when quoted identifiers differ only by case`() {
        val url = "jdbc:h2:mem:cap4k-db-source-case-collision;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table "VideoPost" (
                        id bigint primary key
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    create table "videopost" (
                        id bigint primary key
                    )
                    """.trimIndent()
                )
            }
        }

        val snapshot = DbSchemaSourceProvider().collect(
            ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = mapOf(
                    "db" to SourceConfig(
                        enabled = true,
                        options = mapOf(
                            "url" to url,
                            "username" to "sa",
                            "password" to "",
                            "schema" to "PUBLIC",
                            "includeTables" to listOf("VideoPost"),
                            "excludeTables" to emptyList<String>(),
                        )
                    )
                ),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        ) as DbSchemaSnapshot

        assertEquals(listOf("VideoPost"), snapshot.tables.map { it.tableName })
    }

    @Test
    fun `returns no tables when include filter matches nothing`() {
        val url = "jdbc:h2:mem:cap4k-db-source-include-miss;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table video_post (
                        id bigint primary key
                    )
                    """.trimIndent()
                )
            }
        }

        val snapshot = DbSchemaSourceProvider().collect(
            ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = mapOf(
                    "db" to SourceConfig(
                        enabled = true,
                        options = mapOf(
                            "url" to url,
                            "username" to "sa",
                            "password" to "",
                            "schema" to "PUBLIC",
                            "includeTables" to listOf("video_post_typo"),
                            "excludeTables" to emptyList<String>(),
                        )
                    )
                ),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        ) as DbSchemaSnapshot

        assertEquals(emptyList<String>(), snapshot.tables.map { it.tableName })
    }

    @Test
    fun `preserves composite primary key and unique constraint column order`() {
        val url = "jdbc:h2:mem:cap4k-db-source-composite;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table account_entry (
                        sequence_id bigint not null,
                        tenant_id bigint not null,
                        code varchar(64) not null,
                        region_id bigint not null,
                        constraint pk_account_entry primary key (tenant_id, sequence_id),
                        constraint uq_account_entry unique (region_id, code)
                    )
                    """.trimIndent()
                )
            }
        }

        val snapshot = DbSchemaSourceProvider().collect(
            ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = mapOf(
                    "db" to SourceConfig(
                        enabled = true,
                        options = mapOf(
                            "url" to url,
                            "username" to "sa",
                            "password" to "",
                            "schema" to "PUBLIC",
                            "includeTables" to listOf("account_entry"),
                            "excludeTables" to emptyList<String>(),
                        )
                    )
                ),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        ) as DbSchemaSnapshot

        val table = snapshot.tables.single()
        assertEquals(listOf("SEQUENCE_ID", "TENANT_ID", "CODE", "REGION_ID"), table.columns.map { it.name })
        assertEquals(listOf("SEQUENCE_ID", "TENANT_ID"), table.primaryKey)
        assertEquals(listOf(listOf("REGION_ID", "CODE")), table.uniqueConstraints)
    }
}
