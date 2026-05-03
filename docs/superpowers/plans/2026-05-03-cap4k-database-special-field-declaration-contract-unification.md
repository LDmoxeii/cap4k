# Cap4k Database Special-Field Declaration Contract Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement one unified special-field declaration contract where DSL only provides project-wide defaults and DB column annotations provide entity-local overrides for ID generation, soft-delete, and optimistic-lock version, with resolved policy exposed by `cap4kPlan`.

**Architecture:** Keep generator/runtime behavior stable by introducing a unified core resolver that computes per-entity resolved special-field policy, then derive existing downstream controls (`aggregateIdPolicyControls`, `aggregatePersistenceProviderControls`) from that result. Prune Gradle DSL surface to project defaults only, migrate DB annotation parsing to column-level markers, and expose resolved policy through plan report data. Execute with TDD in narrow slices, keeping tests and fixtures in sync after each slice.

**Tech Stack:** Kotlin, Gradle TestKit, JUnit 5, existing cap4k pipeline modules (`api`, `core`, `source-db`, `generator-aggregate`, `gradle`).

---

### Task 1: Add Unified API Models for Defaults and Resolved Policies

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Test: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`

- [ ] **Step 1: Write failing API model tests for new plan/report fields**

```kotlin
@Test
fun `plan report carries special field defaults and resolved policies`() {
    val report = PlanReport(
        items = emptyList(),
        aggregateSpecialFieldDefaults = AggregateSpecialFieldDefaultsConfig(
            idDefaultStrategy = "uuid7",
            deletedDefaultColumn = "deleted",
            versionDefaultColumn = "version",
        ),
        aggregateSpecialFieldResolvedPolicies = listOf(
            AggregateSpecialFieldResolvedPolicy(
                entityName = "VideoPost",
                entityPackageName = "com.acme.demo.domain.aggregates.video_post",
                tableName = "video_post",
                id = ResolvedIdPolicy("id", "id", "uuid7", AggregateIdPolicyKind.APPLICATION_SIDE, SpecialFieldSource.DSL_DEFAULT),
                deleted = ResolvedMarkerPolicy(false, null, null, SpecialFieldSource.NONE),
                version = ResolvedMarkerPolicy(true, "version", "version", SpecialFieldSource.DSL_DEFAULT),
            )
        ),
    )

    assertEquals("uuid7", report.aggregateSpecialFieldDefaults!!.idDefaultStrategy)
    assertEquals(1, report.aggregateSpecialFieldResolvedPolicies.size)
}
```

- [ ] **Step 2: Run API tests to verify failure**

Run: `./gradlew :cap4k-plugin-pipeline-api:test --tests "*PipelineModelsTest*"`  
Expected: FAIL with unresolved references for `AggregateSpecialFieldDefaultsConfig`, `AggregateSpecialFieldResolvedPolicy`, and new `PlanReport` fields.

- [ ] **Step 3: Implement API model changes**

```kotlin
// ProjectConfig.kt
data class ProjectConfig(
    // ...
    val aggregateSpecialFieldDefaults: AggregateSpecialFieldDefaultsConfig = AggregateSpecialFieldDefaultsConfig(),
)

data class AggregateSpecialFieldDefaultsConfig(
    val idDefaultStrategy: String = "uuid7",
    val deletedDefaultColumn: String = "",
    val versionDefaultColumn: String = "",
)
```

```kotlin
// PipelineModels.kt
enum class SpecialFieldSource {
    DB_EXPLICIT,
    DSL_DEFAULT,
    NONE,
}

data class ResolvedIdPolicy(
    val fieldName: String,
    val columnName: String,
    val strategy: String,
    val kind: AggregateIdPolicyKind,
    val source: SpecialFieldSource,
)

data class ResolvedMarkerPolicy(
    val enabled: Boolean,
    val fieldName: String?,
    val columnName: String?,
    val source: SpecialFieldSource,
)

data class AggregateSpecialFieldResolvedPolicy(
    val entityName: String,
    val entityPackageName: String,
    val tableName: String,
    val id: ResolvedIdPolicy,
    val deleted: ResolvedMarkerPolicy,
    val version: ResolvedMarkerPolicy,
)
```

```kotlin
// PipelineModels.kt
data class CanonicalModel(
    // ...
    val aggregateSpecialFieldResolvedPolicies: List<AggregateSpecialFieldResolvedPolicy> = emptyList(),
)

data class PlanReport(
    val items: List<ArtifactPlanItem>,
    val diagnostics: PipelineDiagnostics? = null,
    val aggregateSpecialFieldDefaults: AggregateSpecialFieldDefaultsConfig? = null,
    val aggregateSpecialFieldResolvedPolicies: List<AggregateSpecialFieldResolvedPolicy> = emptyList(),
)

data class PipelineResult(
    // ...
    val aggregateSpecialFieldResolvedPolicies: List<AggregateSpecialFieldResolvedPolicy> = emptyList(),
)
```

- [ ] **Step 4: Run API tests to verify pass**

Run: `./gradlew :cap4k-plugin-pipeline-api:test --tests "*PipelineModelsTest*"`  
Expected: PASS with new plan/report model coverage.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt
git commit -m "feat: add unified special-field defaults and resolved policy models"
```

### Task 2: Prune Gradle DSL to Project-Level Special-Field Defaults

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`

- [ ] **Step 1: Write failing DSL/config-factory tests for new specialFields defaults**

```kotlin
@Test
fun `specialFields defaults map into project config`() {
    val extension = testProject.extensions.getByType(Cap4kExtension::class.java)
    extension.generators.aggregate.specialFields {
        idDefaultStrategy.set("uuid7")
        deletedDefaultColumn.set("deleted")
        versionDefaultColumn.set("version")
    }

    val config = Cap4kProjectConfigFactory().build(testProject, extension)
    assertEquals("uuid7", config.aggregateSpecialFieldDefaults.idDefaultStrategy)
    assertEquals("deleted", config.aggregateSpecialFieldDefaults.deletedDefaultColumn)
    assertEquals("version", config.aggregateSpecialFieldDefaults.versionDefaultColumn)
}
```

```kotlin
@Test
fun `legacy aggregate and entity id overrides are rejected`() {
    val extension = testProject.extensions.getByType(Cap4kExtension::class.java)
    val error = assertThrows(IllegalArgumentException::class.java) {
        extension.generators.aggregate.idPolicy {
            aggregate("video.Video", "snowflake-long")
        }
    }
    assertTrue(error.message!!.contains("aggregate/entity ID overrides are removed"))
}
```

- [ ] **Step 2: Run Gradle plugin unit tests to verify failure**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kProjectConfigFactoryTest*"`  
Expected: FAIL due missing `specialFields` DSL and missing `aggregateSpecialFieldDefaults` mapping.

- [ ] **Step 3: Implement DSL pruning and config mapping**

```kotlin
// Cap4kExtension.kt
open class AggregateGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val specialFields: AggregateSpecialFieldsExtension =
        objects.newInstance(AggregateSpecialFieldsExtension::class.java)

    fun specialFields(block: AggregateSpecialFieldsExtension.() -> Unit) {
        specialFields.block()
    }
}

open class AggregateSpecialFieldsExtension @Inject constructor(objects: ObjectFactory) {
    val idDefaultStrategy: Property<String> = objects.property(String::class.java).convention("uuid7")
    val deletedDefaultColumn: Property<String> = objects.property(String::class.java).convention("")
    val versionDefaultColumn: Property<String> = objects.property(String::class.java).convention("")
}
```

```kotlin
// Cap4kProjectConfigFactory.kt
private fun buildAggregateSpecialFieldDefaults(extension: Cap4kExtension): AggregateSpecialFieldDefaultsConfig {
    val specialFields = extension.generators.aggregate.specialFields
    return AggregateSpecialFieldDefaultsConfig(
        idDefaultStrategy = specialFields.idDefaultStrategy.normalized().ifEmpty { "uuid7" },
        deletedDefaultColumn = specialFields.deletedDefaultColumn.normalized(),
        versionDefaultColumn = specialFields.versionDefaultColumn.normalized(),
    )
}
```

```kotlin
// Cap4kProjectConfigFactory.kt (ProjectConfig builder)
aggregateSpecialFieldDefaults = buildAggregateSpecialFieldDefaults(extension)
```

- [ ] **Step 4: Run Gradle plugin unit tests to verify pass**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kProjectConfigFactoryTest*"`  
Expected: PASS with new DSL defaults mapping and no aggregate/entity override surface.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt
git commit -m "refactor: keep only project-level special-field defaults in aggregate DSL"
```

### Task 3: Migrate DB Annotation Parsing to Column-Level Markers

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParser.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Test: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt`
- Test: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParserTest.kt`
- Test: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`

- [ ] **Step 1: Write failing parser tests for marker annotations and legacy rejections**

```kotlin
@Test
fun `column parser supports generated value marker and explicit strategy`() {
    val marker = DbColumnAnnotationParser.parse("@GeneratedValue;")
    val explicit = DbColumnAnnotationParser.parse("@GeneratedValue=snowflake-long;")

    assertEquals(true, marker.generatedValueDeclared)
    assertEquals(null, marker.generatedValueStrategy)
    assertEquals("snowflake-long", explicit.generatedValueStrategy)
}
```

```kotlin
@Test
fun `column parser supports deleted and version markers`() {
    val metadata = DbColumnAnnotationParser.parse("@Deleted;@Version;")
    assertEquals(true, metadata.deleted)
    assertEquals(true, metadata.version)
}
```

```kotlin
@Test
fun `table parser rejects legacy soft-delete table annotation`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
        DbTableAnnotationParser.parse("@SoftDeleteColumn=deleted;")
    }
    assertEquals("unsupported table annotation @SoftDeleteColumn: use column marker @Deleted instead", error.message)
}
```

- [ ] **Step 2: Run source-db tests to verify failure**

Run: `./gradlew :cap4k-plugin-pipeline-source-db:test --tests "*DbColumnAnnotationParserTest*" --tests "*DbTableAnnotationParserTest*"`  
Expected: FAIL because marker fields and legacy rejection do not exist yet.

- [ ] **Step 3: Implement parser and snapshot model changes**

```kotlin
// PipelineModels.kt
data class DbColumnSnapshot(
    // ...
    val generatedValueDeclared: Boolean = false,
    val generatedValueStrategy: String? = null,
    val deleted: Boolean? = null,
    val version: Boolean? = null,
)

data class DbTableSnapshot(
    // remove table-level softDeleteColumn
)
```

```kotlin
// DbColumnAnnotationParser.kt
when (annotation.key) {
    "GENERATEDVALUE" -> {
        generatedValueDeclared = true
        generatedValueStrategy = annotation.value.ifBlank { null }
    }
    "DELETED" -> {
        require(!annotation.hasExplicitValue) { "invalid @Deleted annotation: explicit values are not supported." }
        deleted = true
    }
    "VERSION" -> {
        require(!annotation.hasExplicitValue) { "invalid @Version annotation: explicit values are not supported." }
        version = true
    }
}
```

```kotlin
// DbTableAnnotationParser.kt
annotations.firstOrNull { it.key == "SOFTDELETECOLUMN" }?.let {
    throw IllegalArgumentException("unsupported table annotation @SoftDeleteColumn: use column marker @Deleted instead")
}
```

```kotlin
// DbSchemaSourceProvider.kt
DbColumnSnapshot(
    // ...
    generatedValueDeclared = annotationMetadata.generatedValueDeclared,
    generatedValueStrategy = annotationMetadata.generatedValueStrategy,
    deleted = annotationMetadata.deleted,
    version = annotationMetadata.version,
)
```

- [ ] **Step 4: Run source-db tests to verify pass**

Run: `./gradlew :cap4k-plugin-pipeline-source-db:test --tests "*DbColumnAnnotationParserTest*" --tests "*DbTableAnnotationParserTest*" --tests "*DbSchemaSourceProviderTest*"`  
Expected: PASS with marker parsing and legacy-annotation rejection coverage.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParser.kt cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbTableAnnotationParserTest.kt cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt
git commit -m "feat: migrate special-field source parsing to column-level markers"
```

### Task 4: Implement Unified Special-Field Resolver in Core

**Files:**
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdPolicyResolver.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceProviderInference.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Write failing core tests for precedence and non-failure defaults**

```kotlin
@Test
fun `missing DSL deleted and version default columns do not fail and stay disabled`() {
    val result = assembleAggregate(
        config = projectConfigWithSpecialFieldDefaults(
            idDefaultStrategy = "uuid7",
            deletedDefaultColumn = "deleted",
            versionDefaultColumn = "version",
        ),
        tables = listOf(
            table(
                name = "video",
                columns = listOf(column("id", "UUID", "UUID", false, primaryKey = true)),
                primaryKey = listOf("id"),
                aggregateRoot = true,
            )
        )
    )

    val policy = result.model.aggregateSpecialFieldResolvedPolicies.single()
    assertEquals(false, policy.deleted.enabled)
    assertEquals(false, policy.version.enabled)
}
```

```kotlin
@Test
fun `explicit deleted marker overrides DSL default column`() {
    val result = assembleAggregate(
        config = projectConfigWithSpecialFieldDefaults(
            idDefaultStrategy = "snowflake-long",
            deletedDefaultColumn = "deleted",
            versionDefaultColumn = "",
        ),
        tables = listOf(
            table(
                name = "audit_log",
                columns = listOf(
                    column("id", "BIGINT", "Long", false, primaryKey = true),
                    column("is_deleted", "INT", "Int", false, deleted = true),
                    column("deleted", "INT", "Int", false),
                ),
                primaryKey = listOf("id"),
                aggregateRoot = true,
            )
        )
    )

    val policy = result.model.aggregateSpecialFieldResolvedPolicies.single()
    assertEquals("isDeleted", policy.deleted.fieldName)
    assertEquals(SpecialFieldSource.DB_EXPLICIT, policy.deleted.source)
}
```

- [ ] **Step 2: Run core tests to verify failure**

Run: `./gradlew :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest*"`  
Expected: FAIL because unified resolver and new policy list do not exist.

- [ ] **Step 3: Implement unified resolver and keep downstream controls derived**

```kotlin
// AggregateSpecialFieldPolicyResolver.kt
internal data class AggregateSpecialFieldResolutionResult(
    val resolvedPolicies: List<AggregateSpecialFieldResolvedPolicy>,
    val idControls: List<AggregateIdPolicyControl>,
    val providerControls: List<AggregatePersistenceProviderControl>,
)

internal object AggregateSpecialFieldPolicyResolver {
    fun resolve(
        config: ProjectConfig,
        entities: List<EntityModel>,
        tables: List<DbTableSnapshot>,
    ): AggregateSpecialFieldResolutionResult {
        // 1) resolve per-entity id/deleted/version with DB_EXPLICIT > DSL_DEFAULT > NONE
        // 2) validate fail-fast matrix
        // 3) build aggregateIdPolicyControls and aggregatePersistenceProviderControls from resolved policies
    }
}
```

```kotlin
// AggregateIdPolicyResolver.kt
internal fun normalizeStrategy(raw: String): String = when (raw.trim().lowercase(Locale.ROOT)) {
    "database-identity" -> "identity"
    else -> raw.trim().lowercase(Locale.ROOT)
}
```

```kotlin
// AggregatePersistenceProviderInference.kt
// Delegate into resolver-derived provider control output.
```

- [ ] **Step 4: Run core tests to verify pass**

Run: `./gradlew :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest*"`  
Expected: PASS including explicit-marker precedence and missing-default non-failure behavior.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdPolicyResolver.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceProviderInference.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: resolve unified per-entity special-field policy with precedence and validations"
```

### Task 5: Wire Canonical Assembler and Runner to Carry Resolved Policies

**Files:**
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunner.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt`

- [ ] **Step 1: Write failing runner test for resolved policy pass-through**

```kotlin
@Test
fun `pipeline result carries aggregate special-field resolved policies`() {
    val result = runner.run(config)
    assertTrue(result.aggregateSpecialFieldResolvedPolicies.isNotEmpty())
}
```

- [ ] **Step 2: Run pipeline-core tests to verify failure**

Run: `./gradlew :cap4k-plugin-pipeline-core:test --tests "*DefaultPipelineRunnerTest*"`  
Expected: FAIL because `PipelineResult` is not populated with resolved policies.

- [ ] **Step 3: Implement assembler and runner wiring**

```kotlin
// DefaultCanonicalAssembler.kt
val specialFieldResolution = AggregateSpecialFieldPolicyResolver.resolve(
    config = config,
    entities = entities,
    tables = supportedTables,
)

val aggregatePersistenceProviderControls = specialFieldResolution.providerControls
val aggregateIdPolicyControls = specialFieldResolution.idControls
```

```kotlin
// CanonicalModel construction
aggregateSpecialFieldResolvedPolicies = specialFieldResolution.resolvedPolicies,
```

```kotlin
// DefaultPipelineRunner.kt
return PipelineResult(
    // ...
    aggregateSpecialFieldResolvedPolicies = model.aggregateSpecialFieldResolvedPolicies,
)
```

- [ ] **Step 4: Run pipeline-core tests to verify pass**

Run: `./gradlew :cap4k-plugin-pipeline-core:test --tests "*DefaultPipelineRunnerTest*" --tests "*DefaultCanonicalAssemblerTest*"`  
Expected: PASS with runner carrying resolved policy data.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunner.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt
git commit -m "refactor: wire unified special-field resolution through canonical model and pipeline result"
```

### Task 6: Expose Defaults and Resolved Policies in cap4kPlan

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kPlanTask.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAnalysisPlanTask.kt`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Write failing functional test for resolved policy JSON**

```kotlin
@Test
fun `cap4kPlan includes special-field defaults and resolved policies`() {
    val planJson = projectDir.resolve("build/cap4k/plan.json").readText()
    assertTrue(planJson.contains("\"aggregateSpecialFieldDefaults\""))
    assertTrue(planJson.contains("\"aggregateSpecialFieldResolvedPolicies\""))
    assertTrue(planJson.contains("\"source\": \"DSL_DEFAULT\""))
}
```

- [ ] **Step 2: Run functional test to verify failure**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginFunctionalTest.cap4kPlan includes special-field defaults and resolved policies*"`  
Expected: FAIL because plan report currently only exposes old ID policy config.

- [ ] **Step 3: Implement plan report serialization updates**

```kotlin
// Cap4kPlanTask.kt
report = PlanReport(
    items = result.planItems,
    diagnostics = result.diagnostics,
    aggregateSpecialFieldDefaults = config.aggregateSpecialFieldDefaults,
    aggregateSpecialFieldResolvedPolicies = result.aggregateSpecialFieldResolvedPolicies,
)
```

```kotlin
// Cap4kAnalysisPlanTask.kt
report = PlanReport(
    items = result.planItems,
    diagnostics = result.diagnostics,
    aggregateSpecialFieldDefaults = null,
    aggregateSpecialFieldResolvedPolicies = emptyList(),
)
```

- [ ] **Step 4: Run functional test to verify pass**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginFunctionalTest*"`  
Expected: PASS with plan JSON containing defaults and resolved policy sections.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kPlanTask.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAnalysisPlanTask.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git commit -m "feat: expose special-field defaults and resolved policy in cap4kPlan"
```

### Task 7: Update Aggregate Fixtures and Functional Expectations to New Annotation Contract

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/schema.sql`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/schema.sql`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/schema.sql`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-sample/schema.sql`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-compile-sample/schema.sql`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Write failing fixture-based tests for marker syntax**

```kotlin
@Test
fun `aggregate provider fixtures use marker-based deleted and version annotations`() {
    val schema = projectDir.resolve("schema.sql").readText()
    assertTrue(schema.contains("@Deleted;"))
    assertTrue(schema.contains("@Version;"))
    assertFalse(schema.contains("@SoftDeleteColumn="))
    assertFalse(schema.contains("@Version=true"))
}
```

- [ ] **Step 2: Run targeted functional tests to verify failure**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*aggregate provider persistence generation*"`  
Expected: FAIL because fixtures and assertions still use legacy syntax.

- [ ] **Step 3: Migrate fixtures and assertions**

```sql
-- schema.sql migration example
create table video_post (
    id bigint primary key comment '@GeneratedValue=identity;',
    version bigint not null comment '@Version;',
    deleted int not null comment '@Deleted;',
    title varchar(128) not null
);
comment on table video_post is '@AggregateRoot=true;@DynamicInsert=true;@DynamicUpdate=true;';
```

```kotlin
// Update functional assertions to remove soft-delete table annotation expectations
assertFalse(schemaContent.contains("@SoftDeleteColumn"))
assertTrue(schemaContent.contains("@Deleted;"))
assertTrue(schemaContent.contains("@Version;"))
```

- [ ] **Step 4: Run compile/functional tests to verify pass**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*aggregate provider persistence generation*" --tests "*aggregate persistence generation*"`  
Expected: PASS with new marker-based fixtures.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/schema.sql cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/schema.sql cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/schema.sql cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-sample/schema.sql cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-compile-sample/schema.sql cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git commit -m "test: migrate aggregate fixtures and functional checks to marker annotations"
```

### Task 8: End-to-End Verification and Documentation Sync

**Files:**
- Modify: `docs/superpowers/mainline-roadmap.md` (only if status/details changed during implementation)
- Modify: `docs/superpowers/specs/2026-05-03-cap4k-database-special-field-declaration-contract-unification-design.md` (only if implementation-driven clarifications are needed)

- [ ] **Step 1: Run full targeted verification matrix**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-api:test --tests "*PipelineModelsTest*"
./gradlew :cap4k-plugin-pipeline-source-db:test --tests "*DbColumnAnnotationParserTest*" --tests "*DbTableAnnotationParserTest*" --tests "*DbSchemaSourceProviderTest*"
./gradlew :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest*" --tests "*DefaultPipelineRunnerTest*"
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest*"
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kProjectConfigFactoryTest*" --tests "*PipelinePluginFunctionalTest*" --tests "*PipelinePluginCompileFunctionalTest*"
```

Expected: PASS for all targeted modules.

- [ ] **Step 2: Run regression smoke for plan and generate tasks**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*cap4kPlan*"
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*cap4kGenerate*"
```

Expected: PASS; `cap4kPlan` test assertions confirm resolved-policy exposure.

- [ ] **Step 3: Update docs only when implementation diverges from spec text**

```markdown
- Update wording for final annotation spellings and defaults if code-level naming differs.
- Keep roadmap status aligned (spec written / plan written / implementation complete after merge).
```

- [ ] **Step 4: Re-run docs-adjacent test command for safety**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginFunctionalTest.cap4kPlan includes special-field defaults and resolved policies*"`  
Expected: PASS after doc-aligned plan assertions.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/mainline-roadmap.md docs/superpowers/specs/2026-05-03-cap4k-database-special-field-declaration-contract-unification-design.md
git commit -m "docs: align roadmap and spec with implemented special-field unification"
```

## Self-Review

### 1) Spec Coverage Check

- DSL project-level defaults only: covered by Task 2.
- DB column-level annotations (`@GeneratedValue`, `@Deleted`, `@Version`): covered by Task 3 and Task 7.
- Unified resolution model and precedence: covered by Task 4 and Task 5.
- Mixed ID strategy support at entity level: covered by Task 4 tests and Task 7 functional coverage.
- `cap4kPlan` resolved policy exposure: covered by Task 6.
- Fail-fast rules: covered by Task 3 and Task 4 tests.

No spec requirement is left without an implementation task.

### 2) Placeholder Scan

Checked for forbidden placeholders (`TBD`, `TODO`, "implement later", "write tests for above"): none remain in executable steps.

### 3) Type and Naming Consistency

- `AggregateSpecialFieldDefaultsConfig`, `AggregateSpecialFieldResolvedPolicy`, `SpecialFieldSource`, `ResolvedIdPolicy`, `ResolvedMarkerPolicy` names are used consistently across tasks.
- DSL name `specialFields` and fields `idDefaultStrategy`, `deletedDefaultColumn`, `versionDefaultColumn` are consistent across test and implementation steps.
- PlanReport/Runner wiring uses the same property names in Task 1, Task 5, and Task 6.
