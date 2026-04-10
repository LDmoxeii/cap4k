# Cap4k Pipeline Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first working vertical slice of the redesigned cap4k pipeline: fixed-stage core, repository DSL, `design-json` + `ksp-metadata` sources, command/query planning, Pebble rendering, and `cap4kPlan` / `cap4kGenerate` Gradle tasks.

**Architecture:** This plan creates new `cap4k-plugin-pipeline-*` modules alongside the legacy modules so the new architecture can be proven without rewriting old tasks in place. The slice is intentionally narrow: it migrates only the `design-json` + `ksp-metadata` path and only `command` / `query` output planning, leaving DB, IR, aggregate, drawing-board, and flow migration for follow-up plans after the kernel is stable.

**Tech Stack:** Kotlin 2.2, Gradle plugin API, Gradle TestKit, Pebble, Gson, JUnit 5

---

## File Structure

### Root Build Wiring

- Modify: `settings.gradle.kts`
  - include all new `cap4k-plugin-pipeline-*` modules

### API Contracts

- Create: `cap4k-plugin-pipeline-api/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`
- Create: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Create: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineContracts.kt`
- Create: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfigTest.kt`

### Pipeline Core

- Create: `cap4k-plugin-pipeline-core/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunner.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/FilesystemArtifactExporter.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/NoopArtifactExporter.kt`
- Create: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt`
- Create: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

### Renderer Abstraction

- Create: `cap4k-plugin-pipeline-renderer-api/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-renderer-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/api/ArtifactRenderer.kt`
- Create: `cap4k-plugin-pipeline-renderer-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/api/TemplateResolver.kt`

### Pebble Renderer

- Create: `cap4k-plugin-pipeline-renderer-pebble/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRenderer.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PresetTemplateResolver.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/command.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

### Source Modules

- Create: `cap4k-plugin-pipeline-source-design-json/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`
- Create: `cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt`
- Create: `cap4k-plugin-pipeline-source-design-json/src/test/resources/fixtures/design/design.json`
- Create: `cap4k-plugin-pipeline-source-ksp-metadata/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-source-ksp-metadata/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ksp/KspMetadataSourceProvider.kt`
- Create: `cap4k-plugin-pipeline-source-ksp-metadata/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/ksp/KspMetadataSourceProviderTest.kt`
- Create: `cap4k-plugin-pipeline-source-ksp-metadata/src/test/resources/fixtures/metadata/aggregate-Order.json`

### Generator Module

- Create: `cap4k-plugin-pipeline-generator-design/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt`

### Gradle Adapter

- Create: `cap4k-plugin-pipeline-gradle/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelineExtension.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kPlanTask.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kGenerateTask.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/domain/build/generated/ksp/main/resources/metadata/aggregate-Order.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/command.kt.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query.kt.peb`

## Scope Note

This plan intentionally stops after the first end-to-end vertical slice. Separate follow-up plans must cover:

- `db` source migration
- `ir-analysis` source migration
- aggregate generator migration
- drawing-board generator migration
- flow generator migration

### Task 1: Add Module Wiring And API Contracts

**Files:**
- Modify: `settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-api/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`
- Create: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Create: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineContracts.kt`
- Test: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfigTest.kt`

- [ ] **Step 1: Write the failing API contract test**

```kotlin
package com.only4.cap4k.plugin.pipeline.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProjectConfigTest {

    @Test
    fun `stores enabled source and generator ids with module mapping`() {
        val config = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = mapOf(
                "domain" to ":demo-domain",
                "application" to ":demo-application"
            ),
            sources = mapOf(
                "design-json" to SourceConfig(
                    enabled = true,
                    options = mapOf("files" to listOf("design/design.json"))
                ),
                "ksp-metadata" to SourceConfig(enabled = false)
            ),
            generators = mapOf(
                "design" to GeneratorConfig(enabled = true),
                "aggregate" to GeneratorConfig(enabled = false)
            ),
            templates = TemplateConfig(
                preset = "ddd-default",
                overrideDirs = listOf("codegen/templates"),
                conflictPolicy = ConflictPolicy.SKIP
            )
        )

        assertEquals(setOf("design-json"), config.enabledSourceIds())
        assertEquals(setOf("design"), config.enabledGeneratorIds())
        assertEquals(":demo-domain", config.modules["domain"])
        assertEquals(ConflictPolicy.SKIP, config.templates.conflictPolicy)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.ProjectConfigTest"`
Expected: FAIL with `Project 'cap4k-plugin-pipeline-api' not found` or missing class compilation errors.

- [ ] **Step 3: Write the minimal API module and contract code**

```kotlin
// settings.gradle.kts
include("cap4k-plugin-pipeline-api")
include("cap4k-plugin-pipeline-core")
include("cap4k-plugin-pipeline-renderer-api")
include("cap4k-plugin-pipeline-renderer-pebble")
include("cap4k-plugin-pipeline-source-design-json")
include("cap4k-plugin-pipeline-source-ksp-metadata")
include("cap4k-plugin-pipeline-generator-design")
include("cap4k-plugin-pipeline-gradle")
```

```kotlin
// cap4k-plugin-pipeline-api/build.gradle.kts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
```

```kotlin
// cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt
package com.only4.cap4k.plugin.pipeline.api

data class ProjectConfig(
    val basePackage: String,
    val layout: ProjectLayout,
    val modules: Map<String, String>,
    val sources: Map<String, SourceConfig>,
    val generators: Map<String, GeneratorConfig>,
    val templates: TemplateConfig,
) {
    fun enabledSourceIds(): Set<String> = sources.filterValues { it.enabled }.keys
    fun enabledGeneratorIds(): Set<String> = generators.filterValues { it.enabled }.keys
}

enum class ProjectLayout {
    SINGLE_MODULE,
    MULTI_MODULE
}

data class SourceConfig(
    val enabled: Boolean,
    val options: Map<String, Any?> = emptyMap(),
)

data class GeneratorConfig(
    val enabled: Boolean,
    val options: Map<String, Any?> = emptyMap(),
)

data class TemplateConfig(
    val preset: String,
    val overrideDirs: List<String>,
    val conflictPolicy: ConflictPolicy,
)

enum class ConflictPolicy {
    SKIP,
    OVERWRITE,
    FAIL
}
```

```kotlin
// cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt
package com.only4.cap4k.plugin.pipeline.api

data class FieldModel(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null,
)

data class DesignSpecEntry(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String,
    val aggregates: List<String>,
    val requestFields: List<FieldModel>,
    val responseFields: List<FieldModel>,
)

data class AggregateMetadataRecord(
    val aggregateName: String,
    val rootQualifiedName: String,
    val rootPackageName: String,
    val rootClassName: String,
)

sealed interface SourceSnapshot {
    val id: String
}

data class DesignSpecSnapshot(
    override val id: String = "design-json",
    val entries: List<DesignSpecEntry>,
) : SourceSnapshot

data class KspMetadataSnapshot(
    override val id: String = "ksp-metadata",
    val aggregates: List<AggregateMetadataRecord>,
) : SourceSnapshot

enum class RequestKind {
    COMMAND,
    QUERY
}

data class RequestModel(
    val kind: RequestKind,
    val packageName: String,
    val typeName: String,
    val description: String,
    val aggregateName: String?,
    val aggregatePackageName: String?,
    val requestFields: List<FieldModel>,
    val responseFields: List<FieldModel>,
)

data class CanonicalModel(
    val requests: List<RequestModel> = emptyList(),
)

data class ArtifactPlanItem(
    val generatorId: String,
    val moduleRole: String,
    val templateId: String,
    val outputPath: String,
    val context: Map<String, Any?>,
    val conflictPolicy: ConflictPolicy,
)

data class RenderedArtifact(
    val outputPath: String,
    val content: String,
    val conflictPolicy: ConflictPolicy,
)

data class PipelineResult(
    val planItems: List<ArtifactPlanItem>,
    val renderedArtifacts: List<RenderedArtifact>,
    val writtenPaths: List<String>,
    val warnings: List<String>,
)
```

```kotlin
// cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineContracts.kt
package com.only4.cap4k.plugin.pipeline.api

interface SourceProvider {
    val id: String
    fun collect(config: ProjectConfig): SourceSnapshot
}

interface GeneratorProvider {
    val id: String
    fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem>
}

interface PipelineRunner {
    fun run(config: ProjectConfig): PipelineResult
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.ProjectConfigTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts \
        cap4k-plugin-pipeline-api/build.gradle.kts \
        cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt \
        cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt \
        cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineContracts.kt \
        cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfigTest.kt
git commit -m "feat: add pipeline api contracts"
```

### Task 2: Add Core Pipeline Runner

**Files:**
- Create: `cap4k-plugin-pipeline-core/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-renderer-api/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-renderer-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/api/ArtifactRenderer.kt`
- Create: `cap4k-plugin-pipeline-renderer-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/api/TemplateResolver.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunner.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/FilesystemArtifactExporter.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/NoopArtifactExporter.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt`

- [ ] **Step 1: Write the failing pipeline-runner test**

```kotlin
package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.*
import com.only4.cap4k.plugin.pipeline.renderer.api.ArtifactRenderer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DefaultPipelineRunnerTest {

    @Test
    fun `runs collect normalize plan render export in order`() {
        val calls = mutableListOf<String>()
        val source = object : SourceProvider {
            override val id: String = "design-json"
            override fun collect(config: ProjectConfig): SourceSnapshot {
                calls += "collect"
                return DesignSpecSnapshot(entries = emptyList())
            }
        }
        val generator = object : GeneratorProvider {
            override val id: String = "design"
            override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
                calls += "plan"
                return listOf(
                    ArtifactPlanItem(
                        generatorId = "design",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt",
                        context = mapOf("Query" to "FindOrderQry"),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                )
            }
        }
        val assembler = object : CanonicalAssembler {
            override fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalModel {
                calls += "normalize"
                return CanonicalModel()
            }
        }
        val renderer = object : ArtifactRenderer {
            override fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact> {
                calls += "render"
                return listOf(
                    RenderedArtifact(
                        outputPath = planItems.single().outputPath,
                        content = "class FindOrderQry",
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                )
            }
        }
        val exportRoot = Files.createTempDirectory("pipeline-runner-test")
        val exporter = FilesystemArtifactExporter(exportRoot)
        val runner = DefaultPipelineRunner(
            sources = listOf(source),
            generators = listOf(generator),
            assembler = assembler,
            renderer = renderer,
            exporter = exporter
        )

        val result = runner.run(
            ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = mapOf("application" to ":demo-application"),
                sources = mapOf("design-json" to SourceConfig(enabled = true)),
                generators = mapOf("design" to GeneratorConfig(enabled = true)),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP)
            )
        )

        assertEquals(listOf("collect", "normalize", "plan", "render"), calls)
        assertEquals(1, result.writtenPaths.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultPipelineRunnerTest"`
Expected: FAIL with missing module or unresolved `DefaultPipelineRunner`.

- [ ] **Step 3: Write the minimal core pipeline implementation**

```kotlin
// cap4k-plugin-pipeline-renderer-api/build.gradle.kts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":cap4k-plugin-pipeline-api"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
```

```kotlin
// cap4k-plugin-pipeline-renderer-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/api/ArtifactRenderer.kt
package com.only4.cap4k.plugin.pipeline.renderer.api

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact

interface ArtifactRenderer {
    fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact>
}
```

```kotlin
// cap4k-plugin-pipeline-renderer-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/api/TemplateResolver.kt
package com.only4.cap4k.plugin.pipeline.renderer.api

interface TemplateResolver {
    fun resolve(templateId: String): String
}
```

```kotlin
// cap4k-plugin-pipeline-core/build.gradle.kts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":cap4k-plugin-pipeline-api"))
    implementation(project(":cap4k-plugin-pipeline-renderer-api"))

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
```

```kotlin
// cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt
package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SourceSnapshot

interface CanonicalAssembler {
    fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalModel
}

class DefaultCanonicalAssembler : CanonicalAssembler {
    override fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalModel {
        return CanonicalModel()
    }
}
```

```kotlin
// cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/FilesystemArtifactExporter.kt
package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class FilesystemArtifactExporter(
    private val root: Path,
) : ArtifactExporter {
    override fun export(artifacts: List<RenderedArtifact>): List<String> {
        return artifacts.map { artifact ->
            val target = root.resolve(artifact.outputPath)
            target.parent?.createDirectories()
            target.writeText(artifact.content)
            target.toString()
        }
    }
}
```

```kotlin
// cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/NoopArtifactExporter.kt
package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact

interface ArtifactExporter {
    fun export(artifacts: List<RenderedArtifact>): List<String>
}

class NoopArtifactExporter : ArtifactExporter {
    override fun export(artifacts: List<RenderedArtifact>): List<String> = emptyList()
}
```

```kotlin
// cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunner.kt
package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.*
import com.only4.cap4k.plugin.pipeline.renderer.api.ArtifactRenderer

class DefaultPipelineRunner(
    private val sources: List<SourceProvider>,
    private val generators: List<GeneratorProvider>,
    private val assembler: CanonicalAssembler,
    private val renderer: ArtifactRenderer,
    private val exporter: ArtifactExporter,
) : PipelineRunner {

    override fun run(config: ProjectConfig): PipelineResult {
        val snapshots = sources
            .filter { config.sources[it.id]?.enabled == true }
            .map { it.collect(config) }

        val model = assembler.assemble(config, snapshots)
        val planItems = generators
            .filter { config.generators[it.id]?.enabled == true }
            .flatMap { it.plan(config, model) }

        val renderedArtifacts = renderer.render(planItems, config)
        val writtenPaths = exporter.export(renderedArtifacts)

        return PipelineResult(
            planItems = planItems,
            renderedArtifacts = renderedArtifacts,
            writtenPaths = writtenPaths,
            warnings = emptyList()
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultPipelineRunnerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-core/build.gradle.kts \
        cap4k-plugin-pipeline-renderer-api/build.gradle.kts \
        cap4k-plugin-pipeline-renderer-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/api/ArtifactRenderer.kt \
        cap4k-plugin-pipeline-renderer-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/api/TemplateResolver.kt \
        cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunner.kt \
        cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt \
        cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/FilesystemArtifactExporter.kt \
        cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/NoopArtifactExporter.kt \
        cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt
git commit -m "feat: add pipeline core runner"
```

### Task 3: Add Design JSON Source

**Files:**
- Create: `cap4k-plugin-pipeline-source-design-json/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`
- Create: `cap4k-plugin-pipeline-source-design-json/src/test/resources/fixtures/design/design.json`
- Test: `cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt`

- [ ] **Step 1: Write the failing design-json source test**

```kotlin
package com.only4.cap4k.plugin.pipeline.source.designjson

import com.only4.cap4k.plugin.pipeline.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class DesignJsonSourceProviderTest {

    @Test
    fun `loads command and query entries from configured files`() {
        val fixture = File("src/test/resources/fixtures/design/design.json").absolutePath
        val provider = DesignJsonSourceProvider()

        val snapshot = provider.collect(
            ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = mapOf(
                    "design-json" to SourceConfig(
                        enabled = true,
                        options = mapOf("files" to listOf(fixture))
                    )
                ),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP)
            )
        ) as DesignSpecSnapshot

        assertEquals(2, snapshot.entries.size)
        assertEquals("cmd", snapshot.entries.first().tag)
        assertEquals("FindOrder", snapshot.entries.last().name)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :cap4k-plugin-pipeline-source-design-json:test --tests "com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonSourceProviderTest"`
Expected: FAIL with missing module or unresolved `DesignJsonSourceProvider`.

- [ ] **Step 3: Write the minimal design-json source implementation**

```json
// cap4k-plugin-pipeline-source-design-json/src/test/resources/fixtures/design/design.json
[
  {
    "tag": "cmd",
    "package": "order.submit",
    "name": "SubmitOrder",
    "desc": "submit order command",
    "aggregates": ["Order"],
    "requestFields": [
      { "name": "orderId", "type": "Long" }
    ],
    "responseFields": [
      { "name": "accepted", "type": "Boolean" }
    ]
  },
  {
    "tag": "qry",
    "package": "order.read",
    "name": "FindOrder",
    "desc": "find order query",
    "aggregates": ["Order"],
    "requestFields": [
      { "name": "orderId", "type": "Long" }
    ],
    "responseFields": [
      { "name": "status", "type": "String" }
    ]
  }
]
```

```kotlin
// cap4k-plugin-pipeline-source-design-json/build.gradle.kts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":cap4k-plugin-pipeline-api"))
    implementation(libs.gson)

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
```

```kotlin
// cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt
package com.only4.cap4k.plugin.pipeline.source.designjson

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.*
import java.io.File

class DesignJsonSourceProvider : SourceProvider {
    override val id: String = "design-json"

    override fun collect(config: ProjectConfig): SourceSnapshot {
        val files = config.sources[id]?.options?.get("files") as? List<*> ?: emptyList<Any>()
        val entries = files
            .map { File(it.toString()) }
            .flatMap { parseFile(it) }
        return DesignSpecSnapshot(entries = entries)
    }

    private fun parseFile(file: File): List<DesignSpecEntry> {
        val root = JsonParser.parseString(file.readText()).asJsonArray
        return root.map { json ->
            val obj = json.asJsonObject
            DesignSpecEntry(
                tag = obj.get("tag").asString,
                packageName = obj.get("package").asString,
                name = obj.get("name").asString,
                description = obj.get("desc")?.asString.orEmpty(),
                aggregates = obj.getAsJsonArray("aggregates")?.map { it.asString }.orEmpty(),
                requestFields = parseFields(obj.getAsJsonArray("requestFields")),
                responseFields = parseFields(obj.getAsJsonArray("responseFields"))
            )
        }
    }

    private fun parseFields(array: JsonArray?): List<FieldModel> {
        if (array == null) return emptyList()
        return array.map { element ->
            val obj = element as JsonObject
            FieldModel(
                name = obj.get("name").asString,
                type = obj.get("type")?.asString ?: "kotlin.String",
                nullable = obj.get("nullable")?.asBoolean ?: false,
                defaultValue = obj.get("defaultValue")?.asString
            )
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew :cap4k-plugin-pipeline-source-design-json:test --tests "com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonSourceProviderTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-source-design-json/build.gradle.kts \
        cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt \
        cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt \
        cap4k-plugin-pipeline-source-design-json/src/test/resources/fixtures/design/design.json
git commit -m "feat: add design json source provider"
```

### Task 4: Add KSP Metadata Source

**Files:**
- Create: `cap4k-plugin-pipeline-source-ksp-metadata/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-source-ksp-metadata/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ksp/KspMetadataSourceProvider.kt`
- Create: `cap4k-plugin-pipeline-source-ksp-metadata/src/test/resources/fixtures/metadata/aggregate-Order.json`
- Test: `cap4k-plugin-pipeline-source-ksp-metadata/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/ksp/KspMetadataSourceProviderTest.kt`

- [ ] **Step 1: Write the failing ksp-metadata source test**

```kotlin
package com.only4.cap4k.plugin.pipeline.source.ksp

import com.only4.cap4k.plugin.pipeline.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class KspMetadataSourceProviderTest {

    @Test
    fun `reads aggregate root metadata from aggregate files`() {
        val fixtureDir = File("src/test/resources/fixtures/metadata").absolutePath
        val provider = KspMetadataSourceProvider()

        val snapshot = provider.collect(
            ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = mapOf(
                    "ksp-metadata" to SourceConfig(
                        enabled = true,
                        options = mapOf("inputDir" to fixtureDir)
                    )
                ),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP)
            )
        ) as KspMetadataSnapshot

        assertEquals(1, snapshot.aggregates.size)
        assertEquals("Order", snapshot.aggregates.single().aggregateName)
        assertEquals("com.acme.demo.domain.aggregates.order.Order", snapshot.aggregates.single().rootQualifiedName)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :cap4k-plugin-pipeline-source-ksp-metadata:test --tests "com.only4.cap4k.plugin.pipeline.source.ksp.KspMetadataSourceProviderTest"`
Expected: FAIL with missing module or unresolved `KspMetadataSourceProvider`.

- [ ] **Step 3: Write the minimal ksp-metadata source implementation**

```json
// cap4k-plugin-pipeline-source-ksp-metadata/src/test/resources/fixtures/metadata/aggregate-Order.json
{
  "aggregateName": "Order",
  "aggregateRoot": {
    "className": "Order",
    "qualifiedName": "com.acme.demo.domain.aggregates.order.Order",
    "packageName": "com.acme.demo.domain.aggregates.order"
  }
}
```

```kotlin
// cap4k-plugin-pipeline-source-ksp-metadata/build.gradle.kts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":cap4k-plugin-pipeline-api"))
    implementation(libs.gson)

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
```

```kotlin
// cap4k-plugin-pipeline-source-ksp-metadata/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ksp/KspMetadataSourceProvider.kt
package com.only4.cap4k.plugin.pipeline.source.ksp

import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.*
import java.io.File

class KspMetadataSourceProvider : SourceProvider {
    override val id: String = "ksp-metadata"

    override fun collect(config: ProjectConfig): SourceSnapshot {
        val inputDir = File(config.sources[id]?.options?.get("inputDir").toString())
        val aggregates = inputDir
            .listFiles { file -> file.isFile && file.name.startsWith("aggregate-") && file.name.endsWith(".json") }
            ?.sortedBy { it.name }
            ?.map { parseAggregateFile(it) }
            .orEmpty()
        return KspMetadataSnapshot(aggregates = aggregates)
    }

    private fun parseAggregateFile(file: File): AggregateMetadataRecord {
        val root = JsonParser.parseString(file.readText()).asJsonObject
        val aggregateRoot = root.getAsJsonObject("aggregateRoot")
        return AggregateMetadataRecord(
            aggregateName = root.get("aggregateName").asString,
            rootQualifiedName = aggregateRoot.get("qualifiedName").asString,
            rootPackageName = aggregateRoot.get("packageName").asString,
            rootClassName = aggregateRoot.get("className").asString
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew :cap4k-plugin-pipeline-source-ksp-metadata:test --tests "com.only4.cap4k.plugin.pipeline.source.ksp.KspMetadataSourceProviderTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-source-ksp-metadata/build.gradle.kts \
        cap4k-plugin-pipeline-source-ksp-metadata/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ksp/KspMetadataSourceProvider.kt \
        cap4k-plugin-pipeline-source-ksp-metadata/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/ksp/KspMetadataSourceProviderTest.kt \
        cap4k-plugin-pipeline-source-ksp-metadata/src/test/resources/fixtures/metadata/aggregate-Order.json
git commit -m "feat: add ksp metadata source provider"
```

### Task 5: Add Canonical Assembly For Command And Query Requests

**Files:**
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Write the failing canonical-assembly test**

```kotlin
package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultCanonicalAssemblerTest {

    @Test
    fun `maps design entries and ksp aggregates into canonical requests`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = mapOf("application" to ":demo-application"),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP)
            ),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "cmd",
                            packageName = "order.submit",
                            name = "SubmitOrder",
                            description = "submit order",
                            aggregates = listOf("Order"),
                            requestFields = listOf(FieldModel("orderId", "Long")),
                            responseFields = listOf(FieldModel("accepted", "Boolean"))
                        ),
                        DesignSpecEntry(
                            tag = "qry",
                            packageName = "order.read",
                            name = "FindOrder",
                            description = "find order",
                            aggregates = listOf("Order"),
                            requestFields = listOf(FieldModel("orderId", "Long")),
                            responseFields = listOf(FieldModel("status", "String"))
                        )
                    )
                ),
                KspMetadataSnapshot(
                    aggregates = listOf(
                        AggregateMetadataRecord(
                            aggregateName = "Order",
                            rootQualifiedName = "com.acme.demo.domain.aggregates.order.Order",
                            rootPackageName = "com.acme.demo.domain.aggregates.order",
                            rootClassName = "Order"
                        )
                    )
                )
            )
        )

        assertEquals(2, model.requests.size)
        assertEquals(RequestKind.COMMAND, model.requests.first().kind)
        assertEquals("com.acme.demo.domain.aggregates.order", model.requests.first().aggregatePackageName)
        assertEquals(RequestKind.QUERY, model.requests.last().kind)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"`
Expected: FAIL because `DefaultCanonicalAssembler` returns an empty model.

- [ ] **Step 3: Write the minimal canonical assembly logic**

```kotlin
// cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt
package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.*

class DefaultCanonicalAssembler : CanonicalAssembler {
    override fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalModel {
        val designSnapshot = snapshots.filterIsInstance<DesignSpecSnapshot>().firstOrNull()
            ?: return CanonicalModel()
        val aggregateLookup = snapshots
            .filterIsInstance<KspMetadataSnapshot>()
            .flatMap { it.aggregates }
            .associateBy { it.aggregateName }

        val requests = designSnapshot.entries.mapNotNull { entry ->
            val kind = when (entry.tag.lowercase()) {
                "cmd", "command" -> RequestKind.COMMAND
                "qry", "query" -> RequestKind.QUERY
                else -> null
            } ?: return@mapNotNull null

            val aggregate = entry.aggregates.firstOrNull()?.let { aggregateLookup[it] }
            RequestModel(
                kind = kind,
                packageName = entry.packageName,
                typeName = when (kind) {
                    RequestKind.COMMAND -> "${entry.name}Cmd"
                    RequestKind.QUERY -> "${entry.name}Qry"
                },
                description = entry.description,
                aggregateName = aggregate?.aggregateName,
                aggregatePackageName = aggregate?.rootPackageName,
                requestFields = entry.requestFields,
                responseFields = entry.responseFields
            )
        }

        return CanonicalModel(requests = requests)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt \
        cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: assemble canonical request model"
```

### Task 6: Add Renderer API And Pebble Default Templates

**Files:**
- Create: `cap4k-plugin-pipeline-renderer-pebble/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PresetTemplateResolver.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRenderer.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/command.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb`
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Write the failing Pebble renderer test**

```kotlin
package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.only4.cap4k.plugin.pipeline.api.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class PebbleArtifactRendererTest {

    @Test
    fun `prefers override template over preset template`() {
        val overrideDir = Files.createTempDirectory("renderer-override")
        Files.createDirectories(overrideDir.resolve("design"))
        Files.writeString(
            overrideDir.resolve("design/query.kt.peb"),
            "package {{ packageName }}\nclass {{ typeName }}Override"
        )

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            listOf(
                ArtifactPlanItem(
                    generatorId = "design",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.queries",
                        "typeName" to "FindOrderQry"
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", listOf(overrideDir.toString()), ConflictPolicy.SKIP)
            )
        )

        assertTrue(rendered.single().content.contains("FindOrderQryOverride"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"`
Expected: FAIL with missing renderer modules or unresolved classes.

- [ ] **Step 3: Write the minimal renderer modules**

// cap4k-plugin-pipeline-renderer-pebble/build.gradle.kts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":cap4k-plugin-pipeline-api"))
    implementation(project(":cap4k-plugin-pipeline-renderer-api"))
    implementation(libs.pebble)

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
```

```kotlin
// cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PresetTemplateResolver.kt
package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.only4.cap4k.plugin.pipeline.renderer.api.TemplateResolver
import java.io.File

class PresetTemplateResolver(
    private val preset: String,
    private val overrideDirs: List<String>,
) : TemplateResolver {
    override fun resolve(templateId: String): String {
        overrideDirs.forEach { dir ->
            val file = File(dir, templateId)
            if (file.exists()) return file.readText()
        }
        val resourcePath = "presets/$preset/$templateId"
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: error("Template not found: $resourcePath")
        return stream.bufferedReader().use { it.readText() }
    }
}
```

```kotlin
// cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRenderer.kt
package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact
import com.only4.cap4k.plugin.pipeline.renderer.api.ArtifactRenderer
import com.only4.cap4k.plugin.pipeline.renderer.api.TemplateResolver
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.StringLoader
import java.io.StringWriter

class PebbleArtifactRenderer(
    private val templateResolver: TemplateResolver,
) : ArtifactRenderer {
    private val engine = PebbleEngine.Builder()
        .loader(StringLoader())
        .build()

    override fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact> {
        return planItems.map { item ->
            val template = engine.getLiteralTemplate(templateResolver.resolve(item.templateId))
            val writer = StringWriter()
            template.evaluate(writer, item.context)
            RenderedArtifact(
                outputPath = item.outputPath,
                content = writer.toString(),
                conflictPolicy = item.conflictPolicy
            )
        }
    }
}
```

```text
// cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/command.kt.peb
package {{ packageName }}

class {{ typeName }}
```

```text
// cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb
package {{ packageName }}

class {{ typeName }}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-renderer-pebble/build.gradle.kts \
        cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PresetTemplateResolver.kt \
        cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRenderer.kt \
        cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/command.kt.peb \
        cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb \
        cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: add pebble pipeline renderer"
```

### Task 7: Add Design Artifact Planner

**Files:**
- Create: `cap4k-plugin-pipeline-generator-design/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt`
- Test: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt`

- [ ] **Step 1: Write the failing design planner test**

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DesignArtifactPlannerTest {

    @Test
    fun `plans command and query artifacts into application module paths`() {
        val planner = DesignArtifactPlanner()

        val items = planner.plan(
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = mapOf("application" to "demo-application"),
                sources = emptyMap(),
                generators = mapOf("design" to GeneratorConfig(enabled = true)),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP)
            ),
            model = CanonicalModel(
                requests = listOf(
                    RequestModel(
                        kind = RequestKind.COMMAND,
                        packageName = "order.submit",
                        typeName = "SubmitOrderCmd",
                        description = "submit order",
                        aggregateName = "Order",
                        aggregatePackageName = "com.acme.demo.domain.aggregates.order",
                        requestFields = emptyList(),
                        responseFields = emptyList()
                    ),
                    RequestModel(
                        kind = RequestKind.QUERY,
                        packageName = "order.read",
                        typeName = "FindOrderQry",
                        description = "find order",
                        aggregateName = "Order",
                        aggregatePackageName = "com.acme.demo.domain.aggregates.order",
                        requestFields = emptyList(),
                        responseFields = emptyList()
                    )
                )
            )
        )

        assertEquals(2, items.size)
        assertEquals("design/command.kt.peb", items.first().templateId)
        assertEquals("design/query.kt.peb", items.last().templateId)
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt",
            items.first().outputPath
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignArtifactPlannerTest"`
Expected: FAIL with missing module or unresolved `DesignArtifactPlanner`.

- [ ] **Step 3: Write the minimal design planner**

```kotlin
// cap4k-plugin-pipeline-generator-design/build.gradle.kts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":cap4k-plugin-pipeline-api"))

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
```

```kotlin
// cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt
package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.*

class DesignArtifactPlanner : GeneratorProvider {
    override val id: String = "design"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = config.modules["application"] ?: error("application module is required")
        val basePath = config.basePackage.replace(".", "/")
        return model.requests.map { request ->
            val packagePath = request.packageName.replace(".", "/")
            val subdir = when (request.kind) {
                RequestKind.COMMAND -> "commands"
                RequestKind.QUERY -> "queries"
            }
            val templateId = when (request.kind) {
                RequestKind.COMMAND -> "design/command.kt.peb"
                RequestKind.QUERY -> "design/query.kt.peb"
            }
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = templateId,
                outputPath = "$applicationRoot/src/main/kotlin/$basePath/application/$subdir/$packagePath/${request.typeName}.kt",
                context = mapOf(
                    "packageName" to "${config.basePackage}.application.$subdir.${request.packageName}",
                    "typeName" to request.typeName,
                    "description" to request.description,
                    "aggregateName" to request.aggregateName
                ),
                conflictPolicy = config.templates.conflictPolicy
            )
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignArtifactPlannerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-generator-design/build.gradle.kts \
        cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt \
        cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt
git commit -m "feat: add design artifact planner"
```

### Task 8: Add Gradle Plugin And Functional Vertical Slice

**Files:**
- Create: `cap4k-plugin-pipeline-gradle/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelineExtension.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kPlanTask.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kGenerateTask.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/domain/build/generated/ksp/main/resources/metadata/aggregate-Order.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/command.kt.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query.kt.peb`

- [ ] **Step 1: Write the failing functional test**

```kotlin
package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class PipelinePluginFunctionalTest {

    @Test
    fun `cap4kGenerate renders command and query files from repository config`() {
        val projectDir = Files.createTempDirectory("pipeline-functional").toFile()
        File("src/test/resources/functional/design-sample").copyRecursively(projectDir, overwrite = true)

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(
            File(projectDir, "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt").exists()
        )
        assertTrue(
            File(projectDir, "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt").exists()
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"`
Expected: FAIL with missing plugin module or missing `cap4kGenerate` task.

- [ ] **Step 3: Write the minimal Gradle plugin and functional fixture**

```kotlin
// cap4k-plugin-pipeline-gradle/build.gradle.kts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-gradle-plugin`
}

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation(project(":cap4k-plugin-pipeline-api"))
    implementation(project(":cap4k-plugin-pipeline-core"))
    implementation(project(":cap4k-plugin-pipeline-renderer-api"))
    implementation(project(":cap4k-plugin-pipeline-renderer-pebble"))
    implementation(project(":cap4k-plugin-pipeline-source-design-json"))
    implementation(project(":cap4k-plugin-pipeline-source-ksp-metadata"))
    implementation(project(":cap4k-plugin-pipeline-generator-design"))

    testImplementation(gradleTestKit())
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        create("cap4kPipeline") {
            id = "com.only4.cap4k.plugin.pipeline"
            implementationClass = "com.only4.cap4k.plugin.pipeline.gradle.PipelinePlugin"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelineExtension.kt
package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

open class PipelineExtension @Inject constructor(objects: ObjectFactory) {
    val basePackage: Property<String> = objects.property(String::class.java)
    val applicationModulePath: Property<String> = objects.property(String::class.java)
    val designFiles: ConfigurableFileCollection = objects.fileCollection()
    val kspMetadataDir: Property<String> = objects.property(String::class.java)
    val templateOverrideDir: Property<String> = objects.property(String::class.java)
}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt
package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.*
import com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssembler
import com.only4.cap4k.plugin.pipeline.core.DefaultPipelineRunner
import com.only4.cap4k.plugin.pipeline.core.FilesystemArtifactExporter
import com.only4.cap4k.plugin.pipeline.core.NoopArtifactExporter
import com.only4.cap4k.plugin.pipeline.generator.design.DesignArtifactPlanner
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRenderer
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PresetTemplateResolver
import com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonSourceProvider
import com.only4.cap4k.plugin.pipeline.source.ksp.KspMetadataSourceProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.nio.file.Paths

class PipelinePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("cap4kPipeline", PipelineExtension::class.java)

        project.tasks.register("cap4kPlan", Cap4kPlanTask::class.java) { task ->
            task.extension.set(extension)
        }
        project.tasks.register("cap4kGenerate", Cap4kGenerateTask::class.java) { task ->
            task.extension.set(extension)
        }
    }
}

internal fun buildConfig(project: Project, extension: PipelineExtension): ProjectConfig {
    return ProjectConfig(
        basePackage = extension.basePackage.get(),
        layout = ProjectLayout.MULTI_MODULE,
        modules = mapOf("application" to extension.applicationModulePath.get()),
        sources = mapOf(
            "design-json" to SourceConfig(
                enabled = true,
                options = mapOf("files" to extension.designFiles.files.map { it.absolutePath })
            ),
            "ksp-metadata" to SourceConfig(
                enabled = true,
                options = mapOf("inputDir" to extension.kspMetadataDir.get())
            )
        ),
        generators = mapOf("design" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig(
            preset = "ddd-default",
            overrideDirs = listOf(extension.templateOverrideDir.get()),
            conflictPolicy = ConflictPolicy.SKIP
        )
    )
}

internal fun buildRunner(project: Project, config: ProjectConfig, exportEnabled: Boolean): DefaultPipelineRunner {
    return DefaultPipelineRunner(
        sources = listOf(
            DesignJsonSourceProvider(),
            KspMetadataSourceProvider()
        ),
        generators = listOf(DesignArtifactPlanner()),
        assembler = DefaultCanonicalAssembler(),
        renderer = PebbleArtifactRenderer(
            PresetTemplateResolver(config.templates.preset, config.templates.overrideDirs)
        ),
        exporter = if (exportEnabled) {
            FilesystemArtifactExporter(Paths.get(project.projectDir.absolutePath))
        } else {
            NoopArtifactExporter()
        }
    )
}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kPlanTask.kt
package com.only4.cap4k.plugin.pipeline.gradle

import com.google.gson.GsonBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class Cap4kPlanTask : DefaultTask() {
    @get:Input
    abstract val extension: Property<PipelineExtension>

    @TaskAction
    fun runPlan() {
        val config = buildConfig(project, extension.get())
        val result = buildRunner(project, config, exportEnabled = false).run(config)
        val out = project.layout.buildDirectory.file("cap4k/plan.json").get().asFile
        out.parentFile.mkdirs()
        out.writeText(GsonBuilder().setPrettyPrinting().create().toJson(result.planItems))
    }
}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kGenerateTask.kt
package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class Cap4kGenerateTask : DefaultTask() {
    @get:Input
    abstract val extension: Property<PipelineExtension>

    @TaskAction
    fun generate() {
        val config = buildConfig(project, extension.get())
        buildRunner(project, config, exportEnabled = true).run(config)
    }
}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/settings.gradle.kts
rootProject.name = "design-sample"
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4kPipeline {
    basePackage.set("com.acme.demo")
    applicationModulePath.set("demo-application")
    designFiles.from("design/design.json")
    kspMetadataDir.set("domain/build/generated/ksp/main/resources/metadata")
    templateOverrideDir.set("codegen/templates")
}
```

```json
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/design/design.json
[
  {
    "tag": "cmd",
    "package": "order.submit",
    "name": "SubmitOrder",
    "desc": "submit order command",
    "aggregates": ["Order"],
    "requestFields": [{ "name": "orderId", "type": "Long" }],
    "responseFields": [{ "name": "accepted", "type": "Boolean" }]
  },
  {
    "tag": "qry",
    "package": "order.read",
    "name": "FindOrder",
    "desc": "find order query",
    "aggregates": ["Order"],
    "requestFields": [{ "name": "orderId", "type": "Long" }],
    "responseFields": [{ "name": "status", "type": "String" }]
  }
]
```

```json
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/domain/build/generated/ksp/main/resources/metadata/aggregate-Order.json
{
  "aggregateName": "Order",
  "aggregateRoot": {
    "className": "Order",
    "qualifiedName": "com.acme.demo.domain.aggregates.order.Order",
    "packageName": "com.acme.demo.domain.aggregates.order"
  }
}
```

```text
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/command.kt.peb
package {{ packageName }}

class {{ typeName }}
```

```text
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query.kt.peb
package {{ packageName }}

class {{ typeName }}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-gradle/build.gradle.kts \
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelineExtension.kt \
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt \
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kPlanTask.kt \
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kGenerateTask.kt \
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt \
        cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample
git commit -m "feat: add pipeline gradle vertical slice"
```
