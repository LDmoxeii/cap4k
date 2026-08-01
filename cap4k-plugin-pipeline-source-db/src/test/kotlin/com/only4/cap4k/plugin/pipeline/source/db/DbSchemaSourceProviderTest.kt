package com.only4.cap4k.plugin.pipeline.source.db

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import java.sql.DriverManager
import java.sql.Types
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DbSchemaSourceProviderTest {

    @Test
    fun `collect retains jdbc type name and column capacity`() {
        val url = "jdbc:h2:mem:cap4k-strong-id-metadata;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table strong_id_evidence (
                        uuid_text varchar(36) not null,
                        uuid_native uuid not null,
                        snowflake_text varchar(19) not null,
                        snowflake_long bigint not null,
                        primary key (uuid_text)
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
                        options = mapOf(
                            "url" to url,
                            "username" to "sa",
                            "password" to "",
                            "schema" to "PUBLIC",
                            "includeTables" to listOf("strong_id_evidence"),
                            "excludeTables" to emptyList<String>(),
                        )
                    )
                ),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        ) as DbSchemaSnapshot

        val columns = snapshot.tables.single().columns.associateBy { it.name.lowercase() }
        assertEquals(Types.CHAR == columns.getValue("uuid_text").jdbcType || Types.VARCHAR == columns.getValue("uuid_text").jdbcType, true)
        assertEquals(36, columns.getValue("uuid_text").columnSize)
        assertEquals("UUID", columns.getValue("uuid_native").dbType.uppercase())
        assertTrue(columns.getValue("uuid_native").jdbcType in setOf(Types.OTHER, Types.BINARY))
        assertEquals(19, columns.getValue("snowflake_text").columnSize)
        assertEquals(Types.BIGINT, columns.getValue("snowflake_long").jdbcType)
    }

    @Test
    fun `h2 standard metadata preserves soft delete defaults and physical names`() {
        val snapshot = collectSoftDeleteMetadata(
            "jdbc:h2:mem:cap4k-soft-delete-h2-standard;DB_CLOSE_DELAY=-1;" +
                "INIT=RUNSCRIPT FROM 'classpath:/soft-delete-h2-standard.sql'"
        )

        assertSoftDeleteMetadata(snapshot)
    }

    @Test
    fun `h2 mysql metadata preserves soft delete defaults and physical names`() {
        val snapshot = collectSoftDeleteMetadata(
            "jdbc:h2:mem:cap4k-soft-delete-h2-mysql;MODE=MySQL;DB_CLOSE_DELAY=-1;" +
                "INIT=RUNSCRIPT FROM 'classpath:/soft-delete-h2-mysql.sql'"
        )

        assertSoftDeleteMetadata(snapshot)
    }

    @Test
    fun `metadata scope treats mysql database name as catalog`() {
        val mysqlScope = resolveJdbcMetadataScope(
            url = "jdbc:mysql://127.0.0.1:3306/only_danmuku",
            configuredSchema = "only_danmuku",
            connectionCatalog = "ignored_catalog",
        )
        val mariadbScope = resolveJdbcMetadataScope(
            url = "jdbc:mariadb://127.0.0.1:3306/only_danmuku",
            configuredSchema = "only_danmuku",
            connectionCatalog = "ignored_catalog",
        )

        assertEquals("only_danmuku", mysqlScope.catalog)
        assertEquals(null, mysqlScope.schemaPattern)
        assertEquals("only_danmuku", mariadbScope.catalog)
        assertEquals(null, mariadbScope.schemaPattern)
    }

    @Test
    fun `metadata scope keeps schema pattern for schema based databases`() {
        val h2Scope = resolveJdbcMetadataScope(
            url = "jdbc:h2:mem:cap4k-db-source-scope;MODE=MySQL",
            configuredSchema = "PUBLIC",
            connectionCatalog = "CAP4K",
        )
        val postgresScope = resolveJdbcMetadataScope(
            url = "jdbc:postgresql://127.0.0.1:5432/only_danmuku",
            configuredSchema = "public",
            connectionCatalog = "only_danmuku",
        )

        assertEquals(null, h2Scope.catalog)
        assertEquals("PUBLIC", h2Scope.schemaPattern)
        assertEquals(null, postgresScope.catalog)
        assertEquals("public", postgresScope.schemaPattern)
    }

    @Test
    fun `db source omits tables marked ignored from selected schema snapshot`() {
        val url = "jdbc:h2:mem:cap4k-db-source-ignored-table;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table video_post (
                        id bigint primary key,
                        title varchar(128) not null
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    create table event_record (
                        id bigint primary key,
                        payload varchar(128) not null
                    )
                    """.trimIndent()
                )
                statement.execute("comment on table video_post is 'Video post root'")
                statement.execute("comment on table event_record is 'Framework event table @Ignore;'")
            }
        }

        val snapshot = DbSchemaSourceProvider().collect(
            ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = mapOf(
                    "db" to SourceConfig(
                        options = mapOf(
                            "url" to url,
                            "username" to "sa",
                            "password" to "",
                            "schema" to "PUBLIC",
                            "includeTables" to listOf("video_post", "event_record"),
                            "excludeTables" to emptyList<String>(),
                        )
                    )
                ),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        ) as DbSchemaSnapshot

        assertEquals(listOf("VIDEO_POST"), snapshot.tables.map { it.tableName })
        assertEquals(listOf("VIDEO_POST"), snapshot.includedTables)
        assertEquals(listOf("EVENT_RECORD", "VIDEO_POST"), snapshot.discoveredTables)
    }

    @Test
    fun `collect maps parent ref and managed roles into db snapshots`() {
        val url = "jdbc:h2:mem:cap4k-db-source-relation;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table video_post (
                        id bigint primary key comment 'pk',
                        title varchar(128) not null
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    create table video_post_item (
                        id bigint primary key comment 'pk',
                        video_post_id bigint not null comment 'owning parent @ParentRef;',
                        tenant_id bigint not null comment 'tenant scope @Managed=scope.tenant;',
                        deleted int not null comment 'soft marker @Managed=soft-delete;'
                    )
                    """.trimIndent()
                )
                statement.execute("comment on table video_post is 'Video post root'")
                statement.execute("comment on table video_post_item is 'Video post item @Parent=video_post;'")
            }
        }

        val snapshot = DbSchemaSourceProvider().collect(
            ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = mapOf(
                    "db" to SourceConfig(
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

        assertEquals(true, rootTable.aggregateRoot)
        assertEquals("video_post", childTable.parentTable)
        assertFalse(childTable.aggregateRoot)
        assertEquals("Video post root", rootTable.comment)
        assertEquals("Video post item", childTable.comment)
        assertFalse(childTable.comment.contains("@Parent"))
        assertEquals(1, childTable.columns.count { it.parentRef })
        assertEquals(true, childTable.columns.single { it.name.equals("VIDEO_POST_ID", true) }.parentRef)
        assertEquals("scope.tenant", childTable.columns.single { it.name.equals("TENANT_ID", true) }.managedPolicyKey)
        assertEquals("soft-delete", childTable.columns.single { it.name.equals("DELETED", true) }.managedPolicyKey)
        assertEquals("owning parent", childTable.columns.single { it.name.equals("VIDEO_POST_ID", true) }.comment)
    }

    @Test
    fun `provider rejects parent ref without parent table binding`() {
        val url = "jdbc:h2:mem:cap4k-db-source-parent-ref-without-parent;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table video_post_item (
                        id bigint primary key,
                        video_post_id bigint not null comment '@ParentRef;'
                    )
                    """.trimIndent()
                )
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            DbSchemaSourceProvider().collect(
                ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    sources = mapOf(
                        "db" to SourceConfig(
                            options = mapOf(
                                "url" to url,
                                "username" to "sa",
                                "password" to "",
                                "schema" to "PUBLIC",
                                "includeTables" to listOf("video_post_item"),
                                "excludeTables" to emptyList<String>(),
                            )
                        )
                    ),
                    generators = emptyMap(),
                    templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
                )
            )
        }

        assertEquals("@ParentRef is valid only on child tables with @Parent", error.message)
    }

    @Test
    fun `provider rejects duplicate parent refs on child table`() {
        val url = "jdbc:h2:mem:cap4k-db-source-duplicate-parent-refs;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table video_post (
                        id bigint primary key
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    create table video_post_item (
                        id bigint primary key,
                        video_post_id bigint not null comment '@ParentRef;',
                        backup_video_post_id bigint not null comment '@ParentRef;'
                    )
                    """.trimIndent()
                )
                statement.execute("comment on table video_post_item is '@Parent=video_post;'")
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            DbSchemaSourceProvider().collect(
                ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    sources = mapOf(
                        "db" to SourceConfig(
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
            )
        }

        assertEquals(
            "table VIDEO_POST_ITEM declares @Parent=video_post but must declare exactly one @ParentRef column.",
            error.message
        )
    }

    @Test
    fun `provider preserves identifier policy on non primary key for canonical validation`() {
        val url = "jdbc:h2:mem:cap4k-db-source-db-identity-non-pk;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table video_post (
                        id bigint primary key,
                        external_id bigint not null comment '@Managed=identifier.database-identity;'
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

        assertEquals(
            "identifier.database-identity",
            snapshot.tables.single().columns.single { it.name.equals("EXTERNAL_ID", true) }.managedPolicyKey,
        )
    }

    @Test
    fun `db source records ref aggregate and ref id column metadata from separate comments`() {
        val url = "jdbc:h2:mem:cap4k-db-source-strong-id-column;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table media_file (
                        id bigint primary key comment 'pk',
                        task_id bigint not null comment 'Task strong id @RefAggregate=MediaProcessingTask;',
                        owner_id bigint not null comment 'Owner strong id @RefId=UserId;'
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
                        options = mapOf(
                            "url" to url,
                            "username" to "sa",
                            "password" to "",
                            "schema" to "PUBLIC",
                            "includeTables" to listOf("media_file"),
                            "excludeTables" to emptyList<String>(),
                        )
                    )
                ),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        ) as DbSchemaSnapshot
        val taskId = snapshot.tables.single().columns.single { it.name.equals("TASK_ID", true) }
        val ownerId = snapshot.tables.single().columns.single { it.name.equals("OWNER_ID", true) }

        assertEquals("MediaProcessingTask", taskId.refAggregate)
        assertNull(taskId.refId)
        assertEquals("Task strong id", taskId.comment)
        assertNull(ownerId.refAggregate)
        assertEquals("UserId", ownerId.refId)
        assertEquals("Owner strong id", ownerId.comment)
    }

    @Test
    fun `db source records ref aggregate strong id column metadata from comments`() {
        val url = "jdbc:h2:mem:cap4k-db-source-ref-aggregate-column;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table media_file (
                        id bigint primary key comment 'pk',
                        task_id bigint not null comment 'Task strong id @RefAggregate=MediaProcessingTask;'
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
                        options = mapOf(
                            "url" to url,
                            "username" to "sa",
                            "password" to "",
                            "schema" to "PUBLIC",
                            "includeTables" to listOf("media_file"),
                            "excludeTables" to emptyList<String>(),
                        )
                    )
                ),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        ) as DbSchemaSnapshot

        val taskId = snapshot.tables.single().columns.single { it.name.equals("TASK_ID", true) }

        assertEquals("MediaProcessingTask", taskId.refAggregate)
        assertEquals(null, taskId.refId)
        assertEquals("Task strong id", taskId.comment)
        assertFalse(taskId.comment.contains("@RefAggregate"))
        assertFalse(taskId.comment.contains("@RefId"))
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
                        status int not null comment 'shared status @Type=Status;',
                        visibility int not null comment '@Type=VideoPostVisibility;'
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
        assertEquals(emptyList<Any>(), visibility.enumItems)
    }

    @Test
    fun `provider carries exact managed policy keys into db snapshot`() {
        val url = "jdbc:h2:mem:cap4k-db-source-persistence-field-behavior;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table video_post (
                        id bigint primary key comment '@Managed=identifier.database-identity;',
                        version bigint not null comment '@Managed=version;',
                        deleted int not null comment '@Managed=soft-delete;',
                        title varchar(128) not null
                    );
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

        val table = snapshot.tables.single { it.tableName.equals("VIDEO_POST", true) }

        assertEquals("identifier.database-identity", table.columns.single { it.name.equals("ID", true) }.managedPolicyKey)
        assertEquals("version", table.columns.single { it.name.equals("VERSION", true) }.managedPolicyKey)
        assertEquals("soft-delete", table.columns.single { it.name.equals("DELETED", true) }.managedPolicyKey)
    }

    @Test
    fun `provider carries custom managed policy key into db snapshot`() {
        val url = "jdbc:h2:mem:cap4k-db-source-managed-exposed-column-behavior;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table video_post (
                        id bigint primary key,
                        created_at timestamp not null comment '@Managed=custom.created-at;',
                        title varchar(128) not null
                    );
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

        val table = snapshot.tables.single { it.tableName.equals("VIDEO_POST", true) }

        assertEquals("custom.created-at", table.columns.single { it.name.equals("CREATED_AT", true) }.managedPolicyKey)
        assertEquals(null, table.columns.single { it.name.equals("TITLE", true) }.managedPolicyKey)
    }

    @Test
    fun `provider carries audit policy without declaration placement metadata`() {
        val url = "jdbc:h2:mem:cap4k-db-source-inherited-column;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table content (
                        id varchar(36) primary key,
                        title varchar(100) not null,
                        created_at timestamp not null comment '@Managed=enrichment.audit-time.created-at;'
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
                        options = mapOf(
                            "url" to url,
                            "username" to "sa",
                            "password" to "",
                            "schema" to "PUBLIC",
                        )
                    )
                ),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        ) as DbSchemaSnapshot

        val createdAt = snapshot.tables.single().columns.single { it.name.equals("CREATED_AT", true) }
        assertEquals("enrichment.audit-time.created-at", createdAt.managedPolicyKey)
    }

    @Test
    fun `provider rejects removed exposed annotation`() {
        val url = "jdbc:h2:mem:cap4k-db-source-exposed-marker-value;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table video_post (
                        id bigint primary key,
                        title varchar(128) not null comment '@Exposed=1;'
                    );
                    """.trimIndent()
                )
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            DbSchemaSourceProvider().collect(
                ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    sources = mapOf(
                        "db" to SourceConfig(
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
            )
        }

        assertEquals(
            "unsupported column annotation @Exposed. Supported column annotations: @ParentRef, @Type, @RefAggregate, @RefId, @Managed=<policy-key>.",
            error.message,
        )
    }

    @Test
    fun `provider rejects removed exposed annotation before managed exposed mutual exclusion`() {
        val url = "jdbc:h2:mem:cap4k-db-source-managed-exposed-conflict;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table video_post (
                        id bigint primary key,
                        title varchar(128) not null comment '@Managed=custom.system;@Exposed;'
                    );
                    """.trimIndent()
                )
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            DbSchemaSourceProvider().collect(
                ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    sources = mapOf(
                        "db" to SourceConfig(
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
            )
        }

        assertEquals(
            "unsupported column annotation @Exposed. Supported column annotations: @ParentRef, @Type, @RefAggregate, @RefId, @Managed=<policy-key>.",
            error.message,
        )
    }

    @Test
    fun `db source rejects unsupported table soft delete annotation`() {
        val url = "jdbc:h2:mem:cap4k-db-source-legacy-soft-delete-table;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table video_post (
                        id bigint primary key,
                        deleted int not null
                    );
                    """.trimIndent()
                )
                statement.execute(
                    "comment on table video_post is '@SoftDeleteColumn=deleted;'"
                )
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            DbSchemaSourceProvider().collect(
                ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    sources = mapOf(
                        "db" to SourceConfig(
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
            )
        }

        assertEquals(
            "unsupported table annotation @SoftDeleteColumn. Supported table annotations: @Parent=<table>, @Ignore.",
            error.message,
        )
    }

    @Test
    fun `db source rejects unsupported table id generator annotation`() {
        val url = "jdbc:h2:mem:cap4k-db-source-entity-id-generator;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table video_post (
                        id bigint primary key,
                        title varchar(128) not null
                    );
                    """.trimIndent()
                )
                statement.execute(
                    "comment on table video_post is 'Video post root @IdGenerator=snowflakeIdGenerator;'"
                )
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            DbSchemaSourceProvider().collect(
                ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    sources = mapOf(
                        "db" to SourceConfig(
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
            )
        }

        assertEquals(
            "unsupported table annotation @IdGenerator. Supported table annotations: @Parent=<table>, @Ignore.",
            error.message,
        )
    }

    @Test
    fun `provider rejects removed version marker annotation`() {
        val url = "jdbc:h2:mem:cap4k-db-source-persistence-field-version-false;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table video_post (
                        id bigint primary key,
                        version bigint not null comment '@Version=false;'
                    );
                    """.trimIndent()
                )
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            DbSchemaSourceProvider().collect(
                ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    sources = mapOf(
                        "db" to SourceConfig(
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
            )
        }

        assertEquals(
            "unsupported column annotation @Version. Supported column annotations: @ParentRef, @Type, @RefAggregate, @RefId, @Managed=<policy-key>.",
            error.message,
        )
    }

    @Test
    fun `provider keeps managed role null when source is silent`() {
        val url = "jdbc:h2:mem:cap4k-db-source-persistence-field-version-null;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table video_post (
                        id bigint primary key,
                        version bigint not null comment 'plain version comment'
                    );
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

        val column = snapshot.tables.single().columns.single { it.name.equals("VERSION", true) }

        assertEquals(null, column.managedPolicyKey)
    }

    @Test
    fun `db source fails fast on malformed relation column comments`() {
        val url = "jdbc:h2:mem:cap4k-db-source-invalid-relation;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table video_post (
                        id bigint primary key comment 'pk',
                        author_id bigint not null comment '@Lazy=true;'
                    )
                    """.trimIndent()
                )
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            DbSchemaSourceProvider().collect(
                ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    sources = mapOf(
                        "db" to SourceConfig(
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
            )
        }

        assertEquals(
            "unsupported column annotation @Lazy. Supported column annotations: @ParentRef, @Type, @RefAggregate, @RefId, @Managed=<policy-key>.",
            error.message,
        )
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
                        slug varchar(128) not null,
                        title varchar(255) not null,
                        published boolean default false,
                        constraint video_post_uk_v_slug unique (slug)
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
        val unique = table.uniqueConstraints.single()
        assertTrue(unique.physicalName.startsWith("VIDEO_POST_UK_V_SLUG", ignoreCase = true))
        assertTrue(unique.physicalName.contains("_INDEX_", ignoreCase = true))
        assertEquals(listOf("SLUG"), unique.columns)
        assertEquals(listOf("ID", "SLUG", "TITLE", "PUBLISHED"), table.columns.map { it.name })
        assertEquals("Long", table.columns.first { it.name == "ID" }.kotlinType)
        assertEquals("Boolean", table.columns.first { it.name == "PUBLISHED" }.kotlinType)
    }

    @Test
    fun `db source maps native uuid columns to UUID`() {
        val url = "jdbc:h2:mem:cap4k-db-source-native-uuid;MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table video_post (
                        id uuid primary key,
                        title varchar(255) not null
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

        val id = snapshot.tables.single().columns.single { it.name.equals("ID", true) }

        assertTrue(id.dbType.equals("UUID", ignoreCase = true))
        assertEquals("UUID", id.kotlinType)
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
                        "Slug" varchar(128) not null,
                        "Title" varchar(255) not null,
                        "Published" boolean default false,
                        constraint "VideoPost_uk_v_slug" unique ("Slug")
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
        val unique = table.uniqueConstraints.single()
        assertTrue(unique.physicalName.startsWith("VideoPost_uk_v_slug"))
        assertTrue(unique.physicalName.contains("_INDEX_"))
        assertEquals(listOf("Slug"), unique.columns)
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
        val unique = table.uniqueConstraints.single()
        assertTrue(unique.physicalName.startsWith("UQ_ACCOUNT_ENTRY", ignoreCase = true))
        assertTrue(unique.physicalName.contains("_INDEX_", ignoreCase = true))
        assertEquals(listOf("REGION_ID", "CODE"), unique.columns)
        assertTrue(unique.complete)
        assertNull(unique.filterCondition)
    }

    @Test
    fun `unique index rows preserve filters and mark null terms incomplete`() {
        val constraints = uniqueConstraintsFromIndexRows(
            rows = listOf(
                UniqueIndexMetadataRow(
                    indexName = "uk_video_post_parent_active",
                    columnName = "VIDEO_POST_ID",
                    ordinalPosition = 1,
                    metadataSequence = 0,
                    filterCondition = " deleted = 0 ",
                ),
                UniqueIndexMetadataRow(
                    indexName = "uk_video_post_parent_active",
                    columnName = null,
                    ordinalPosition = 2,
                    metadataSequence = 1,
                    filterCondition = " deleted = 0 ",
                ),
            ),
            primaryKey = setOf("id"),
            physicalColumns = setOf("id", "video_post_id", "deleted"),
        )

        val unique = constraints.single()
        assertEquals("uk_video_post_parent_active", unique.physicalName)
        assertEquals(listOf("video_post_id"), unique.columns)
        assertFalse(unique.complete)
        assertEquals("deleted = 0", unique.filterCondition)
    }

    @Test
    fun `unique index rows mark non physical expression terms incomplete`() {
        val constraints = uniqueConstraintsFromIndexRows(
            rows = listOf(
                UniqueIndexMetadataRow(
                    indexName = "uk_video_post_slug_expression",
                    columnName = "video_post_id",
                    ordinalPosition = 1,
                    metadataSequence = 0,
                    filterCondition = null,
                ),
                UniqueIndexMetadataRow(
                    indexName = "uk_video_post_slug_expression",
                    columnName = "lower(slug)",
                    ordinalPosition = 2,
                    metadataSequence = 1,
                    filterCondition = null,
                ),
            ),
            primaryKey = setOf("id"),
            physicalColumns = setOf("id", "video_post_id", "slug"),
        )

        val unique = constraints.single()
        assertEquals("uk_video_post_slug_expression", unique.physicalName)
        assertEquals(listOf("video_post_id"), unique.columns)
        assertFalse(unique.complete)
        assertNull(unique.filterCondition)
    }

    @Test
    fun `unique index rows mark ordinal gaps incomplete`() {
        val constraints = uniqueConstraintsFromIndexRows(
            rows = listOf(
                UniqueIndexMetadataRow("uk_video_post_parent", "video_post_id", 1, 0, null),
                UniqueIndexMetadataRow("uk_video_post_parent", "slug", 3, 1, null),
            ),
            primaryKey = setOf("id"),
            physicalColumns = setOf("id", "video_post_id", "slug"),
        )

        assertFalse(constraints.single().complete)
    }

    @Test
    fun `unique index rows mark duplicate ordinals incomplete`() {
        val constraints = uniqueConstraintsFromIndexRows(
            rows = listOf(
                UniqueIndexMetadataRow("uk_video_post_parent", "video_post_id", 1, 0, null),
                UniqueIndexMetadataRow("uk_video_post_parent", "slug", 1, 1, null),
            ),
            primaryKey = setOf("id"),
            physicalColumns = setOf("id", "video_post_id", "slug"),
        )

        assertFalse(constraints.single().complete)
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

    private fun collectSoftDeleteMetadata(url: String): DbSchemaSnapshot =
        DbSchemaSourceProvider().collect(
            ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = mapOf(
                    "db" to SourceConfig(
                        options = mapOf(
                            "url" to url,
                            "username" to "sa",
                            "password" to "",
                            "schema" to "PUBLIC",
                            "includeTables" to listOf("soft_delete_evidence", "MixedCaseEvidence"),
                            "excludeTables" to emptyList<String>(),
                        )
                    )
                ),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        ) as DbSchemaSnapshot

    private fun assertSoftDeleteMetadata(snapshot: DbSchemaSnapshot) {
        val evidence = actualDefaultWrappers(snapshot)
        assertEquals(
            listOf("MixedCaseEvidence", "SOFT_DELETE_EVIDENCE"),
            snapshot.tables.map { it.tableName },
            evidence,
        )

        val unquoted = snapshot.tables.single { it.tableName == "SOFT_DELETE_EVIDENCE" }
        assertEquals(
            listOf("BIGINT_DEFAULT", "VARCHAR_DEFAULT", "UUID_DEFAULT"),
            unquoted.columns.map { it.name },
            evidence,
        )
        assertSoftDeleteColumn(
            column = unquoted.columns.single { it.name == "BIGINT_DEFAULT" },
            expectedName = "BIGINT_DEFAULT",
            expectedDbType = "BIGINT",
            expectedJdbcType = Types.BIGINT,
            expectedKotlinType = "Long",
            expectedColumnSize = 64,
            expectedNullable = false,
            expectedDefaultValue = "0",
            evidence = evidence,
        )
        assertSoftDeleteColumn(
            column = unquoted.columns.single { it.name == "VARCHAR_DEFAULT" },
            expectedName = "VARCHAR_DEFAULT",
            expectedDbType = "CHARACTER VARYING",
            expectedJdbcType = Types.VARCHAR,
            expectedKotlinType = "String",
            expectedColumnSize = 36,
            expectedNullable = false,
            expectedDefaultValue = "'00000000-0000-0000-0000-000000000000'",
            evidence = evidence,
        )
        assertSoftDeleteColumn(
            column = unquoted.columns.single { it.name == "UUID_DEFAULT" },
            expectedName = "UUID_DEFAULT",
            expectedDbType = "UUID",
            expectedJdbcType = Types.BINARY,
            expectedKotlinType = "UUID",
            expectedColumnSize = 16,
            expectedNullable = false,
            expectedDefaultValue = "'00000000-0000-0000-0000-000000000000'",
            evidence = evidence,
        )

        val quoted = snapshot.tables.single { it.tableName == "MixedCaseEvidence" }
        assertSoftDeleteColumn(
            column = quoted.columns.single(),
            expectedName = "MixedCaseDefault",
            expectedDbType = "BIGINT",
            expectedJdbcType = Types.BIGINT,
            expectedKotlinType = "Long",
            expectedColumnSize = 64,
            expectedNullable = false,
            expectedDefaultValue = "0",
            evidence = evidence,
        )
    }

    private fun assertSoftDeleteColumn(
        column: DbColumnSnapshot,
        expectedName: String,
        expectedDbType: String,
        expectedJdbcType: Int,
        expectedKotlinType: String,
        expectedColumnSize: Int,
        expectedNullable: Boolean,
        expectedDefaultValue: String,
        evidence: String,
    ) {
        assertEquals(expectedName, column.name, evidence)
        assertEquals(expectedDbType, column.dbType, evidence)
        assertEquals(expectedJdbcType, column.jdbcType, evidence)
        assertEquals(expectedKotlinType, column.kotlinType, evidence)
        assertEquals(expectedColumnSize, column.columnSize, evidence)
        assertEquals(expectedNullable, column.nullable, evidence)
        assertEquals(expectedDefaultValue, column.defaultValue, evidence)
    }

    private fun actualDefaultWrappers(snapshot: DbSchemaSnapshot): String =
        snapshot.tables.joinToString(prefix = "actual default wrappers: ", separator = "; ") { table ->
            table.columns.joinToString(prefix = "${table.tableName}[", postfix = "]") { column ->
                "${column.name}=${column.defaultValue ?: "<null>"}"
            }
        }
}
