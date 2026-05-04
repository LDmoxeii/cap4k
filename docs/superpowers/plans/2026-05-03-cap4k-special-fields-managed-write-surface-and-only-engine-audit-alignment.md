# Cap4k Special-Fields Managed Write-Surface and only-engine Audit Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement one cross-repository contract where cap4k resolves managed special-field write-surface policies, only-engine provides pluggable runtime audit autofill, engine-satoken provides default operator SPI, and only-danmuku-zero removes audited base-class inheritance dependency.

**Architecture:** Execute in three bounded tracks with strict ownership: (1) cap4k declaration/resolution/plan exposure for managed special fields, (2) only-engine runtime audit SPI + auto configuration + satoken bridge, (3) only-danmuku-zero migration from domain-base audit callbacks to engine audit module. Keep existing executed special-field spec untouched and deliver all new behavior through additive files or targeted edits only.

**Tech Stack:** Kotlin, Gradle TestKit, JUnit 5, Spring Boot AutoConfiguration, Hibernate/JPA entity lifecycle callbacks, cap4k pipeline modules (`api`, `source-db`, `core`, `generator-aggregate`, `renderer-pebble`, `gradle`), only-engine modules (`engine-spi`, `engine-audit`, `engine-satoken`), only-danmuku-zero multi-module app.

---

### Task 1: Extend cap4k API Models for Managed Write-Surface

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Test: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`

- [ ] **Step 1: Add failing API tests for managed defaults and write-policy shape**

```kotlin
@Test
fun `plan report and canonical model carry managed defaults and write policies`() {
    val defaults = AggregateSpecialFieldDefaultsConfig(
        idDefaultStrategy = "uuid7",
        deletedDefaultColumn = "deleted",
        versionDefaultColumn = "version",
        managedDefaultColumns = listOf("create_user_id", "update_user_id"),
    )
    val policy = AggregateSpecialFieldResolvedPolicy(
        entityName = "Category",
        entityPackageName = "edu.only4.demo.domain.aggregates.category",
        tableName = "category",
        id = ResolvedIdPolicy(
            fieldName = "id",
            columnName = "id",
            strategy = "uuid7",
            kind = AggregateIdPolicyKind.APPLICATION_SIDE,
            writePolicy = SpecialFieldWritePolicy.CREATE_ONLY,
            source = SpecialFieldSource.DSL_DEFAULT,
        ),
        deleted = ResolvedMarkerPolicy(
            enabled = true,
            fieldName = "deleted",
            columnName = "deleted",
            writePolicy = SpecialFieldWritePolicy.SYSTEM_TRANSITION_ONLY,
            source = SpecialFieldSource.DB_EXPLICIT,
        ),
        version = ResolvedMarkerPolicy(
            enabled = false,
            fieldName = null,
            columnName = null,
            writePolicy = SpecialFieldWritePolicy.READ_WRITE,
            source = SpecialFieldSource.NONE,
        ),
        managedFields = listOf(
            ResolvedManagedFieldPolicy(
                fieldName = "createUserId",
                columnName = "create_user_id",
                writePolicy = SpecialFieldWritePolicy.READ_ONLY,
                source = SpecialFieldSource.DSL_DEFAULT,
            )
        ),
        writeSurface = ResolvedWriteSurfacePolicy(
            createAllowedFields = listOf("id", "name"),
            updateAllowedFields = listOf("name"),
        ),
    )

    val report = PlanReport(
        items = emptyList(),
        aggregateSpecialFieldDefaults = defaults,
        aggregateSpecialFieldResolvedPolicies = listOf(policy),
    )
    val model = CanonicalModel(aggregateSpecialFieldResolvedPolicies = listOf(policy))

    assertEquals(listOf("create_user_id", "update_user_id"), report.aggregateSpecialFieldDefaults!!.managedDefaultColumns)
    assertEquals(SpecialFieldWritePolicy.CREATE_ONLY, report.aggregateSpecialFieldResolvedPolicies.single().id.writePolicy)
    assertEquals(listOf("name"), model.aggregateSpecialFieldResolvedPolicies.single().writeSurface.updateAllowedFields)
}
```

- [ ] **Step 2: Run API tests to verify failure**

Run: `./gradlew :cap4k-plugin-pipeline-api:test --tests "*PipelineModelsTest*"`  
Expected: FAIL because `managedDefaultColumns`, `SpecialFieldWritePolicy`, `ResolvedManagedFieldPolicy`, and `ResolvedWriteSurfacePolicy` do not exist yet.

- [ ] **Step 3: Implement API model additions**

```kotlin
// ProjectConfig.kt
data class AggregateSpecialFieldDefaultsConfig(
    val idDefaultStrategy: String = "uuid7",
    val deletedDefaultColumn: String = "",
    val versionDefaultColumn: String = "",
    val managedDefaultColumns: List<String> = emptyList(),
)
```

```kotlin
// PipelineModels.kt
enum class SpecialFieldWritePolicy {
    READ_WRITE,
    CREATE_ONLY,
    READ_ONLY,
    SYSTEM_TRANSITION_ONLY,
}

data class ResolvedIdPolicy(
    val fieldName: String,
    val columnName: String,
    val strategy: String,
    val kind: AggregateIdPolicyKind,
    val writePolicy: SpecialFieldWritePolicy,
    val source: SpecialFieldSource,
)

data class ResolvedMarkerPolicy(
    val enabled: Boolean,
    val fieldName: String? = null,
    val columnName: String? = null,
    val writePolicy: SpecialFieldWritePolicy = SpecialFieldWritePolicy.READ_WRITE,
    val source: SpecialFieldSource,
)

data class ResolvedManagedFieldPolicy(
    val fieldName: String,
    val columnName: String,
    val writePolicy: SpecialFieldWritePolicy,
    val source: SpecialFieldSource,
)

data class ResolvedWriteSurfacePolicy(
    val createAllowedFields: List<String>,
    val updateAllowedFields: List<String>,
)

data class AggregateSpecialFieldResolvedPolicy(
    val entityName: String,
    val entityPackageName: String,
    val tableName: String,
    val id: ResolvedIdPolicy,
    val deleted: ResolvedMarkerPolicy,
    val version: ResolvedMarkerPolicy,
    val managedFields: List<ResolvedManagedFieldPolicy> = emptyList(),
    val writeSurface: ResolvedWriteSurfacePolicy = ResolvedWriteSurfacePolicy(emptyList(), emptyList()),
)
```

```kotlin
// PipelineModels.kt
data class DbColumnSnapshot(
    // ... keep existing fields unchanged
    val managed: Boolean? = null,
    val exposed: Boolean? = null,
)
```

- [ ] **Step 4: Run API tests to verify pass**

Run: `./gradlew :cap4k-plugin-pipeline-api:test --tests "*PipelineModelsTest*"`  
Expected: PASS with new write-policy and managed-default assertions.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt
git commit -m "feat: add managed special-field write-surface API models"
```

### Task 2: Extend cap4k DSL Defaults with `managedDefaultColumns`

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`

- [ ] **Step 1: Add failing DSL mapping test**

```kotlin
@Test
fun `factory maps managed default columns into aggregate special field defaults`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

    extension.project { basePackage.set("edu.only4.demo") }
    extension.generators {
        aggregate {
            specialFields {
                managedDefaultColumns.set(listOf(" create_user_id ", "update_user_id", ""))
            }
        }
    }

    val config = Cap4kProjectConfigFactory().build(project, extension)
    assertEquals(listOf("create_user_id", "update_user_id"), config.aggregateSpecialFieldDefaults.managedDefaultColumns)
}
```

- [ ] **Step 2: Run Gradle module test to verify failure**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kProjectConfigFactoryTest*"`  
Expected: FAIL because `managedDefaultColumns` is not in extension/config mapping.

- [ ] **Step 3: Implement DSL and config-factory updates**

```kotlin
// Cap4kExtension.kt
open class AggregateSpecialFieldsExtension @Inject constructor(objects: ObjectFactory) {
    val idDefaultStrategy: Property<String> = objects.property(String::class.java).convention("uuid7")
    val deletedDefaultColumn: Property<String> = objects.property(String::class.java).convention("")
    val versionDefaultColumn: Property<String> = objects.property(String::class.java).convention("")
    val managedDefaultColumns: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
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
        managedDefaultColumns = specialFields.managedDefaultColumns.normalizedValues(),
    )
}
```

- [ ] **Step 4: Run Gradle module test to verify pass**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kProjectConfigFactoryTest*"`  
Expected: PASS with normalized `managedDefaultColumns`.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt
git commit -m "feat: add managed default columns to cap4k specialFields DSL"
```

### Task 3: Parse `@Managed` and `@Exposed` in DB Column Annotations

**Files:**
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Test: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt`
- Test: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`

- [ ] **Step 1: Add failing parser tests**

```kotlin
@Test
fun `parser supports managed and exposed markers`() {
    val managed = DbColumnAnnotationParser.parse("@Managed;")
    val exposed = DbColumnAnnotationParser.parse("@Exposed;")

    assertEquals(true, managed.managed)
    assertEquals(null, managed.exposed)
    assertEquals(true, exposed.exposed)
    assertEquals(null, exposed.managed)
}
```

```kotlin
@Test
fun `parser rejects valued managed exposed and mutual exclusion`() {
    val managedError = assertThrows(IllegalArgumentException::class.java) {
        DbColumnAnnotationParser.parse("@Managed=true;")
    }
    val exposedError = assertThrows(IllegalArgumentException::class.java) {
        DbColumnAnnotationParser.parse("@Exposed=1;")
    }
    val conflictError = assertThrows(IllegalArgumentException::class.java) {
        DbColumnAnnotationParser.parse("@Managed;@Exposed;")
    }

    assertEquals("invalid @Managed annotation: explicit values are not supported.", managedError.message)
    assertEquals("invalid @Exposed annotation: explicit values are not supported.", exposedError.message)
    assertEquals("conflicting @Managed/@Exposed annotations on the same column comment.", conflictError.message)
}
```

- [ ] **Step 2: Run source-db tests to verify failure**

Run: `./gradlew :cap4k-plugin-pipeline-source-db:test --tests "*DbColumnAnnotationParserTest*"`  
Expected: FAIL because parse result has no `managed/exposed` fields yet.

- [ ] **Step 3: Implement parser + snapshot plumbing**

```kotlin
// DbColumnAnnotationParser.kt
val managed = resolveMarkerAnnotation(annotations, "MANAGED", "Managed")
val exposed = resolveMarkerAnnotation(annotations, "EXPOSED", "Exposed")
require(!(managed == true && exposed == true)) {
    "conflicting @Managed/@Exposed annotations on the same column comment."
}

return DbColumnAnnotationParseResult(
    // existing properties
    managed = managed,
    exposed = exposed,
)
```

```kotlin
// DbColumnAnnotationParser.kt
internal data class DbColumnAnnotationParseResult(
    // existing properties
    val managed: Boolean? = null,
    val exposed: Boolean? = null,
)
```

```kotlin
// DbSchemaSourceProvider.kt
DbColumnSnapshot(
    // existing properties
    managed = annotationMetadata.managed,
    exposed = annotationMetadata.exposed,
)
```

- [ ] **Step 4: Run source-db tests to verify pass**

Run: `./gradlew :cap4k-plugin-pipeline-source-db:test --tests "*DbColumnAnnotationParserTest*" --tests "*DbSchemaSourceProviderTest*"`  
Expected: PASS with managed/exposed parsing.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt
git commit -m "feat: parse managed and exposed DB column markers"
```

### Task 4: Implement Core Managed Resolution and Lifecycle Write Policies

**Files:**
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceFieldBehaviorInference.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt`

- [ ] **Step 1: Add failing resolver tests for policy semantics**

```kotlin
@Test
fun `application-side id resolves create-only write policy`() {
    val result = assembleAggregate(
        config = projectConfigWithSpecialFieldDefaults(
            idDefaultStrategy = "uuid7",
            deletedDefaultColumn = "",
            versionDefaultColumn = "",
            managedDefaultColumns = emptyList(),
        ),
        tables = listOf(
            table(
                name = "category",
                columns = listOf(column("id", "UUID", "UUID", false, primaryKey = true)),
                primaryKey = listOf("id"),
                aggregateRoot = true,
            )
        )
    )

    val policy = result.model.aggregateSpecialFieldResolvedPolicies.single()
    assertEquals(SpecialFieldWritePolicy.CREATE_ONLY, policy.id.writePolicy)
}
```

```kotlin
@Test
fun `missing managed defaults on entity do not fail and produce empty managed list`() {
    val result = assembleAggregate(
        config = projectConfigWithSpecialFieldDefaults(
            idDefaultStrategy = "identity",
            deletedDefaultColumn = "",
            versionDefaultColumn = "",
            managedDefaultColumns = listOf("create_user_id"),
        ),
        tables = listOf(
            table(
                name = "audit_log",
                columns = listOf(column("id", "BIGINT", "Long", false, primaryKey = true)),
                primaryKey = listOf("id"),
                aggregateRoot = true,
            )
        )
    )

    val policy = result.model.aggregateSpecialFieldResolvedPolicies.single()
    assertEquals(emptyList<String>(), policy.managedFields.map { it.columnName })
}
```

```kotlin
@Test
fun `exposed on resolved version field fails fast`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
        assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(
                idDefaultStrategy = "identity",
                deletedDefaultColumn = "",
                versionDefaultColumn = "",
                managedDefaultColumns = emptyList(),
            ),
            tables = listOf(
                table(
                    name = "category",
                    columns = listOf(
                        column("id", "BIGINT", "Long", false, primaryKey = true, generatedValueDeclared = true, generatedValueStrategy = "identity"),
                        column("version", "BIGINT", "Long", false, version = true, exposed = true),
                    ),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )
    }

    assertEquals("@Exposed cannot be applied to protected special field: category.version", error.message)
}
```

- [ ] **Step 2: Run core tests to verify failure**

Run: `./gradlew :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest*"`  
Expected: FAIL because managed resolution and write policy mapping are not implemented yet.

- [ ] **Step 3: Implement resolver + inference updates**

```kotlin
// AggregateSpecialFieldPolicyResolver.kt
private fun idWritePolicy(kind: AggregateIdPolicyKind): SpecialFieldWritePolicy =
    if (kind == AggregateIdPolicyKind.APPLICATION_SIDE) {
        SpecialFieldWritePolicy.CREATE_ONLY
    } else {
        SpecialFieldWritePolicy.READ_ONLY
    }

private fun markerWritePolicy(enabled: Boolean, marker: String): SpecialFieldWritePolicy = when {
    !enabled -> SpecialFieldWritePolicy.READ_WRITE
    marker == "deleted" -> SpecialFieldWritePolicy.SYSTEM_TRANSITION_ONLY
    else -> SpecialFieldWritePolicy.READ_ONLY
}
```

```kotlin
// AggregateSpecialFieldPolicyResolver.kt
private fun resolveManagedFields(/* existing args */): List<ResolvedManagedFieldPolicy> {
    // 1) add protected fields (id/deleted/version)
    // 2) add matched DSL managed defaults
    // 3) apply @Managed/@Exposed overrides
    // 4) fail on illegal @Exposed for protected fields
}
```

```kotlin
// AggregateSpecialFieldPolicyResolver.kt
private fun buildWriteSurface(entity: EntityModel, managed: List<ResolvedManagedFieldPolicy>): ResolvedWriteSurfacePolicy {
    val createDenied = managed
        .filter { it.writePolicy == SpecialFieldWritePolicy.READ_ONLY || it.writePolicy == SpecialFieldWritePolicy.SYSTEM_TRANSITION_ONLY }
        .map { it.fieldName }
        .toSet()
    val updateDenied = managed
        .filter { it.writePolicy != SpecialFieldWritePolicy.READ_WRITE }
        .map { it.fieldName }
        .toSet()
    return ResolvedWriteSurfacePolicy(
        createAllowedFields = entity.fields.map { it.name }.filterNot { it in createDenied },
        updateAllowedFields = entity.fields.map { it.name }.filterNot { it in updateDenied },
    )
}
```

```kotlin
// AggregatePersistenceFieldBehaviorInference.kt
val hasExplicitControl =
    column.generatedValueStrategy != null ||
        column.version != null ||
        column.insertable != null ||
        column.updatable != null ||
        column.managed != null ||
        column.exposed != null
```

- [ ] **Step 4: Run core tests to verify pass**

Run: `./gradlew :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest*" --tests "*DefaultPipelineRunnerTest*"`  
Expected: PASS with managed/write-policy assertions.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceFieldBehaviorInference.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt
git commit -m "feat: resolve managed special fields and lifecycle write policies"
```

### Task 5: Propagate Write Policies into Planner and Entity Rendering

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Test: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Add failing planner/renderer tests**

```kotlin
@Test
fun `planner marks application-side id as create-only and non-updatable`() {
    val item = EntityArtifactPlanner().plan(config, model).single { it.typeName == "Category" }
    @Suppress("UNCHECKED_CAST")
    val scalarFields = item.context.getValue("scalarFields") as List<Map<String, Any?>>
    val id = scalarFields.single { it["fieldName"] == "id" }

    assertEquals("CREATE_ONLY", id["writePolicy"])
    assertEquals(false, id["updatable"])
}
```

```kotlin
@Test
fun `entity template keeps application-side id annotation with explicit insertable updatable flags`() {
    val rendered = renderEntityTemplate(
        mapOf(
            "scalarFields" to listOf(
                mapOf(
                    "fieldName" to "id",
                    "name" to "id",
                    "type" to "UUID",
                    "nullable" to false,
                    "columnName" to "id",
                    "isId" to true,
                    "applicationSideIdStrategy" to "uuid7",
                    "writePolicy" to "CREATE_ONLY",
                    "insertable" to true,
                    "updatable" to false,
                )
            )
        )
    )
    assertTrue(rendered.contains("@field:ApplicationSideId(strategy = \"uuid7\")"))
    assertTrue(rendered.contains("@Column(name = \"id\", insertable = true, updatable = false)"))
}
```

- [ ] **Step 2: Run planner/renderer tests to verify failure**

Run: `./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest*" :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleArtifactRendererTest*"`  
Expected: FAIL because write policy is not carried to planner context.

- [ ] **Step 3: Implement planner + template wiring**

```kotlin
// EntityArtifactPlanner.kt
val resolvedPolicy = model.aggregateSpecialFieldResolvedPolicies.firstOrNull {
    it.entityName == entity.name && it.entityPackageName == entity.packageName
}
val managedByField = resolvedPolicy?.managedFields.orEmpty().associateBy { it.fieldName }
val writePolicy = when {
    jpa.isId -> resolvedPolicy?.id?.writePolicy?.name ?: "READ_WRITE"
    control?.version == true -> resolvedPolicy?.version?.writePolicy?.name ?: "READ_ONLY"
    managedByField[field.name] != null -> managedByField.getValue(field.name).writePolicy.name
    else -> "READ_WRITE"
}
```

```kotlin
// EntityArtifactPlanner.kt scalar field map
"writePolicy" to writePolicy,
```

```pebble
{# entity.kt.peb #}
{% if field.insertable is not null or field.updatable is not null %}
    @Column(name = "{{ field.columnName }}", insertable = {{ field.insertable }}, updatable = {{ field.updatable }})
{% else %}
    @Column(name = "{{ field.columnName }}")
{% endif %}
```

- [ ] **Step 4: Run planner/renderer tests to verify pass**

Run: `./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest*" :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleArtifactRendererTest*"`  
Expected: PASS with write-policy-based field behavior.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: propagate managed write policies into aggregate entity generation"
```

### Task 6: Expose Managed Defaults and Write Surface in `cap4kPlan`

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kPlanTask.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAnalysisPlanTask.kt`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Add failing functional test for plan json**

```kotlin
@Test
fun `cap4kPlan includes managed defaults and write surface`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-special-fields-managed")
    copyFixture(projectDir, "aggregate-provider-persistence-sample")

    val buildFile = projectDir.resolve("build.gradle.kts")
    buildFile.writeText(
        buildFile.readText().replace(
            "idDefaultStrategy.set(\" snowflake-long \")",
            """
            idDefaultStrategy.set(" snowflake-long ")
                managedDefaultColumns.set(listOf("create_user_id", "update_user_id"))
            """.trimIndent()
        )
    )

    val result = GradleRunner.create().withProjectDir(projectDir.toFile()).withPluginClasspath().withArguments("cap4kPlan").build()
    val planObject = JsonParser.parseString(projectDir.resolve("build/cap4k/plan.json").readText()).asJsonObject
    val defaults = planObject.getAsJsonObject("aggregateSpecialFieldDefaults")
    val firstPolicy = planObject.getAsJsonArray("aggregateSpecialFieldResolvedPolicies").first().asJsonObject

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(defaults.getAsJsonArray("managedDefaultColumns").map { it.asString }.contains("create_user_id"))
    assertTrue(firstPolicy.has("managedFields"))
    assertTrue(firstPolicy.has("writeSurface"))
}
```

- [ ] **Step 2: Run functional test to verify failure**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginFunctionalTest*cap4kPlan*managed*"`  
Expected: FAIL because plan json does not include managed/write-surface fields yet.

- [ ] **Step 3: Ensure plan task serializes enriched `PlanReport` directly**

```kotlin
// Cap4kPlanTask.kt
val report = PlanReport(
    items = result.planItems,
    diagnostics = result.diagnostics,
    aggregateSpecialFieldDefaults = config.aggregateSpecialFieldDefaults,
    aggregateSpecialFieldResolvedPolicies = result.aggregateSpecialFieldResolvedPolicies,
)
```

```kotlin
// Cap4kAnalysisPlanTask.kt
val report = PlanReport(
    items = result.planItems,
    diagnostics = result.diagnostics,
    aggregateSpecialFieldDefaults = null,
    aggregateSpecialFieldResolvedPolicies = emptyList(),
)
```

- [ ] **Step 4: Run plan-related functional tests to verify pass**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginFunctionalTest*cap4kPlan*"`  
Expected: PASS with managed defaults and write-surface payload visible.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kPlanTask.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAnalysisPlanTask.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git commit -m "feat: expose managed special-field policies in cap4kPlan"
```

### Task 7: Add `AuditOperatorProvider` SPI in only-engine

**Files:**
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine/engine-spi/src/main/kotlin/com/only/engine/spi/audit/AuditOperatorProvider.kt`
- Test: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine/engine-spi/src/test/kotlin/com/only/engine/spi/audit/AuditOperatorProviderContractTest.kt`

- [ ] **Step 1: Add failing SPI contract test**

```kotlin
package com.only.engine.spi.audit

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AuditOperatorProviderContractTest {

    @Test
    fun `provider contract allows nullable id and name`() {
        val provider = object : AuditOperatorProvider {
            override fun currentOperatorId(): Any? = null
            override fun currentOperatorName(): String? = null
        }
        assertNull(provider.currentOperatorId())
        assertNull(provider.currentOperatorName())
    }
}
```

- [ ] **Step 2: Run SPI tests to verify failure**

Run: `./gradlew :engine-spi:test --tests "*AuditOperatorProviderContractTest*"`  
Expected: FAIL because `AuditOperatorProvider` does not exist.

- [ ] **Step 3: Implement SPI interface**

```kotlin
package com.only.engine.spi.audit

interface AuditOperatorProvider {
    fun currentOperatorId(): Any?
    fun currentOperatorName(): String?
}
```

- [ ] **Step 4: Run SPI tests to verify pass**

Run: `./gradlew :engine-spi:test --tests "*AuditOperatorProviderContractTest*"`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git -C C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine add engine-spi/src/main/kotlin/com/only/engine/spi/audit/AuditOperatorProvider.kt engine-spi/src/test/kotlin/com/only/engine/spi/audit/AuditOperatorProviderContractTest.kt
git -C C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine commit -m "feat: add audit operator provider SPI"
```

### Task 8: Add `engine-audit` Module with AutoConfiguration and Lifecycle Filler

**Files:**
- Modify: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine/settings.gradle.kts`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine/engine-audit/build.gradle.kts`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine/engine-audit/src/main/kotlin/com/only/engine/audit/AuditInitPrinter.kt`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine/engine-audit/src/main/kotlin/com/only/engine/audit/config/properties/AuditProperties.kt`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine/engine-audit/src/main/kotlin/com/only/engine/audit/config/AuditAutoConfiguration.kt`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine/engine-audit/src/main/kotlin/com/only/engine/audit/core/AuditEntityLifecycleListener.kt`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine/engine-audit/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine/engine-audit/src/test/kotlin/com/only/engine/audit/core/AuditEntityLifecycleListenerTest.kt`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine/engine-audit/src/test/kotlin/com/only/engine/audit/config/AuditAutoConfigurationTest.kt`

- [ ] **Step 1: Add failing tests for lifecycle fill behavior**

```kotlin
@Test
fun `prePersist fills create and update fields when null`() {
    val listener = AuditEntityLifecycleListener(
        properties = AuditProperties().apply { enable = true },
        provider = object : AuditOperatorProvider {
            override fun currentOperatorId(): Any? = "u-1001"
            override fun currentOperatorName(): String? = "tester"
        }
    )
    val entity = DemoEntity()

    listener.prePersist(entity)

    assertEquals("u-1001", entity.createUserId)
    assertEquals("tester", entity.createBy)
    assertNotNull(entity.createTime)
    assertEquals("u-1001", entity.updateUserId)
    assertEquals("tester", entity.updateBy)
    assertNotNull(entity.updateTime)
}
```

```kotlin
@Test
fun `preUpdate overwrites update fields`() {
    val listener = AuditEntityLifecycleListener(
        properties = AuditProperties().apply { enable = true },
        provider = object : AuditOperatorProvider {
            override fun currentOperatorId(): Any? = 99L
            override fun currentOperatorName(): String? = "updater"
        }
    )
    val entity = DemoEntity().apply {
        updateUserId = 1L
        updateBy = "old"
        updateTime = 1L
    }

    listener.preUpdate(entity)

    assertEquals(99L, entity.updateUserId)
    assertEquals("updater", entity.updateBy)
    assertTrue(entity.updateTime!! > 1L)
}
```

- [ ] **Step 2: Run engine-audit tests to verify failure**

Run: `./gradlew :engine-audit:test --tests "*AuditEntityLifecycleListenerTest*"`  
Expected: FAIL because module and classes do not exist.

- [ ] **Step 3: Create module and production code**

```kotlin
// settings.gradle.kts
include(":engine-audit")
```

```kotlin
// engine-audit/build.gradle.kts
plugins {
    id("buildsrc.convention.kotlin-jvm")
    kotlin("kapt")
}

dependencies {
    api(platform(libs.spring.boot.dependencies))
    kapt(libs.spring.boot.configuration.processor)

    implementation(project(":engine-common"))
    implementation(project(":engine-spi"))

    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.jakarta.annotation.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.junit.core)
    testImplementation(libs.mockk) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    testRuntimeOnly(libs.bundles.junit.runtime)
    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
}
```

```kotlin
// AuditProperties.kt
@ConfigurationProperties(prefix = "only.engine.audit")
class AuditProperties {
    var enable: Boolean = true
    var createUserIdField: String = "createUserId"
    var createByField: String = "createBy"
    var createTimeField: String = "createTime"
    var updateUserIdField: String = "updateUserId"
    var updateByField: String = "updateBy"
    var updateTimeField: String = "updateTime"
}
```

```kotlin
// AuditAutoConfiguration.kt
@AutoConfiguration
@EnableConfigurationProperties(AuditProperties::class)
@ConditionalOnProperty(prefix = "only.engine.audit", name = ["enable"], havingValue = "true", matchIfMissing = true)
class AuditAutoConfiguration : AuditInitPrinter {
    companion object {
        private val log = LoggerFactory.getLogger(AuditAutoConfiguration::class.java)
    }

    @Bean
    @ConditionalOnMissingBean
    fun auditEntityLifecycleListener(
        properties: AuditProperties,
        provider: ObjectProvider<AuditOperatorProvider>,
    ): AuditEntityLifecycleListener {
        printInit(AuditEntityLifecycleListener::class.java, log)
        return AuditEntityLifecycleListener(properties, provider.ifAvailable)
    }
}
```

```kotlin
// AuditEntityLifecycleListener.kt
class AuditEntityLifecycleListener(
    private val properties: AuditProperties,
    private val provider: AuditOperatorProvider?,
) {
    @PrePersist
    fun prePersist(entity: Any) {
        if (!properties.enable) return
        val now = Instant.now().epochSecond
        val id = provider?.currentOperatorId()
        val name = provider?.currentOperatorName()
        setIfNull(entity, properties.createUserIdField, id)
        setIfNull(entity, properties.createByField, name)
        setIfNull(entity, properties.createTimeField, now)
        setIfNull(entity, properties.updateUserIdField, id)
        setIfNull(entity, properties.updateByField, name)
        setIfNull(entity, properties.updateTimeField, now)
    }

    @PreUpdate
    fun preUpdate(entity: Any) {
        if (!properties.enable) return
        val now = Instant.now().epochSecond
        val id = provider?.currentOperatorId()
        val name = provider?.currentOperatorName()
        setAlways(entity, properties.updateUserIdField, id)
        setAlways(entity, properties.updateByField, name)
        setAlways(entity, properties.updateTimeField, now)
    }

    private fun setIfNull(target: Any, fieldName: String, value: Any?) { /* reflection lookup + null guard */ }
    private fun setAlways(target: Any, fieldName: String, value: Any?) { /* reflection lookup + assign */ }
}
```

```text
# AutoConfiguration.imports
com.only.engine.audit.config.AuditAutoConfiguration
```

- [ ] **Step 4: Run engine-audit tests to verify pass**

Run: `./gradlew :engine-audit:test`  
Expected: PASS with lifecycle and auto-configuration coverage.

- [ ] **Step 5: Commit**

```bash
git -C C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine add settings.gradle.kts engine-audit
git -C C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine commit -m "feat: add engine-audit runtime module with lifecycle autofill"
```

### Task 9: Add engine-satoken Default `AuditOperatorProvider`

**Files:**
- Modify: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine/engine-satoken/build.gradle.kts`
- Modify: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine/engine-satoken/src/main/kotlin/com/only/engine/satoken/config/SaTokenAutoConfiguration.kt`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine/engine-satoken/src/main/kotlin/com/only/engine/satoken/provider/SaTokenAuditOperatorProvider.kt`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine/engine-satoken/src/test/kotlin/com/only/engine/satoken/config/SaTokenAuditProviderAutoConfigurationTest.kt`

- [ ] **Step 1: Add failing satoken auto-config test**

```kotlin
@SpringBootTest(
    classes = [SaTokenAutoConfiguration::class],
    properties = [
        "only.engine.satoken.enable=true",
        "only.engine.sa-token.enable=true"
    ]
)
class SaTokenAuditProviderAutoConfigurationTest {

    @Autowired
    private lateinit var provider: AuditOperatorProvider

    @Test
    fun `satoken auto config provides default audit operator provider bean`() {
        assertNotNull(provider)
        assertEquals(SaTokenAuditOperatorProvider::class.java, provider::class.java)
    }
}
```

- [ ] **Step 2: Run satoken tests to verify failure**

Run: `./gradlew :engine-satoken:test --tests "*SaTokenAuditProviderAutoConfigurationTest*"`  
Expected: FAIL because `AuditOperatorProvider` bean is not provided.

- [ ] **Step 3: Implement provider and bean wiring**

```kotlin
// engine-satoken/build.gradle.kts
dependencies {
    // existing
    implementation(project(":engine-audit"))
}
```

```kotlin
// SaTokenAuditOperatorProvider.kt
class SaTokenAuditOperatorProvider : AuditOperatorProvider {
    override fun currentOperatorId(): Any? = LoginHelper.getUserInfo()?.id
    override fun currentOperatorName(): String? = LoginHelper.getUserInfo()?.username
}
```

```kotlin
// SaTokenAutoConfiguration.kt
@Bean
@ConditionalOnMissingBean(AuditOperatorProvider::class)
fun auditOperatorProvider(): AuditOperatorProvider {
    printInit(SaTokenAuditOperatorProvider::class.java, log)
    return SaTokenAuditOperatorProvider()
}
```

- [ ] **Step 4: Run satoken tests to verify pass**

Run: `./gradlew :engine-satoken:test --tests "*SaTokenAuditProviderAutoConfigurationTest*"`  
Expected: PASS with default provider bean present.

- [ ] **Step 5: Commit**

```bash
git -C C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine add engine-satoken/build.gradle.kts engine-satoken/src/main/kotlin/com/only/engine/satoken/config/SaTokenAutoConfiguration.kt engine-satoken/src/main/kotlin/com/only/engine/satoken/provider/SaTokenAuditOperatorProvider.kt engine-satoken/src/test/kotlin/com/only/engine/satoken/config/SaTokenAuditProviderAutoConfigurationTest.kt
git -C C:/Users/LD_moxeii/Documents/code/only-workspace/only-engine commit -m "feat: provide default satoken audit operator SPI implementation"
```

### Task 10: Migrate only-danmuku-zero off Audited Base-Class Dependency

**Files:**
- Delete: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-danmuku-zero/only-danmuku-domain/src/main/kotlin/edu/only4/danmuku/domain/_share/audit/AuditedEntity.kt`
- Delete: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-danmuku-zero/only-danmuku-domain/src/main/kotlin/edu/only4/danmuku/domain/_share/audit/AuditedFieldsEntity.kt`
- Delete: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-danmuku-zero/only-danmuku-domain/src/main/kotlin/edu/only4/danmuku/domain/_share/audit/AuditSupport.kt`
- Delete: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-danmuku-zero/only-danmuku-adapter/src/main/kotlin/edu/only4/danmuku/adapter/domain/_share/configure/JpaAuditingConfig.kt`
- Modify: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-danmuku-zero/docs/dogfood/cap4k-pipeline-type-registry.json`
- Modify: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-danmuku-zero/build.gradle.kts`
- Modify: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-danmuku-zero/only-danmuku-adapter/build.gradle.kts`
- Create: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-danmuku-zero/only-danmuku-start/src/test/kotlin/edu/only4/danmuku/start/audit/EngineAuditSmokeTest.kt`

- [ ] **Step 1: Add failing smoke test for startup wiring**

```kotlin
@SpringBootTest
class EngineAuditSmokeTest {

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `application context loads with engine audit module and no domain audit bridge`() {
        assertNotNull(context.getBean("auditEntityLifecycleListener"))
        assertThrows(NoSuchBeanDefinitionException::class.java) {
            context.getBean("jpaAuditingConfig")
        }
    }
}
```

- [ ] **Step 2: Run start-module smoke test to verify failure**

Run: `./gradlew :only-danmuku-start:test --tests "*EngineAuditSmokeTest*"`  
Expected: FAIL because `engine-audit` dependency is missing and old bridge still present.

- [ ] **Step 3: Apply migration edits**

```kotlin
// build.gradle.kts (cap4k config)
cap4k {
    generators {
        aggregate {
            specialFields {
                idDefaultStrategy.set("uuid7")
                deletedDefaultColumn.set("deleted")
                versionDefaultColumn.set("version")
                managedDefaultColumns.set(
                    listOf(
                        "create_user_id",
                        "create_by",
                        "create_time",
                        "update_user_id",
                        "update_by",
                        "update_time",
                    )
                )
            }
        }
    }
}
```

```kotlin
// only-danmuku-adapter/build.gradle.kts
dependencies {
    // existing
    api("com.only4:engine-audit:${libs.versions.only-engine.get()}")
}
```

```json
// docs/dogfood/cap4k-pipeline-type-registry.json
{
  // remove AuditedFieldsEntity registration entry
}
```

```text
// delete domain audit base classes and adapter bridge config files
```

- [ ] **Step 4: Run project tests/generation to verify pass**

Run: `./gradlew cap4kGenerate :only-danmuku-start:test --tests "*EngineAuditSmokeTest*"`  
Expected: PASS with generated entities containing audit columns and startup without old base-class bridge.

- [ ] **Step 5: Commit**

```bash
git -C C:/Users/LD_moxeii/Documents/code/only-workspace/only-danmuku-zero add build.gradle.kts only-danmuku-adapter/build.gradle.kts docs/dogfood/cap4k-pipeline-type-registry.json only-danmuku-start/src/test/kotlin/edu/only4/danmuku/start/audit/EngineAuditSmokeTest.kt
git -C C:/Users/LD_moxeii/Documents/code/only-workspace/only-danmuku-zero rm only-danmuku-domain/src/main/kotlin/edu/only4/danmuku/domain/_share/audit/AuditedEntity.kt only-danmuku-domain/src/main/kotlin/edu/only4/danmuku/domain/_share/audit/AuditedFieldsEntity.kt only-danmuku-domain/src/main/kotlin/edu/only4/danmuku/domain/_share/audit/AuditSupport.kt only-danmuku-adapter/src/main/kotlin/edu/only4/danmuku/adapter/domain/_share/configure/JpaAuditingConfig.kt
git -C C:/Users/LD_moxeii/Documents/code/only-workspace/only-danmuku-zero commit -m "refactor: remove audited base inheritance and adopt engine audit runtime module"
```

### Task 11: Cross-Repo Verification Matrix and Doc Sync

**Files:**
- Modify: `docs/superpowers/mainline-roadmap.md` (status update only, if this slice is merged complete)
- Modify: `docs/superpowers/specs/2026-05-03-cap4k-special-fields-managed-write-surface-and-only-engine-audit-alignment-design.md` (only if implementation naming deviates)

- [ ] **Step 1: Run cap4k targeted verification**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-api:test --tests "*PipelineModelsTest*"
./gradlew :cap4k-plugin-pipeline-source-db:test --tests "*DbColumnAnnotationParserTest*" --tests "*DbSchemaSourceProviderTest*" --tests "*DbTableAnnotationParserTest*"
./gradlew :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest*" --tests "*DefaultPipelineRunnerTest*"
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest*"
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleArtifactRendererTest*"
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kProjectConfigFactoryTest*" --tests "*PipelinePluginFunctionalTest*cap4kPlan*" --tests "*PipelinePluginCompileFunctionalTest*"
```

Expected: PASS for all targeted cap4k modules.

- [ ] **Step 2: Run only-engine targeted verification**

Run:

```bash
./gradlew :engine-spi:test --tests "*AuditOperatorProviderContractTest*"
./gradlew :engine-audit:test
./gradlew :engine-satoken:test --tests "*SaTokenAuditProviderAutoConfigurationTest*"
```

Expected: PASS for SPI, audit module, and satoken bridge tests.

- [ ] **Step 3: Run only-danmuku-zero verification**

Run:

```bash
./gradlew cap4kPlan cap4kGenerate
./gradlew :only-danmuku-start:test --tests "*EngineAuditSmokeTest*"
```

Expected: PASS with plan output showing managed/write-surface policies and startup smoke test green after migration.

- [ ] **Step 4: Update docs only if code naming diverged**

```markdown
- If implementation renamed properties/classes from spec text, update the spec to final names.
- If all naming matched spec, skip spec edits.
- Update roadmap status only after all matrix checks pass in merged branch.
```

- [ ] **Step 5: Final commit**

```bash
git add docs/superpowers/mainline-roadmap.md docs/superpowers/specs/2026-05-03-cap4k-special-fields-managed-write-surface-and-only-engine-audit-alignment-design.md
git commit -m "docs: align roadmap and spec status after special-field audit alignment delivery"
```

## Self-Review

### 1) Spec coverage check

- cap4k managed special-field model + plan exposure: Task 1-6.
- only-engine pluggable audit module (`engine-audit`): Task 8.
- engine-satoken default SPI implementation: Task 9.
- only-danmuku-zero remove audited base-class dependency and keep audit columns: Task 10.
- cross-repo verification: Task 11.

No spec requirement is left without a task.

### 2) Placeholder scan

- No `TODO/TBD` placeholders remain.
- Every task has concrete file paths, code snippets, commands, expected outcomes, and commit step.

### 3) Type and naming consistency

- Uses one naming set across tasks: `managedDefaultColumns`, `SpecialFieldWritePolicy`, `ResolvedManagedFieldPolicy`, `ResolvedWriteSurfacePolicy`, `AuditOperatorProvider`.
- only-engine module name and package conventions align with existing repository style (`engine-<module>`, `com.only.engine.<module>`).
