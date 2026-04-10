# Cap4k Pipeline Drawing-Board Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fixture-tested `ir-analysis -> drawing-board export` slice to the new pipeline that reads compiler `design-elements.json` outputs and generates per-tag drawing-board JSON files under the repository.

**Architecture:** Extend the existing `ir-analysis` source so it also parses `design-elements.json`, add a dedicated canonical `drawingBoard` slice in pipeline API/core, then introduce a new `generator-drawing-board` module that plans one JSON artifact per supported tag group. Keep renderer logic generic by adding one shared Pebble preset, and keep Gradle integration thin by wiring a single new generator option plus one functional fixture through the existing pipeline plugin.

**Tech Stack:** Kotlin 2.2, Gradle Kotlin DSL, Gson, JUnit 5, Pebble, Gradle TestKit

---

## File Map

- Modify: `cap4k/settings.gradle.kts`
- Modify: `cap4k/cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-source-ir-analysis/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProvider.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-source-ir-analysis/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProviderTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-drawing-board/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-drawing-board/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlannerTest.kt`
- Create: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/drawing-board/document.json.peb`
- Modify: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/build.gradle.kts`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelineExtension.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/drawing-board-sample/settings.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/drawing-board-sample/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/drawing-board-sample/analysis/app/build/cap4k-code-analysis/nodes.json`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/drawing-board-sample/analysis/app/build/cap4k-code-analysis/rels.json`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/drawing-board-sample/analysis/app/build/cap4k-code-analysis/design-elements.json`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/drawing-board-sample/template-overrides/.gitkeep`

### Task 1: Expand Pipeline API Models for Drawing-Board Data

**Files:**
- Modify: `cap4k/settings.gradle.kts`
- Modify: `cap4k/cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`

- [ ] **Step 1: Add the failing API model tests**

```kotlin
package com.only4.cap4k.plugin.pipeline.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PipelineModelsTest {

    @Test
    fun `ir analysis snapshot keeps design elements alongside graph data`() {
        val snapshot = IrAnalysisSnapshot(
            inputDirs = listOf("app/build/cap4k-code-analysis"),
            nodes = listOf(
                IrNodeSnapshot(
                    id = "OrderController::submit",
                    name = "submit",
                    fullName = "demo.OrderController::submit",
                    type = "controllermethod",
                )
            ),
            edges = emptyList(),
            designElements = listOf(
                DesignElementSnapshot(
                    tag = "cmd",
                    packageName = "orders",
                    name = "SubmitOrder",
                    description = "submit order",
                    aggregates = listOf("Order"),
                    requestFields = listOf(
                        DesignFieldSnapshot(name = "id", type = "Long")
                    ),
                )
            ),
        )

        assertEquals("ir-analysis", snapshot.id)
        assertEquals("SubmitOrder", snapshot.designElements.single().name)
        assertEquals("Long", snapshot.designElements.single().requestFields.single().type)
    }

    @Test
    fun `canonical model keeps optional drawing board slice`() {
        val model = CanonicalModel(
            drawingBoard = DrawingBoardModel(
                elements = listOf(
                    DrawingBoardElementModel(
                        tag = "cmd",
                        packageName = "orders",
                        name = "SubmitOrder",
                        description = "submit order",
                    )
                ),
                elementsByTag = mapOf(
                    "cmd" to listOf(
                        DrawingBoardElementModel(
                            tag = "cmd",
                            packageName = "orders",
                            name = "SubmitOrder",
                            description = "submit order",
                        )
                    )
                ),
            )
        )

        assertEquals("SubmitOrder", model.drawingBoard!!.elements.single().name)
        assertEquals("cmd", model.drawingBoard!!.elementsByTag.keys.single())
    }

    @Test
    fun `canonical model defaults drawing board to null`() {
        assertNull(CanonicalModel().drawingBoard)
    }
}
```

- [ ] **Step 2: Run the API tests and confirm they fail**

Run: `./gradlew :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest" --rerun-tasks`

Expected: compilation fails because `DesignFieldSnapshot`, `DesignElementSnapshot`, `DrawingBoardFieldModel`, `DrawingBoardElementModel`, `DrawingBoardModel`, and `CanonicalModel.drawingBoard` do not exist yet.

- [ ] **Step 3: Add the new API types and new module include**

```kotlin
// cap4k/settings.gradle.kts
include(
    "cap4k-plugin-pipeline-api",
    "cap4k-plugin-pipeline-core",
    "cap4k-plugin-pipeline-renderer-api",
    "cap4k-plugin-pipeline-renderer-pebble",
    "cap4k-plugin-pipeline-source-design-json",
    "cap4k-plugin-pipeline-source-db",
    "cap4k-plugin-pipeline-source-ksp-metadata",
    "cap4k-plugin-pipeline-source-ir-analysis",
    "cap4k-plugin-pipeline-generator-design",
    "cap4k-plugin-pipeline-generator-aggregate",
    "cap4k-plugin-pipeline-generator-flow",
    "cap4k-plugin-pipeline-generator-drawing-board",
    "cap4k-plugin-pipeline-gradle",
)

// cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt
data class DesignFieldSnapshot(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null,
)

data class DesignElementSnapshot(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String,
    val aggregates: List<String> = emptyList(),
    val entity: String? = null,
    val persist: Boolean? = null,
    val requestFields: List<DesignFieldSnapshot> = emptyList(),
    val responseFields: List<DesignFieldSnapshot> = emptyList(),
)

data class DrawingBoardFieldModel(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null,
)

data class DrawingBoardElementModel(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String,
    val aggregates: List<String> = emptyList(),
    val entity: String? = null,
    val persist: Boolean? = null,
    val requestFields: List<DrawingBoardFieldModel> = emptyList(),
    val responseFields: List<DrawingBoardFieldModel> = emptyList(),
)

data class DrawingBoardModel(
    val elements: List<DrawingBoardElementModel>,
    val elementsByTag: Map<String, List<DrawingBoardElementModel>>,
)

data class IrAnalysisSnapshot(
    override val id: String = "ir-analysis",
    val inputDirs: List<String>,
    val nodes: List<IrNodeSnapshot>,
    val edges: List<IrEdgeSnapshot>,
    val designElements: List<DesignElementSnapshot> = emptyList(),
) : SourceSnapshot

data class CanonicalModel(
    val requests: List<RequestModel> = emptyList(),
    val schemas: List<SchemaModel> = emptyList(),
    val entities: List<EntityModel> = emptyList(),
    val repositories: List<RepositoryModel> = emptyList(),
    val analysisGraph: AnalysisGraphModel? = null,
    val drawingBoard: DrawingBoardModel? = null,
)
```

- [ ] **Step 4: Run the API module tests**

Run: `./gradlew :cap4k-plugin-pipeline-api:test --rerun-tasks`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the API model changes**

```bash
git add \
  settings.gradle.kts \
  cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt \
  cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt
git commit -m "feat: add drawing board pipeline models"
```

### Task 2: Extend `source-ir-analysis` to Parse `design-elements.json`

**Files:**
- Modify: `cap4k/cap4k-plugin-pipeline-source-ir-analysis/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProvider.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-source-ir-analysis/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProviderTest.kt`

- [ ] **Step 1: Add the failing source tests**

```kotlin
@Test
fun `collect parses design elements when file exists`() {
    val dir = Files.createTempDirectory("cap4k-ir-design")
    dir.resolve("nodes.json").writeText("""[{"id":"A","name":"A","fullName":"demo.A","type":"controllermethod"}]""")
    dir.resolve("rels.json").writeText("""[]""")
    dir.resolve("design-elements.json").writeText(
        """
        [
          {
            "tag":"cmd",
            "package":"orders",
            "name":"SubmitOrder",
            "desc":"submit order",
            "aggregates":["Order"],
            "requestFields":[{"name":"id","type":"Long","nullable":false}],
            "responseFields":[{"name":"success","type":"Boolean","nullable":false}]
          },
          {
            "tag":"de",
            "package":"orders",
            "name":"OrderCreated",
            "desc":"created",
            "entity":"Order",
            "persist":true
          }
        ]
        """.trimIndent()
    )

    val snapshot = IrAnalysisSourceProvider().collect(configWithIrInputDirs(dir.toString()))

    assertEquals(2, snapshot.designElements.size)
    assertEquals("SubmitOrder", snapshot.designElements.first().name)
    assertEquals("Order", snapshot.designElements.last().entity)
    assertEquals(true, snapshot.designElements.last().persist)
}

@Test
fun `collect returns empty design elements when file is absent`() {
    val dir = Files.createTempDirectory("cap4k-ir-no-design")
    dir.resolve("nodes.json").writeText("""[{"id":"A","name":"A","fullName":"demo.A","type":"controllermethod"}]""")
    dir.resolve("rels.json").writeText("""[]""")

    val snapshot = IrAnalysisSourceProvider().collect(configWithIrInputDirs(dir.toString()))

    assertTrue(snapshot.designElements.isEmpty())
}

private fun configWithIrInputDirs(vararg dirs: String) = ProjectConfig(
    basePackage = "com.acme.demo",
    layout = ProjectLayout.MULTI_MODULE,
    modules = emptyMap(),
    sources = mapOf(
        "ir-analysis" to SourceConfig(
            enabled = true,
            options = mapOf("inputDirs" to dirs.toList()),
        )
    ),
    generators = emptyMap(),
    templates = TemplateConfig(
        preset = "ddd-default",
        overrideDirs = emptyList(),
        conflictPolicy = ConflictPolicy.SKIP,
    ),
)
```

- [ ] **Step 2: Run the source tests and confirm they fail**

Run: `./gradlew :cap4k-plugin-pipeline-source-ir-analysis:test --tests "com.only4.cap4k.plugin.pipeline.source.ir.IrAnalysisSourceProviderTest" --rerun-tasks`

Expected: test compilation or assertions fail because the source provider does not parse `design-elements.json`.

- [ ] **Step 3: Implement design-element parsing in `IrAnalysisSourceProvider`**

```kotlin
override fun collect(config: ProjectConfig): IrAnalysisSnapshot {
    val inputDirs = (config.sources[id]?.options?.get("inputDirs") as? List<*> ?: emptyList<Any>())
        .map { it.toString().trim() }
        .filter { it.isNotEmpty() }
    require(inputDirs.isNotEmpty()) { "ir-analysis source requires at least one inputDirs entry." }

    val nodesById = linkedMapOf<String, IrNodeSnapshot>()
    val edgeKeys = linkedSetOf<EdgeKey>()
    val designElementKeys = linkedSetOf<String>()
    val designElements = mutableListOf<DesignElementSnapshot>()

    inputDirs.forEach { inputDir ->
        val dir = File(inputDir)
        val nodesFile = File(dir, "nodes.json")
        val relsFile = File(dir, "rels.json")
        require(nodesFile.exists() && relsFile.exists()) {
            "ir-analysis inputDir is missing nodes.json or rels.json: $inputDir"
        }

        parseNodes(nodesFile).forEach { node -> nodesById.putIfAbsent(node.id, node) }
        parseEdges(relsFile).forEach { edge -> edgeKeys.add(EdgeKey(edge.fromId, edge.toId, edge.type, edge.label)) }

        val designElementsFile = File(dir, "design-elements.json")
        if (designElementsFile.exists()) {
            parseDesignElements(designElementsFile).forEach { element ->
                val key = "${element.tag}|${element.packageName}|${element.name}"
                if (designElementKeys.add(key)) {
                    designElements.add(element)
                }
            }
        }
    }

    return IrAnalysisSnapshot(
        inputDirs = inputDirs,
        nodes = nodesById.values.toList(),
        edges = edgeKeys.map { key -> IrEdgeSnapshot(key.fromId, key.toId, key.type, key.label) },
        designElements = designElements,
    )
}
```

- [ ] **Step 4: Run the source module tests**

Run: `./gradlew :cap4k-plugin-pipeline-source-ir-analysis:test --rerun-tasks`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the source changes**

```bash
git add \
  cap4k-plugin-pipeline-source-ir-analysis/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProvider.kt \
  cap4k-plugin-pipeline-source-ir-analysis/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProviderTest.kt
git commit -m "feat: parse drawing board elements from ir analysis"
```

### Task 3: Assemble Canonical Drawing-Board Models in Core

**Files:**
- Modify: `cap4k/cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Add the failing assembler tests**

```kotlin
@Test
fun `assemble builds drawing board model from ir design elements`() {
    val snapshot = IrAnalysisSnapshot(
        inputDirs = listOf("app/build/cap4k-code-analysis"),
        nodes = emptyList(),
        edges = emptyList(),
        designElements = listOf(
            DesignElementSnapshot(
                tag = "cmd",
                packageName = "orders",
                name = "SubmitOrder",
                description = "submit order",
                requestFields = listOf(DesignFieldSnapshot(name = "id", type = "Long")),
            ),
            DesignElementSnapshot(
                tag = "cmd",
                packageName = "orders",
                name = "SubmitOrder",
                description = "duplicate should be ignored",
            ),
            DesignElementSnapshot(
                tag = "payload",
                packageName = "orders",
                name = "OrderPayload",
                description = "payload",
            ),
            DesignElementSnapshot(
                tag = "unknown",
                packageName = "orders",
                name = "Ignored",
                description = "ignored",
            ),
        ),
    )

    val model = DefaultCanonicalAssembler().assemble(
        config = sampleProjectConfig(),
        snapshots = listOf(snapshot),
    )

    assertEquals(2, model.drawingBoard!!.elements.size)
    assertEquals(setOf("cmd", "payload"), model.drawingBoard!!.elementsByTag.keys)
    assertEquals("Long", model.drawingBoard!!.elementsByTag.getValue("cmd").single().requestFields.single().type)
}

@Test
fun `assemble leaves drawing board null when there are no supported design elements`() {
    val snapshot = IrAnalysisSnapshot(
        inputDirs = listOf("app/build/cap4k-code-analysis"),
        nodes = emptyList(),
        edges = emptyList(),
        designElements = listOf(
            DesignElementSnapshot(tag = "other", packageName = "", name = "Ignored", description = "")
        ),
    )

    val model = DefaultCanonicalAssembler().assemble(sampleProjectConfig(), listOf(snapshot))

    assertNull(model.drawingBoard)
}

private fun sampleProjectConfig() = ProjectConfig(
    basePackage = "com.acme.demo",
    layout = ProjectLayout.MULTI_MODULE,
    modules = emptyMap(),
    sources = emptyMap(),
    generators = emptyMap(),
    templates = TemplateConfig(
        preset = "ddd-default",
        overrideDirs = emptyList(),
        conflictPolicy = ConflictPolicy.SKIP,
    ),
)
```

- [ ] **Step 2: Run the core tests and confirm they fail**

Run: `./gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest" --rerun-tasks`

Expected: tests fail because `DefaultCanonicalAssembler` does not populate `CanonicalModel.drawingBoard`.

- [ ] **Step 3: Implement drawing-board assembly logic**

```kotlin
private val supportedDrawingBoardTags = linkedSetOf("cli", "cmd", "qry", "payload", "de")

override fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalModel {
    val analysisSnapshot = snapshots.filterIsInstance<IrAnalysisSnapshot>().firstOrNull()
    val drawingBoardElements = analysisSnapshot
        ?.designElements
        .orEmpty()
        .asSequence()
        .filter { it.tag in supportedDrawingBoardTags }
        .distinctBy { "${it.tag}|${it.packageName}|${it.name}" }
        .map { element ->
            DrawingBoardElementModel(
                tag = element.tag,
                packageName = element.packageName,
                name = element.name,
                description = element.description,
                aggregates = element.aggregates,
                entity = element.entity,
                persist = element.persist,
                requestFields = element.requestFields.map { field ->
                    DrawingBoardFieldModel(field.name, field.type, field.nullable, field.defaultValue)
                },
                responseFields = element.responseFields.map { field ->
                    DrawingBoardFieldModel(field.name, field.type, field.nullable, field.defaultValue)
                },
            )
        }
        .toList()

    val drawingBoard = drawingBoardElements
        .takeIf { it.isNotEmpty() }
        ?.let { elements ->
            DrawingBoardModel(
                elements = elements,
                elementsByTag = elements.groupBy { it.tag },
            )
        }

    return CanonicalModel(
        requests = requests,
        schemas = aggregateModels.map { it.first },
        entities = aggregateModels.map { it.second },
        repositories = aggregateModels.map { it.third },
        analysisGraph = analysisGraph,
        drawingBoard = drawingBoard,
    )
}
```

- [ ] **Step 4: Run the core module tests**

Run: `./gradlew :cap4k-plugin-pipeline-core:test --rerun-tasks`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the core changes**

```bash
git add \
  cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt \
  cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: assemble drawing board canonical model"
```

### Task 4: Add the `generator-drawing-board` Module

**Files:**
- Create: `cap4k/cap4k-plugin-pipeline-generator-drawing-board/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-drawing-board/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlannerTest.kt`

- [ ] **Step 1: Write the failing planner tests**

```kotlin
@Test
fun `plan emits one artifact per non empty supported tag`() {
    val planner = DrawingBoardArtifactPlanner()
    val model = CanonicalModel(
        drawingBoard = DrawingBoardModel(
            elements = listOf(
                DrawingBoardElementModel(tag = "cmd", packageName = "orders", name = "SubmitOrder", description = "submit"),
                DrawingBoardElementModel(tag = "cli", packageName = "auth", name = "FetchCaptcha", description = "fetch"),
            ),
            elementsByTag = mapOf(
                "cmd" to listOf(DrawingBoardElementModel(tag = "cmd", packageName = "orders", name = "SubmitOrder", description = "submit")),
                "cli" to listOf(DrawingBoardElementModel(tag = "cli", packageName = "auth", name = "FetchCaptcha", description = "fetch")),
            ),
        )
    )

    val plan = planner.plan(configWithDrawingBoard(), model)

    assertEquals(listOf("design/cli.json", "design/cmd.json"), plan.map { it.outputPath }.sorted())
    assertEquals("drawing-board/document.json.peb", plan.first().templateId)
}

@Test
fun `plan rejects absolute output dir`() {
    val planner = DrawingBoardArtifactPlanner()
    val model = CanonicalModel(
        drawingBoard = DrawingBoardModel(
            elements = listOf(DrawingBoardElementModel(tag = "cmd", packageName = "", name = "SubmitOrder", description = "")),
            elementsByTag = mapOf("cmd" to listOf(DrawingBoardElementModel(tag = "cmd", packageName = "", name = "SubmitOrder", description = ""))),
        )
    )

    assertThrows<IllegalArgumentException> {
        planner.plan(configWithDrawingBoard(outputDir = "C:/tmp/design"), model)
    }
}

@Test
fun `plan fails when drawing board generator is enabled but canonical slice is missing`() {
    val planner = DrawingBoardArtifactPlanner()

    val error = assertThrows<IllegalArgumentException> {
        planner.plan(configWithDrawingBoard(), CanonicalModel())
    }

    assertEquals(
        "drawing-board generator requires at least one parsed design-elements.json input.",
        error.message
    )
}

private fun configWithDrawingBoard(outputDir: String = "design") = ProjectConfig(
    basePackage = "com.acme.demo",
    layout = ProjectLayout.MULTI_MODULE,
    modules = emptyMap(),
    sources = emptyMap(),
    generators = mapOf(
        "drawing-board" to GeneratorConfig(
            enabled = true,
            options = mapOf("outputDir" to outputDir),
        )
    ),
    templates = TemplateConfig(
        preset = "ddd-default",
        overrideDirs = emptyList(),
        conflictPolicy = ConflictPolicy.SKIP,
    ),
)
```

- [ ] **Step 2: Run the planner tests and confirm they fail**

Run: `./gradlew :cap4k-plugin-pipeline-generator-drawing-board:test --rerun-tasks`

Expected: Gradle fails because the new module and planner do not exist yet.

- [ ] **Step 3: Create the module and planner implementation**

```kotlin
// cap4k-plugin-pipeline-generator-drawing-board/build.gradle.kts
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

// DrawingBoardArtifactPlanner.kt
class DrawingBoardArtifactPlanner : GeneratorProvider {
    override val id: String = "drawing-board"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val drawingBoard = model.drawingBoard
            ?: throw IllegalArgumentException(
                "drawing-board generator requires at least one parsed design-elements.json input."
            )
        val outputDir = requireRelativeOutputDir(config)
        val orderedTags = listOf("cli", "cmd", "qry", "payload", "de")

        return orderedTags.flatMap { tag ->
            val elements = drawingBoard.elementsByTag[tag].orEmpty()
            if (elements.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    ArtifactPlanItem(
                        generatorId = id,
                        moduleRole = "project",
                        templateId = "drawing-board/document.json.peb",
                        outputPath = "$outputDir/$tag.json",
                        context = mapOf(
                            "drawingBoardTag" to tag,
                            "elements" to elements,
                        ),
                        conflictPolicy = config.templates.conflictPolicy,
                    )
                )
            }
        }
    }
}
```

- [ ] **Step 4: Run the new generator tests**

Run: `./gradlew :cap4k-plugin-pipeline-generator-drawing-board:test --rerun-tasks`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the planner module**

```bash
git add \
  cap4k-plugin-pipeline-generator-drawing-board/build.gradle.kts \
  cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt \
  cap4k-plugin-pipeline-generator-drawing-board/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlannerTest.kt
git commit -m "feat: add drawing board artifact planner"
```

### Task 5: Add the Drawing-Board Pebble Preset

**Files:**
- Create: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/drawing-board/document.json.peb`
- Modify: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Add the failing renderer test**

```kotlin
@Test
fun `renders drawing board json with optional fields`() {
    val artifact = renderer.render(
        ArtifactPlanItem(
            generatorId = "drawing-board",
            moduleRole = "project",
            templateId = "drawing-board/document.json.peb",
            outputPath = "design/cmd.json",
            context = mapOf(
                "drawingBoardTag" to "cmd",
                "elements" to listOf(
                    mapOf(
                        "tag" to "cmd",
                        "packageName" to "orders",
                        "name" to "SubmitOrder",
                        "description" to "submit order",
                        "aggregates" to listOf("Order"),
                        "entity" to "Order",
                        "persist" to true,
                        "requestFields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false, "defaultValue" to null)
                        ),
                        "responseFields" to listOf(
                            mapOf("name" to "success", "type" to "Boolean", "nullable" to false, "defaultValue" to null)
                        ),
                    )
                ),
            ),
            conflictPolicy = ConflictPolicy.SKIP,
        )
    )

    assertEquals("design/cmd.json", artifact.outputPath)
    assertTrue(artifact.content.contains("\"tag\": \"cmd\""))
    assertTrue(artifact.content.contains("\"entity\": \"Order\""))
    assertTrue(artifact.content.contains("\"persist\": true"))
    assertTrue(artifact.content.contains("\"name\": \"id\""))
}
```

- [ ] **Step 2: Run the renderer test and confirm it fails**

Run: `./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --rerun-tasks`

Expected: renderer fails because the drawing-board preset does not exist.

- [ ] **Step 3: Add the shared Pebble template**

```pebble
[
{%- for element in elements %}
  {
    "tag": "{{ element.tag }}",
    "package": "{{ element.packageName }}",
    "name": "{{ element.name }}",
    "desc": "{{ element.description }}",
    "aggregates": [{% for agg in element.aggregates %}"{{ agg }}"{% if not loop.last %}, {% endif %}{% endfor %}]
{%- if element.entity is not null %},
    "entity": "{{ element.entity }}"
{%- endif %}
{%- if element.persist is not null %},
    "persist": {{ element.persist }}
{%- endif %},
    "requestFields": {% if element.requestFields|length == 0 %}[]{% else %}[{% for field in element.requestFields %}
      { "name": "{{ field.name }}", "type": "{{ field.type }}", "nullable": {{ field.nullable }}{% if field.defaultValue is not null %}, "defaultValue": "{{ field.defaultValue }}"{% endif %} }{% if not loop.last %},{% endif %}
    {% endfor %}]{% endif %},
    "responseFields": {% if element.responseFields|length == 0 %}[]{% else %}[{% for field in element.responseFields %}
      { "name": "{{ field.name }}", "type": "{{ field.type }}", "nullable": {{ field.nullable }}{% if field.defaultValue is not null %}, "defaultValue": "{{ field.defaultValue }}"{% endif %} }{% if not loop.last %},{% endif %}
    {% endfor %}]{% endif %}
  }{% if not loop.last %},{% endif %}
{%- endfor %}
]
```

- [ ] **Step 4: Run the renderer module tests**

Run: `./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --rerun-tasks`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the renderer changes**

```bash
git add \
  cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/drawing-board/document.json.peb \
  cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: add drawing board pebble preset"
```

### Task 6: Wire Drawing-Board into Gradle and Prove the Slice End-to-End

**Files:**
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/build.gradle.kts`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelineExtension.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/drawing-board-sample/settings.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/drawing-board-sample/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/drawing-board-sample/analysis/app/build/cap4k-code-analysis/nodes.json`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/drawing-board-sample/analysis/app/build/cap4k-code-analysis/rels.json`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/drawing-board-sample/analysis/app/build/cap4k-code-analysis/design-elements.json`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/drawing-board-sample/template-overrides/.gitkeep`

- [ ] **Step 1: Add the failing functional test**

```kotlin
@Test
fun `cap4kGenerate writes drawing board json artifacts from ir analysis inputs`() {
    val fixtureDir = Files.createTempDirectory("pipeline-functional-drawing-board")
    copyFixture(fixtureDir, "drawing-board-sample")

    val result = GradleRunner.create()
        .withProjectDir(fixtureDir.toFile())
        .withArguments("cap4kGenerate", "--stacktrace")
        .withPluginClasspath()
        .build()

    assertEquals(TaskOutcome.SUCCESS, result.task(":cap4kGenerate")!!.outcome)
    assertTrue(fixtureDir.resolve("design/cli.json").toFile().exists())
    assertTrue(fixtureDir.resolve("design/cmd.json").toFile().exists())
    assertTrue(fixtureDir.resolve("design/cmd.json").toFile().readText().contains("\"name\": \"SubmitOrder\""))
}
```

- [ ] **Step 2: Run the Gradle functional test and confirm it fails**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kGenerate writes drawing board json artifacts from ir analysis inputs" --rerun-tasks`

Expected: test fails because the pipeline plugin does not register drawing-board generator wiring yet.

- [ ] **Step 3: Wire extension, config building, runner registration, and fixture**

```kotlin
// PipelineExtension.kt
val drawingBoardOutputDir: Property<String> = objects.property(String::class.java)

// buildConfig(...)
val drawingBoardEnabled = irInputDirs.isNotEmpty()

if (drawingBoardEnabled) {
    put(
        "drawing-board",
        GeneratorConfig(
            enabled = true,
            options = mapOf(
                "outputDir" to extension.drawingBoardOutputDir.optionalValue().orEmpty().ifBlank { "design" },
            ),
        )
    )
}

// buildRunner(...)
generators = listOf(
    DesignArtifactPlanner(),
    AggregateArtifactPlanner(),
    FlowArtifactPlanner(),
    DrawingBoardArtifactPlanner(),
)
```

```kotlin
// drawing-board-sample/build.gradle.kts
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4kPipeline {
    basePackage.set("demo.sample")
    templateOverrideDir.set("template-overrides")
    irAnalysisInputDirs.from("analysis/app/build/cap4k-code-analysis")
    drawingBoardOutputDir.set("design")
}
```

- [ ] **Step 4: Run the Gradle module tests**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --rerun-tasks`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Run the full slice verification**

Run: `./gradlew :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-source-ir-analysis:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-drawing-board:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test --rerun-tasks`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit the Gradle/TestKit wiring**

```bash
git add \
  cap4k-plugin-pipeline-gradle/build.gradle.kts \
  cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelineExtension.kt \
  cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt \
  cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt \
  cap4k-plugin-pipeline-gradle/src/test/resources/functional/drawing-board-sample \
  cap4k-plugin-pipeline-generator-drawing-board \
  cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/drawing-board/document.json.peb
git commit -m "feat: wire drawing board pipeline slice"
```
