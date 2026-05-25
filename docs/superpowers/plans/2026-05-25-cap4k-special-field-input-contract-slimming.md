# Cap4k Special Field Input Contract Slimming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove redundant DB column annotations, add `@Inherited`, and make inherited columns omit default generated entity fields while preserving canonical schema facts.

**Architecture:** Keep DB schema facts intact in `DbColumnSnapshot`, `FieldModel`, and canonical planning. Add one marker flag for inherited columns and consume it only at aggregate entity render planning. Parser removals are fail-fast input-contract changes, while docs are updated to match the actual primary-key source of truth.

**Tech Stack:** Kotlin, Gradle, JUnit 5, cap4k pipeline DB source, canonical assembler, aggregate generator, Pebble renderer.

---

## File Map

- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
  - Add `inherited: Boolean?` to `DbColumnSnapshot`.
  - Add `inherited: Boolean = false` to `FieldModel`.
- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt`
  - Parse marker-only `@Inherited`.
  - Reject removed annotations.
  - Restrict `@GeneratedValue` to explicit `identity` / `database-identity`.
- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
  - Copy parser `inherited` metadata into `DbColumnSnapshot`.
- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
  - Copy `DbColumnSnapshot.inherited == true` into `FieldModel.inherited`.
- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
  - Filter inherited scalar fields out of `scalarFields` render context only.
- `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt`
  - Parser red/green coverage.
- `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`
  - Source-provider propagation coverage.
- `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
  - Canonical preservation coverage.
- `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
  - Entity artifact omission coverage.
- `docs/public/authoring/generator/input-sources.md`
  - Update active public annotation contract.
- `docs/public/reference/generator-dsl.md`
  - Remove removed annotations if present.

---

### Task 1: Parser Contract For `@Inherited` And Removed Annotations

**Files:**
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt`

- [ ] **Step 1: Write failing parser tests**

Add tests:

```kotlin
@Test
fun `parser supports inherited marker`() {
    val metadata = DbColumnAnnotationParser.parse("created at @Inherited;")

    assertEquals(true, metadata.inherited)
}

@Test
fun `parser rejects valued inherited marker`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
        DbColumnAnnotationParser.parse("@Inherited=true;")
    }

    assertEquals("invalid @Inherited annotation: explicit values are not supported.", error.message)
}

@Test
fun `parser rejects removed generated value strategies and marker`() {
    val markerError = assertThrows(IllegalArgumentException::class.java) {
        DbColumnAnnotationParser.parse("@GeneratedValue;")
    }
    val uuidError = assertThrows(IllegalArgumentException::class.java) {
        DbColumnAnnotationParser.parse("@GeneratedValue=uuid7;")
    }
    val snowflakeError = assertThrows(IllegalArgumentException::class.java) {
        DbColumnAnnotationParser.parse("@GeneratedValue=snowflake-long;")
    }

    assertEquals("invalid @GeneratedValue annotation: explicit database strategy is required.", markerError.message)
    assertEquals("unsupported @GeneratedValue strategy: uuid7", uuidError.message)
    assertEquals("unsupported @GeneratedValue strategy: snowflake-long", snowflakeError.message)
}

@Test
fun `parser rejects removed exposed and jpa mutability annotations`() {
    val exposedError = assertThrows(IllegalArgumentException::class.java) {
        DbColumnAnnotationParser.parse("@Exposed;")
    }
    val insertableError = assertThrows(IllegalArgumentException::class.java) {
        DbColumnAnnotationParser.parse("@Insertable=false;")
    }
    val updatableError = assertThrows(IllegalArgumentException::class.java) {
        DbColumnAnnotationParser.parse("@Updatable=false;")
    }

    assertEquals("unsupported column annotation @Exposed: remove broad managed defaults or stop marking this field managed.", exposedError.message)
    assertEquals("unsupported column annotation @Insertable: use template overrides for JPA-specific mutability.", insertableError.message)
    assertEquals("unsupported column annotation @Updatable: use template overrides for JPA-specific mutability.", updatableError.message)
}
```

- [ ] **Step 2: Run parser tests and verify RED**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-source-db:test --tests "*DbColumnAnnotationParserTest*"
```

Expected: FAIL because `inherited` does not exist, removed annotations are still accepted, or existing messages differ.

- [ ] **Step 3: Implement parser changes**

In `DbColumnAnnotationParser.kt`:

```kotlin
val inherited = resolveMarkerAnnotation(annotations, "INHERITED", "Inherited")
rejectRemovedAnnotations(annotations)
```

Restrict generated values:

```kotlin
private val supportedGeneratedValueStrategies = setOf("identity", "database-identity")
```

Reject marker generated value:

```kotlin
require(markerAnnotations.isEmpty()) {
    "invalid @GeneratedValue annotation: explicit database strategy is required."
}
```

Add removed-annotation rejection:

```kotlin
private fun rejectRemovedAnnotations(annotations: List<ParsedAnnotation>) {
    annotations.firstOrNull { it.key == "EXPOSED" }?.let {
        throw IllegalArgumentException(
            "unsupported column annotation @Exposed: remove broad managed defaults or stop marking this field managed."
        )
    }
    annotations.firstOrNull { it.key == "INSERTABLE" }?.let {
        throw IllegalArgumentException(
            "unsupported column annotation @Insertable: use template overrides for JPA-specific mutability."
        )
    }
    annotations.firstOrNull { it.key == "UPDATABLE" }?.let {
        throw IllegalArgumentException(
            "unsupported column annotation @Updatable: use template overrides for JPA-specific mutability."
        )
    }
}
```

Return `inherited` from `DbColumnAnnotationParseResult`.

- [ ] **Step 4: Run parser tests and verify GREEN**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-source-db:test --tests "*DbColumnAnnotationParserTest*"
```

Expected: PASS.

- [ ] **Step 5: Commit parser contract**

```powershell
git add cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt
git commit -m "feat: slim db column annotation parser contract"
```

---

### Task 2: Propagate `@Inherited` Through Source And Canonical Models

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Write failing source-provider test**

Add a test in `DbSchemaSourceProviderTest.kt`:

```kotlin
@Test
fun `provider carries inherited column marker into db snapshot`() {
    val url = "jdbc:h2:mem:cap4k-db-source-inherited-column;MODE=MySQL;DB_CLOSE_DELAY=-1"
    DriverManager.getConnection(url).use { connection ->
        connection.createStatement().use { statement ->
            statement.execute(
                """
                create table content (
                    id varchar(36) primary key,
                    title varchar(100) not null,
                    created_at timestamp not null comment '@Inherited;@Managed;'
                )
                """.trimIndent()
            )
        }
    }

    val snapshot = DbSchemaSourceProvider().collect(
        ProjectConfig(
            basePackage = "com.acme.demo",
            sources = mapOf("db" to SourceConfig("db", mapOf("url" to url))),
        )
    )

    val createdAt = snapshot.tables.single().columns.single { it.name.equals("CREATED_AT", true) }
    assertEquals(true, createdAt.inherited)
    assertEquals(true, createdAt.managed)
}
```

- [ ] **Step 2: Write failing canonical assembler test**

Add a test in `DefaultCanonicalAssemblerTest.kt`:

```kotlin
@Test
fun `db inherited columns remain canonical fields with inherited flag`() {
    val snapshot = DbSchemaSnapshot(
        tables = listOf(
            DbTableSnapshot(
                tableName = "content",
                comment = "",
                columns = listOf(
                    DbColumnSnapshot("id", "VARCHAR", "String", false, isPrimaryKey = true),
                    DbColumnSnapshot("created_at", "TIMESTAMP", "java.time.Instant", false, inherited = true, managed = true),
                ),
                primaryKey = listOf("id"),
                uniqueConstraints = emptyList(),
            )
        )
    )

    val model = DefaultCanonicalAssembler().assemble(ProjectConfig(basePackage = "com.acme.demo"), listOf(snapshot))
    val field = model.entities.single().fields.single { it.name == "createdAt" }

    assertEquals(true, field.inherited)
}
```

- [ ] **Step 3: Run tests and verify RED**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-source-db:test --tests "*DbSchemaSourceProviderTest*inherited*" :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest*inherited*"
```

Expected: FAIL because model properties do not exist or are not propagated.

- [ ] **Step 4: Implement model propagation**

In `PipelineModels.kt`:

```kotlin
data class FieldModel(
    ...
    val columnName: String? = null,
    val inherited: Boolean = false,
)
```

```kotlin
data class DbColumnSnapshot(
    ...
    val updatable: Boolean? = null,
    val inherited: Boolean? = null,
)
```

In `DbColumnAnnotationParseResult` add:

```kotlin
val inherited: Boolean? = null,
```

In `DbSchemaSourceProvider.kt`:

```kotlin
inherited = annotationMetadata.inherited,
```

In `DefaultCanonicalAssembler.kt` when creating `FieldModel`:

```kotlin
inherited = it.inherited == true,
```

- [ ] **Step 5: Run tests and verify GREEN**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-source-db:test --tests "*DbSchemaSourceProviderTest*inherited*" :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest*inherited*"
```

Expected: PASS.

- [ ] **Step 6: Commit propagation**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: propagate inherited db column metadata"
```

---

### Task 3: Omit Inherited Scalar Fields From Default Entity Generation

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`

- [ ] **Step 1: Write failing aggregate planner test**

Add a test:

```kotlin
@Test
fun `entity planner omits inherited scalar fields from default entity render context`() {
    val entity = EntityModel(
        name = "Content",
        packageName = "com.acme.demo.domain.content",
        tableName = "content",
        comment = "",
        fields = listOf(
            FieldModel("id", "ContentId", columnName = "id"),
            FieldModel("title", "String", columnName = "title"),
            FieldModel("createdAt", "java.time.Instant", columnName = "created_at", inherited = true),
        ),
        idField = FieldModel("id", "ContentId", columnName = "id"),
    )
    val model = CanonicalModel(
        entities = listOf(entity),
        aggregateEntityJpa = listOf(
            AggregateEntityJpaModel(
                entityName = "Content",
                entityPackageName = "com.acme.demo.domain.content",
                entityEnabled = true,
                tableName = "content",
                columns = listOf(
                    AggregateColumnJpaModel("id", "id", isId = true),
                    AggregateColumnJpaModel("title", "title", isId = false),
                    AggregateColumnJpaModel("createdAt", "created_at", isId = false),
                ),
            )
        ),
    )

    val item = EntityArtifactPlanner().plan(ProjectConfig(basePackage = "com.acme.demo"), model).single()
    val scalarFields = item.context["scalarFields"] as List<Map<String, Any?>>

    assertEquals(listOf("id", "title"), scalarFields.map { it["name"] })
}
```

- [ ] **Step 2: Run test and verify RED**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest*inherited scalar*"
```

Expected: FAIL because `createdAt` is still included.

- [ ] **Step 3: Implement entity planner filter**

In `EntityArtifactPlanner.kt`, before mapping scalar field render models:

```kotlin
val scalarFields = entity.fields
    .filterNot { it.inherited }
    .mapNotNull { field ->
        ...
    }
```

Do not filter inherited fields in canonical model, special-field resolver, unique planning, or factory planning.

- [ ] **Step 4: Run test and verify GREEN**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest*inherited scalar*"
```

Expected: PASS.

- [ ] **Step 5: Commit entity omission**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: omit inherited fields from generated entities"
```

---

### Task 4: Update Public Documentation Contract

**Files:**
- Modify: `docs/public/authoring/generator/input-sources.md`
- Modify: `docs/public/reference/generator-dsl.md`

- [ ] **Step 1: Update docs**

In `input-sources.md`:

- Replace `@Id` guidance with “database primary-key metadata is the ID source of truth.”
- Remove rows for `@GeneratedValue`, `@GeneratedValue=uuid7`, `@GeneratedValue=snowflake-long`, `@Insertable`, `@Updatable`, and `@Exposed`.
- Keep rows for `@GeneratedValue=identity` and `@GeneratedValue=database-identity`.
- Add row:

```markdown
| `@Inherited` | 该列由实体父类或模板 override 声明；canonical facts 保留，但默认 entity 不重复生成字段 |
```

- Update examples to remove `@Id;` from primary-key column comments.

In `generator-dsl.md`, remove references to deleted annotations if present.

- [ ] **Step 2: Scan docs for stale active guidance**

Run:

```powershell
rg -n "@Id|GeneratedValue=uuid7|GeneratedValue=snowflake-long|@Insertable|@Updatable|@Exposed" docs/public
```

Expected: no stale `@Id` column-comment guidance or removed annotation support in active public docs. Historical specs under `docs/superpowers` are allowed to remain historical.

- [ ] **Step 3: Commit docs**

```powershell
git add docs/public/authoring/generator/input-sources.md docs/public/reference/generator-dsl.md
git commit -m "docs: slim db column annotation contract"
```

---

### Task 5: Final Focused Verification

**Files:**
- No production files.

- [ ] **Step 1: Run focused test suite**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-source-db:test --tests "*DbColumnAnnotationParserTest*" --tests "*DbSchemaSourceProviderTest*" :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest*" :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest*"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run docs and whitespace checks**

Run:

```powershell
rg -n "@Id|GeneratedValue=uuid7|GeneratedValue=snowflake-long|@Insertable|@Updatable|@Exposed" docs/public
git diff --check
```

Expected: no active public stale guidance for removed annotations; no whitespace errors.

- [ ] **Step 3: Review diff**

Run:

```powershell
git status --short
git diff --stat master...HEAD
```

Expected: only parser/model/planner/tests/docs changed.

