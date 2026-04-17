# Cap4k Aggregate Wrapper Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing `aggregate` generator with bounded wrapper parity so it plans, renders, generates, and compile-verifies `Agg<Entity>` wrapper artifacts without restoring old naming DSL or source-side switches.

**Architecture:** This slice stays inside the current `aggregate` generator. It adds one wrapper family planner, extends the aggregate derived-type reference helper with deterministic factory references, adds one aggregate preset template, and reuses the existing aggregate functional and compile fixtures to verify wrapper output end to end.

**Tech Stack:** Kotlin, JUnit 5, Gradle TestKit, Pebble preset rendering, existing aggregate DB-source fixtures

---

## File Structure

### New files

- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateWrapperArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/wrapper.kt.peb`

### Existing files to modify

- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateDerivedTypeReferences.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateCompileSmoke.kt`

### Responsibilities

- `AggregateWrapperArtifactPlanner.kt`
  - derive `Agg<Entity>` wrapper artifacts from `CanonicalModel.entities`
  - preserve entity-package placement
  - surface planner-owned `entityTypeFqn`, `factoryTypeFqn`, and `idType`

- `AggregateDerivedTypeReferences.kt`
  - keep aggregate-side type references deterministic and collision-safe
  - extend the helper with wrapper-needed factory FQNs without reintroducing mutable runtime maps

- `AggregateArtifactPlanner.kt`
  - register wrapper planning in the existing aggregate delegate list

- `AggregateArtifactPlannerTest.kt`
  - lock wrapper path, naming, package placement, and derived-reference behavior

- `wrapper.kt.peb`
  - render the minimal compile-safe wrapper class and nested `Id` type using explicit aggregate-side imports only

- `PebbleArtifactRendererTest.kt`
  - prove preset fallback for `aggregate/wrapper.kt.peb`
  - lock the wrapper inheritance and factory payload contract

- `PipelinePluginFunctionalTest.kt`
  - prove `cap4kPlan` and `cap4kGenerate` emit wrapper artifacts in the aggregate sample

- `AggregateCompileSmoke.kt`
  - make the aggregate compile fixture consume the generated wrapper and nested `Id`

- `PipelinePluginCompileFunctionalTest.kt`
  - prove wrapper, factory, specification, and entity types compile together in `:demo-domain:compileKotlin`

## Task 1: Extend Aggregate Planning with Wrapper Artifacts

**Files:**
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateWrapperArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateDerivedTypeReferences.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

- [ ] **Step 1: Add failing wrapper planner assertions**

In `AggregateArtifactPlannerTest.kt`, extend `plans schema entity and repository artifacts into domain and adapter modules` with wrapper expectations:

```kotlin
assertEquals(6, planItems.size)
assertEquals(
    "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggVideoPost.kt",
    planItems.first { it.templateId == "aggregate/wrapper.kt.peb" }.outputPath,
)

val wrapperContext = planItems.first { it.templateId == "aggregate/wrapper.kt.peb" }.context
assertEquals("com.acme.demo.domain.aggregates.video_post", wrapperContext["packageName"])
assertEquals("AggVideoPost", wrapperContext["typeName"])
assertEquals("VideoPost", wrapperContext["entityName"])
assertEquals("com.acme.demo.domain.aggregates.video_post.VideoPost", wrapperContext["entityTypeFqn"])
assertEquals("VideoPostFactory", wrapperContext["factoryTypeName"])
assertEquals(
    "com.acme.demo.domain.aggregates.video_post.factory.VideoPostFactory",
    wrapperContext["factoryTypeFqn"],
)
assertEquals("Long", wrapperContext["idType"])
assertEquals("Video post entity", wrapperContext["comment"])
```

Extend `schema is ambiguity-safe and factory plus specification planners use the current entity when names collide` so wrapper contexts are also locked:

```kotlin
val primaryWrapperContext = planItems.first {
    it.templateId == "aggregate/wrapper.kt.peb" &&
        it.context["packageName"] == "com.acme.demo.domain.aggregates.primary_video_post"
}.context
val secondaryWrapperContext = planItems.first {
    it.templateId == "aggregate/wrapper.kt.peb" &&
        it.context["packageName"] == "com.acme.demo.domain.aggregates.secondary_video_post"
}.context

assertEquals(
    "com.acme.demo.domain.aggregates.primary_video_post.VideoPost",
    primaryWrapperContext["entityTypeFqn"],
)
assertEquals(
    "com.acme.demo.domain.aggregates.primary_video_post.factory.VideoPostFactory",
    primaryWrapperContext["factoryTypeFqn"],
)
assertEquals(
    "com.acme.demo.domain.aggregates.secondary_video_post.VideoPost",
    secondaryWrapperContext["entityTypeFqn"],
)
assertEquals(
    "com.acme.demo.domain.aggregates.secondary_video_post.factory.VideoPostFactory",
    secondaryWrapperContext["factoryTypeFqn"],
)
```

Add a new blank-package determinism test:

```kotlin
@Test
fun `wrapper planner uses simple names when entity package is blank`() {
    val config = aggregateConfig()
    val model = CanonicalModel(
        entities = listOf(
            EntityModel(
                name = "VideoPost",
                packageName = "",
                tableName = "video_post",
                comment = "Video post entity",
                fields = listOf(FieldModel("id", "Long")),
                idField = FieldModel("id", "Long"),
            )
        )
    )

    val planItems = AggregateArtifactPlanner().plan(config, model)
    val wrapperContext = planItems.first { it.templateId == "aggregate/wrapper.kt.peb" }.context

    assertEquals("", wrapperContext["packageName"])
    assertEquals("AggVideoPost", wrapperContext["typeName"])
    assertEquals("VideoPost", wrapperContext["entityTypeFqn"])
    assertEquals("VideoPostFactory", wrapperContext["factoryTypeFqn"])
    assertEquals("Long", wrapperContext["idType"])
}
```

- [ ] **Step 2: Run the aggregate planner test class and verify it fails**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest"
```

Expected:

- FAIL because `aggregate/wrapper.kt.peb` is not planned yet
- wrapper-specific context keys are missing

- [ ] **Step 3: Implement deterministic wrapper planning**

Create `AggregateWrapperArtifactPlanner.kt` with this implementation:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class AggregateWrapperArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val domainRoot = requireRelativeModule(config, "domain")
        val derivedTypeReferences = AggregateDerivedTypeReferences.from(model)

        return model.entities.map { entity ->
            val typeName = "Agg${entity.name}"
            val relativePath = if (entity.packageName.isBlank()) {
                "$typeName.kt"
            } else {
                "${entity.packageName.replace(".", "/")}/$typeName.kt"
            }

            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/wrapper.kt.peb",
                outputPath = "$domainRoot/src/main/kotlin/$relativePath",
                context = mapOf(
                    "packageName" to entity.packageName,
                    "typeName" to typeName,
                    "entityName" to entity.name,
                    "entityTypeFqn" to derivedTypeReferences.entityFqn(entity),
                    "factoryTypeName" to "${entity.name}Factory",
                    "factoryTypeFqn" to derivedTypeReferences.factoryFqn(entity),
                    "idType" to entity.idField.type,
                    "comment" to entity.comment,
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
```

Update `AggregateDerivedTypeReferences.kt` to surface deterministic factory FQNs:

```kotlin
internal class AggregateDerivedTypeReferences private constructor(
    private val entityFqns: Map<String, String?>,
) {
    fun entityFqn(entityName: String): String? = entityFqns[entityName]

    fun entityFqn(entity: EntityModel): String = buildEntityFqn(entity)

    fun qEntityFqn(entityName: String): String? =
        entityFqn(entityName)?.let(::buildQEntityFqn)

    fun factoryFqn(entity: EntityModel): String = buildFactoryFqn(entity.packageName, entity.name)

    companion object {
        // existing from(...) and entity/qEntity helpers stay unchanged

        private fun buildFactoryFqn(packageName: String, entityName: String): String {
            val factoryTypeName = "${entityName}Factory"
            return if (packageName.isBlank()) {
                factoryTypeName
            } else {
                "$packageName.factory.$factoryTypeName"
            }
        }
    }
}
```

Update the delegate list in `AggregateArtifactPlanner.kt`:

```kotlin
private val delegates: List<AggregateArtifactFamilyPlanner> = listOf(
    SchemaArtifactPlanner(),
    EntityArtifactPlanner(),
    RepositoryArtifactPlanner(),
    FactoryArtifactPlanner(),
    SpecificationArtifactPlanner(),
    AggregateWrapperArtifactPlanner(),
)
```

- [ ] **Step 4: Re-run the aggregate planner test class and verify it passes**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest"
```

Expected:

- PASS
- wrapper output path is `.../AggVideoPost.kt`
- duplicate simple-name entities still keep entity-specific wrapper references

- [ ] **Step 5: Commit the planner slice**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateDerivedTypeReferences.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateWrapperArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: add aggregate wrapper planner"
```

## Task 2: Add the Aggregate Wrapper Preset and Renderer Coverage

**Files:**
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/wrapper.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Add failing renderer assertions for wrapper fallback**

In `PebbleArtifactRendererTest.kt`, extend the existing aggregate preset fallback test by appending this plan item:

```kotlin
ArtifactPlanItem(
    generatorId = "aggregate",
    moduleRole = "domain",
    templateId = "aggregate/wrapper.kt.peb",
    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/AggOrder.kt",
    context = mapOf(
        "packageName" to "com.acme.demo.domain.aggregates.order",
        "typeName" to "AggOrder",
        "entityName" to "Order",
        "entityTypeFqn" to "com.acme.demo.domain.aggregates.order.Order",
        "factoryTypeName" to "OrderFactory",
        "factoryTypeFqn" to "com.acme.demo.domain.aggregates.order.factory.OrderFactory",
        "idType" to "Long",
        "comment" to "Order aggregate",
    ),
    conflictPolicy = ConflictPolicy.SKIP,
)
```

Add these assertions after `factoryContent` and `specificationContent`:

```kotlin
val wrapperContent = contentFor("/aggregates/order/AggOrder.kt")

assertTrue(wrapperContent.contains("import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate"))
assertTrue(wrapperContent.contains("import com.acme.demo.domain.aggregates.order.Order"))
assertTrue(wrapperContent.contains("import com.acme.demo.domain.aggregates.order.factory.OrderFactory"))
assertTrue(wrapperContent.contains("class AggOrder("))
assertTrue(wrapperContent.contains("payload: OrderFactory.Payload? = null"))
assertTrue(wrapperContent.contains(") : Aggregate.Default<Order>(payload)"))
assertTrue(wrapperContent.contains("val id by lazy { root.id }"))
assertTrue(
    wrapperContent.contains(
        "class Id(key: Long) : com.only4.cap4k.ddd.core.domain.aggregate.Id.Default<AggOrder, Long>(key)"
    )
)
```

- [ ] **Step 2: Run the renderer test class and verify it fails**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleArtifactRendererTest"
```

Expected:

- FAIL because `aggregate/wrapper.kt.peb` does not exist yet

- [ ] **Step 3: Add the aggregate wrapper preset template**

Create `wrapper.kt.peb` with this content:

```pebble
package {{ packageName }}

import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
import {{ entityTypeFqn }}
import {{ factoryTypeFqn }}

/**
 * {{ entityName }}聚合封装
 * {{ comment }}
 */
class {{ typeName }}(
    payload: {{ factoryTypeName }}.Payload? = null,
) : Aggregate.Default<{{ entityName }}>(payload) {

    val id by lazy { root.id }

    class Id(key: {{ idType }}) :
        com.only4.cap4k.ddd.core.domain.aggregate.Id.Default<{{ typeName }}, {{ idType }}>(key)
}
```

- [ ] **Step 4: Re-run the renderer test class and verify it passes**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleArtifactRendererTest"
```

Expected:

- PASS
- fallback rendering now includes the wrapper preset
- wrapper output consumes planner-owned `factoryTypeFqn` instead of guessing factory package in Pebble

- [ ] **Step 5: Commit the renderer slice**

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/wrapper.kt.peb
git add cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: add aggregate wrapper template"
```

## Task 3: Lock Wrapper Plan, Generate, and Compile Behavior in Functional Tests

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateCompileSmoke.kt`

- [ ] **Step 1: Add failing functional assertions for wrapper planning and generation**

In `PipelinePluginFunctionalTest.kt`, extend `cap4kPlan and cap4kGenerate produce aggregate artifacts from db schema` with wrapper assertions:

```kotlin
val wrapperFile = projectDir.resolve(
    "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggVideoPost.kt"
)

assertTrue(wrapperFile.toFile().exists())
val wrapperContent = wrapperFile.readText()

assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/wrapper.kt.peb\""))
assertTrue(wrapperContent.contains("class AggVideoPost("))
assertTrue(wrapperContent.contains("payload: VideoPostFactory.Payload? = null"))
assertTrue(wrapperContent.contains(") : Aggregate.Default<VideoPost>(payload)"))
assertTrue(wrapperContent.contains("val id by lazy { root.id }"))
assertTrue(
    wrapperContent.contains(
        "class Id(key: Long) : com.only4.cap4k.ddd.core.domain.aggregate.Id.Default<AggVideoPost, Long>(key)"
    )
)
```

In `PipelinePluginCompileFunctionalTest.kt`, extend `aggregate factory and specification generation participates in domain compileKotlin` with wrapper assertions:

```kotlin
assertTrue(beforeGenerateCompileResult.output.contains("AggVideoPost"))
assertGeneratedFilesExist(
    projectDir,
    "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt",
    "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/specification/VideoPostSpecification.kt",
    "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggVideoPost.kt",
)
```

Update the compile smoke file `AggregateCompileSmoke.kt` to reference the wrapper and nested `Id`:

```kotlin
package com.acme.demo.domain.aggregates.video_post

import com.acme.demo.domain.aggregates.video_post.factory.VideoPostFactory
import com.acme.demo.domain.aggregates.video_post.specification.VideoPostSpecification

class AggregateCompileSmoke(
    private val factory: VideoPostFactory,
    private val specification: VideoPostSpecification,
) {
    fun wire(): Triple<VideoPostFactory, VideoPostSpecification, AggVideoPost.Id> {
        val aggregate = AggVideoPost()
        return Triple(factory, specification, AggVideoPost.Id(1L))
    }
}
```

- [ ] **Step 2: Run the functional and compile tests and verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*cap4kPlan and cap4kGenerate produce aggregate artifacts from db schema" --tests "*aggregate factory and specification generation participates in domain compileKotlin"
```

Expected:

- FAIL because wrapper artifacts are not planned/generated yet
- compile fixture now references `AggVideoPost`

- [ ] **Step 3: Implement the minimal fixture-consumption changes**

Make the `PipelinePluginFunctionalTest.kt` assertions line up with the new wrapper artifact:

```kotlin
val wrapperFile = projectDir.resolve(
    "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggVideoPost.kt"
)
val wrapperContent = wrapperFile.readText()

assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/wrapper.kt.peb\""))
assertTrue(wrapperContent.contains("import com.acme.demo.domain.aggregates.video_post.factory.VideoPostFactory"))
assertTrue(wrapperContent.contains("class AggVideoPost("))
assertTrue(wrapperContent.contains("payload: VideoPostFactory.Payload? = null"))
```

Make the compile test assert the wrapper-specific failure mode before generation and success after generation:

```kotlin
assertEquals(
    TaskOutcome.FAILED,
    beforeGenerateCompileResult.task(":demo-domain:compileKotlin")?.outcome
)
assertTrue(beforeGenerateCompileResult.output.contains("AggVideoPost"))
assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
```

- [ ] **Step 4: Re-run the targeted functional and compile tests and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*cap4kPlan and cap4kGenerate produce aggregate artifacts from db schema" --tests "*aggregate factory and specification generation participates in domain compileKotlin"
```

Expected:

- PASS
- aggregate sample now writes and verifies `AggVideoPost.kt`
- aggregate compile fixture proves wrapper, factory, specification, and entity coexist in `:demo-domain:compileKotlin`

- [ ] **Step 5: Commit the functional slice**

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateCompileSmoke.kt
git commit -m "test: cover aggregate wrapper parity"
```

## Task 4: Run Aggregate-Focused Regression and Final Verification

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateWrapperArtifactPlanner.kt` if any regression fix is required
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/wrapper.kt.peb` if any compile-contract fix is required
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt` only if targeted regression exposes a wrapper-specific issue
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt` only if targeted regression exposes a wrapper-specific issue
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt` only if targeted regression exposes a wrapper-specific issue
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt` only if targeted regression exposes a wrapper-specific issue

- [ ] **Step 1: Run the aggregate module regression set**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test
```

Expected:

- PASS
- no wrapper regressions in aggregate planner, renderer, or Gradle functional coverage

- [ ] **Step 2: If regression exposes a wrapper-only issue, fix it minimally and re-run the failing command**

Only make changes if the failure is directly caused by wrapper parity. Acceptable examples:

```kotlin
val relativePath = if (entity.packageName.isBlank()) {
    "$typeName.kt"
} else {
    "${entity.packageName.replace(".", "/")}/$typeName.kt"
}
```

```pebble
class {{ typeName }}(
    payload: {{ factoryTypeName }}.Payload? = null,
) : Aggregate.Default<{{ entityName }}>(payload) {
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
git log --oneline -5
```

Expected:

- branch shows only the wrapper parity work for this slice
- no unexpected modified or untracked files remain

- [ ] **Step 5: Commit any final wrapper-only regression fix**

If Step 2 required a code or template adjustment, commit it separately:

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateWrapperArtifactPlanner.kt
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/wrapper.kt.peb
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "fix: harden aggregate wrapper parity"
```

If no extra fix was required, mark this step complete without creating another commit.
