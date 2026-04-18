package com.only4.cap4k.plugin.pipeline.source.db

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import java.sql.DriverManager
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DbSchemaSourceProviderTest {

    @Test
    fun `db source records table and column relation metadata from comments`() {
        val url = "jdbc:h2:mem:cap4k-db-source-relation;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table video_post (
                        id bigint primary key comment 'pk',
                        author_id bigint not null comment '@Reference=user_profile;@Relation=ManyToOne;@Lazy=true;@Count=single;'
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    create table video_post_item (
                        id bigint primary key comment 'pk',
                        video_post_id bigint not null comment '@Reference=video_post;'
                    )
                    """.trimIndent()
                )
                statement.execute("comment on table video_post is 'Video post root @AggregateRoot=true;'")
                statement.execute("comment on table video_post_item is 'Video post item @Parent=video_post;@VO;'")
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
                            "includeTables" to listOf("video_post", "video_post_item"),
                            "excludeTables" to emptyList<String>(),
                        )
                    )
                ),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        ) as DbSchemaSnapshot

        val rootTable = snapshot.tables.first { it.tableName.equals("VIDEO_POST", true) }
        val childTable = snapshot.tables.first { it.tableName.equals("VIDEO_POST_ITEM", true) }
        val authorId = rootTable.columns.first { it.name.equals("AUTHOR_ID", true) }

        assertEquals(true, rootTable.aggregateRoot)
        assertEquals("video_post", childTable.parentTable)
        assertEquals(true, childTable.valueObject)
        assertEquals("Video post root", rootTable.comment)
        assertEquals("Video post item", childTable.comment)
        assertFalse(rootTable.comment.contains("@AggregateRoot"))
        assertFalse(childTable.comment.contains("@Parent"))
        assertEquals("user_profile", authorId.referenceTable)
        assertEquals("MANY_TO_ONE", authorId.explicitRelationType)
        assertEquals(true, authorId.lazy)
        assertEquals("single", authorId.countHint)
    }

    @Test
    fun `db source records parsed type binding and enum items from column comments`() {
        val url = "jdbc:h2:mem:cap4k-db-source-enum-comment;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table video_post (
                        id bigint primary key comment 'pk',
                        status int not null comment 'shared status @T=Status;',
                        visibility int not null comment '@T=VideoPostVisibility;@E=0:HIDDEN:Hidden|1:PUBLIC:Public;'
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
        val status = table.columns.first { it.name.equals("STATUS", true) }
        val visibility = table.columns.first { it.name.equals("VISIBILITY", true) }

        assertEquals("Status", status.typeBinding)
        assertEquals(emptyList<Any>(), status.enumItems)
        assertEquals("VideoPostVisibility", visibility.typeBinding)
        assertEquals(listOf("HIDDEN", "PUBLIC"), visibility.enumItems.map { it.name })
    }

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
        assertEquals(listOf("VIDEO_POST"), snapshot.discoveredTables)
        assertEquals(emptyList<String>(), snapshot.includedTables)
        assertEquals(emptyList<String>(), snapshot.excludedTables)
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

    @Test
    fun `db source records discovered included and excluded table diagnostics`() {
        val dbFile = Files.createTempDirectory("db-source-diagnostics").resolve("demo").toAbsolutePath().toString()
        DriverManager.getConnection(
            "jdbc:h2:file:$dbFile;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
            "sa",
            "secret",
        ).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("create table video_post (id bigint primary key, title varchar(128) not null)")
                statement.execute(
                    "create table audit_log (tenant_id bigint not null, event_id varchar(64) not null, primary key (tenant_id, event_id))"
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
                            "url" to "jdbc:h2:file:$dbFile;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
                            "username" to "sa",
                            "password" to "secret",
                            "schema" to "PUBLIC",
                            "includeTables" to listOf("video_post", "audit_log"),
                            "excludeTables" to listOf("audit_log"),
                        )
                    )
                ),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        ) as DbSchemaSnapshot

        assertEquals(listOf("audit_log", "video_post"), snapshot.discoveredTables)
        assertEquals(listOf("video_post"), snapshot.includedTables)
        assertEquals(listOf("audit_log"), snapshot.excludedTables)
    }
}
