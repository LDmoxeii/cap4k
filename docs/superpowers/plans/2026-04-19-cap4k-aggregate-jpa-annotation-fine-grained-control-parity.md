# Cap4k Aggregate JPA Annotation Fine-Grained Control Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bounded Jakarta-baseline entity/table/column JPA annotation parity to the aggregate generator without reopening relation semantics or Hibernate-specific control.

**Architecture:** Keep the work on the existing aggregate line. Extend the current `db` source and aggregate canonical assembly with minimal JPA-relevant metadata, map that metadata explicitly through `EntityArtifactPlanner`, and let `aggregate/entity.kt.peb` emit only the bounded Jakarta baseline (`@Entity`, `@Table`, `@Id`, `@Column`, `@Convert`). Verification stays layered: source/parser, canonical, planner/renderer, and bounded functional/compile tests.

**Tech Stack:** Kotlin, JUnit 5, Gradle TestKit, Pebble preset rendering, existing aggregate db source and compile harness

---

## File Structure

### New files

- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateJpaControlInference.kt`

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
  - carry bounded aggregate-owned JPA metadata without polluting generic `FieldModel`

- `DbSchemaSourceProvider.kt`
  - continue surfacing db schema facts needed for bounded JPA table/column control

- `AggregateJpaControlInference.kt`
  - convert source/canonical facts into bounded aggregate JPA metadata before planning

- `DefaultCanonicalAssembler.kt`
  - attach aggregate JPA metadata to entity output in immutable canonical form

- `EntityArtifactPlanner.kt`
  - map bounded entity/table/column JPA metadata into explicit render context

- `aggregate/entity.kt.peb`
  - emit bounded Jakarta baseline annotations only

- aggregate tests
  - lock bounded JPA control behavior without widening into relation-side or Hibernate-specific semantics

## Task 1: Add Bounded Aggregate JPA Metadata to Canonical Assembly

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateJpaControlInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Write the failing canonical tests for bounded JPA metadata**

Add to `DefaultCanonicalAssemblerTest.kt` cases equivalent to:

```kotlin
@Test
fun `assembler records entity table and scalar column jpa metadata`() {
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
                    )
                )
            )
        )
    )

    val entity = result.model.entities.single()
    val jpa = result.model.aggregateEntityJpa.single { it.entityName == "VideoPost" }

    assertEquals("video_post", entity.tableName)
    assertEquals(true, jpa.entityEnabled)
    assertEquals("video_post", jpa.tableName)
    assertEquals("id", jpa.columns.single { it.fieldName == "id" }.columnName)
    assertEquals(true, jpa.columns.single { it.fieldName == "id" }.isId)
    assertEquals("title", jpa.columns.single { it.fieldName == "title" }.columnName)
    assertEquals(false, jpa.columns.single { it.fieldName == "title" }.isId)
}
```

and:

```kotlin
@Test
fun `assembler only assigns converter metadata to stable enum-backed fields`() {
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
                            DbColumnSnapshot(
                                name = "status",
                                dbType = "INT",
                                kotlinType = "Int",
                                nullable = false,
                                typeBinding = "Status",
                            ),
                            DbColumnSnapshot(
                                name = "payload",
                                dbType = "JSON",
                                kotlinType = "String",
                                nullable = false,
                                typeBinding = "SubmitPayload",
                            ),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                    )
                )
            ),
            EnumManifestSnapshot(
                definitions = listOf(
                    SharedEnumDefinition(
                        typeName = "Status",
                        packageName = "shared",
                        generateTranslation = true,
                        items = listOf(EnumItemModel(0, "DRAFT", "Draft")),
                    )
                )
            )
        )
    )

    val entityJpa = result.model.aggregateEntityJpa.single { it.entityName == "VideoPost" }

    assertTrue(
        entityJpa.columns.single { it.fieldName == "status" }.converterTypeFqn
            ?.endsWith(".Status") == true
    )
    assertEquals(
        null,
        entityJpa.columns.single { it.fieldName == "payload" }.converterTypeFqn
    )
}
```

- [ ] **Step 2: Run the canonical tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: FAIL because bounded aggregate JPA metadata does not exist yet.

- [ ] **Step 3: Add the bounded aggregate JPA metadata model**

Update `PipelineModels.kt` with aggregate-owned metadata only. Keep `FieldModel` unchanged.

Add something along these lines:

```kotlin
data class AggregateColumnJpaModel(
    val fieldName: String,
    val columnName: String,
    val isId: Boolean,
    val converterTypeFqn: String? = null,
)

data class AggregateEntityJpaModel(
    val entityName: String,
    val entityPackageName: String,
    val entityEnabled: Boolean,
    val tableName: String,
    val columns: List<AggregateColumnJpaModel>,
)
```

and extend `CanonicalModel`:

```kotlin
data class CanonicalModel(
    entities: List<EntityModel> = emptyList(),
    aggregateEntityJpa: List<AggregateEntityJpaModel> = emptyList(),
)
```

Do not add JPA fields to `FieldModel`.

- [ ] **Step 4: Implement bounded aggregate JPA inference before planning**

Create `AggregateJpaControlInference.kt` and wire it from `DefaultCanonicalAssembler.kt`.

Use logic equivalent to:

```kotlin
internal object AggregateJpaControlInference {
    fun fromModel(
        entities: List<EntityModel>,
        schema: DbSchemaSnapshot?,
        sharedEnums: List<SharedEnumDefinition>,
    ): List<AggregateEntityJpaModel> {
        val sharedEnumTypes = sharedEnums.associateBy { it.typeName }
        val tableByName = schema?.tables?.associateBy { it.tableName.lowercase() }.orEmpty()

        return entities.map { entity ->
            val table = requireNotNull(tableByName[entity.tableName.lowercase()]) {
                "missing db table snapshot for entity ${entity.name}"
            }
            val columnsByName = table.columns.associateBy { it.name.lowercase() }

            AggregateEntityJpaModel(
                entityName = entity.name,
                entityPackageName = entity.packageName,
                entityEnabled = true,
                tableName = entity.tableName,
                columns = entity.fields.map { field ->
                    val column = requireNotNull(columnsByName[field.name.lowercase()]) {
                        "missing db column snapshot for field ${entity.name}.${field.name}"
                    }
                    AggregateColumnJpaModel(
                        fieldName = field.name,
                        columnName = column.name,
                        isId = column.isPrimaryKey,
                        converterTypeFqn = column.typeBinding
                            ?.takeIf { it in sharedEnumTypes || field.enumItems.isNotEmpty() }
                    )
                }
            )
        }
    }
}
```

Bounded rules:
- converter metadata only for stable shared/local enum ownership
- no `@GeneratedValue`, `@Version`, Hibernate-specific metadata
- no relation-side metadata here

- [ ] **Step 5: Run canonical tests to verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: PASS with new bounded JPA metadata assertions green.

- [ ] **Step 6: Commit**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateJpaControlInference.kt
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt
git add cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: add aggregate JPA canonical metadata"
```

### Task 2: Surface Bounded JPA Metadata Through the Aggregate Entity Planner

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

- [ ] **Step 1: Write the failing planner tests for entity/table/scalar-column JPA metadata**

Add to `AggregateArtifactPlannerTest.kt` a case equivalent to:

```kotlin
@Test
fun `entity planner surfaces bounded aggregate JPA metadata`() {
    val plan = AggregateArtifactPlanner().plan(
        aggregateConfig(),
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
                        FieldModel("status", "Status"),
                    ),
                    idField = FieldModel("id", "Long"),
                )
            ),
            aggregateEntityJpa = listOf(
                AggregateEntityJpaModel(
                    entityName = "VideoPost",
                    entityPackageName = "com.acme.demo.domain.aggregates.video_post",
                    entityEnabled = true,
                    tableName = "video_post",
                    columns = listOf(
                        AggregateColumnJpaModel("id", "id", true, null),
                        AggregateColumnJpaModel("title", "title", false, null),
                        AggregateColumnJpaModel(
                            "status",
                            "status",
                            false,
                            "com.acme.demo.domain.shared.enums.Status",
                        ),
                    ),
                )
            )
        )
    )

    val entityItem = plan.single { it.templateId == "aggregate/entity.kt.peb" }
    val entityJpa = entityItem.context["entityJpa"] as Map<String, Any?>
    val scalarFields = entityItem.context["scalarFields"] as List<Map<String, Any?>>

    assertEquals(true, entityJpa["entityEnabled"])
    assertEquals("video_post", entityJpa["tableName"])
    assertEquals(true, scalarFields.single { it["name"] == "id" }["isId"])
    assertEquals("id", scalarFields.single { it["name"] == "id" }["columnName"])
    assertEquals(
        "com.acme.demo.domain.shared.enums.Status",
        scalarFields.single { it["name"] == "status" }["converterTypeRef"]
    )
}
```

- [ ] **Step 2: Run planner tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

Expected: FAIL because the entity planner does not yet surface bounded aggregate JPA metadata.

- [ ] **Step 3: Implement explicit planner mapping**

Update `EntityArtifactPlanner.kt` so the entity plan item carries explicit entity-level JPA metadata and scalar-field JPA metadata.

Use logic equivalent to:

```kotlin
val entityJpa = model.aggregateEntityJpa.singleOrNull {
    it.entityName == entity.name && it.entityPackageName == entity.packageName
}

val scalarJpaByField = entityJpa?.columns.orEmpty().associateBy { it.fieldName }

val scalarFields = entity.fields.map { field ->
    val jpa = requireNotNull(scalarJpaByField[field.name]) {
        "missing aggregate JPA metadata for ${entity.packageName}.${entity.name}.${field.name}"
    }
    mapOf(
        "name" to field.name,
        "type" to field.type,
        "nullable" to field.nullable,
        "defaultValue" to field.defaultValue,
        "columnName" to jpa.columnName,
        "isId" to jpa.isId,
        "converterTypeRef" to jpa.converterTypeFqn,
    )
}

context = mapOf(
    "packageName" to entity.packageName,
    "typeName" to entity.name,
    "comment" to entity.comment,
    "tableName" to entity.tableName,
    "entityJpa" to mapOf(
        "entityEnabled" to (entityJpa?.entityEnabled ?: true),
        "tableName" to (entityJpa?.tableName ?: entity.tableName),
    ),
    "scalarFields" to scalarFields,
    "hasConverterFields" to scalarFields.any { it["converterTypeRef"] != null },
    "relationFields" to relationPlan.relationFields,
    "imports" to relationPlan.imports,
    "jpaImports" to relationPlan.jpaImports,
)
```

Bounded rules:
- planner fails fast if canonical JPA metadata is missing for a scalar field
- no new relation-side JPA controls
- no Hibernate-specific planner metadata

- [ ] **Step 4: Run planner tests to verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

Expected: PASS with explicit bounded JPA render context now visible.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: surface aggregate JPA planner metadata"
```

### Task 3: Render the Jakarta Baseline in the Aggregate Entity Template

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Write the failing renderer tests for bounded Jakarta baseline output**

Add to `PebbleArtifactRendererTest.kt` a case equivalent to:

```kotlin
@Test
fun `aggregate entity preset renders bounded Jakarta baseline annotations`() {
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
                "entityJpa" to mapOf(
                    "entityEnabled" to true,
                    "tableName" to "video_post",
                ),
                "scalarFields" to listOf(
                    mapOf(
                        "name" to "id",
                        "type" to "Long",
                        "nullable" to false,
                        "columnName" to "id",
                        "isId" to true,
                        "converterTypeRef" to null,
                    ),
                    mapOf(
                        "name" to "status",
                        "type" to "Status",
                        "nullable" to false,
                        "columnName" to "status",
                        "isId" to false,
                        "converterTypeRef" to "com.acme.demo.domain.shared.enums.Status",
                    ),
                ),
                "relationFields" to emptyList<Map<String, Any?>>(),
                "imports" to listOf("com.acme.demo.domain.shared.enums.Status"),
                "jpaImports" to emptyList<String>(),
            ),
            conflictPolicy = ConflictPolicy.SKIP,
        ),
    )

    val content = rendered.content

    assertTrue(content.contains("@Entity"))
    assertTrue(content.contains("@Table(name = \"video_post\")"))
    assertTrue(content.contains("@Id"))
    assertTrue(content.contains("@Column(name = \"id\")"))
    assertTrue(content.contains("@Column(name = \"status\")"))
    assertTrue(content.contains("@Convert(converter = Status.Converter::class)"))
    assertFalse(content.contains("@GeneratedValue"))
    assertFalse(content.contains("@Version"))
    assertFalse(content.contains("@DynamicInsert"))
    assertFalse(content.contains("@SQLDelete"))
}
```

- [ ] **Step 2: Run renderer tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: FAIL because the template does not yet emit the bounded Jakarta baseline.

- [ ] **Step 3: Update `aggregate/entity.kt.peb` to render bounded entity/table/scalar-column annotations**

Change the template so it emits:

```pebble
package {{ packageName }}

{% if entityJpa.entityEnabled %}
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
{% endif %}
{% if hasConverterFields %}
import jakarta.persistence.Convert
{% endif %}
{% for import in imports(jpaImports) %}
import {{ import }}
{% endfor %}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

{% if entityJpa.entityEnabled %}@Entity
@Table(name = "{{ entityJpa.tableName }}")
{% endif %}
{% if relationFields|length > 0 %}class{% else %}data class{% endif %} {{ typeName }}(
{% for field in scalarFields %}
    {% if field.isId %}@Id {% endif %}@Column(name = "{{ field.columnName }}")
    {% if field.converterTypeRef %}@Convert(converter = {{ field.converterTypeRef|split('.')|last }}.Converter::class) {% endif %}
    val {{ field.name }}: {{ field.type }}{% if field.nullable %}?{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
){% if relationFields|length > 0 %} {
{% for relation in relationFields %}
{% if relation.relationType == "MANY_TO_ONE" %}
    @ManyToOne(fetch = FetchType.{{ relation.fetchType }})
    @JoinColumn(name = "{{ relation.joinColumn }}")
    {% if relation.nullable %}var {{ relation.name }}: {{ relation.targetTypeRef }}? = null{% else %}lateinit var {{ relation.name }}: {{ relation.targetTypeRef }}{% endif %}
{% elseif relation.relationType == "ONE_TO_ONE" %}
    @OneToOne(fetch = FetchType.{{ relation.fetchType }})
    @JoinColumn(name = "{{ relation.joinColumn }}")
    {% if relation.nullable %}var {{ relation.name }}: {{ relation.targetTypeRef }}? = null{% else %}lateinit var {{ relation.name }}: {{ relation.targetTypeRef }}{% endif %}
{% elseif relation.relationType == "ONE_TO_MANY" %}
    @OneToMany(fetch = FetchType.{{ relation.fetchType }})
    @JoinColumn(name = "{{ relation.joinColumn }}")
    var {{ relation.name }}: List<{{ relation.targetTypeRef }}> = emptyList()
{% endif %}
{% endfor %}
}{% endif %}
```

Keep bounded rules:
- render `class` only when relation fields exist
- render `data class` for scalar-only entities
- no `@GeneratedValue`, `@Version`, Hibernate annotations
- no new relation-side JPA controls

- [ ] **Step 4: Run renderer tests to verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: PASS with bounded Jakarta baseline locked.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb
git add cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: render aggregate Jakarta baseline annotations"
```

### Task 4: Add Functional and Compile Verification for Aggregate JPA Baseline

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Write the failing functional assertion on generated aggregate entity output**

Extend the existing aggregate db functional flow or relation functional flow with assertions equivalent to:

```kotlin
assertTrue(generatedEntity.contains("@Entity"))
assertTrue(generatedEntity.contains("@Table(name = \"video_post\")"))
assertTrue(generatedEntity.contains("@Id"))
assertTrue(generatedEntity.contains("@Column(name = \"id\")"))
assertTrue(generatedEntity.contains("@Column(name = \"status\")"))
assertTrue(generatedEntity.contains("@Convert(converter = Status.Converter::class)"))
assertFalse(generatedEntity.contains("@GeneratedValue"))
assertFalse(generatedEntity.contains("@Version"))
assertFalse(generatedEntity.contains("@DynamicInsert"))
```

Use a fixture that already exercises enum-backed aggregate fields if possible. Reuse instead of creating a new fixture unless reuse proves impossible.

- [ ] **Step 2: Write the failing compile assertion for JPA-baseline aggregate entity output**

Add or extend a compile-functional test so the generated aggregate entity still participates in `:demo-domain:compileKotlin` with the bounded Jakarta baseline present.

Reuse an existing aggregate compile fixture if possible. The compile check should prove:

- generated source includes the bounded JPA annotations
- compile still passes after generation

- [ ] **Step 3: Run gradle functional and compile tests to verify red/green**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected:
- first red while assertions are missing
- then green once bounded JPA baseline output is in place

- [ ] **Step 4: Commit**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "test: cover aggregate JPA baseline parity"
```

### Task 5: Run Full Slice Regression and Verify Scope Boundaries

**Files:**
- Modify only if verification reveals one last missing assertion:
  - `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
  - `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
  - `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
  - `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
  - `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Add one explicit regression that Hibernate-specific control remains out of scope**

If no direct assertion already exists, add one bounded test assertion equivalent to:

```kotlin
assertFalse(content.contains("@DynamicInsert"))
assertFalse(content.contains("@DynamicUpdate"))
assertFalse(content.contains("@SQLDelete"))
assertFalse(content.contains("@Where"))
assertFalse(content.contains("@GenericGenerator"))
```

Prefer adding it to an existing renderer or functional aggregate-entity test rather than creating a redundant new test.

- [ ] **Step 2: Run the focused module regression**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-db:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test
```

Expected: `BUILD SUCCESSFUL`, with no regressions in aggregate relation, enum, unique, wrapper, or factory/specification slices.

- [ ] **Step 3: Inspect git diff for scope leaks**

Run:

```powershell
git diff --stat
git diff --name-only
```

Expected: only the files listed in Tasks 1-4 changed. No bootstrap files, no unrelated design-generator files, no new DSL surfaces.

- [ ] **Step 4: Commit only if one final regression assertion was needed**

If verification exposed one last missing bounded assertion, use:

```bash
git add cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git add cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "test: harden aggregate JPA baseline regressions"
```

If no final assertion was needed, stop after verification without creating a no-op commit.

## Acceptance Checklist

- [ ] bounded entity/table/column JPA control metadata is carried through the existing aggregate source and canonical line
- [ ] `FieldModel` remains scalar-only
- [ ] `EntityArtifactPlanner` surfaces explicit aggregate-owned JPA render metadata
- [ ] `aggregate/entity.kt.peb` renders `@Entity`, `@Table`, `@Id`, `@Column`, and bounded `@Convert`
- [ ] enum-backed scalar fields get converter annotations only from stable shared/local enum ownership
- [ ] scalar-only entities remain `data class` and relation-bearing entities keep the current bounded relation shape
- [ ] relation-side control remains unchanged from the bounded relation slice
- [ ] Hibernate-specific controls remain unsupported in this slice
- [ ] functional generation proves representative bounded Jakarta baseline output
- [ ] compile verification proves generated aggregate entity output still participates in `:demo-domain:compileKotlin`
