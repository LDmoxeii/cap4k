# Cap4k Aggregate Factory / Specification Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing `aggregate` generator with bounded factory/specification parity, consuming deterministic entity type references and verifying the new families through planner, renderer, functional, and compile-level checks.

**Architecture:** This slice stays inside the existing `aggregate` generator. It adds two aggregate family planners driven by `CanonicalModel.entities`, two aggregate preset templates, and one compile-capable aggregate fixture. The implementation explicitly consumes planner-owned `entityTypeFqn` and preserves old family naming/package layout without restoring old source semantics or mutable `typeMapping`.

**Tech Stack:** Kotlin, JUnit 5, Gradle TestKit, Pebble preset rendering, existing aggregate functional fixtures

---

## File Structure

### New files

- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SpecificationArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/specification.kt.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/schema.sql`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateCompileSmoke.kt`

### Existing files to modify

- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

### Responsibilities

- `FactoryArtifactPlanner.kt`
  - plan `<Entity>Factory` artifacts from `CanonicalModel.entities`
  - preserve old `entity.packageName + ".factory"` layout
  - surface planner-owned `entityTypeFqn`

- `SpecificationArtifactPlanner.kt`
  - plan `<Entity>Specification` artifacts from `CanonicalModel.entities`
  - preserve old `entity.packageName + ".specification"` layout
  - surface planner-owned `entityTypeFqn`

- `AggregateArtifactPlanner.kt`
  - register the new family planners into the existing aggregate generator delegate list

- `AggregateArtifactPlannerTest.kt`
  - lock output paths, package layout, type names, and derived-reference exposure rules

- `factory.kt.peb`
  - render minimal compileable factory contract using explicit imports only

- `specification.kt.peb`
  - render minimal compileable specification contract using explicit imports only

- `PebbleArtifactRendererTest.kt`
  - prove preset fallback and rendered class shapes for the new aggregate templates

- `PipelinePluginFunctionalTest.kt`
  - prove `cap4kPlan` / `cap4kGenerate` emit and write the new aggregate families in the existing aggregate sample

- `aggregate-compile-sample/*`
  - compile-capable derivative fixture for aggregate-side factory/specification verification

- `PipelinePluginCompileFunctionalTest.kt`
  - prove generated factory/specification artifacts participate in `:demo-domain:compileKotlin`

## Task 1: Extend the Aggregate Planner with Factory and Specification Families

**Files:**
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SpecificationArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

- [ ] **Step 1: Write the failing planner assertions for factory/specification artifacts**

Add these assertions inside `plans schema entity and repository artifacts into domain and adapter modules` in `AggregateArtifactPlannerTest.kt`:

```kotlin
assertEquals(5, planItems.size)
assertEquals(
    "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt",
    planItems.first { it.templateId == "aggregate/factory.kt.peb" }.outputPath,
)
assertEquals(
    "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/specification/VideoPostSpecification.kt",
    planItems.first { it.templateId == "aggregate/specification.kt.peb" }.outputPath,
)
```

Add these focused context assertions:

```kotlin
val factoryContext = planItems.first { it.templateId == "aggregate/factory.kt.peb" }.context
assertEquals("com.acme.demo.domain.aggregates.video_post.factory", factoryContext["packageName"])
assertEquals("VideoPostFactory", factoryContext["typeName"])
assertEquals("Payload", factoryContext["payloadTypeName"])
assertEquals("VideoPost", factoryContext["entityName"])
assertEquals("com.acme.demo.domain.aggregates.video_post.VideoPost", factoryContext["entityTypeFqn"])
assertEquals("VideoPost", factoryContext["aggregateName"])

val specificationContext = planItems.first { it.templateId == "aggregate/specification.kt.peb" }.context
assertEquals("com.acme.demo.domain.aggregates.video_post.specification", specificationContext["packageName"])
assertEquals("VideoPostSpecification", specificationContext["typeName"])
assertEquals("VideoPost", specificationContext["entityName"])
assertEquals("com.acme.demo.domain.aggregates.video_post.VideoPost", specificationContext["entityTypeFqn"])
assertEquals("VideoPost", specificationContext["aggregateName"])
```

Replace the old schema-only boundary test with a new one:

```kotlin
@Test
fun `derived aggregate references are exposed only to schema factory and specification contexts`() {
    val config = aggregateConfig()
    val model = CanonicalModel(
        schemas = listOf(
            SchemaModel(
                name = "SVideoPost",
                packageName = "com.acme.demo.domain._share.meta.video_post",
                entityName = "VideoPost",
                comment = "Video post schema",
                fields = listOf(FieldModel("id", "Long")),
            )
        ),
        entities = listOf(
            EntityModel(
                name = "VideoPost",
                packageName = "com.acme.demo.domain.aggregates.video_post",
                tableName = "video_post",
                comment = "Video post entity",
                fields = listOf(FieldModel("id", "Long")),
                idField = FieldModel("id", "Long"),
            )
        ),
        repositories = listOf(
            RepositoryModel(
                name = "VideoPostRepository",
                packageName = "com.acme.demo.adapter.domain.repositories",
                entityName = "VideoPost",
                idType = "Long",
            )
        ),
    )

    val planItems = AggregateArtifactPlanner().plan(config, model)

    val schemaContext = planItems.first { it.templateId == "aggregate/schema.kt.peb" }.context
    val factoryContext = planItems.first { it.templateId == "aggregate/factory.kt.peb" }.context
    val specificationContext = planItems.first { it.templateId == "aggregate/specification.kt.peb" }.context
    val entityContext = planItems.first { it.templateId == "aggregate/entity.kt.peb" }.context
    val repositoryContext = planItems.first { it.templateId == "aggregate/repository.kt.peb" }.context

    assertEquals("com.acme.demo.domain.aggregates.video_post.VideoPost", schemaContext["entityTypeFqn"])
    assertEquals("com.acme.demo.domain.aggregates.video_post.QVideoPost", schemaContext["qEntityTypeFqn"])
    assertEquals("com.acme.demo.domain.aggregates.video_post.VideoPost", factoryContext["entityTypeFqn"])
    assertEquals("com.acme.demo.domain.aggregates.video_post.VideoPost", specificationContext["entityTypeFqn"])
    assertEquals(false, entityContext.containsKey("entityTypeFqn"))
    assertEquals(false, repositoryContext.containsKey("entityTypeFqn"))
}
```

- [ ] **Step 2: Run the aggregate planner test class and verify it fails**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest"
```

Expected:

- FAIL because `aggregate/factory.kt.peb` and `aggregate/specification.kt.peb` are not planned yet

- [ ] **Step 3: Implement the two new family planners and register them**

Create `FactoryArtifactPlanner.kt` with this implementation:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class FactoryArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val domainRoot = requireRelativeModule(config, "domain")
        val derivedReferences = AggregateDerivedTypeReferences.from(model)

        return model.entities.map { entity ->
            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/factory.kt.peb",
                outputPath = "$domainRoot/src/main/kotlin/${entity.packageName.replace(".", "/")}/factory/${entity.name}Factory.kt",
                context = mapOf(
                    "packageName" to "${entity.packageName}.factory",
                    "typeName" to "${entity.name}Factory",
                    "payloadTypeName" to "Payload",
                    "entityName" to entity.name,
                    "entityTypeFqn" to derivedReferences.entityFqn(entity.name).orEmpty(),
                    "aggregateName" to entity.name,
                    "comment" to entity.comment,
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
```

Create `SpecificationArtifactPlanner.kt` with this implementation:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class SpecificationArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val domainRoot = requireRelativeModule(config, "domain")
        val derivedReferences = AggregateDerivedTypeReferences.from(model)

        return model.entities.map { entity ->
            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/specification.kt.peb",
                outputPath = "$domainRoot/src/main/kotlin/${entity.packageName.replace(".", "/")}/specification/${entity.name}Specification.kt",
                context = mapOf(
                    "packageName" to "${entity.packageName}.specification",
                    "typeName" to "${entity.name}Specification",
                    "entityName" to entity.name,
                    "entityTypeFqn" to derivedReferences.entityFqn(entity.name).orEmpty(),
                    "aggregateName" to entity.name,
                    "comment" to entity.comment,
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
```

Update `AggregateArtifactPlanner.kt` delegate list to:

```kotlin
private val delegates: List<AggregateArtifactFamilyPlanner> = listOf(
    SchemaArtifactPlanner(),
    EntityArtifactPlanner(),
    RepositoryArtifactPlanner(),
    FactoryArtifactPlanner(),
    SpecificationArtifactPlanner(),
)
```

- [ ] **Step 4: Re-run the planner test class and verify it passes**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest"
```

Expected:

- PASS
- plan size becomes `5`
- new context keys are present only where intended

- [ ] **Step 5: Commit the planner slice**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SpecificationArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: add aggregate factory specification planners"
```

## Task 2: Add Aggregate Factory/Specification Preset Templates and Renderer Coverage

**Files:**
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/specification.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Add failing renderer assertions for factory/specification preset fallback**

Extend the existing aggregate preset fallback test in `PebbleArtifactRendererTest.kt` by adding these two plan items:

```kotlin
ArtifactPlanItem(
    generatorId = "aggregate",
    moduleRole = "domain",
    templateId = "aggregate/factory.kt.peb",
    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/factory/OrderFactory.kt",
    context = mapOf(
        "packageName" to "com.acme.demo.domain.aggregates.order.factory",
        "typeName" to "OrderFactory",
        "payloadTypeName" to "Payload",
        "entityName" to "Order",
        "entityTypeFqn" to "com.acme.demo.domain.aggregates.order.Order",
        "aggregateName" to "Order",
        "comment" to "Order aggregate",
    ),
    conflictPolicy = ConflictPolicy.SKIP,
),
ArtifactPlanItem(
    generatorId = "aggregate",
    moduleRole = "domain",
    templateId = "aggregate/specification.kt.peb",
    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/specification/OrderSpecification.kt",
    context = mapOf(
        "packageName" to "com.acme.demo.domain.aggregates.order.specification",
        "typeName" to "OrderSpecification",
        "entityName" to "Order",
        "entityTypeFqn" to "com.acme.demo.domain.aggregates.order.Order",
        "aggregateName" to "Order",
        "comment" to "Order aggregate",
    ),
    conflictPolicy = ConflictPolicy.SKIP,
),
```

Add these assertions after rendering:

```kotlin
val factoryContent = rendered.first { it.outputPath.endsWith("/factory/OrderFactory.kt") }.content
val specificationContent = rendered.first { it.outputPath.endsWith("/specification/OrderSpecification.kt") }.content

assertTrue(factoryContent.contains("import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory"))
assertTrue(factoryContent.contains("import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload"))
assertTrue(factoryContent.contains("import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate"))
assertTrue(factoryContent.contains("import org.springframework.stereotype.Service"))
assertTrue(factoryContent.contains("import com.acme.demo.domain.aggregates.order.Order"))
assertTrue(factoryContent.contains("class OrderFactory : AggregateFactory<OrderFactory.Payload, Order>"))
assertTrue(factoryContent.contains("data class Payload("))

assertTrue(specificationContent.contains("import com.only4.cap4k.ddd.core.domain.aggregate.Specification"))
assertTrue(specificationContent.contains("import com.only4.cap4k.ddd.core.domain.aggregate.Specification.Result"))
assertTrue(specificationContent.contains("import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate"))
assertTrue(specificationContent.contains("import org.springframework.stereotype.Service"))
assertTrue(specificationContent.contains("import com.acme.demo.domain.aggregates.order.Order"))
assertTrue(specificationContent.contains("class OrderSpecification : Specification<Order>"))
assertTrue(specificationContent.contains("return Result.pass()"))
```

- [ ] **Step 2: Run the renderer test class and verify it fails**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleArtifactRendererTest"
```

Expected:

- FAIL because the two aggregate preset templates do not exist yet

- [ ] **Step 3: Add the two aggregate preset templates**

Create `factory.kt.peb` with this content:

```pebble
package {{ packageName }}

import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
import org.springframework.stereotype.Service
import {{ entityTypeFqn }}

@Service
@Aggregate(
    aggregate = "{{ aggregateName }}",
    name = "{{ typeName }}",
    type = Aggregate.TYPE_FACTORY,
    description = ""
)
class {{ typeName }} : AggregateFactory<{{ typeName }}.{{ payloadTypeName }}, {{ entityName }}> {

    override fun create(payload: {{ payloadTypeName }}): {{ entityName }} {
        return {{ entityName }}()
    }

    @Aggregate(
        aggregate = "{{ aggregateName }}",
        name = "{{ payloadTypeName }}",
        type = Aggregate.TYPE_FACTORY_PAYLOAD,
        description = ""
    )
    data class {{ payloadTypeName }}(
        val name: String
    ) : AggregatePayload<{{ entityName }}>
}
```

Create `specification.kt.peb` with this content:

```pebble
package {{ packageName }}

import com.only4.cap4k.ddd.core.domain.aggregate.Specification
import com.only4.cap4k.ddd.core.domain.aggregate.Specification.Result
import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
import org.springframework.stereotype.Service
import {{ entityTypeFqn }}

@Service
@Aggregate(
    aggregate = "{{ aggregateName }}",
    name = "{{ typeName }}",
    type = Aggregate.TYPE_SPECIFICATION,
    description = ""
)
class {{ typeName }} : Specification<{{ entityName }}> {

    override fun specify(entity: {{ entityName }}): Result {
        return Result.pass()
    }
}
```

- [ ] **Step 4: Re-run the renderer test class and verify it passes**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleArtifactRendererTest"
```

Expected:

- PASS
- aggregate fallback test now renders five aggregate families
- existing `use helper is unavailable in aggregate templates` test still passes unchanged

- [ ] **Step 5: Commit the renderer slice**

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/specification.kt.peb
git add cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: add aggregate factory specification templates"
```

## Task 3: Extend Functional and Compile Verification for Aggregate Factory/Specification Parity

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/schema.sql`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateCompileSmoke.kt`

- [ ] **Step 1: Add failing aggregate functional assertions to the existing aggregate sample**

In `PipelinePluginFunctionalTest.kt`, extend the existing aggregate-sample generate test with these assertions:

```kotlin
assertTrue(
    File(
        projectDir.toFile(),
        "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt"
    ).exists()
)
assertTrue(
    File(
        projectDir.toFile(),
        "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/specification/VideoPostSpecification.kt"
    ).exists()
)
assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/factory.kt.peb\""))
assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/specification.kt.peb\""))
```

Add content assertions after generation:

```kotlin
val factoryContent = projectDir.resolve(
    "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt"
).readText()
val specificationContent = projectDir.resolve(
    "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/specification/VideoPostSpecification.kt"
).readText()

assertTrue(factoryContent.contains("class VideoPostFactory : AggregateFactory<VideoPostFactory.Payload, VideoPost>"))
assertTrue(factoryContent.contains("import com.acme.demo.domain.aggregates.video_post.VideoPost"))
assertTrue(specificationContent.contains("class VideoPostSpecification : Specification<VideoPost>"))
assertTrue(specificationContent.contains("return Result.pass()"))
```

- [ ] **Step 2: Run the targeted aggregate functional test and verify it fails**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginFunctionalTest*aggregate*"
```

Expected:

- FAIL because aggregate plan/generate currently emits only schema/entity/repository

- [ ] **Step 3: Create the compile-capable aggregate fixture and compile test**

Create `aggregate-compile-sample/build.gradle.kts` with this content:

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
        }
    }
}
```

Create `aggregate-compile-sample/settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
    }
}

includeBuild("__CAP4K_REPO_ROOT__")

rootProject.name = "aggregate-compile-sample"
include("demo-domain", "demo-adapter")
```

Create `aggregate-compile-sample/schema.sql`:

```sql
create table if not exists video_post (
    id bigint primary key,
    slug varchar(128) not null unique,
    title varchar(255) not null,
    published boolean default false
);
```

Create `aggregate-compile-sample/demo-domain/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.only4:ddd-core:0.4.2-SNAPSHOT")
    implementation("org.springframework:spring-context")
}
```

Create `aggregate-compile-sample/demo-adapter/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.only4:ddd-core:0.4.2-SNAPSHOT")
}
```

Create `aggregate-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateCompileSmoke.kt`:

```kotlin
package com.acme.demo.domain.aggregates.video_post

import com.acme.demo.domain.aggregates.video_post.factory.VideoPostFactory
import com.acme.demo.domain.aggregates.video_post.specification.VideoPostSpecification

class AggregateCompileSmoke(
    private val factory: VideoPostFactory,
    private val specification: VideoPostSpecification,
) {
    fun wire(): Pair<VideoPostFactory, VideoPostSpecification> = factory to specification
}
```

Add this test to `PipelinePluginCompileFunctionalTest.kt`:

```kotlin
@Test
fun `aggregate factory and specification generation participates in domain compileKotlin`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-compile")
    FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-compile-sample")

    val beforeGenerateCompileResult = FunctionalFixtureSupport
        .runner(projectDir, ":demo-domain:compileKotlin")
        .buildAndFail()
    assertEquals(
        TaskOutcome.FAILED,
        beforeGenerateCompileResult.task(":demo-domain:compileKotlin")?.outcome
    )
    assertTrue(beforeGenerateCompileResult.output.contains("VideoPostFactory"))

    val generateResult = FunctionalFixtureSupport
        .runner(projectDir, "cap4kGenerate")
        .build()
    val compileResult = FunctionalFixtureSupport
        .runner(projectDir, ":demo-domain:compileKotlin")
        .build()

    assertGeneratedFilesExist(
        projectDir,
        "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt",
        "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/specification/VideoPostSpecification.kt",
    )
    assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
}
```

- [ ] **Step 4: Run the targeted functional and compile tests, then implement any missing glue until they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginFunctionalTest*aggregate*" --tests "*PipelinePluginCompileFunctionalTest*aggregate*"
```

Expected:

- initial FAIL until planner/template work and fixture assumptions align
- final PASS with:
  - aggregate plan/generate test green
  - aggregate compile verification green

- [ ] **Step 5: Commit the functional/compile slice**

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample
git commit -m "test: cover aggregate factory specification parity"
```

## Task 4: Full Regression and Boundary Verification

**Files:**
- Verify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/*.kt`
- Verify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/*.peb`
- Verify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/*.kt`

- [ ] **Step 1: Run the focused suites for this slice**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest" `
          :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleArtifactRendererTest" `
          :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginFunctionalTest*aggregate*" --tests "*PipelinePluginCompileFunctionalTest*aggregate*"
```

Expected:

- aggregate planner tests pass
- aggregate renderer tests pass
- aggregate functional and compile tests pass

- [ ] **Step 2: Run the broad regression suite touched by this slice**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-api:test `
          :cap4k-plugin-pipeline-core:test `
          :cap4k-plugin-pipeline-generator-aggregate:test `
          :cap4k-plugin-pipeline-renderer-pebble:test `
          :cap4k-plugin-pipeline-gradle:test
```

Expected:

- PASS
- no regression to existing aggregate schema/entity/repository behavior
- no regression to compile harness behavior

- [ ] **Step 3: Confirm working tree cleanliness**

Run:

```powershell
git status --short --branch
```

Expected:

- working tree clean
- branch ahead of `origin/master` with the expected commits only

## Self-Review

### Spec coverage

- Extend existing aggregate generator instead of new DSL:
  - Task 1
- Consume deterministic `entityTypeFqn`:
  - Tasks 1 and 2
- Preserve old naming/package layout:
  - Tasks 1 and 3
- Add planner, renderer, functional, and compile verification:
  - Tasks 1 through 4
- Avoid source semantic recovery and full aggregate parity:
  - Tasks 1 through 4 stay inside existing `CanonicalModel.entities` and current aggregate DSL

### Placeholder scan

Run:

```powershell
Get-Content docs/superpowers/plans/2026-04-17-cap4k-aggregate-factory-specification-parity.md |
    Select-Object -SkipLast 20 |
    Select-String -Pattern "TBD|TODO|implement later|similar to|appropriate error handling|fill in details"
```

Expected:

- no matches

### Type consistency

This plan consistently uses:

- `FactoryArtifactPlanner`
- `SpecificationArtifactPlanner`
- `aggregate/factory.kt.peb`
- `aggregate/specification.kt.peb`
- `entityTypeFqn`
- `payloadTypeName`
- `aggregate-compile-sample`

Do not rename these during implementation unless the entire plan is updated consistently.
