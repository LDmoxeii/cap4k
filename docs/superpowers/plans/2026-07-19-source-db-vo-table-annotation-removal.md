# Source DB Value Object Table Annotation Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove DB table `@ValueObject` / `@VO` support by treating unsupported table annotations generically, while preserving manifest-driven value-object generation.

**Architecture:** `DbTableAnnotationParser` becomes the only table comment parser and validates table annotation names against the current supported set. Source snapshots and canonical entity metadata stop carrying a source-db `valueObject` table flag. Docs, skills, and fixtures describe only the current DB annotation contract.

**Tech Stack:** Kotlin/JVM, Gradle Kotlin DSL, JUnit 5, H2 test fixtures, Markdown docs, cap4k skills, PowerShell, ripgrep.

## Global Constraints

- Work only in `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\issue-114-db-vo-removal` on branch `spec/issue-114-db-vo-removal` unless a new implementation worktree/branch is explicitly created.
- Do not implement directly on `master`.
- Do not preserve compatibility for `@ValueObject` or `@VO`.
- Do not add a value-object-specific rejection branch or migration diagnostic.
- Do not change value-object manifest generation.
- Do not change generated value-object data classes, nested converters, template ids, or artifact layout.
- Do not reintroduce a runtime `ValueObject` interface.
- Do not solve owned child factory creation inputs.
- Do not solve child entity ID strategy.
- Do not write public docs or skills as a migration story.
- Do not add public docs or skill wording that explains unsupported DB `@VO` behavior.
- Public docs and skills must describe only the current supported state.
- Run compile or tests only in an implementation turn where command side effects are allowed.
- Do not commit unless the user explicitly asks for commits.

---

## File Structure

- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParser.kt`
  - Owns DB table comment annotation parsing.
  - After this slice it supports only `@Parent/@P`, `@AggregateRoot/@Root/@R`, `@Ignore/@I`, `@DynamicInsert`, and `@DynamicUpdate`.
  - It validates unsupported table annotations generically.

- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParser.kt`
  - Owns DB column relation annotation parsing only.
  - Table parsing methods and table metadata data classes are removed.

- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
  - Converts JDBC metadata into `DbSchemaSnapshot`.
  - Stops populating `DbTableSnapshot.valueObject`.

- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
  - Defines source snapshots and canonical models.
  - Removes source-db table `valueObject` flags from `DbTableSnapshot` and `EntityModel`.

- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
  - Converts supported DB tables into canonical entities.
  - Stops copying table `valueObject` into entity metadata.

- `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParserTest.kt`
  - Table annotation parser contract tests.
  - Receives generic unsupported annotation tests and moved table relation cases.

- `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParserTest.kt`
  - Column relation parser tests.
  - Loses all `parseTable` tests.

- `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`
  - DB source integration-like tests.
  - Stops using `@VO` in table comments and stops asserting `childTable.valueObject`.

- `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
  - Canonical assembler tests.
  - Stops setting or asserting source-db entity `valueObject` flags.

- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/schema.sql`
  - Functional DB schema fixture.
  - Removes `@VO` from owned child table comments.

- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/schema.sql`
  - Functional compile schema fixture.
  - Removes `@VO` from owned child table comments.

- `docs/public/reference/db-schema-annotations.md`
  - Public current DB schema annotation reference.
  - Removes DB table `@ValueObject` / `@VO` and does not mention unsupported behavior.

- `skills/cap4k-generator-inputs/references/db-schema-annotations.md`
  - Canonical repo skill reference for DB schema annotations.
  - Matches current supported input only.

- `.agents/skills/cap4k-generator-inputs/references/db-schema-annotations.md`
  - Workspace synchronized copy of the canonical repo skill DB annotation reference.

---

### Task 1: Make Table Annotation Parsing Generic And Remove Dead Table Relation Parsing

**Files:**
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParser.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParser.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParserTest.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParserTest.kt`

**Interfaces:**
- Consumes: `DbTableAnnotationParser.parse(comment: String): DbTableAnnotationParseResult`
- Produces: `DbTableAnnotationParseResult(parentTable: String?, aggregateRoot: Boolean, ignored: Boolean, dynamicInsert: Boolean?, dynamicUpdate: Boolean?, cleanedComment: String)`
- Produces: `DbRelationAnnotationParser.parseColumn(comment: String): ColumnRelationMetadata`
- Removes: `DbRelationAnnotationParser.parseTable(comment: String): TableRelationMetadata`
- Removes: `TableRelationMetadata`

- [ ] **Step 1: Add failing table parser tests for generic unsupported annotations**

In `DbTableAnnotationParserTest.kt`, add these tests below `parser rejects valued table ignore marker`:

```kotlin
    @Test
    fun `parser rejects unsupported table annotation generically`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("Video post @CustomMarker;")
        }

        assertEquals(
            "unsupported table annotation @CustomMarker. Supported table annotations: @Parent/@P, @AggregateRoot/@Root/@R, @Ignore/@I, @DynamicInsert, @DynamicUpdate.",
            error.message,
        )
    }

    @Test
    fun `parser rejects value object table annotations through generic unsupported annotation path`() {
        val shortError = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@VO;")
        }
        val longError = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@ValueObject;")
        }

        assertEquals(
            "unsupported table annotation @VO. Supported table annotations: @Parent/@P, @AggregateRoot/@Root/@R, @Ignore/@I, @DynamicInsert, @DynamicUpdate.",
            shortError.message,
        )
        assertEquals(
            "unsupported table annotation @ValueObject. Supported table annotations: @Parent/@P, @AggregateRoot/@Root/@R, @Ignore/@I, @DynamicInsert, @DynamicUpdate.",
            longError.message,
        )
    }
```

- [ ] **Step 2: Move remaining table parser behavior tests into `DbTableAnnotationParserTest`**

In `DbTableAnnotationParserTest.kt`, add these tests below the unsupported annotation tests:

```kotlin
    @Test
    fun `parser extracts table parent annotation`() {
        val metadata = DbTableAnnotationParser.parse("@Parent=video_post;")

        assertEquals("video_post", metadata.parentTable)
        assertEquals(false, metadata.aggregateRoot)
        assertEquals("", metadata.cleanedComment)
    }

    @Test
    fun `parser extracts short table parent alias with explicit aggregate root false`() {
        val metadata = DbTableAnnotationParser.parse("@P=video_post;@Root=false;")

        assertEquals("video_post", metadata.parentTable)
        assertEquals(false, metadata.aggregateRoot)
        assertEquals("", metadata.cleanedComment)
    }

    @Test
    fun `parser rejects conflicting aggregate root aliases`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@AggregateRoot=true;@R=false;")
        }

        assertEquals("conflicting @AggregateRoot/@Root/@R annotations on the same table comment.", error.message)
    }

    @Test
    fun `parser rejects blank parent value`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@Parent=;")
        }

        assertEquals("blank @Parent/@P value is not allowed.", error.message)
    }

    @Test
    fun `parser rejects valueless parent annotation`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@Parent;")
        }

        assertEquals("missing value for @Parent/@P annotation.", error.message)
    }

    @Test
    fun `parser rejects parent combined with explicit aggregate root true`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@Parent=video_post;@AggregateRoot=true;")
        }

        assertEquals("conflicting table relation annotations: @Parent/@P cannot be combined with @AggregateRoot=true.", error.message)
    }

    @Test
    fun `parser rejects malformed aggregate root boolean`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@AggregateRoot=maybe;")
        }

        assertEquals("invalid @AggregateRoot/@Root/@R boolean value: maybe", error.message)
    }
```

- [ ] **Step 3: Update table unsupported annotation tests for existing unsupported table markers**

Replace the existing tests named `parser rejects legacy soft delete column annotation with migration message`, `parser rejects legacy id generator annotation`, and `parser rejects legacy id generator alias annotation` in `DbTableAnnotationParserTest.kt` with:

```kotlin
    @Test
    fun `parser rejects unsupported table soft delete marker generically`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@SoftDeleteColumn=deleted;")
        }

        assertEquals(
            "unsupported table annotation @SoftDeleteColumn. Supported table annotations: @Parent/@P, @AggregateRoot/@Root/@R, @Ignore/@I, @DynamicInsert, @DynamicUpdate.",
            error.message,
        )
    }

    @Test
    fun `parser rejects unsupported table id generator marker generically`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("Video post root @AggregateRoot=true;@IdGenerator=snowflakeIdGenerator;")
        }

        assertEquals(
            "unsupported table annotation @IdGenerator. Supported table annotations: @Parent/@P, @AggregateRoot/@Root/@R, @Ignore/@I, @DynamicInsert, @DynamicUpdate.",
            error.message,
        )
    }

    @Test
    fun `parser rejects unsupported table id generator alias marker generically`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("Video post root @AggregateRoot=true;@IG=snowflakeIdGenerator;")
        }

        assertEquals(
            "unsupported table annotation @IG. Supported table annotations: @Parent/@P, @AggregateRoot/@Root/@R, @Ignore/@I, @DynamicInsert, @DynamicUpdate.",
            error.message,
        )
    }
```

- [ ] **Step 4: Run the focused parser tests and verify they fail before implementation**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbTableAnnotationParserTest"
```

Expected before implementation: FAIL. The new unsupported annotation expectations fail because `@VO`, `@ValueObject`, and unknown annotations are not rejected generically yet.

- [ ] **Step 5: Implement generic table annotation validation**

In `DbTableAnnotationParser.kt`, replace the top constants and parsed annotation creation with this shape:

```kotlin
internal object DbTableAnnotationParser {
    private val annotationPattern = Regex("@([A-Za-z]+)(=([^;]*))?;?")
    private val tableAliases = setOf("PARENT", "P", "AGGREGATEROOT", "ROOT", "R", "IGNORE", "I")
    private val providerAliases = setOf("DYNAMICINSERT", "DYNAMICUPDATE")
    private val supportedAliases = tableAliases + providerAliases
    private const val supportedTableAnnotationsMessage =
        "@Parent/@P, @AggregateRoot/@Root/@R, @Ignore/@I, @DynamicInsert, @DynamicUpdate"
    private val multiSpacePattern = Regex("\\s{2,}")

    fun parse(comment: String): DbTableAnnotationParseResult {
        val annotations = annotationPattern.findAll(comment)
            .map { match ->
                val rawKey = match.groupValues[1]
                ParsedTableAnnotation(
                    rawKey = rawKey,
                    key = rawKey.uppercase(Locale.ROOT),
                    value = match.groupValues.getOrElse(3) { "" }.trim(),
                    range = match.range,
                    hasExplicitValue = match.groups[2] != null,
                )
            }
            .toList()

        rejectUnsupportedAnnotations(annotations)
```

Add this helper in the object before `resolveAnnotationValue`:

```kotlin
    private fun rejectUnsupportedAnnotations(annotations: List<ParsedTableAnnotation>) {
        val unsupported = annotations.firstOrNull { it.key !in supportedAliases } ?: return
        throw IllegalArgumentException(
            "unsupported table annotation @${unsupported.rawKey}. Supported table annotations: $supportedTableAnnotationsMessage."
        )
    }
```

Remove these old helper calls from `parse`:

```kotlin
        rejectLegacyIdGeneratorAnnotation(annotations)
        rejectLegacySoftDeleteColumnAnnotation(annotations)
```

Remove these old helper functions completely:

```kotlin
    private fun rejectLegacyIdGeneratorAnnotation(annotations: List<ParsedTableAnnotation>) {
        annotations.firstOrNull { it.key == "IDGENERATOR" }?.let {
            throw IllegalArgumentException(
                "unsupported table annotation @IdGenerator: use @GeneratedValue on the ID column instead"
            )
        }
        annotations.firstOrNull { it.key == "IG" }?.let {
            throw IllegalArgumentException(
                "unsupported table annotation @IG: use @GeneratedValue on the ID column instead"
            )
        }
    }

    private fun rejectLegacySoftDeleteColumnAnnotation(annotations: List<ParsedTableAnnotation>) {
        annotations.firstOrNull { it.key == "SOFTDELETECOLUMN" }?.let {
            throw IllegalArgumentException(
                "unsupported table annotation @SoftDeleteColumn: use @Deleted marker on the delete column instead"
            )
        }
    }
```

- [ ] **Step 6: Remove table value-object parsing from `DbTableAnnotationParser`**

In `DbTableAnnotationParser.kt`, remove this block:

```kotlin
        val valueObject = resolvePresenceAnnotation(
            annotations = annotations,
            aliases = setOf("VALUEOBJECT", "VO"),
            invalidValueMessage = "invalid @ValueObject/@VO annotation: explicit values are not supported.",
        )
```

In the `DbTableAnnotationParseResult` construction, remove:

```kotlin
            valueObject = valueObject,
```

Change cleaned comment stripping to use only supported aliases:

```kotlin
            cleanedComment = stripRecognizedAnnotations(comment, supportedAliases),
```

Change the data class to:

```kotlin
internal data class DbTableAnnotationParseResult(
    val parentTable: String? = null,
    val aggregateRoot: Boolean = true,
    val ignored: Boolean = false,
    val dynamicInsert: Boolean? = null,
    val dynamicUpdate: Boolean? = null,
    val cleanedComment: String = "",
)
```

Change `ParsedTableAnnotation` to include the raw key:

```kotlin
private data class ParsedTableAnnotation(
    val rawKey: String,
    val key: String,
    val value: String,
    val range: IntRange,
    val hasExplicitValue: Boolean,
)
```

In `stripRecognizedAnnotations`, change the local parsed annotation creation to:

```kotlin
                val rawKey = match.groupValues[1]
                ParsedTableAnnotation(
                    rawKey = rawKey,
                    key = rawKey.uppercase(Locale.ROOT),
                    value = match.groupValues.getOrElse(3) { "" }.trim(),
                    range = match.range,
                    hasExplicitValue = match.groups[2] != null,
                )
```

- [ ] **Step 7: Remove dead table parsing from `DbRelationAnnotationParser`**

In `DbRelationAnnotationParser.kt`, delete the full `parseTable` function. The deletion starts at this signature:

```kotlin
    fun parseTable(comment: String): TableRelationMetadata {
```

The deletion ends at the closing brace immediately before this remaining column parser signature:

```kotlin
    fun parseColumn(comment: String): ColumnRelationMetadata {
```

Delete this helper because only table parsing used it:

```kotlin
    private fun resolvePresenceAnnotation(
        annotations: List<RelationParsedAnnotation>,
        aliases: Set<String>,
        invalidValueMessage: String,
    ): Boolean {
        val matchingAnnotations = annotations.filter { it.key in aliases }
        require(matchingAnnotations.none { it.hasExplicitValue }) { invalidValueMessage }
        return matchingAnnotations.isNotEmpty()
    }
```

Delete these companion constants:

```kotlin
        private val VALUE_OBJECT_ALIASES = setOf("VALUEOBJECT", "VO")
        private val TABLE_RELATION_ALIASES = setOf("PARENT", "P", "AGGREGATEROOT", "ROOT", "R", "VALUEOBJECT", "VO")
```

Delete this data class:

```kotlin
internal data class TableRelationMetadata(
    val parentTable: String? = null,
    val aggregateRoot: Boolean = true,
    val valueObject: Boolean = false,
    val cleanedComment: String = "",
)
```

- [ ] **Step 8: Remove table parser tests from `DbRelationAnnotationParserTest`**

In `DbRelationAnnotationParserTest.kt`, delete these tests:

```kotlin
fun `parses table parent and value object annotations`()
fun `parses short table aliases and long value object annotation`()
fun `standalone value object keeps default aggregate root semantics`()
fun `rejects conflicting aggregate root aliases`()
fun `rejects blank parent value`()
fun `rejects valueless parent annotation`()
fun `rejects valued value object annotation`()
fun `rejects parent combined with explicit aggregate root true`()
fun `rejects malformed aggregate root boolean`()
```

Keep all `parseColumn` tests unchanged.

- [ ] **Step 9: Run focused parser tests**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbTableAnnotationParserTest" --tests "com.only4.cap4k.plugin.pipeline.source.db.DbRelationAnnotationParserTest"
```

Expected after implementation: PASS.

- [ ] **Step 10: Static verification for parser boundaries**

Run:

```powershell
rg -n "parseTable|TableRelationMetadata|VALUE_OBJECT_ALIASES|TABLE_RELATION_ALIASES|ValueObject/@VO|VALUEOBJECT|\\bVO\\b|valueObject = valueObject|val valueObject" cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db
```

Expected after implementation: no matches for removed table parser or source-db table value-object parsing. Matches for manifest-independent words should not appear in source-db main code.

- [ ] **Step 11: Commit gate**

Run only if the user explicitly asks for commits:

```powershell
git add cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParser.kt `
        cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParser.kt `
        cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParserTest.kt `
        cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParserTest.kt
git commit -m "refactor: remove db value object table annotation parsing"
```

---

### Task 2: Remove Source-DB ValueObject Flags From API And Canonical Assembly

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Inspect and modify when the static scan in Step 8 finds named `valueObject` arguments: test files under `cap4k-plugin-pipeline-api/src/test/kotlin`, `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin`, `cap4k-plugin-pipeline-generator-design/src/test/kotlin`, `cap4k-plugin-pipeline-generator-common/src/test/kotlin`, and `cap4k-plugin-pipeline-generator-types/src/test/kotlin`

**Interfaces:**
- Consumes: Task 1 output where `DbTableAnnotationParseResult` no longer has `valueObject`
- Produces: `DbTableSnapshot` without `valueObject`
- Produces: `EntityModel` without `valueObject`
- Preserves: `CanonicalModel.valueObjects: List<ValueObjectModel>`

- [ ] **Step 1: Run the pre-change value-object flag scan**

Run:

```powershell
rg -n "\\.valueObject|valueObject = true|valueObject = false|valueObject = !aggregateRoot|valueObject = table\\.valueObject|valueObject = tableMetadata\\.valueObject|val valueObject: Boolean = false" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-source-db cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-generator-design cap4k-plugin-pipeline-generator-common cap4k-plugin-pipeline-generator-types --glob "!**/build/**"
```

Expected before implementation: matches in `PipelineModels.kt`, `DbSchemaSourceProvider.kt`, `DefaultCanonicalAssembler.kt`, source-db tests, and core tests.

- [ ] **Step 2: Remove `valueObject` from source snapshot and canonical entity models**

In `PipelineModels.kt`, change `DbTableSnapshot` from:

```kotlin
data class DbTableSnapshot(
    val tableName: String,
    val comment: String,
    val columns: List<DbColumnSnapshot>,
    val primaryKey: List<String>,
    val uniqueConstraints: List<UniqueConstraintModel>,
    val parentTable: String? = null,
    val aggregateRoot: Boolean = true,
    val valueObject: Boolean = false,
    val dynamicInsert: Boolean? = null,
    val dynamicUpdate: Boolean? = null,
)
```

to:

```kotlin
data class DbTableSnapshot(
    val tableName: String,
    val comment: String,
    val columns: List<DbColumnSnapshot>,
    val primaryKey: List<String>,
    val uniqueConstraints: List<UniqueConstraintModel>,
    val parentTable: String? = null,
    val aggregateRoot: Boolean = true,
    val dynamicInsert: Boolean? = null,
    val dynamicUpdate: Boolean? = null,
)
```

Change `EntityModel` from:

```kotlin
data class EntityModel(
    val name: String,
    val packageName: String,
    val tableName: String,
    val comment: String,
    val fields: List<FieldModel>,
    val idField: FieldModel,
    val uniqueConstraints: List<UniqueConstraintModel> = emptyList(),
    val aggregateRoot: Boolean = true,
    val valueObject: Boolean = false,
    val parentEntityName: String? = null,
)
```

to:

```kotlin
data class EntityModel(
    val name: String,
    val packageName: String,
    val tableName: String,
    val comment: String,
    val fields: List<FieldModel>,
    val idField: FieldModel,
    val uniqueConstraints: List<UniqueConstraintModel> = emptyList(),
    val aggregateRoot: Boolean = true,
    val parentEntityName: String? = null,
)
```

- [ ] **Step 3: Remove source-db population of table value-object metadata**

In `DbSchemaSourceProvider.kt`, remove this named argument from the `DbTableSnapshot` constructor call:

```kotlin
                valueObject = tableMetadata.valueObject,
```

Keep the surrounding arguments:

```kotlin
                parentTable = tableMetadata.parentTable,
                aggregateRoot = tableMetadata.aggregateRoot,
                dynamicInsert = tableMetadata.dynamicInsert,
                dynamicUpdate = tableMetadata.dynamicUpdate,
```

- [ ] **Step 4: Remove canonical entity value-object copying**

In `DefaultCanonicalAssembler.kt`, remove this named argument from the `EntityModel` constructor call:

```kotlin
                    valueObject = table.valueObject,
```

Keep the surrounding arguments:

```kotlin
                    uniqueConstraints = table.uniqueConstraints,
                    aggregateRoot = table.aggregateRoot,
                    parentEntityName = when {
```

- [ ] **Step 5: Update DB schema source test fixture**

In `DbSchemaSourceProviderTest.kt`, replace:

```kotlin
                statement.execute("comment on table video_post_item is 'Video post item @Parent=video_post;@VO;'")
```

with:

```kotlin
                statement.execute("comment on table video_post_item is 'Video post item @Parent=video_post;'")
```

Remove this assertion:

```kotlin
        assertEquals(true, childTable.valueObject)
```

Keep these assertions:

```kotlin
        assertEquals(true, rootTable.aggregateRoot)
        assertEquals("video_post", childTable.parentTable)
        assertEquals("Video post root", rootTable.comment)
        assertEquals("Video post item", childTable.comment)
```

- [ ] **Step 6: Update canonical assembler test helper**

In `DefaultCanonicalAssemblerTest.kt`, change the helper function at the bottom of the file from:

```kotlin
    private fun table(
        name: String,
        columns: List<DbColumnSnapshot>,
        primaryKey: List<String>,
        aggregateRoot: Boolean,
        parentTable: String? = null,
    ): DbTableSnapshot = DbTableSnapshot(
        tableName = name,
        comment = "",
        columns = columns,
        primaryKey = primaryKey,
        uniqueConstraints = emptyList(),
        parentTable = parentTable,
        aggregateRoot = aggregateRoot,
        valueObject = !aggregateRoot,
    )
```

to:

```kotlin
    private fun table(
        name: String,
        columns: List<DbColumnSnapshot>,
        primaryKey: List<String>,
        aggregateRoot: Boolean,
        parentTable: String? = null,
    ): DbTableSnapshot = DbTableSnapshot(
        tableName = name,
        comment = "",
        columns = columns,
        primaryKey = primaryKey,
        uniqueConstraints = emptyList(),
        parentTable = parentTable,
        aggregateRoot = aggregateRoot,
    )
```

- [ ] **Step 7: Remove explicit core test fixture value-object flags**

Run:

```powershell
rg -n "valueObject = true|valueObject = false|\\.valueObject" cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
```

For each `DbTableSnapshot` or `EntityModel` fixture constructor call that contains:

```kotlin
                            valueObject = true,
```

or:

```kotlin
                    valueObject = true,
```

remove that named argument.

For the assertions in the owned child entity test, remove:

```kotlin
        assertEquals(false, root.valueObject)
        assertEquals(true, child.valueObject)
```

Keep:

```kotlin
        assertEquals(true, root.aggregateRoot)
        assertEquals(null, root.parentEntityName)
        assertEquals(false, child.aggregateRoot)
        assertEquals("VideoPost", child.parentEntityName)
```

- [ ] **Step 8: Remove any other named `EntityModel` or `DbTableSnapshot` valueObject arguments**

Run:

```powershell
rg -n "valueObject = true|valueObject = false|valueObject = !aggregateRoot|\\.valueObject" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-source-db cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-generator-design cap4k-plugin-pipeline-generator-common cap4k-plugin-pipeline-generator-types --glob "!**/build/**"
```

For every match that is a `DbTableSnapshot` or `EntityModel` named argument, remove the complete line whose named argument is `valueObject`.

For every match that is an assertion against `DbTableSnapshot.valueObject` or `EntityModel.valueObject`, delete the assertion and keep the ownership assertion through `aggregateRoot`, `parentTable`, or `parentEntityName`.

Do not remove matches that refer to manifest-driven `ValueObjectModel`, `ValueObjectManifestSnapshot`, `CanonicalModel.valueObjects`, or `ProjectConfig.artifactLayout.valueObject`.

- [ ] **Step 9: Run focused source-db and core tests**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-source-db:test
./gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected after implementation: PASS.

- [ ] **Step 10: Static verification for data model cleanup**

Run:

```powershell
rg -n "val valueObject: Boolean|\\.valueObject|valueObject = true|valueObject = false|valueObject = !aggregateRoot|valueObject = table\\.valueObject|valueObject = tableMetadata\\.valueObject" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-source-db cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-generator-design cap4k-plugin-pipeline-generator-common cap4k-plugin-pipeline-generator-types --glob "!**/build/**"
```

Expected after implementation: no matches for source-db table or canonical entity value-object flags. Manifest-driven `valueObjects` plural may still match in broader searches and is supported.

- [ ] **Step 11: Static verification for preserved manifest-driven value-object support**

Run:

```powershell
rg -n "types\\.valueObjectManifest|ValueObjectModel|ValueObjectManifestSnapshot|ValueObjectArtifactPlanner|value_object\\.kt\\.peb|family = \"value-object\"|CanonicalModel\\(.*valueObjects|val valueObjects" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-generator-types cap4k-plugin-pipeline-source-value-object-manifest cap4k-plugin-pipeline-renderer-pebble docs/public --glob "!**/build/**"
```

Expected after implementation: matches remain, proving the manifest-driven value-object path still exists.

- [ ] **Step 12: Commit gate**

Run only if the user explicitly asks for commits:

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt `
        cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt `
        cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt `
        cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt `
        cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "refactor: remove db value object metadata flag"
```

---

### Task 3: Update Fixtures, Public Docs, And Skills To Current DB Annotation Contract

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/schema.sql`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/schema.sql`
- Modify: `docs/public/reference/db-schema-annotations.md`
- Modify: `skills/cap4k-generator-inputs/references/db-schema-annotations.md`
- Modify: `.agents/skills/cap4k-generator-inputs/references/db-schema-annotations.md`

**Interfaces:**
- Consumes: Task 1 parser contract and Task 2 data model cleanup.
- Produces: DB schema examples and agent references that list only current supported DB annotations.
- Preserves: present-tense manifest-driven value-object docs elsewhere.

- [ ] **Step 1: Update functional DB schema fixtures**

In `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/schema.sql`, replace:

```sql
comment on table video_post_item is '@Parent=video_post;@VO;';
```

with:

```sql
comment on table video_post_item is '@Parent=video_post;';
```

In `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/schema.sql`, replace:

```sql
comment on table video_post_item is '@Parent=video_post;@VO;';
```

with:

```sql
comment on table video_post_item is '@Parent=video_post;';
```

- [ ] **Step 2: Rewrite public DB annotation reference as current-state docs**

Replace the full content of `docs/public/reference/db-schema-annotations.md` with:

```markdown
# DB Schema Annotations

DB/schema comments are generator input facts for persistence and aggregate structure. The generator reads these annotations from DDL or database metadata.

DB table comments describe table ownership, aggregate-root metadata, exclusion, and JPA provider controls. Value Object inputs use `types.valueObjectManifest`; they are not DB table annotations.

## Table Comment Annotations

| Annotation | Meaning |
| --- | --- |
| `@Parent=<table>` / `@P=<table>` | Marks the table as an owned child entity table of another table. |
| `@AggregateRoot=<bool>` / `@Root=<bool>` / `@R=<bool>` | Explicitly marks aggregate-root state. |
| `@Ignore` / `@I` | Excludes the table from generation. |
| `@DynamicInsert=<bool>` | Declares dynamic insert metadata. |
| `@DynamicUpdate=<bool>` | Declares dynamic update metadata. |

## Column Comment Annotations

| Annotation | Meaning |
| --- | --- |
| `@T=<TypeName>` / `@TYPE=<TypeName>` | Overrides or binds the generated field type. |
| `@E=<items>` / `@ENUM=<items>` | Declares enum items; requires `@T` / `@TYPE`. |
| `@RefId=<TypeName>` | Marks an external reference identity type in the current context. |
| `@Deleted` | Marks a soft-delete column. |
| `@Version` | Marks an optimistic-lock version column. |
| `@GeneratedValue=identity` / `@GeneratedValue=database-identity` | Marks explicit database identity semantics. |
| `@Managed` | Marks framework-managed field metadata. |
| `@Inherited` | Marks inherited field metadata. |
| `@Reference=<table>` / `@Ref=<table>` | Specifies the referenced table for relation metadata. |
| `@Relation=<type>` / `@Rel=<type>` | Specifies relation type. |
| `@Lazy=<bool>` / `@L=<bool>` | Marks lazy relation metadata. |
| `@Count=<value>` / `@C=<value>` | Marks relation count metadata. |
| `@RefAggregate=<AggregateName>` | References another aggregate by aggregate name. |

## Rules

- Unsupported table annotations fail table comment parsing.
- `@Parent` / `@P` cannot combine with aggregate-root true.
- Presence annotations do not take explicit values.
- Boolean values use strict lowercase `true` or `false`.
- `@E` / `@ENUM` requires `@T` / `@TYPE`.
- `@Relation` / `@Rel`, `@Lazy` / `@L`, and `@Count` / `@C` require `@Reference` or `@Ref` on the same column.
- `@Relation` / `@Rel` supports `MANY_TO_ONE`, `ONE_TO_ONE`, `1:1`, `*:1`, `MANYTOONE`, and `ONETOONE`.
- `@RefAggregate` conflicts with `@Reference` / `@Ref`.
- `@RefAggregate` conflicts with `@RefId`.
```

- [ ] **Step 3: Rewrite canonical cap4k generator-inputs skill DB annotation reference**

Replace the full content of `skills/cap4k-generator-inputs/references/db-schema-annotations.md` with:

```markdown
# DB Schema Annotations

Use these annotations in DB/schema DDL comments when the schema is the selected generator input surface.

DB table comments describe table ownership, aggregate-root metadata, exclusion, and JPA provider controls. Value Object inputs use `types.valueObjectManifest`; they are not DB table annotations.

## Table Annotations

- `@Parent=<table>` / `@P=<table>`
- `@AggregateRoot=<bool>` / `@Root=<bool>` / `@R=<bool>`
- `@Ignore` / `@I`
- `@DynamicInsert=<bool>`
- `@DynamicUpdate=<bool>`

## Column Annotations

- `@T=<TypeName>` / `@TYPE=<TypeName>`
- `@E=<items>` / `@ENUM=<items>`
- `@RefId=<TypeName>`
- `@Deleted`
- `@Version`
- `@GeneratedValue=identity`
- `@GeneratedValue=database-identity`
- `@Managed`
- `@Inherited`
- `@Reference=<table>` / `@Ref=<table>`
- `@Relation=<type>` / `@Rel=<type>`
- `@Lazy=<bool>` / `@L=<bool>`
- `@Count=<value>` / `@C=<value>`
- `@RefAggregate=<AggregateName>`

## Rules

- Unsupported table annotations fail table comment parsing.
- Presence annotations do not take explicit values.
- Boolean values are strict lowercase `true` or `false`.
- `@Parent` / `@P` cannot combine with `@AggregateRoot=true`, `@Root=true`, or `@R=true`.
- `@E` / `@ENUM` requires `@T` / `@TYPE`.
- `@Relation` / `@Rel`, `@Lazy` / `@L`, and `@Count` / `@C` require `@Reference` or `@Ref` on the same column.
- `@Relation` / `@Rel` supports `MANY_TO_ONE`, `ONE_TO_ONE`, `1:1`, `*:1`, `MANYTOONE`, and `ONETOONE`.
- `@RefAggregate` conflicts with `@Reference` / `@Ref`.
- `@RefAggregate` conflicts with `@RefId`.
```

- [ ] **Step 4: Sync workspace `.agents` skill DB annotation reference**

Copy the exact content from `skills/cap4k-generator-inputs/references/db-schema-annotations.md` into `.agents/skills/cap4k-generator-inputs/references/db-schema-annotations.md`.

The two files must be byte-for-byte equal after this task:

```powershell
Compare-Object `
  (Get-Content -Path "skills/cap4k-generator-inputs/references/db-schema-annotations.md") `
  (Get-Content -Path ".agents/skills/cap4k-generator-inputs/references/db-schema-annotations.md")
```

Expected after implementation: no output.

- [ ] **Step 5: Run docs and skill static scan**

Run:

```powershell
rg -n "@ValueObject|@VO|\\bVO\\b|migration|historical|removed|legacy" docs/public/reference/db-schema-annotations.md skills/cap4k-generator-inputs/references/db-schema-annotations.md .agents/skills/cap4k-generator-inputs/references/db-schema-annotations.md
```

Expected after implementation: no matches.

- [ ] **Step 6: Run fixture static scan**

Run:

```powershell
rg -n "@VO|@ValueObject" cap4k-plugin-pipeline-gradle/src/test/resources/functional --glob "!**/build/**"
```

Expected after implementation: no matches.

- [ ] **Step 7: Run focused Gradle functional test if command side effects are allowed**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"
```

Expected after implementation: PASS, unless unrelated pre-existing functional test debt appears. If unrelated failures appear, record exact failing test names and run the narrower tests that consume `aggregate-relation-sample` and `aggregate-relation-compile-sample`.

- [ ] **Step 8: Commit gate**

Run only if the user explicitly asks for commits:

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/schema.sql `
        cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/schema.sql `
        docs/public/reference/db-schema-annotations.md `
        skills/cap4k-generator-inputs/references/db-schema-annotations.md `
        .agents/skills/cap4k-generator-inputs/references/db-schema-annotations.md
git commit -m "docs: align db schema annotations with current contract"
```

---

### Task 4: Final Static Acceptance And Issue Evidence

**Files:**
- Inspect: all modified files from Tasks 1 through 3
- Inspect: `docs/superpowers/specs/2026-07-19-source-db-vo-table-annotation-removal-design.md`
- Inspect: `docs/superpowers/plans/2026-07-19-source-db-vo-table-annotation-removal.md`

**Interfaces:**
- Consumes: completed Tasks 1 through 3.
- Produces: final evidence summary for issue `#114`.

- [ ] **Step 1: Check worktree and branch**

Run:

```powershell
git branch --show-current
git status --short
```

Expected branch:

```text
spec/issue-114-db-vo-removal
```

Expected status: only files intentionally changed for issue `#114`.

- [ ] **Step 2: Run source-db table annotation acceptance scan**

Run:

```powershell
rg -n "@ValueObject|@VO|\\bVO\\b" cap4k-plugin-pipeline-source-db cap4k-plugin-pipeline-gradle/src/test/resources/functional docs/public skills .agents/skills --glob "!**/build/**"
```

Expected after implementation:

- no source-db main code support for `@ValueObject` or `@VO`
- no functional schema fixture uses `@VO`
- no public DB annotation doc or skill lists `@ValueObject` or `@VO`
- source-db tests may contain `@VO` and `@ValueObject` only in tests that assert generic unsupported annotation validation

- [ ] **Step 3: Run source-db value-object flag acceptance scan**

Run:

```powershell
rg -n "val valueObject: Boolean|\\.valueObject|valueObject = true|valueObject = false|valueObject = !aggregateRoot|valueObject = table\\.valueObject|valueObject = tableMetadata\\.valueObject" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-source-db cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-generator-design cap4k-plugin-pipeline-generator-common cap4k-plugin-pipeline-generator-types --glob "!**/build/**"
```

Expected after implementation: no matches for source-db table or canonical entity value-object flags.

- [ ] **Step 4: Run manifest-driven value-object preservation scan**

Run:

```powershell
rg -n "types\\.valueObjectManifest|ValueObjectModel|ValueObjectManifestSnapshot|ValueObjectArtifactPlanner|value_object\\.kt\\.peb|family = \"value-object\"|CanonicalModel\\(.*valueObjects|val valueObjects" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-generator-types cap4k-plugin-pipeline-source-value-object-manifest cap4k-plugin-pipeline-renderer-pebble docs/public --glob "!**/build/**"
```

Expected after implementation: matches remain.

- [ ] **Step 5: Run focused tests if command side effects are allowed**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-source-db:test
./gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
./gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"
```

Expected after implementation: PASS. If broader Gradle functional tests expose unrelated fixture debt, record exact failures and run narrower impacted tests if identifiable.

- [ ] **Step 6: Review final diff**

Run:

```powershell
git diff --stat
git diff -- cap4k-plugin-pipeline-source-db cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-gradle docs/public/reference/db-schema-annotations.md skills/cap4k-generator-inputs/references/db-schema-annotations.md .agents/skills/cap4k-generator-inputs/references/db-schema-annotations.md docs/superpowers/specs/2026-07-19-source-db-vo-table-annotation-removal-design.md docs/superpowers/plans/2026-07-19-source-db-vo-table-annotation-removal.md
```

Expected diff:

- `DbTableAnnotationParser` uses generic unsupported table annotation validation.
- `DbRelationAnnotationParser` keeps column parsing only.
- `DbTableSnapshot` and `EntityModel` no longer expose a source-db `valueObject` flag.
- source-db tests and core tests no longer assert source-db value-object table flags.
- functional schema fixtures use `@Parent=<table>` without `@VO`.
- public DB annotation docs and skills list only current supported DB annotations.
- manifest-driven value-object files are not removed.

- [ ] **Step 7: Prepare issue lifecycle evidence**

Prepare this issue update text for `#114`:

```markdown
Spec and plan are written:

- Spec: `docs/superpowers/specs/2026-07-19-source-db-vo-table-annotation-removal-design.md`
- Plan: `docs/superpowers/plans/2026-07-19-source-db-vo-table-annotation-removal.md`

Scope decision:

- No compatibility path for DB `@ValueObject` / `@VO`.
- Unsupported table annotations use generic validation; `@ValueObject` / `@VO` are not special cases.
- Public docs and skills describe only the current supported DB annotation contract.
```

Update the issue lifecycle only when GitHub issue write access is available and the user approves the update.

- [ ] **Step 8: Commit gate**

Run only if the user explicitly asks for commits:

```powershell
git add docs/superpowers/specs/2026-07-19-source-db-vo-table-annotation-removal-design.md `
        docs/superpowers/plans/2026-07-19-source-db-vo-table-annotation-removal.md
git commit -m "docs: plan db value object table annotation removal"
```

---

## Final Review Checklist

- [ ] `DbTableAnnotationParser` has one generic unsupported table annotation validation path.
- [ ] There is no `@VO` or `@ValueObject` special-case rejection branch.
- [ ] `DbRelationAnnotationParser` has no `parseTable` method.
- [ ] `TableRelationMetadata` is gone.
- [ ] `DbTableSnapshot.valueObject` is gone.
- [ ] `EntityModel.valueObject` is gone.
- [ ] Source-db tests and functional schema fixtures do not model owned child tables with `@VO`.
- [ ] Public DB annotation docs do not list `@ValueObject` or `@VO`.
- [ ] cap4k skills do not route agents toward `@VO`.
- [ ] `.agents/skills/cap4k-generator-inputs/references/db-schema-annotations.md` matches `skills/cap4k-generator-inputs/references/db-schema-annotations.md`.
- [ ] Manifest-driven value-object support remains visible in static scans.
- [ ] No compile/run/test/install command was run unless command side effects were allowed.
- [ ] No commit was created unless the user explicitly asked for commits.
