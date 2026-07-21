# cap4k DB Custom Annotation Contract Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the DB schema comment contract with a strict present-tense allow-list that keeps owned-parent binding, managed-field roles, and reference identity facts separate, while leaving owned-cardinality and soft-delete policy expansion points for the two sibling specs.

**Architecture:** Make the DB source parser the single gate for comment annotations, move every supported fact into typed snapshot fields, and remove all old DB annotation aliases from the active pipeline. Keep later behavior expansion points explicit by carrying parent binding, managed roles, and ID strategy facts through the source snapshot and canonical write-surface models, but do not infer owned cardinality or rewrite soft-delete SQL in this iteration. Use narrow TDD slices so parser, source provider, core inference, generator context, and docs all move together without leaving half-removed annotation paths behind.

**Tech Stack:** Kotlin, Gradle, JUnit 5, JDBC metadata, Pebble templates, workspace docs and skill references.

## Global Constraints

- Work in the isolated worktree at `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/.worktrees/db-custom-annotation-contract-redesign`.
- DB schema comments support only `@Parent=<table>` and `@Ignore` at table scope.
- DB schema comments support only `@ParentRef`, `@Type`, `@RefAggregate`, `@RefId`, `@IdStrategy=db_identity`, `@Managed=system|scope|deleted|version`, and `@Inherited` at column scope.
- No DB annotation aliases are supported in the active contract.
- Old annotations fail through one generic unsupported-annotation path per scope.
- Do not depend on physical DB foreign-key metadata.
- Do not infer owned-cardinality in this iteration.
- Do not redesign soft-delete tombstone SQL in this iteration.
- Do not reintroduce value-object DB annotations in this iteration.
- Preserve the current generator and template architecture unless a task needs a narrow contract change.
- Use exact annotation names in docs and tests so `@TYPE`, `@P`, `@Ref`, `@Relation`, `@Lazy`, `@Count`, `@DynamicInsert`, and `@DynamicUpdate` are rejected, not normalized.

---

## File Structure

- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
  - Owns public snapshot types, enum facts, and the persistence provider control models that downstream tasks consume.
- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbCommentAnnotationParser.kt`
  - New internal scanner for exact-name parsing and supported-annotation stripping.
- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParser.kt`
  - Table allow-list parser for `@Parent` and `@Ignore`.
- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt`
  - Column allow-list parser for `@ParentRef`, `@Type`, `@RefAggregate`, `@RefId`, `@IdStrategy`, `@Managed`, and `@Inherited`.
- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParser.kt`
  - Delete this file; the old relation annotation path is not part of the new contract.
- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
  - Maps parsed comment facts into snapshots and validates parent binding rules.
- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/OwnedParentBindingResolver.kt`
  - New resolver boundary for explicit parent binding without FK metadata or `<parent_table>_id` fallback.
- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt`
  - Builds owned relations from explicit parent bindings only.
- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateInverseRelationInference.kt`
  - Keeps inverse relation inference aligned with the owned-parent link.
- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt`
  - Carries managed roles and ID strategy into special-field policy resolution.
- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceFieldBehaviorInference.kt`
  - Derives persistence field behavior from the new snapshot facts.
- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
  - Keeps canonical validation aligned with the reshaped snapshot model.
- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceProviderInference.kt`
  - Emits provider controls from canonical special-field facts only.
- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
  - Exposes structural and managed field metadata to templates.
- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt`
  - Exposes unresolved constructor state and structural inputs.
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
  - Renders entity output from the new generator context.
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb`
  - Renders factory output from the new generator context.
- `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
  - Covers end-to-end fixture behavior for comment syntax and generated output.
- `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
  - Covers compile-time fixture behavior for the same contract.
- `docs/public/reference/db-schema-annotations.md`
  - Public contract reference that defines the live annotation set.
- `skills/cap4k-generator-inputs/references/db-schema-annotations.md`
  - Skill reference that mirrors the public contract.
- `.agents/skills/cap4k-generator-inputs/references/db-schema-annotations.md`
  - Workspace skill reference that mirrors the public contract.
- `docs/public/reference/generator-input-validation.md`
  - Public validation guidance for allowed generator inputs.
- `docs/public/generator/inputs-and-sources.md`
  - Source-to-input mapping reference for generator consumers.
- `scripts/validate-cap4k-generator-inputs.py`
  - Drift scanner that flags stale annotation names in docs and skill references.

### Task 1: Reshape DB Snapshot Models For The New Contract

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Test: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`

**Interfaces:**
- Consumes: current `DbColumnSnapshot`, `DbTableSnapshot`, `AggregateSpecialFieldResolvedPolicy`, `ResolvedManagedFieldPolicy`, `ResolvedIdPolicy`, `AggregatePersistenceProviderControl`
- Produces: typed source facts for `parentRef`, `managedRole`, and `idStrategy`, with DB-owned dynamic insert/update flags removed from the active API surface

- [ ] **Step 1: Write failing model tests for the new snapshot shape**

```kotlin
@Test
fun `db column snapshot carries parent ref managed role and id strategy`() {
    val column = DbColumnSnapshot(
        name = "parent_id",
        dbType = "BIGINT",
        kotlinType = "Long",
        nullable = false,
        parentRef = true,
        managedRole = DbManagedRole.SCOPE,
        idStrategy = DbIdStrategy.DB_IDENTITY,
    )

    assertTrue(column.parentRef)
    assertEquals(DbManagedRole.SCOPE, column.managedRole)
    assertEquals(DbIdStrategy.DB_IDENTITY, column.idStrategy)
}
```

- [ ] **Step 2: Run API tests to prove the old model no longer compiles**

Run: `./gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest"`
Expected: FAIL because the new properties and enums are not defined yet and old DB annotation fields still exist.

- [ ] **Step 3: Update `PipelineModels.kt` with the new source facts**

```kotlin
enum class DbManagedRole {
    SYSTEM,
    SCOPE,
    DELETED,
    VERSION,
}

enum class DbIdStrategy {
    DB_IDENTITY,
}

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
    val parentRef: Boolean = false,
    val refAggregate: String? = null,
    val refId: String? = null,
    val idStrategy: DbIdStrategy? = null,
    val managedRole: DbManagedRole? = null,
    val inherited: Boolean? = null,
)

data class DbTableSnapshot(
    val tableName: String,
    val comment: String,
    val columns: List<DbColumnSnapshot>,
    val primaryKey: List<String>,
    val uniqueConstraints: List<UniqueConstraintModel>,
    val parentTable: String? = null,
    val aggregateRoot: Boolean = true,
)
```

Delete the stale DB-contract fields from `DbColumnSnapshot` and `DbTableSnapshot` instead of keeping parallel legacy and new fields.

Also remove `dynamicInsert` and `dynamicUpdate` from `AggregatePersistenceProviderControl` unless a non-DB source has a separate live owner for those fields. The current DB contract no longer carries `@DynamicInsert` or `@DynamicUpdate`, so leaving them in this control would keep a stale write-surface path alive.

- [ ] **Step 4: Re-run API tests until the model assertions pass**

Run: `./gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest"`
Expected: PASS with the new enum and snapshot fields in place.

- [ ] **Step 5: Commit the model-only slice**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt
git add cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt
git commit -m "refactor: reshape db snapshot contract"
```

### Task 2: Replace The Comment Parsers With A Strict Allow-List

**Files:**
- Create: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbCommentAnnotationParser.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParser.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt`
- Delete: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParser.kt`
- Test: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParserTest.kt`
- Test: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt`
- Delete: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParserTest.kt`

**Interfaces:**
- Consumes: raw table and column comment strings
- Produces: exact parsed annotations with raw names preserved, plus cleaned comments that strip only supported annotations

- [ ] **Step 1: Write failing parser tests for exact names and generic rejection**

```kotlin
@Test
fun `table parser accepts only parent and ignore`() {
    val metadata = DbTableAnnotationParser.parse("video_post @Parent=content; @Ignore;")

    assertEquals("content", metadata.parentTable)
    assertTrue(metadata.ignored)
    assertEquals("video_post", metadata.cleanedComment)
}

@Test
fun `table parser rejects uppercase alias names`() {
    val error = assertFailsWith<IllegalArgumentException> {
        DbTableAnnotationParser.parse("@P=content;")
    }

    assertEquals(
        "unsupported table annotation @P. Supported table annotations: @Parent=<table>, @Ignore.",
        error.message
    )
}

@Test
fun `column parser accepts parent ref managed role and id strategy`() {
    val parentRef = DbColumnAnnotationParser.parse("@ParentRef;@Managed=scope;")
    val id = DbColumnAnnotationParser.parse("@IdStrategy=db_identity;")

    assertTrue(parentRef.parentRef)
    assertEquals(DbManagedRole.SCOPE, parentRef.managedRole)
    assertEquals(DbIdStrategy.DB_IDENTITY, id.idStrategy)
}

@Test
fun `column parser rejects removed relation annotations through one generic path`() {
    val error = assertFailsWith<IllegalArgumentException> {
        DbColumnAnnotationParser.parse("@Reference=user_profile;@Relation=ManyToOne;")
    }

    assertEquals(
        "unsupported column annotation @Reference. Supported column annotations: @ParentRef, @Type, @RefAggregate, @RefId, @IdStrategy=db_identity, @Managed=system|scope|deleted|version, @Inherited.",
        error.message
    )
}
```

Also add parameterized coverage for the full alias set from the first draft so every legacy spelling falls through the same unsupported-annotation path:

```kotlin
@ParameterizedTest
@ValueSource(strings = [
    "@AggregateRoot=true;",
    "@Root=true;",
    "@R=true;",
    "@DynamicInsert=true;",
    "@DynamicUpdate=true;",
])
fun `rejects old table annotations through generic path`(comment: String) {
    val error = assertFailsWith<IllegalArgumentException> {
        DbTableAnnotationParser.parse(comment)
    }

    assertTrue(error.message!!.startsWith("unsupported table annotation @"))
}

@ParameterizedTest
@ValueSource(strings = [
    "@T=Status;",
    "@TYPE=Status;",
    "@E=0:A:a;",
    "@ENUM=0:A:a;",
    "@Deleted;",
    "@Version;",
    "@GeneratedValue=identity;",
    "@Reference=video_post;",
    "@Ref=video_post;",
    "@Relation=ManyToOne;",
    "@Rel=*:1;",
    "@Lazy=true;",
    "@L=true;",
    "@Count=one;",
    "@C=one;",
    "@One;",
    "@Exposed;",
    "@Insertable=false;",
    "@Updatable=false;",
])
fun `rejects old column annotations through generic path`(comment: String) {
    val error = assertFailsWith<IllegalArgumentException> {
        DbColumnAnnotationParser.parse(comment)
    }

    assertTrue(error.message!!.startsWith("unsupported column annotation @"))
}
```

- [ ] **Step 2: Run parser tests to confirm the current allow-list is still wrong**

Run: `./gradlew.bat :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbTableAnnotationParserTest" --tests "com.only4.cap4k.plugin.pipeline.source.db.DbColumnAnnotationParserTest"`
Expected: FAIL because the parsers still recognize old aliases and relation annotations.

- [ ] **Step 3: Introduce one shared scanner with raw annotation names**

```kotlin
internal data class ParsedDbCommentAnnotation(
    val rawName: String,
    val value: String,
    val range: IntRange,
    val hasExplicitValue: Boolean,
)

internal object DbCommentAnnotationParser {
    fun parse(comment: String): List<ParsedDbCommentAnnotation>
    fun strip(comment: String, supportedNames: Set<String>): String
}
```

Use the raw annotation name exactly as written in the comment so `@Type` is accepted and `@TYPE` is rejected.

- [ ] **Step 4: Rewrite table parsing around the new shared scanner**

```kotlin
private val supportedTableAnnotations = setOf("Parent", "Ignore")

fun parse(comment: String): DbTableAnnotationParseResult {
    val annotations = DbCommentAnnotationParser.parse(comment)
    rejectUnsupportedAnnotations(annotations, supportedTableAnnotations)
    val parentTable = resolveRequiredValue(annotations, "Parent")
    val ignored = hasMarker(annotations, "Ignore")
    return DbTableAnnotationParseResult(
        parentTable = parentTable,
        aggregateRoot = parentTable == null,
        ignored = ignored,
        cleanedComment = DbCommentAnnotationParser.strip(comment, supportedTableAnnotations),
    )
}
```

No `@AggregateRoot`, `@Root`, `@R`, `@DynamicInsert`, or `@DynamicUpdate` branches remain in the active parser.

- [ ] **Step 5: Rewrite column parsing around the new shared scanner**

```kotlin
private val supportedColumnAnnotations = setOf(
    "ParentRef",
    "Type",
    "RefAggregate",
    "RefId",
    "IdStrategy",
    "Managed",
    "Inherited",
)
```

Validate:

```kotlin
require(managedRoleValues.size <= 1) { "multiple @Managed annotations are not allowed." }
require(managedRole != null) { "invalid @Managed annotation: value is required." }
require(idStrategy == null || idStrategy == DbIdStrategy.DB_IDENTITY) { "unsupported @IdStrategy value: $rawValue" }
require(!(parentRef && (refAggregate != null || refId != null || idStrategy != null))) {
    "@ParentRef cannot be combined with @RefAggregate, @RefId, or @IdStrategy."
}
require(inherited == null || managedRole != null) {
    "@Inherited is valid only with @Managed=system, @Managed=scope, @Managed=deleted, or @Managed=version."
}
```

- [ ] **Step 6: Delete the legacy relation parser and its test file**

Remove the `DbRelationAnnotationParser` file entirely so there is no secondary relation annotation path left in `source-db`.

- [ ] **Step 7: Re-run parser tests until the new allow-list passes**

Run: `./gradlew.bat :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbTableAnnotationParserTest" --tests "com.only4.cap4k.plugin.pipeline.source.db.DbColumnAnnotationParserTest"`
Expected: PASS with all removed aliases failing through the same unsupported-annotation route.

- [ ] **Step 8: Commit the parser slice**

```bash
git add cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbCommentAnnotationParser.kt
git add cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParser.kt
git add cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt
git add cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParserTest.kt
git add cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt
git rm cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParser.kt
git rm cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParserTest.kt
git commit -m "refactor: enforce strict db comment allow-list"
```

### Task 3: Map Parsed Facts Into Source Snapshots And Validate Parent Binding

**Files:**
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Test: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`

**Interfaces:**
- Consumes: strict table and column parse results
- Produces: `DbSchemaSnapshot` with `parentRef`, `managedRole`, `idStrategy`, `parentTable`, and `aggregateRoot` populated

- [ ] **Step 1: Write failing provider tests for parent binding, managed roles, and ID strategy**

```kotlin
@Test
fun `collect maps parent ref and managed roles into db snapshots`() {
    // table comment: @Parent=video_post;
    // column comments:
    //   video_post_item.video_post_id -> @ParentRef;
    //   video_post_item.tenant_id -> @Managed=scope;
    //   video_post_item.deleted -> @Managed=deleted;
    val snapshot = provider.collect(config)

val child = snapshot.tables.single { it.tableName == "video_post_item" }
assertEquals("video_post", child.parentTable)
assertFalse(child.aggregateRoot)
assertEquals(1, child.columns.count { it.parentRef })
assertEquals(DbManagedRole.SCOPE, child.columns.single { it.name == "tenant_id" }.managedRole)
assertEquals(DbManagedRole.DELETED, child.columns.single { it.name == "deleted" }.managedRole)
}
```

Add one test that asserts a child table with `@ParentRef` but no `@Parent` fails fast, and another that asserts `@IdStrategy=db_identity` is rejected on any non-primary-key column. Keep those failures in the provider layer so the structural contract is enforced before core inference sees the snapshot.

- [ ] **Step 2: Run the provider tests and capture the current breakage**

Run: `./gradlew.bat :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProviderTest"`
Expected: FAIL because the provider still feeds old fields and still tolerates legacy annotations.

- [ ] **Step 3: Wire the new parsers into the JDBC source provider**

```kotlin
val tableMetadata = tableAnnotationParser.parse(tableComment)
val columnMetadata = DbColumnAnnotationParser.parse(comment)

DbColumnSnapshot(
    name = name,
    dbType = typeName,
    kotlinType = JdbcTypeMapper.toKotlinType(rows.getInt("DATA_TYPE"), typeName),
    nullable = rows.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
    defaultValue = rows.getString("COLUMN_DEF"),
    comment = columnMetadata.cleanedComment,
    isPrimaryKey = name in primaryKeySet,
    typeBinding = columnMetadata.typeBinding,
    enumItems = columnMetadata.enumItems,
    parentRef = columnMetadata.parentRef,
    refAggregate = columnMetadata.refAggregate,
    refId = columnMetadata.refId,
    idStrategy = columnMetadata.idStrategy,
    managedRole = columnMetadata.managedRole,
    inherited = columnMetadata.inherited,
)
```

Compute `aggregateRoot = parentTable == null` in the provider, not from a table annotation flag.

- [ ] **Step 4: Add provider-side validation for the new structural rules**

```kotlin
require(child.columns.count { it.parentRef } == 1) {
    "table ${child.tableName} with @Parent=${child.parentTable} must declare exactly one @ParentRef"
}

require(child.columns.none { it.parentRef } || child.parentTable != null) {
    "@ParentRef is valid only on child tables with @Parent"
}

require(child.columns.filter { it.idStrategy == DbIdStrategy.DB_IDENTITY }.all { it.isPrimaryKey }) {
    "@IdStrategy=db_identity is valid only on a primary-key column"
}
```

This validation is where the `@Parent` and `@ParentRef` contract becomes concrete without touching cardinality inference.

- [ ] **Step 5: Re-run provider tests until the new snapshot facts are green**

Run: `./gradlew.bat :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProviderTest"`
Expected: PASS with legacy DB annotations rejected and cleaned comments stripped correctly.

- [ ] **Step 6: Commit the provider slice**

```bash
git add cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt
git add cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt
git commit -m "refactor: map db comment facts into strict snapshots"
```

### Task 4: Rebase Core Relation Inference Onto Explicit ParentRef

**Files:**
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/OwnedParentBindingResolver.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateInverseRelationInference.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

**Interfaces:**
- Consumes: `DbTableSnapshot.parentTable`, `DbColumnSnapshot.parentRef`
- Produces: owned parent-child relations resolved only from the explicit parent reference column

- [ ] **Step 1: Write failing core tests for explicit parent binding**

```kotlin
@Test
fun `owned parent binding fails without parent ref`() {
    val error = assertFailsWith<IllegalArgumentException> {
        assembleCanonical(
            table(
                tableName = "video_post_item",
                parentTable = "video_post",
                columns = listOf(
                    column("id", isPrimaryKey = true),
                    column("video_post_id"),
                ),
            )
        )
    }

    assertEquals("missing parent reference column for table: video_post_item", error.message)
}
```

Add a second test that fails when more than one column has `parentRef = true`.

Add a third test that proves old weak relation metadata no longer creates owned relations, so only explicit `@ParentRef` drives the relation graph.

- [ ] **Step 2: Run core tests to prove the current fallback still exists**

Run: `./gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"`
Expected: FAIL because `AggregateRelationInference` still falls back to `<parent_table>_id` and still builds explicit weak relations from old metadata.

- [ ] **Step 3: Add a focused resolver for parent binding**

```kotlin
internal data class OwnedParentBinding(
    val childTable: DbTableSnapshot,
    val parentTable: String,
    val parentRefColumn: DbColumnSnapshot,
)

internal object OwnedParentBindingResolver {
    fun resolve(
        tables: List<DbTableSnapshot>,
        skippedTableNames: Set<String> = emptySet(),
        outOfScopeTableNames: Set<String> = emptySet(),
    ): List<OwnedParentBinding>
}
```

The resolver must:

1. Require exactly one `parentRef` column on every `@Parent` table.
2. Never fall back to `<parent_table>_id`.
3. Ignore only tables that are skipped or out of scope after the parent-ref validation has already happened.

- [ ] **Step 4: Rewrite `AggregateRelationInference` to consume the resolver**

Replace the join-column lookup block with:

```kotlin
val parentBindings = OwnedParentBindingResolver.resolve(
    tables = tables,
    skippedTableNames = skippedTableNames,
    outOfScopeTableNames = outOfScopeTableNames,
)
```

Then build parent-child relations from `binding.parentRefColumn.name` and remove all relation generation that depends on `referenceTable`, `explicitRelationType`, `lazy`, or `countHint`.

The only relation fact this iteration keeps is the owned parent-child link.

- [ ] **Step 5: Keep inverse relation inference aligned with the owned-parent link**

Update `AggregateInverseRelationInference` so it no longer depends on old relation annotations. It should infer the read-only child-side parent reference from the owned parent-child relation already emitted by `AggregateRelationInference`, not from `referenceTable` metadata.

- [ ] **Step 6: Re-run canonical tests until parent binding is explicit and deterministic**

Run: `./gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"`
Expected: PASS with no `<parent_table>_id` fallback and no explicit legacy relation annotations required.

- [ ] **Step 7: Commit the core relation slice**

```bash
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/OwnedParentBindingResolver.kt
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateInverseRelationInference.kt
git add cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "refactor: require explicit parent ref for owned relations"
```

### Task 5: Carry Managed Roles And Id Strategy Through Special-Field Resolution

**Files:**
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceFieldBehaviorInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

**Interfaces:**
- Consumes: `DbColumnSnapshot.idStrategy`, `DbColumnSnapshot.managedRole`, `DbColumnSnapshot.inherited`
- Produces: resolved special-field policies that later specs can consume for soft-delete and owned-cardinality

- [ ] **Step 1: Write failing tests for managed roles and db identity**

```kotlin
@Test
fun `managed role scope is carried into special field resolution`() {
    val model = assembleCanonical(
        table(
            tableName = "video_post",
            columns = listOf(
                column("id", isPrimaryKey = true),
                column("tenant_id", managedRole = DbManagedRole.SCOPE),
                column("deleted", managedRole = DbManagedRole.DELETED),
            ),
        )
    )

    val policy = model.aggregateSpecialFieldResolvedPolicies.single()
    assertEquals("tenantId", policy.managedFields.single { it.columnName == "tenant_id" }.fieldName)
    assertEquals("deleted", policy.deleted.columnName)
}

@Test
fun `inherited managed fields remain visible to canonical policy`() {
    val model = assembleCanonical(
        table(
            tableName = "video_post",
            columns = listOf(
                column("id", isPrimaryKey = true),
                column("created_at", managedRole = DbManagedRole.SYSTEM, inherited = true),
            ),
        )
    )

    val policy = model.aggregateSpecialFieldResolvedPolicies.single()
    assertTrue(policy.managedFields.any { it.columnName == "created_at" && it.managedRole == DbManagedRole.SYSTEM })
}
```

- [ ] **Step 2: Run core tests to prove special-field resolution still reads old booleans**

Run: `./gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"`
Expected: FAIL because `deleted/version/managed` are still read from legacy boolean fields.

- [ ] **Step 3: Make the policy resolver consume the new role fields**

```kotlin
val generatedValueStrategy = idColumn.idStrategy?.let { "identity" }
val deletedColumns = table.columns.filter { it.managedRole == DbManagedRole.DELETED }
val versionColumns = table.columns.filter { it.managedRole == DbManagedRole.VERSION }
val managedColumns = table.columns.filter {
    it.managedRole == DbManagedRole.SYSTEM || it.managedRole == DbManagedRole.SCOPE
}
```

Extend `ResolvedManagedFieldPolicy` with the resolved role so later owned-cardinality work can reuse the same metadata without re-parsing comments.

- [ ] **Step 4: Keep inherited fields visible to canonical and write-surface logic**

`@Inherited` should continue to omit the scalar field only from the default generated concrete entity when the field is part of a managed role, but the canonical model and write-surface calculation must still see it.

Add a test that proves an inherited managed field is still present in `aggregateSpecialFieldResolvedPolicies.managedFields`.

Update `DefaultCanonicalAssembler` so any guard that previously read `generatedValueDeclared` or `generatedValueStrategy` now reads `DbColumnSnapshot.idStrategy`. This keeps canonical validation aligned with the new snapshot shape instead of preserving a hidden legacy id path.

- [ ] **Step 5: Re-run core tests until special-field resolution passes**

Run: `./gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"`
Expected: PASS with managed roles and DB identity coming from the new snapshot fields.

- [ ] **Step 6: Commit the special-field slice**

```bash
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceFieldBehaviorInference.kt
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt
git add cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "refactor: resolve managed roles from db comments"
```

### Task 6: Expose Structural, Managed, Inherited, And Unresolved Inputs In Generator Contexts

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceProviderInference.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb`
- Test: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

**Interfaces:**
- Consumes: structural parent-ref facts, managed roles, inherited flags, and resolved special-field policies
- Produces: template context that can distinguish structural inputs from ordinary payload inputs

- [ ] **Step 1: Write failing generator tests for contextual distinctions**

```kotlin
@Test
fun `entity planner exposes structural and managed field metadata`() {
    val plan = planner.plan(config, model)

    val entityContext = plan.single { it.templateId == "aggregate/entity.kt.peb" }.context
    val fields = entityContext["fields"] as List<Map<String, Any?>>
    assertTrue(fields.any { it["parentRef"] == true })
    assertTrue(fields.any { it["managedRole"] == "SCOPE" })
    assertTrue(fields.any { it["inherited"] == true })
}

@Test
fun `factory planner marks unresolved constructor inputs explicitly`() {
    val plan = planner.plan(config, model)

    val factoryContext = plan.single { it.templateId == "aggregate/factory.kt.peb" }.context
    assertTrue(factoryContext.containsKey("constructorMappingResolved"))
    assertTrue(factoryContext.containsKey("constructorUnresolvedFields"))
}
```

- [ ] **Step 2: Run generator tests to show the old context lacks these markers**

Run: `./gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"`
Expected: FAIL because the current context only distinguishes legacy write flags, not structural parent refs and managed roles.

- [ ] **Step 3: Extend entity context with explicit field roles**

Add per-field context keys:

```kotlin
"parentRef" to field.parentRef,
"managedRole" to field.managedRole?.name,
"managed" to (field.managedRole != null),
"inherited" to field.inherited,
    "structuralParentRef" to field.parentRef,
```

Keep the current field ordering and existing type resolution intact.

- [ ] **Step 4: Extend factory planning with explicit unresolved constructor state**

Make factory planning emit:

```kotlin
"constructorMappingResolved" to constructorMapping.resolved,
"constructorUnresolvedFields" to constructorMapping.unresolvedFields,
"constructorStructuralFields" to constructorMapping.structuralFields,
```

This is the extension point the later owned-cardinality spec will consume when it decides how to treat child constructors.

- [ ] **Step 5: Remove DB-driven dynamic insert/update rendering from the active entity template**

The DB contract no longer owns `@DynamicInsert` or `@DynamicUpdate`, so the entity template must stop rendering them from snapshot data. Keep the rest of the JPA rendering stable.

Update `AggregatePersistenceProviderInference` at the same time so provider controls are emitted only for soft-delete and version facts still owned by the canonical model. Do not carry `dynamicInsert` or `dynamicUpdate` through `AggregatePersistenceProviderControl`.

- [ ] **Step 6: Re-run generator tests until the new context is visible**

Run: `./gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"`
Expected: PASS with structural and managed markers visible in the generated context.

- [ ] **Step 7: Commit the generator slice**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceProviderInference.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb
git add cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "refactor: expose structural db field context"
```

### Task 7: Update Functional Fixtures For The New Contract

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/**`

**Interfaces:**
- Consumes: the strict source parser, owned binding resolver, and managed-role write-surface facts from earlier tasks
- Produces: end-to-end fixture coverage that only the new annotation contract is accepted

- [ ] **Step 1: Replace old happy-path fixture comments**

Use these conversions in functional SQL fixture strings:

```sql
-- old
comment on table video_post is '@AggregateRoot=true;';
comment on table video_post_item is '@Parent=video_post;';
comment on column video_post_item.video_post_id is '@Reference=video_post;';
comment on column video_post.version is '@Version;';
comment on column video_post.deleted is '@Deleted;';
comment on column video_post.id is '@GeneratedValue=IDENTITY;';

-- new
comment on table video_post_item is '@Parent=video_post;';
comment on column video_post_item.video_post_id is '@ParentRef;';
comment on column video_post.version is '@Managed=version;';
comment on column video_post.deleted is '@Managed=deleted;';
comment on column video_post.id is '@IdStrategy=db_identity;';
```

- [ ] **Step 2: Replace removed lazy and dynamic-write failure tests**

Remove tests whose only purpose is `@Lazy`, `@DynamicInsert`, or `@DynamicUpdate` on DB-backed relations or entity tables. Add this replacement:

```kotlin
@Test
fun `cap4kGenerate fails fast when parent table has no parent ref`() {
    val result = runCap4kGenerateWithSchema(
        """
        create table video_post (id bigint primary key);
        create table video_post_item (id bigint primary key, video_post_id bigint not null);
        comment on table video_post_item is '@Parent=video_post;';
        """.trimIndent()
    )

    assertFalse(result.success)
    assertTrue(result.output.contains("table VIDEO_POST_ITEM declares @Parent=video_post but has no @ParentRef column."))
}
```

- [ ] **Step 3: Add functional unsupported old annotation test**

```kotlin
@Test
fun `cap4kGenerate rejects removed db annotations through generic path`() {
    val result = runCap4kGenerateWithSchema(
        """
        create table video_post (
            id bigint primary key,
            version bigint,
            deleted boolean
        );
        comment on table video_post is '@AggregateRoot=true;';
        comment on column video_post.version is '@Version;';
        comment on column video_post.deleted is '@Deleted;';
        """.trimIndent()
    )

    assertFalse(result.success)
    assertTrue(result.output.contains("unsupported table annotation @AggregateRoot"))
    assertTrue(result.output.contains("unsupported column annotation @Version") || result.output.contains("unsupported column annotation @Deleted"))
}
```

- [ ] **Step 4: Run the Gradle functional tests**

Run: `./gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"`
Expected: PASS with the new comments accepted and the removed annotations rejected through the generic parser path.

- [ ] **Step 5: Commit the fixture slice**

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git add cap4k-plugin-pipeline-gradle/src/test/resources
git commit -m "test: refresh db comment functional fixtures"
```

### Task 8: Sync Docs, Skills, And Validation Scans To The New Contract

**Files:**
- Modify: `docs/public/reference/db-schema-annotations.md`
- Modify: `docs/public/reference/generator-input-validation.md`
- Modify: `docs/public/generator/inputs-and-sources.md`
- Modify: `skills/cap4k-generator-inputs/references/db-schema-annotations.md`
- Modify: `.agents/skills/cap4k-generator-inputs/references/db-schema-annotations.md`
- Modify: `scripts/validate-cap4k-generator-inputs.py`
- Test: `scripts/validate-cap4k-generator-inputs.py`

**Interfaces:**
- Consumes: the final supported annotation set from the parser
- Produces: docs and validation scripts that reject stale contract drift

- [ ] **Step 1: Write a doc scan test that proves old names are gone from the active references**

```powershell
rg -n "@Count|@C=|countHint|@Reference|@Ref=|@Relation|@Rel=|@Lazy|@L=|@AggregateRoot|@Root|@R|@DynamicInsert|@DynamicUpdate" docs/public skills .agents/skills
```

Expected after the doc update: only historical specs and review notes match, not the live reference pages or live skill references.

- [ ] **Step 2: Update the public reference page**

Rewrite `docs/public/reference/db-schema-annotations.md` so it lists only:

- table annotations: `@Parent=<table>`, `@Ignore`
- column annotations: `@ParentRef`, `@Type=<TypeName>`, `@RefAggregate=<AggregateName>`, `@RefId=<TypeName>`, `@IdStrategy=db_identity`, `@Managed=system|scope|deleted|version`, `@Inherited`

The page must not mention aliases, compatibility notes, or old relation/provider annotations.

- [ ] **Step 3: Update skill references and validator text to match the public doc**

Mirror the same supported set in:

- `skills/cap4k-generator-inputs/references/db-schema-annotations.md`
- `.agents/skills/cap4k-generator-inputs/references/db-schema-annotations.md`
- `docs/public/reference/generator-input-validation.md`
- `docs/public/generator/inputs-and-sources.md`

Keep the wording present-tense and do not describe removed annotations as accepted anywhere in the active references.

- [ ] **Step 4: Update the validation script to flag old names as drift**

Make `scripts/validate-cap4k-generator-inputs.py` fail on any live-doc or live-skill reference to:

`@AggregateRoot`, `@Root`, `@R`, `@DynamicInsert`, `@DynamicUpdate`, `@Reference`, `@Relation`, `@Lazy`, `@Count`, `@Deleted`, `@Version`, `@GeneratedValue`, `@Exposed`, `@Insertable`, `@Updatable`.

- [ ] **Step 5: Re-run the validation script and the doc scans**

Run:

```powershell
python scripts/validate-cap4k-generator-inputs.py
rg -n "@Count|@C=|countHint|@Reference|@Ref=|@Relation|@Rel=|@Lazy|@L=|@AggregateRoot|@Root|@R|@DynamicInsert|@DynamicUpdate" docs/public skills .agents/skills
```

Expected: the validator passes and the scan only finds historical references outside the active contract pages.

- [ ] **Step 6: Commit the documentation slice**

```bash
git add docs/public/reference/db-schema-annotations.md
git add docs/public/reference/generator-input-validation.md
git add docs/public/generator/inputs-and-sources.md
git add skills/cap4k-generator-inputs/references/db-schema-annotations.md
git add .agents/skills/cap4k-generator-inputs/references/db-schema-annotations.md
git add scripts/validate-cap4k-generator-inputs.py
git commit -m "docs: publish strict db comment contract"
```

## Self-Review Checklist

1. The plan covers every spec section that belongs to this iteration:
   - table allow-list parsing
   - column allow-list parsing
   - parent binding without FK metadata and without `<parent_table>_id`
   - managed-role and id-strategy propagation
   - inherited-field visibility
   - generator context extension points for later owned-cardinality work
   - functional fixture coverage for the new contract
   - docs and skill sync
2. The plan intentionally does not implement owned-cardinality inference or soft-delete tombstone SQL. Those remain extension points for the sibling specs.
3. The plan removes old live annotation paths instead of keeping compatibility branches.
4. Every task has a concrete test command and a commit boundary.
