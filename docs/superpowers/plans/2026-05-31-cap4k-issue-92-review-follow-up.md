# Cap4k Issue 92 Review Follow-Up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining PR #95 review gaps for issue #92 by removing old public metadata leakage, making enum manifest a first-class input-driven generated-source path, enforcing canonical fail-fast semantics, and synchronizing active docs/skills.

**Architecture:** Keep the existing issue #92 design: public design metadata is `tag` plus `artifacts[{ family, variant }]`, with fields/resultFields and aggregates as the public schema. Apply small focused fixes at source/recovery boundaries, canonical assembly, Gradle wiring, and active documentation; do not introduce a new public generator switch. Add targeted regression tests before each production change.

**Tech Stack:** Kotlin 2.2.20, Gradle TestKit, JUnit 5, Gson, cap4k pipeline API/core/source/generator/Gradle plugin modules, markdown docs and workspace skills.

---

## File Structure

- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt`
  - Responsibility: recover `@BuildingBlock` design metadata from compiled Kotlin without writing removed public fields into `design-elements.json`.
- Modify: `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementExtractionTest.kt`
  - Responsibility: regression coverage for generated domain-event recovery output.
- Modify: `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`
  - Responsibility: fail fast on removed public fields in design JSON input.
- Modify: `cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt`
  - Responsibility: regression coverage for rejected top-level `entity`.
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
  - Responsibility: validate authoring `DesignSpecSnapshot` tags at canonical boundary.
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
  - Responsibility: replace silent unsupported-tag tests with fail-fast assertions.
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
  - Responsibility: include required domain module when `types.enumManifest` is configured.
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
  - Responsibility: register enum-manifest generated sources into the domain module compile lifecycle.
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
  - Responsibility: config factory coverage for enum-manifest-only module participation.
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt`
  - Responsibility: generated source module role coverage for enum manifest.
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
  - Responsibility: enum-manifest-only compile fixture coverage.
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/enum-manifest-compile-sample/**`
  - Responsibility: minimal enum-only functional fixture.
- Modify: `docs/public/reference/generator-dsl.md`
  - Responsibility: remove stale non-DB `enabled` examples and document input-presence semantics.
- Modify: `docs/superpowers/capability-matrix.md`
  - Responsibility: update integration event capability row to artifact variants.
- Modify: `docs/public/authoring/generator/input-sources.md`
  - Responsibility: remove public `entity` guidance and document aggregate ownership.
- Modify: `skills/cap4k-generation/references/sources/design-json.md`
  - Responsibility: remove public `entity` guidance from agent skill source docs.
- Modify: `skills/cap4k-generation/references/sources/value-object-manifest.md`
  - Responsibility: align `aggregates` guidance with 0..1 implementation rule.
- Modify: `skills/cap4k-generation/workflows/generate-from-design.md`
  - Responsibility: remove stale `desc`, `requestFields`, `responseFields`, `role` checklist wording.
- Modify: `cap4k-plugin-pipeline-source-ir-analysis/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProvider.kt`
  - Responsibility: remove trailing blank line reported by `git diff --check`.

---

### Task 1: Prevent Domain-Event Recovery From Emitting Public `entity`

**Files:**
- Modify: `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementExtractionTest.kt`
- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt`

- [ ] **Step 1: Write the failing regression test**

Add this test in `DesignElementExtractionTest` near the other `design-elements.json` recovery tests:

```kotlin
@Test
fun `generated domain event recovery excludes synthetic entity constructor parameter`() {
    val sources = listOf(
        SourceFile.kotlin(
            "BuildingBlock.kt",
            """
                package com.only4.cap4k.ddd.core.annotation

                annotation class BuildingBlock(
                    val tag: String,
                    val name: String,
                    val packageName: String,
                    val description: String = "",
                    val aggregates: Array<String> = [],
                    val eventName: String = "",
                    val family: String = "",
                    val variant: String = "",
                )
            """.trimIndent()
        ),
        SourceFile.kotlin(
            "DomainEvent.kt",
            """
                package com.only4.cap4k.ddd.core.domain.event.annotation
                annotation class DomainEvent(val value: String = "", val persist: Boolean = false)
            """.trimIndent()
        ),
        SourceFile.kotlin(
            "OrderCreated.kt",
            """
                package demo.domain.aggregates.order.events

                import com.only4.cap4k.ddd.core.annotation.BuildingBlock
                import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent

                class Order

                @BuildingBlock(
                    tag = "domain_event",
                    packageName = "order.events",
                    name = "OrderCreated",
                    description = "order created",
                    aggregates = ["Order"],
                    family = "domain-event",
                )
                @DomainEvent(persist = true)
                data class OrderCreated(
                    val entity: Order,
                    val orderId: Long,
                    val reason: String? = null,
                )
            """.trimIndent()
        )
    )

    val outputDir = compileWithCap4kPlugin(sources)
    val json = outputDir.resolve("design-elements.json").toFile().readText()
    val orderCreated = findObject(extractTopLevelObjects(json), "domain_event", "OrderCreated")

    assertTrue(orderCreated.contains("\"artifacts\":[{\"family\":\"domain-event\"}]"))
    assertTrue(orderCreated.contains("\"fields\":[{\"name\":\"orderId\",\"type\":\"Long\",\"nullable\":false}"))
    assertTrue(orderCreated.contains("\"name\":\"reason\",\"type\":\"String\",\"nullable\":true,\"defaultValue\":\"null\""))
    assertFalse(orderCreated.contains("\"name\":\"entity\""))
    assertFalse(json.contains("\"requestFields\""))
    assertFalse(json.contains("\"responseFields\""))
}
```

- [ ] **Step 2: Run the focused test and verify failure**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.DesignElementExtractionTest.generated domain event recovery excludes synthetic entity constructor parameter"
```

Expected: FAIL because `DesignElementCollector.collectFieldsRecursive` currently records the constructor parameter named `entity`.

- [ ] **Step 3: Implement the minimal production fix**

In `DesignElementCollector.collectBuildingBlock`, change the `fields` construction so `domain-event` artifacts drop the synthetic aggregate entity parameter after recursive field collection:

```kotlin
val fields = primaryFieldCarrier(declaration, family)?.let { fieldsRoot ->
    collectFields(
        fieldsRoot,
        nestedTypes,
        DefaultValueContext("$tag $name field"),
    ).filterRecoveredFields(family)
}.orEmpty()
```

Add this helper near `hasResultFields()`:

```kotlin
private fun List<DesignField>.filterRecoveredFields(family: String): List<DesignField> =
    when (family) {
        "domain-event" -> filterNot { field -> field.name == "entity" || field.name.startsWith("entity.") }
        else -> this
    }
```

Do not filter `integration-event`; that event payload is public metadata and should stay in `fields`.

- [ ] **Step 4: Run the focused test and verify pass**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.DesignElementExtractionTest.generated domain event recovery excludes synthetic entity constructor parameter"
```

Expected: PASS.

- [ ] **Step 5: Run the module test**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 2: Reject Top-Level `entity` In Design JSON Input

**Files:**
- Modify: `cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt`
- Modify: `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`

- [ ] **Step 1: Extend the removed-field test to include `entity`**

In `DesignJsonSourceProviderTest.rejects removed public fields with stable entry message`, update the JSON input:

```json
{
  "tag": "query",
  "package": "order.read",
  "name": "FindOrder",
  "desc": "old",
  "requestFields": [],
  "responseFields": [],
  "traits": ["page"],
  "entity": "Order"
}
```

Update the expected assertion:

```kotlin
assertEquals(
    "design entry FindOrder uses removed fields: desc, requestFields, responseFields, traits, entity",
    error.message,
)
```

- [ ] **Step 2: Run the focused test and verify failure**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-source-design-json:test --tests "com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonSourceProviderTest.rejects removed public fields with stable entry message"
```

Expected: FAIL because `entity` is not listed in the current `removedPublicFields`.

- [ ] **Step 3: Add `entity` to the removed field list**

In `DesignJsonSourceProvider`, change:

```kotlin
private val removedPublicFields = listOf("desc", "requestFields", "responseFields", "traits", "role", "scope")
```

to:

```kotlin
private val removedPublicFields = listOf("desc", "requestFields", "responseFields", "traits", "role", "scope", "entity")
```

- [ ] **Step 4: Run the focused test and module test**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-source-design-json:test --tests "com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonSourceProviderTest.rejects removed public fields with stable entry message"
.\gradlew.bat :cap4k-plugin-pipeline-source-design-json:test
```

Expected: both commands end with `BUILD SUCCESSFUL`.

---

### Task 3: Make Canonical Assembly Fail Fast On Unsupported Authoring Tags

**Files:**
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`

- [ ] **Step 1: Replace the silent unsupported-tag tests**

Replace `skips entries with unsupported tags` with:

```kotlin
@Test
fun `design spec assembly rejects unsupported tags`() {
    val assembler = DefaultCanonicalAssembler()

    val error = assertThrows(IllegalArgumentException::class.java) {
        assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "evt",
                            packageName = "order.events",
                            name = "OrderCreated",
                            description = "order created event",
                            aggregates = listOf("Order"),
                        ),
                    )
                ),
            ),
        )
    }

    assertEquals("Unsupported design tag: evt", error.message)
}
```

Replace `design spec assembly ignores non exact canonical tags and historical aliases` with:

```kotlin
@Test
fun `design spec assembly rejects non exact canonical tags and historical aliases`() {
    val assembler = DefaultCanonicalAssembler()

    listOf("COMMAND", "Query", "Client", "API_PAYLOAD", "DOMAIN_EVENT", "cmd", "qry", "cli").forEach { tag ->
        val error = assertThrows(IllegalArgumentException::class.java) {
            assembler.assemble(
                config = baseConfig(),
                snapshots = listOf(
                    DesignSpecSnapshot(
                        entries = listOf(
                            DesignSpecEntry(
                                tag = tag,
                                packageName = "order.submit",
                                name = "UnsupportedTagBlock",
                                description = "unsupported tag",
                                aggregates = emptyList(),
                            ),
                        )
                    ),
                ),
            )
        }

        assertEquals("Unsupported design tag: $tag", error.message)
    }
}
```

- [ ] **Step 2: Run focused tests and verify failure**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest.design spec assembly rejects unsupported tags" --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest.design spec assembly rejects non exact canonical tags and historical aliases"
```

Expected: FAIL because unsupported entries are currently filtered by `.filter { entry -> entry.tag in SupportedDesignBlockTags }`.

- [ ] **Step 3: Validate authoring entries before conversion**

In `DefaultCanonicalAssembler.assemble`, replace the current `designBlocks` start:

```kotlin
val designBlocks = designSnapshot?.entries.orEmpty()
    .asSequence()
    .filter { entry -> entry.tag in SupportedDesignBlockTags }
    .map { entry -> entry.toDesignBlockModel() }
```

with:

```kotlin
val designEntries = designSnapshot?.entries.orEmpty()
designEntries.forEach { entry ->
    require(entry.tag in SupportedDesignBlockTags) {
        "Unsupported design tag: ${entry.tag}"
    }
}
val designBlocks = designEntries
    .asSequence()
    .map { entry -> entry.toDesignBlockModel() }
```

Keep recovered observation inputs separate; this validation applies to authoring `DesignSpecSnapshot.entries`.

- [ ] **Step 4: Run focused tests and module test**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest.design spec assembly rejects unsupported tags" --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest.design spec assembly rejects non exact canonical tags and historical aliases"
.\gradlew.bat :cap4k-plugin-pipeline-core:test
```

Expected: both commands end with `BUILD SUCCESSFUL`.

---

### Task 4: Make `types.enumManifest` A First-Class Generated-Source Input

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/enum-manifest-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/enum-manifest-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/enum-manifest-compile-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/enum-manifest-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/EnumManifestCompileSmoke.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/enum-manifest-compile-sample/enums/shared-enums.json`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`

- [ ] **Step 1: Add config factory regression coverage**

Add this test in `Cap4kProjectConfigFactoryTest` near `value object manifest only config carries domain module and source options`:

```kotlin
@Test
fun `enum manifest only config carries domain module and source options`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
    val manifest = project.file("shared-enums.json")
    manifest.writeText(
        """
        [
          { "name": "Status", "package": "shared", "items": [ { "value": 0, "name": "DRAFT", "desc": "Draft" } ] }
        ]
        """.trimIndent()
    )

    extension.project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
    }
    extension.types {
        enumManifest {
            files.from(manifest)
        }
    }

    val config = Cap4kProjectConfigFactory().build(project, extension)

    assertEquals(mapOf("domain" to "demo-domain"), config.modules)
    assertEquals(setOf("enum-manifest"), config.sources.keys)
    assertEquals(
        mapOf("files" to listOf(manifest.absolutePath)),
        config.sources.getValue("enum-manifest").options,
    )
}
```

- [ ] **Step 2: Add generated-source role regression coverage**

In `PipelinePluginTest`, rename the test `generated source module roles are limited to aggregate generated source families` to:

```kotlin
fun `generated source module roles include generated source families`()
```

Add this assertion inside that test after the design-json `emptySet` case:

```kotlin
assertEquals(
    setOf("domain"),
    generatedSourceModuleRoles(
        projectConfig(
            modules = mapOf("domain" to "demo-domain"),
            sources = mapOf("enum-manifest" to SourceConfig()),
            generators = emptyMap(),
        )
    )
)
```

- [ ] **Step 3: Create enum-only functional fixture**

Create `cap4k-plugin-pipeline-gradle/src/test/resources/functional/enum-manifest-compile-sample/settings.gradle.kts`:

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
    }
}

includeBuild("__CAP4K_REPO_ROOT__")

rootProject.name = "enum-manifest-compile-sample"
include("demo-domain")
```

Create `cap4k-plugin-pipeline-gradle/src/test/resources/functional/enum-manifest-compile-sample/build.gradle.kts`:

```kotlin
plugins {
    id("io.github.ldmoxeii.cap4k.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
    }
    types {
        enumManifest {
            files.from("enums/shared-enums.json")
        }
    }
}
```

Create `cap4k-plugin-pipeline-gradle/src/test/resources/functional/enum-manifest-compile-sample/demo-domain/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
}
```

Create `cap4k-plugin-pipeline-gradle/src/test/resources/functional/enum-manifest-compile-sample/enums/shared-enums.json`:

```json
[
  {
    "name": "Status",
    "package": "com.acme.demo.domain.shared.enums",
    "items": [
      { "value": 0, "name": "DRAFT", "desc": "Draft" },
      { "value": 1, "name": "PUBLISHED", "desc": "Published" }
    ]
  }
]
```

Create `cap4k-plugin-pipeline-gradle/src/test/resources/functional/enum-manifest-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/EnumManifestCompileSmoke.kt`:

```kotlin
package com.acme.demo.domain

import com.acme.demo.domain.shared.enums.Status

class EnumManifestCompileSmoke(
    private val status: Status,
) {
    fun wire(): Status = status
}
```

- [ ] **Step 4: Add enum-only compile functional test**

Add this test in `PipelinePluginCompileFunctionalTest` near `aggregate enum generation participates in domain compileKotlin`:

```kotlin
@Test
fun `enum manifest only generation participates in domain compileKotlin`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-enum-manifest-domain-compile")
    FunctionalFixtureSupport.copyCompileFixture(projectDir, "enum-manifest-compile-sample")

    val compileResult = FunctionalFixtureSupport
        .runner(projectDir, ":demo-domain:compileKotlin")
        .build()
    val generatedSharedEnum = projectDir.resolve(
        generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/shared/enums/Status.kt")
    ).readText()

    assertGeneratedFilesExist(
        projectDir,
        generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/shared/enums/Status.kt"),
    )
    assertTrue(generatedSharedEnum.contains("enum class Status"))
    assertTrue(generatedSharedEnum.contains("DRAFT(0)"))
    assertTrue(generatedSharedEnum.contains("PUBLISHED(1)"))
    assertTrue(generatedSharedEnum.contains("class Converter : AttributeConverter<Status, Int>"))
    assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
}
```

- [ ] **Step 5: Run focused Gradle tests and verify failure**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest.enum manifest only config carries domain module and source options" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginTest.generated source module roles include generated source families" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.enum manifest only generation participates in domain compileKotlin"
```

Expected: FAIL before implementation because enum manifest does not currently add the domain module or generated-source role.

- [ ] **Step 6: Include domain module for enum manifest**

In `Cap4kProjectConfigFactory.buildModules`, change:

```kotlin
if (sources.valueObjectManifestConfigured) {
    put("domain", extension.project.domainModulePath.required("project.domainModulePath"))
}
```

to:

```kotlin
if (sources.enumManifestConfigured || sources.valueObjectManifestConfigured) {
    put("domain", extension.project.domainModulePath.required("project.domainModulePath"))
}
```

- [ ] **Step 7: Register enum manifest generated source role**

In `PipelinePlugin.generatedSourceModuleRoles`, add this block before the aggregate block:

```kotlin
if ("enum-manifest" in config.sources) {
    roles += "domain"
}
```

The function should still filter roles with `role in config.modules`.

- [ ] **Step 8: Run focused Gradle tests and module test**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest.enum manifest only config carries domain module and source options" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginTest.generated source module roles include generated source families" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.enum manifest only generation participates in domain compileKotlin"
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test
```

Expected: both commands end with `BUILD SUCCESSFUL`.

---

### Task 5: Synchronize Active Docs And Skills With Issue #92 Contracts

**Files:**
- Modify: `docs/public/reference/generator-dsl.md`
- Modify: `docs/superpowers/capability-matrix.md`
- Modify: `docs/public/authoring/generator/input-sources.md`
- Modify: `skills/cap4k-generation/references/sources/design-json.md`
- Modify: `skills/cap4k-generation/references/sources/value-object-manifest.md`
- Modify: `skills/cap4k-generation/workflows/generate-from-design.md`

- [ ] **Step 1: Remove stale non-DB `enabled` examples from generator DSL docs**

In `docs/public/reference/generator-dsl.md`, update the `sources` example so only DB keeps `enabled`:

```kotlin
sources {
    designJson { files.from("design/design.json") }
    db { enabled.set(true); url.set("jdbc:..."); schema.set("PUBLIC") }
    irAnalysis { inputDirs.from("path/to/ir-analysis") }
}
```

Update the source table rows to use these public options:

```markdown
| `designJson` | `files`, `manifestFile` | `cap4kPlan` / `cap4kGenerate` |
| `db` | `enabled`, `url`, `username`, `password`, `schema`, `includeTables`, `excludeTables` | `cap4kPlan` / `cap4kGenerate` |
| `irAnalysis` | `inputDirs` | `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` |
```

Update the generator example to remove `flow` and `drawingBoard` switches:

```kotlin
generators {
    aggregate { }
    aggregateProjection { }
}
```

Replace the explanatory sentence:

```markdown
启用 `sources.designJson` 即可进入 design family planning，不再需要 `designCommand`、`designQuery`、`designDomainEvent` 等公开 generator switch。`aggregate` 和 `aggregateProjection` 由对应 `generators {}` block presence 参与；`flow` 和 `drawingBoard` 是 observation outputs，由 `sources.irAnalysis.inputDirs` 驱动。
```

Update all remaining examples in this file from:

```kotlin
sources { designJson { enabled.set(true); files.from("design/design.json") } }
sources { irAnalysis { enabled.set(true); inputDirs.from("path/to/ir-analysis") } }
generators { flow { enabled.set(true) } }
generators { drawingBoard { enabled.set(true) } }
generators { aggregate { enabled.set(true) } }
```

to:

```kotlin
sources { designJson { files.from("design/design.json") } }
sources { irAnalysis { inputDirs.from("path/to/ir-analysis") } }
generators { aggregate { } }
```

- [ ] **Step 2: Update integration-event public contract docs**

In `docs/superpowers/capability-matrix.md`, replace the `design.integration_event` row text with:

```markdown
| `design.integration_event` | `design` | `implemented` | Design JSON supports `integration_event` through `artifacts[{ family: "integration-event", variant: "inbound" | "outbound" }]` plus optional `integration-subscriber` for inbound subscribers. Event contracts live under application integration packages; inbound variants generate Spring `@EventListener` subscribers, and outbound variants generate contracts only. | `unit`, `functional`, `compile` | design integration event parser/canonical/planner/template tests; code-analysis extraction tests; `design-integration-event-compile-sample` | `yes` | No MQ-specific generators and no `EventSubscriber<T>` subscriber skeletons in this slice. |
```

- [ ] **Step 3: Remove public `entity` guidance from authoring docs**

In `docs/public/authoring/generator/input-sources.md`, replace any wording that says `entity` is a request field derived from `aggregates[0]` with:

```markdown
`domain_event` entries declare aggregate ownership through `aggregates`. Exactly one aggregate owner is required for generated domain events. The generated Kotlin event may carry an aggregate instance as a runtime constructor parameter, but `entity` is not a public design-json or recovery metadata field.
```

- [ ] **Step 4: Remove public `entity` guidance from generation skill docs**

In `skills/cap4k-generation/references/sources/design-json.md`, replace the domain-event guidance with:

```markdown
- `domain_event` uses `aggregates` for aggregate ownership. Declare exactly one aggregate name. Do not emit `entity`; it is not a public design-json or recovered metadata field.
```

- [ ] **Step 5: Align value-object manifest aggregate cardinality**

In `skills/cap4k-generation/references/sources/value-object-manifest.md`, replace the aggregate guidance with:

```markdown
- `aggregates`: omit for shared value objects; set exactly one aggregate name for aggregate-owned value objects.
```

- [ ] **Step 6: Update design generation workflow checklist**

In `skills/cap4k-generation/workflows/generate-from-design.md`, replace the old checklist:

```markdown
4. Check common fields: package, name, desc, aggregates, requestFields, and responseFields.
5. For `integration_event`, require role, eventName, at least one request field, and empty response fields.
6. Check tag-specific rules: page traits, persisted domain events, integration event roles, domain-service/saga skeleton ownership, and manifest path safety.
```

with:

```markdown
4. Check common fields: tag, package, name, description, aggregates, fields, resultFields, and artifacts.
5. For `integration_event`, require eventName, at least one field, empty resultFields, and an `integration-event` artifact variant of `inbound` or `outbound`; add `integration-subscriber` only for inbound subscribers.
6. Check tag-specific rules: page/list artifacts, persisted domain events, integration event variants, domain-service/saga skeleton ownership, and manifest path safety.
```

- [ ] **Step 7: Run active stale scans**

Run:

```powershell
rg -n "\btraits\b|\brole\b|ValueObjectScope|IntegrationEventRole|requestFields|responseFields|\bdesc\b|design-domain-event-handler|design-integration-event-subscriber|design-command|design-query|SourceConfig\(enabled|GeneratorConfig\(enabled|enabledSourceIds\(|enabledGeneratorIds\(" -g "cap4k-plugin-*/**" -g "ddd-core/**" -g "cap4k-ddd-starter/**" -g "docs/public/**" -g "docs/superpowers/capability-matrix.md" -g "skills/cap4k-generation/**" -g "skills/cap4k-modeling/**"
```

Expected:
- No active public-contract references to old `role`, `requestFields`, `responseFields`, `traits`, or `desc`.
- Remaining `desc` hits are allowed only for enum item description input or historical docs not included in this active scan.
- Remaining `enabled` hits are allowed only for DB source, bootstrap, aggregate internal marker policies, or historical plans/specs outside this active scan.

Run:

```powershell
rg -n "@Aggregate\(|Aggregate\.TYPE_|domain\.aggregate\.annotation\.Aggregate|core\.archinfo|ddd\.archinfo" -g "cap4k-plugin-*/**" -g "ddd-core/**" -g "cap4k-ddd-starter/**" -g "docs/public/**" -g "skills/cap4k-generation/**" -g "skills/cap4k-modeling/**"
```

Expected:
- No active generated template/source guidance reintroduces `@Aggregate`.
- Runtime `ddd-core/.../archinfo` files may still physically exist only if issue #92 explicitly left runtime compatibility out of scope; if active source/test/template paths reference them for generation recovery, fix those references before continuing.

---

### Task 6: Whitespace Cleanup And Verification

**Files:**
- Modify: `cap4k-plugin-pipeline-source-ir-analysis/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProvider.kt`
- No code changes unless earlier tasks require them.

- [ ] **Step 1: Remove trailing blank line**

Ensure `IrAnalysisSourceProvider.kt` ends with exactly one newline after the final declaration. If the final lines look like:

```kotlin
private data class EdgeKey(...)

```

remove the extra blank line so the file ends immediately after the final declaration plus one newline.

- [ ] **Step 2: Run whitespace check**

Run:

```powershell
git diff --check origin/master...HEAD
```

Expected: no output and exit code 0.

- [ ] **Step 3: Run focused verification**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test `
  :cap4k-plugin-pipeline-source-design-json:test `
  :cap4k-plugin-pipeline-core:test `
  :cap4k-plugin-pipeline-gradle:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run generator regression verification**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test `
  :cap4k-plugin-pipeline-renderer-pebble:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run full repository verification**

Run:

```powershell
.\gradlew.bat test
```

Expected: `BUILD SUCCESSFUL`.

If this command is blocked by environment, network, or time, record:

```markdown
- Blocked command: `.\gradlew.bat test`
- Failure mode: <timeout | network | auth | daemon crash | exact error summary>
- Verified commands that passed:
  - `<command>`: BUILD SUCCESSFUL
- Residual risk:
  - Full suite not re-run after review follow-up; focused modules passed.
```

- [ ] **Step 6: Commit review follow-up**

Run:

```powershell
git status --short --branch
git add -A
git diff --cached --stat
git commit -m "fix: address issue 92 review follow-up"
git push
```

Expected:
- Commit succeeds.
- Push updates PR #95 branch.

- [ ] **Step 7: Update PR and issue comments**

Update PR #95 with a short comment:

```markdown
Review follow-up pushed:

- Removed remaining public `entity` recovery/input leaks.
- Made `types.enumManifest` participate as an independent domain generated-source input.
- Switched canonical authoring tag handling to fail fast.
- Synchronized active DSL/docs/skills guidance and removed stale non-DB `enabled` examples.
- Verification:
  - `<focused command>`: BUILD SUCCESSFUL
  - `.\gradlew.bat test`: BUILD SUCCESSFUL
```

Update issue #92 with the same evidence and keep it open:

```markdown
PR #95 received a review follow-up commit. Issue remains open until PR merge, required release, and downstream verification are complete.
```

Do not close issue #92.

---

## Self-Review Checklist

- Spec coverage: all Reviewer A/B findings are mapped to a task.
- No new public generator switch is introduced for enum manifest.
- Design JSON old-field handling remains fail-fast with no compatibility fallback.
- Observation output policy is not widened for normal checked-in/generated source conflicts.
- Docs and skills are treated as active public guidance, not historical records.
- Tests are written before production edits in each code task.
- Full verification includes focused modules, whitespace check, stale scans, and full `.\gradlew.bat test` when environment permits.
