# Cap4k Generated Source Output And Entity Behavior Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to execute this plan. The implementation must use exploration subagents before code edits, review subagents after implementation, and a second review after fixes. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split pure generated Kotlin artifacts from checked-in user-owned source, move aggregate entities to module-local Gradle generated source output, and add a checked-in aggregate behavior scaffold that preserves a behavior-safe mutability contract.

**Architecture:** Add artifact output ownership to the pipeline plan model, let aggregate planners classify pure derived families as `GENERATED_SOURCE`, write those files under each owning module's `build/generated/cap4k/main/kotlin`, register those roots with the owning subproject's Kotlin `main` source set, and keep behavior/user-owned files under `src/main/kotlin` with skip semantics. Add a pre-compile generated-source task so Kotlin compilation consumes build-owned generated sources without running the full checked-in scaffold generator.

**Tech Stack:** Kotlin, Gradle plugin API, Kotlin Gradle source-set extension via bounded reflection bridge, Gradle TestKit, JUnit 5, Pebble templates, Gson.

---

## Execution Result

Status after implementation:

- implemented artifact output ownership and generated-source export filtering
- implemented module-local `cap4kGenerateSources` integration for Gradle compilation
- moved aggregate entity/schema/repository/enum/enum-translation/unique families to generated source output
- added checked-in aggregate behavior scaffolds with skip semantics
- rewrote aggregate entity template to behavior-safe mutable class shape
- completed required exploration, review, fix, and second-review subagent flow
- verified with targeted API/core/aggregate/renderer tests and Gradle plugin unit, functional, and compile-functional tests

Deferred by design:

- no broad `irAnalysis` restructuring
- no drawing-board PAGE/api_payload round-trip fix
- no real-project integration workaround
- no legacy `cap4k-plugin-codegen` work
- no commit was created by this plan

---

## Freshness Review Against 2026-04-29 `master`

Reviewed before this plan:

- `AGENTS.md`
- `docs/superpowers/mainline-roadmap.md`
- `docs/superpowers/specs/2026-04-28-cap4k-generated-source-output-and-entity-behavior-split-design.md`
- current pipeline API/core/renderer/aggregate/Gradle plugin source
- old plugin entity shape reference in `only-danmuku/only-danmuku-domain/src/main/kotlin/edu/only4/danmuku/domain/aggregates/category/Category.kt`

Spec deltas already applied before writing this plan:

- Replaced ambiguous placement language with built-in output classification and output-root resolution.
- Confirmed users cannot customize generated-source placement in this slice.
- Made aggregate entity files generated-source artifacts in this slice.
- Required old-plugin-style behavior-safe entity shape in this slice.
- Clarified that only fully derived aggregate unique handlers move to generated source; ordinary design query handlers remain checked-in.
- Clarified Gradle source-set integration is module-local and pre-compile, not a root-project generated directory or exporter-root switch.

Current `master` constraints to preserve:

- Do not touch `cap4k-plugin-codegen`.
- Do not move user-owned handlers, ordinary validator bodies, subscribers, controllers, behavior files, or slot-owned files into build-generated source.
- Do not broaden this into `irAnalysis` restructuring.
- Do not fold the known contract-first query / analysis projection gap into this slice.
- Do not claim `drawing_board` fully covers PAGE query or `api_payload` round-trip semantics.
- Do not make generated-source functional or compile tests depend on analysis/drawing_board output.
- Keep `PipelineModels.kt` changes local and additive around `outputKind` / `resolvedOutputRoot`; avoid structural reshaping that would make later `traits` restoration harder.
- Do not add real-project integration workarounds.
- Do not silently delete old checked-in entity files; duplicate classes during migration are a project cleanup issue.
- Use short commands and small patches on Windows.

Implementation calibration after first Gradle TestKit verification:

- Directly adding `libs.kotlin.gradle.plugin` to the pipeline Gradle plugin classpath caused plugin-resolution/classpath interference in functional tests.
- The implementation therefore registers generated Kotlin source directories through the applied subproject's existing `kotlin` extension with a small reflection bridge, instead of adding a hard Kotlin Gradle plugin API dependency to `cap4k-plugin-pipeline-gradle`.
- This remains module-local and pre-compile; it is not a root-project output switch.

## Required Subagent Flow

- [ ] **Step 1: Pre-implementation exploration agents**

Spawn at least two read-only explorer agents before implementation:

```text
Agent A: artifact output model, exporter, Gradle source-set/task graph.
Agent B: aggregate artifact families, entity template shape, behavior scaffold boundary.
```

Record findings in the implementation session before editing. If findings contradict this plan, update the plan first.

- [ ] **Step 2: Main-context integration**

The main context owns final design decisions, code edits, and verification. Do not let subagents edit the same files unless their write scope is explicitly isolated.

- [ ] **Step 3: Post-implementation review agents**

After implementation and first verification, spawn two review agents:

```text
Reviewer A: spec and boundary audit.
Reviewer B: code quality and test coverage audit.
```

Evaluate every finding before fixing. Do not blindly apply review feedback.

- [ ] **Step 4: Second review**

After fixes, run a second review on the changed areas. The slice is not complete until second-round review issues are resolved or explicitly rejected with technical rationale.

---

## Source Specs And Constraints

- Spec: `docs/superpowers/specs/2026-04-28-cap4k-generated-source-output-and-entity-behavior-split-design.md`
- Roadmap: `docs/superpowers/mainline-roadmap.md`

Generated-source families for this slice:

- aggregate entity files
- aggregate schema `S*` classes
- standard aggregate repositories
- generated aggregate/local/shared enums
- enum translation/converter artifacts
- aggregate unique query classes
- aggregate unique query handlers whose bodies are fully derived from aggregate metadata
- aggregate unique validators

Checked-in source families for this slice:

- aggregate behavior scaffold, generated once per aggregate root with skip semantics
- aggregate factory/specification/wrapper unless a later spec reclassifies them
- ordinary design command/query/client/domain-event contracts and handlers
- ordinary design validators
- subscribers, controllers, API adapters, slot-provided files

Gradle task contract:

- `cap4kPlan` reports output kind and resolved output root for all source-pipeline artifacts.
- `cap4kGenerate` exports both checked-in scaffolds and build-generated sources.
- A new pre-compile task, named `cap4kGenerateSources`, exports only `GENERATED_SOURCE` artifacts.
- Affected subproject `compileKotlin` tasks depend on `cap4kGenerateSources`, not on full `cap4kGenerate`.
- Each affected subproject registers its own `build/generated/cap4k/main/kotlin` as a Kotlin `main` source directory.

---

## File Map

Pipeline API and core:

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolverTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/FilesystemArtifactExporter.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/FilteringArtifactExporter.kt`
- Add or modify core exporter tests as needed.

Renderer:

- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRenderer.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/behavior.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

Aggregate planner:

- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactOutputs.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/BehaviorArtifactPlanner.kt`
- Modify aggregate family planners that currently create pure derived artifacts.
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

Gradle plugin:

- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kGenerateSourcesTask.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify functional fixtures only where expected paths or compile behavior changes.

Docs:

- Modify: `cap4k-plugin-pipeline-gradle/README.md`
- Modify: `docs/superpowers/mainline-roadmap.md`

---

### Task 1: Artifact Output Ownership Model

**Files:**

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolverTest.kt`

- [ ] **Step 1: Add failing API tests for output kind and generated roots**

In `PipelineModelsTest`, assert defaults:

```kotlin
val item = ArtifactPlanItem(
    generatorId = "aggregate",
    moduleRole = "domain",
    templateId = "aggregate/entity.kt.peb",
    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/Category.kt",
    conflictPolicy = ConflictPolicy.SKIP,
)

assertEquals(ArtifactOutputKind.CHECKED_IN_SOURCE, item.outputKind)
assertEquals("", item.resolvedOutputRoot)
```

Add a generated-source case:

```kotlin
val item = ArtifactPlanItem(
    generatorId = "aggregate",
    moduleRole = "domain",
    templateId = "aggregate/entity.kt.peb",
    outputPath = "demo-domain/build/generated/cap4k/main/kotlin/com/acme/demo/Category.kt",
    conflictPolicy = ConflictPolicy.OVERWRITE,
    outputKind = ArtifactOutputKind.GENERATED_SOURCE,
    resolvedOutputRoot = "demo-domain/build/generated/cap4k/main/kotlin",
)

assertEquals(ArtifactOutputKind.GENERATED_SOURCE, item.outputKind)
assertEquals("demo-domain/build/generated/cap4k/main/kotlin", item.resolvedOutputRoot)
```

In `ArtifactLayoutResolverTest`, add assertions for:

```text
kotlinSourceRoot("demo-domain") -> demo-domain/src/main/kotlin
generatedKotlinSourceRoot("demo-domain") -> demo-domain/build/generated/cap4k/main/kotlin
generatedKotlinSourcePath("demo-domain", "com.acme.demo", "Category") -> demo-domain/build/generated/cap4k/main/kotlin/com/acme/demo/Category.kt
```

- [ ] **Step 2: Run API tests and verify RED**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest" --tests "com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolverTest" --rerun-tasks
```

Expected: FAIL because `ArtifactOutputKind`, generated-root helpers, and metadata fields do not exist yet.

- [ ] **Step 3: Implement artifact output metadata**

In `PipelineModels.kt`, add:

```kotlin
enum class ArtifactOutputKind {
    CHECKED_IN_SOURCE,
    GENERATED_SOURCE,
    OUTPUT_ARTIFACT,
}
```

Update `ArtifactPlanItem`:

```kotlin
data class ArtifactPlanItem(
    val generatorId: String,
    val moduleRole: String,
    val templateId: String,
    val outputPath: String,
    val context: Map<String, Any?> = emptyMap(),
    val conflictPolicy: ConflictPolicy,
    val outputKind: ArtifactOutputKind = ArtifactOutputKind.CHECKED_IN_SOURCE,
    val resolvedOutputRoot: String = "",
)
```

Update `RenderedArtifact` similarly:

```kotlin
data class RenderedArtifact(
    val outputPath: String,
    val content: String,
    val conflictPolicy: ConflictPolicy,
    val outputKind: ArtifactOutputKind = ArtifactOutputKind.CHECKED_IN_SOURCE,
    val resolvedOutputRoot: String = "",
)
```

In `ArtifactLayoutResolver.kt`, keep `kotlinSourcePath()` and add:

```kotlin
fun kotlinSourceRoot(moduleRoot: String): String =
    joinPath(moduleRoot, "src/main/kotlin")

fun generatedKotlinSourceRoot(moduleRoot: String): String =
    joinPath(moduleRoot, "build/generated/cap4k/main/kotlin")

fun generatedKotlinSourcePath(moduleRoot: String, packageName: String, typeName: String): String =
    joinPath(generatedKotlinSourceRoot(moduleRoot), packageName.replace('.', '/'), "$typeName.kt")
```

- [ ] **Step 4: Run API tests and verify GREEN**

Run the same API test command. Expected: PASS.

---

### Task 2: Renderer And Exporter Output Semantics

**Files:**

- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRenderer.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/FilesystemArtifactExporter.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/FilteringArtifactExporter.kt`
- Add or modify exporter tests in `cap4k-plugin-pipeline-core/src/test/kotlin/...`

- [ ] **Step 1: Add failing renderer metadata preservation test**

In `PebbleArtifactRendererTest`, add or update a small renderer test that renders a minimal existing template with:

```kotlin
outputKind = ArtifactOutputKind.GENERATED_SOURCE
resolvedOutputRoot = "demo-domain/build/generated/cap4k/main/kotlin"
```

Assert the returned `RenderedArtifact` preserves both values.

- [ ] **Step 2: Add failing exporter tests**

Add tests for:

```text
GENERATED_SOURCE overwrites an existing file even if artifact conflictPolicy is SKIP.
CHECKED_IN_SOURCE with SKIP leaves existing file content unchanged.
FilteringArtifactExporter delegates only included artifacts.
```

Keep the path traversal validation behavior unchanged.

- [ ] **Step 3: Run renderer/core targeted tests and verify RED**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --rerun-tasks
.\gradlew.bat :cap4k-plugin-pipeline-core:test --rerun-tasks
```

Expected: FAIL before implementation.

- [ ] **Step 4: Preserve output metadata in renderer**

In `PebbleArtifactRenderer`, construct `RenderedArtifact` with:

```kotlin
outputKind = item.outputKind,
resolvedOutputRoot = item.resolvedOutputRoot,
```

- [ ] **Step 5: Implement generated-source overwrite semantics**

In `FilesystemArtifactExporter`, apply an effective policy:

```kotlin
private fun effectiveConflictPolicy(artifact: RenderedArtifact): ConflictPolicy =
    when (artifact.outputKind) {
        ArtifactOutputKind.GENERATED_SOURCE -> ConflictPolicy.OVERWRITE
        else -> artifact.conflictPolicy
    }
```

Use the effective policy for existing-file decisions. Do not change validation that constrains writes under the exporter root.

- [ ] **Step 6: Add filtering exporter**

Create:

```kotlin
class FilteringArtifactExporter(
    private val delegate: ArtifactExporter,
    private val include: (RenderedArtifact) -> Boolean,
) : ArtifactExporter {
    override fun export(artifacts: List<RenderedArtifact>): List<String> =
        delegate.export(artifacts.filter(include))
}
```

Use this only for task-level filtering. Do not put output classification in the exporter root.

- [ ] **Step 7: Run renderer/core targeted tests and verify GREEN**

Run the same commands. Expected: PASS.

---

### Task 3: Aggregate Generated-Source Classification And Behavior Planning

**Files:**

- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactOutputs.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/BehaviorArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Modify aggregate planner files for entity/schema/repository/enums/enum translation/unique family.
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

- [ ] **Step 1: Add failing aggregate planner tests**

Add assertions that these templates produce `GENERATED_SOURCE`, `ConflictPolicy.OVERWRITE`, and paths under `build/generated/cap4k/main/kotlin`:

```text
aggregate/entity.kt.peb
aggregate/schema.kt.peb
aggregate/repository.kt.peb
aggregate/enum.kt.peb from shared enum planning
aggregate/enum.kt.peb from local enum planning
aggregate/enum_translation.kt.peb
aggregate/unique-query.kt.peb
aggregate/unique-query-handler.kt.peb
aggregate/unique-validator.kt.peb
```

For each item, assert `resolvedOutputRoot` is the owning module's generated Kotlin source root.

Add assertions that factory/specification/wrapper remain checked-in source for this slice.

Add behavior scaffold assertions:

```text
one aggregate/behavior.kt.peb item per aggregate root
same package as the aggregate root
outputPath under src/main/kotlin
outputKind CHECKED_IN_SOURCE
conflictPolicy SKIP
global template conflict policy does not change behavior scaffold SKIP
no behavior file for non-root child entities
```

- [ ] **Step 2: Run aggregate planner tests and verify RED**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --rerun-tasks
```

Expected: FAIL before output classification and behavior planner implementation.

- [ ] **Step 3: Add aggregate output helper functions**

Create `AggregateArtifactOutputs.kt` with internal helpers so planners do not duplicate output-root logic:

```kotlin
internal fun generatedKotlinArtifact(
    config: ProjectConfig,
    moduleRole: String,
    packageName: String,
    typeName: String,
    templateId: String,
    context: Map<String, Any?>,
): ArtifactPlanItem
```

The helper must:

```text
resolve the role module path through requireRelativeModule(config, moduleRole)
use ArtifactLayoutResolver.generatedKotlinSourceRoot/path
set ArtifactOutputKind.GENERATED_SOURCE
set ConflictPolicy.OVERWRITE
set resolvedOutputRoot
```

Add a checked-in helper for behavior and remaining scaffold/source families:

```kotlin
internal fun checkedInKotlinArtifact(..., conflictPolicy: ConflictPolicy = config.templates.conflictPolicy): ArtifactPlanItem
```

The checked-in helper must set `ArtifactOutputKind.CHECKED_IN_SOURCE` and `resolvedOutputRoot` to `kotlinSourceRoot(moduleRoot)`.

- [ ] **Step 4: Classify aggregate pure-derived families**

Update these planners to use `generatedKotlinArtifact`:

```text
EntityArtifactPlanner
SchemaArtifactPlanner
RepositoryArtifactPlanner
SharedEnumArtifactPlanner
LocalEnumArtifactPlanner
EnumTranslationArtifactPlanner
UniqueQueryArtifactPlanner
UniqueQueryHandlerArtifactPlanner
UniqueValidatorArtifactPlanner
```

Keep these families checked-in in this slice:

```text
FactoryArtifactPlanner
SpecificationArtifactPlanner
WrapperArtifactPlanner
ordinary design generator planners
```

- [ ] **Step 5: Add behavior scaffold planner**

Create `BehaviorArtifactPlanner`.

Planning rules:

```text
iterate aggregate root entities only
templateId = aggregate/behavior.kt.peb
typeName = <RootName>Behavior
moduleRole = domain
packageName = root entity package
outputKind = CHECKED_IN_SOURCE
conflictPolicy = SKIP
context includes rootName and owned entity names if already available without model restructuring
```

Register it in `AggregateArtifactPlanner` after entity planning and before user-facing support families. Do not add behavior items for child entities.

- [ ] **Step 6: Run aggregate planner tests and verify GREEN**

Run the same aggregate planner test command. Expected: PASS.

---

### Task 4: Entity And Behavior Templates

**Files:**

- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/behavior.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Add failing entity behavior-safe template tests**

Use existing aggregate entity renderer test fixtures where possible. Add assertions for a scalar-only entity and a relation-owning aggregate:

```text
entity renders "class Category(" and never "data class Category("
primary constructor params do not include val or var
scalar JPA annotations render on body properties
scalar persisted fields render as "var name: String = name" with "internal set"
nullable scalar fields preserve nullable type and default value where the model provides one
owned one-to-many collections render as MutableList<T> initialized with mutableListOf()
generated entity contains no business method body and no managed-section marker
relation annotations remain present
provider-specific annotations remain present
custom id generator annotations remain present
```

Add behavior template test:

```text
aggregate/behavior.kt.peb renders the package declaration
file name is <RootName>Behavior.kt through the planner
template contains no generated business method
template contains no managed-section marker
```

- [ ] **Step 2: Run renderer tests and verify RED**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --rerun-tasks
```

Expected: FAIL before template changes.

- [ ] **Step 3: Rewrite entity template to old-plugin-style generated entity shape**

Change `aggregate/entity.kt.peb` so entities always render as regular classes:

```kotlin
class Category(
    name: String,
    parentId: Long? = null,
) : AggregateRoot<Long>() {
    var name: String = name
        internal set

    var parentId: Long? = parentId
        internal set

    val children: MutableList<Category> = mutableListOf()
}
```

Template rules:

```text
constructor parameters are initialization inputs only
do not put val/var on constructor parameters
body scalar properties carry the existing persistence annotations
body scalar properties use var plus internal set
owned collection properties use MutableList<T> and mutableListOf()
relation properties that are mutable aggregate state use bounded setters compatible with behavior files
annotations/imports already generated from canonical metadata must not be dropped
```

Do not add business methods or managed sections.

- [ ] **Step 4: Add behavior scaffold template**

Create a minimal `aggregate/behavior.kt.peb`:

```peb
package {{ packageName }}

/**
 * Place behavior for {{ rootName }} and its owned entities here.
 */
```

Keep it checked-in and user-owned. The generator must skip existing files; it must not merge later edits.

- [ ] **Step 5: Run renderer tests and verify GREEN**

Run the same renderer command. Expected: PASS.

---

### Task 5: Gradle Generated Kotlin Source-Set Integration

**Files:**

- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kGenerateSourcesTask.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt`

- [ ] **Step 1: Add failing Gradle plugin unit tests**

Add or update tests for:

```text
generated source module roles for aggregate include domain and adapter
generated source module roles include application only when aggregate unique output is enabled
generated source roots are module-local: <module>/build/generated/cap4k/main/kotlin
compileKotlin depends on cap4kGenerateSources for affected subprojects
compileKotlin does not depend on full cap4kGenerate
source-set registration skips missing modules rather than failing configuration
```

If direct source-set assertions are difficult in unit tests, keep path and task wiring assertions in unit tests and cover real source-set behavior in functional tests.

- [ ] **Step 2: Run Gradle plugin unit tests and verify RED**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginTest" --rerun-tasks
```

Expected: FAIL before the new task/source-set integration exists.

- [ ] **Step 3: Register Kotlin source sets without plugin classpath pollution**

Attempt direct Kotlin Gradle plugin API usage only if it does not pollute the pipeline plugin classpath. If functional tests show plugin-resolution/classpath interference, keep `cap4k-plugin-pipeline-gradle` free of a hard `libs.kotlin.gradle.plugin` dependency and configure the applied subproject's existing `kotlin` extension through a bounded reflection bridge.

- [ ] **Step 4: Add `Cap4kGenerateSourcesTask`**

Create a task that runs the source pipeline with a generated-source-only exporter:

```kotlin
abstract class Cap4kGenerateSourcesTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Cap4kExtension

    @get:Internal
    lateinit var configFactory: Cap4kProjectConfigFactory

    @TaskAction
    fun generateSources() {
        val config = sourceTaskConfig(configFactory.build(project, extension))
        buildGeneratedSourceRunner(project, config).run(config)
    }
}
```

Implement `buildGeneratedSourceRunner()` by reusing the source runner and wrapping `FilesystemArtifactExporter` with `FilteringArtifactExporter { it.outputKind == ArtifactOutputKind.GENERATED_SOURCE }`.

Do not make this task export checked-in behavior scaffolds.

- [ ] **Step 5: Register generated source roots per owning subproject**

Add helper functions in `PipelinePlugin.kt`:

```text
generatedSourceModuleRoles(config): Set<String>
registerGeneratedKotlinSourceSets(rootProject, config)
wireGeneratedSourceCompilation(rootProject, config, generateSourcesTaskProvider)
```

Rules:

```text
domain role gets aggregate entity/schema/enum generated source roots
adapter role gets repository/enum translation generated source roots
application role gets aggregate unique query/validator generated source roots when unique generation is enabled
ordinary design generators do not add generated source roots in this slice
missing modules are skipped
each resolved Gradle subproject registers its own build/generated/cap4k/main/kotlin
```

Use:

```kotlin
domainProject.plugins.withId("org.jetbrains.kotlin.jvm") {
    domainProject.extensions.configure(KotlinJvmProjectExtension::class.java) {
        sourceSets.named("main") {
            kotlin.srcDir(domainProject.layout.buildDirectory.dir("generated/cap4k/main/kotlin"))
        }
    }
}
```

Wire affected subproject Kotlin compile tasks:

```text
tasks named compileKotlin depend on root cap4kGenerateSources
do not wire compileKotlin to cap4kGenerate
do not wire analysis tasks into this dependency path
```

- [ ] **Step 6: Keep dependency inference cycle-free**

Update `projectsEvaluated` so:

```text
cap4kPlan and cap4kGenerate keep existing source dependency inference
cap4kGenerateSources uses the same source prerequisites as source generation
analysis plan/generate keep compileKotlin analysis dependency inference
compileKotlin -> cap4kGenerateSources must not create compileKotlin -> analysis -> compileKotlin cycles
```

Do not add `ir-analysis` sources or drawing-board/flow generators to `cap4kGenerateSources`.

- [ ] **Step 7: Run Gradle plugin unit tests and verify GREEN**

Run the same `PipelinePluginTest` command. Expected: PASS.

---

### Task 6: Functional And Compile Coverage Migration

**Files:**

- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify functional fixture expected assertions as needed.

- [ ] **Step 1: Add failing functional coverage for plan output metadata**

In the aggregate functional plan coverage, assert `build/cap4k/plan.json` includes:

```json
"outputKind": "GENERATED_SOURCE"
"resolvedOutputRoot": "demo-domain/build/generated/cap4k/main/kotlin"
```

Assert behavior scaffold has:

```json
"templateId": "aggregate/behavior.kt.peb"
"outputKind": "CHECKED_IN_SOURCE"
"conflictPolicy": "SKIP"
```

- [ ] **Step 2: Add failing generated-source export coverage**

Add a functional test that runs:

```powershell
cap4kGenerateSources
```

Assert:

```text
entity/schema/repository/enum/translation/unique artifacts are under <module>/build/generated/cap4k/main/kotlin
no behavior scaffold is created by cap4kGenerateSources
rerunning cap4kGenerateSources overwrites a generated file
```

Then run:

```powershell
cap4kGenerate
```

Assert:

```text
behavior scaffold is created under src/main/kotlin
rerunning cap4kGenerate skips an edited behavior scaffold
```

- [ ] **Step 3: Update compile-functional tests for pre-compile generation**

Adjust tests that currently expect `:demo-domain:compileKotlin` to fail before `cap4kGenerate` when the missing files are now generated-source families.

New expectation:

```text
:demo-domain:compileKotlin triggers cap4kGenerateSources
generated aggregate classes are visible from build/generated/cap4k/main/kotlin
compile succeeds without checked-in duplicate entity/schema/repository files
```

Keep failure-before-generate tests only for families that still require checked-in scaffolds or explicit `cap4kGenerate`.

- [ ] **Step 4: Add behavior-safe compile smoke**

Add or update a fixture so checked-in source can compile aggregate behavior against generated entities:

```kotlin
fun Category.rename(name: String) {
    this.name = name
}

fun Category.addChild(child: Category) {
    children.add(child)
}
```

Run full generation before this compile smoke when the behavior file is scaffolded by `cap4kGenerate`. The test should prove:

```text
internal setters are accessible from same-module behavior source
MutableList owned collections are mutable from behavior source
generated entity source is consumed from build/generated/cap4k/main/kotlin
```

- [ ] **Step 5: Run focused functional tests and verify RED/GREEN**

First run the tests after adding assertions and before implementation if task ordering allows. Expected: FAIL.

After implementation, run targeted functional classes separately:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --rerun-tasks
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest" --rerun-tasks
```

Expected after implementation: PASS. Use long shell timeouts for Gradle TestKit runs.

---

### Task 7: Documentation Updates

**Files:**

- Modify: `cap4k-plugin-pipeline-gradle/README.md`
- Modify: `docs/superpowers/mainline-roadmap.md`

- [ ] **Step 1: Update plugin README**

Document:

```text
cap4kPlan reports outputKind and resolvedOutputRoot.
cap4kGenerate writes checked-in scaffolds and build-generated source.
cap4kGenerateSources writes only GENERATED_SOURCE artifacts for Gradle compilation.
Aggregate behavior scaffold is generated once under src/main/kotlin and then user-owned.
Aggregate entity/schema/repository/enums/enum translation/unique families are build-generated in this slice.
Ordinary design handlers, validators, subscribers, controllers, and behavior files stay checked-in.
```

- [ ] **Step 2: Update roadmap status**

In `docs/superpowers/mainline-roadmap.md`, update this slice from "implementation plan not written" to:

```text
- implementation plan written
- implementation complete
- verified through targeted API/core/renderer/aggregate/Gradle plugin tests
```

Only mark implementation complete after all verification and review gates pass. Keep `irAnalysis restructuring analysis` and repository backend work deferred.

---

### Task 8: Required Review Round One

**Files:**

- No direct source files unless fixes are required.

- [ ] **Step 1: Spawn spec and boundary reviewer**

Ask a read-only reviewer to audit:

```text
Does any generated-source classification violate the spec?
Did user-owned handlers, ordinary validators, subscribers, controllers, behavior files, or slots move to build output?
Did the implementation become an exporter-root switch instead of artifact ownership classification?
Did Gradle integration use module-local generated roots?
Did entity mutability change match the approved behavior-safe contract only?
Did any broad irAnalysis or legacy cap4k-plugin-codegen work leak in?
```

- [ ] **Step 2: Spawn code quality reviewer**

Ask a read-only reviewer to audit:

```text
task graph cycles or hidden compile dependencies
Gradle source-set registration timing
path resolution and Windows path handling
overwrite/skip behavior correctness
template regressions in annotations/imports
test coverage gaps for generated-source output and behavior scaffold skip
```

- [ ] **Step 3: Evaluate review findings**

For every finding:

```text
accept and fix
reject with a concrete technical reason
defer only if outside this spec and documented
```

Use `superpowers:receiving-code-review` before applying review feedback.

- [ ] **Step 4: Fix accepted findings**

Implement only accepted fixes. Re-run the narrow tests for touched areas.

---

### Task 9: Second Review And Final Verification

**Files:**

- No direct source files unless fixes are required.

- [ ] **Step 1: Run second review**

Ask review agents to re-check the files changed after round one. The second review can be narrower but must cover any accepted fixes.

- [ ] **Step 2: Run final targeted verification**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test --rerun-tasks
.\gradlew.bat :cap4k-plugin-pipeline-core:test --rerun-tasks
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --rerun-tasks
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --rerun-tasks
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginTest" --rerun-tasks
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --rerun-tasks
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest" --rerun-tasks
```

Use separate commands to avoid Windows command length limits and Gradle TestKit timeouts.

- [ ] **Step 3: Run boundary scans**

Run:

```powershell
rg -n "cap4k-plugin-codegen" --glob "!docs/superpowers/**" --glob "!cap4k-plugin-codegen/**"
rg -n "src/main/kotlin/.+Behavior|behavior.kt.peb|GENERATED_SOURCE|CHECKED_IN_SOURCE" cap4k-plugin-pipeline-* docs/superpowers
rg -n "outputKind|resolvedOutputRoot|build/generated/cap4k/main/kotlin" cap4k-plugin-pipeline-*
git diff --check
git status --short
```

Expected:

```text
no accidental legacy plugin edits
behavior remains checked-in
generated-source paths are module-local
no whitespace errors
status shows only intentional slice files
```

- [ ] **Step 4: Final diff review**

Run:

```powershell
git diff -- docs/superpowers/specs/2026-04-28-cap4k-generated-source-output-and-entity-behavior-split-design.md docs/superpowers/plans/2026-04-29-cap4k-generated-source-output-and-entity-behavior-split.md cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-gradle docs/superpowers/mainline-roadmap.md
```

Confirm the diff is limited to this slice.

- [ ] **Step 5: Commit after successful verification**

If committing in this session, use a single slice commit after review and verification:

```powershell
git add docs/superpowers/specs/2026-04-28-cap4k-generated-source-output-and-entity-behavior-split-design.md
git add docs/superpowers/plans/2026-04-29-cap4k-generated-source-output-and-entity-behavior-split.md
git add docs/superpowers/mainline-roadmap.md
git add cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-gradle
git commit -m "feat: split generated source output and entity behavior"
```
