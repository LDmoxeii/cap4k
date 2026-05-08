# Cap4k Remove Wrapper From Core Pipeline And Docs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `wrapper` from Cap4k core aggregate DSL, planner, renderer, tests, and live docs so it no longer appears as a supported core pipeline capability.

**Architecture:** Treat wrapper as an active core contract removal, not as a deprecated shim. Delete the DSL/config/planner/template/schema branches that still materialize wrapper, then rewrite tests and fixtures around the remaining supported aggregate surfaces such as `factory` and `specification`. Live docs are updated only where they still describe wrapper as a current capability.

**Tech Stack:** Kotlin, Gradle, JUnit 5, Gradle TestKit, Pebble templates, Markdown docs.

---

## Scope Guard

Do:

- work from current `cap4k/master@3bb6ce76`
- implement in an isolated worktree
- prefer real deletion over deprecated no-op shims
- update only live docs that still mislead readers about wrapper support

Do not:

- touch `settings.gradle.kts`
- mix in `cap4k-plugin-codegen` removal
- sweep archive docs under `docs/superpowers/specs/**` or `docs/superpowers/plans/**`
- expand into runtime wrapper cleanup in `ddd-core` or `ddd-domain-repo-jpa`
- redesign aggregate semantics beyond removing wrapper-specific branching

## File Map

Production files expected to change:

- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactSelection.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Delete: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateWrapperArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb`
- Delete: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/wrapper.kt.peb`

Test and fixture files expected to change:

- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolverTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateCompileSmoke.kt`

Live docs expected to change:

- Modify: `docs/public/reference/generator-dsl.zh-CN.md`
- Modify: `docs/public/authoring/generation-boundaries.zh-CN.md`
- Modify: `docs/public/authoring/generator/code-generation.zh-CN.md`
- Modify: `docs/superpowers/capability-matrix.md`

Audit-only target unless a hidden stale mention appears:

- `AGENTS.md`

### Task 1: Create The Isolated Worktree And Capture Baseline

**Files:**
- Modify: none
- Test: focused Gradle commands below

- [ ] **Step 1: Create the worktree**

Run:

```powershell
git -C C:\Users\LD_moxeii\Documents\code\cap4k worktree add C:\Users\LD_moxeii\.config\superpowers\worktrees\cap4k\feature-remove-wrapper-core-pipeline -b feature/remove-wrapper-core-pipeline
```

Expected: a new worktree exists at `C:\Users\LD_moxeii\.config\superpowers\worktrees\cap4k\feature-remove-wrapper-core-pipeline` on branch `feature/remove-wrapper-core-pipeline`.

- [ ] **Step 2: Verify the execution context**

Run:

```powershell
git -C C:\Users\LD_moxeii\.config\superpowers\worktrees\cap4k\feature-remove-wrapper-core-pipeline status --short --branch
git -C C:\Users\LD_moxeii\.config\superpowers\worktrees\cap4k\feature-remove-wrapper-core-pipeline rev-parse HEAD
```

Expected:

- branch output starts with `## feature/remove-wrapper-core-pipeline`
- `HEAD` resolves to `3bb6ce76d33545606bfe7dcaae49a0a30e5b6d97`

- [ ] **Step 3: Run the wrapper-related baseline tests before edits**

Run from the new worktree root:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolverTest"
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest"
```

Expected: PASS. This establishes the pre-removal baseline.

### Task 2: Remove Wrapper From DSL And Project Config

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`

- [ ] **Step 1: Update the config-factory tests first**

In `Cap4kProjectConfigFactoryTest.kt`, rewrite the wrapper-specific assertions so the test suite now expects only the surviving optional artifacts:

```kotlin
extension.generators {
    aggregate {
        enabled.set(true)
        artifacts {
            factory.set(true)
            specification.set(true)
            unique.set(true)
            enumTranslation.set(true)
        }
    }
}

val options = config.generators.getValue("aggregate").options

assertEquals(true, options["artifact.factory"])
assertEquals(true, options["artifact.specification"])
assertFalse(options.containsKey("artifact.wrapper"))
assertEquals(true, options["artifact.unique"])
assertEquals(true, options["artifact.enumTranslation"])
```

Also delete the dedicated `aggregate wrapper artifact requires factory artifact` test, because that dependency disappears with the feature itself.

- [ ] **Step 2: Run the focused config-factory test and confirm it fails**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest"
```

Expected: FAIL because production code still exposes and emits `artifact.wrapper`.

- [ ] **Step 3: Remove the DSL and config projection**

Apply these minimal production edits:

```kotlin
open class AggregateGeneratorArtifactsExtension @Inject constructor(objects: ObjectFactory) {
    val factory: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val specification: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val unique: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val enumTranslation: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}
```

```kotlin
if (generators.aggregateEnabled) {
    put(
        "aggregate",
        GeneratorConfig(
            enabled = true,
            options = mapOf(
                "artifact.factory" to aggregate.artifacts.factory.get(),
                "artifact.specification" to aggregate.artifacts.specification.get(),
                "artifact.unique" to aggregate.artifacts.unique.get(),
                "artifact.enumTranslation" to aggregate.artifacts.enumTranslation.get(),
            ),
        ),
    )
}
```

Delete the wrapper-specific validation block entirely:

```kotlin
if (aggregate.artifacts.wrapper.get() && !aggregate.artifacts.factory.get()) {
    throw IllegalArgumentException("aggregate wrapper artifact requires enabled aggregate factory artifact.")
}
```

- [ ] **Step 4: Re-run the config-factory test**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest"
```

Expected: PASS.

- [ ] **Step 5: Commit the DSL/config removal**

Run:

```powershell
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt
git commit -m "refactor: remove aggregate wrapper dsl surface"
```

### Task 3: Delete Wrapper Planning, Layout, And Schema Branching

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactSelection.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Delete: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateWrapperArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt`
- Delete: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/wrapper.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolverTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Rewrite the planner, layout, and renderer tests to the post-wrapper contract**

Update the planner default options helper:

```kotlin
private fun allAggregateArtifactsEnabled(): Map<String, Any?> =
    mapOf(
        "artifact.factory" to true,
        "artifact.specification" to true,
        "artifact.unique" to true,
        "artifact.enumTranslation" to true,
    )
```

Update planner assertions so schema context no longer carries wrapper state:

```kotlin
assertFalse(schema.context.containsKey("aggregateTypeFqn"))
assertFalse(schema.context.containsKey("wrapperEnabled"))
```

Delete wrapper-template rendering tests and collapse schema rendering expectations to the single non-wrapper contract:

```kotlin
assertFalse(schemaContent.contains("AggUserMessage"))
assertFalse(schemaContent.contains("AggregatePredicate"))
assertTrue(schemaContent.contains("fun predicateById(id: Any): JpaPredicate<UserMessage>"))
assertTrue(schemaContent.contains("fun predicate(builder: PredicateBuilder<SUserMessage>): JpaPredicate<UserMessage>"))
```

Update `ArtifactLayoutResolverTest.kt` to remove:

```kotlin
assertEquals(entityPackage, resolver.aggregateWrapperPackage(entityPackage))
```

- [ ] **Step 2: Run the focused tests and confirm they fail**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolverTest"
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: FAIL because wrapper-specific production code and template branches still exist.

- [ ] **Step 3: Delete the wrapper production path**

Apply these production changes:

```kotlin
internal data class AggregateArtifactSelection(
    val factoryEnabled: Boolean,
    val specificationEnabled: Boolean,
    val uniqueEnabled: Boolean,
    val enumTranslationEnabled: Boolean,
)
```

```kotlin
private val delegates: List<AggregateArtifactFamilyPlanner> = listOf(
    SchemaArtifactPlanner(),
    EntityArtifactPlanner(),
    BehaviorArtifactPlanner(),
    RepositoryArtifactPlanner(),
    FactoryArtifactPlanner(),
    SpecificationArtifactPlanner(),
    UniqueQueryArtifactPlanner(),
    UniqueQueryHandlerArtifactPlanner(),
    UniqueValidatorArtifactPlanner(),
    SharedEnumArtifactPlanner(),
    LocalEnumArtifactPlanner(),
    EnumTranslationArtifactPlanner(),
)
```

Delete `AggregateWrapperArtifactPlanner.kt` and remove this layout helper:

```kotlin
fun aggregateWrapperPackage(entityPackage: String): String =
    entityPackage
```

Collapse `SchemaArtifactPlanner.kt` render context to remove wrapper keys:

```kotlin
context = mapOf(
    "packageName" to schema.packageName,
    "typeName" to schema.name,
    "comment" to schema.comment,
    "entityName" to schema.entityName,
    "isAggregateRoot" to entity.aggregateRoot,
    "schemaRuntimePackage" to SCHEMA_RUNTIME_PACKAGE,
    "entityTypeFqn" to entityTypeFqn,
    "qEntityTypeFqn" to qEntityTypeFqn,
    "repositorySupportQuerydsl" to false,
    "imports" to imports,
    "fields" to fields,
)
```

Collapse `aggregate/schema.kt.peb` so root predicate helpers always use `JpaPredicate<{{ entityName }}>` and remove every `{% if wrapperEnabled %}` branch.

- [ ] **Step 4: Re-run the focused tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolverTest"
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: PASS.

- [ ] **Step 5: Commit the planner/template removal**

Run:

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolverTest.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactSelection.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git rm cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateWrapperArtifactPlanner.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/wrapper.kt.peb
git commit -m "refactor: remove aggregate wrapper planner and template"
```

### Task 4: Rewrite Functional And Compile Fixtures Around Supported Aggregate Surfaces

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateCompileSmoke.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Update the fixtures and tests first**

In both aggregate fixture build scripts, delete:

```kotlin
wrapper.set(true)
```

Rewrite the compile smoke fixture so it proves only the surviving checked-in helpers:

```kotlin
class AggregateCompileSmoke(
    private val factory: VideoPostFactory,
    private val specification: VideoPostSpecification,
) {
    fun wire(): Pair<VideoPostFactory, VideoPostSpecification> =
        factory to specification
}
```

Update `PipelinePluginFunctionalTest.kt` expectations from wrapper presence to wrapper absence:

```kotlin
assertFalse(planFile.readText().contains("\"templateId\": \"aggregate/wrapper.kt.peb\""))
assertFalse(wrapperFile.toFile().exists())
assertTrue(schemaContent.contains("fun predicateById(id: Any): JpaPredicate<VideoPost>"))
assertFalse(schemaContent.contains("AggVideoPost"))
```

Update `PipelinePluginCompileFunctionalTest.kt` so pre-generate compile failure no longer looks for `AggVideoPost` and generated file assertions no longer include `AggVideoPost.kt`.

- [ ] **Step 2: Run the two focused functional tests and confirm they fail**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "*cap4kPlan and cap4kGenerate produce aggregate artifacts from db schema*"
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "*aggregate factory and specification generation participates in domain compileKotlin*"
```

Expected: FAIL because production plan output still contains wrapper until Task 3 changes are in place or because test expectations are ahead of fixtures.

- [ ] **Step 3: Finish the fixture-aligned cleanup if any assertion still points at wrapper**

Use this checklist to remove any leftover wrapper references:

```text
- no fixture build.gradle.kts contains wrapper.set(...)
- no functional assertion expects aggregate/wrapper.kt.peb
- no generated-file assertion expects AggVideoPost.kt
- no compile assertion expects AggVideoPost in compiler output
```

- [ ] **Step 4: Re-run the focused functional tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "*cap4kPlan and cap4kGenerate produce aggregate artifacts from db schema*"
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "*aggregate factory and specification generation participates in domain compileKotlin*"
```

Expected: PASS.

- [ ] **Step 5: Commit the fixture and functional-test updates**

Run:

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateCompileSmoke.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "test: remove wrapper aggregate fixtures"
```

### Task 5: Remove Wrapper From Live Docs And Re-Verify The Slice

**Files:**
- Modify: `docs/public/reference/generator-dsl.zh-CN.md`
- Modify: `docs/public/authoring/generation-boundaries.zh-CN.md`
- Modify: `docs/public/authoring/generator/code-generation.zh-CN.md`
- Modify: `docs/superpowers/capability-matrix.md`
- Audit: `AGENTS.md`

- [ ] **Step 1: Update the live docs to the post-wrapper contract**

In `generator-dsl.zh-CN.md`, change the aggregate artifact example from:

```kotlin
artifacts {
    factory.set(false)
    specification.set(false)
    wrapper.set(false)
    unique.set(false)
    enumTranslation.set(false)
}
```

to:

```kotlin
artifacts {
    factory.set(false)
    specification.set(false)
    unique.set(false)
    enumTranslation.set(false)
}
```

Delete the `artifacts.wrapper` row from the reference table.

In `generation-boundaries.zh-CN.md` and `code-generation.zh-CN.md`, remove wrapper from checked-in artifact lists and from any prose that still treats it as a normal author-facing aggregate capability.

In `capability-matrix.md`, delete the entire `aggregate.wrapper` row.

- [ ] **Step 2: Re-audit `AGENTS.md` and keep it unchanged unless a stale wrapper claim appears**

Run:

```powershell
rg -n "wrapper" AGENTS.md
```

Expected: no live wrapper capability claim. If the search is empty, do not edit `AGENTS.md`.

- [ ] **Step 3: Run the minimal slice verification**

Run from the worktree root:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolverTest"
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest"
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "*cap4kPlan and cap4kGenerate produce aggregate artifacts from db schema*"
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "*aggregate factory and specification generation participates in domain compileKotlin*"
```

Expected: PASS.

- [ ] **Step 4: Commit the docs and final verification state**

Run:

```powershell
git add docs/public/reference/generator-dsl.zh-CN.md docs/public/authoring/generation-boundaries.zh-CN.md docs/public/authoring/generator/code-generation.zh-CN.md docs/superpowers/capability-matrix.md
git commit -m "docs: remove wrapper from core capability docs"
```

## Self-Review Checklist

- Spec coverage:
  - active core wrapper surfaces are removed in Tasks 2-4
  - compatibility residue is explicitly left out of scope by the Scope Guard
  - remove-vs-deprecate is implemented as true deletion in Tasks 2-3
  - aggregate/schema/template/plan/test/docs contracts are all covered
- Placeholder scan:
  - every task lists exact files
  - every code-edit step contains concrete snippets
  - every verification step includes exact commands and expected outcomes
- Type consistency:
  - no task depends on `Agg*` surviving after Task 3
  - compile fixture is intentionally reduced to `factory/specification` because current generated entity template does not expose a wrapper replacement type

## Expected Follow-Up Policy

If implementation reveals wrapper references outside the live core surfaces above, only do one of these:

1. remove them if they are directly required to make the core removal compile and test clean
2. list them explicitly as follow-up residue if they belong to runtime internals or historical docs

Do not silently expand the scope.
