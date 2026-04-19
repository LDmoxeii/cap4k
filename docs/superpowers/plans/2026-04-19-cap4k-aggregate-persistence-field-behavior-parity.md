# Cap4k Aggregate Persistence Field-Behavior Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bounded aggregate persistence field-behavior parity for explicit `@GeneratedValue(IDENTITY)`, `@Version`, and scalar `insertable/updatable` control without reopening implicit legacy heuristics or provider-specific behavior.

**Architecture:** Keep the work on the existing aggregate line. Extend `DbColumnSnapshot` and db comment parsing with explicit field-behavior carriage, map that metadata into aggregate-owned canonical persistence controls before planning, let `EntityArtifactPlanner` join those controls into scalar render context, and let `aggregate/entity.kt.peb` emit only the bounded field-behavior annotations. Verification stays layered: source/parser, canonical, planner/renderer, and functional/compile tests.

**Tech Stack:** Kotlin, JUnit 5, Gradle TestKit, Pebble preset rendering, existing aggregate db source and compile harness

---

## File Structure

### New files

- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceFieldBehaviorInference.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-sample/schema.sql`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-compile-sample/schema.sql`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-compile-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-compile-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-compile-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregatePersistenceCompileSmoke.kt`

### Existing files to modify

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt`
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
  - carry explicit source-owned field-behavior truth and aggregate-owned canonical persistence controls without polluting `FieldModel`

- `DbColumnAnnotationParser.kt`
  - parse the bounded explicit annotation set for generated-value, version, insertability, and updatability

- `DbSchemaSourceProvider.kt`
  - surface parsed column-level field-behavior metadata through `DbColumnSnapshot`

- `AggregatePersistenceFieldBehaviorInference.kt`
  - convert explicit source carriage into deterministic aggregate-owned canonical persistence controls

- `DefaultCanonicalAssembler.kt`
  - wire bounded persistence control inference into canonical assembly before planning

- `EntityArtifactPlanner.kt`
  - join aggregate-owned persistence controls into scalar render context

- `aggregate/entity.kt.peb`
  - emit bounded `@GeneratedValue`, `@Version`, and scalar `@Column(insertable/updatable)` output only when explicitly requested

- functional fixtures and tests
  - prove plan/generate/compile closure without reopening implicit conventions or provider-specific behavior

## Task 1: Extend db Source Carriage for Explicit Field-Behavior Metadata

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`

- [ ] **Step 1: Write the failing parser and source-provider tests**

Add to `DbColumnAnnotationParserTest.kt` cases equivalent to:

```kotlin
@Test
fun `parser extracts generated value version and write controls from comment`() {
    val metadata = DbColumnAnnotationParser.parse(
        "audit field @GeneratedValue=IDENTITY;@Version=true;@Insertable=false;@Updatable=false;"
    )

    assertEquals("IDENTITY", metadata.generatedValueStrategy)
    assertEquals(true, metadata.version)
    assertEquals(false, metadata.insertable)
    assertEquals(false, metadata.updatable)
}

@Test
fun `parser rejects unsupported generated value strategy`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
        DbColumnAnnotationParser.parse("@GeneratedValue=SEQUENCE;")
    }

    assertEquals("unsupported @GeneratedValue strategy in this slice: SEQUENCE", error.message)
}
```

Add to `DbSchemaSourceProviderTest.kt` a provider-level case equivalent to:

```kotlin
@Test
fun `provider carries explicit persistence field behavior into db snapshot`() {
    val snapshot = loadSchema(
        """
        create table video_post (
            id bigint primary key comment '@GeneratedValue=IDENTITY;',
            version bigint not null comment '@Version=true;',
            created_by varchar(64) comment '@Insertable=false;',
            updated_by varchar(64) comment '@Updatable=false;',
            title varchar(128) not null
        );
        comment on table video_post is '@AggregateRoot=true;';
        """.trimIndent()
    )

    val table = snapshot.tables.single { it.tableName == "video_post" }

    assertEquals("IDENTITY", table.columns.single { it.name == "id" }.generatedValueStrategy)
    assertEquals(true, table.columns.single { it.name == "version" }.version)
    assertEquals(false, table.columns.single { it.name == "created_by" }.insertable)
    assertEquals(false, table.columns.single { it.name == "updated_by" }.updatable)
}
```

- [ ] **Step 2: Run the source-db tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbColumnAnnotationParserTest" --tests "com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProviderTest"
```

Expected: FAIL because `DbColumnSnapshot` and parser metadata do not yet expose persistence field-behavior carriage.

- [ ] **Step 3: Add explicit source carriage and parser support**

Update `PipelineModels.kt` so `DbColumnSnapshot` includes:

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
    val generatedValueStrategy: String? = null,
    val version: Boolean = false,
    val insertable: Boolean? = null,
    val updatable: Boolean? = null,
)
```

Extend the annotation parser metadata shape and parsing logic in `DbColumnAnnotationParser.kt` along these lines:

```kotlin
data class DbColumnAnnotationMetadata(
    val typeBinding: String? = null,
    val enumItems: List<EnumItemModel> = emptyList(),
    val generatedValueStrategy: String? = null,
    val version: Boolean = false,
    val insertable: Boolean? = null,
    val updatable: Boolean? = null,
)
```

and:

```kotlin
when (key.uppercase()) {
    "GENERATEDVALUE" -> {
        val strategy = value.uppercase()
        require(strategy == "IDENTITY") {
            "unsupported @GeneratedValue strategy in this slice: $strategy"
        }
        generatedValueStrategy = strategy
    }
    "VERSION" -> version = value.equals("true", ignoreCase = true)
    "INSERTABLE" -> insertable = value.equals("true", ignoreCase = true)
    "UPDATABLE" -> updatable = value.equals("true", ignoreCase = true)
}
```

Then thread the parsed metadata through `DbSchemaSourceProvider.kt` when constructing `DbColumnSnapshot`.

- [ ] **Step 4: Run the source-db tests to verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbColumnAnnotationParserTest" --tests "com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProviderTest"
```

Expected: PASS with explicit field-behavior metadata carried through snapshot creation.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt
git commit -m "feat: add explicit db persistence field behavior carriage"
```

## Task 2: Add Aggregate-Owned Canonical Persistence Controls

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceFieldBehaviorInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Write the failing canonical assembler tests**

Add to `DefaultCanonicalAssemblerTest.kt` cases equivalent to:

```kotlin
@Test
fun `assembler records explicit aggregate persistence field controls`() {
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
                            DbColumnSnapshot("created_by", "VARCHAR", "String", false, insertable = false),
                            DbColumnSnapshot("updated_by", "VARCHAR", "String", false, updatable = false),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                    )
                )
            )
        )
    )

    val controls = result.model.aggregatePersistenceFieldControls

    assertEquals("IDENTITY", controls.single { it.fieldName == "id" }.generatedValueStrategy)
    assertEquals(true, controls.single { it.fieldName == "version" }.version)
    assertEquals(false, controls.single { it.fieldName == "createdBy" }.insertable)
    assertEquals(false, controls.single { it.fieldName == "updatedBy" }.updatable)
}

@Test
fun `assembler does not infer persistence field controls when source is silent`() {
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
                            DbColumnSnapshot("version", "BIGINT", "Long", false),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                    )
                )
            )
        )
    )

    assertTrue(result.model.aggregatePersistenceFieldControls.isEmpty())
}
```

- [ ] **Step 2: Run the core tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: FAIL because canonical persistence controls do not exist yet.

- [ ] **Step 3: Add aggregate-owned canonical persistence control inference**

Extend `PipelineModels.kt` with:

```kotlin
data class AggregatePersistenceFieldControl(
    val entityName: String,
    val fieldName: String,
    val columnName: String,
    val generatedValueStrategy: String? = null,
    val version: Boolean = false,
    val insertable: Boolean? = null,
    val updatable: Boolean? = null,
)
```

and extend `CanonicalModel`:

```kotlin
val aggregatePersistenceFieldControls: List<AggregatePersistenceFieldControl> = emptyList()
```

Create `AggregatePersistenceFieldBehaviorInference.kt` with focused logic equivalent to:

```kotlin
internal object AggregatePersistenceFieldBehaviorInference {
    fun infer(entities: List<EntityModel>, schema: DbSchemaSnapshot?): List<AggregatePersistenceFieldControl> {
        if (schema == null) return emptyList()

        return entities.flatMap { entity ->
            val table = schema.tables.firstOrNull { it.tableName == entity.tableName } ?: return@flatMap emptyList()
            table.columns.mapNotNull { column ->
                val fieldName = toLowerCamelCase(column.name) ?: column.name
                val hasExplicitControl =
                    column.generatedValueStrategy != null || column.version || column.insertable != null || column.updatable != null
                if (!hasExplicitControl) return@mapNotNull null
                AggregatePersistenceFieldControl(
                    entityName = entity.name,
                    fieldName = fieldName,
                    columnName = column.name,
                    generatedValueStrategy = column.generatedValueStrategy,
                    version = column.version,
                    insertable = column.insertable,
                    updatable = column.updatable,
                )
            }
        }
    }
}
```

Wire it into `DefaultCanonicalAssembler.kt` when constructing the final `CanonicalModel`. Do not infer controls when source carriage is absent.

- [ ] **Step 4: Run the core tests to verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: PASS with explicit-only canonical persistence controls.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceFieldBehaviorInference.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: infer aggregate persistence field controls"
```

## Task 3: Surface Persistence Controls Through Planner and Template Rendering

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Write the failing planner and renderer tests**

Add to `AggregateArtifactPlannerTest.kt` a case equivalent to:

```kotlin
@Test
fun `entity planner exposes explicit persistence field behavior in render model`() {
    val plan = AggregateArtifactPlanner().plan(
        aggregateGeneratorConfig(),
        canonicalModel(
            entities = listOf(videoPostEntity()),
            aggregatePersistenceFieldControls = listOf(
                AggregatePersistenceFieldControl("VideoPost", "id", "id", generatedValueStrategy = "IDENTITY"),
                AggregatePersistenceFieldControl("VideoPost", "version", "version", version = true),
                AggregatePersistenceFieldControl("VideoPost", "createdBy", "created_by", insertable = false),
                AggregatePersistenceFieldControl("VideoPost", "updatedBy", "updated_by", updatable = false),
            )
        )
    )

    val entityArtifact = plan.single { it.outputPath.endsWith("/VideoPost.kt") }
    val scalarFields = entityArtifact.renderModel["fields"] as List<Map<String, Any?>>

    assertEquals("IDENTITY", scalarFields.single { it["fieldName"] == "id" }["generatedValueStrategy"])
    assertEquals(true, scalarFields.single { it["fieldName"] == "version" }["isVersion"])
    assertEquals(false, scalarFields.single { it["fieldName"] == "createdBy" }["insertable"])
    assertEquals(true, scalarFields.single { it["fieldName"] == "createdBy" }["updatable"])
    assertEquals(true, scalarFields.single { it["fieldName"] == "updatedBy" }["insertable"])
    assertEquals(false, scalarFields.single { it["fieldName"] == "updatedBy" }["updatable"])
}
```

Add to `PebbleArtifactRendererTest.kt` a renderer case equivalent to:

```kotlin
@Test
fun `aggregate entity template renders explicit persistence field behavior`() {
    val rendered = renderer.render(
        artifact(
            templateId = "aggregate/entity.kt.peb",
            renderModel = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.video_post",
                "imports" to listOf(
                    "jakarta.persistence.Column",
                    "jakarta.persistence.Entity",
                    "jakarta.persistence.GeneratedValue",
                    "jakarta.persistence.GenerationType",
                    "jakarta.persistence.Id",
                    "jakarta.persistence.Table",
                    "jakarta.persistence.Version",
                ),
                "typeName" to "VideoPost",
                "tableName" to "video_post",
                "fields" to listOf(
                    mapOf("fieldName" to "id", "fieldType" to "Long", "columnName" to "id", "isId" to true, "generatedValueStrategy" to "IDENTITY"),
                    mapOf("fieldName" to "version", "fieldType" to "Long", "columnName" to "version", "isVersion" to true),
                    mapOf("fieldName" to "createdBy", "fieldType" to "String", "columnName" to "created_by", "insertable" to false, "updatable" to true),
                    mapOf("fieldName" to "updatedBy", "fieldType" to "String", "columnName" to "updated_by", "insertable" to true, "updatable" to false),
                ),
                "relationFields" to emptyList<Map<String, Any?>>(),
            )
        )
    )

    assertTrue(rendered.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
    assertTrue(rendered.contains("@Version"))
    assertTrue(rendered.contains("@Column(name = \"created_by\", insertable = false, updatable = true)"))
    assertTrue(rendered.contains("@Column(name = \"updated_by\", insertable = true, updatable = false)"))
}
```

- [ ] **Step 2: Run planner and renderer tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: FAIL because persistence field-behavior controls are not yet mapped into render context or template output.

- [ ] **Step 3: Implement planner joins and template emission**

Update `EntityArtifactPlanner.kt` to join `AggregatePersistenceFieldControl` into scalar field render entries. Keep relation fields unchanged.

Use logic equivalent to:

```kotlin
val controlsByField = model.aggregatePersistenceFieldControls
    .filter { it.entityName == entity.name }
    .associateBy { it.fieldName }

val fields = entity.fields.map { field ->
    val control = controlsByField[field.name]
    mapOf(
        "fieldName" to field.name,
        "fieldType" to field.type,
        "columnName" to fieldColumnName(field.name),
        "isId" to (field.name == entity.idField.name),
        "generatedValueStrategy" to control?.generatedValueStrategy,
        "isVersion" to (control?.version == true),
        "insertable" to when {
            control?.insertable != null -> control.insertable
            control?.updatable != null -> true
            else -> null
        },
        "updatable" to when {
            control?.updatable != null -> control.updatable
            control?.insertable != null -> true
            else -> null
        },
    )
}
```

Update `aggregate/entity.kt.peb` so scalar field emission follows:

```pebble
{% if field.isId %}@Id{% endif %}
{% if field.generatedValueStrategy == "IDENTITY" %}
@GeneratedValue(strategy = GenerationType.IDENTITY)
{% endif %}
{% if field.isVersion %}@Version{% endif %}
{% if field.insertable is not null or field.updatable is not null %}
@Column(name = "{{ field.columnName }}", insertable = {{ field.insertable }}, updatable = {{ field.updatable }})
{% else %}
@Column(name = "{{ field.columnName }}")
{% endif %}
val {{ field.fieldName }}: {{ field.fieldType }}
```

Do not emit `@GeneratedValue` for non-id fields. Do not guess unsupported strategies.

- [ ] **Step 4: Run planner and renderer tests to verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: PASS with deterministic render-model mapping and bounded template emission.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: render aggregate persistence field behavior"
```

## Task 4: Add Functional and Compile Coverage for Explicit Field-Behavior Control

**Files:**
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-sample/schema.sql`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-compile-sample/schema.sql`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-compile-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-compile-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-compile-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregatePersistenceCompileSmoke.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Write the failing functional and compile tests**

Add to `PipelinePluginFunctionalTest.kt` a generation case equivalent to:

```kotlin
@Test
fun `aggregate persistence field behavior generation renders explicit field controls`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-persistence-generate")
    copyFixture(projectDir, "aggregate-persistence-sample")

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kGenerate")
        .build()

    val generatedEntity = projectDir.resolve(
        "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
    ).readText()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(generatedEntity.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
    assertTrue(generatedEntity.contains("@Version"))
    assertTrue(generatedEntity.contains("@Column(name = \"created_by\", insertable = false, updatable = true)"))
    assertTrue(generatedEntity.contains("@Column(name = \"updated_by\", insertable = true, updatable = false)"))
}
```

Add to `PipelinePluginCompileFunctionalTest.kt` a compile case equivalent to:

```kotlin
@Test
fun `aggregate persistence field behavior generation participates in domain compileKotlin`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-persistence-compile")
    FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-persistence-compile-sample")

    val beforeGenerateCompileResult = FunctionalFixtureSupport
        .runner(projectDir, ":demo-domain:compileKotlin")
        .buildAndFail()
    assertEquals(TaskOutcome.FAILED, beforeGenerateCompileResult.task(":demo-domain:compileKotlin")?.outcome)

    val (generateResult, compileResult) = FunctionalFixtureSupport.generateThenCompile(
        projectDir,
        ":demo-domain:compileKotlin"
    )

    val generatedEntity = projectDir.resolve(
        "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
    ).readText()

    assertTrue(generatedEntity.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
    assertTrue(generatedEntity.contains("@Version"))
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
}
```

- [ ] **Step 2: Run the functional tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected: FAIL because the dedicated fixtures and field-behavior output do not yet exist.

- [ ] **Step 3: Add dedicated persistence fixtures and wire the tests**

Create `aggregate-persistence-sample/schema.sql` with explicit comment carriage only:

```sql
create table video_post (
    id bigint primary key comment '@GeneratedValue=IDENTITY;',
    version bigint not null comment '@Version=true;',
    created_by varchar(64) comment '@Insertable=false;',
    updated_by varchar(64) comment '@Updatable=false;',
    title varchar(128) not null
);

comment on table video_post is '@AggregateRoot=true;';
```

Create `aggregate-persistence-compile-sample/schema.sql` with the same table definition.

Use the same multi-module Gradle fixture structure already used by aggregate compile fixtures, with:

```kotlin
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

val schemaScriptPath = layout.projectDirectory.file("schema.sql").asFile.absolutePath.replace("\\", "/")
val dbFilePath = layout.buildDirectory.file("h2/demo").get().asFile.absolutePath.replace("\\", "/")

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
            url.set(
                "jdbc:h2:file:$dbFilePath;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;INIT=RUNSCRIPT FROM '$schemaScriptPath'"
            )
            username.set("sa")
            password.set("secret")
            schema.set("PUBLIC")
            includeTables.set(listOf("video_post"))
            excludeTables.set(emptyList())
        }
    }
    generators {
        aggregate {
            enabled.set(true)
            unsupportedTablePolicy.set("SKIP")
        }
    }
}
```

Create `AggregatePersistenceCompileSmoke.kt` as:

```kotlin
package com.acme.demo.domain.aggregates.video_post

class AggregatePersistenceCompileSmoke {
    fun touch(entity: VideoPost) {
        entity.id
        entity.version
        entity.createdBy
        entity.updatedBy
        entity.title
    }
}
```

Wire both new fixtures into the functional tests.

- [ ] **Step 4: Run the functional tests to verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected: PASS with generate and compile coverage for explicit persistence field behavior.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-sample cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-compile-sample cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "test: cover aggregate persistence field behavior"
```

## Task 5: Run Focused Regression and Verify Scope Guards

**Files:**
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Add one final guard for unsupported strategy behavior**

If the earlier parser-only failure coverage is not enough, add a planner-or-functional guard equivalent to:

```kotlin
@Test
fun `aggregate persistence field behavior fails fast for unsupported generated value strategy`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
        DbColumnAnnotationParser.parse("@GeneratedValue=SEQUENCE;")
    }

    assertEquals("unsupported @GeneratedValue strategy in this slice: SEQUENCE", error.message)
}
```

The purpose is to lock that this slice remains bounded to `IDENTITY`.

- [ ] **Step 2: Run the focused multi-module regression**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-db:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Check generated output scope explicitly**

Re-open a generated aggregate entity from the functional fixture and confirm it includes:

```kotlin
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Version
@Column(name = "created_by", insertable = false, updatable = true)
@Column(name = "updated_by", insertable = true, updatable = false)
```

and does not include:

```kotlin
@DynamicInsert
@DynamicUpdate
@GenericGenerator
@SQLDelete
@Where
```

- [ ] **Step 4: Commit any final test-guard adjustments**

```bash
git add cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "test: lock aggregate persistence field behavior scope"
```

- [ ] **Step 5: Final status check before branch handoff**

Run:

```powershell
git status --short
git log --oneline -5
```

Expected:

- no unintended modified files
- a short, reviewable commit stack for source carriage, canonical inference, planner/renderer, and functional coverage
