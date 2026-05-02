# Aggregate Unique Family Naming Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> Status: Implemented. This is now a historical execution plan; do not treat unchecked checklist items as pending work. Current completion status is tracked in `docs/superpowers/mainline-roadmap.md` and the implemented spec.

**Goal:** Make aggregate unique query, query handler, and validator names derive from DB unique names (`uk`, `uk_v_<fragment>`, `<table>_uk`, `<table>_uk_v_<fragment>`) while filtering soft-delete and version control fields from generated business APIs.

**Architecture:** Preserve named unique constraints from DB source collection into canonical aggregate entities, then centralize all unique family naming in `AggregateUniqueConstraintPlanning`. Artifact planners consume the shared resolved selection and add review metadata to `cap4kPlan` contexts.

**Tech Stack:** Kotlin, Gradle, JUnit 5, Gradle TestKit, H2 JDBC metadata, cap4k pipeline API/core/source/generator/renderer modules.

---

## Spec Reference

Implement:

- `docs/superpowers/specs/2026-05-03-cap4k-aggregate-unique-family-naming-contract-design.md`

Do not implement:

- Gradle DSL unique naming overrides
- legacy `cap4k-plugin-codegen` changes
- unique SQL or handler persistence redesign
- family-level conflict policy overrides

## File Structure

Planned changes:

- Modify `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
  - Add the named unique constraint model.
  - Change DB and canonical aggregate entity models to carry named unique constraints.
- Modify `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
  - Preserve JDBC `INDEX_NAME` as `physicalName`.
  - Preserve ordered unique columns.
- Modify `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
  - Carry named unique constraints from DB tables into canonical entities.
- Modify `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceProviderInference.kt`
  - Produce provider control when a table has an explicit version field even if there is no soft-delete or dynamic insert/update setting.
- Modify `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateUniqueConstraintPlanning.kt`
  - Normalize unique physical names.
  - Resolve suffixes.
  - Filter soft-delete and version fields.
  - Fail fast on invalid or colliding generated names.
- Modify `UniqueQueryArtifactPlanner.kt`, `UniqueQueryHandlerArtifactPlanner.kt`, and `UniqueValidatorArtifactPlanner.kt`
  - Pass provider controls into the shared planner.
  - Add unique naming review metadata to artifact contexts.
- Modify tests in:
  - `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
  - `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`
  - `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
  - `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
  - `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/schema.sql`

## Task Overview

1. Preserve unique physical names in DB source snapshots while keeping canonical output temporarily column-based.
2. Carry named unique constraints into canonical entity models and keep old fallback generation compiling.
3. Implement unique name normalization, control-field filtering, and collision checks in the shared planner.
4. Wire provider controls and plan metadata into unique query, handler, and validator artifact planners.
5. Add Gradle functional coverage for H2-style table-prefixed unique names and plan metadata.
6. Run the targeted verification set and update spec status.

### Task 1: Preserve Unique Physical Names In DB Source Snapshots

**Files:**

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Write the API model expectation**

In `PipelineModelsTest.kt`, update `db schema snapshot preserves normalized table metadata` so the unique constraint assertion expects a physical name.

Use this shape:

```kotlin
uniqueConstraints = listOf(
    UniqueConstraintModel(
        physicalName = "uk_v_id",
        columns = listOf("id"),
    )
),
```

Add this assertion after the primary key assertion:

```kotlin
assertEquals("uk_v_id", snapshot.tables.single().uniqueConstraints.single().physicalName)
assertEquals(listOf("id"), snapshot.tables.single().uniqueConstraints.single().columns)
```

- [ ] **Step 2: Write the DB source test for named H2 constraints**

In `DbSchemaSourceProviderTest.kt`, update the existing `collects tables columns primary keys unique constraints and comments` test to use an explicit named unique constraint:

```sql
constraint video_post_uk_v_slug unique (slug),
```

Replace the old assertion:

```kotlin
assertEquals(listOf(listOf("SLUG")), table.uniqueConstraints)
```

with:

```kotlin
val unique = table.uniqueConstraints.single()
assertTrue(unique.physicalName.equals("VIDEO_POST_UK_V_SLUG", ignoreCase = true))
assertEquals(listOf("SLUG"), unique.columns)
```

Also update `collects quoted mixed case table metadata` with:

```sql
constraint "VideoPost_uk_v_slug" unique ("Slug"),
```

and assert:

```kotlin
val unique = table.uniqueConstraints.single()
assertEquals("VideoPost_uk_v_slug", unique.physicalName)
assertEquals(listOf("Slug"), unique.columns)
```

- [ ] **Step 3: Run the source/API tests and confirm they fail**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test --tests "*PipelineModelsTest*"
.\gradlew.bat :cap4k-plugin-pipeline-source-db:test --tests "*DbSchemaSourceProviderTest*"
```

Expected: compilation fails because `UniqueConstraintModel` does not exist and `DbTableSnapshot.uniqueConstraints` still expects `List<List<String>>`.

- [ ] **Step 4: Add the named unique constraint API model**

In `PipelineModels.kt`, add this model after `DbColumnSnapshot`:

```kotlin
data class UniqueConstraintModel(
    val physicalName: String,
    val columns: List<String>,
)
```

Change `DbTableSnapshot` from:

```kotlin
val uniqueConstraints: List<List<String>>,
```

to:

```kotlin
val uniqueConstraints: List<UniqueConstraintModel>,
```

- [ ] **Step 5: Preserve `INDEX_NAME` in the DB source provider**

In `DbSchemaSourceProvider.kt`, import the model:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.UniqueConstraintModel
```

Replace the unique constraint mapping tail with:

```kotlin
            }.entries
                .map { (physicalName, columns) ->
                    UniqueConstraintModel(
                        physicalName = physicalName,
                        columns = columns
                            .sortedWith(
                                compareBy<IndexedConstraintColumn> {
                                    if (it.ordinalPosition > 0) it.ordinalPosition else Int.MAX_VALUE
                                }.thenBy { it.metadataSequence }
                            )
                            .map { it.name },
                    )
                }
                .filter { it.columns.toSet() != primaryKeySet }
```

Keep the existing `linkedMapOf<String, MutableList<IndexedConstraintColumn>>()` grouping and the existing primary-key filtering behavior.

- [ ] **Step 6: Keep canonical output temporarily column-based**

In `DefaultCanonicalAssembler.kt`, keep `EntityModel.uniqueConstraints` unchanged for this task by mapping the named DB constraints back to columns:

```kotlin
uniqueConstraints = table.uniqueConstraints.map { it.columns },
```

This temporary bridge lets source/API changes compile before Task 2 changes canonical entities.

- [ ] **Step 7: Update direct `DbTableSnapshot` test constructors**

Run:

```powershell
rg -n "uniqueConstraints = listOf\(listOf" cap4k\cap4k-plugin-pipeline-api cap4k\cap4k-plugin-pipeline-core cap4k\cap4k-plugin-pipeline-source-db
```

For each `DbTableSnapshot` constructor hit, replace `listOf(listOf("column"))` with:

```kotlin
listOf(
    UniqueConstraintModel(
        physicalName = "uk_v_column",
        columns = listOf("column"),
    )
)
```

Use the actual column name in `physicalName`. For example:

```kotlin
uniqueConstraints = listOf(
    UniqueConstraintModel(
        physicalName = "uk_v_title",
        columns = listOf("title"),
    )
),
```

Add this import where needed:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.UniqueConstraintModel
```

- [ ] **Step 8: Run the tests and confirm Task 1 passes**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test --tests "*PipelineModelsTest*"
.\gradlew.bat :cap4k-plugin-pipeline-source-db:test --tests "*DbSchemaSourceProviderTest*"
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest*"
```

Expected: all three commands exit with `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit Task 1**

Run:

```powershell
git add cap4k-plugin-pipeline-api cap4k-plugin-pipeline-source-db cap4k-plugin-pipeline-core
git commit -m "Preserve DB unique constraint names"
```

### Task 2: Carry Named Unique Constraints Into Canonical Entities

**Files:**

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceProviderInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateUniqueConstraintPlanning.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

- [ ] **Step 1: Add a core test for named canonical unique constraints**

In `DefaultCanonicalAssemblerTest.kt`, update `maps db schema snapshot into schema entity and repository models`.

Use this input:

```kotlin
uniqueConstraints = listOf(
    UniqueConstraintModel(
        physicalName = "video_post_uk_v_title",
        columns = listOf("title"),
    )
),
```

Replace the old assertion:

```kotlin
assertEquals(listOf(listOf("title")), model.entities.single().uniqueConstraints)
```

with:

```kotlin
val unique = model.entities.single().uniqueConstraints.single()
assertEquals("video_post_uk_v_title", unique.physicalName)
assertEquals(listOf("title"), unique.columns)
```

- [ ] **Step 2: Add a core test for version-only provider controls**

In `DefaultCanonicalAssemblerTest.kt`, add this test near the existing provider control tests:

```kotlin
@Test
fun `assembler infers provider control for explicit version column without provider specific settings`() {
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
                            DbColumnSnapshot("version", "BIGINT", "Long", false, version = true),
                            DbColumnSnapshot("slug", "VARCHAR", "String", false),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                    )
                )
            )
        )
    )

    val control = result.model.aggregatePersistenceProviderControls.single()
    assertEquals("VideoPost", control.entityName)
    assertEquals("id", control.idFieldName)
    assertEquals("version", control.versionFieldName)
    assertEquals(null, control.softDeleteColumn)
    assertEquals(null, control.dynamicInsert)
    assertEquals(null, control.dynamicUpdate)
}
```

Expected initial failure: `aggregatePersistenceProviderControls` is empty because `AggregatePersistenceProviderInference` currently skips version-only tables.

- [ ] **Step 3: Change canonical `EntityModel` to keep named constraints**

In `PipelineModels.kt`, change `EntityModel` from:

```kotlin
val uniqueConstraints: List<List<String>> = emptyList(),
```

to:

```kotlin
val uniqueConstraints: List<UniqueConstraintModel> = emptyList(),
```

In `DefaultCanonicalAssembler.kt`, replace the temporary bridge from Task 1:

```kotlin
uniqueConstraints = table.uniqueConstraints.map { it.columns },
```

with:

```kotlin
uniqueConstraints = table.uniqueConstraints,
```

- [ ] **Step 4: Keep old unique planning behavior compiling with named constraints**

In `AggregateUniqueConstraintPlanning.kt`, change the planner loop from:

```kotlin
return entity.uniqueConstraints.map { columns ->
    val selectedFields = selectConstraintFields(entity, columns)
```

to:

```kotlin
return entity.uniqueConstraints.map { constraint ->
    val selectedFields = selectConstraintFields(entity, constraint.columns)
```

Do not change suffix behavior yet. Task 3 owns naming semantics.

- [ ] **Step 5: Make version-only provider controls explicit**

In `AggregatePersistenceProviderInference.kt`, compute version columns before the early return:

```kotlin
val versionColumns = table.columns.filter { it.version == true }
require(versionColumns.size <= 1) {
    "multiple explicit version columns found for table ${table.tableName}"
}
if (
    table.dynamicInsert == null &&
    table.dynamicUpdate == null &&
    table.softDeleteColumn == null &&
    versionColumns.isEmpty()
) {
    return@mapNotNull null
}
```

Remove the later duplicate declaration of `versionColumns`.

Keep the existing `versionFieldName` mapping:

```kotlin
val versionFieldName = versionColumns
    .singleOrNull()
    ?.let { versionColumn -> fieldNameByColumnName[versionColumn.name.lowercase(Locale.ROOT)] }
```

- [ ] **Step 6: Update aggregate generator test data**

In `AggregateArtifactPlannerTest.kt`, add this helper near other private helpers:

```kotlin
private fun uniqueConstraint(physicalName: String, vararg columns: String): UniqueConstraintModel =
    UniqueConstraintModel(
        physicalName = physicalName,
        columns = columns.toList(),
    )
```

Add this import:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.UniqueConstraintModel
```

Run:

```powershell
rg -n "uniqueConstraints = listOf\(listOf" cap4k\cap4k-plugin-pipeline-generator-aggregate\src\test
```

Replace each aggregate generator test construction with the helper. Examples:

```kotlin
uniqueConstraints = listOf(uniqueConstraint("uk_v_tenant_slug", "tenant_id", "slug")),
```

```kotlin
uniqueConstraints = listOf(uniqueConstraint("uk_v_slug", "slug")),
```

```kotlin
uniqueConstraints = listOf(uniqueConstraint("uk_v_message_key", "message_key")),
```

- [ ] **Step 7: Update remaining canonical/core test data**

Run:

```powershell
rg -n "uniqueConstraints = listOf\(listOf" cap4k\cap4k-plugin-pipeline-core cap4k\cap4k-plugin-pipeline-api
```

Replace each remaining non-empty construction with `UniqueConstraintModel`. Example:

```kotlin
uniqueConstraints = listOf(
    UniqueConstraintModel(
        physicalName = "uk_v_message_key",
        columns = listOf("message_key"),
    )
),
```

- [ ] **Step 8: Run canonical and aggregate generator tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest*"
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest*"
```

Expected: both commands exit with `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit Task 2**

Run:

```powershell
git add cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-aggregate
git commit -m "Carry named unique constraints into canonical models"
```

### Task 3: Implement Unique Name Normalization And Control-Field Filtering

**Files:**

- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateUniqueConstraintPlanning.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

- [ ] **Step 1: Add planner tests for table-prefixed `uk_v_<fragment>` names**

In `AggregateArtifactPlannerTest.kt`, add:

```kotlin
@Test
fun `unique planning uses table prefixed uk_v fragment and filters soft delete fields`() {
    val planning = AggregateUniqueConstraintPlanning.from(
        entity = EntityModel(
            name = "Category",
            packageName = "com.acme.demo.domain.aggregates.category",
            tableName = "category",
            comment = "Category",
            fields = listOf(
                FieldModel("id", "Long", columnName = "id"),
                FieldModel("code", "String", columnName = "code"),
                FieldModel("deleted", "Long", columnName = "deleted"),
            ),
            idField = FieldModel("id", "Long", columnName = "id"),
            uniqueConstraints = listOf(uniqueConstraint("category_uk_v_code", "code", "deleted")),
        ),
        providerControl = AggregatePersistenceProviderControl(
            entityName = "Category",
            entityPackageName = "com.acme.demo.domain.aggregates.category",
            tableName = "category",
            softDeleteColumn = "deleted",
            idFieldName = "id",
        ),
    )

    val selection = planning.single()
    assertEquals("category_uk_v_code", selection.physicalName)
    assertEquals("uk_v_code", selection.normalizedName)
    assertEquals("Code", selection.suffix)
    assertEquals(listOf("code"), selection.requestProps.map { it.name })
    assertEquals(listOf("deleted"), selection.filteredControlFields.map { it.name })
    assertEquals("UniqueCategoryCode", selection.validatorTypeName)
    assertEquals("UniqueCategoryCodeQry", selection.queryTypeName)
    assertEquals("UniqueCategoryCodeQryHandler", selection.queryHandlerTypeName)
}
```

Add these imports if missing:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceProviderControl
```

- [ ] **Step 2: Add planner tests for `uk`, fallback, version filtering, and failures**

In the same test file, add:

```kotlin
@Test
fun `unique planning uses uk as empty suffix`() {
    val planning = AggregateUniqueConstraintPlanning.from(
        entity = EntityModel(
            name = "VideoPostProcessing",
            packageName = "com.acme.demo.domain.aggregates.video_post_processing",
            tableName = "video_post_processing",
            comment = "Video post processing",
            fields = listOf(
                FieldModel("id", "Long", columnName = "id"),
                FieldModel("videoPostId", "Long", columnName = "video_post_id"),
            ),
            idField = FieldModel("id", "Long", columnName = "id"),
            uniqueConstraints = listOf(uniqueConstraint("video_post_processing_uk", "video_post_id")),
        ),
    )

    val selection = planning.single()
    assertEquals("uk", selection.normalizedName)
    assertEquals("", selection.suffix)
    assertEquals("UniqueVideoPostProcessing", selection.validatorTypeName)
    assertEquals("UniqueVideoPostProcessingQry", selection.queryTypeName)
    assertEquals(listOf("videoPostId"), selection.requestProps.map { it.name })
}

@Test
fun `unique planning fallback filters version fields`() {
    val planning = AggregateUniqueConstraintPlanning.from(
        entity = EntityModel(
            name = "UserMessage",
            packageName = "com.acme.demo.domain.aggregates.user_message",
            tableName = "user_message",
            comment = "User message",
            fields = listOf(
                FieldModel("id", "Long", columnName = "id"),
                FieldModel("messageKey", "String", columnName = "message_key"),
                FieldModel("version", "Long", columnName = "version"),
            ),
            idField = FieldModel("id", "Long", columnName = "id"),
            uniqueConstraints = listOf(uniqueConstraint("uq_message_version", "message_key", "version")),
        ),
        providerControl = AggregatePersistenceProviderControl(
            entityName = "UserMessage",
            entityPackageName = "com.acme.demo.domain.aggregates.user_message",
            tableName = "user_message",
            idFieldName = "id",
            versionFieldName = "version",
        ),
    )

    val selection = planning.single()
    assertEquals("MessageKey", selection.suffix)
    assertEquals(listOf("messageKey"), selection.requestProps.map { it.name })
    assertEquals(listOf("version"), selection.filteredControlFields.map { it.name })
}

@Test
fun `unique planning fails when fallback has only control fields`() {
    val error = assertFailsWith<IllegalArgumentException> {
        AggregateUniqueConstraintPlanning.from(
            entity = EntityModel(
                name = "Category",
                packageName = "com.acme.demo.domain.aggregates.category",
                tableName = "category",
                comment = "Category",
                fields = listOf(
                    FieldModel("id", "Long", columnName = "id"),
                    FieldModel("deleted", "Long", columnName = "deleted"),
                ),
                idField = FieldModel("id", "Long", columnName = "id"),
                uniqueConstraints = listOf(uniqueConstraint("uq_deleted", "deleted")),
            ),
            providerControl = AggregatePersistenceProviderControl(
                entityName = "Category",
                entityPackageName = "com.acme.demo.domain.aggregates.category",
                tableName = "category",
                softDeleteColumn = "deleted",
                idFieldName = "id",
            ),
        )
    }

    assertEquals(
        "Unique constraint uq_deleted on entity Category has no business fields after filtering control fields.",
        error.message,
    )
}

@Test
fun `unique planning fails on duplicate generated type names`() {
    val error = assertFailsWith<IllegalArgumentException> {
        AggregateUniqueConstraintPlanning.from(
            entity = EntityModel(
                name = "Category",
                packageName = "com.acme.demo.domain.aggregates.category",
                tableName = "category",
                comment = "Category",
                fields = listOf(
                    FieldModel("id", "Long", columnName = "id"),
                    FieldModel("code", "String", columnName = "code"),
                ),
                idField = FieldModel("id", "Long", columnName = "id"),
                uniqueConstraints = listOf(
                    uniqueConstraint("category_uk_v_code", "code"),
                    uniqueConstraint("uk_v_code", "code"),
                ),
            )
        )
    }

    assertEquals(
        "Duplicate aggregate unique validator names for entity Category: UniqueCategoryCode",
        error.message,
    )
}
```

Add this import if missing:

```kotlin
import kotlin.test.assertFailsWith
```

- [ ] **Step 3: Run planner tests and confirm they fail**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest*unique planning*"
```

Expected: compilation fails because `AggregateUniqueConstraintSelection` does not yet expose `physicalName`, `normalizedName`, or `filteredControlFields`, and `AggregateUniqueConstraintPlanning.from` does not accept `providerControl`.

- [ ] **Step 4: Extend `AggregateUniqueConstraintSelection`**

In `AggregateUniqueConstraintPlanning.kt`, replace the data class with:

```kotlin
internal data class AggregateUniqueConstraintSelection(
    val physicalName: String,
    val normalizedName: String,
    val suffix: String,
    val requestProps: List<FieldModel>,
    val filteredControlFields: List<FieldModel>,
    val idType: String,
    val excludeIdParamName: String,
    val queryTypeName: String,
    val queryHandlerTypeName: String,
    val validatorTypeName: String,
)
```

- [ ] **Step 5: Change planner signature and selection resolution**

Add this import:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceProviderControl
```

Change the planner signature:

```kotlin
fun from(
    entity: EntityModel,
    providerControl: AggregatePersistenceProviderControl? = null,
): List<AggregateUniqueConstraintSelection>
```

Replace the body with:

```kotlin
val selections = entity.uniqueConstraints.map { constraint ->
    val resolvedFields = selectConstraintFields(entity, constraint.columns)
    val controlColumnNames = controlColumnNames(entity, providerControl)
    val filteredControlFields = resolvedFields.filter { field ->
        (field.columnName ?: field.name).lowercase(Locale.ROOT) in controlColumnNames
    }
    val businessFields = resolvedFields.filterNot { field ->
        (field.columnName ?: field.name).lowercase(Locale.ROOT) in controlColumnNames
    }
    val normalizedName = normalizeUniqueName(entity.tableName, constraint.physicalName)
    val explicitEmptySuffix = normalizedName.equals("uk", ignoreCase = true)
    require(explicitEmptySuffix || businessFields.isNotEmpty()) {
        "Unique constraint ${constraint.physicalName} on entity ${entity.name} has no business fields after filtering control fields."
    }
    val suffix = resolveSuffix(
        normalizedName = normalizedName,
        businessFields = businessFields,
    )

    AggregateUniqueConstraintSelection(
        physicalName = constraint.physicalName,
        normalizedName = normalizedName,
        suffix = suffix,
        requestProps = businessFields,
        filteredControlFields = filteredControlFields,
        idType = entity.idField.type,
        excludeIdParamName = "exclude${entity.name}Id",
        queryTypeName = "Unique${entity.name}${suffix}Qry",
        queryHandlerTypeName = "Unique${entity.name}${suffix}QryHandler",
        validatorTypeName = "Unique${entity.name}${suffix}",
    )
}
validateUniqueSelections(entity, selections)
return selections
```

- [ ] **Step 6: Add planner helper functions**

Add these private helpers below `selectConstraintFields`:

```kotlin
private fun controlColumnNames(
    entity: EntityModel,
    providerControl: AggregatePersistenceProviderControl?,
): Set<String> = buildSet {
    providerControl?.softDeleteColumn
        ?.lowercase(Locale.ROOT)
        ?.let(::add)

    providerControl?.versionFieldName?.let { versionFieldName ->
        entity.fields
            .firstOrNull { field -> field.name.equals(versionFieldName, ignoreCase = true) }
            ?.let { field -> (field.columnName ?: field.name).lowercase(Locale.ROOT) }
            ?.let(::add)
    }
}

private fun normalizeUniqueName(tableName: String, physicalName: String): String {
    val trimmed = physicalName.trim()
    val tablePrefix = "${tableName}_"
    return if (trimmed.startsWith(tablePrefix, ignoreCase = true)) {
        trimmed.substring(tablePrefix.length)
    } else {
        trimmed
    }
}

private fun resolveSuffix(
    normalizedName: String,
    businessFields: List<FieldModel>,
): String {
    if (normalizedName.equals("uk", ignoreCase = true)) {
        return ""
    }

    val explicitFragment = Regex("^uk_v_(.+)$", RegexOption.IGNORE_CASE).find(normalizedName)
    if (explicitFragment != null) {
        return uniqueUpperCamel(explicitFragment.groupValues[1])
    }

    return businessFields.joinToString(separator = "") { field ->
        uniqueUpperCamel(field.name)
    }
}

private fun validateUniqueSelections(
    entity: EntityModel,
    selections: List<AggregateUniqueConstraintSelection>,
) {
    val emptySuffixCount = selections.count { it.suffix.isEmpty() }
    require(emptySuffixCount <= 1) {
        "Entity ${entity.name} has multiple aggregate unique constraints resolving to Unique${entity.name}."
    }
    requireNoDuplicateNames(entity, "validator", selections.map { it.validatorTypeName })
    requireNoDuplicateNames(entity, "query", selections.map { it.queryTypeName })
    requireNoDuplicateNames(entity, "query handler", selections.map { it.queryHandlerTypeName })
}

private fun requireNoDuplicateNames(
    entity: EntityModel,
    label: String,
    names: List<String>,
) {
    val duplicates = names
        .groupingBy { it }
        .eachCount()
        .filterValues { count -> count > 1 }
        .keys
    require(duplicates.isEmpty()) {
        "Duplicate aggregate unique $label names for entity ${entity.name}: ${duplicates.joinToString(", ")}"
    }
}

private fun uniqueUpperCamel(value: String): String =
    uniqueLowerCamel(value).replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
    }
```

Keep the existing `uniqueLowerCamel(...)` function.

- [ ] **Step 7: Run planner tests and confirm Task 3 passes**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest*unique planning*"
```

Expected: the command exits with `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit Task 3**

Run:

```powershell
git add cap4k-plugin-pipeline-generator-aggregate
git commit -m "Resolve aggregate unique family names"
```

### Task 4: Wire Unique Selections Into Artifact Planners And Plan Metadata

**Files:**

- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateUniqueConstraintPlanning.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/UniqueQueryArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/UniqueQueryHandlerArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/UniqueValidatorArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

- [ ] **Step 1: Add a full artifact planner test for shared unique metadata**

In `AggregateArtifactPlannerTest.kt`, add this test near the existing unique artifact tests:

```kotlin
@Test
fun `aggregate unique artifacts share resolved db unique family metadata`() {
    val entity = EntityModel(
        name = "Category",
        packageName = "com.acme.demo.domain.aggregates.category",
        tableName = "category",
        comment = "Category",
        fields = listOf(
            FieldModel("id", "Long", columnName = "id"),
            FieldModel("code", "String", columnName = "code"),
            FieldModel("deleted", "Long", columnName = "deleted"),
        ),
        idField = FieldModel("id", "Long", columnName = "id"),
        uniqueConstraints = listOf(uniqueConstraint("category_uk_v_code", "code", "deleted")),
    )
    val planItems = AggregateArtifactPlanner().plan(
        aggregateConfig(),
        CanonicalModel(
            entities = listOf(entity),
            schemas = listOf(
                SchemaModel(
                    name = "SCategory",
                    packageName = "com.acme.demo.domain._share.meta.category",
                    entityName = "Category",
                    comment = "Category",
                    fields = entity.fields,
                )
            ),
            repositories = listOf(
                RepositoryModel(
                    name = "CategoryRepository",
                    packageName = "com.acme.demo.adapter.domain.repositories",
                    entityName = "Category",
                    idType = "Long",
                )
            ),
            aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
            aggregatePersistenceProviderControls = listOf(
                AggregatePersistenceProviderControl(
                    entityName = "Category",
                    entityPackageName = "com.acme.demo.domain.aggregates.category",
                    tableName = "category",
                    softDeleteColumn = "deleted",
                    idFieldName = "id",
                )
            ),
        ),
    )

    val query = planItems.single { it.templateId == "aggregate/unique_query.kt.peb" }
    val handler = planItems.single { it.templateId == "aggregate/unique_query_handler.kt.peb" }
    val validator = planItems.single { it.templateId == "aggregate/unique_validator.kt.peb" }

    listOf(query, handler, validator).forEach { item ->
        assertEquals("category_uk_v_code", item.context["uniquePhysicalName"])
        assertEquals("uk_v_code", item.context["uniqueNormalizedName"])
        assertEquals("Code", item.context["uniqueResolvedSuffix"])
        assertEquals(listOf("code"), item.context["uniqueSelectedBusinessFields"])
        assertEquals(listOf("deleted"), item.context["uniqueFilteredControlFields"])
    }
    assertEquals("UniqueCategoryCodeQry", query.context["typeName"])
    assertEquals("UniqueCategoryCodeQryHandler", handler.context["typeName"])
    assertEquals("UniqueCategoryCode", validator.context["typeName"])
    assertEquals(listOf("code"), handler.context["whereProps"])
}
```

Expected initial failure: artifact planners still call `AggregateUniqueConstraintPlanning.from(entity)` without provider controls and do not put unique metadata into context.

- [ ] **Step 2: Add a model-level planning helper**

In `AggregateUniqueConstraintPlanning.kt`, import `CanonicalModel`:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
```

Add this public internal helper inside `AggregateUniqueConstraintPlanning`:

```kotlin
fun from(model: CanonicalModel): List<Pair<EntityModel, List<AggregateUniqueConstraintSelection>>> {
    val providerControlsByEntity = model.aggregatePersistenceProviderControls.associateBy {
        it.entityPackageName to it.entityName
    }
    return model.entities
        .map { entity ->
            entity to from(
                entity = entity,
                providerControl = providerControlsByEntity[entity.packageName to entity.name],
            )
        }
        .filter { (_, selections) -> selections.isNotEmpty() }
}
```

- [ ] **Step 3: Use the shared model-level helper in all unique artifact planners**

In `UniqueQueryArtifactPlanner.kt`, replace:

```kotlin
val plannedSelections = model.entities.map { entity ->
    entity to AggregateUniqueConstraintPlanning.from(entity)
}.filter { (_, selections) -> selections.isNotEmpty() }
```

with:

```kotlin
val plannedSelections = AggregateUniqueConstraintPlanning.from(model)
```

Make the same replacement in:

- `UniqueQueryHandlerArtifactPlanner.kt`
- `UniqueValidatorArtifactPlanner.kt`

- [ ] **Step 4: Add unique metadata to query artifact context**

In `UniqueQueryArtifactPlanner.kt`, add these context entries:

```kotlin
"uniquePhysicalName" to selection.physicalName,
"uniqueNormalizedName" to selection.normalizedName,
"uniqueResolvedSuffix" to selection.suffix,
"uniqueSelectedBusinessFields" to selection.requestProps.map { it.name },
"uniqueFilteredControlFields" to selection.filteredControlFields.map { it.name },
```

The context block should include these entries alongside existing `entityName`, `requestProps`, `idType`, and `excludeIdParamName`.

- [ ] **Step 5: Add unique metadata to handler artifact context**

In `UniqueQueryHandlerArtifactPlanner.kt`, add:

```kotlin
"uniquePhysicalName" to selection.physicalName,
"uniqueNormalizedName" to selection.normalizedName,
"uniqueResolvedSuffix" to selection.suffix,
"uniqueSelectedBusinessFields" to selection.requestProps.map { it.name },
"uniqueFilteredControlFields" to selection.filteredControlFields.map { it.name },
```

Keep:

```kotlin
"whereProps" to selection.requestProps.map { it.name },
```

This ensures soft-delete and version fields are excluded from handler query input and where props.

- [ ] **Step 6: Add unique metadata to validator artifact context**

In `UniqueValidatorArtifactPlanner.kt`, add:

```kotlin
"uniquePhysicalName" to selection.physicalName,
"uniqueNormalizedName" to selection.normalizedName,
"uniqueResolvedSuffix" to selection.suffix,
"uniqueSelectedBusinessFields" to selection.requestProps.map { it.name },
"uniqueFilteredControlFields" to selection.filteredControlFields.map { it.name },
```

Keep `requestProps` built from `selection.requestProps`; do not add filtered control fields to annotation parameters.

- [ ] **Step 7: Run aggregate generator tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest*"
```

Expected: the command exits with `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit Task 4**

Run:

```powershell
git add cap4k-plugin-pipeline-generator-aggregate
git commit -m "Expose resolved unique family metadata"
```

### Task 5: Add Gradle Functional Coverage For Portable Unique Names

**Files:**

- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/schema.sql`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Change aggregate sample schema to use portable named unique constraints**

Replace `aggregate-sample/schema.sql` with:

```sql
create table if not exists video_post (
    id bigint primary key,
    slug varchar(128) not null,
    deleted bigint not null default 0,
    version bigint not null comment '@Version=true;',
    title varchar(255) not null,
    published boolean default false,
    constraint video_post_uk_v_slug unique (slug, deleted, version)
);

comment on table video_post is '@AggregateRoot=true;@SoftDeleteColumn=deleted;';
```

This fixture proves:

- H2-compatible table-prefixed unique physical names are accepted.
- `video_post_uk_v_slug` resolves to `uk_v_slug`.
- `deleted` and `version` do not appear in generated unique request fields.

- [ ] **Step 2: Extend functional assertions for generated unique files**

In `PipelinePluginFunctionalTest.kt`, inside `cap4kPlan and cap4kGenerate produce aggregate artifacts from db schema`, keep the existing unique file paths and add these assertions after existing unique content assertions:

```kotlin
assertFalse(uniqueQueryContent.contains("val deleted"))
assertFalse(uniqueQueryContent.contains("val version"))
assertFalse(uniqueQueryHandlerContent.contains("request.deleted"))
assertFalse(uniqueQueryHandlerContent.contains("request.version"))
assertFalse(uniqueValidatorContent.contains("deletedField"))
assertFalse(uniqueValidatorContent.contains("versionField"))
```

- [ ] **Step 3: Extend functional assertions for plan metadata**

In the same test, add these assertions after `val planContent = planFile.readText()`:

```kotlin
assertTrue(planContent.contains("\"uniquePhysicalName\": \"video_post_uk_v_slug\""))
assertTrue(planContent.contains("\"uniqueNormalizedName\": \"uk_v_slug\""))
assertTrue(planContent.contains("\"uniqueResolvedSuffix\": \"Slug\""))
assertTrue(planContent.contains("\"uniqueSelectedBusinessFields\": ["))
assertTrue(planContent.contains("\"slug\""))
assertTrue(planContent.contains("\"uniqueFilteredControlFields\": ["))
assertTrue(planContent.contains("\"deleted\""))
assertTrue(planContent.contains("\"version\""))
```

Do not assert exact JSON array formatting beyond these substrings; Gson pretty printing controls line breaks.

- [ ] **Step 4: Run the functional test**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginFunctionalTest.cap4kPlan and cap4kGenerate produce aggregate artifacts from db schema"
```

Expected: the command exits with `BUILD SUCCESSFUL`.

If it fails because `uniquePhysicalName` casing differs, do not weaken production code. Adjust the assertion to match the H2 metadata emitted by the fixture with `DATABASE_TO_UPPER=false`.

- [ ] **Step 5: Commit Task 5**

Run:

```powershell
git add cap4k-plugin-pipeline-gradle
git commit -m "Verify portable aggregate unique naming"
```

### Task 6: Final Verification And Documentation Status

**Files:**

- Modify: `docs/superpowers/specs/2026-05-03-cap4k-aggregate-unique-family-naming-contract-design.md`
- Modify: `docs/superpowers/mainline-roadmap.md`

- [ ] **Step 1: Run focused module tests**

Run each command separately to avoid Windows command-line length issues:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test
```

Expected: `BUILD SUCCESSFUL`.

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-source-db:test
```

Expected: `BUILD SUCCESSFUL`.

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test
```

Expected: `BUILD SUCCESSFUL`.

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run Gradle plugin functional coverage**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginFunctionalTest*"
```

Expected: `BUILD SUCCESSFUL`.

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginCompileFunctionalTest*aggregate unique*"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run whitespace verification**

Run:

```powershell
git diff --check
```

Expected: no whitespace errors.

- [ ] **Step 4: Update spec status**

In `docs/superpowers/specs/2026-05-03-cap4k-aggregate-unique-family-naming-contract-design.md`, change:

```markdown
Status: Draft for review
```

to:

```markdown
Status: Implemented
```

Add this section before `## Open Decisions`:

```markdown
## Implementation Notes

Implemented through:

- named unique constraint source and canonical models
- DB-source physical unique name preservation
- unique name normalization for `uk`, `uk_v_<fragment>`, `<table>_uk`, and `<table>_uk_v_<fragment>`
- soft-delete and version control-field filtering
- shared query, query handler, and validator family naming
- plan metadata for physical name, normalized name, resolved suffix, selected business fields, and filtered control fields
- fail-fast generated-name collision checks
```

- [ ] **Step 5: Update roadmap completion state**

In `docs/superpowers/mainline-roadmap.md`, add `aggregate unique family naming contract` to `Recently completed mainline slices`.

Add a completed section after the UUID7 completed section:

```markdown
### Completed: Aggregate unique family naming contract

Status:

- implementation complete
- verified through pipeline API/source/core/aggregate generator tests and Gradle plugin functional tests

Reference:

- [aggregate unique family naming contract design](specs/2026-05-03-cap4k-aggregate-unique-family-naming-contract-design.md)
- [aggregate unique family naming implementation plan](plans/2026-05-03-cap4k-aggregate-unique-family-naming-contract.md)

Next action:

- remove duplicate hand-migrated unique query, query handler, and validator files in dogfood projects after regenerating with the new pipeline

Notes:

- unique naming is DDL-source-driven, not DSL-first
- supported logical names are `uk` and `uk_v_<fragment>`
- H2-compatible physical names may include the exact current table prefix
- soft-delete and optimistic-lock version fields are filtered from fallback unique business names and generated request properties
```

- [ ] **Step 6: Commit final documentation**

Run:

```powershell
git add docs/superpowers
git commit -m "Complete aggregate unique naming documentation"
```

- [ ] **Step 7: Report final verification evidence**

Collect the final command outcomes and include them in the implementation final response:

```text
:cap4k-plugin-pipeline-api:test -> BUILD SUCCESSFUL
:cap4k-plugin-pipeline-source-db:test -> BUILD SUCCESSFUL
:cap4k-plugin-pipeline-core:test -> BUILD SUCCESSFUL
:cap4k-plugin-pipeline-generator-aggregate:test -> BUILD SUCCESSFUL
:cap4k-plugin-pipeline-gradle:test PipelinePluginFunctionalTest -> BUILD SUCCESSFUL
:cap4k-plugin-pipeline-gradle:test aggregate unique compile-functional subset -> BUILD SUCCESSFUL
git diff --check -> no errors
```

Do not claim completion if any command fails. Report the failing command and the first actionable error instead.
