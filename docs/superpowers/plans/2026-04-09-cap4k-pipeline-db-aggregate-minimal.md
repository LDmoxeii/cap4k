# Cap4k Pipeline DB Aggregate Minimal Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fixture-tested DB-driven aggregate path to the new pipeline that generates minimal `Schema`, `Entity`, and `Repository` artifacts from JDBC schema metadata.

**Architecture:** Add a dedicated JDBC source module that emits `DbSchemaSnapshot`, extend the canonical assembler with aggregate-oriented models and fixed naming rules, and add a single aggregate generator module that plans three artifact families. Keep Gradle integration thin by wiring the new source/generator behind explicit DB and module properties and prove the slice with H2-backed TestKit fixtures.

**Tech Stack:** Kotlin 2.2, Gradle Kotlin DSL, JUnit 5, H2 JDBC, Pebble, Gradle TestKit

---

## File Map

- Modify: `cap4k/settings.gradle.kts`
- Modify: `cap4k/gradle/libs.versions.toml`
- Modify: `cap4k/cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Create: `cap4k/cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
- Create: `cap4k/cap4k-plugin-pipeline-source-db/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/JdbcTypeMapper.kt`
- Create: `cap4k/cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Create: `cap4k/cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`
- Create: `cap4k/cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateNaming.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-aggregate/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/RepositoryArtifactPlanner.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Create: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb`
- Create: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Create: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/repository.kt.peb`
- Modify: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/build.gradle.kts`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelineExtension.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/settings.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/demo-domain/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/schema.sql`

### Task 1: Wire New Modules and Expand Pipeline API Models

**Files:**
- Modify: `cap4k/settings.gradle.kts`
- Modify: `cap4k/gradle/libs.versions.toml`
- Modify: `cap4k/cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Create: `cap4k/cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`

- [ ] **Step 1: Write the failing API model test**

```kotlin
package com.only4.cap4k.plugin.pipeline.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PipelineModelsTest {

    @Test
    fun `canonical model keeps aggregate slices`() {
        val schema = SchemaModel(
            name = "SVideoPost",
            packageName = "com.acme.demo.domain._share.meta.video_post",
            entityName = "VideoPost",
            comment = "Video post schema",
            fields = listOf(FieldModel(name = "id", type = "Long")),
        )
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "Video post",
            fields = listOf(FieldModel(name = "id", type = "Long")),
            idField = FieldModel(name = "id", type = "Long"),
        )
        val repository = RepositoryModel(
            name = "VideoPostRepository",
            packageName = "com.acme.demo.adapter.domain.repositories",
            entityName = "VideoPost",
            idType = "Long",
        )

        val model = CanonicalModel(
            schemas = listOf(schema),
            entities = listOf(entity),
            repositories = listOf(repository),
        )

        assertEquals(listOf(schema), model.schemas)
        assertEquals(listOf(entity), model.entities)
        assertEquals(listOf(repository), model.repositories)
    }

    @Test
    fun `db schema snapshot preserves normalized table metadata`() {
        val snapshot = DbSchemaSnapshot(
            tables = listOf(
                DbTableSnapshot(
                    tableName = "video_post",
                    comment = "Video posts",
                    columns = listOf(
                        DbColumnSnapshot(
                            name = "id",
                            dbType = "BIGINT",
                            kotlinType = "Long",
                            nullable = false,
                            defaultValue = null,
                            comment = "primary key",
                            isPrimaryKey = true,
                        )
                    ),
                    primaryKey = listOf("id"),
                    uniqueConstraints = listOf(listOf("id")),
                )
            )
        )

        assertEquals("video_post", snapshot.tables.single().tableName)
        assertEquals(listOf("id"), snapshot.tables.single().primaryKey)
        assertEquals("Long", snapshot.tables.single().columns.single().kotlinType)
    }
}
```

- [ ] **Step 2: Run the API model test and confirm it fails**

Run: `./gradlew :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest" --rerun-tasks`

Expected: compilation fails because `DbSchemaSnapshot`, `DbTableSnapshot`, `DbColumnSnapshot`, `SchemaModel`, `EntityModel`, and `RepositoryModel` do not exist yet.

- [ ] **Step 3: Add the new modules, H2 catalog entry, and aggregate-capable API models**

```kotlin
// cap4k/settings.gradle.kts
include(
    "cap4k-plugin-pipeline-api",
    "cap4k-plugin-pipeline-core",
    "cap4k-plugin-pipeline-renderer-api",
    "cap4k-plugin-pipeline-renderer-pebble",
    "cap4k-plugin-pipeline-source-design-json",
    "cap4k-plugin-pipeline-source-ksp-metadata",
    "cap4k-plugin-pipeline-source-db",
    "cap4k-plugin-pipeline-generator-design",
    "cap4k-plugin-pipeline-generator-aggregate",
    "cap4k-plugin-pipeline-gradle"
)

// cap4k/gradle/libs.versions.toml
[versions]
h2 = "2.3.232"

[libraries]
h2 = { module = "com.h2database:h2", version.ref = "h2" }

// cap4k/cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt
data class DbColumnSnapshot(
    val name: String,
    val dbType: String,
    val kotlinType: String,
    val nullable: Boolean,
    val defaultValue: String? = null,
    val comment: String = "",
    val isPrimaryKey: Boolean = false,
)

data class DbTableSnapshot(
    val tableName: String,
    val comment: String,
    val columns: List<DbColumnSnapshot>,
    val primaryKey: List<String>,
    val uniqueConstraints: List<List<String>>,
)

data class DbSchemaSnapshot(
    override val id: String = "db",
    val tables: List<DbTableSnapshot>,
) : SourceSnapshot

data class SchemaModel(
    val name: String,
    val packageName: String,
    val entityName: String,
    val comment: String,
    val fields: List<FieldModel>,
)

data class EntityModel(
    val name: String,
    val packageName: String,
    val tableName: String,
    val comment: String,
    val fields: List<FieldModel>,
    val idField: FieldModel,
)

data class RepositoryModel(
    val name: String,
    val packageName: String,
    val entityName: String,
    val idType: String,
)

data class CanonicalModel(
    val requests: List<RequestModel> = emptyList(),
    val schemas: List<SchemaModel> = emptyList(),
    val entities: List<EntityModel> = emptyList(),
    val repositories: List<RepositoryModel> = emptyList(),
)
```

- [ ] **Step 4: Run the API test suite**

Run: `./gradlew :cap4k-plugin-pipeline-api:test --rerun-tasks`

Expected: `BUILD SUCCESSFUL` and both `ProjectConfigTest` and `PipelineModelsTest` pass.

- [ ] **Step 5: Commit the API groundwork**

```bash
git add \
  settings.gradle.kts \
  gradle/libs.versions.toml \
  cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt \
  cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt
git commit -m "feat: add aggregate pipeline models"
```

### Task 2: Add the JDBC DB Source Module

**Files:**
- Create: `cap4k/cap4k-plugin-pipeline-source-db/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/JdbcTypeMapper.kt`
- Create: `cap4k/cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Create: `cap4k/cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`

- [ ] **Step 1: Write the failing DB source test against H2**

```kotlin
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
                    \"\"\"
                    create table video_post (
                        id bigint primary key,
                        slug varchar(128) not null unique,
                        title varchar(255) not null,
                        published boolean default false
                    )
                    \"\"\".trimIndent()
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
        assertEquals("video_post", table.tableName)
        assertEquals(listOf("id"), table.primaryKey)
        assertEquals(listOf(listOf("slug")), table.uniqueConstraints)
        assertEquals(listOf("id", "published", "slug", "title"), table.columns.map { it.name }.sorted())
        assertEquals("Long", table.columns.first { it.name == "id" }.kotlinType)
        assertEquals("Boolean", table.columns.first { it.name == "published" }.kotlinType)
    }
}
```

- [ ] **Step 2: Run the DB source test and confirm it fails**

Run: `./gradlew :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProviderTest" --rerun-tasks`

Expected: Gradle fails because the new module and source classes do not exist yet.

- [ ] **Step 3: Implement the DB source module**

```kotlin
// cap4k/cap4k-plugin-pipeline-source-db/build.gradle.kts
plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":cap4k-plugin-pipeline-api"))
    implementation(libs.h2)

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// cap4k/cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/JdbcTypeMapper.kt
package com.only4.cap4k.plugin.pipeline.source.db

import java.sql.Types

internal object JdbcTypeMapper {
    fun toKotlinType(sqlType: Int): String = when (sqlType) {
        Types.BIGINT -> "Long"
        Types.INTEGER, Types.SMALLINT, Types.TINYINT -> "Int"
        Types.BOOLEAN, Types.BIT -> "Boolean"
        Types.DECIMAL, Types.NUMERIC -> "java.math.BigDecimal"
        Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "java.time.LocalDateTime"
        Types.DATE -> "java.time.LocalDate"
        else -> "String"
    }
}

// cap4k/cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt
package com.only4.cap4k.plugin.pipeline.source.db

import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import java.sql.DriverManager
import java.util.Locale

class DbSchemaSourceProvider : SourceProvider {
    override val id: String = "db"

    override fun collect(config: ProjectConfig): DbSchemaSnapshot {
        val source = requireNotNull(config.sources[id]) { "Missing db source config" }
        val url = source.options["url"] as? String ?: error("db.url is required")
        val username = source.options["username"] as? String ?: ""
        val password = source.options["password"] as? String ?: ""
        val schema = (source.options["schema"] as? String ?: "").ifBlank { null }
        val includeTables = ((source.options["includeTables"] as? List<*>) ?: emptyList<Any>())
            .map { it.toString().lowercase(Locale.ROOT) }
            .toSet()
        val excludeTables = ((source.options["excludeTables"] as? List<*>) ?: emptyList<Any>())
            .map { it.toString().lowercase(Locale.ROOT) }
            .toSet()

        DriverManager.getConnection(url, username, password).use { connection ->
            val metadata = connection.metaData
            val tables = metadata.getTables(null, schema, "%", arrayOf("TABLE")).use { tableRows ->
                buildList {
                    while (tableRows.next()) {
                        val tableName = tableRows.getString("TABLE_NAME").lowercase(Locale.ROOT)
                        if (includeTables.isNotEmpty() && tableName !in includeTables) continue
                        if (tableName in excludeTables) continue
                        add(readTable(metadata, schema, tableName))
                    }
                }
            }

            return DbSchemaSnapshot(tables = tables.sortedBy { it.tableName })
        }
    }

    private fun readTable(metadata: java.sql.DatabaseMetaData, schema: String?, tableName: String): DbTableSnapshot {
        val primaryKey = metadata.getPrimaryKeys(null, schema, tableName.uppercase(Locale.ROOT)).use { rows ->
            buildList {
                while (rows.next()) {
                    add(rows.getString("COLUMN_NAME").lowercase(Locale.ROOT))
                }
            }
        }
        val columns = metadata.getColumns(null, schema, tableName.uppercase(Locale.ROOT), "%").use { rows ->
            buildList {
                while (rows.next()) {
                    val name = rows.getString("COLUMN_NAME").lowercase(Locale.ROOT)
                    add(
                        DbColumnSnapshot(
                            name = name,
                            dbType = rows.getString("TYPE_NAME"),
                            kotlinType = JdbcTypeMapper.toKotlinType(rows.getInt("DATA_TYPE")),
                            nullable = rows.getInt("NULLABLE") == java.sql.DatabaseMetaData.columnNullable,
                            defaultValue = rows.getString("COLUMN_DEF"),
                            comment = rows.getString("REMARKS") ?: "",
                            isPrimaryKey = name in primaryKey,
                        )
                    )
                }
            }
        }
        val uniqueConstraints = metadata.getIndexInfo(null, schema, tableName.uppercase(Locale.ROOT), true, false).use { rows ->
            buildMap<String, MutableList<String>> {
                while (rows.next()) {
                    val indexName = rows.getString("INDEX_NAME") ?: continue
                    if (indexName == "PRIMARY_KEY") continue
                    val columnName = rows.getString("COLUMN_NAME")?.lowercase(Locale.ROOT) ?: continue
                    getOrPut(indexName) { mutableListOf() }.add(columnName)
                }
            }.values.map { it.sorted() }.sortedBy { it.joinToString(",") }
        }

        return DbTableSnapshot(
            tableName = tableName,
            comment = "",
            columns = columns.sortedBy { it.name },
            primaryKey = primaryKey.sorted(),
            uniqueConstraints = uniqueConstraints,
        )
    }
}
```

- [ ] **Step 4: Run the DB source test**

Run: `./gradlew :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProviderTest" --rerun-tasks`

Expected: `BUILD SUCCESSFUL` and the source test proves normalized H2 metadata collection.

- [ ] **Step 5: Commit the DB source module**

```bash
git add \
  cap4k-plugin-pipeline-source-db/build.gradle.kts \
  cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/JdbcTypeMapper.kt \
  cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt \
  cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt
git commit -m "feat: add db schema source provider"
```

### Task 3: Extend the Canonical Assembler for Aggregate Models

**Files:**
- Create: `cap4k/cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateNaming.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Write the failing canonical assembler tests**

```kotlin
@Test
fun `maps db schema snapshot into schema entity and repository models`() {
    val assembler = DefaultCanonicalAssembler()

    val model = assembler.assemble(
        config = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = MULTI_MODULE,
            modules = mapOf(
                "domain" to "demo-domain",
                "adapter" to "demo-adapter",
            ),
            sources = emptyMap(),
            generators = emptyMap(),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        ),
        snapshots = listOf(
            DbSchemaSnapshot(
                tables = listOf(
                    DbTableSnapshot(
                        tableName = "video_post",
                        comment = "Video post",
                        columns = listOf(
                            DbColumnSnapshot("id", "BIGINT", "Long", false, null, "", true),
                            DbColumnSnapshot("title", "VARCHAR", "String", false, null, "", false),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = listOf(listOf("title")),
                    )
                )
            )
        ),
    )

    assertEquals("SVideoPost", model.schemas.single().name)
    assertEquals("com.acme.demo.domain._share.meta.video_post", model.schemas.single().packageName)
    assertEquals("VideoPost", model.entities.single().name)
    assertEquals("com.acme.demo.domain.aggregates.video_post", model.entities.single().packageName)
    assertEquals("VideoPostRepository", model.repositories.single().name)
    assertEquals("com.acme.demo.adapter.domain.repositories", model.repositories.single().packageName)
    assertEquals("Long", model.repositories.single().idType)
}

@Test
fun `fails fast when db table has no primary key`() {
    val assembler = DefaultCanonicalAssembler()

    val error = assertThrows<IllegalArgumentException> {
        assembler.assemble(
            config = baseAggregateConfig(),
            snapshots = listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "audit_log",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("event_id", "VARCHAR", "String", false, null, "", false),
                            ),
                            primaryKey = emptyList(),
                            uniqueConstraints = emptyList(),
                        )
                    )
                )
            ),
        )
    }

    assertEquals("db table audit_log must define a primary key", error.message)
}
```

- [ ] **Step 2: Run the canonical assembler tests and confirm they fail**

Run: `./gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest" --rerun-tasks`

Expected: compilation or assertion failures because the assembler does not populate `schemas`, `entities`, or `repositories`.

- [ ] **Step 3: Implement deterministic aggregate naming and canonical assembly**

```kotlin
// cap4k/cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateNaming.kt
package com.only4.cap4k.plugin.pipeline.core

internal object AggregateNaming {
    fun entityName(tableName: String): String =
        tableName.split("_")
            .filter { it.isNotBlank() }
            .joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }

    fun schemaName(tableName: String): String = "S${entityName(tableName)}"

    fun repositoryName(tableName: String): String = "${entityName(tableName)}Repository"

    fun tableSegment(tableName: String): String = tableName.lowercase()
}

// cap4k/cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt
val dbTables = snapshots
    .filterIsInstance<DbSchemaSnapshot>()
    .flatMap { it.tables }

val aggregateModels = dbTables.map { table ->
    require(table.primaryKey.isNotEmpty()) { "db table ${table.tableName} must define a primary key" }
    val entityName = AggregateNaming.entityName(table.tableName)
    val schemaName = AggregateNaming.schemaName(table.tableName)
    val repositoryName = AggregateNaming.repositoryName(table.tableName)
    val segment = AggregateNaming.tableSegment(table.tableName)
    val fields = table.columns.map { FieldModel(it.name, it.kotlinType, it.nullable, it.defaultValue) }
    val idField = fields.first { it.name == table.primaryKey.first() }

    Triple(
        SchemaModel(
            name = schemaName,
            packageName = "${config.basePackage}.domain._share.meta.$segment",
            entityName = entityName,
            comment = table.comment,
            fields = fields,
        ),
        EntityModel(
            name = entityName,
            packageName = "${config.basePackage}.domain.aggregates.$segment",
            tableName = table.tableName,
            comment = table.comment,
            fields = fields,
            idField = idField,
        ),
        RepositoryModel(
            name = repositoryName,
            packageName = "${config.basePackage}.adapter.domain.repositories",
            entityName = entityName,
            idType = idField.type,
        ),
    )
}

return CanonicalModel(
    requests = requests,
    schemas = aggregateModels.map { it.first },
    entities = aggregateModels.map { it.second },
    repositories = aggregateModels.map { it.third },
)
```

- [ ] **Step 4: Run the core tests**

Run: `./gradlew :cap4k-plugin-pipeline-core:test --rerun-tasks`

Expected: `BUILD SUCCESSFUL` and both the design-request tests and the new aggregate canonical tests pass.

- [ ] **Step 5: Commit the canonical model assembly**

```bash
git add \
  cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateNaming.kt \
  cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt \
  cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: assemble aggregate canonical models"
```

### Task 4: Add the Aggregate Artifact Planner Module

**Files:**
- Create: `cap4k/cap4k-plugin-pipeline-generator-aggregate/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/RepositoryArtifactPlanner.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

- [ ] **Step 1: Write the failing aggregate planner test**

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.RepositoryModel
import com.only4.cap4k.plugin.pipeline.api.SchemaModel
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AggregateArtifactPlannerTest {

    @Test
    fun `plans schema entity and repository artifacts into domain and adapter modules`() {
        val config = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = mapOf(
                "domain" to "demo-domain",
                "adapter" to "demo-adapter",
            ),
            sources = emptyMap(),
            generators = mapOf("aggregate" to GeneratorConfig(enabled = true)),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )

        val model = CanonicalModel(
            schemas = listOf(
                SchemaModel(
                    name = "SVideoPost",
                    packageName = "com.acme.demo.domain._share.meta.video_post",
                    entityName = "VideoPost",
                    comment = "",
                    fields = listOf(FieldModel("id", "Long")),
                )
            ),
            entities = listOf(
                EntityModel(
                    name = "VideoPost",
                    packageName = "com.acme.demo.domain.aggregates.video_post",
                    tableName = "video_post",
                    comment = "",
                    fields = listOf(FieldModel("id", "Long")),
                    idField = FieldModel("id", "Long"),
                )
            ),
            repositories = listOf(
                RepositoryModel(
                    name = "VideoPostRepository",
                    packageName = "com.acme.demo.adapter.domain.repositories",
                    entityName = "VideoPost",
                    idType = "Long",
                )
            ),
        )

        val planItems = AggregateArtifactPlanner().plan(config, model)

        assertEquals(3, planItems.size)
        assertEquals(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPost.kt",
            planItems.first { it.templateId == "aggregate/schema.kt.peb" }.outputPath,
        )
        assertEquals(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
            planItems.first { it.templateId == "aggregate/entity.kt.peb" }.outputPath,
        )
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/repositories/VideoPostRepository.kt",
            planItems.first { it.templateId == "aggregate/repository.kt.peb" }.outputPath,
        )
    }
}
```

- [ ] **Step 2: Run the aggregate planner test and confirm it fails**

Run: `./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --rerun-tasks`

Expected: Gradle fails because the new generator module and planners do not exist yet.

- [ ] **Step 3: Implement the aggregate planner module**

```kotlin
// cap4k/cap4k-plugin-pipeline-generator-aggregate/build.gradle.kts
plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":cap4k-plugin-pipeline-api"))

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt
package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class AggregateArtifactPlanner : GeneratorProvider {
    override val id: String = "aggregate"

    private val delegates = listOf(
        SchemaArtifactPlanner(),
        EntityArtifactPlanner(),
        RepositoryArtifactPlanner(),
    )

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> =
        delegates.flatMap { it.plan(config, model) }
}

// cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt
internal class SchemaArtifactPlanner {
    fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val domainRoot = requireRelativeModule(config, "domain")
        return model.schemas.map { schema ->
            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/schema.kt.peb",
                outputPath = "$domainRoot/src/main/kotlin/${schema.packageName.replace(".", "/")}/${schema.name}.kt",
                context = mapOf(
                    "packageName" to schema.packageName,
                    "typeName" to schema.name,
                    "entityName" to schema.entityName,
                    "fields" to schema.fields,
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}

// cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt
internal class EntityArtifactPlanner {
    fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val domainRoot = requireRelativeModule(config, "domain")
        return model.entities.map { entity ->
            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/entity.kt.peb",
                outputPath = "$domainRoot/src/main/kotlin/${entity.packageName.replace(".", "/")}/${entity.name}.kt",
                context = mapOf(
                    "packageName" to entity.packageName,
                    "typeName" to entity.name,
                    "tableName" to entity.tableName,
                    "fields" to entity.fields,
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}

// cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/RepositoryArtifactPlanner.kt
internal class RepositoryArtifactPlanner {
    fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val adapterRoot = requireRelativeModule(config, "adapter")
        return model.repositories.map { repository ->
            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "adapter",
                templateId = "aggregate/repository.kt.peb",
                outputPath = "$adapterRoot/src/main/kotlin/${repository.packageName.replace(".", "/")}/${repository.name}.kt",
                context = mapOf(
                    "packageName" to repository.packageName,
                    "typeName" to repository.name,
                    "entityName" to repository.entityName,
                    "idType" to repository.idType,
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}

private fun requireRelativeModule(config: ProjectConfig, role: String): String {
    val value = config.modules[role] ?: error("$role module is required")
    require(!value.startsWith(":")) { "$role module must be a repository-relative path: $value" }
    require(!java.nio.file.Path.of(value).isAbsolute) { "$role module must be a repository-relative path: $value" }
    return value
}
```

- [ ] **Step 4: Run the aggregate planner tests**

Run: `./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --rerun-tasks`

Expected: `BUILD SUCCESSFUL` and the planner test proves correct template IDs, module roles, and output paths.

- [ ] **Step 5: Commit the aggregate planner module**

```bash
git add \
  cap4k-plugin-pipeline-generator-aggregate/build.gradle.kts \
  cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt \
  cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt \
  cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt \
  cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/RepositoryArtifactPlanner.kt \
  cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: add aggregate artifact planners"
```

### Task 5: Add Aggregate Pebble Templates and Renderer Coverage

**Files:**
- Create: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb`
- Create: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Create: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/repository.kt.peb`
- Modify: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Write the failing aggregate renderer test**

```kotlin
@Test
fun `renders aggregate preset templates`() {
    val renderer = PebbleArtifactRenderer(
        templateResolver = PresetTemplateResolver(
            preset = "ddd-default",
            overrideDirs = emptyList()
        )
    )

    val rendered = renderer.render(
        planItems = listOf(
            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/entity.kt.peb",
                outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                context = mapOf(
                    "packageName" to "com.acme.demo.domain.aggregates.video_post",
                    "typeName" to "VideoPost",
                    "tableName" to "video_post",
                    "fields" to listOf(
                        mapOf("name" to "id", "type" to "Long", "nullable" to false),
                        mapOf("name" to "title", "type" to "String", "nullable" to false),
                    ),
                ),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "adapter",
                templateId = "aggregate/repository.kt.peb",
                outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/repositories/VideoPostRepository.kt",
                context = mapOf(
                    "packageName" to "com.acme.demo.adapter.domain.repositories",
                    "typeName" to "VideoPostRepository",
                    "entityName" to "VideoPost",
                    "idType" to "Long",
                ),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        ),
        config = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = emptyMap(),
            generators = emptyMap(),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
    )

    assertTrue(rendered.first().content.contains("data class VideoPost"))
    assertTrue(rendered.first().content.contains("val id: Long"))
    assertTrue(rendered.last().content.contains("interface VideoPostRepository"))
}
```

- [ ] **Step 2: Run the renderer test and confirm it fails**

Run: `./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --rerun-tasks`

Expected: renderer fails with `Template not found: presets/ddd-default/aggregate/...` because the new preset templates do not exist yet.

- [ ] **Step 3: Add the aggregate preset templates**

```twig
{# cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb #}
package {{ packageName }}

object {{ typeName }} {
{% for field in fields %}
    const val {{ field.name }} = "{{ field.name }}"
{% endfor %}
}

{# cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb #}
package {{ packageName }}

data class {{ typeName }}(
{% for field in fields %}
    val {{ field.name }}: {{ field.type }}{% if field.nullable %}?{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
)

{# cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/repository.kt.peb #}
package {{ packageName }}

interface {{ typeName }}
```

- [ ] **Step 4: Run the renderer suite**

Run: `./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --rerun-tasks`

Expected: `BUILD SUCCESSFUL` and the renderer suite now covers both the design templates and the new aggregate templates.

- [ ] **Step 5: Commit the renderer templates**

```bash
git add \
  cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb \
  cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb \
  cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/repository.kt.peb \
  cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: add aggregate pebble templates"
```

### Task 6: Wire Gradle Integration and Add an H2-backed TestKit Fixture

**Files:**
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/build.gradle.kts`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelineExtension.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/settings.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/demo-domain/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/schema.sql`

- [ ] **Step 1: Write the failing Gradle functional test**

```kotlin
@OptIn(ExperimentalPathApi::class)
@Test
fun `cap4kGenerate renders schema entity and repository files from db fixture`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate")
    copyFixture(projectDir, "aggregate-sample")

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kPlan", "cap4kGenerate")
        .build()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(
        projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPost.kt"
        ).toFile().exists()
    )
    assertTrue(
        projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
        ).toFile().exists()
    )
    assertTrue(
        projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/repositories/VideoPostRepository.kt"
        ).toFile().exists()
    )
    assertTrue(
        projectDir.resolve("build/cap4k/plan.json").readText().contains("\"templateId\": \"aggregate/entity.kt.peb\"")
    )
}
```

- [ ] **Step 2: Run the Gradle functional test and confirm it fails**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --rerun-tasks`

Expected: the new functional test fails because the Gradle extension and runner do not know about DB source options, `domain` and `adapter` module roots, or the aggregate generator.

- [ ] **Step 3: Extend the Gradle adapter and add the aggregate fixture**

```kotlin
// cap4k/cap4k-plugin-pipeline-gradle/build.gradle.kts
dependencies {
    implementation(project(":cap4k-plugin-pipeline-source-db"))
    implementation(project(":cap4k-plugin-pipeline-generator-aggregate"))
}

// cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelineExtension.kt
open class PipelineExtension @Inject constructor(objects: ObjectFactory) {
    val basePackage: Property<String> = objects.property(String::class.java)
    val applicationModulePath: Property<String> = objects.property(String::class.java)
    val domainModulePath: Property<String> = objects.property(String::class.java)
    val adapterModulePath: Property<String> = objects.property(String::class.java)
    val designFiles: ConfigurableFileCollection = objects.fileCollection()
    val kspMetadataDir: Property<String> = objects.property(String::class.java)
    val dbUrl: Property<String> = objects.property(String::class.java)
    val dbUsername: Property<String> = objects.property(String::class.java)
    val dbPassword: Property<String> = objects.property(String::class.java)
    val dbSchema: Property<String> = objects.property(String::class.java)
    val dbIncludeTables: ListProperty<String> = objects.listProperty(String::class.java)
    val dbExcludeTables: ListProperty<String> = objects.listProperty(String::class.java)
    val templateOverrideDir: Property<String> = objects.property(String::class.java)
}

// cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt
internal fun buildConfig(project: Project, extension: PipelineExtension): ProjectConfig {
    val modules = linkedMapOf<String, String>()
    val sources = linkedMapOf<String, SourceConfig>()
    val generators = linkedMapOf<String, GeneratorConfig>()

    extension.applicationModulePath.orNull?.let { modules["application"] = it }
    extension.domainModulePath.orNull?.let { modules["domain"] = it }
    extension.adapterModulePath.orNull?.let { modules["adapter"] = it }

    if (!extension.designFiles.isEmpty) {
        sources["design-json"] = SourceConfig(true, mapOf("files" to extension.designFiles.files.map { it.absolutePath }))
    }
    extension.kspMetadataDir.orNull?.let {
        sources["ksp-metadata"] = SourceConfig(true, mapOf("inputDir" to project.file(it).absolutePath))
    }
    if (extension.dbUrl.isPresent) {
        sources["db"] = SourceConfig(
            enabled = true,
            options = mapOf(
                "url" to extension.dbUrl.get(),
                "username" to extension.dbUsername.orNull.orEmpty(),
                "password" to extension.dbPassword.orNull.orEmpty(),
                "schema" to extension.dbSchema.orNull.orEmpty(),
                "includeTables" to extension.dbIncludeTables.getOrElse(emptyList()),
                "excludeTables" to extension.dbExcludeTables.getOrElse(emptyList()),
            )
        )
    }

    if ("design-json" in sources) generators["design"] = GeneratorConfig(enabled = true)
    if ("db" in sources && "domain" in modules && "adapter" in modules) {
        generators["aggregate"] = GeneratorConfig(enabled = true)
    }

    return ProjectConfig(
        basePackage = extension.basePackage.get(),
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = sources,
        generators = generators,
        templates = TemplateConfig(
            preset = "ddd-default",
            overrideDirs = listOf(project.file(extension.templateOverrideDir.get()).absolutePath),
            conflictPolicy = ConflictPolicy.SKIP,
        ),
    )
}

internal fun buildRunner(project: Project, config: ProjectConfig, exportEnabled: Boolean): PipelineRunner {
    return DefaultPipelineRunner(
        sources = listOf(
            DesignJsonSourceProvider(),
            KspMetadataSourceProvider(),
            DbSchemaSourceProvider(),
        ),
        generators = listOf(
            DesignArtifactPlanner(),
            AggregateArtifactPlanner(),
        ),
        assembler = DefaultCanonicalAssembler(),
        renderer = PebbleArtifactRenderer(
            PresetTemplateResolver(config.templates.preset, config.templates.overrideDirs)
        ),
        exporter = if (exportEnabled) FilesystemArtifactExporter(project.projectDir.toPath()) else NoopArtifactExporter(),
    )
}

// cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
private fun copyFixture(targetDir: Path, name: String = "design-sample") {
    val sourceDir = Path.of(
        requireNotNull(javaClass.getResource("/functional/$name")) {
            "Missing functional fixture directory: $name"
        }.toURI()
    )
    sourceDir.copyToRecursively(targetDir, followLinks = false)
}

// cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/build.gradle.kts
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

val schemaFile = file("schema.sql").invariantSeparatorsPath

cap4kPipeline {
    basePackage.set("com.acme.demo")
    domainModulePath.set("demo-domain")
    adapterModulePath.set("demo-adapter")
    dbUrl.set("jdbc:h2:mem:cap4k-functional;MODE=MySQL;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM '$schemaFile'")
    dbUsername.set("sa")
    dbPassword.set("")
    dbSchema.set("PUBLIC")
    dbIncludeTables.set(listOf("video_post"))
    dbExcludeTables.set(emptyList())
    templateOverrideDir.set("codegen/templates")
}

// cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/settings.gradle.kts
rootProject.name = "aggregate-sample"
include("demo-domain", "demo-adapter")

// cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/demo-domain/build.gradle.kts
// fixture module for generated domain sources

// cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/demo-adapter/build.gradle.kts
// fixture module for generated adapter sources

// cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/schema.sql
create table video_post (
    id bigint primary key,
    title varchar(255) not null,
    author_id bigint not null
);
```

- [ ] **Step 4: Run the full Gradle plugin test suite**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --rerun-tasks`

Expected: `BUILD SUCCESSFUL`; the existing design fixture still passes and the new aggregate fixture proves `cap4kPlan` plus `cap4kGenerate`.

- [ ] **Step 5: Commit the Gradle/TestKit wiring**

```bash
git add \
  cap4k-plugin-pipeline-gradle/build.gradle.kts \
  cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelineExtension.kt \
  cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt \
  cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt \
  cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/settings.gradle.kts \
  cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/build.gradle.kts \
  cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/demo-domain/build.gradle.kts \
  cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/demo-adapter/build.gradle.kts \
  cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/schema.sql
git commit -m "feat: wire aggregate pipeline into gradle plugin"
```

## Final Verification

- [ ] Run: `./gradlew :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-source-db:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test --rerun-tasks`
- [ ] Expected: `BUILD SUCCESSFUL`
- [ ] Run: `git status --short`
- [ ] Expected: clean working tree except for intentionally untracked planning docs
