# Cap4k Aggregate Provider-Specific Persistence Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bounded aggregate provider-specific persistence parity for explicit `@DynamicInsert`, `@DynamicUpdate`, `@SQLDelete`, and `@Where` without reopening implicit deleted-field heuristics, relation-side provider behavior, or id-generator semantics.

**Architecture:** Keep the work on the existing aggregate line. Extend `DbTableSnapshot` and db comment parsing with explicit table-level provider-specific carriage, map that metadata into aggregate-owned canonical provider controls before planning, let `EntityArtifactPlanner` compose render-ready soft-delete SQL and where clauses, and let `aggregate/entity.kt.peb` emit only the bounded provider-specific annotations. Verification stays layered: source/parser, canonical, planner/renderer, and functional/compile tests.

**Tech Stack:** Kotlin, JUnit 5, Gradle TestKit, Pebble preset rendering, existing aggregate db source and compile harness

---

## File Structure

### New files

- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceProviderInference.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/schema.sql`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/schema.sql`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateProviderPersistenceCompileSmoke.kt`

### Existing files to modify

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParser.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParserTest.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

### Responsibilities

- `PipelineModels.kt`
  - carry explicit source-owned provider-specific truth and aggregate-owned canonical provider controls without polluting shared scalar models

- `DbTableAnnotationParser.kt`
  - parse the bounded explicit annotation set for `dynamicInsert`, `dynamicUpdate`, and `softDeleteColumn`

- `DbSchemaSourceProvider.kt`
  - surface parsed table-level provider-specific metadata through `DbTableSnapshot`

- `AggregatePersistenceProviderInference.kt`
  - convert explicit source carriage plus existing id/version truth into deterministic aggregate-owned provider controls

- `DefaultCanonicalAssembler.kt`
  - wire bounded provider control inference into canonical assembly before planning

- `EntityArtifactPlanner.kt`
  - join aggregate-owned provider controls into render-ready entity context including `softDeleteSql` and `softDeleteWhereClause`

- `aggregate/entity.kt.peb`
  - emit bounded `@DynamicInsert`, `@DynamicUpdate`, `@SQLDelete`, and `@Where` only when explicitly requested

- functional fixtures and tests
  - prove plan/generate/compile closure without reopening implicit deleted-field heuristics, relation-side behavior, or `@GenericGenerator`

## Task 1: Extend db Source Carriage for Explicit Provider-Specific Table Metadata

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParser.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParserTest.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`

- [ ] **Step 1: Write the failing parser and source-provider tests**

Add to `DbTableAnnotationParserTest.kt` cases equivalent to:

```kotlin
@Test
fun `parser extracts provider specific table controls from comment`() {
    val metadata = DbTableAnnotationParser.parse(
        "@AggregateRoot=true;@DynamicInsert=true;@DynamicUpdate=true;@SoftDeleteColumn=deleted;"
    )

    assertEquals(true, metadata.aggregateRoot)
    assertEquals(true, metadata.dynamicInsert)
    assertEquals(true, metadata.dynamicUpdate)
    assertEquals("deleted", metadata.softDeleteColumn)
}

@Test
fun `parser rejects malformed dynamic insert value`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
        DbTableAnnotationParser.parse("@DynamicInsert=maybe;")
    }

    assertEquals("invalid @DynamicInsert value: maybe", error.message)
}
```

Add to `DbSchemaSourceProviderTest.kt` a provider-level case equivalent to:

```kotlin
@Test
fun `provider carries explicit provider specific table metadata into db snapshot`() {
    val snapshot = loadSchema(
        """
        create table video_post (
            id bigint primary key comment '@GeneratedValue=IDENTITY;',
            version bigint not null comment '@Version=true;',
            deleted int not null,
            title varchar(128) not null
        );
        comment on table video_post is '@AggregateRoot=true;@DynamicInsert=true;@DynamicUpdate=true;@SoftDeleteColumn=deleted;';
        """.trimIndent()
    )

    val table = snapshot.tables.single { it.tableName == "video_post" }

    assertEquals(true, table.dynamicInsert)
    assertEquals(true, table.dynamicUpdate)
    assertEquals("deleted", table.softDeleteColumn)
}
```

- [ ] **Step 2: Run the source-db tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbTableAnnotationParserTest" --tests "com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProviderTest"
```

Expected: FAIL because `DbTableSnapshot` and parser metadata do not yet expose provider-specific table carriage.

- [ ] **Step 3: Add explicit table-level source carriage and parser support**

Update `PipelineModels.kt` so `DbTableSnapshot` includes:

```kotlin
data class DbTableSnapshot(
    val tableName: String,
    val comment: String,
    val columns: List<DbColumnSnapshot>,
    val primaryKey: List<String>,
    val uniqueConstraints: List<List<String>>,
    val parentTable: String? = null,
    val aggregateRoot: Boolean? = null,
    val valueObject: Boolean? = null,
    val dynamicInsert: Boolean? = null,
    val dynamicUpdate: Boolean? = null,
    val softDeleteColumn: String? = null,
)
```

Extend the table annotation parser metadata shape and parsing logic in `DbTableAnnotationParser.kt` along these lines:

```kotlin
data class DbTableAnnotationMetadata(
    val parentTable: String? = null,
    val aggregateRoot: Boolean? = null,
    val valueObject: Boolean? = null,
    val dynamicInsert: Boolean? = null,
    val dynamicUpdate: Boolean? = null,
    val softDeleteColumn: String? = null,
)
```

and:

```kotlin
when (key.uppercase()) {
    "DYNAMICINSERT" -> dynamicInsert = parseBooleanAnnotation("DynamicInsert", value)
    "DYNAMICUPDATE" -> dynamicUpdate = parseBooleanAnnotation("DynamicUpdate", value)
    "SOFTDELETECOLUMN" -> {
        require(value.isNotBlank()) { "invalid @SoftDeleteColumn value: $value" }
        softDeleteColumn = value.trim()
    }
}

private fun parseBooleanAnnotation(name: String, value: String): Boolean {
    return when {
        value.equals("true", ignoreCase = true) -> true
        value.equals("false", ignoreCase = true) -> false
        else -> throw IllegalArgumentException("invalid @$name value: $value")
    }
}
```

Then thread the parsed metadata through `DbSchemaSourceProvider.kt` when constructing `DbTableSnapshot`.

- [ ] **Step 4: Run the source-db tests to verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbTableAnnotationParserTest" --tests "com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProviderTest"
```

Expected: PASS with explicit provider-specific table metadata carried through snapshot creation.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParser.kt cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParserTest.kt cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt
git commit -m "feat: add explicit db provider persistence carriage"
```

## Task 2: Add Aggregate-Owned Canonical Provider Controls

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceProviderInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Write the failing canonical assembler tests**

Add to `DefaultCanonicalAssemblerTest.kt` cases equivalent to:

```kotlin
@Test
fun `assembler records explicit aggregate provider persistence controls`() {
    val result = DefaultCanonicalAssembler().assemble(
        aggregateProjectConfig(),
        listOf(
            DbSchemaSnapshot(
                tables = listOf(
                    DbTableSnapshot(
                        tableName = "video_post",
                        comment = "@AggregateRoot=true;",
                        columns = listOf(
                            DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true, generatedValueStrategy = "IDENTITY"),
                            DbColumnSnapshot("version", "BIGINT", "Long", false, version = true),
                            DbColumnSnapshot("deleted", "INT", "Int", false),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                        dynamicInsert = true,
                        dynamicUpdate = true,
                        softDeleteColumn = "deleted",
                    )
                )
            )
        )
    )

    val control = result.model.aggregatePersistenceProviderControls.single()

    assertEquals("VideoPost", control.entityName)
    assertEquals(true, control.dynamicInsert)
    assertEquals(true, control.dynamicUpdate)
    assertEquals("deleted", control.softDeleteColumn)
    assertEquals("id", control.idFieldName)
    assertEquals("version", control.versionFieldName)
}

@Test
fun `assembler does not infer provider controls when source is silent`() {
    val result = DefaultCanonicalAssembler().assemble(
        aggregateProjectConfig(),
        listOf(
            DbSchemaSnapshot(
                tables = listOf(
                    DbTableSnapshot(
                        tableName = "video_post",
                        comment = "@AggregateRoot=true;",
                        columns = listOf(
                            DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            DbColumnSnapshot("title", "VARCHAR", "String", false),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                    )
                )
            )
        )
    )

    assertTrue(result.model.aggregatePersistenceProviderControls.isEmpty())
}
```

- [ ] **Step 2: Run the core tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: FAIL because canonical provider controls do not exist yet.

- [ ] **Step 3: Add aggregate-owned canonical provider control inference**

Extend `PipelineModels.kt` with:

```kotlin
data class AggregatePersistenceProviderControl(
    val entityName: String,
    val entityPackageName: String,
    val tableName: String,
    val dynamicInsert: Boolean? = null,
    val dynamicUpdate: Boolean? = null,
    val softDeleteColumn: String? = null,
    val idFieldName: String,
    val versionFieldName: String? = null,
)
```

and extend `CanonicalModel`:

```kotlin
val aggregatePersistenceProviderControls: List<AggregatePersistenceProviderControl> = emptyList()
```

Create `AggregatePersistenceProviderInference.kt` with a helper shaped like:

```kotlin
object AggregatePersistenceProviderInference {
    fun infer(
        entities: List<EntityModel>,
        tables: List<DbTableSnapshot>,
    ): List<AggregatePersistenceProviderControl> {
        return entities.mapNotNull { entity ->
            val table = tables.firstOrNull { it.tableName.equals(entity.tableName, ignoreCase = true) } ?: return@mapNotNull null
            if (table.dynamicInsert == null && table.dynamicUpdate == null && table.softDeleteColumn == null) return@mapNotNull null

            val idField = entity.idField.name
            val versionField = entity.fields.firstOrNull { field ->
                table.columns.firstOrNull {
                    val normalizedColumn = toLowerSnakeCase(field.name)
                    it.name.equals(normalizedColumn, ignoreCase = true)
                }?.version == true
            }?.name

            AggregatePersistenceProviderControl(
                entityName = entity.name,
                entityPackageName = entity.packageName,
                tableName = entity.tableName,
                dynamicInsert = table.dynamicInsert,
                dynamicUpdate = table.dynamicUpdate,
                softDeleteColumn = table.softDeleteColumn,
                idFieldName = idField,
                versionFieldName = versionField,
            )
        }
    }
}
```

In `DefaultCanonicalAssembler.kt`, call the inference helper after entity assembly and place the result into `CanonicalModel`.

- [ ] **Step 4: Run the core tests to verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: PASS with explicit canonical provider controls only for explicitly annotated tables.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceProviderInference.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: infer aggregate provider persistence controls"
```

## Task 3: Thread Provider Controls Through Planner and Renderer

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Write the failing planner and renderer tests**

Add to `AggregateArtifactPlannerTest.kt` a planner case equivalent to:

```kotlin
@Test
fun `entity planner exposes provider specific persistence contract`() {
    val model = aggregateCanonicalModel(
        providerControls = listOf(
            AggregatePersistenceProviderControl(
                entityName = "VideoPost",
                entityPackageName = "com.acme.demo.domain.aggregates.video_post",
                tableName = "video_post",
                dynamicInsert = true,
                dynamicUpdate = true,
                softDeleteColumn = "deleted",
                idFieldName = "id",
                versionFieldName = "version",
            )
        )
    )

    val artifact = AggregateArtifactPlanner().plan(projectConfig(), model).single { it.templateId == "aggregate/entity.kt.peb" }

    assertEquals(true, artifact.renderModel["dynamicInsert"])
    assertEquals(true, artifact.renderModel["dynamicUpdate"])
    assertEquals("update \"video_post\" set \"deleted\" = 1 where \"id\" = ? and \"version\" = ?", artifact.renderModel["softDeleteSql"])
    assertEquals("\"deleted\" = 0", artifact.renderModel["softDeleteWhereClause"])
}
```

Add to `PebbleArtifactRendererTest.kt` renderer cases equivalent to:

```kotlin
@Test
fun `aggregate entity template renders provider specific persistence controls`() {
    val rendered = renderPreset(
        "aggregate/entity.kt.peb",
        mapOf(
            "packageName" to "com.acme.demo.domain.aggregates.video_post",
            "typeName" to "VideoPost",
            "tableName" to "video_post",
            "fields" to emptyList<Map<String, Any?>>(),
            "relationFields" to emptyList<Map<String, Any?>>(),
            "dynamicInsert" to true,
            "dynamicUpdate" to true,
            "softDeleteSql" to "update \"video_post\" set \"deleted\" = 1 where \"id\" = ? and \"version\" = ?",
            "softDeleteWhereClause" to "\"deleted\" = 0",
        )
    )

    assertTrue(rendered.contains("@DynamicInsert"))
    assertTrue(rendered.contains("@DynamicUpdate"))
    assertTrue(rendered.contains("@SQLDelete(sql = \"update \\\"video_post\\\" set \\\"deleted\\\" = 1 where \\\"id\\\" = ? and \\\"version\\\" = ?\")"))
    assertTrue(rendered.contains("@Where(clause = \"\\\"deleted\\\" = 0\")"))
}
```

- [ ] **Step 2: Run the planner and renderer tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: FAIL because entity planner and template do not yet expose provider-specific render contract.

- [ ] **Step 3: Add planner composition and template emission**

Update `EntityArtifactPlanner.kt` so it joins `aggregatePersistenceProviderControls` by both `entityName` and `entityPackageName`, then derives:

```kotlin
val providerControl = model.aggregatePersistenceProviderControls.firstOrNull {
    it.entityName == entity.name && it.entityPackageName == entity.packageName
}

val softDeleteSql = providerControl?.softDeleteColumn?.let { column ->
    if (providerControl.versionFieldName != null) {
        """update "$tableName" set "$column" = 1 where "${providerControl.idFieldName}" = ? and "${providerControl.versionFieldName}" = ?"""
    } else {
        """update "$tableName" set "$column" = 1 where "${providerControl.idFieldName}" = ?"""
    }
}

val softDeleteWhereClause = providerControl?.softDeleteColumn?.let { column ->
    """"$column" = 0"""
}
```

Thread into render model:

```kotlin
"dynamicInsert" to (providerControl?.dynamicInsert == true),
"dynamicUpdate" to (providerControl?.dynamicUpdate == true),
"softDeleteSql" to softDeleteSql,
"softDeleteWhereClause" to softDeleteWhereClause,
```

Then update `aggregate/entity.kt.peb` to mechanically emit:

```pebble
{% if dynamicInsert %}@DynamicInsert{% endif %}
{% if dynamicUpdate %}@DynamicUpdate{% endif %}
{% if softDeleteSql %}@SQLDelete(sql = "{{ softDeleteSql }}"){% endif %}
{% if softDeleteWhereClause %}@Where(clause = "{{ softDeleteWhereClause }}"){% endif %}
```

Also add any required conditional imports for:

- `org.hibernate.annotations.DynamicInsert`
- `org.hibernate.annotations.DynamicUpdate`
- `org.hibernate.annotations.SQLDelete`
- `org.hibernate.annotations.Where`

- [ ] **Step 4: Run the planner and renderer tests to verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: PASS with bounded provider-specific annotations emitted only when planner provides them.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: render aggregate provider persistence controls"
```

## Task 4: Add Functional and Compile Verification for Provider-Specific Persistence Parity

**Files:**
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/schema.sql`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/schema.sql`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateProviderPersistenceCompileSmoke.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Write the failing functional and compile tests**

Add to `PipelinePluginFunctionalTest.kt` a generation case equivalent to:

```kotlin
@Test
fun `aggregate provider specific persistence generation renders bounded controls`() {
    withFunctionalFixture("aggregate-provider-persistence-sample") { fixtureDir ->
        gradleRunner(fixtureDir, "cap4kGenerate").build()

        val generated = fixtureDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
        ).readText()

        assertTrue(generated.contains("@DynamicInsert"))
        assertTrue(generated.contains("@DynamicUpdate"))
        assertTrue(generated.contains("""@SQLDelete(sql = "update \"video_post\" set \"deleted\" = 1 where \"id\" = ? and \"version\" = ?")"""))
        assertTrue(generated.contains("""@Where(clause = "\"deleted\" = 0")"""))
        assertFalse(generated.contains("@GenericGenerator"))
    }
}
```

Add to `PipelinePluginCompileFunctionalTest.kt` a compile case equivalent to:

```kotlin
@Test
fun `aggregate provider specific persistence generation participates in domain compileKotlin`() {
    withFunctionalFixture("aggregate-provider-persistence-compile-sample") { fixtureDir ->
        gradleRunner(fixtureDir, "cap4kGenerate", ":demo-domain:compileKotlin").build()

        val generated = fixtureDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
        ).readText()

        assertTrue(generated.contains("@DynamicInsert"))
        assertTrue(generated.contains("@SQLDelete"))
    }
}
```

- [ ] **Step 2: Run the gradle functional tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected: FAIL because provider-specific fixtures and generation coverage do not exist yet.

- [ ] **Step 3: Add bounded provider-specific fixtures and test coverage**

Create `aggregate-provider-persistence-sample/schema.sql` with representative tables:

```sql
create table video_post (
    id bigint primary key comment '@GeneratedValue=IDENTITY;',
    version bigint not null comment '@Version=true;',
    deleted int not null,
    title varchar(128) not null
);
comment on table video_post is '@AggregateRoot=true;@DynamicInsert=true;@DynamicUpdate=true;@SoftDeleteColumn=deleted;';

create table audit_log (
    id bigint primary key comment '@GeneratedValue=IDENTITY;',
    deleted int not null,
    content varchar(128) not null
);
comment on table audit_log is '@AggregateRoot=true;@SoftDeleteColumn=deleted;';
```

Use minimal fixture build files following the current compile-harness pattern:

`aggregate-provider-persistence-sample/build.gradle.kts`

```kotlin
plugins {
    id("com.only4.cap4k.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
        applicationModulePath.set("demo-application")
        adapterModulePath.set("demo-adapter")
    }
    sources {
        db {
            enabled.set(true)
            url.set("jdbc:h2:file:./build/testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
            username.set("sa")
            password.set("")
        }
    }
    generators {
        aggregate {
            enabled.set(true)
        }
    }
}
```

`aggregate-provider-persistence-compile-sample/demo-domain/build.gradle.kts`

```kotlin
plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.springframework:spring-context:6.1.6")
    implementation("org.hibernate.orm:hibernate-core:6.4.4.Final")
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
}
```

`AggregateProviderPersistenceCompileSmoke.kt`

```kotlin
package com.acme.demo.domain.aggregates.video_post

object AggregateProviderPersistenceCompileSmoke {
    fun verify(post: VideoPost, log: AuditLog): List<Any> = listOf(post, log)
}
```

Keep non-compiled application and adapter module build files as placeholders when they do not participate in this slice.

- [ ] **Step 4: Run the gradle functional tests to verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected: PASS with bounded provider-specific generation and compile verification.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "test: cover aggregate provider persistence parity"
```

## Task 5: Run Focused Regression and Verify Scope Guards

**Files:**
- Modify only if a real regression is found while running verification

- [ ] **Step 1: Run the focused regression suite**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-db:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test
```

Expected: PASS across source, core, aggregate generator, renderer, and functional/compile verification.

- [ ] **Step 2: Verify bounded output and absence of forbidden drift**

Inspect representative generated entity output and assert:

```text
present:
- @DynamicInsert
- @DynamicUpdate
- @SQLDelete(sql = "update \"video_post\" set \"deleted\" = 1 where \"id\" = ? and \"version\" = ?")
- @Where(clause = "\"deleted\" = 0")

absent:
- @GenericGenerator
- relation-side provider-specific behavior
- implicit deleted-field heuristics
```

Use a direct check such as:

```powershell
Select-String -Path .\cap4k-plugin-pipeline-gradle\src\test\resources\functional\aggregate-provider-persistence-compile-sample\demo-domain\src\main\kotlin\com\acme\demo\domain\aggregates\video_post\VideoPost.kt -Pattern "@DynamicInsert","@DynamicUpdate","@SQLDelete","@Where","@GenericGenerator"
```

Expected: first four patterns found, `@GenericGenerator` absent.

- [ ] **Step 3: Fix only real regressions if any are found**

If verification exposes a real issue, apply the minimal bounded fix. Examples of acceptable fixes:

```kotlin
// Example: only emit provider-specific imports when corresponding annotations are present
val hasProviderSpecificSoftDelete = softDeleteSql != null && softDeleteWhereClause != null
```

Do not widen the slice to solve broader old-codegen parity issues.

- [ ] **Step 4: Re-run the focused regression if any fix was needed**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-db:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test
```

Expected: PASS after the minimal bounded fix.

- [ ] **Step 5: Commit only if a verification fix was required**

```bash
git add <exact files changed>
git commit -m "fix: harden aggregate provider persistence parity"
```

## Spec Coverage Check

- Source carriage for `dynamicInsert`, `dynamicUpdate`, and `softDeleteColumn` is implemented in Task 1.
- Aggregate-owned provider control inference and bounded composition are implemented in Task 2.
- Planner-owned render mapping and template-owned mechanical emission are implemented in Task 3.
- Functional and compile verification for bounded provider-specific parity are implemented in Task 4.
- Focused regression and explicit scope-guard verification are implemented in Task 5.

## Placeholder Scan

- No `TODO`, `TBD`, or deferred implementation placeholders remain.
- Every code step includes concrete code or exact commands.
- Every verification step has explicit expected outcomes.

## Type Consistency Check

- Source carriage uses `DbTableSnapshot.dynamicInsert`, `DbTableSnapshot.dynamicUpdate`, and `DbTableSnapshot.softDeleteColumn` consistently across Tasks 1-5.
- Canonical provider metadata uses `AggregatePersistenceProviderControl` consistently across Tasks 2-5.
- Planner and template render contract uses `dynamicInsert`, `dynamicUpdate`, `softDeleteSql`, and `softDeleteWhereClause` consistently across Tasks 3-5.
