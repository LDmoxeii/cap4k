# Cap4k Design Query-Handler Family Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a bounded `designQueryHandler` pipeline generator that produces old-style query handler families into the adapter module, with conservative list/page routing, helper-first handler presets, and end-to-end migration coverage.

**Architecture:** Keep request-family generation in the existing `design` generator and add a separate public generator contract, `design-query-handler`, implemented inside the same Kotlin module. Reuse a shared internal query-variant resolver so request-side and handler-side routing stay aligned, keep template override customization on bounded `design/...` template ids, and require `adapterModulePath` only when the handler family is enabled.

**Tech Stack:** Kotlin, JUnit 5, Gradle TestKit, Pebble templates, existing `cap4k` pipeline generator/renderer/Gradle functional fixtures

---

## File Structure

- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
  Responsibility: expose `generators.designQueryHandler` in the public DSL

- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
  Responsibility: validate `designQueryHandler`, wire `design-query-handler` into `ProjectConfig`, and require `adapterModulePath` only when needed

- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
  Responsibility: unit coverage for DSL defaults, config wiring, and validation failures

- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignModuleRootResolver.kt`
  Responsibility: shared relative-module-path validation for application and adapter planners

- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryVariantResolver.kt`
  Responsibility: internal bounded query-family variant resolution shared by request and handler planners

- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt`
  Responsibility: reuse shared query-family resolver for request-side template ids

- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerRenderModels.kt`
  Responsibility: bounded render-model context for adapter-side query handlers

- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerArtifactPlanner.kt`
  Responsibility: new `design-query-handler` provider that emits adapter-side handler artifacts

- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerArtifactPlannerTest.kt`
  Responsibility: planner regression coverage for default/list/page handler routing and adapter output paths

- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
  Responsibility: register `DesignQueryHandlerArtifactPlanner` in the runner

- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_handler.kt.peb`
  Responsibility: helper-first default query-handler preset using `Query<...>`

- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list_handler.kt.peb`
  Responsibility: helper-first list query-handler preset using `ListQuery<...>`

- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page_handler.kt.peb`
  Responsibility: helper-first page query-handler preset using `PageQuery<...>`

- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
  Responsibility: preset-level rendering coverage for bounded handler templates

- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts`
  Responsibility: fixture-level adapter module path so handler generation can be enabled without ad hoc module rewrites

- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_handler.kt.peb`
  Responsibility: representative default query-handler override template

- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_list_handler.kt.peb`
  Responsibility: representative list query-handler override template

- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_page_handler.kt.peb`
  Responsibility: representative page query-handler override template

- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
  Responsibility: end-to-end plan/generate/override coverage plus configuration-failure coverage for `designQueryHandler`

- Verify only: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/design/design.json`
  Responsibility: existing default/list/page query entries already provide the request-side sample data for the new handler family

### Task 1: Add DSL and Config Wiring for `designQueryHandler`

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`

- [ ] **Step 1: Add failing config tests for the new generator block**

Add these assertions and tests to `Cap4kProjectConfigFactoryTest.kt`:

```kotlin
@Test
fun `nested cap4k extension exposes explicit disabled defaults`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

    assertFalse(extension.sources.designJson.enabled.get())
    assertFalse(extension.sources.kspMetadata.enabled.get())
    assertFalse(extension.sources.db.enabled.get())
    assertFalse(extension.sources.irAnalysis.enabled.get())
    assertFalse(extension.generators.design.enabled.get())
    assertFalse(extension.generators.designQueryHandler.enabled.get())
    assertFalse(extension.generators.aggregate.enabled.get())
    assertEquals("FAIL", extension.generators.aggregate.unsupportedTablePolicy.get())
    assertFalse(extension.generators.drawingBoard.enabled.get())
    assertFalse(extension.generators.flow.enabled.get())
    assertEquals("ddd-default", extension.templates.preset.get())
    assertEquals("SKIP", extension.templates.conflictPolicy.get())
    assertEquals(null, extension.types.registryFile.orNull)
}

@Test
fun `factory includes adapter module and design query handler generator when enabled`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

    extension.project {
        basePackage.set("com.acme.demo")
        applicationModulePath.set("demo-application")
        adapterModulePath.set("demo-adapter")
    }
    extension.sources {
        designJson {
            enabled.set(true)
            files.from(project.file("design/design.json"))
        }
    }
    extension.generators {
        design { enabled.set(true) }
        designQueryHandler { enabled.set(true) }
    }

    val config = Cap4kProjectConfigFactory().build(project, extension)

    assertEquals(
        mapOf(
            "application" to "demo-application",
            "adapter" to "demo-adapter",
        ),
        config.modules,
    )
    assertEquals(setOf("design", "design-query-handler"), config.enabledGeneratorIds())
}

@Test
fun `design query handler generator requires adapter module path`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

    extension.project {
        basePackage.set("com.acme.demo")
        applicationModulePath.set("demo-application")
    }
    extension.sources {
        designJson {
            enabled.set(true)
            files.from(project.file("design/design.json"))
        }
    }
    extension.generators {
        design { enabled.set(true) }
        designQueryHandler { enabled.set(true) }
    }

    val error = assertThrows(IllegalArgumentException::class.java) {
        Cap4kProjectConfigFactory().build(project, extension)
    }

    assertEquals("project.adapterModulePath is required when designQueryHandler is enabled.", error.message)
}

@Test
fun `design query handler generator requires enabled design generator`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

    extension.project {
        basePackage.set("com.acme.demo")
        adapterModulePath.set("demo-adapter")
    }
    extension.sources {
        designJson {
            enabled.set(true)
            files.from(project.file("design/design.json"))
        }
    }
    extension.generators {
        designQueryHandler { enabled.set(true) }
    }

    val error = assertThrows(IllegalArgumentException::class.java) {
        Cap4kProjectConfigFactory().build(project, extension)
    }

    assertEquals("designQueryHandler generator requires enabled design generator.", error.message)
}
```

- [ ] **Step 2: Run the focused config tests and confirm they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*nested cap4k extension exposes explicit disabled defaults" --tests "*factory includes adapter module and design query handler generator when enabled" --tests "*design query handler generator requires adapter module path" --tests "*design query handler generator requires enabled design generator" --rerun-tasks
```

Expected: FAIL because `Cap4kExtension` does not yet expose `designQueryHandler`, and `Cap4kProjectConfigFactory` does not yet recognize generator id `design-query-handler`.

- [ ] **Step 3: Implement the DSL and config wiring**

Update `Cap4kExtension.kt` with the new extension block:

```kotlin
open class Cap4kGeneratorsExtension @Inject constructor(objects: ObjectFactory) {
    val design: DesignGeneratorExtension = objects.newInstance(DesignGeneratorExtension::class.java)
    val designQueryHandler: DesignQueryHandlerGeneratorExtension =
        objects.newInstance(DesignQueryHandlerGeneratorExtension::class.java)
    val aggregate: AggregateGeneratorExtension = objects.newInstance(AggregateGeneratorExtension::class.java)
    val drawingBoard: DrawingBoardGeneratorExtension = objects.newInstance(DrawingBoardGeneratorExtension::class.java)
    val flow: FlowGeneratorExtension = objects.newInstance(FlowGeneratorExtension::class.java)

    fun design(block: DesignGeneratorExtension.() -> Unit) {
        design.block()
    }

    fun designQueryHandler(block: DesignQueryHandlerGeneratorExtension.() -> Unit) {
        designQueryHandler.block()
    }

    fun aggregate(block: AggregateGeneratorExtension.() -> Unit) {
        aggregate.block()
    }

    fun drawingBoard(block: DrawingBoardGeneratorExtension.() -> Unit) {
        drawingBoard.block()
    }

    fun flow(block: FlowGeneratorExtension.() -> Unit) {
        flow.block()
    }
}

open class DesignGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

open class DesignQueryHandlerGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}
```

Update `Cap4kProjectConfigFactory.kt` to wire the new generator:

```kotlin
class Cap4kProjectConfigFactory {

    fun build(project: Project, extension: Cap4kExtension): ProjectConfig {
        val basePackage = extension.project.basePackage.required("project.basePackage")

        val sourceStates = SourceStates(
            designJsonEnabled = extension.sources.designJson.enabled.get(),
            kspMetadataEnabled = extension.sources.kspMetadata.enabled.get(),
            dbEnabled = extension.sources.db.enabled.get(),
            irAnalysisEnabled = extension.sources.irAnalysis.enabled.get(),
        )
        val generatorStates = GeneratorStates(
            designEnabled = extension.generators.design.enabled.get(),
            designQueryHandlerEnabled = extension.generators.designQueryHandler.enabled.get(),
            aggregateEnabled = extension.generators.aggregate.enabled.get(),
            flowEnabled = extension.generators.flow.enabled.get(),
            drawingBoardEnabled = extension.generators.drawingBoard.enabled.get(),
        )

        validateProjectRules(extension, generatorStates)
        val modules = buildModules(extension, generatorStates)
        val sources = buildSources(project, extension, sourceStates)
        val generators = buildGenerators(extension, generatorStates)
        validateGeneratorDependencies(sourceStates, generatorStates)
        val typeRegistry = buildTypeRegistry(project, extension)

        return ProjectConfig(
            basePackage = basePackage,
            layout = ProjectLayout.MULTI_MODULE,
            modules = modules,
            typeRegistry = typeRegistry,
            sources = sources,
            generators = generators,
            templates = TemplateConfig(
                preset = extension.templates.preset.normalized().ifEmpty { "ddd-default" },
                overrideDirs = resolveTemplateOverrideDirs(project, extension),
                conflictPolicy = ConflictPolicy.valueOf(
                    extension.templates.conflictPolicy.normalized().ifEmpty { "SKIP" }
                ),
            ),
        )
    }

    private fun validateProjectRules(extension: Cap4kExtension, generators: GeneratorStates) {
        if (generators.designEnabled) {
            extension.project.applicationModulePath.requiredWhenEnabled(
                "project.applicationModulePath",
                "design"
            )
        }
        if (generators.designQueryHandlerEnabled) {
            extension.project.adapterModulePath.requiredWhenEnabled(
                "project.adapterModulePath",
                "designQueryHandler"
            )
        }
        if (generators.aggregateEnabled) {
            val missingDomain = extension.project.domainModulePath.optionalValue() == null
            val missingAdapter = extension.project.adapterModulePath.optionalValue() == null
            if (missingDomain || missingAdapter) {
                throw IllegalArgumentException(
                    "project.domainModulePath and project.adapterModulePath are required when aggregate is enabled."
                )
            }
        }
    }

    private fun buildModules(
        extension: Cap4kExtension,
        generators: GeneratorStates,
    ): Map<String, String> = buildMap {
        if (generators.designEnabled) {
            put("application", extension.project.applicationModulePath.required("project.applicationModulePath"))
        }
        if (generators.designQueryHandlerEnabled) {
            put("adapter", extension.project.adapterModulePath.required("project.adapterModulePath"))
        }
        if (generators.aggregateEnabled) {
            put("domain", extension.project.domainModulePath.required("project.domainModulePath"))
            put("adapter", extension.project.adapterModulePath.required("project.adapterModulePath"))
        }
    }

    private fun buildGenerators(
        extension: Cap4kExtension,
        states: GeneratorStates,
    ): Map<String, GeneratorConfig> = buildMap {
        if (states.designEnabled) {
            put("design", GeneratorConfig(enabled = true))
        }
        if (states.designQueryHandlerEnabled) {
            put("design-query-handler", GeneratorConfig(enabled = true))
        }
        if (states.aggregateEnabled) {
            put(
                "aggregate",
                GeneratorConfig(
                    enabled = true,
                    options = mapOf(
                        "unsupportedTablePolicy" to extension.generators.aggregate.unsupportedTablePolicy
                            .normalized()
                            .uppercase(Locale.ROOT)
                            .ifEmpty { "FAIL" }
                    ),
                )
            )
        }
        if (states.flowEnabled) {
            put(
                "flow",
                GeneratorConfig(
                    enabled = true,
                    options = mapOf(
                        "outputDir" to extension.generators.flow.outputDir.requiredWhenEnabled(
                            "generators.flow.outputDir",
                            "flow"
                        )
                    ),
                )
            )
        }
        if (states.drawingBoardEnabled) {
            put(
                "drawing-board",
                GeneratorConfig(
                    enabled = true,
                    options = mapOf(
                        "outputDir" to extension.generators.drawingBoard.outputDir.requiredWhenEnabled(
                            "generators.drawingBoard.outputDir",
                            "drawingBoard"
                        )
                    ),
                )
            )
        }
    }

    private fun validateGeneratorDependencies(sources: SourceStates, generators: GeneratorStates) {
        if (generators.designQueryHandlerEnabled && !generators.designEnabled) {
            throw IllegalArgumentException("designQueryHandler generator requires enabled design generator.")
        }
        if (generators.designEnabled && !sources.designJsonEnabled) {
            throw IllegalArgumentException("design generator requires enabled designJson source.")
        }
        if (generators.aggregateEnabled && !sources.dbEnabled) {
            throw IllegalArgumentException("aggregate generator requires enabled db source.")
        }
        if (generators.flowEnabled && !sources.irAnalysisEnabled) {
            throw IllegalArgumentException("flow generator requires enabled irAnalysis source.")
        }
        if (generators.drawingBoardEnabled && !sources.irAnalysisEnabled) {
            throw IllegalArgumentException("drawingBoard generator requires enabled irAnalysis source.")
        }
    }
}

private data class GeneratorStates(
    val designEnabled: Boolean,
    val designQueryHandlerEnabled: Boolean,
    val aggregateEnabled: Boolean,
    val flowEnabled: Boolean,
    val drawingBoardEnabled: Boolean,
)
```

- [ ] **Step 4: Re-run the focused config tests and confirm they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*nested cap4k extension exposes explicit disabled defaults" --tests "*factory includes adapter module and design query handler generator when enabled" --tests "*design query handler generator requires adapter module path" --tests "*design query handler generator requires enabled design generator" --rerun-tasks
```

Expected: PASS with the new DSL block available, `config.modules` containing `adapter` only when handler generation is enabled, and configuration failures using the exact messages from the new tests.

- [ ] **Step 5: Commit the DSL/config change**

```bash
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt
git commit -m "feat: add design query handler generator config"
```

### Task 2: Add the Bounded Query-Handler Planner and Register It

**Files:**
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignModuleRootResolver.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryVariantResolver.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerRenderModels.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Test: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerArtifactPlannerTest.kt`

- [ ] **Step 1: Add failing planner tests for adapter-side query handlers**

Create `DesignQueryHandlerArtifactPlannerTest.kt` with these tests:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.RequestKind
import com.only4.cap4k.plugin.pipeline.api.RequestModel
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignQueryHandlerArtifactPlannerTest {

    @Test
    fun `plans bounded query handler variants into adapter module paths`() {
        val planner = DesignQueryHandlerArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(
                modules = mapOf(
                    "application" to "demo-application",
                    "adapter" to "demo-adapter",
                )
            ),
            model = CanonicalModel(
                requests = listOf(
                    RequestModel(
                        kind = RequestKind.QUERY,
                        packageName = "order.read",
                        typeName = "FindOrderQry",
                        description = "find order",
                        responseFields = listOf(
                            FieldModel("responseStatus", "com.bar.Status"),
                            FieldModel("snapshot", "Snapshot", nullable = true),
                            FieldModel("snapshot.updatedAt", "java.time.LocalDateTime"),
                        ),
                    ),
                    RequestModel(
                        kind = RequestKind.QUERY,
                        packageName = "order.read",
                        typeName = "FindOrderListQry",
                        description = "find order list",
                        responseFields = listOf(
                            FieldModel("responseStatus", "com.bar.Status"),
                        ),
                    ),
                    RequestModel(
                        kind = RequestKind.QUERY,
                        packageName = "order.read",
                        typeName = "FindOrderPageQry",
                        description = "find order page",
                        responseFields = listOf(
                            FieldModel("responseStatus", "com.bar.Status"),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                "design/query_handler.kt.peb",
                "design/query_list_handler.kt.peb",
                "design/query_page_handler.kt.peb",
            ),
            items.map { it.templateId },
        )
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderQryHandler.kt",
            items[0].outputPath,
        )
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderListQryHandler.kt",
            items[1].outputPath,
        )
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderPageQryHandler.kt",
            items[2].outputPath,
        )
        assertEquals("adapter", items[0].moduleRole)
        assertEquals("com.acme.demo.adapter.queries.order.read", items[0].context["packageName"])
        assertEquals("FindOrderQryHandler", items[0].context["typeName"])
        assertEquals("FindOrderQry", items[0].context["queryTypeName"])
        assertEquals(
            listOf("com.acme.demo.application.queries.order.read.FindOrderQry"),
            items[0].context["imports"],
        )
        val responseFields = requireNotNull(items[0].context["responseFields"] as? List<*>)
            .map { requireNotNull(it as? DesignQueryHandlerResponseFieldModel) }
        assertEquals(
            listOf(
                DesignQueryHandlerResponseFieldModel("responseStatus"),
                DesignQueryHandlerResponseFieldModel("snapshot"),
            ),
            responseFields,
        )
    }

    @Test
    fun `keeps default handler template when page or list are not suffix variants`() {
        val planner = DesignQueryHandlerArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(
                modules = mapOf(
                    "application" to "demo-application",
                    "adapter" to "demo-adapter",
                )
            ),
            model = CanonicalModel(
                requests = listOf(
                    RequestModel(
                        kind = RequestKind.QUERY,
                        packageName = "order.read",
                        typeName = "FindOrderPageableQry",
                        description = "pageable",
                    ),
                    RequestModel(
                        kind = RequestKind.QUERY,
                        packageName = "order.read",
                        typeName = "FindOrderListingQry",
                        description = "listing",
                    ),
                ),
            ),
        )

        assertTrue(items.all { it.templateId == "design/query_handler.kt.peb" })
    }

    @Test
    fun `fails when adapter module is missing`() {
        val planner = DesignQueryHandlerArtifactPlanner()

        val error = assertThrows(IllegalStateException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("application" to "demo-application")),
                model = CanonicalModel(
                    requests = listOf(
                        RequestModel(
                            kind = RequestKind.QUERY,
                            packageName = "order.read",
                            typeName = "FindOrderQry",
                            description = "find order",
                        ),
                    ),
                ),
            )
        }

        assertEquals("adapter module is required", error.message)
    }

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("design-query-handler" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}
```

- [ ] **Step 2: Run the focused planner tests and confirm they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "*DesignQueryHandlerArtifactPlannerTest*" --rerun-tasks
```

Expected: FAIL because `DesignQueryHandlerArtifactPlanner` and `DesignQueryHandlerResponseFieldModel` do not exist yet.

- [ ] **Step 3: Implement the shared variant resolver, handler planner, and provider registration**

Create `DesignModuleRootResolver.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal fun requireRelativeModuleRoot(config: ProjectConfig, role: String): String {
    val moduleRoot = config.modules[role] ?: error("$role module is required")
    if (moduleRoot.isBlank()) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $moduleRoot")
    }
    if (moduleRoot.startsWith(":")) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $moduleRoot")
    }

    val path = try {
        Path.of(moduleRoot)
    } catch (ex: InvalidPathException) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $moduleRoot", ex)
    }

    if (path.isAbsolute || path.root != null) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $moduleRoot")
    }

    val normalized = path.normalize()
    if (normalized.nameCount > 0 && normalized.getName(0).toString() == "..") {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $moduleRoot")
    }

    return moduleRoot
}
```

Create `DesignQueryVariantResolver.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.RequestKind
import com.only4.cap4k.plugin.pipeline.api.RequestModel

internal enum class DesignQueryVariant(
    val requestTemplateId: String,
    val handlerTemplateId: String,
) {
    DEFAULT(
        requestTemplateId = "design/query.kt.peb",
        handlerTemplateId = "design/query_handler.kt.peb",
    ),
    LIST(
        requestTemplateId = "design/query_list.kt.peb",
        handlerTemplateId = "design/query_list_handler.kt.peb",
    ),
    PAGE(
        requestTemplateId = "design/query_page.kt.peb",
        handlerTemplateId = "design/query_page_handler.kt.peb",
    ),
}

internal object DesignQueryVariantResolver {
    fun resolve(request: RequestModel): DesignQueryVariant? =
        if (request.kind != RequestKind.QUERY) {
            null
        } else when {
            request.typeName.endsWith("PageQry") -> DesignQueryVariant.PAGE
            request.typeName.endsWith("ListQry") -> DesignQueryVariant.LIST
            else -> DesignQueryVariant.DEFAULT
        }
}
```

Create `DesignQueryHandlerRenderModels.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.RequestModel

internal data class DesignQueryHandlerResponseFieldModel(
    val name: String,
)

internal data class DesignQueryHandlerRenderModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val queryTypeName: String,
    val imports: List<String>,
    val responseFields: List<DesignQueryHandlerResponseFieldModel>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "typeName" to typeName,
        "description" to description,
        "queryTypeName" to queryTypeName,
        "imports" to imports,
        "responseFields" to responseFields,
    )
}

internal object DesignQueryHandlerRenderModelFactory {
    fun create(basePackage: String, request: RequestModel): DesignQueryHandlerRenderModel {
        return DesignQueryHandlerRenderModel(
            packageName = "$basePackage.adapter.queries.${request.packageName}",
            typeName = "${request.typeName}Handler",
            description = request.description,
            queryTypeName = request.typeName,
            imports = listOf("$basePackage.application.queries.${request.packageName}.${request.typeName}"),
            responseFields = request.responseFields
                .asSequence()
                .filterNot { it.name.contains('.') }
                .map { DesignQueryHandlerResponseFieldModel(it.name) }
                .toList(),
        )
    }
}
```

Create `DesignQueryHandlerArtifactPlanner.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.RequestKind

class DesignQueryHandlerArtifactPlanner : GeneratorProvider {
    override val id: String = "design-query-handler"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val adapterRoot = requireRelativeModuleRoot(config, "adapter")
        val basePath = config.basePackage.replace(".", "/")

        return model.requests
            .asSequence()
            .filter { it.kind == RequestKind.QUERY }
            .map { request ->
                val variant = requireNotNull(DesignQueryVariantResolver.resolve(request))
                val packagePath = request.packageName.replace(".", "/")

                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "adapter",
                    templateId = variant.handlerTemplateId,
                    outputPath = "$adapterRoot/src/main/kotlin/$basePath/adapter/queries/$packagePath/${request.typeName}Handler.kt",
                    context = DesignQueryHandlerRenderModelFactory.create(
                        basePackage = config.basePackage,
                        request = request,
                    ).toContextMap(),
                    conflictPolicy = config.templates.conflictPolicy,
                )
            }
            .toList()
    }
}
```

Update `DesignArtifactPlanner.kt` to reuse the shared validator and variant resolver:

```kotlin
class DesignArtifactPlanner : GeneratorProvider {
    override val id: String = "design"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val basePath = config.basePackage.replace(".", "/")

        return model.requests.mapIndexed { index, request ->
            val siblingRequestTypeNames = model.requests.withIndex()
                .asSequence()
                .filter { it.index != index && it.value.packageName == request.packageName }
                .map { it.value.typeName }
                .toSet()
            val packagePath = request.packageName.replace(".", "/")
            val subdir = if (request.kind == RequestKind.COMMAND) "commands" else "queries"
            val templateId = resolveTemplateId(request)

            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = templateId,
                outputPath = "$applicationRoot/src/main/kotlin/$basePath/application/$subdir/$packagePath/${request.typeName}.kt",
                context = DesignRenderModelFactory.create(
                    packageName = "${config.basePackage}.application.$subdir.${request.packageName}",
                    request = request,
                    typeRegistry = config.typeRegistry,
                    siblingRequestTypeNames = siblingRequestTypeNames,
                ).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }

    private fun resolveTemplateId(request: RequestModel): String {
        return when (request.kind) {
            RequestKind.COMMAND -> "design/command.kt.peb"
            RequestKind.QUERY -> requireNotNull(DesignQueryVariantResolver.resolve(request)).requestTemplateId
        }
    }
}
```

Update `PipelinePlugin.kt` to register the new provider:

```kotlin
internal fun buildRunner(project: Project, config: ProjectConfig, exportEnabled: Boolean): PipelineRunner {
    return DefaultPipelineRunner(
        sources = listOf(
            DbSchemaSourceProvider(),
            DesignJsonSourceProvider(),
            KspMetadataSourceProvider(),
            IrAnalysisSourceProvider(),
        ),
        generators = listOf(
            DesignArtifactPlanner(),
            DesignQueryHandlerArtifactPlanner(),
            AggregateArtifactPlanner(),
            DrawingBoardArtifactPlanner(),
            FlowArtifactPlanner(),
        ),
        assembler = DefaultCanonicalAssembler(),
        renderer = PebbleArtifactRenderer(
            PresetTemplateResolver(
                preset = config.templates.preset,
                overrideDirs = config.templates.overrideDirs,
            )
        ),
        exporter = if (exportEnabled) {
            FilesystemArtifactExporter(project.projectDir.toPath())
        } else {
            NoopArtifactExporter()
        },
    )
}
```

- [ ] **Step 4: Re-run the focused planner tests and confirm they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "*DesignQueryHandlerArtifactPlannerTest*" --tests "*plans bounded query template variants from conservative suffixes" --rerun-tasks
```

Expected: PASS with adapter-side handler output paths, bounded handler template ids, and the existing request-side query-variant test still passing after the internal resolver refactor.

- [ ] **Step 5: Commit the planner/provider change**

```bash
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignModuleRootResolver.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryVariantResolver.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerRenderModels.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerArtifactPlanner.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerArtifactPlannerTest.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt
git commit -m "feat: add bounded design query handler planner"
```

### Task 3: Add Helper-First Query-Handler Presets and Renderer Coverage

**Files:**
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_handler.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list_handler.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page_handler.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Add failing renderer tests for the new handler presets**

Add these tests to `PebbleArtifactRendererTest.kt`:

```kotlin
@Test
fun `default query handler preset renders service stub`() {
    val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-query-handler-contract")
    val renderer = PebbleArtifactRenderer(
        templateResolver = PresetTemplateResolver(
            preset = "ddd-default",
            overrideDirs = listOf(overrideDir.toString())
        )
    )

    val rendered = renderer.render(
        planItems = listOf(
            ArtifactPlanItem(
                generatorId = "design-query-handler",
                moduleRole = "adapter",
                templateId = "design/query_handler.kt.peb",
                outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderQryHandler.kt",
                context = mapOf(
                    "packageName" to "com.acme.demo.adapter.queries.order.read",
                    "typeName" to "FindOrderQryHandler",
                    "description" to "find order query",
                    "queryTypeName" to "FindOrderQry",
                    "imports" to listOf("com.acme.demo.application.queries.order.read.FindOrderQry"),
                    "responseFields" to listOf(
                        mapOf("name" to "responseStatus"),
                        mapOf("name" to "snapshot"),
                    ),
                ),
                conflictPolicy = ConflictPolicy.SKIP
            )
        ),
        config = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = emptyMap(),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString()),
                conflictPolicy = ConflictPolicy.SKIP
            )
        )
    )

    val content = rendered.single().content
    assertTrue(content.contains("import org.springframework.stereotype.Service"))
    assertTrue(content.contains("import com.only4.cap4k.ddd.core.application.query.Query"))
    assertTrue(content.contains("import com.acme.demo.application.queries.order.read.FindOrderQry"))
    assertTrue(content.contains("class FindOrderQryHandler : Query<FindOrderQry.Request, FindOrderQry.Response>"))
    assertTrue(content.contains("responseStatus = TODO(\"set responseStatus\")"))
    assertTrue(content.contains("snapshot = TODO(\"set snapshot\")"))
}

@Test
fun `bounded query handler presets render list and page contracts`() {
    val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-bounded-query-handler-contracts")
    val renderer = PebbleArtifactRenderer(
        templateResolver = PresetTemplateResolver(
            preset = "ddd-default",
            overrideDirs = listOf(overrideDir.toString())
        )
    )

    val rendered = renderer.render(
        planItems = listOf(
            ArtifactPlanItem(
                generatorId = "design-query-handler",
                moduleRole = "adapter",
                templateId = "design/query_list_handler.kt.peb",
                outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderListQryHandler.kt",
                context = mapOf(
                    "packageName" to "com.acme.demo.adapter.queries.order.read",
                    "typeName" to "FindOrderListQryHandler",
                    "description" to "find order list query",
                    "queryTypeName" to "FindOrderListQry",
                    "imports" to listOf("com.acme.demo.application.queries.order.read.FindOrderListQry"),
                    "responseFields" to listOf(
                        mapOf("name" to "responseStatus"),
                    ),
                ),
                conflictPolicy = ConflictPolicy.SKIP
            ),
            ArtifactPlanItem(
                generatorId = "design-query-handler",
                moduleRole = "adapter",
                templateId = "design/query_page_handler.kt.peb",
                outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderPageQryHandler.kt",
                context = mapOf(
                    "packageName" to "com.acme.demo.adapter.queries.order.read",
                    "typeName" to "FindOrderPageQryHandler",
                    "description" to "find order page query",
                    "queryTypeName" to "FindOrderPageQry",
                    "imports" to listOf("com.acme.demo.application.queries.order.read.FindOrderPageQry"),
                    "responseFields" to listOf(
                        mapOf("name" to "responseStatus"),
                    ),
                ),
                conflictPolicy = ConflictPolicy.SKIP
            )
        ),
        config = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = emptyMap(),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString()),
                conflictPolicy = ConflictPolicy.SKIP
            )
        )
    )

    val listContent = rendered[0].content
    assertTrue(listContent.contains("import com.only4.cap4k.ddd.core.application.query.ListQuery"))
    assertTrue(listContent.contains("import com.acme.demo.application.queries.order.read.FindOrderListQry"))
    assertTrue(listContent.contains("class FindOrderListQryHandler : ListQuery<FindOrderListQry.Request, FindOrderListQry.Response>"))
    assertTrue(listContent.contains("responseStatus = TODO(\"set responseStatus\")"))

    val pageContent = rendered[1].content
    assertTrue(pageContent.contains("import com.only4.cap4k.ddd.core.application.query.PageQuery"))
    assertTrue(pageContent.contains("import com.acme.demo.application.queries.order.read.FindOrderPageQry"))
    assertTrue(pageContent.contains("class FindOrderPageQryHandler : PageQuery<FindOrderPageQry.Request, FindOrderPageQry.Response>"))
    assertTrue(pageContent.contains("responseStatus = TODO(\"set responseStatus\")"))
}

@Test
fun `query handler presets return object response when response fields are empty`() {
    val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-query-handler-empty-response")
    val renderer = PebbleArtifactRenderer(
        templateResolver = PresetTemplateResolver(
            preset = "ddd-default",
            overrideDirs = listOf(overrideDir.toString())
        )
    )

    val rendered = renderer.render(
        planItems = listOf(
            ArtifactPlanItem(
                generatorId = "design-query-handler",
                moduleRole = "adapter",
                templateId = "design/query_handler.kt.peb",
                outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderQryHandler.kt",
                context = mapOf(
                    "packageName" to "com.acme.demo.adapter.queries.order.read",
                    "typeName" to "FindOrderQryHandler",
                    "description" to "find order query",
                    "queryTypeName" to "FindOrderQry",
                    "imports" to listOf("com.acme.demo.application.queries.order.read.FindOrderQry"),
                    "responseFields" to emptyList<Map<String, Any?>>(),
                ),
                conflictPolicy = ConflictPolicy.SKIP
            )
        ),
        config = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = emptyMap(),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString()),
                conflictPolicy = ConflictPolicy.SKIP
            )
        )
    )

    val content = rendered.single().content
    assertTrue(content.contains("return FindOrderQry.Response"))
    assertFalse(content.contains("return FindOrderQry.Response("))
}
```

- [ ] **Step 2: Run the focused renderer tests and confirm they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*default query handler preset renders service stub" --tests "*bounded query handler presets render list and page contracts" --tests "*query handler presets return object response when response fields are empty" --rerun-tasks
```

Expected: FAIL because the three handler preset templates do not exist yet.

- [ ] **Step 3: Add the helper-first handler presets**

Create `query_handler.kt.peb`:

```pebble
{{ use("org.springframework.stereotype.Service") -}}
{{ use("com.only4.cap4k.ddd.core.application.query.Query") -}}
package {{ packageName }}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

/**
 * {{ description }}
 *
 * 本文件由 cap4k pipeline 生成
 */
@Service
class {{ typeName }} : Query<{{ queryTypeName }}.Request, {{ queryTypeName }}.Response> {

    override fun exec(request: {{ queryTypeName }}.Request): {{ queryTypeName }}.Response {
{% if responseFields|length > 0 %}
        return {{ queryTypeName }}.Response(
{% for field in responseFields %}
            {{ field.name }} = TODO("set {{ field.name }}"){% if not loop.last %},{% endif %}
{% endfor %}
        )
{% else %}
        return {{ queryTypeName }}.Response
{% endif %}
    }
}
```

Create `query_list_handler.kt.peb`:

```pebble
{{ use("org.springframework.stereotype.Service") -}}
{{ use("com.only4.cap4k.ddd.core.application.query.ListQuery") -}}
package {{ packageName }}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

/**
 * {{ description }}
 *
 * 本文件由 cap4k pipeline 生成
 */
@Service
class {{ typeName }} : ListQuery<{{ queryTypeName }}.Request, {{ queryTypeName }}.Response> {

    override fun exec(request: {{ queryTypeName }}.Request): {{ queryTypeName }}.Response {
{% if responseFields|length > 0 %}
        return {{ queryTypeName }}.Response(
{% for field in responseFields %}
            {{ field.name }} = TODO("set {{ field.name }}"){% if not loop.last %},{% endif %}
{% endfor %}
        )
{% else %}
        return {{ queryTypeName }}.Response
{% endif %}
    }
}
```

Create `query_page_handler.kt.peb`:

```pebble
{{ use("org.springframework.stereotype.Service") -}}
{{ use("com.only4.cap4k.ddd.core.application.query.PageQuery") -}}
package {{ packageName }}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

/**
 * {{ description }}
 *
 * 本文件由 cap4k pipeline 生成
 */
@Service
class {{ typeName }} : PageQuery<{{ queryTypeName }}.Request, {{ queryTypeName }}.Response> {

    override fun exec(request: {{ queryTypeName }}.Request): {{ queryTypeName }}.Response {
{% if responseFields|length > 0 %}
        return {{ queryTypeName }}.Response(
{% for field in responseFields %}
            {{ field.name }} = TODO("set {{ field.name }}"){% if not loop.last %},{% endif %}
{% endfor %}
        )
{% else %}
        return {{ queryTypeName }}.Response
{% endif %}
    }
}
```

- [ ] **Step 4: Re-run the focused renderer tests and confirm they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*default query handler preset renders service stub" --tests "*bounded query handler presets render list and page contracts" --tests "*query handler presets return object response when response fields are empty" --rerun-tasks
```

Expected: PASS with the handler presets importing fixed framework interfaces through `use()`, importing the generated query type through `imports()`, and returning `Response` without constructor invocation when the query response is a `data object`.

- [ ] **Step 5: Commit the renderer preset change**

```bash
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_handler.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list_handler.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page_handler.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: add query handler presets"
```

### Task 4: Add Functional Query-Handler Migration Fixtures and End-to-End Coverage

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_handler.kt.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_list_handler.kt.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_page_handler.kt.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Verify only: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/design/design.json`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Add failing functional tests for handler planning, generation, override, and config failures**

Add these tests to `PipelinePluginFunctionalTest.kt`:

```kotlin
@OptIn(ExperimentalPathApi::class)
@Test
fun `cap4kPlan includes query handler artifacts when enabled`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-query-handler-plan")
    copyFixture(projectDir, "design-sample")

    val buildFile = projectDir.resolve("build.gradle.kts")
    buildFile.writeText(
        buildFile.readText()
            .replace("\r\n", "\n")
            .replace(
                """
                |        design {
                |            enabled.set(true)
                |        }
                """.trimMargin(),
                """
                |        design {
                |            enabled.set(true)
                |        }
                |        designQueryHandler {
                |            enabled.set(true)
                |        }
                """.trimMargin()
            )
    )

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kPlan")
        .build()

    val planFile = projectDir.resolve("build/cap4k/plan.json").toFile()
    val content = planFile.readText()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(planFile.exists())
    assertTrue(content.contains("\"templateId\": \"design/query_handler.kt.peb\""))
    assertTrue(content.contains("\"templateId\": \"design/query_list_handler.kt.peb\""))
    assertTrue(content.contains("\"templateId\": \"design/query_page_handler.kt.peb\""))
    assertTrue(content.contains("demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderQryHandler.kt"))
    assertTrue(content.contains("demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderListQryHandler.kt"))
    assertTrue(content.contains("demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderPageQryHandler.kt"))
}

@OptIn(ExperimentalPathApi::class)
@Test
fun `cap4kGenerate renders query handler variants into adapter module`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-query-handler-generate")
    copyFixture(projectDir, "design-sample")

    val buildFile = projectDir.resolve("build.gradle.kts")
    buildFile.writeText(
        buildFile.readText()
            .replace("\r\n", "\n")
            .replace(
                """
                |        design {
                |            enabled.set(true)
                |        }
                """.trimMargin(),
                """
                |        design {
                |            enabled.set(true)
                |        }
                |        designQueryHandler {
                |            enabled.set(true)
                |        }
                """.trimMargin()
            )
    )

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kGenerate")
        .build()

    val defaultHandlerFile = projectDir.resolve(
        "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderQryHandler.kt"
    )
    val listHandlerFile = projectDir.resolve(
        "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderListQryHandler.kt"
    )
    val pageHandlerFile = projectDir.resolve(
        "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderPageQryHandler.kt"
    )

    val defaultContent = defaultHandlerFile.readText()
    val listContent = listHandlerFile.readText()
    val pageContent = pageHandlerFile.readText()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(defaultHandlerFile.toFile().exists())
    assertTrue(listHandlerFile.toFile().exists())
    assertTrue(pageHandlerFile.toFile().exists())

    assertTrue(defaultContent.contains("import com.only4.cap4k.ddd.core.application.query.Query"))
    assertTrue(defaultContent.contains("import com.acme.demo.application.queries.order.read.FindOrderQry"))
    assertTrue(defaultContent.contains("class FindOrderQryHandler : Query<FindOrderQry.Request, FindOrderQry.Response>"))
    assertTrue(defaultContent.contains("responseStatus = TODO(\"set responseStatus\")"))
    assertTrue(defaultContent.contains("snapshot = TODO(\"set snapshot\")"))

    assertTrue(listContent.contains("import com.only4.cap4k.ddd.core.application.query.ListQuery"))
    assertTrue(listContent.contains("import com.acme.demo.application.queries.order.read.FindOrderListQry"))
    assertTrue(listContent.contains("class FindOrderListQryHandler : ListQuery<FindOrderListQry.Request, FindOrderListQry.Response>"))
    assertTrue(listContent.contains("responseStatus = TODO(\"set responseStatus\")"))

    assertTrue(pageContent.contains("import com.only4.cap4k.ddd.core.application.query.PageQuery"))
    assertTrue(pageContent.contains("import com.acme.demo.application.queries.order.read.FindOrderPageQry"))
    assertTrue(pageContent.contains("class FindOrderPageQryHandler : PageQuery<FindOrderPageQry.Request, FindOrderPageQry.Response>"))
    assertTrue(pageContent.contains("responseStatus = TODO(\"set responseStatus\")"))
    assertTrue(pageContent.contains("snapshot = TODO(\"set snapshot\")"))
}

@OptIn(ExperimentalPathApi::class)
@Test
fun `cap4kGenerate supports override query handler templates`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-query-handler-override")
    copyFixture(projectDir, "design-sample")

    val buildFile = projectDir.resolve("build.gradle.kts")
    val buildFileContent = buildFile.readText().replace("\r\n", "\n")
    buildFile.writeText(
        buildFileContent.replace(
            """
            |    generators {
            |        design {
            |            enabled.set(true)
            |        }
            |    }
            """.trimMargin(),
            """
            |    generators {
            |        design {
            |            enabled.set(true)
            |        }
            |        designQueryHandler {
            |            enabled.set(true)
            |        }
            |    }
            |    templates {
            |        overrideDirs.from("codegen/templates")
            |    }
            """.trimMargin()
        )
    )

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kGenerate")
        .build()

    val defaultContent = projectDir.resolve(
        "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderQryHandler.kt"
    ).readText()
    val listContent = projectDir.resolve(
        "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderListQryHandler.kt"
    ).readText()
    val pageContent = projectDir.resolve(
        "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderPageQryHandler.kt"
    ).readText()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(defaultContent.contains("// override: representative default query handler migration template"))
    assertTrue(listContent.contains("// override: representative list query handler migration template"))
    assertTrue(pageContent.contains("// override: representative page query handler migration template"))
}
```

Add these two failure tests immediately after the override test:

```kotlin
@OptIn(ExperimentalPathApi::class)
@Test
fun `cap4kPlan fails fast when design query handler lacks adapter module path`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-query-handler-missing-adapter")
    copyFixture(projectDir, "design-sample")

    val buildFile = projectDir.resolve("build.gradle.kts")
    buildFile.writeText(
        buildFile.readText()
            .replace("        adapterModulePath.set(\"demo-adapter\")", "        adapterModulePath.set(\"\")")
            .replace(
                """
                |        design {
                |            enabled.set(true)
                |        }
                """.trimMargin(),
                """
                |        design {
                |            enabled.set(true)
                |        }
                |        designQueryHandler {
                |            enabled.set(true)
                |        }
                """.trimMargin()
            )
    )

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kPlan")
        .buildAndFail()

    assertTrue(result.output.contains("project.adapterModulePath is required when designQueryHandler is enabled."))
    assertFalse(projectDir.resolve("build/cap4k/plan.json").toFile().exists())
}

@OptIn(ExperimentalPathApi::class)
@Test
fun `cap4kPlan fails fast when design query handler is enabled without design`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-query-handler-without-design")
    copyFixture(projectDir, "design-sample")

    val buildFile = projectDir.resolve("build.gradle.kts")
    buildFile.writeText(
        buildFile.readText()
            .replace(
                """
                |        design {
                |            enabled.set(true)
                |        }
                """.trimMargin(),
                """
                |        design {
                |            enabled.set(false)
                |        }
                |        designQueryHandler {
                |            enabled.set(true)
                |        }
                """.trimMargin()
            )
    )

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kPlan")
        .buildAndFail()

    assertTrue(result.output.contains("designQueryHandler generator requires enabled design generator."))
    assertFalse(projectDir.resolve("build/cap4k/plan.json").toFile().exists())
}
```

- [ ] **Step 2: Run the focused functional tests and confirm they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*cap4kPlan includes query handler artifacts when enabled" --tests "*cap4kGenerate renders query handler variants into adapter module" --tests "*cap4kGenerate supports override query handler templates" --tests "*cap4kPlan fails fast when design query handler lacks adapter module path" --tests "*cap4kPlan fails fast when design query handler is enabled without design" --rerun-tasks
```

Expected: FAIL because the fixture does not yet expose `adapterModulePath`, the handler override templates do not exist, and the new handler template ids are not yet produced by the pipeline.

- [ ] **Step 3: Update the fixture build file and add handler override templates**

Modify `design-sample/build.gradle.kts` so the project block includes `adapterModulePath`:

```kotlin
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        applicationModulePath.set("demo-application")
        adapterModulePath.set("demo-adapter")
    }
    sources {
        designJson {
            enabled.set(true)
            files.from("design/design.json")
        }
        kspMetadata {
            enabled.set(true)
            inputDir.set("domain/build/generated/ksp/main/resources/metadata")
        }
    }
    generators {
        design {
            enabled.set(true)
        }
    }
}
```

Create `query_handler.kt.peb` in the fixture override directory:

```pebble
{{ use("org.springframework.stereotype.Service") -}}
{{ use("com.only4.cap4k.ddd.core.application.query.Query") -}}
package {{ packageName }}
// override: representative default query handler migration template
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

@Service
class {{ typeName }} : Query<{{ queryTypeName }}.Request, {{ queryTypeName }}.Response> {
    override fun exec(request: {{ queryTypeName }}.Request): {{ queryTypeName }}.Response {
{% if responseFields|length > 0 %}
        return {{ queryTypeName }}.Response(
{% for field in responseFields %}
            {{ field.name }} = TODO("set {{ field.name }}"){% if not loop.last %},{% endif %}
{% endfor %}
        )
{% else %}
        return {{ queryTypeName }}.Response
{% endif %}
    }
}
```

Create `query_list_handler.kt.peb`:

```pebble
{{ use("org.springframework.stereotype.Service") -}}
{{ use("com.only4.cap4k.ddd.core.application.query.ListQuery") -}}
package {{ packageName }}
// override: representative list query handler migration template
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

@Service
class {{ typeName }} : ListQuery<{{ queryTypeName }}.Request, {{ queryTypeName }}.Response> {
    override fun exec(request: {{ queryTypeName }}.Request): {{ queryTypeName }}.Response {
{% if responseFields|length > 0 %}
        return {{ queryTypeName }}.Response(
{% for field in responseFields %}
            {{ field.name }} = TODO("set {{ field.name }}"){% if not loop.last %},{% endif %}
{% endfor %}
        )
{% else %}
        return {{ queryTypeName }}.Response
{% endif %}
    }
}
```

Create `query_page_handler.kt.peb`:

```pebble
{{ use("org.springframework.stereotype.Service") -}}
{{ use("com.only4.cap4k.ddd.core.application.query.PageQuery") -}}
package {{ packageName }}
// override: representative page query handler migration template
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

@Service
class {{ typeName }} : PageQuery<{{ queryTypeName }}.Request, {{ queryTypeName }}.Response> {
    override fun exec(request: {{ queryTypeName }}.Request): {{ queryTypeName }}.Response {
{% if responseFields|length > 0 %}
        return {{ queryTypeName }}.Response(
{% for field in responseFields %}
            {{ field.name }} = TODO("set {{ field.name }}"){% if not loop.last %},{% endif %}
{% endfor %}
        )
{% else %}
        return {{ queryTypeName }}.Response
{% endif %}
    }
}
```

- [ ] **Step 4: Re-run the focused functional tests and confirm they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*cap4kPlan includes query handler artifacts when enabled" --tests "*cap4kGenerate renders query handler variants into adapter module" --tests "*cap4kGenerate supports override query handler templates" --tests "*cap4kPlan fails fast when design query handler lacks adapter module path" --tests "*cap4kPlan fails fast when design query handler is enabled without design" --rerun-tasks
```

Expected: PASS with handler plan items emitted only when `designQueryHandler` is enabled, generated handler files landing under `demo-adapter`, override templates replacing all three bounded handler template ids, and both invalid-config cases failing during configuration with the expected messages.

- [ ] **Step 5: Commit the functional migration coverage**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_handler.kt.peb cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_list_handler.kt.peb cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_page_handler.kt.peb cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git commit -m "test: cover query handler migration flow"
```

### Task 5: Run Full Verification for the Slice

**Files:**
- Verify only: `cap4k-plugin-pipeline-generator-design`
- Verify only: `cap4k-plugin-pipeline-renderer-pebble`
- Verify only: `cap4k-plugin-pipeline-gradle`

- [ ] **Step 1: Run the full generator-design module tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test
```

Expected: PASS, including existing request-family tests and the new `DesignQueryHandlerArtifactPlannerTest`.

- [ ] **Step 2: Run the full renderer-pebble module tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test
```

Expected: PASS, including the new handler preset rendering tests and the existing query-family helper-contract regressions.

- [ ] **Step 3: Run the full Gradle plugin functional test suite**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test
```

Expected: PASS, including the new `designQueryHandler` plan/generate/override/failure tests.

- [ ] **Step 4: Run the combined slice verification command**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test
```

Expected: PASS with `BUILD SUCCESSFUL`.

- [ ] **Step 5: Confirm the branch is clean and summarize the landed commits**

Run:

```powershell
git status --short --branch
git log --oneline --max-count=6
```

Expected: clean working tree on the feature branch, with the latest commits covering:

- DSL/config wiring
- bounded query-handler planner
- handler presets
- functional migration coverage
