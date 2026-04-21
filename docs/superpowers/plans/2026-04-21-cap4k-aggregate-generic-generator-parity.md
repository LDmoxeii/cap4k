# Cap4k Aggregate Generic-Generator Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bounded aggregate custom id-generator parity so explicit table-level `@IdGenerator=...` truth can produce old custom-generator entity id output with `@GeneratedValue(generator = "...")` and `@GenericGenerator(name = "...", strategy = "...")` while preserving the existing `IDENTITY` fallback path.

**Architecture:** Keep this slice on the existing aggregate persistence line. Add one bounded table-level `entityIdGenerator` source field, map it into a dedicated aggregate-owned canonical control via a new inference helper, join that control inside `EntityArtifactPlanner`, and keep `aggregate/entity.kt.peb` mechanical by rendering either the custom-generator path or the existing `IDENTITY` path, never both. Reuse current aggregate persistence and provider-specific fixtures, mutating schema text in tests instead of adding a new public DSL or a broad id-generation subsystem.

**Tech Stack:** Kotlin, JUnit 5, Gradle TestKit, Pebble preset rendering, existing aggregate db source and canonical pipeline

---

## File Structure

### New files

- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdGeneratorInference.kt`

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

### Existing fixtures to reuse

- Reuse and mutate inside tests: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/schema.sql`
- Reuse and mutate inside tests: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/schema.sql`

### Responsibilities

- `PipelineModels.kt`
  - add bounded table-level source carriage and aggregate-owned canonical id-generator control

- `DbTableAnnotationParser.kt`
  - parse explicit table-level `@IdGenerator=<value>` truth and strip it from cleaned comments

- `DbSchemaSourceProvider.kt`
  - persist parsed `entityIdGenerator` onto `DbTableSnapshot`

- `AggregateIdGeneratorInference.kt`
  - derive `AggregateIdGeneratorControl` only for eligible entities

- `DefaultCanonicalAssembler.kt`
  - invoke the new inference helper and expose results on `CanonicalModel`

- `EntityArtifactPlanner.kt`
  - join id-generator control into scalar id-field render context and keep custom-generator and identity paths mutually exclusive

- `aggregate/entity.kt.peb`
  - render bounded `@GeneratedValue(generator = "...")` and `@GenericGenerator(...)` mechanically when planner context requests it

- renderer, functional, and compile tests
  - prove custom-generator path exists only when explicit source truth is present
  - prove default identity path remains unchanged

## Task 1: Add Explicit Table-Level Id-Generator Source Carriage

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParser.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParserTest.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`

- [ ] **Step 1: Write the failing parser tests**

Add to `DbTableAnnotationParserTest.kt`:

```kotlin
@Test
fun `parser extracts bounded entity id generator from table comment`() {
    val metadata = DbTableAnnotationParser.parse(
        "@AggregateRoot=true;@IdGenerator=snowflakeIdGenerator;"
    )

    assertEquals(true, metadata.aggregateRoot)
    assertEquals("snowflakeIdGenerator", metadata.entityIdGenerator)
}

@Test
fun `parser rejects blank entity id generator value`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
        DbTableAnnotationParser.parse("@IdGenerator=   ;")
    }

    assertEquals("invalid @IdGenerator value: ", error.message)
}

@Test
fun `parser rejects conflicting duplicate entity id generator annotations`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
        DbTableAnnotationParser.parse(
            "@IdGenerator=snowflakeIdGenerator;@IdGenerator=uuidGenerator;"
        )
    }

    assertEquals(
        "conflicting @IdGenerator annotations on the same table comment.",
        error.message
    )
}
```

Add to `DbSchemaSourceProviderTest.kt`:

```kotlin
@Test
fun `provider carries table level entity id generator into db snapshot`() {
    val url = "jdbc:h2:mem:cap4k-db-source-id-generator;MODE=MySQL;DB_CLOSE_DELAY=-1"
    DriverManager.getConnection(url, "sa", "").use { connection ->
        connection.createStatement().use { statement ->
            statement.execute(
                """
                create table video_post (
                    id bigint primary key,
                    title varchar(64) not null
                );
                """.trimIndent()
            )
            statement.execute(
                "comment on table video_post is '@AggregateRoot=true;@IdGenerator=snowflakeIdGenerator;'"
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

    val table = snapshot.tables.single { it.tableName.equals("VIDEO_POST", true) }

    assertEquals("snowflakeIdGenerator", table.entityIdGenerator)
}
```

- [ ] **Step 2: Run source-db tests to verify failure**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbTableAnnotationParserTest" --tests "com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProviderTest"
```

Expected:

- FAIL because `DbTableAnnotationParseResult` and `DbTableSnapshot` do not yet expose `entityIdGenerator`
- FAIL because parser does not yet recognize `@IdGenerator`

- [ ] **Step 3: Add the bounded source carriage**

Modify `PipelineModels.kt`:

```kotlin
data class DbTableSnapshot(
    val tableName: String,
    val comment: String,
    val columns: List<DbColumnSnapshot>,
    val primaryKey: List<String>,
    val uniqueConstraints: List<List<String>>,
    val parentTable: String? = null,
    val aggregateRoot: Boolean = true,
    val valueObject: Boolean = false,
    val dynamicInsert: Boolean? = null,
    val dynamicUpdate: Boolean? = null,
    val softDeleteColumn: String? = null,
    val entityIdGenerator: String? = null,
)
```

Modify `DbTableAnnotationParser.kt`:

```kotlin
private val providerAliases = setOf(
    "DYNAMICINSERT",
    "DYNAMICUPDATE",
    "SOFTDELETECOLUMN",
    "IDGENERATOR",
)

val entityIdGenerator = resolveAnnotationValue(
    annotations = annotations,
    aliases = setOf("IDGENERATOR"),
    conflictMessage = "conflicting @IdGenerator annotations on the same table comment.",
    blankValueMessage = "invalid @IdGenerator value: ",
    missingValueMessage = "invalid @IdGenerator value: ",
)?.trim()

return DbTableAnnotationParseResult(
    parentTable = parentTable,
    aggregateRoot = aggregateRootAnnotation.value ?: (parentTable == null),
    valueObject = valueObject,
    dynamicInsert = dynamicInsert,
    dynamicUpdate = dynamicUpdate,
    softDeleteColumn = softDeleteColumn,
    entityIdGenerator = entityIdGenerator,
    cleanedComment = stripRecognizedAnnotations(comment, tableAliases + providerAliases),
)
```

Also extend `DbTableAnnotationParseResult`:

```kotlin
internal data class DbTableAnnotationParseResult(
    val parentTable: String? = null,
    val aggregateRoot: Boolean = true,
    val valueObject: Boolean = false,
    val dynamicInsert: Boolean? = null,
    val dynamicUpdate: Boolean? = null,
    val softDeleteColumn: String? = null,
    val entityIdGenerator: String? = null,
    val cleanedComment: String = "",
)
```

Modify `DbSchemaSourceProvider.kt` table construction:

```kotlin
return DbTableSnapshot(
    tableName = tableName,
    comment = tableMetadata.cleanedComment,
    columns = columns,
    primaryKey = primaryKey,
    uniqueConstraints = uniqueConstraints,
    parentTable = tableMetadata.parentTable,
    aggregateRoot = tableMetadata.aggregateRoot,
    valueObject = tableMetadata.valueObject,
    dynamicInsert = tableMetadata.dynamicInsert,
    dynamicUpdate = tableMetadata.dynamicUpdate,
    softDeleteColumn = tableMetadata.softDeleteColumn,
    entityIdGenerator = tableMetadata.entityIdGenerator,
)
```

- [ ] **Step 4: Re-run the focused source-db tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbTableAnnotationParserTest" --tests "com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProviderTest"
```

Expected:

- PASS
- parser now carries `entityIdGenerator`
- invalid and conflicting cases fail with the exact messages above

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParser.kt cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParserTest.kt cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt
git commit -m "feat: add aggregate entity id generator source carriage"
```

## Task 2: Assemble and Plan Bounded Aggregate Id-Generator Control

**Files:**
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdGeneratorInference.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

- [ ] **Step 1: Write the failing canonical and planner tests**

Add to `DefaultCanonicalAssemblerTest.kt`:

```kotlin
@Test
fun `assembler derives aggregate id generator control for eligible entity`() {
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
                            DbColumnSnapshot("title", "VARCHAR", "String", false),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                        entityIdGenerator = "snowflakeIdGenerator",
                    )
                )
            )
        )
    )

    val control = result.model.aggregateIdGeneratorControls.single()

    assertEquals("VideoPost", control.entityName)
    assertEquals("com.acme.demo.domain.aggregates.video_post", control.entityPackageName)
    assertEquals("video_post", control.tableName)
    assertEquals("id", control.idFieldName)
    assertEquals("snowflakeIdGenerator", control.entityIdGenerator)
}

@Test
fun `assembler does not derive aggregate id generator control for value object`() {
    val result = DefaultCanonicalAssembler().assemble(
        aggregateProjectConfig(),
        listOf(
            DbSchemaSnapshot(
                tables = listOf(
                    DbTableSnapshot(
                        tableName = "video_post_item",
                        comment = "",
                        columns = listOf(
                            DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                        valueObject = true,
                        entityIdGenerator = "snowflakeIdGenerator",
                    )
                )
            )
        )
    )

    assertTrue(result.model.aggregateIdGeneratorControls.isEmpty())
}
```

Add to `AggregateArtifactPlannerTest.kt`:

```kotlin
@Test
fun `entity planner exposes custom generator render keys on id field`() {
    val entity = EntityModel(
        name = "VideoPost",
        packageName = "com.acme.demo.domain.aggregates.video_post",
        tableName = "video_post",
        comment = "video post",
        fields = listOf(FieldModel("id", "Long"), FieldModel("title", "String")),
        idField = FieldModel("id", "Long"),
    )

    val plan = AggregateArtifactPlanner().plan(
        aggregateConfig(),
        CanonicalModel(
            entities = listOf(entity),
            aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
            aggregateIdGeneratorControls = listOf(
                AggregateIdGeneratorControl(
                    entityName = "VideoPost",
                    entityPackageName = entity.packageName,
                    tableName = "video_post",
                    idFieldName = "id",
                    entityIdGenerator = "snowflakeIdGenerator",
                )
            ),
        )
    )

    val entityArtifact = plan.single { it.outputPath.endsWith("/VideoPost.kt") }
    @Suppress("UNCHECKED_CAST")
    val scalarFields = entityArtifact.context["scalarFields"] as List<Map<String, Any?>>
    val idField = scalarFields.single { it["fieldName"] == "id" }

    assertEquals("snowflakeIdGenerator", idField["generatedValueGenerator"])
    assertEquals("snowflakeIdGenerator", idField["genericGeneratorName"])
    assertEquals("snowflakeIdGenerator", idField["genericGeneratorStrategy"])
    assertEquals(false, entityArtifact.context["hasGeneratedValueFields"])
    assertEquals(true, entityArtifact.context["hasGenericGeneratorFields"])
}
```

- [ ] **Step 2: Run core and aggregate planner tests to verify failure**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest" :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

Expected:

- FAIL because `CanonicalModel` has no `aggregateIdGeneratorControls`
- FAIL because `EntityArtifactPlanner` does not yet expose custom generator render keys

- [ ] **Step 3: Add canonical control model and inference helper**

Extend `PipelineModels.kt`:

```kotlin
data class AggregateIdGeneratorControl(
    val entityName: String,
    val entityPackageName: String,
    val tableName: String,
    val idFieldName: String,
    val entityIdGenerator: String,
)
```

Also add to `CanonicalModel`:

```kotlin
val aggregateIdGeneratorControls: List<AggregateIdGeneratorControl> = emptyList(),
```

Create `AggregateIdGeneratorInference.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateIdGeneratorControl
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot

internal object AggregateIdGeneratorInference {
    fun infer(
        entities: List<EntityModel>,
        tables: List<DbTableSnapshot>,
    ): List<AggregateIdGeneratorControl> {
        val tablesByName = tables.associateBy { it.tableName }

        return entities.mapNotNull { entity ->
            val table = tablesByName.getValue(entity.tableName)
            val generator = table.entityIdGenerator?.trim().orEmpty()

            when {
                generator.isBlank() -> null
                entity.valueObject -> null
                else -> AggregateIdGeneratorControl(
                    entityName = entity.name,
                    entityPackageName = entity.packageName,
                    tableName = entity.tableName,
                    idFieldName = entity.idField.name,
                    entityIdGenerator = generator,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Wire assembler and planner**

Modify `DefaultCanonicalAssembler.kt`:

```kotlin
val aggregateIdGeneratorControls = AggregateIdGeneratorInference.infer(
    entities = entities,
    tables = supportedTables,
)
```

Then pass it into `CanonicalModel(...)`:

```kotlin
aggregateIdGeneratorControls = aggregateIdGeneratorControls,
```

Modify `EntityArtifactPlanner.kt` scalar-field mapping:

```kotlin
val idGeneratorControl = model.aggregateIdGeneratorControls.firstOrNull {
    it.entityName == entity.name && it.entityPackageName == entity.packageName
}
```

Inside the scalar-field map:

```kotlin
val isCustomGeneratorIdField =
    jpa.isId && idGeneratorControl?.idFieldName == field.name

mapOf(
    "fieldName" to field.name,
    "fieldType" to fieldType,
    "name" to field.name,
    "type" to fieldType,
    "nullable" to field.nullable,
    "defaultValue" to field.defaultValue,
    "typeBinding" to field.typeBinding,
    "enumItems" to field.enumItems,
    "columnName" to jpa.columnName,
    "isId" to jpa.isId,
    "converterTypeRef" to jpa.converterTypeFqn,
    "generatedValueStrategy" to if (isCustomGeneratorIdField) null else control?.generatedValueStrategy,
    "generatedValueGenerator" to if (isCustomGeneratorIdField) idGeneratorControl?.entityIdGenerator else null,
    "genericGeneratorName" to if (isCustomGeneratorIdField) idGeneratorControl?.entityIdGenerator else null,
    "genericGeneratorStrategy" to if (isCustomGeneratorIdField) idGeneratorControl?.entityIdGenerator else null,
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
```

And add a new context key:

```kotlin
"hasGenericGeneratorFields" to scalarFields.any { it["genericGeneratorName"] != null },
```

- [ ] **Step 5: Re-run core and aggregate planner tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest" :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

Expected:

- PASS
- assembler now emits bounded `AggregateIdGeneratorControl`
- planner now exposes mutually-exclusive custom-generator render keys

- [ ] **Step 6: Commit**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdGeneratorInference.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: add aggregate generic generator planning"
```

## Task 3: Render Bounded Generic-Generator Output in Aggregate Entity Template

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Write the failing renderer tests**

Add to `PebbleArtifactRendererTest.kt`:

```kotlin
@Test
fun `aggregate entity template renders bounded custom generator annotations`() {
    val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-generic-generator")
    val renderer = PebbleArtifactRenderer(
        templateResolver = PresetTemplateResolver(
            preset = "ddd-default",
            overrideDirs = listOf(overrideDir.toString())
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
                    "comment" to "video post",
                    "entityJpa" to mapOf("entityEnabled" to true, "tableName" to "video_post"),
                    "hasConverterFields" to false,
                    "hasGeneratedValueFields" to false,
                    "hasGenericGeneratorFields" to true,
                    "hasVersionFields" to false,
                    "scalarFields" to listOf(
                        mapOf(
                            "fieldName" to "id",
                            "fieldType" to "Long",
                            "name" to "id",
                            "type" to "Long",
                            "columnName" to "id",
                            "isId" to true,
                            "generatedValueGenerator" to "snowflakeIdGenerator",
                            "genericGeneratorName" to "snowflakeIdGenerator",
                            "genericGeneratorStrategy" to "snowflakeIdGenerator",
                        ),
                        mapOf(
                            "fieldName" to "title",
                            "fieldType" to "String",
                            "name" to "title",
                            "type" to "String",
                            "columnName" to "title",
                        ),
                    ),
                    "fields" to emptyList<Map<String, Any?>>(),
                    "relationFields" to emptyList<Map<String, Any?>>(),
                    "imports" to emptyList<String>(),
                    "jpaImports" to emptyList<String>(),
                ),
                conflictPolicy = ConflictPolicy.SKIP,
            )
        ),
        config = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = emptyMap(),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString()),
                conflictPolicy = ConflictPolicy.SKIP
            )
        )
    )

    val content = rendered.single().content

    assertTrue(content.contains("import org.hibernate.annotations.GenericGenerator"))
    assertTrue(content.contains("@GeneratedValue(generator = \"snowflakeIdGenerator\")"))
    assertTrue(
        content.contains(
            "@GenericGenerator(name = \"snowflakeIdGenerator\", strategy = \"snowflakeIdGenerator\")"
        )
    )
    assertFalse(content.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
}
```

Also strengthen the existing plain aggregate entity test so it keeps asserting:

```kotlin
assertFalse(content.contains("@GenericGenerator"))
```

- [ ] **Step 2: Run renderer tests to verify failure**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected:

- FAIL because template and context gating do not yet support `hasGenericGeneratorFields`
- FAIL because `@GeneratedValue(generator = ...)` and `@GenericGenerator(...)` are not rendered

- [ ] **Step 3: Update the aggregate entity template**

Modify `entity.kt.peb` import section:

```pebble
{% if hasGeneratedValueFields or hasGenericGeneratorFields %}
import jakarta.persistence.GeneratedValue
{% endif %}
{% if hasGeneratedValueFields %}
import jakarta.persistence.GenerationType
{% endif %}
{% if hasGenericGeneratorFields %}
import org.hibernate.annotations.GenericGenerator
{% endif %}
```

Modify id-field annotation emission:

```pebble
{% if field.isId %}@Id {% endif %}
{% if field.isId and field.generatedValueGenerator %}@GeneratedValue(generator = "{{ field.generatedValueGenerator }}")
    @GenericGenerator(name = "{{ field.genericGeneratorName }}", strategy = "{{ field.genericGeneratorStrategy }}")
{% elseif field.isId and field.generatedValueStrategy == "IDENTITY" %}@GeneratedValue(strategy = GenerationType.IDENTITY)
{% endif %}
```

Keep the rest of the scalar-field `@Column(...)` and `@Convert(...)` output unchanged.

- [ ] **Step 4: Re-run renderer tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected:

- PASS
- custom-generator context renders bounded provider import and annotations
- default entity rendering still keeps `@GenericGenerator` absent

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: render aggregate generic generator annotations"
```

## Task 4: Prove End-to-End Generate and Compile Closure for Both Id Paths

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Write the failing functional and compile tests**

Add to `PipelinePluginFunctionalTest.kt`:

```kotlin
@OptIn(ExperimentalPathApi::class)
@Test
fun `aggregate generic generator generation renders bounded custom id annotations`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-generic-generator")
    copyFixture(projectDir, "aggregate-provider-persistence-sample")

    projectDir.resolve("schema.sql").writeText(
        """
        create table video_post (
            id bigint primary key comment '@GeneratedValue=IDENTITY;',
            title varchar(128) not null
        );
        comment on table video_post is '@AggregateRoot=true;@IdGenerator=snowflakeIdGenerator;';

        create table audit_log (
            id bigint primary key comment '@GeneratedValue=IDENTITY;',
            content varchar(128) not null
        );
        comment on table audit_log is '@AggregateRoot=true;';
        """.trimIndent()
    )

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kGenerate")
        .build()

    val generatedVideoPost = projectDir.resolve(
        "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
    ).readText()
    val generatedAuditLog = projectDir.resolve(
        "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/audit_log/AuditLog.kt"
    ).readText()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(generatedVideoPost.contains("@GeneratedValue(generator = \"snowflakeIdGenerator\")"))
    assertTrue(
        generatedVideoPost.contains(
            "@GenericGenerator(name = \"snowflakeIdGenerator\", strategy = \"snowflakeIdGenerator\")"
        )
    )
    assertFalse(generatedVideoPost.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
    assertTrue(generatedAuditLog.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
    assertFalse(generatedAuditLog.contains("@GenericGenerator"))
}
```

Add to `PipelinePluginCompileFunctionalTest.kt`:

```kotlin
@Test
fun `aggregate generic generator generation participates in domain compileKotlin`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-generic-generator-compile")
    FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-provider-persistence-compile-sample")

    projectDir.resolve("schema.sql").writeText(
        """
        create table video_post (
            id bigint primary key comment '@GeneratedValue=IDENTITY;',
            title varchar(128) not null
        );
        comment on table video_post is '@AggregateRoot=true;@IdGenerator=snowflakeIdGenerator;';

        create table audit_log (
            id bigint primary key comment '@GeneratedValue=IDENTITY;',
            content varchar(128) not null
        );
        comment on table audit_log is '@AggregateRoot=true;';
        """.trimIndent()
    )

    val beforeGenerateCompileResult = FunctionalFixtureSupport
        .runner(projectDir, ":demo-domain:compileKotlin")
        .buildAndFail()
    assertEquals(TaskOutcome.FAILED, beforeGenerateCompileResult.task(":demo-domain:compileKotlin")?.outcome)

    val (generateResult, compileResult) = FunctionalFixtureSupport.generateThenCompile(
        projectDir,
        ":demo-domain:compileKotlin"
    )

    val generatedVideoPost = projectDir.resolve(
        "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
    ).readText()
    val generatedAuditLog = projectDir.resolve(
        "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/audit_log/AuditLog.kt"
    ).readText()

    assertTrue(generatedVideoPost.contains("@GenericGenerator"))
    assertTrue(generatedVideoPost.contains("@GeneratedValue(generator = \"snowflakeIdGenerator\")"))
    assertTrue(generatedAuditLog.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
    assertFalse(generatedAuditLog.contains("@GenericGenerator"))
    assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
}
```

- [ ] **Step 2: Run the gradle functional tests to verify failure**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected:

- FAIL because generated aggregate entities still only know the `IDENTITY` path
- FAIL because `@GenericGenerator` imports and annotations are still missing from generated output

- [ ] **Step 3: Align functional coverage with the finalized planner context gating**

Ensure `EntityArtifactPlanner.kt` exports the exact booleans consumed by the renderer and exercised by the new functional tests:

```kotlin
"hasGeneratedValueFields" to scalarFields.any {
    it["isId"] == true && it["generatedValueStrategy"] == "IDENTITY"
},
"hasGenericGeneratorFields" to scalarFields.any {
    it["isId"] == true && it["genericGeneratorName"] != null
},
```

Do not add any other fallback path. Keep the default identity entity on the existing code path and the custom-generator entity only on the explicit table-truth path.

- [ ] **Step 4: Re-run the full focused regression for this slice**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-db:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test
```

Expected:

- PASS
- source parser tests, canonical assembly tests, planner tests, renderer tests, functional generate tests, and compile tests all pass together

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "test: cover aggregate generic generator parity"
```

## Final Verification

- [ ] **Step 1: Run the final focused regression on a clean worktree state**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-db:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test
```

Expected:

- all five tasks complete with `BUILD SUCCESSFUL`

- [ ] **Step 2: Re-run the dedicated end-to-end generic-generator tests alone**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected:

- PASS
- functional assertions prove custom-generator and identity entities coexist in the same generated project

- [ ] **Step 3: Prepare merge-ready summary**

Summarize:

- source carriage added for explicit table-level `entityIdGenerator`
- canonical aggregate id-generator control added
- planner now splits custom-generator and identity paths cleanly
- renderer emits bounded `@GeneratedValue(generator = ...)` and `@GenericGenerator(...)`
- functional and compile coverage prove both paths coexist safely
