# Cap4k Aggregate Unique-Constraint Family Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing `aggregate` generator with bounded unique-constraint family parity so it can generate representative `unique_query`, `unique_query_handler`, and `unique_validator` artifacts with deterministic naming, stable type references, and compile-verified representative fixtures.

**Architecture:** This slice stays inside the current `aggregate` generator. It first enriches current aggregate canonical input just enough to surface unique constraints on `EntityModel`, then adds one aggregate-internal shared unique-constraint planning helper, three family planners, and three aggregate preset templates. The final stage reuses the existing aggregate functional and compile fixtures, upgraded to include an application module, to prove the full query/handler/validator family closure.

**Tech Stack:** Kotlin, JUnit 5, Gradle TestKit, Pebble preset rendering, existing aggregate DB-source fixtures

---

## File Structure

### New files

- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateUniqueConstraintPlanning.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/UniqueQueryArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/UniqueQueryHandlerArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/UniqueValidatorArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query_handler.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_validator.kt.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/AggregateUniqueApplicationCompileSmoke.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video_post/unique/AggregateUniqueAdapterCompileSmoke.kt`

### Existing files to modify

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/settings.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/settings.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-adapter/build.gradle.kts`

### Responsibilities

- `PipelineModels.kt`
  - extend `EntityModel` just enough to carry `uniqueConstraints` without introducing a new top-level canonical slice

- `DefaultCanonicalAssembler.kt`
  - propagate `DbTableSnapshot.uniqueConstraints` into canonical entity models

- `AggregateUniqueConstraintPlanning.kt`
  - provide one shared deterministic planning layer for suffix derivation, request prop derivation, id type selection, and family type-name coordination

- `UniqueQueryArtifactPlanner.kt`
  - emit application-side unique query contracts from entity unique constraints

- `UniqueQueryHandlerArtifactPlanner.kt`
  - emit adapter-side handler contracts tied to generated unique queries

- `UniqueValidatorArtifactPlanner.kt`
  - emit application-side validators tied to the corresponding generated unique queries

- `AggregateArtifactPlanner.kt`
  - register the three new family planners into the existing aggregate generator

- `AggregateArtifactPlannerTest.kt`
  - lock aggregate unique family naming, output roles, output paths, and derived request/id semantics

- `Cap4kProjectConfigFactory.kt`
  - require `project.applicationModulePath` when `aggregate` is enabled
  - include `application` module in aggregate project config

- `Cap4kProjectConfigFactoryTest.kt`
  - lock the new aggregate module requirement and positive aggregate module inclusion

- `unique_query.kt.peb`
  - render compile-safe minimal unique query request/response contract

- `unique_query_handler.kt.peb`
  - render compile-safe minimal unique query handler contract without restoring old `_share.model`

- `unique_validator.kt.peb`
  - render compile-safe minimal validator contract tied to the generated unique query type

- `PebbleArtifactRendererTest.kt`
  - prove preset fallback and minimal compile-safe shapes for all three templates

- aggregate functional fixtures
  - upgrade aggregate sample and compile sample to include an application module because aggregate parity will now emit application-side artifacts

- `PipelinePluginFunctionalTest.kt`
  - prove `cap4kPlan` / `cap4kGenerate` write all three unique-constraint family artifacts

- `PipelinePluginCompileFunctionalTest.kt`
  - prove generated unique query / validator / handler artifacts participate in representative application and adapter compile flows

## Task 1: Enrich Aggregate Canonical Input and Project Gating

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/demo-application/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/demo-adapter/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-application/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-adapter/build.gradle.kts`

- [ ] **Step 1: Write failing tests for canonical unique-constraint carry-over and aggregate application module gating**

In `DefaultCanonicalAssemblerTest.kt`, add this test:

```kotlin
@Test
fun `db aggregate assembly carries unique constraints into canonical entity models`() {
    val assembler = DefaultCanonicalAssembler()

    val result = assembler.assemble(
        config = baseConfig(),
        snapshots = listOf(
            DbSchemaSnapshot(
                tables = listOf(
                    DbTableSnapshot(
                        tableName = "video_post",
                        comment = "Video post",
                        columns = listOf(
                            DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            DbColumnSnapshot("slug", "VARCHAR", "String", false),
                            DbColumnSnapshot("title", "VARCHAR", "String", false),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = listOf(listOf("slug")),
                    )
                )
            )
        )
    ).model

    val entity = result.entities.single()
    assertEquals("VideoPost", entity.name)
    assertEquals(listOf(listOf("slug")), entity.uniqueConstraints)
}
```

In `Cap4kProjectConfigFactoryTest.kt`, replace the old aggregate requirement test with:

```kotlin
@Test
fun `aggregate generator requires domain application and adapter modules`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

    extension.project {
        basePackage.set("com.acme.demo")
    }
    extension.sources {
        db {
            enabled.set(true)
            url.set("jdbc:h2:mem:test")
            username.set("sa")
            password.set("secret")
        }
    }
    extension.generators {
        aggregate { enabled.set(true) }
    }

    val error = assertThrows(IllegalArgumentException::class.java) {
        Cap4kProjectConfigFactory().build(project, extension)
    }

    assertEquals(
        "project.domainModulePath, project.applicationModulePath, and project.adapterModulePath are required when aggregate is enabled.",
        error.message,
    )
}
```

Add this positive config test:

```kotlin
@Test
fun `factory includes domain application and adapter modules when aggregate is enabled`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

    extension.project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
        applicationModulePath.set("demo-application")
        adapterModulePath.set("demo-adapter")
    }
    extension.sources {
        db {
            enabled.set(true)
            url.set("jdbc:h2:mem:test")
            username.set("sa")
            password.set("secret")
        }
    }
    extension.generators {
        aggregate { enabled.set(true) }
    }

    val config = Cap4kProjectConfigFactory().build(project, extension)

    assertEquals(
        mapOf(
            "domain" to "demo-domain",
            "application" to "demo-application",
            "adapter" to "demo-adapter",
        ),
        config.modules,
    )
    assertEquals(setOf("aggregate"), config.enabledGeneratorIds())
}
```

- [ ] **Step 2: Run the focused canonical and config tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest" :cap4k-plugin-pipeline-gradle:test --tests "*aggregate generator requires domain application and adapter modules" --tests "*factory includes domain application and adapter modules when aggregate is enabled"
```

Expected:

- FAIL because `EntityModel` does not expose `uniqueConstraints`
- FAIL because aggregate config still only requires `domain` and `adapter`

- [ ] **Step 3: Implement minimal canonical and config changes**

In `PipelineModels.kt`, extend `EntityModel`:

```kotlin
data class EntityModel(
    val name: String,
    val packageName: String,
    val tableName: String,
    val comment: String,
    val fields: List<FieldModel>,
    val idField: FieldModel,
    val uniqueConstraints: List<List<String>> = emptyList(),
)
```

In `DefaultCanonicalAssembler.kt`, populate the new field:

```kotlin
EntityModel(
    name = entityName,
    packageName = "${config.basePackage}.domain.aggregates.$segment",
    tableName = table.tableName,
    comment = table.comment,
    fields = fields,
    idField = idField,
    uniqueConstraints = table.uniqueConstraints,
)
```

In `Cap4kProjectConfigFactory.kt`, tighten aggregate rules:

```kotlin
if (generators.aggregateEnabled) {
    val missingDomain = extension.project.domainModulePath.optionalValue() == null
    val missingApplication = extension.project.applicationModulePath.optionalValue() == null
    val missingAdapter = extension.project.adapterModulePath.optionalValue() == null
    if (missingDomain || missingApplication || missingAdapter) {
        throw IllegalArgumentException(
            "project.domainModulePath, project.applicationModulePath, and project.adapterModulePath are required when aggregate is enabled."
        )
    }
}
```

and include `application` in aggregate modules:

```kotlin
if (generators.aggregateEnabled) {
    put("domain", extension.project.domainModulePath.required("project.domainModulePath"))
    put("application", extension.project.applicationModulePath.required("project.applicationModulePath"))
    put("adapter", extension.project.adapterModulePath.required("project.adapterModulePath"))
}
```

- [ ] **Step 4: Upgrade aggregate fixture topology so aggregate keeps a valid module graph**

In both aggregate fixture root builds, add:

```kotlin
cap4k {
    project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
        applicationModulePath.set("demo-application")
        adapterModulePath.set("demo-adapter")
    }
    // existing source and generator blocks stay unchanged
}
```

In both `settings.gradle.kts` files, include the new module:

```kotlin
include("demo-domain", "demo-application", "demo-adapter")
```

Create `aggregate-sample/demo-application/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.only4:ddd-core:0.4.2-SNAPSHOT")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.20")
}
```

Create `aggregate-compile-sample/demo-application/build.gradle.kts` with the same content.

Update `aggregate-compile-sample/demo-adapter/build.gradle.kts` to depend on the application module:

```kotlin
dependencies {
    implementation(project(":demo-application"))
    implementation("com.only4:ddd-core:0.4.2-SNAPSHOT")
    implementation("org.springframework:spring-context")
}
```

- [ ] **Step 5: Re-run the focused canonical and config tests and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest" :cap4k-plugin-pipeline-gradle:test --tests "*aggregate generator requires domain application and adapter modules" --tests "*factory includes domain application and adapter modules when aggregate is enabled"
```

Expected:

- PASS
- aggregate fixtures now declare `demo-application`
- aggregate config now includes `application` in `config.modules`

- [ ] **Step 6: Commit the canonical and config slice**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt
git add cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/build.gradle.kts
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/settings.gradle.kts
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/demo-application/build.gradle.kts
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/build.gradle.kts
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/settings.gradle.kts
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-application/build.gradle.kts
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-adapter/build.gradle.kts
git commit -m "feat: add aggregate unique constraint prerequisites"
```

## Task 2: Add Shared Unique-Constraint Planning and Preset Templates

**Files:**
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateUniqueConstraintPlanning.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/UniqueQueryArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/UniqueQueryHandlerArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/UniqueValidatorArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query_handler.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_validator.kt.peb`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Write failing aggregate planner assertions for the three unique families**

In `AggregateArtifactPlannerTest.kt`, add a new test using one unique constraint on `slug`:

```kotlin
@Test
fun `plans unique query handler and validator artifacts from entity unique constraints`() {
    val config = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = mapOf(
            "domain" to "demo-domain",
            "application" to "demo-application",
            "adapter" to "demo-adapter",
        ),
        sources = emptyMap(),
        generators = mapOf("aggregate" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )

    val model = CanonicalModel(
        entities = listOf(
            EntityModel(
                name = "VideoPost",
                packageName = "com.acme.demo.domain.aggregates.video_post",
                tableName = "video_post",
                comment = "Video post entity",
                fields = listOf(
                    FieldModel("id", "Long"),
                    FieldModel("slug", "String"),
                    FieldModel("title", "String"),
                ),
                idField = FieldModel("id", "Long"),
                uniqueConstraints = listOf(listOf("slug")),
            )
        )
    )

    val planItems = AggregateArtifactPlanner().plan(config, model)

    val query = planItems.first { it.templateId == "aggregate/unique_query.kt.peb" }
    val handler = planItems.first { it.templateId == "aggregate/unique_query_handler.kt.peb" }
    val validator = planItems.first { it.templateId == "aggregate/unique_validator.kt.peb" }

    assertEquals(
        "demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/UniqueVideoPostSlugQry.kt",
        query.outputPath,
    )
    assertEquals(
        "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video_post/unique/UniqueVideoPostSlugQryHandler.kt",
        handler.outputPath,
    )
    assertEquals(
        "demo-application/src/main/kotlin/com/acme/demo/application/validators/video_post/unique/UniqueVideoPostSlug.kt",
        validator.outputPath,
    )

    assertEquals("UniqueVideoPostSlugQry", query.context["typeName"])
    assertEquals("UniqueVideoPostSlugQryHandler", handler.context["typeName"])
    assertEquals("UniqueVideoPostSlug", validator.context["typeName"])
    assertEquals("Long", query.context["idType"])
    assertEquals("excludeVideoPostId", query.context["excludeIdParamName"])
}
```

Add a suffix determinism test for composite unique constraints:

```kotlin
@Test
fun `shared unique planning derives one suffix per constraint and keeps family names aligned`() {
    val planning = AggregateUniqueConstraintPlanning.from(
        entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "Video post entity",
            fields = listOf(
                FieldModel("id", "Long"),
                FieldModel("tenantId", "Long"),
                FieldModel("slug", "String"),
            ),
            idField = FieldModel("id", "Long"),
            uniqueConstraints = listOf(listOf("tenantId", "slug")),
        )
    )

    val selection = planning.single()
    assertEquals("TenantIdSlug", selection.suffix)
    assertEquals("UniqueVideoPostTenantIdSlugQry", selection.queryTypeName)
    assertEquals("UniqueVideoPostTenantIdSlugQryHandler", selection.queryHandlerTypeName)
    assertEquals("UniqueVideoPostTenantIdSlug", selection.validatorTypeName)
}
```

- [ ] **Step 2: Run the aggregate planner tests and verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest"
```

Expected:

- FAIL because unique-constraint planners and helper do not exist yet

- [ ] **Step 3: Implement the shared planning helper and three planners**

Create `AggregateUniqueConstraintPlanning.kt` with this shape:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel

internal data class AggregateUniqueConstraintSelection(
    val suffix: String,
    val requestProps: List<FieldModel>,
    val idType: String,
    val excludeIdParamName: String,
    val queryTypeName: String,
    val queryHandlerTypeName: String,
    val validatorTypeName: String,
)

internal object AggregateUniqueConstraintPlanning {
    fun from(entity: EntityModel): List<AggregateUniqueConstraintSelection> {
        return entity.uniqueConstraints.map { columns ->
            val selectedFields = columns.map { columnName ->
                entity.fields.first { it.name.equals(columnName, ignoreCase = true) }
            }
            val suffix = selectedFields.joinToString("") { field ->
                field.name.replaceFirstChar { it.uppercase() }
            }
            AggregateUniqueConstraintSelection(
                suffix = suffix,
                requestProps = selectedFields,
                idType = entity.idField.type,
                excludeIdParamName = "exclude${entity.name}Id",
                queryTypeName = "Unique${entity.name}${suffix}Qry",
                queryHandlerTypeName = "Unique${entity.name}${suffix}QryHandler",
                validatorTypeName = "Unique${entity.name}${suffix}",
            )
        }
    }
}
```

Create `UniqueQueryArtifactPlanner.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class UniqueQueryArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModule(config, "application")
        return model.entities.flatMap { entity ->
            AggregateUniqueConstraintPlanning.from(entity).map { selection ->
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "application",
                    templateId = "aggregate/unique_query.kt.peb",
                    outputPath = "$applicationRoot/src/main/kotlin/${config.basePackage.replace(".", "/")}/application/queries/${entity.tableName}/unique/${selection.queryTypeName}.kt",
                    context = mapOf(
                        "packageName" to "${config.basePackage}.application.queries.${entity.tableName}.unique",
                        "typeName" to selection.queryTypeName,
                        "entityName" to entity.name,
                        "requestProps" to selection.requestProps,
                        "idType" to selection.idType,
                        "excludeIdParamName" to selection.excludeIdParamName,
                    ),
                    conflictPolicy = config.templates.conflictPolicy,
                )
            }
        }
    }
}
```

Create `UniqueQueryHandlerArtifactPlanner.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class UniqueQueryHandlerArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val adapterRoot = requireRelativeModule(config, "adapter")
        return model.entities.flatMap { entity ->
            AggregateUniqueConstraintPlanning.from(entity).map { selection ->
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "adapter",
                    templateId = "aggregate/unique_query_handler.kt.peb",
                    outputPath = "$adapterRoot/src/main/kotlin/${config.basePackage.replace(".", "/")}/adapter/queries/${entity.tableName}/unique/${selection.queryHandlerTypeName}.kt",
                    context = mapOf(
                        "packageName" to "${config.basePackage}.adapter.queries.${entity.tableName}.unique",
                        "typeName" to selection.queryHandlerTypeName,
                        "queryTypeName" to selection.queryTypeName,
                        "queryTypeFqn" to "${config.basePackage}.application.queries.${entity.tableName}.unique.${selection.queryTypeName}",
                    ),
                    conflictPolicy = config.templates.conflictPolicy,
                )
            }
        }
    }
}
```

Create `UniqueValidatorArtifactPlanner.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class UniqueValidatorArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModule(config, "application")
        return model.entities.flatMap { entity ->
            AggregateUniqueConstraintPlanning.from(entity).map { selection ->
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "application",
                    templateId = "aggregate/unique_validator.kt.peb",
                    outputPath = "$applicationRoot/src/main/kotlin/${config.basePackage.replace(".", "/")}/application/validators/${entity.tableName}/unique/${selection.validatorTypeName}.kt",
                    context = mapOf(
                        "packageName" to "${config.basePackage}.application.validators.${entity.tableName}.unique",
                        "typeName" to selection.validatorTypeName,
                        "queryTypeName" to selection.queryTypeName,
                        "queryTypeFqn" to "${config.basePackage}.application.queries.${entity.tableName}.unique.${selection.queryTypeName}",
                        "requestProps" to selection.requestProps,
                        "idType" to selection.idType,
                        "excludeIdParamName" to selection.excludeIdParamName,
                        "entityName" to entity.name,
                    ),
                    conflictPolicy = config.templates.conflictPolicy,
                )
            }
        }
    }
}
```

Register the planners in `AggregateArtifactPlanner.kt`:

```kotlin
private val delegates: List<AggregateArtifactFamilyPlanner> = listOf(
    SchemaArtifactPlanner(),
    EntityArtifactPlanner(),
    RepositoryArtifactPlanner(),
    FactoryArtifactPlanner(),
    SpecificationArtifactPlanner(),
    AggregateWrapperArtifactPlanner(),
    UniqueQueryArtifactPlanner(),
    UniqueQueryHandlerArtifactPlanner(),
    UniqueValidatorArtifactPlanner(),
)
```

- [ ] **Step 4: Add failing renderer assertions for the three aggregate unique templates**

In `PebbleArtifactRendererTest.kt`, extend the aggregate preset fallback test with these plan items:

```kotlin
ArtifactPlanItem(
    generatorId = "aggregate",
    moduleRole = "application",
    templateId = "aggregate/unique_query.kt.peb",
    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/UniqueVideoPostSlugQry.kt",
    context = mapOf(
        "packageName" to "com.acme.demo.application.queries.video_post.unique",
        "typeName" to "UniqueVideoPostSlugQry",
        "entityName" to "VideoPost",
        "requestProps" to listOf(mapOf("name" to "slug", "type" to "String")),
        "idType" to "Long",
        "excludeIdParamName" to "excludeVideoPostId",
    ),
    conflictPolicy = ConflictPolicy.SKIP,
),
ArtifactPlanItem(
    generatorId = "aggregate",
    moduleRole = "adapter",
    templateId = "aggregate/unique_query_handler.kt.peb",
    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video_post/unique/UniqueVideoPostSlugQryHandler.kt",
    context = mapOf(
        "packageName" to "com.acme.demo.adapter.queries.video_post.unique",
        "typeName" to "UniqueVideoPostSlugQryHandler",
        "queryTypeName" to "UniqueVideoPostSlugQry",
        "queryTypeFqn" to "com.acme.demo.application.queries.video_post.unique.UniqueVideoPostSlugQry",
    ),
    conflictPolicy = ConflictPolicy.SKIP,
),
ArtifactPlanItem(
    generatorId = "aggregate",
    moduleRole = "application",
    templateId = "aggregate/unique_validator.kt.peb",
    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/validators/video_post/unique/UniqueVideoPostSlug.kt",
    context = mapOf(
        "packageName" to "com.acme.demo.application.validators.video_post.unique",
        "typeName" to "UniqueVideoPostSlug",
        "queryTypeName" to "UniqueVideoPostSlugQry",
        "queryTypeFqn" to "com.acme.demo.application.queries.video_post.unique.UniqueVideoPostSlugQry",
        "requestProps" to listOf(mapOf("name" to "slug", "type" to "String")),
        "idType" to "Long",
        "excludeIdParamName" to "excludeVideoPostId",
        "entityName" to "VideoPost",
    ),
    conflictPolicy = ConflictPolicy.SKIP,
),
```

Add these assertions:

```kotlin
val uniqueQueryContent = contentFor("/application/queries/video_post/unique/UniqueVideoPostSlugQry.kt")
val uniqueHandlerContent = contentFor("/adapter/queries/video_post/unique/UniqueVideoPostSlugQryHandler.kt")
val uniqueValidatorContent = contentFor("/application/validators/video_post/unique/UniqueVideoPostSlug.kt")

assertTrue(uniqueQueryContent.contains("object UniqueVideoPostSlugQry"))
assertTrue(uniqueQueryContent.contains("import com.only4.cap4k.ddd.core.application.RequestParam"))
assertTrue(uniqueQueryContent.contains("val excludeVideoPostId: Long?"))

assertTrue(uniqueHandlerContent.contains("class UniqueVideoPostSlugQryHandler"))
assertTrue(uniqueHandlerContent.contains("import com.only4.cap4k.ddd.core.application.query.Query"))
assertTrue(uniqueHandlerContent.contains("import com.acme.demo.application.queries.video_post.unique.UniqueVideoPostSlugQry"))

assertTrue(uniqueValidatorContent.contains("annotation class UniqueVideoPostSlug"))
assertTrue(uniqueValidatorContent.contains("import jakarta.validation.Constraint"))
assertTrue(uniqueValidatorContent.contains("import com.acme.demo.application.queries.video_post.unique.UniqueVideoPostSlugQry"))
```

- [ ] **Step 5: Run planner and renderer tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest" :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleArtifactRendererTest"
```

Expected:

- FAIL because planners and preset templates do not exist yet

- [ ] **Step 6: Add the three aggregate unique preset templates**

Create `unique_query.kt.peb`:

```pebble
package {{ packageName }}

import com.only4.cap4k.ddd.core.application.RequestParam

object {{ typeName }} {

    data class Request(
{%- for prop in requestProps %}
        val {{ prop.name }}: {{ prop.type }},
{%- endfor %}
        val {{ excludeIdParamName }}: {{ idType }}?
    ) : RequestParam<Response>

    data class Response(
        val exists: Boolean
    )
}
```

Create `unique_query_handler.kt.peb`:

```pebble
package {{ packageName }}

import com.only4.cap4k.ddd.core.application.query.Query
import org.springframework.stereotype.Service
import {{ queryTypeFqn }}

@Service
class {{ typeName }} : Query<{{ queryTypeName }}.Request, {{ queryTypeName }}.Response> {
    override fun exec(request: {{ queryTypeName }}.Request): {{ queryTypeName }}.Response {
        return {{ queryTypeName }}.Response(
            exists = false
        )
    }
}
```

Create `unique_validator.kt.peb`:

```pebble
package {{ packageName }}

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass
import {{ queryTypeFqn }}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [{{ typeName }}.Validator::class])
annotation class {{ typeName }}(
    val message: String = "唯一性校验未通过",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
) {
    class Validator : ConstraintValidator<{{ typeName }}, Any> {
        override fun isValid(value: Any?, context: ConstraintValidatorContext): Boolean = true
    }
}
```

- [ ] **Step 7: Re-run planner and renderer tests and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest" :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleArtifactRendererTest"
```

Expected:

- PASS
- all three new template ids render through preset fallback
- planner locks naming and path rules for the unique family

- [ ] **Step 8: Commit the planning and renderer slice**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateUniqueConstraintPlanning.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/UniqueQueryArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/UniqueQueryHandlerArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/UniqueValidatorArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query.kt.peb
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query_handler.kt.peb
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_validator.kt.peb
git add cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: add aggregate unique constraint planners"
```

## Task 3: Add Functional and Compile Verification for the Unique Family

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/AggregateUniqueApplicationCompileSmoke.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video_post/unique/AggregateUniqueAdapterCompileSmoke.kt`

- [ ] **Step 1: Add failing functional assertions for unique query, handler, and validator generation**

In `PipelinePluginFunctionalTest.kt`, extend `cap4kPlan and cap4kGenerate produce aggregate artifacts from db schema` with:

```kotlin
val uniqueQueryFile = projectDir.resolve(
    "demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/UniqueVideoPostSlugQry.kt"
)
val uniqueHandlerFile = projectDir.resolve(
    "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video_post/unique/UniqueVideoPostSlugQryHandler.kt"
)
val uniqueValidatorFile = projectDir.resolve(
    "demo-application/src/main/kotlin/com/acme/demo/application/validators/video_post/unique/UniqueVideoPostSlug.kt"
)

assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/unique_query.kt.peb\""))
assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/unique_query_handler.kt.peb\""))
assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/unique_validator.kt.peb\""))
assertTrue(uniqueQueryFile.toFile().exists())
assertTrue(uniqueHandlerFile.toFile().exists())
assertTrue(uniqueValidatorFile.toFile().exists())
assertTrue(uniqueQueryFile.readText().contains("object UniqueVideoPostSlugQry"))
assertTrue(uniqueHandlerFile.readText().contains("class UniqueVideoPostSlugQryHandler"))
assertTrue(uniqueValidatorFile.readText().contains("annotation class UniqueVideoPostSlug"))
```

In `PipelinePluginCompileFunctionalTest.kt`, add two new tests:

```kotlin
@Test
fun `aggregate unique query and validator generation participates in application compileKotlin`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-unique-application-compile")
    FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-compile-sample")

    val beforeGenerateCompileResult = FunctionalFixtureSupport
        .runner(projectDir, ":demo-application:compileKotlin")
        .buildAndFail()
    assertTrue(beforeGenerateCompileResult.output.contains("UniqueVideoPostSlugQry"))

    val generateResult = FunctionalFixtureSupport
        .runner(projectDir, "cap4kGenerate")
        .build()
    val compileResult = FunctionalFixtureSupport
        .runner(projectDir, ":demo-application:compileKotlin")
        .build()

    assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
}

@Test
fun `aggregate unique query handler generation participates in adapter compileKotlin`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-unique-adapter-compile")
    FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-compile-sample")

    val beforeGenerateCompileResult = FunctionalFixtureSupport
        .runner(projectDir, ":demo-adapter:compileKotlin")
        .buildAndFail()
    assertTrue(beforeGenerateCompileResult.output.contains("UniqueVideoPostSlugQryHandler"))

    val generateResult = FunctionalFixtureSupport
        .runner(projectDir, "cap4kGenerate")
        .build()
    val compileResult = FunctionalFixtureSupport
        .runner(projectDir, ":demo-adapter:compileKotlin")
        .build()

    assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
}
```

- [ ] **Step 2: Run the targeted functional and compile tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*cap4kPlan and cap4kGenerate produce aggregate artifacts from db schema" --tests "*aggregate unique query and validator generation participates in application compileKotlin" --tests "*aggregate unique query handler generation participates in adapter compileKotlin"
```

Expected:

- FAIL because unique family files are not yet asserted in fixtures and compile smoke sources do not reference them

- [ ] **Step 3: Add compile smoke sources that force generated unique family participation**

Create `AggregateUniqueApplicationCompileSmoke.kt`:

```kotlin
package com.acme.demo.application.queries.video_post.unique

import com.acme.demo.application.validators.video_post.unique.UniqueVideoPostSlug

class AggregateUniqueApplicationCompileSmoke(
    private val validatorAnnotation: UniqueVideoPostSlug,
) {
    fun wire(): Pair<UniqueVideoPostSlugQry.Request, Class<UniqueVideoPostSlug>> =
        UniqueVideoPostSlugQry.Request(
            slug = "demo-slug",
            excludeVideoPostId = 1L,
        ) to UniqueVideoPostSlug::class.java
}
```

Create `AggregateUniqueAdapterCompileSmoke.kt`:

```kotlin
package com.acme.demo.adapter.queries.video_post.unique

import com.acme.demo.application.queries.video_post.unique.UniqueVideoPostSlugQry

class AggregateUniqueAdapterCompileSmoke(
    private val handler: UniqueVideoPostSlugQryHandler,
) {
    fun wire(): Pair<UniqueVideoPostSlugQryHandler, Class<UniqueVideoPostSlugQry.Response>> =
        handler to UniqueVideoPostSlugQry.Response::class.java
}
```

Update `demo-adapter/build.gradle.kts` in the compile fixture to include the application module dependency if it is not already present:

```kotlin
dependencies {
    implementation(project(":demo-application"))
    implementation("com.only4:ddd-core:0.4.2-SNAPSHOT")
    implementation("org.springframework:spring-context")
}
```

- [ ] **Step 4: Re-run the targeted functional and compile tests and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*cap4kPlan and cap4kGenerate produce aggregate artifacts from db schema" --tests "*aggregate unique query and validator generation participates in application compileKotlin" --tests "*aggregate unique query handler generation participates in adapter compileKotlin"
```

Expected:

- PASS
- aggregate plan/generate test now proves all three unique family artifacts are produced
- application compile depends on generated unique query and validator
- adapter compile depends on generated unique handler

- [ ] **Step 5: Commit the functional and compile slice**

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/AggregateUniqueApplicationCompileSmoke.kt
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video_post/unique/AggregateUniqueAdapterCompileSmoke.kt
git commit -m "test: cover aggregate unique family parity"
```

## Task 4: Run Aggregate-Focused Regression and Final Verification

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateUniqueConstraintPlanning.kt` only if a regression exposes a unique-family bug
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query.kt.peb` only if a regression exposes a unique query contract bug
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query_handler.kt.peb` only if a regression exposes a handler contract bug
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_validator.kt.peb` only if a regression exposes a validator contract bug
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt` only if a regression exposes a unique-family test gap
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt` only if a regression exposes a unique-family test gap

- [ ] **Step 1: Run the aggregate-focused regression set**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test
```

Expected:

- PASS
- no unique-family regression in canonical assembly, aggregate planners, renderer fallback, or Gradle functional/compile coverage

- [ ] **Step 2: If regression exposes a unique-family-only issue, fix it minimally and re-run the exact failing command**

Only make changes if the failure is directly caused by aggregate unique family parity. Acceptable examples:

```kotlin
val suffix = selectedFields.joinToString("") { field ->
    field.name.replaceFirstChar { it.uppercase() }
}
```

```pebble
override fun exec(request: {{ queryTypeName }}.Request): {{ queryTypeName }}.Response {
    return {{ queryTypeName }}.Response(
        exists = false
    )
}
```

Re-run the exact failing command first, then continue.

- [ ] **Step 3: Run the final verification command**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 4: Verify git state before declaring completion**

Run:

```powershell
git status --short --branch
git log --oneline -6
```

Expected:

- branch shows only unique-family parity work for this slice
- no unexpected modified or untracked files remain

- [ ] **Step 5: Commit any final unique-family-only regression fix**

If Step 2 required a code or template adjustment, commit it separately:

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateUniqueConstraintPlanning.kt
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query.kt.peb
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query_handler.kt.peb
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_validator.kt.peb
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "fix: harden aggregate unique family parity"
```

If no extra fix was required, mark this step complete without creating another commit.
*** End Patch
