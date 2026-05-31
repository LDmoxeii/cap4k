# cap4k Issue 92 Artifact Metadata Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement #92 by replacing variant-style public generator metadata with explicit artifact selection, adding stable cap4k concept annotations, and making analysis/drawing-board recovery emit valid generator input.

**Architecture:** Start at the API/config model, then move source parsing and canonical assembly to a unified design block contract. Update planners/templates to consume artifact selections and emit `@BuildingBlock` / `@AggregateElement`, then migrate code-analysis, drawing-board, flow, Gradle wiring, and removal of old `@Aggregate` / `archinfo`.

**Tech Stack:** Kotlin, Gradle multi-module build, Gson, Kotlin compiler IR plugin, Pebble templates, JUnit/Kotlin test suites, GitHub issue #92 spec `docs/superpowers/specs/2026-05-29-cap4k-issue-92-artifact-metadata-recovery-design.md`.

---

### Task 1: Public API Model And Config Contract

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfigTest.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`

- [ ] **Step 1: Add API tests for enabled-free config and artifact selections**

In `ProjectConfigTest.kt`, add tests that construct `SourceConfig(options = mapOf("files" to listOf("design/design.json")))` and `GeneratorConfig(options = mapOf("artifact.specification" to false))`. Assert there is no `enabled` argument in the constructor call and no `enabledSourceIds()` / `enabledGeneratorIds()` usage in tests.

In `PipelineModelsTest.kt`, add tests for a `DesignBlockModel` with two artifact selections:

```kotlin
@Test
fun `design block stores artifact selections`() {
    val block = DesignBlockModel(
        tag = "query",
        packageName = "order.read",
        name = "FindOrderPage",
        description = "Find order page",
        aggregates = listOf("Order"),
        artifacts = listOf(
            ArtifactSelectionModel(family = "query", variant = "page"),
            ArtifactSelectionModel(family = "query-handler"),
        ),
        fields = listOf(FieldModel(name = "keyword", type = "String", nullable = true)),
        resultFields = listOf(FieldModel(name = "orderNo", type = "String")),
    )

    assertEquals("query", block.tag)
    assertEquals(listOf("Order"), block.aggregates)
    assertEquals("page", block.artifacts.first().variant)
    assertEquals("query-handler", block.artifacts.last().family)
    assertEquals("keyword", block.fields.single().name)
    assertEquals("orderNo", block.resultFields.single().name)
}
```

- [ ] **Step 2: Run API tests and verify they fail**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.ProjectConfigTest" --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest"
```

Expected: compile failure because `SourceConfig` / `GeneratorConfig` still require `enabled`, and `DesignBlockModel` / `ArtifactSelectionModel` do not exist.

- [ ] **Step 3: Update `ProjectConfig.kt`**

Change the config models to:

```kotlin
data class SourceConfig(
    val options: Map<String, Any?> = emptyMap(),
)

data class GeneratorConfig(
    val options: Map<String, Any?> = emptyMap(),
)
```

Remove these methods from `ProjectConfig`:

```kotlin
fun enabledSourceIds(): Set<String> = sources.filterValues { it.enabled }.keys
fun enabledGeneratorIds(): Set<String> = generators.filterValues { it.enabled }.keys
```

Keep `typeRegistryFqns()` unchanged.

- [ ] **Step 4: Update `PipelineModels.kt` with design block types**

Add near the current design entry models:

```kotlin
data class ArtifactSelectionModel(
    val family: String,
    val variant: String = "",
)

data class DesignBlockModel(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String = "",
    val aggregates: List<String> = emptyList(),
    val eventName: String = "",
    val persist: Boolean? = null,
    val artifacts: List<ArtifactSelectionModel>,
    val fields: List<FieldModel> = emptyList(),
    val resultFields: List<FieldModel> = emptyList(),
)
```

Add `val designBlocks: List<DesignBlockModel> = emptyList()` to `CanonicalModel`. Keep the old typed design lists temporarily so this task stays compile-focused; later tasks remove or stop using them.

- [ ] **Step 5: Run API tests and update call sites in tests**

Run the same API test command. Fix compile errors in API tests by removing `enabled = true/false` arguments. Do not chase cross-module compile errors in this task unless they are in `cap4k-plugin-pipeline-api`.

- [ ] **Step 6: Commit API model changes**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt `
        cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt `
        cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfigTest.kt `
        cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt
git commit -m "feat: add issue 92 artifact selection API model"
```

### Task 2: Design-Json Parsing And Canonical Design Blocks

**Files:**
- Modify: `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Add failing source tests for new JSON names and removed fields**

In `DesignJsonSourceProviderTest.kt`, add a test that writes this array to a temp file and collects the source:

```json
[
  {
    "tag": "query",
    "package": "order.read",
    "name": "FindOrderPage",
    "description": "Find order page",
    "aggregates": ["Order"],
    "artifacts": [
      { "family": "query", "variant": "page" },
      { "family": "query-handler" }
    ],
    "fields": [{ "name": "keyword", "type": "String", "nullable": true }],
    "resultFields": [{ "name": "orderNo", "type": "String" }]
  }
]
```

Assert the collected snapshot contains a design entry or design block with `description`, `fields`, `resultFields`, and two artifact selections.

Add a second test that includes old fields:

```json
[{ "tag": "query", "name": "FindOrder", "desc": "old", "requestFields": [], "responseFields": [], "traits": ["page"] }]
```

Expected error message should mention removed public fields, for example `design entry FindOrder uses removed fields: desc, requestFields, responseFields, traits`.

- [ ] **Step 2: Add failing canonical tests for default expansion and validation**

In `DefaultCanonicalAssemblerTest.kt`, add tests that assemble `DesignSpecEntry` / source snapshot equivalents for:

- `tag = "query"` without artifacts: expects `DesignBlockModel.artifacts == [query, query-handler]`.
- `tag = "domain_event"` without exactly one aggregate: expects failure.
- `tag = "integration_event"` with explicit `integration-subscriber` and `integration-event/outbound`: expects failure.
- `tag = "integration_event"` without artifacts: expects `integration-event/outbound`.
- explicit `query/page` without `query-handler`: expects only `query/page`.

Use exact assertions against `model.designBlocks`.

- [ ] **Step 3: Run focused tests and verify they fail**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-source-design-json:test --tests "com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonSourceProviderTest" `
  :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: failures because source still parses `desc/requestFields/responseFields/traits/role`, and assembler does not populate `designBlocks`.

- [ ] **Step 4: Implement artifact parsing in `DesignJsonSourceProvider`**

Update parsing rules:

- reject object keys `role`, `traits`, `scope`, `desc`, `requestFields`, `responseFields`;
- read `description` as string default `""`;
- read `fields` into `FieldModel` list;
- read `resultFields` into `FieldModel` list;
- read `artifacts` as a list of `{ family: String, variant: String? }`;
- keep `package` as authoring package input mapped to internal `packageName`;
- keep `persist` only as raw domain-event input.

If keeping `DesignSpecEntry` as the source snapshot type temporarily, add `artifacts`, `fields`, and `resultFields` to it and stop filling old `traits`, `role`, `requestFields`, and `responseFields`.

- [ ] **Step 5: Implement default expansion and validation in assembler**

Add helpers in `DefaultCanonicalAssembler.kt`:

```kotlin
private fun defaultArtifactsFor(tag: String): List<ArtifactSelectionModel> = when (tag) {
    "command" -> listOf(ArtifactSelectionModel("command"))
    "query" -> listOf(ArtifactSelectionModel("query"), ArtifactSelectionModel("query-handler"))
    "client" -> listOf(ArtifactSelectionModel("client"), ArtifactSelectionModel("client-handler"))
    "api_payload" -> listOf(ArtifactSelectionModel("api-payload"))
    "domain_event" -> listOf(ArtifactSelectionModel("domain-event"), ArtifactSelectionModel("domain-subscriber"))
    "integration_event" -> listOf(ArtifactSelectionModel("integration-event", "outbound"))
    "domain_service" -> listOf(ArtifactSelectionModel("domain-service"))
    "saga" -> listOf(ArtifactSelectionModel("saga"))
    else -> error("Unsupported design tag: $tag")
}
```

Add validation for supported families and variants exactly as the spec states. Populate `CanonicalModel.designBlocks` from design source entries.

- [ ] **Step 6: Run focused tests until green**

Run the command from Step 3. Expected: source and canonical tests pass.

- [ ] **Step 7: Commit design-json and canonical changes**

```powershell
git add cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt `
        cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt `
        cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt `
        cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: parse design blocks with artifact selections"
```

### Task 3: Enum And Value-Object Manifest Ownership Style

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/CanonicalEnumCatalog.kt`
- Modify: `cap4k-plugin-pipeline-source-enum-manifest/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/enummanifest/EnumManifestSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-source-value-object-manifest/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/valueobject/ValueObjectManifestSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateJpaControlInference.kt`
- Modify tests for enum manifest, value-object manifest, API models, and core inference.

- [ ] **Step 1: Add failing manifest tests for `aggregates`**

In `EnumManifestSourceProviderTest.kt`, add cases:

```json
[
  { "name": "SharedStatus", "package": "shared.enums", "items": [{ "name": "ACTIVE" }], "aggregates": [] },
  { "name": "OrderStatus", "package": "order", "items": [{ "name": "PAID" }], "aggregates": ["Order"] }
]
```

Assert shared enum has `aggregates == emptyList()` and owned enum has `aggregates == listOf("Order")`.

Add a failure case with `aggregates: ["Order", "Payment"]` and assert `enum OrderStatus may declare at most one aggregate`.

In `ValueObjectManifestSourceProviderTest.kt`, add equivalent cases for value objects and a failure case for old fields:

```json
[{ "name": "Money", "scope": "shared", "aggregate": "Order" }]
```

Expected: rejected because `scope` and single `aggregate` are removed.

- [ ] **Step 2: Run source manifest tests and verify they fail**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-source-enum-manifest:test --tests "com.only4.cap4k.plugin.pipeline.source.enummanifest.EnumManifestSourceProviderTest" `
  :cap4k-plugin-pipeline-source-value-object-manifest:test --tests "com.only4.cap4k.plugin.pipeline.source.valueobject.ValueObjectManifestSourceProviderTest"
```

Expected: failures because manifest sources do not yet use `aggregates` consistently.

- [ ] **Step 3: Replace value-object scope model**

In `PipelineModels.kt`, remove `ValueObjectScope` from the public value-object model. Change `ValueObjectModel` ownership fields to:

```kotlin
val aggregates: List<String> = emptyList()
```

Add helper properties only if needed internally:

```kotlin
val ValueObjectModel.ownerAggregate: String?
    get() = aggregates.singleOrNull()
```

Do not keep public `scope` or single `aggregate` fields.

- [ ] **Step 4: Update enum and value-object source providers**

Implement `aggregates` parsing:

- omitted `aggregates` means empty list;
- empty list means shared;
- one item means aggregate-owned;
- more than one item fails;
- old `scope` and single `aggregate` fail for value-object manifest.

- [ ] **Step 5: Update dependent helpers**

Update `CanonicalEnumCatalog.kt` and `AggregateJpaControlInference.kt` to use `aggregates.isEmpty()` for shared and `aggregates.singleOrNull() == ownerAggregateName` for owned.

Search and update compile references:

```powershell
rg -n "ValueObjectScope|\.scope|\.aggregate" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-types cap4k-plugin-pipeline-source-value-object-manifest
```

Only remove references that belong to value-object manifest ownership. Do not touch unrelated aggregate fields.

- [ ] **Step 6: Run source/core tests until green**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-source-enum-manifest:test `
  :cap4k-plugin-pipeline-source-value-object-manifest:test `
  :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.CanonicalEnumCatalogTest" `
  :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

- [ ] **Step 7: Commit manifest ownership migration**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt `
        cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/CanonicalEnumCatalog.kt `
        cap4k-plugin-pipeline-source-enum-manifest/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/enummanifest/EnumManifestSourceProvider.kt `
        cap4k-plugin-pipeline-source-value-object-manifest/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/valueobject/ValueObjectManifestSourceProvider.kt `
        cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateJpaControlInference.kt `
        cap4k-plugin-pipeline-source-enum-manifest/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/enummanifest/EnumManifestSourceProviderTest.kt `
        cap4k-plugin-pipeline-source-value-object-manifest/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/valueobject/ValueObjectManifestSourceProviderTest.kt
git commit -m "feat: use aggregates in type manifests"
```

### Task 4: Enabled-Free Runner And Gradle Config Wiring

**Files:**
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunner.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify Gradle plugin tests.

- [ ] **Step 1: Add runner tests for config-key participation**

In `DefaultPipelineRunnerTest.kt`, add tests that prove:

- a source provider is collected when `config.sources` contains its id, with `SourceConfig(options = emptyMap())`;
- a source provider is not collected when `config.sources` does not contain its id;
- a model-driven built-in planner can run without `config.generators[planner.id]`;
- an unknown generator config key fails with `configured generators have no registered providers: missing-generator`;
- addon provider validation still works.

Use small fake providers already present in the test file. Expected built-in behavior after implementation: `generators.flatMap { it.plan(config, model) }`, with each planner responsible for returning empty plan when its inputs are absent, except config-key-only groups handled by their planner or a runner policy helper.

- [ ] **Step 2: Add Gradle config tests for removed enabled flags**

In `Cap4kProjectConfigFactoryTest.kt`, update tests so:

- design-json source is configured by files/default presence, not `sources.designJson.enabled`;
- enum/value-object manifest config presence creates source config without `enabled`;
- `irAnalysis.inputDirs` creates `ir-analysis` source and causes flow/drawing-board availability without generator enabled flags;
- aggregate internal options still map to `GeneratorConfig(options = mapOf(...))`;
- aggregate projection config key appears only when `generators.aggregateProjection` is explicitly configured.

- [ ] **Step 3: Run focused tests and verify failures**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultPipelineRunnerTest" `
  :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest"
```

Expected: compile failures and assertion failures around `.enabled`.

- [ ] **Step 4: Update `DefaultPipelineRunner`**

Implement source collection by config-key presence:

```kotlin
val configuredSourceIds = config.sources.keys
val snapshots = sources
    .filter { it.id in configuredSourceIds }
    .map { it.collect(config) }
```

Validate unknown configured generators by `config.generators.keys - installedGeneratorIds`.

Run built-in planners in a way that supports model-driven design/type/observation planners. A pragmatic first implementation is:

```kotlin
val configuredGeneratorIds = config.generators.keys
val builtInPlanItems = generators
    .filter { provider -> provider.id !in configKeyRequiredGeneratorIds || provider.id in configuredGeneratorIds }
    .flatMap { it.plan(config, model) }
    .map { ProvenancedPlanItem(it) }
```

Define `configKeyRequiredGeneratorIds` in the runner or pipeline API as:

```kotlin
private val configKeyRequiredGeneratorIds = setOf("aggregate", "aggregate-projection")
```

Do not include design family, enum/value-object, drawing-board, or flow in this required set.

- [ ] **Step 5: Update Gradle extension/config factory**

Remove source `enabled` properties for `designJson`, `enumManifest`, `valueObjectManifest`, and `irAnalysis` where the spec says input presence drives participation. Keep DB source enabled only if the current DSL still needs an explicit DB connection gate; if kept, map it to source key presence rather than `SourceConfig.enabled`.

Remove `generators.flow.enabled` and `generators.drawingBoard.enabled`. Keep aggregate internal artifact options:

```kotlin
"aggregate" to GeneratorConfig(
    options = mapOf(
        "unsupportedTablePolicy" to ...,
        "artifact.factory" to aggregate.artifacts.factory.get(),
        "artifact.specification" to aggregate.artifacts.specification.get(),
        "artifact.unique" to aggregate.artifacts.unique.get(),
    )
)
```

Update config construction so `GeneratorConfig` has no `enabled` argument.

- [ ] **Step 6: Update PipelinePlugin dependency inference**

Replace `config.enabledSourceIds()` / `config.enabledGeneratorIds()` with config-key and input-based checks:

```kotlin
val sourceIds = config.sources.keys
val generatorIds = config.generators.keys
```

For analysis compile dependency, use:

```kotlin
val shouldDependOnCompileKotlin = "ir-analysis" in sourceIds
```

For generated source module roles, include design/type outputs based on canonical planning or source config where the plugin currently needs static Gradle source roots. Keep aggregate/projection roles based on config keys.

- [ ] **Step 7: Run focused tests until green**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultPipelineRunnerTest" `
  :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest" `
  :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginTest"
```

- [ ] **Step 8: Commit runner and Gradle config changes**

```powershell
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunner.kt `
        cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt `
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt `
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt `
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt
git commit -m "feat: use input driven pipeline configuration"
```

### Task 5: Core Concept Annotations And Old Metadata Removal

**Files:**
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/annotation/BuildingBlock.kt`
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/annotation/AggregateElement.kt`
- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/annotation/Aggregate.kt`
- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/archinfo/**`
- Delete: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/archinfo/**`
- Delete: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/archinfo/**`
- Modify: `cap4k-ddd-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: templates under `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/**`
- Modify renderer and functional tests that assert `@Aggregate`.

- [ ] **Step 1: Add ddd-core annotation tests**

Add `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/annotation/ConceptAnnotationTest.kt`:

```kotlin
package com.only4.cap4k.ddd.core.annotation

import kotlin.test.Test
import kotlin.test.assertEquals

class ConceptAnnotationTest {
    @Test
    fun `building block retention is binary class target`() {
        val retention = BuildingBlock::class.annotations.filterIsInstance<Retention>().single()
        val target = BuildingBlock::class.annotations.filterIsInstance<Target>().single()
        assertEquals(AnnotationRetention.BINARY, retention.value)
        assertEquals(setOf(AnnotationTarget.CLASS), target.allowedTargets.toSet())
    }

    @Test
    fun `aggregate element retention is binary class target`() {
        val retention = AggregateElement::class.annotations.filterIsInstance<Retention>().single()
        val target = AggregateElement::class.annotations.filterIsInstance<Target>().single()
        assertEquals(AnnotationRetention.BINARY, retention.value)
        assertEquals(setOf(AnnotationTarget.CLASS), target.allowedTargets.toSet())
    }
}
```

- [ ] **Step 2: Run ddd-core test and verify it fails**

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.annotation.ConceptAnnotationTest"
```

Expected: compile failure because annotations do not exist.

- [ ] **Step 3: Create annotation classes**

Create `BuildingBlock.kt`:

```kotlin
package com.only4.cap4k.ddd.core.annotation

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class BuildingBlock(
    val tag: String,
    val name: String = "",
    val packageName: String = "",
    val description: String = "",
    val aggregates: Array<String> = [],
    val eventName: String = "",
    val family: String,
    val variant: String = "",
)
```

Create `AggregateElement.kt`:

```kotlin
package com.only4.cap4k.ddd.core.annotation

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class AggregateElement(
    val aggregate: String = "",
    val name: String = "",
    val packageName: String = "",
    val description: String = "",
    val type: String,
    val root: Boolean = false,
)
```

- [ ] **Step 4: Delete old annotation and archinfo**

Remove these paths:

```powershell
Remove-Item -LiteralPath ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/annotation/Aggregate.kt
Remove-Item -LiteralPath ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/archinfo -Recurse
Remove-Item -LiteralPath ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/archinfo -Recurse
Remove-Item -LiteralPath cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/archinfo -Recurse
```

Before running those commands, verify all targets are under the repository root with `Resolve-Path`. Do not delete unrelated packages.

Remove these lines from `cap4k-ddd-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```text
com.only4.cap4k.ddd.archinfo.configure.ArchInfoProperties
com.only4.cap4k.ddd.archinfo.ArchInfoAutoConfiguration
```

- [ ] **Step 5: Update templates for annotations**

Remove all `use("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate")` and `@Aggregate(...)` blocks.

Add `@BuildingBlock` to design and type templates using context values supplied by later planner tasks. Use guarded defaults so templates compile only after planner contexts are updated:

```pebble
{{ use("com.only4.cap4k.ddd.core.annotation.BuildingBlock") -}}
@BuildingBlock(
    tag = "{{ buildingBlock.tag }}",
    name = "{{ buildingBlock.name }}",
    packageName = "{{ buildingBlock.packageName }}",
    description = "{{ buildingBlock.description | replace({'"': '\\"'}) | raw }}",
    aggregates = [{% for aggregate in buildingBlock.aggregates %}"{{ aggregate }}"{% if not loop.last %}, {% endif %}{% endfor %}],
    eventName = "{{ buildingBlock.eventName }}",
    family = "{{ buildingBlock.family }}",
    variant = "{{ buildingBlock.variant }}"
)
```

Add `@AggregateElement` to aggregate templates listed in the spec, except behavior and DB enum:

```pebble
{{ use("com.only4.cap4k.ddd.core.annotation.AggregateElement") -}}
@AggregateElement(
    aggregate = "{{ aggregateElement.aggregate }}",
    name = "{{ aggregateElement.name }}",
    packageName = "{{ aggregateElement.packageName }}",
    description = "{{ aggregateElement.description | replace({'"': '\\"'}) | raw }}",
    type = "{{ aggregateElement.type }}",
    root = {{ aggregateElement.root }}
)
```

- [ ] **Step 6: Update renderer tests**

Replace assertions that expect `@Aggregate(` or `Aggregate.TYPE_*` with assertions for `@AggregateElement(` or no aggregate annotation depending on template:

- `aggregate/entity.kt.peb` contains `@AggregateElement(` and `type = "entity"`.
- `aggregate/repository.kt.peb` contains `type = "repository"`.
- `aggregate/factory.kt.peb` contains `type = "factory"` for the factory class and does not annotate nested payload DTOs.
- `aggregate/behavior.kt.peb` does not contain `@AggregateElement`.
- `aggregate/enum.kt.peb` does not contain `@AggregateElement`.
- design templates contain `@BuildingBlock` with public family names.

- [ ] **Step 7: Run focused annotation/template tests**

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.annotation.ConceptAnnotationTest" `
  :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

- [ ] **Step 8: Scan for stale old metadata**

```powershell
rg -n "domain\.aggregate\.annotation\.Aggregate|@Aggregate\(|Aggregate\.TYPE_|core\.archinfo|ddd\.archinfo" ddd-core cap4k-ddd-starter cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-pipeline-gradle cap4k-plugin-code-analysis-compiler
```

Expected: no active source/template references. Historical docs may remain outside this scan if not part of compiled source.

- [ ] **Step 9: Commit annotation and archinfo removal**

```powershell
git add ddd-core cap4k-ddd-starter cap4k-plugin-pipeline-renderer-pebble
git commit -m "feat: replace aggregate metadata with concept annotations"
```

### Task 6: Design Planner Family IDs And DesignBlock Consumption

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/*ArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/*RenderModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt`
- Modify: design planner tests under `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/`

- [ ] **Step 1: Update planner tests to use `designBlocks`**

For each design planner test, replace old typed canonical setup with `CanonicalModel(designBlocks = listOf(...))`.

Example for query planner tests:

```kotlin
private fun queryBlock(variant: String = "") = DesignBlockModel(
    tag = "query",
    packageName = "order.read",
    name = "FindOrder",
    description = "Find order",
    aggregates = listOf("Order"),
    artifacts = listOf(ArtifactSelectionModel("query", variant)),
    fields = listOf(FieldModel(name = "orderNo", type = "String")),
    resultFields = listOf(FieldModel(name = "status", type = "String")),
)
```

Assert provider IDs exactly match public families:

```kotlin
assertEquals("query", DesignQueryArtifactPlanner().id)
assertEquals("query-handler", DesignQueryHandlerArtifactPlanner().id)
assertEquals("domain-subscriber", DesignDomainEventHandlerArtifactPlanner().id)
```

Add a test proving explicit `query/page` without `query-handler` makes the query planner emit one item and query-handler planner emit none.

- [ ] **Step 2: Run design planner tests and verify failures**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.*"
```

Expected: failures because planners still use typed lists and `design-*` IDs.

- [ ] **Step 3: Rename provider IDs**

Change planner IDs:

```text
design-command -> command
design-query -> query
design-query-handler -> query-handler
design-client -> client
design-client-handler -> client-handler
design-api-payload -> api-payload
design-domain-event -> domain-event
design-domain-event-handler -> domain-subscriber
design-integration-event -> integration-event
design-integration-event-subscriber -> integration-subscriber
design-domain-service -> domain-service
design-saga -> saga
```

Do not add aliases.

- [ ] **Step 4: Add block selection helpers**

Create a small helper file in the design generator package, for example `DesignBlockSelection.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel

internal fun DesignBlockModel.selects(family: String): Boolean =
    artifacts.any { it.family == family }

internal fun DesignBlockModel.selection(family: String): ArtifactSelectionModel? =
    artifacts.firstOrNull { it.family == family }
```

Use this in every design planner.

- [ ] **Step 5: Update planners to consume `model.designBlocks`**

For each planner, replace old typed list iteration with:

```kotlin
model.designBlocks
    .filter { it.selects(id) }
    .map { block -> /* existing context creation mapped from block */ }
```

Map fields:

- `block.fields` replaces request fields.
- `block.resultFields` replaces response fields.
- `block.description` replaces old description.
- `block.aggregates` replaces old related/owner aggregates.
- `block.eventName` and `block.persist` feed event templates where relevant.
- `block.selection(id)?.variant == "page"` drives query/api-payload page behavior.
- `block.selection("integration-event")?.variant` drives inbound/outbound integration event behavior.

- [ ] **Step 6: Add `buildingBlock` render context**

Every design planner must put this map into template context:

```kotlin
"buildingBlock" to mapOf(
    "tag" to block.tag,
    "name" to block.name,
    "packageName" to block.packageName,
    "description" to block.description,
    "aggregates" to block.aggregates,
    "eventName" to block.eventName,
    "family" to id,
    "variant" to (block.selection(id)?.variant ?: ""),
)
```

For integration-event, use the integration-event selection variant. For domain-subscriber and integration-subscriber, variant remains empty unless the family itself has a variant.

- [ ] **Step 7: Update layout resolver usage**

Replace role-based integration event layout with variant-based calls, for example:

```kotlin
artifactLayout.designIntegrationEventPackage(variant = "inbound", designPackage = block.packageName)
```

Keep Kotlin method names private if needed, but do not expose `IntegrationEventRole` in planner input.

- [ ] **Step 8: Run design planner tests until green**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-design:test
```

- [ ] **Step 9: Commit design planner migration**

```powershell
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin `
        cap4k-plugin-pipeline-generator-design/src/test/kotlin `
        cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt `
        cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolverTest.kt
git commit -m "feat: select design artifacts by public family"
```

### Task 7: Type And Aggregate Planner Annotation Contexts

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-types/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/**`
- Modify: type and aggregate planner tests.

- [ ] **Step 1: Add type planner tests for `@BuildingBlock` context**

In `ValueObjectArtifactPlannerTest.kt`, assert the plan context contains:

```kotlin
val buildingBlock = item.context["buildingBlock"] as Map<*, *>
assertEquals("value_object", buildingBlock["tag"])
assertEquals("value-object", buildingBlock["family"])
assertEquals(listOf("Order"), buildingBlock["aggregates"])
```

If enum planner exists as a built-in planner, add equivalent tests for `tag = "enum"`, `family = "enum"`.

- [ ] **Step 2: Add aggregate planner tests for `@AggregateElement` context**

In aggregate planner tests, assert contexts for each annotated type:

- schema: `type = "schema"`, aggregate from entity owner, full package.
- entity: `type = "entity"`, `root` equals entity aggregate root flag.
- repository: `type = "repository"`.
- factory: `type = "factory"`.
- specification: `type = "specification"`.
- unique query: `type = "unique-query"`.
- unique query handler: `type = "unique-query-handler"`.
- unique validator: `type = "unique-validator"`.
- strong id aggregate root: `type = "strong-id"`, `aggregate = ownerAggregateName`, `root = true`.
- strong id reference: `type = "strong-id"`, `aggregate = ""`, `root = false`.
- projection: `type = "projection"`.

Also assert behavior and DB enum contexts do not include `aggregateElement`.

- [ ] **Step 3: Run planner tests and verify failures**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-types:test `
  :cap4k-plugin-pipeline-generator-aggregate:test
```

Expected: failures because contexts are missing.

- [ ] **Step 4: Add `buildingBlock` in value-object and enum planners**

For value-object generated artifacts, add:

```kotlin
"buildingBlock" to mapOf(
    "tag" to "value_object",
    "name" to valueObject.name,
    "packageName" to valueObject.packageName,
    "description" to valueObject.description,
    "aggregates" to valueObject.aggregates,
    "eventName" to "",
    "family" to "value-object",
    "variant" to "",
)
```

For enum manifest generated artifacts, add equivalent `tag = "enum"`, `family = "enum"`. If DB enum still uses the same `aggregate/enum.kt.peb` template, do not pass `buildingBlock` for DB enum; the template should render `@BuildingBlock` only when `buildingBlock` exists.

- [ ] **Step 5: Add `aggregateElement` context helper**

In aggregate generator package, add helper:

```kotlin
internal fun aggregateElementContext(
    aggregate: String,
    name: String,
    packageName: String,
    description: String,
    type: String,
    root: Boolean = false,
): Map<String, Any?> = mapOf(
    "aggregate" to aggregate,
    "name" to name,
    "packageName" to packageName,
    "description" to description,
    "type" to type,
    "root" to root,
)
```

Use it in schema/entity/repository/factory/specification/unique-query/unique-query-handler/unique-validator/strong-id/projection planners.

- [ ] **Step 6: Keep behavior and DB enum unannotated**

Do not add `aggregateElement` to `BehaviorArtifactPlanner`, `SharedEnumArtifactPlanner`, or `LocalEnumArtifactPlanner`.

If the shared template conditionally renders annotations, use:

```pebble
{% if aggregateElement is defined %}
...
{% endif %}
```

- [ ] **Step 7: Run type and aggregate planner tests until green**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-types:test `
  :cap4k-plugin-pipeline-generator-aggregate:test
```

- [ ] **Step 8: Commit planner annotation contexts**

```powershell
git add cap4k-plugin-pipeline-generator-types/src/main/kotlin `
        cap4k-plugin-pipeline-generator-types/src/test/kotlin `
        cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin `
        cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin
git commit -m "feat: emit concept annotation contexts"
```

### Task 8: Code Analysis Design Block Recovery

**Files:**
- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kOptions.kt`
- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt`
- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementJsonWriter.kt`
- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kIrGenerationExtension.kt`
- Modify: code-analysis compiler tests.
- Modify: `cap4k-plugin-pipeline-source-ir-analysis/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProvider.kt`

- [ ] **Step 1: Add analysis output tests for `@BuildingBlock` merge**

In `DesignElementExtractionTest.kt`, add a compile fixture with two top-level classes:

```kotlin
@BuildingBlock(tag = "query", name = "FindOrder", packageName = "order.read", description = "Find order", aggregates = ["Order"], family = "query")
class FindOrderQry(val orderNo: String) {
    data class Response(val status: String)
}

@BuildingBlock(tag = "query", name = "FindOrder", packageName = "order.read", description = "Find order", aggregates = ["Order"], family = "query-handler")
class FindOrderQryHandler
```

Assert `design-elements.json` contains one block with:

```json
"tag":"query"
"package":"order.read"
"name":"FindOrder"
"description":"Find order"
"aggregates":["Order"]
"artifacts":[{"family":"query"},{"family":"query-handler"}]
"fields":[{"name":"orderNo"...}]
"resultFields":[{"name":"status"...}]
```

Add a conflict fixture where two `@BuildingBlock` annotations with the same merge key have different descriptions. Expected compile analysis failure message: `conflicting BuildingBlock metadata for query order.read FindOrder: description`.

- [ ] **Step 2: Add aggregate element IR tests**

In `AnalysisOutputCorrectnessTest.kt`, replace `@Aggregate` fixtures with `@AggregateElement(type = "entity", aggregate = "Category", name = "Category", packageName = "demo.domain.aggregates.category", root = true)`. Assert entity method relationships still emit.

Add a fixture for `@AggregateElement(type = "projection", aggregate = "Category", ...)` only if existing flow relation logic needs projection classification; otherwise only assert the class index accepts and ignores unsupported relationship roles without crashing.

- [ ] **Step 3: Run code-analysis tests and verify failures**

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.DesignElementExtractionTest" `
  --tests "com.only4.cap4k.plugin.codeanalysis.compiler.AnalysisOutputCorrectnessTest"
```

Expected: failures because compiler still reads old `@Aggregate` and outputs old design element schema.

- [ ] **Step 4: Update compiler options**

In `Cap4kOptions.kt`, remove aggregate annotation option keys. Add defaults for:

```kotlin
val buildingBlockAnnFq: String = "com.only4.cap4k.ddd.core.annotation.BuildingBlock"
val aggregateElementAnnFq: String = "com.only4.cap4k.ddd.core.annotation.AggregateElement"
```

Keep existing runtime annotations like `DomainEvent` and `IntegrationEvent` where needed for runtime metadata recovery such as `persist`.

- [ ] **Step 5: Rewrite `DesignElementCollector` around building blocks**

Implement:

- scan top-level classes with `@BuildingBlock`;
- read annotation args `tag`, `name`, `packageName`, `description`, `aggregates`, `eventName`, `family`, `variant`;
- project fields/resultFields only from classes that directly carry payload/result structures;
- group by `tag + packageName + name`;
- merge artifacts;
- reject conflicting shared metadata;
- do not read old `@Aggregate`, `traits`, or `role`.

- [ ] **Step 6: Update `DesignElementJsonWriter`**

Write design block array schema with field names:

```json
tag, package, name, description, aggregates, eventName, persist, artifacts, fields, resultFields
```

Do not write `entity`, `traits`, `role`, `requestFields`, or `responseFields`.

Omit optional empty fields only if existing JSON writer style already omits empty values; otherwise keep compact explicit arrays consistently.

- [ ] **Step 7: Update IR graph collector for aggregate elements**

In `Cap4kIrGenerationExtension.kt`:

- remove `payloadToAggregateName`;
- remove old `readAggregateInfo(aggregateAnn)`;
- add `readAggregateElementInfo(aggregateElementAnn)`;
- treat `type = "entity"` as aggregate entity/root metadata;
- treat `type = "projection"`, `schema`, `repository`, `factory`, `specification`, `unique-*`, and `strong-id` as indexed concept metadata only where useful;
- keep `DomainEvent` / `IntegrationEvent` runtime annotation detection;
- do not use nested factory payload annotations.

- [ ] **Step 8: Update `IrAnalysisSourceProvider` parser**

Parse `design-elements.json` using the same block array schema as design-json. Populate `DesignElementSnapshot` or directly `DesignBlockModel` snapshots according to the API state from earlier tasks. Remove parsing for `entity`, `traits`, and `role`.

- [ ] **Step 9: Run code-analysis and ir source tests until green**

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test `
  :cap4k-plugin-pipeline-source-ir-analysis:test
```

If `cap4k-plugin-pipeline-source-ir-analysis` has no test task or no tests, run the nearest module compile task and record that in the commit message body.

- [ ] **Step 10: Commit analysis recovery migration**

```powershell
git add cap4k-plugin-code-analysis-compiler/src/main/kotlin `
        cap4k-plugin-code-analysis-compiler/src/test/kotlin `
        cap4k-plugin-pipeline-source-ir-analysis/src/main/kotlin
git commit -m "feat: recover design blocks from concept annotations"
```

### Task 9: Drawing-Board And Flow Observation Outputs

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/drawing-board/document.json.peb`
- Modify: `cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunner.kt` if conflict policy handling needs a new observation output kind.
- Modify drawing-board and flow tests.

- [ ] **Step 1: Add drawing-board schema tests**

In `DrawingBoardArtifactPlannerTest.kt`, create a `CanonicalModel` or IR snapshot model with design blocks:

- query block whose artifacts equal default: expected output JSON omits `artifacts`.
- query page block: expected output JSON includes `"artifacts":[{"family":"query","variant":"page"}]`.
- integration outbound default: expected output omits artifacts.
- integration inbound subscriber block: expected output includes both `integration-event/inbound` and `integration-subscriber`.

Assert output path remains `drawing_board_<tag>.json` unless existing tests require another agreed path.

- [ ] **Step 2: Add overwrite tests for drawing-board and flow**

In drawing-board and flow planner tests, assert every plan item has `conflictPolicy == ConflictPolicy.OVERWRITE` or `outputKind == ArtifactOutputKind.GENERATED_SOURCE` only if that is the chosen mechanism. Prefer explicit `ConflictPolicy.OVERWRITE` for observation outputs so they are not mislabeled as generated source.

- [ ] **Step 3: Run drawing-board and flow tests and verify failures**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-drawing-board:test `
  :cap4k-plugin-pipeline-generator-flow:test
```

Expected: failures because drawing-board still emits old fields and flow conflict policy is not overwrite.

- [ ] **Step 4: Update drawing-board planner**

Plan from design blocks rather than old `DrawingBoardElementModel` fields. Group by `tag` and render block arrays with formal JSON keys:

```json
tag, package, name, description, aggregates, eventName, persist, artifacts, fields, resultFields
```

Implement default-match artifact omission:

```kotlin
private fun shouldEmitArtifacts(block: DesignBlockModel): Boolean =
    block.artifacts != defaultArtifactsFor(block.tag)
```

Use stable ordering for artifacts and fields so generated JSON diffs are reviewable.

- [ ] **Step 5: Update drawing-board template**

Remove `traits`, `role`, `entity`, `requestFields`, and `responseFields` output. Render `description`, `fields`, `resultFields`, and conditional `artifacts`.

Do not render template IDs or `.peb` paths.

- [ ] **Step 6: Update flow planner conflict policy**

Set flow plan items to `ConflictPolicy.OVERWRITE`. Keep output paths and graph semantics unchanged.

If the runner centralizes observation overwrite policy, add a small helper that recognizes generator IDs `drawing-board` and `flow` and forces overwrite there. Keep business generated source behavior unchanged.

- [ ] **Step 7: Ensure `irAnalysis.inputDirs` drives both outputs**

In Gradle/pipeline tests, assert that configuring `irAnalysis.inputDirs` causes both registered planners to run without `generators.flow.enabled` or `generators.drawingBoard.enabled`.

- [ ] **Step 8: Run focused tests until green**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-drawing-board:test `
  :cap4k-plugin-pipeline-generator-flow:test `
  :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultPipelineRunnerTest"
```

- [ ] **Step 9: Commit observation output migration**

```powershell
git add cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin `
        cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/drawing-board `
        cap4k-plugin-pipeline-generator-drawing-board/src/test/kotlin `
        cap4k-plugin-pipeline-generator-flow/src/main/kotlin `
        cap4k-plugin-pipeline-generator-flow/src/test/kotlin `
        cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunner.kt `
        cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt
git commit -m "feat: emit overwrite observation outputs"
```

### Task 10: Full Repository Cleanup And Contract Verification

**Files:**
- Modify any remaining tests/fixtures under `cap4k-plugin-pipeline-gradle/src/test/**` and renderer fixtures that still use removed public fields.
- Modify public docs only where current, non-historical generator reference pages would otherwise be wrong.
- No implementation code should remain with old public contract names.

- [ ] **Step 1: Run stale contract scans**

```powershell
rg -n "\btraits\b|\brole\b|ValueObjectScope|IntegrationEventRole|requestFields|responseFields|\bdesc\b|design-domain-event-handler|design-integration-event-subscriber|design-command|design-query|SourceConfig\(enabled|GeneratorConfig\(enabled|enabledSourceIds\(|enabledGeneratorIds\(" cap4k-plugin-* ddd-core cap4k-ddd-starter
```

Expected: no active source/test references except unrelated words such as Bootstrap slot `role`. For each match, either update it or document why it is unrelated in the commit body.

Run old metadata scan:

```powershell
rg -n "@Aggregate\(|Aggregate\.TYPE_|domain\.aggregate\.annotation\.Aggregate|core\.archinfo|ddd\.archinfo" cap4k-plugin-* ddd-core cap4k-ddd-starter
```

Expected: no active source/test/template references.

- [ ] **Step 2: Update functional fixtures**

Find functional design JSON fixtures:

```powershell
rg -n "requestFields|responseFields|\bdesc\b|\btraits\b|\brole\b" cap4k-plugin-pipeline-gradle/src/test/resources cap4k-plugin-pipeline-gradle/src/test/kotlin
```

Update them to `description`, `fields`, `resultFields`, and `artifacts`.

For domain event fixture expectations, assert `domain-subscriber`, not `domain-event-handler`.

- [ ] **Step 3: Add one end-to-end functional fixture for #92**

In `PipelinePluginFunctionalTest.kt`, add or update a fixture that:

- supplies design-json with `query/page`, `integration-event/inbound + integration-subscriber`, and a default `domain_event`;
- supplies enum/value-object manifests using `aggregates`;
- runs generation;
- asserts generated code contains `@BuildingBlock` and no `@Aggregate`;
- runs analysis output if the fixture supports it;
- asserts drawing-board JSON can be fed back as design-json input without schema errors.

Keep this fixture focused; do not add broader flow behavior assertions beyond overwrite and output presence.

- [ ] **Step 4: Run module-level verification**

Run focused module tests first:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test `
  :cap4k-plugin-pipeline-core:test `
  :cap4k-plugin-pipeline-source-design-json:test `
  :cap4k-plugin-pipeline-source-enum-manifest:test `
  :cap4k-plugin-pipeline-source-value-object-manifest:test `
  :cap4k-plugin-pipeline-generator-design:test `
  :cap4k-plugin-pipeline-generator-types:test `
  :cap4k-plugin-pipeline-generator-aggregate:test `
  :cap4k-plugin-pipeline-generator-drawing-board:test `
  :cap4k-plugin-pipeline-generator-flow:test `
  :cap4k-plugin-code-analysis-compiler:test `
  :ddd-core:test
```

Expected: all pass.

- [ ] **Step 5: Run Gradle plugin verification**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test
```

Expected: all pass.

If functional tests are too slow for an intermediate run, at least run the new #92 functional test by exact test name and note skipped broader tests in the commit body.

- [ ] **Step 6: Run full build or explain residual limits**

Preferred final command:

```powershell
.\gradlew.bat test
```

Expected: all tests pass.

If this is too slow or blocked by unrelated environment issues, capture the failing module, error message, and the focused test coverage already passing.

- [ ] **Step 7: Commit final cleanup**

```powershell
git add cap4k-plugin-pipeline-gradle docs cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-* ddd-core cap4k-ddd-starter
git commit -m "test: verify issue 92 metadata contract"
```

### Task 11: Issue Lifecycle Update After Plan And Implementation

**Files:**
- Modify GitHub issue #92 only after plan is written or implementation milestones complete.

- [ ] **Step 1: After this plan is reviewed, update issue for plan milestone**

Use issue-governance lifecycle rules. Mark `plan written` and comment with the plan path and commit hash.

- [ ] **Step 2: After implementation is complete, update issue with evidence**

Do not close #92 unless implementation is merged, release needs are resolved, and downstream verification requirements are resolved.

Comment should include:

- implementation commit range or PR link;
- verification commands and results;
- release/downstream verification status.

---

## Self-Review Checklist

- Spec coverage: tasks cover public input, canonical model, config, annotations, archinfo removal, planner selection, analysis recovery, drawing-board/flow overwrite, and verification.
- Placeholder scan: no task should contain `TBD`, `TODO`, or vague instructions without concrete files and commands.
- Type consistency: `DesignBlockModel`, `ArtifactSelectionModel`, `BuildingBlock`, and `AggregateElement` names match the approved spec.
- Scope: addon artifact selection and addon-generated code recovery stay out of scope.











## File Structure

### Public API And Config

- Modify `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`: remove `enabled` from `SourceConfig` and `GeneratorConfig`, remove `enabledSourceIds()` / `enabledGeneratorIds()`, add family-oriented layout names if needed.
- Modify `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`: add `DesignBlockModel` and `ArtifactSelectionModel`; remove design public `role`, `traits`, `ValueObjectScope`, `IntegrationEventRole` from new contract; migrate value-object ownership to `aggregates`.
- Modify `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt`: add family/variant layout entry points and remove public role-driven integration event layout.
- Modify `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfigTest.kt`, `PipelineModelsTest.kt`, and `ArtifactLayoutResolverTest.kt`.

### Sources And Canonical Assembly

- Modify `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`: parse `description`, `fields`, `resultFields`, `artifacts`; reject removed fields.
- Modify `cap4k-plugin-pipeline-source-enum-manifest/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/enummanifest/EnumManifestSourceProvider.kt`: support `aggregates` with 0..1 rule.
- Modify `cap4k-plugin-pipeline-source-value-object-manifest/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/valueobject/ValueObjectManifestSourceProvider.kt`: replace `scope` / single `aggregate` with `aggregates`.
- Modify `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`: default expansion and validation at source/canonical boundary; populate `designBlocks`; keep enum/value-object typed canonical models.
- Modify matching source/core tests.

### Pipeline Runner And Gradle Wiring

- Modify `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunner.kt`: source participation by config key; built-in planner execution by model/input policy instead of `enabled` filter; observation overwrite handling.
- Modify `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`, `PipelinePlugin.kt`, and `Cap4kExtension.kt`: remove design/source/flow/drawing-board enabled semantics per spec while retaining aggregate internal artifact options.
- Modify Gradle plugin tests and functional tests.

### Concept Annotations And Templates

- Create `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/annotation/BuildingBlock.kt`.
- Create `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/annotation/AggregateElement.kt`.
- Delete `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/annotation/Aggregate.kt`.
- Remove `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/archinfo/**` and `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/archinfo/**` plus starter auto-configuration import entries.
- Modify Pebble templates under `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/**` to emit new annotations and stop emitting `@Aggregate`.
- Modify renderer and ddd-core tests.

### Planners, Analysis, Drawing-Board, Flow

- Modify `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/**`: provider IDs become public family names; planners select `model.designBlocks` by artifact family.
- Modify `cap4k-plugin-pipeline-generator-types/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectArtifactPlanner.kt`: emit `@BuildingBlock` metadata.
- Modify `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/**`: emit `@AggregateElement`; do not annotate behavior or DB enum.
- Modify `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/**`: read `@BuildingBlock` / `@AggregateElement`; output design block schema; remove old `@Aggregate` paths and `payloadToAggregateName`.
- Modify `cap4k-plugin-pipeline-source-ir-analysis/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProvider.kt`: parse design block schema.
- Modify `cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt`: emit formal block arrays and default-match `artifacts` logic.
- Modify `cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlanner.kt`: default overwrite and input-driven execution.

## Commit Strategy

Commit after each task. Use focused commit messages such as:

- `test: define issue 92 API contract failures`
- `feat: add artifact selection canonical model`
- `feat: parse design blocks with artifact selections`
- `feat: emit cap4k concept annotations`
- `feat: recover design blocks from analysis metadata`
- `chore: remove aggregate annotation archinfo path`

---
