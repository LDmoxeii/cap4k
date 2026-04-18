# Cap4k Aggregate Relation Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bounded aggregate relation parity by carrying relation annotations through the existing `db` source, assembling a minimal aggregate relation model before planning, and rendering representative relation fields and annotations through `aggregate/entity.kt.peb`.

**Architecture:** This slice stays on the current aggregate line. The `db` source carries raw `@Parent`, `@Reference`, `@Relation`, and `@Lazy` metadata; the canonical assembler converts that into bounded `aggregateRelations`; the existing `aggregate` generator consumes that model only through `EntityArtifactPlanner`. Verification stays layered: parser/source, assembler, planner/renderer, then functional and compile fixtures.

**Tech Stack:** Kotlin, JUnit 5, H2 metadata fixtures, Gradle TestKit, Pebble preset rendering, existing aggregate compile harness

---

## File Structure

### New files

- Create: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParser.kt`
- Create: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParserTest.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/schema.sql`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/schema.sql`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateRelationCompileSmoke.kt`

### Existing files to modify

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

### Responsibilities

- `PipelineModels.kt`
  - carry bounded relation annotation metadata on DB snapshots
  - introduce aggregate relation canonical types without polluting generic `FieldModel`

- `DbRelationAnnotationParser.kt`
  - normalize old table/column annotation aliases into bounded source metadata
  - reject malformed or unsupported first-slice relation forms

- `DbSchemaSourceProvider.kt`
  - attach parser results to `DbTableSnapshot` and `DbColumnSnapshot`

- `AggregateRelationInference.kt`
  - convert raw source carriage into canonical `AggregateRelationModel`
  - apply bounded inference rules before planning

- `DefaultCanonicalAssembler.kt`
  - expose `aggregateRelations` and minimal entity-level aggregate metadata

- `AggregateRelationPlanning.kt`
  - map canonical relation models into deterministic planner/render inputs

- `EntityArtifactPlanner.kt`
  - split scalar fields from relation fields in template context
  - keep relation rendering isolated to the entity artifact family

- `aggregate/entity.kt.peb`
  - render scalar properties and relation properties separately
  - emit bounded JPA relation annotations for supported first-slice relations

- aggregate relation fixtures
  - prove source parsing, planning, generation, and compile viability for parent-child, explicit `ManyToOne`, explicit `OneToOne`, and bounded `@Lazy`

## Task 1: Add Relation Annotation Carriage to the Existing DB Source

**Files:**
- Create: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParser.kt`
- Create: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParserTest.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`

- [ ] **Step 1: Write the failing parser tests for bounded relation annotations**

Add `DbRelationAnnotationParserTest.kt` with:

```kotlin
package com.only4.cap4k.plugin.pipeline.source.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DbRelationAnnotationParserTest {

    @Test
    fun `parses table parent and value object annotations`() {
        val metadata = DbRelationAnnotationParser().parseTable("@Parent=video_post;@VO;")

        assertEquals("video_post", metadata.parentTable)
        assertEquals(false, metadata.aggregateRoot)
        assertEquals(true, metadata.valueObject)
    }

    @Test
    fun `parses column reference relation and lazy annotations`() {
        val metadata = DbRelationAnnotationParser().parseColumn("@Reference=user_profile;@Relation=OneToOne;@Lazy=true;")

        assertEquals("user_profile", metadata.referenceTable)
        assertEquals("ONE_TO_ONE", metadata.explicitRelationType)
        assertEquals(true, metadata.lazy)
    }

    @Test
    fun `rejects many to many in the first relation slice`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@Reference=tag;@Relation=ManyToMany;")
        }

        assertEquals("unsupported relation type in first slice: ManyToMany", error.message)
    }
}
```

- [ ] **Step 2: Write the failing DB source tests that surface normalized relation carriage**

Add to `DbSchemaSourceProviderTest.kt`:

```kotlin
@Test
fun `db source records table and column relation metadata from comments`() {
    val url = "jdbc:h2:mem:cap4k-db-source-relation;MODE=MySQL;DB_CLOSE_DELAY=-1"
    DriverManager.getConnection(url, "sa", "").use { connection ->
        connection.createStatement().use { statement ->
            statement.execute(
                """
                create table video_post (
                    id bigint primary key comment 'pk',
                    author_id bigint not null comment '@Reference=user_profile;@Relation=ManyToOne;@Lazy=true;'
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
            statement.execute("comment on table video_post is '@AggregateRoot=true;'")
            statement.execute("comment on table video_post_item is '@Parent=video_post;@VO;'")
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
    assertEquals("user_profile", authorId.referenceTable)
    assertEquals("MANY_TO_ONE", authorId.explicitRelationType)
    assertEquals(true, authorId.lazy)
}
```

- [ ] **Step 3: Implement the minimal bounded source carriage**

Update `PipelineModels.kt` and `DbRelationAnnotationParser.kt` along these lines:

```kotlin
data class DbColumnSnapshot(
    val name: String,
    val dbType: String,
    val kotlinType: String,
    val nullable: Boolean,
    val defaultValue: String? = null,
    val comment: String = "",
    val isPrimaryKey: Boolean = false,
    val typeBinding: String? = null,
    val enumItems: List<EnumItemModel> = emptyList(),
    val referenceTable: String? = null,
    val explicitRelationType: String? = null,
    val lazy: Boolean? = null,
    val countHint: String? = null,
)

data class DbTableSnapshot(
    val tableName: String,
    val comment: String,
    val columns: List<DbColumnSnapshot>,
    val primaryKey: List<String>,
    val uniqueConstraints: List<List<String>>,
    val parentTable: String? = null,
    val aggregateRoot: Boolean = true,
    val valueObject: Boolean = false,
)
```

```kotlin
internal class DbRelationAnnotationParser {
    fun parseTable(comment: String): TableRelationMetadata {
        val annotations = parseAnnotations(comment)
        return TableRelationMetadata(
            parentTable = annotations["Parent"] ?: annotations["P"],
            aggregateRoot = (annotations["AggregateRoot"] ?: annotations["Root"] ?: annotations["R"])?.toBooleanStrictOrNull() ?: true,
            valueObject = annotations.containsKey("ValueObject") || annotations.containsKey("VO"),
        )
    }

    fun parseColumn(comment: String): ColumnRelationMetadata {
        val annotations = parseAnnotations(comment)
        val relation = (annotations["Relation"] ?: annotations["Rel"])?.uppercase()
        require(relation == null || relation in setOf("MANY_TO_ONE", "ONE_TO_ONE", "1:1", "*:1", "MANYTOONE", "ONETOONE")) {
            "unsupported relation type in first slice: ${annotations["Relation"] ?: annotations["Rel"]}"
        }
        return ColumnRelationMetadata(
            referenceTable = annotations["Reference"] ?: annotations["Ref"],
            explicitRelationType = when (relation) {
                "ONE_TO_ONE", "1:1", "ONETOONE" -> "ONE_TO_ONE"
                "MANY_TO_ONE", "*:1", "MANYTOONE" -> "MANY_TO_ONE"
                else -> null
            },
            lazy = (annotations["Lazy"] ?: annotations["L"])?.toBooleanStrictOrNull(),
            countHint = annotations["Count"] ?: annotations["C"],
        )
    }
}
```

In `DbSchemaSourceProvider.kt`, call the parser once per table and column and map the normalized values into the snapshots instead of leaving relation metadata inside raw comments.

- [ ] **Step 4: Run the source tests and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbRelationAnnotationParserTest" --tests "com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProviderTest"
```

Expected: `BUILD SUCCESSFUL` and the new relation-carriage tests pass without touching planner or renderer code.

- [ ] **Step 5: Commit the source-carriage slice**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt
git add cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParser.kt
git add cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt
git add cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParserTest.kt
git add cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt
git commit -m "feat: add aggregate relation source carriage"
```

## Task 2: Assemble a Bounded Aggregate Relation Canonical Model Before Planning

**Files:**
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Write the failing assembler tests for relation inference**

Add to `DefaultCanonicalAssemblerTest.kt`:

```kotlin
@Test
fun `assembler builds one to many from parent child table metadata`() {
    val result = DefaultCanonicalAssembler().assemble(
        aggregateProjectConfig(),
        listOf(
            DbSchemaSnapshot(
                tables = listOf(
                    DbTableSnapshot(
                        tableName = "video_post",
                        comment = "",
                        columns = listOf(DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true)),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                        aggregateRoot = true,
                    ),
                    DbTableSnapshot(
                        tableName = "video_post_item",
                        comment = "",
                        columns = listOf(
                            DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            DbColumnSnapshot("video_post_id", "BIGINT", "Long", false, referenceTable = "video_post"),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                        parentTable = "video_post",
                        aggregateRoot = false,
                        valueObject = true,
                    ),
                )
            )
        )
    )

    val relation = result.model.aggregateRelations.single()
    assertEquals("VideoPost", relation.ownerEntityName)
    assertEquals("items", relation.fieldName)
    assertEquals("VideoPostItem", relation.targetEntityName)
    assertEquals(AggregateRelationType.ONE_TO_MANY, relation.relationType)
}
```

```kotlin
@Test
fun `assembler defaults reference without explicit relation to many to one`() {
    val result = DefaultCanonicalAssembler().assemble(
        aggregateProjectConfig(),
        listOf(
            DbSchemaSnapshot(
                tables = listOf(
                    DbTableSnapshot(
                        tableName = "video_post",
                        comment = "",
                        columns = listOf(
                            DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            DbColumnSnapshot("author_id", "BIGINT", "Long", false, referenceTable = "user_profile"),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                    ),
                    DbTableSnapshot(
                        tableName = "user_profile",
                        comment = "",
                        columns = listOf(DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true)),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                    ),
                )
            )
        )
    )

    val relation = result.model.aggregateRelations.single()
    assertEquals(AggregateRelationType.MANY_TO_ONE, relation.relationType)
    assertEquals("author", relation.fieldName)
    assertEquals("UserProfile", relation.targetEntityName)
}
```

```kotlin
@Test
fun `assembler rejects unsupported many to many relation metadata`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
        DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot(
                                    name = "tag_id",
                                    dbType = "BIGINT",
                                    kotlinType = "Long",
                                    nullable = false,
                                    explicitRelationType = "MANY_TO_MANY",
                                    referenceTable = "tag",
                                )
                            ),
                            primaryKey = listOf("tag_id"),
                            uniqueConstraints = emptyList(),
                        )
                    )
                )
            )
        )
    }

    assertEquals("unsupported aggregate relation type in first slice: MANY_TO_MANY", error.message)
}
```

- [ ] **Step 2: Implement the canonical relation types and inference helper**

Extend `PipelineModels.kt` with bounded aggregate types:

```kotlin
enum class AggregateRelationType {
    MANY_TO_ONE,
    ONE_TO_ONE,
    ONE_TO_MANY,
}

enum class AggregateFetchType {
    LAZY,
    EAGER,
}

data class AggregateRelationModel(
    val ownerEntityName: String,
    val ownerEntityPackageName: String,
    val fieldName: String,
    val targetEntityName: String,
    val targetEntityPackageName: String,
    val relationType: AggregateRelationType,
    val joinColumn: String,
    val fetchType: AggregateFetchType,
)
```

Also extend:

```kotlin
data class EntityModel(
    val name: String,
    val packageName: String,
    val tableName: String,
    val comment: String,
    val fields: List<FieldModel>,
    val idField: FieldModel,
    val uniqueConstraints: List<List<String>> = emptyList(),
    val aggregateRoot: Boolean = true,
    val valueObject: Boolean = false,
    val parentEntityName: String? = null,
)
```

Append this property at the end of `CanonicalModel`:

```kotlin
val aggregateRelations: List<AggregateRelationModel> = emptyList(),
```

Create `AggregateRelationInference.kt` with a focused helper:

```kotlin
internal object AggregateRelationInference {
    private data class Endpoint(
        val entityName: String,
        val packageName: String,
    )

    fun fromTables(basePackage: String, tables: List<DbTableSnapshot>): List<AggregateRelationModel> {
        val entityLookup = tables.associateBy(
            keySelector = { it.tableName.lowercase() },
            valueTransform = { table ->
                Endpoint(
                    entityName = AggregateNaming.entityName(table.tableName),
                    packageName = "${basePackage}.domain.aggregates.${AggregateNaming.tableSegment(table.tableName)}",
                )
            }
        )

        val parentChildRelations = tables
            .filter { it.parentTable != null }
            .map { child ->
                val parent = requireNotNull(entityLookup[child.parentTable!!.lowercase()]) { "unknown parent table: ${child.parentTable}" }
                val target = requireNotNull(entityLookup[child.tableName.lowercase()]) { "unknown child table: ${child.tableName}" }
                AggregateRelationModel(
                    ownerEntityName = parent.entityName,
                    ownerEntityPackageName = parent.packageName,
                    fieldName = pluralize(target.entityName),
                    targetEntityName = target.entityName,
                    targetEntityPackageName = target.packageName,
                    relationType = AggregateRelationType.ONE_TO_MANY,
                    joinColumn = child.columns.first { it.referenceTable?.equals(child.parentTable, true) == true }.name,
                    fetchType = AggregateFetchType.LAZY,
                )
            }

        val explicitRelations = tables.flatMap { table ->
            val owner = requireNotNull(entityLookup[table.tableName.lowercase()]) { "unknown owner table: ${table.tableName}" }
            table.columns
                .filter { it.referenceTable != null }
                .map { column ->
                    val target = requireNotNull(entityLookup[column.referenceTable!!.lowercase()]) { "unknown reference table: ${column.referenceTable}" }
                    AggregateRelationModel(
                        ownerEntityName = owner.entityName,
                        ownerEntityPackageName = owner.packageName,
                        fieldName = relationFieldName(column.name, target.entityName),
                        targetEntityName = target.entityName,
                        targetEntityPackageName = target.packageName,
                        relationType = when (column.explicitRelationType) {
                            "ONE_TO_ONE" -> AggregateRelationType.ONE_TO_ONE
                            null, "MANY_TO_ONE" -> AggregateRelationType.MANY_TO_ONE
                            else -> error("unsupported aggregate relation type in first slice: ${column.explicitRelationType}")
                        },
                        joinColumn = column.name,
                        fetchType = if (column.lazy == true) AggregateFetchType.LAZY else AggregateFetchType.EAGER,
                    )
                }
        }

        return (parentChildRelations + explicitRelations).distinctBy {
            listOf(it.ownerEntityName, it.fieldName, it.targetEntityName, it.relationType)
        }
    }

    private fun relationFieldName(columnName: String, targetEntityName: String): String {
        val stem = columnName.removeSuffix("_id").removeSuffix("_ID")
        return if (stem.isNotBlank()) {
            stem.split("_").joinToString("") { part ->
                part.lowercase().replaceFirstChar { it.titlecase() }
            }.replaceFirstChar { it.lowercase() }
        } else {
            targetEntityName.replaceFirstChar { it.lowercase() }
        }
    }

    private fun pluralize(typeName: String): String =
        typeName.replaceFirstChar { it.lowercase() } + "s"
}
```

- [ ] **Step 3: Wire the assembler to produce immutable relation truth**

Update `DefaultCanonicalAssembler.kt` so that:

```kotlin
val aggregateRelations = AggregateRelationInference.fromTables(
    basePackage = config.basePackage,
    tables = supportedTables,
)

EntityModel(
    name = entityName,
    packageName = "${config.basePackage}.domain.aggregates.$segment",
    tableName = table.tableName,
    comment = table.comment,
    fields = fields,
    idField = idField,
    uniqueConstraints = table.uniqueConstraints,
    aggregateRoot = table.aggregateRoot,
    valueObject = table.valueObject,
    parentEntityName = table.parentTable?.let(AggregateNaming::entityName),
)
```

and return:

```kotlin
CanonicalModel(
    requests = requests,
    validators = validators,
    domainEvents = domainEvents,
    schemas = aggregateModels.map { it.first },
    entities = aggregateModels.map { it.second },
    repositories = aggregateModels.map { it.third },
    analysisGraph = analysisGraph,
    drawingBoard = drawingBoard,
    apiPayloads = apiPayloads,
    sharedEnums = sharedEnums,
    aggregateRelations = aggregateRelations,
)
```

Keep inference before planning. Do not push relation semantics into `FieldModel`.

- [ ] **Step 4: Run the assembler tests and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: `BUILD SUCCESSFUL`, with new tests covering `ONE_TO_MANY`, default `MANY_TO_ONE`, explicit `ONE_TO_ONE`, and first-slice rejection.

- [ ] **Step 5: Commit the canonical relation layer**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt
git add cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: add aggregate relation canonical model"
```

## Task 3: Consume Canonical Relations in the Aggregate Entity Planner and Template

**Files:**
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Write the failing planner tests for scalar and relation field separation**

Add to `AggregateArtifactPlannerTest.kt`:

```kotlin
@Test
fun `entity planner surfaces relation fields separately from scalar fields`() {
    val plan = AggregateArtifactPlanner().plan(
        aggregateProjectConfig(),
        CanonicalModel(
            entities = listOf(
                EntityModel(
                    name = "VideoPost",
                    packageName = "com.acme.demo.domain.aggregates.video_post",
                    tableName = "video_post",
                    comment = "video post",
                    fields = listOf(
                        FieldModel("id", "Long"),
                        FieldModel("title", "String"),
                    ),
                    idField = FieldModel("id", "Long"),
                )
            ),
            aggregateRelations = listOf(
                AggregateRelationModel(
                    ownerEntityName = "VideoPost",
                    ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                    fieldName = "items",
                    targetEntityName = "VideoPostItem",
                    targetEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                    relationType = AggregateRelationType.ONE_TO_MANY,
                    joinColumn = "video_post_id",
                    fetchType = AggregateFetchType.LAZY,
                )
            )
        )
    )

    val entityItem = plan.single { it.templateId == "aggregate/entity.kt.peb" }
    val scalarFields = entityItem.context["scalarFields"] as List<Map<String, Any?>>
    val relationFields = entityItem.context["relationFields"] as List<Map<String, Any?>>

    assertEquals(listOf("id", "title"), scalarFields.map { it["name"] })
    assertEquals(listOf("items"), relationFields.map { it["name"] })
    assertEquals("ONE_TO_MANY", relationFields.single()["relationType"])
    assertEquals("VideoPostItem", relationFields.single()["targetType"])
}
```

- [ ] **Step 2: Write the failing renderer fallback tests for relation annotations**

Add to `PebbleArtifactRendererTest.kt`:

```kotlin
@Test
fun `aggregate entity preset renders bounded relation fields`() {
    val rendered = renderer.render(
        config = templateConfig(),
        item = ArtifactPlanItem(
            generatorId = "aggregate",
            moduleRole = "domain",
            templateId = "aggregate/entity.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.video_post",
                "typeName" to "VideoPost",
                "comment" to "video post",
                "tableName" to "video_post",
                "scalarFields" to listOf(mapOf("name" to "id", "type" to "Long", "nullable" to false)),
                "relationFields" to listOf(
                    mapOf(
                        "name" to "author",
                        "targetType" to "UserProfile",
                        "relationType" to "MANY_TO_ONE",
                        "fetchType" to "LAZY",
                        "joinColumn" to "author_id",
                    )
                ),
            ),
            conflictPolicy = ConflictPolicy.SKIP,
        ),
    )

    assertTrue(rendered.content.contains("@ManyToOne(fetch = FetchType.LAZY)"))
    assertTrue(rendered.content.contains("@JoinColumn(name = \"author_id\")"))
    assertTrue(rendered.content.contains("val author: UserProfile"))
}
```

- [ ] **Step 3: Implement planner-to-template relation mapping**

Create `AggregateRelationPlanning.kt` and update `EntityArtifactPlanner.kt` along these lines:

```kotlin
internal object AggregateRelationPlanning {
    fun relationFieldsFor(entity: EntityModel, relations: List<AggregateRelationModel>): List<Map<String, Any?>> =
        relations
            .filter { it.ownerEntityName == entity.name && it.ownerEntityPackageName == entity.packageName }
            .map { relation ->
                mapOf(
                    "name" to relation.fieldName,
                    "targetType" to relation.targetEntityName,
                    "targetPackageName" to relation.targetEntityPackageName,
                    "relationType" to relation.relationType.name,
                    "fetchType" to relation.fetchType.name,
                    "joinColumn" to relation.joinColumn,
                )
            }
}
```

Then in `EntityArtifactPlanner.kt`:

```kotlin
val scalarFields = entity.fields.map { field ->
    mapOf(
        "name" to field.name,
        "type" to field.type,
        "nullable" to field.nullable,
        "defaultValue" to field.defaultValue,
    )
}
val relationFields = AggregateRelationPlanning.relationFieldsFor(entity, model.aggregateRelations)

context = mapOf(
    "packageName" to entity.packageName,
    "typeName" to entity.name,
    "comment" to entity.comment,
    "tableName" to entity.tableName,
    "idField" to entity.idField,
    "scalarFields" to scalarFields,
    "relationFields" to relationFields,
)
```

Keep relation handling local to entity rendering. Do not change schema, repository, factory, specification, wrapper, unique, or enum families in this slice.

- [ ] **Step 4: Update `aggregate/entity.kt.peb` to render bounded relation annotations**

Replace the current single-loop template with a split form:

```pebble
package {{ packageName }}

import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne

data class {{ typeName }}(
{% for field in scalarFields %}
    val {{ field.name }}: {{ field.type }}{% if field.nullable %}?{% endif %}{% if relationFields or not loop.last %},{% endif %}
{% endfor %}
{% for relation in relationFields %}
{% if relation.relationType == "MANY_TO_ONE" %}
    @ManyToOne(fetch = FetchType.{{ relation.fetchType }})
    @JoinColumn(name = "{{ relation.joinColumn }}")
{% elseif relation.relationType == "ONE_TO_ONE" %}
    @OneToOne(fetch = FetchType.{{ relation.fetchType }})
    @JoinColumn(name = "{{ relation.joinColumn }}")
{% elseif relation.relationType == "ONE_TO_MANY" %}
    @OneToMany(mappedBy = "{{ relation.joinColumn }}", fetch = FetchType.{{ relation.fetchType }})
{% endif %}
    val {{ relation.name }}: {% if relation.relationType == "ONE_TO_MANY" %}List<{{ relation.targetType }}>{% else %}{{ relation.targetType }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
)
```

Keep this bounded. Do not add `ManyToMany` or inverse-side controls.

- [ ] **Step 5: Run planner and renderer tests, then commit**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: `BUILD SUCCESSFUL`, with new assertions proving relation fields are planner-owned and template fallback renders bounded annotations.

Commit:

```bash
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb
git add cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: render aggregate relation fields"
```

## Task 4: Add Functional and Compile Verification for Representative Relations

**Files:**
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/schema.sql`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/schema.sql`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateRelationCompileSmoke.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Add the failing functional test for generated relation artifacts**

Add to `PipelinePluginFunctionalTest.kt`:

```kotlin
@Test
fun `aggregate relation generation emits parent child and explicit relation fields`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-relation")
    copyFixture(projectDir, "aggregate-relation-sample")

    val result = FunctionalFixtureSupport
        .runner(projectDir, "cap4kGenerate")
        .build()

    val rootEntity = projectDir.resolve(
        "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
    ).readText()
    val childEntity = projectDir.resolve(
        "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post_item/VideoPostItem.kt"
    ).readText()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(rootEntity.contains("@OneToMany"))
    assertTrue(rootEntity.contains("val items: List<VideoPostItem>"))
    assertTrue(rootEntity.contains("@ManyToOne(fetch = FetchType.LAZY)"))
    assertTrue(rootEntity.contains("val author: UserProfile"))
    assertTrue(rootEntity.contains("@OneToOne"))
    assertTrue(rootEntity.contains("val coverProfile: UserProfile"))
    assertTrue(childEntity.contains("@ManyToOne(fetch = FetchType.LAZY)"))
    assertTrue(childEntity.contains("val videoPost: VideoPost"))
}
```

- [ ] **Step 2: Add the failing compile test for relation-bearing entities**

Add to `PipelinePluginCompileFunctionalTest.kt`:

```kotlin
@Test
fun `aggregate relation generation participates in domain compileKotlin`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-relation-compile")
    FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-relation-compile-sample")

    val beforeGenerateCompileResult = FunctionalFixtureSupport
        .runner(projectDir, ":demo-domain:compileKotlin")
        .buildAndFail()
    assertEquals(
        TaskOutcome.FAILED,
        beforeGenerateCompileResult.task(":demo-domain:compileKotlin")?.outcome
    )
    assertTrue(beforeGenerateCompileResult.output.contains("VideoPost"))
    assertTrue(beforeGenerateCompileResult.output.contains("VideoPostItem"))

    val (generateResult, compileResult) = FunctionalFixtureSupport.generateThenCompile(
        projectDir,
        ":demo-domain:compileKotlin"
    )

    assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    assertGeneratedFilesExist(
        projectDir,
        "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
        "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post_item/VideoPostItem.kt",
        "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/user_profile/UserProfile.kt",
    )
}
```

- [ ] **Step 3: Add the minimal fixtures**

Use `aggregate-sample` and `aggregate-compile-sample` as the base shape. The new `schema.sql` should include one parent-child relation plus one explicit one-to-one reference:

```sql
create table video_post (
    id bigint primary key,
    author_id bigint not null comment '@Reference=user_profile;@Relation=ManyToOne;@Lazy=true;',
    cover_profile_id bigint null comment '@Reference=user_profile;@Relation=OneToOne;',
    title varchar(255) not null
);

create table video_post_item (
    id bigint primary key,
    video_post_id bigint not null comment '@Reference=video_post;',
    label varchar(128) not null
);

create table user_profile (
    id bigint primary key,
    nickname varchar(128) not null
);

comment on table video_post is '@AggregateRoot=true;';
comment on table video_post_item is '@Parent=video_post;@VO;';
comment on table user_profile is '@AggregateRoot=true;';
```

The compile smoke source should force the generated relations to participate in typechecking:

```kotlin
package com.acme.demo.domain.aggregates.video_post

import com.acme.demo.domain.aggregates.video_post_item.VideoPostItem
import com.acme.demo.domain.aggregates.user_profile.UserProfile

class AggregateRelationCompileSmoke {
    fun touch(entity: VideoPost, child: VideoPostItem, profile: UserProfile) {
        entity.items.forEach { it.label }
        entity.author.nickname
        entity.coverProfile?.nickname
        child.videoPost.id
        profile.id
    }
}
```

- [ ] **Step 4: Run functional and compile verification**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected:

- `cap4kGenerate` writes relation-bearing entity files into `demo-domain`
- generated domain entities contain `@OneToMany`, `@ManyToOne`, `@OneToOne`, and bounded `FetchType.LAZY`
- `:demo-domain:compileKotlin` fails before generation and passes after generation for the relation compile sample

- [ ] **Step 5: Commit the functional and compile coverage**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "test: cover aggregate relation parity"
```

## Task 5: Run the Full Relation Slice Regression and Verify No Scope Leaks

**Files:**
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Add one explicit regression that unsupported relation forms stay out of scope**

Add one functional or assembler test that proves first-slice rejection remains hard:

```kotlin
@Test
fun `many to many relation metadata fails fast in first slice`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
        DbRelationAnnotationParser().parseColumn("@Reference=tag;@Relation=ManyToMany;")
    }

    assertEquals("unsupported relation type in first slice: ManyToMany", error.message)
}
```

- [ ] **Step 2: Run the focused module regression**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-db:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test
```

Expected: `BUILD SUCCESSFUL`, with no new failures in aggregate factory/specification/wrapper/unique/enum slices.

- [ ] **Step 3: Verify generated entity output still keeps scalar fields intact**

Re-open one generated entity from the functional fixture and assert both sections coexist:

```kotlin
val generated = projectDir.resolve(
    "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
).readText()

assertTrue(generated.contains("val title: String"))
assertTrue(generated.contains("val items: List<VideoPostItem>"))
assertFalse(generated.contains("ManyToMany"))
```

This step is here to stop accidental template regressions where relation rendering replaces scalar rendering instead of enriching it.

- [ ] **Step 4: Inspect git diff for scope leaks before final commit**

Run:

```powershell
git diff --stat
git diff --name-only
```

Expected: only the files listed in Tasks 1-4 changed. No unrelated generator families, no bootstrap files, no design generator files.

- [ ] **Step 5: Commit final hardening only when verification exposed one last missing assertion**

If Step 1-4 required no further code change, stop after verification. If you had to add or tighten one last regression assertion, use:

```bash
git add cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt
git add cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git add cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "test: harden aggregate relation parity regressions"
```

## Acceptance Checklist

- [ ] `db` source surfaces bounded relation annotation carriage without creating a new source type
- [ ] canonical assembly produces immutable `aggregateRelations` before any planner runs
- [ ] `FieldModel` remains a scalar field carrier; relation semantics live in dedicated aggregate relation models
- [ ] `EntityArtifactPlanner` consumes relation models and keeps scalar fields intact
- [ ] `aggregate/entity.kt.peb` renders bounded `OneToMany`, `ManyToOne`, `OneToOne`, and `FetchType.LAZY`
- [ ] `ManyToMany`, inverse-nav strategy, and JPA fine-grained control remain unsupported in this slice
- [ ] functional generation proves representative relation artifacts are emitted
- [ ] compile verification proves generated relation-bearing entities participate in `:demo-domain:compileKotlin`
