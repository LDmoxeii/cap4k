# Cap4k Pipeline IR Flow Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fixture-tested `ir-analysis -> flow export` slice to the new pipeline that reads compiler `nodes.json` and `rels.json` outputs and generates per-entry flow JSON, Mermaid, and `index.json` artifacts.

**Architecture:** Extend the pipeline API with IR snapshot and canonical graph models, add a dedicated `source-ir-analysis` module that only parses and normalizes raw graph inputs, then add a `generator-flow` module that owns edge filtering, entry-node selection, traversal, slugging, JSON serialization, and Mermaid text preparation. Keep Gradle integration thin by wiring one new source option and one new generator option into the existing plugin, and prove the slice with unit tests plus a TestKit fixture that runs `cap4kPlan` and `cap4kGenerate`.

**Tech Stack:** Kotlin 2.2, Gradle Kotlin DSL, Gson, JUnit 5, Pebble, Gradle TestKit

---

## File Map

- Modify: `cap4k/settings.gradle.kts`
- Modify: `cap4k/cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
- Create: `cap4k/cap4k-plugin-pipeline-source-ir-analysis/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-source-ir-analysis/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProvider.kt`
- Create: `cap4k/cap4k-plugin-pipeline-source-ir-analysis/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProviderTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-flow/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlanner.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowGraphSupport.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-flow/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlannerTest.kt`
- Create: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/flow/entry.json.peb`
- Create: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/flow/entry.mmd.peb`
- Create: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/flow/index.json.peb`
- Modify: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/build.gradle.kts`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelineExtension.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/settings.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/analysis/app/build/cap4k-code-analysis/nodes.json`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/analysis/app/build/cap4k-code-analysis/rels.json`

### Task 1: Expand Pipeline API Models for IR Analysis Graphs

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
    fun `ir analysis snapshot preserves input dirs nodes and edges`() {
        val snapshot = IrAnalysisSnapshot(
            inputDirs = listOf("app/build/cap4k-code-analysis"),
            nodes = listOf(
                IrNodeSnapshot(
                    id = "OrderController::submit",
                    name = "OrderController::submit",
                    fullName = "com.acme.demo.adapter.web.OrderController::submit",
                    type = "controllermethod",
                )
            ),
            edges = listOf(
                IrEdgeSnapshot(
                    fromId = "OrderController::submit",
                    toId = "SubmitOrderCmd",
                    type = "ControllerMethodToCommand",
                    label = null,
                )
            ),
        )

        assertEquals("ir-analysis", snapshot.id)
        assertEquals("OrderController::submit", snapshot.nodes.single().id)
        assertEquals("ControllerMethodToCommand", snapshot.edges.single().type)
    }

    @Test
    fun `canonical model keeps optional analysis graph alongside existing slices`() {
        val graph = AnalysisGraphModel(
            inputDirs = listOf("app/build/cap4k-code-analysis"),
            nodes = listOf(
                AnalysisNodeModel(
                    id = "OrderController::submit",
                    name = "OrderController::submit",
                    fullName = "com.acme.demo.adapter.web.OrderController::submit",
                    type = "controllermethod",
                )
            ),
            edges = listOf(
                AnalysisEdgeModel(
                    fromId = "OrderController::submit",
                    toId = "SubmitOrderCmd",
                    type = "ControllerMethodToCommand",
                    label = null,
                )
            ),
        )

        val model = CanonicalModel(
            requests = listOf(
                RequestModel(
                    kind = RequestKind.COMMAND,
                    packageName = "order.submit",
                    typeName = "SubmitOrderCmd",
                    description = "submit order",
                )
            ),
            analysisGraph = graph,
        )

        assertEquals("SubmitOrderCmd", model.requests.single().typeName)
        assertEquals("OrderController::submit", model.analysisGraph!!.nodes.single().id)
        assertEquals("ControllerMethodToCommand", model.analysisGraph!!.edges.single().type)
    }

    @Test
    fun `canonical model defaults analysis graph to null`() {
        val model = CanonicalModel()
        assertNull(model.analysisGraph)
    }
}
```

- [ ] **Step 2: Run the API tests and confirm they fail**

Run: `./gradlew :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest" --rerun-tasks`

Expected: compilation fails because `IrAnalysisSnapshot`, `IrNodeSnapshot`, `IrEdgeSnapshot`, `AnalysisGraphModel`, `AnalysisNodeModel`, `AnalysisEdgeModel`, and `CanonicalModel.analysisGraph` do not exist yet.

- [ ] **Step 3: Add the new modules to settings and expand `PipelineModels.kt`**

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
    "cap4k-plugin-pipeline-gradle"
)

// cap4k/cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt
data class IrNodeSnapshot(
    val id: String,
    val name: String,
    val fullName: String,
    val type: String,
)

data class IrEdgeSnapshot(
    val fromId: String,
    val toId: String,
    val type: String,
    val label: String? = null,
)

data class IrAnalysisSnapshot(
    override val id: String = "ir-analysis",
    val inputDirs: List<String>,
    val nodes: List<IrNodeSnapshot>,
    val edges: List<IrEdgeSnapshot>,
) : SourceSnapshot

data class AnalysisNodeModel(
    val id: String,
    val name: String,
    val fullName: String,
    val type: String,
)

data class AnalysisEdgeModel(
    val fromId: String,
    val toId: String,
    val type: String,
    val label: String? = null,
)

data class AnalysisGraphModel(
    val inputDirs: List<String>,
    val nodes: List<AnalysisNodeModel>,
    val edges: List<AnalysisEdgeModel>,
)

data class CanonicalModel(
    val requests: List<RequestModel> = emptyList(),
    val schemas: List<SchemaModel> = emptyList(),
    val entities: List<EntityModel> = emptyList(),
    val repositories: List<RepositoryModel> = emptyList(),
    val analysisGraph: AnalysisGraphModel? = null,
)
```

- [ ] **Step 4: Run the API module tests**

Run: `./gradlew :cap4k-plugin-pipeline-api:test --rerun-tasks`

Expected: `BUILD SUCCESSFUL` and `PipelineModelsTest` plus existing API tests pass with the new IR graph models present.

- [ ] **Step 5: Commit the API model changes**

```bash
git add \
  settings.gradle.kts \
  cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt \
  cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt
git commit -m "feat: add pipeline ir analysis models"
```

### Task 2: Add the `source-ir-analysis` Module

**Files:**
- Create: `cap4k/cap4k-plugin-pipeline-source-ir-analysis/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-source-ir-analysis/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProvider.kt`
- Create: `cap4k/cap4k-plugin-pipeline-source-ir-analysis/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProviderTest.kt`

- [ ] **Step 1: Write the failing source tests**

```kotlin
package com.only4.cap4k.plugin.pipeline.source.ir

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.IrAnalysisSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import java.nio.file.Files
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IrAnalysisSourceProviderTest {

    @Test
    fun `collect merges input dirs normalizes blanks and preserves first node by id`() {
        val dirA = Files.createTempDirectory("cap4k-ir-a")
        val dirB = Files.createTempDirectory("cap4k-ir-b")

        dirA.resolve("nodes.json").writeText(
            """
            [
              {"id":"OrderController::submit","name":"","fullName":"","type":"controllermethod"},
              {"id":"SubmitOrderCmd","name":"SubmitOrderCmd","fullName":"com.acme.demo.SubmitOrderCmd","type":"command"}
            ]
            """.trimIndent()
        )
        dirA.resolve("rels.json").writeText(
            """
            [
              {"fromId":"OrderController::submit","toId":"SubmitOrderCmd","type":"ControllerMethodToCommand"}
            ]
            """.trimIndent()
        )
        dirB.resolve("nodes.json").writeText(
            """
            [
              {"id":"OrderController::submit","name":"later-value","fullName":"later-value","type":"controllermethod"},
              {"id":"SubmitOrderHandler","name":"SubmitOrderHandler","fullName":"com.acme.demo.SubmitOrderHandler","type":"commandhandler"}
            ]
            """.trimIndent()
        )
        dirB.resolve("rels.json").writeText(
            """
            [
              {"fromId":"SubmitOrderCmd","toId":"SubmitOrderHandler","type":"CommandToCommandHandler"}
            ]
            """.trimIndent()
        )

        val snapshot = IrAnalysisSourceProvider().collect(config(dirA.toString(), dirB.toString())) as IrAnalysisSnapshot

        assertEquals(listOf(dirA.toString(), dirB.toString()), snapshot.inputDirs)
        assertEquals(3, snapshot.nodes.size)
        assertEquals("submit", snapshot.nodes.first { it.id == "OrderController::submit" }.name)
        assertEquals("OrderController::submit", snapshot.nodes.first { it.id == "OrderController::submit" }.fullName)
        assertEquals(2, snapshot.edges.size)
        assertEquals("CommandToCommandHandler", snapshot.edges.last().type)
    }

    @Test
    fun `collect fails clearly when required files are missing`() {
        val dir = Files.createTempDirectory("cap4k-ir-missing")
        dir.resolve("nodes.json").writeText("""[]""")

        val error = assertThrows<IllegalArgumentException> {
            IrAnalysisSourceProvider().collect(config(dir.toString()))
        }

        assertTrue(error.message!!.contains("ir-analysis inputDir is missing nodes.json or rels.json"))
        assertTrue(error.message!!.contains(dir.toString()))
    }

    private fun config(vararg inputDirs: String): ProjectConfig {
        return ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "ir-analysis" to SourceConfig(
                    enabled = true,
                    options = mapOf("inputDirs" to inputDirs.toList()),
                )
            ),
            generators = emptyMap(),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
    }
}
```

- [ ] **Step 2: Run the source test and confirm it fails**

Run: `./gradlew :cap4k-plugin-pipeline-source-ir-analysis:test --tests "com.only4.cap4k.plugin.pipeline.source.ir.IrAnalysisSourceProviderTest" --rerun-tasks`

Expected: Gradle fails because the module, package, and source provider do not exist yet.

- [ ] **Step 3: Implement the source module and parser**

```kotlin
// cap4k/cap4k-plugin-pipeline-source-ir-analysis/build.gradle.kts
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

// cap4k/cap4k-plugin-pipeline-source-ir-analysis/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProvider.kt
package com.only4.cap4k.plugin.pipeline.source.ir

import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.IrAnalysisSnapshot
import com.only4.cap4k.plugin.pipeline.api.IrEdgeSnapshot
import com.only4.cap4k.plugin.pipeline.api.IrNodeSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import java.io.File

class IrAnalysisSourceProvider : SourceProvider {
    override val id: String = "ir-analysis"

    override fun collect(config: ProjectConfig): IrAnalysisSnapshot {
        val inputDirs = ((config.sources[id]?.options?.get("inputDirs") as? List<*>) ?: emptyList<Any>())
            .map { it.toString().trim() }
            .filter { it.isNotEmpty() }
        require(inputDirs.isNotEmpty()) { "ir-analysis source requires at least one inputDirs entry." }

        val nodesById = linkedMapOf<String, IrNodeSnapshot>()
        val edgeKeys = linkedSetOf<EdgeKey>()

        inputDirs.forEach { inputDir ->
            val dir = File(inputDir)
            require(dir.exists() && dir.isDirectory) { "ir-analysis inputDir does not exist or is not a directory: $inputDir" }

            val nodesFile = File(dir, "nodes.json")
            val relsFile = File(dir, "rels.json")
            require(nodesFile.exists() && relsFile.exists()) {
                "ir-analysis inputDir is missing nodes.json or rels.json: $inputDir"
            }

            parseNodes(nodesFile).forEach { node ->
                nodesById.putIfAbsent(node.id, node)
            }
            parseEdges(relsFile).forEach { edge ->
                edgeKeys.add(EdgeKey(edge.fromId, edge.toId, edge.type, edge.label))
            }
        }

        return IrAnalysisSnapshot(
            inputDirs = inputDirs,
            nodes = nodesById.values.toList(),
            edges = edgeKeys.map { key ->
                IrEdgeSnapshot(
                    fromId = key.fromId,
                    toId = key.toId,
                    type = key.type,
                    label = key.label,
                )
            },
        )
    }

    private fun parseNodes(file: File): List<IrNodeSnapshot> {
        val array = file.reader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonArray }
        return array.mapNotNull { element ->
            val obj = element.asJsonObject
            val id = obj["id"]?.asString?.trim().orEmpty()
            if (id.isEmpty()) {
                return@mapNotNull null
            }
            val normalizedName = obj["name"]?.asString?.trim().orEmpty().ifBlank { shortNameForId(id) }
            val normalizedFullName = obj["fullName"]?.asString?.trim().orEmpty().ifBlank { id }
            val normalizedType = obj["type"]?.asString?.trim().orEmpty().ifBlank { "unknown" }
            IrNodeSnapshot(
                id = id,
                name = normalizedName,
                fullName = normalizedFullName,
                type = normalizedType,
            )
        }
    }

    private fun parseEdges(file: File): List<IrEdgeSnapshot> {
        val array = file.reader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonArray }
        return array.mapNotNull { element ->
            val obj = element.asJsonObject
            val fromId = obj["fromId"]?.asString?.trim().orEmpty()
            val toId = obj["toId"]?.asString?.trim().orEmpty()
            val type = obj["type"]?.asString?.trim().orEmpty()
            if (fromId.isEmpty() || toId.isEmpty() || type.isEmpty()) {
                return@mapNotNull null
            }
            IrEdgeSnapshot(
                fromId = fromId,
                toId = toId,
                type = type,
                label = obj["label"]?.asString,
            )
        }
    }

    private fun shortNameForId(id: String): String {
        val normalized = id.replace('$', '.')
        val byMethod = normalized.substringAfterLast("::", missingDelimiterValue = normalized)
        return byMethod.substringAfterLast('.')
    }
}

private data class EdgeKey(
    val fromId: String,
    val toId: String,
    val type: String,
    val label: String?,
)
```

- [ ] **Step 4: Run the new source module tests**

Run: `./gradlew :cap4k-plugin-pipeline-source-ir-analysis:test --rerun-tasks`

Expected: `BUILD SUCCESSFUL` and the tests prove merged parsing, blank-field normalization, duplicate-node handling, and missing-file failures.

- [ ] **Step 5: Commit the source module**

```bash
git add \
  cap4k-plugin-pipeline-source-ir-analysis/build.gradle.kts \
  cap4k-plugin-pipeline-source-ir-analysis/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProvider.kt \
  cap4k-plugin-pipeline-source-ir-analysis/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProviderTest.kt
git commit -m "feat: add pipeline ir analysis source"
```

### Task 3: Extend the Canonical Assembler for Analysis Graphs

**Files:**
- Modify: `cap4k/cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Add the failing assembler tests**

```kotlin
    @Test
    fun `maps ir analysis snapshot into canonical analysis graph`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                IrAnalysisSnapshot(
                    inputDirs = listOf("app/build/cap4k-code-analysis"),
                    nodes = listOf(
                        IrNodeSnapshot(
                            id = "OrderController::submit",
                            name = "OrderController::submit",
                            fullName = "com.acme.demo.adapter.web.OrderController::submit",
                            type = "controllermethod",
                        ),
                        IrNodeSnapshot(
                            id = "SubmitOrderCmd",
                            name = "SubmitOrderCmd",
                            fullName = "com.acme.demo.application.commands.SubmitOrderCmd",
                            type = "command",
                        ),
                    ),
                    edges = listOf(
                        IrEdgeSnapshot(
                            fromId = "OrderController::submit",
                            toId = "SubmitOrderCmd",
                            type = "ControllerMethodToCommand",
                        )
                    ),
                )
            ),
        )

        assertEquals(listOf("app/build/cap4k-code-analysis"), model.analysisGraph!!.inputDirs)
        assertEquals(2, model.analysisGraph!!.nodes.size)
        assertEquals("controllermethod", model.analysisGraph!!.nodes.first().type)
        assertEquals("ControllerMethodToCommand", model.analysisGraph!!.edges.single().type)
    }

    @Test
    fun `keeps analysis graph null when ir snapshot is absent`() {
        val assembler = DefaultCanonicalAssembler()
        val model = assembler.assemble(config = baseConfig(), snapshots = emptyList())
        assertNull(model.analysisGraph)
    }
```

- [ ] **Step 2: Run the assembler test and confirm it fails**

Run: `./gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest" --rerun-tasks`

Expected: compilation fails because `DefaultCanonicalAssembler` does not map `IrAnalysisSnapshot` into `CanonicalModel.analysisGraph`.

- [ ] **Step 3: Extend `DefaultCanonicalAssembler`**

```kotlin
// cap4k/cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt
package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AnalysisEdgeModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisGraphModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisNodeModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.IrAnalysisSnapshot
import com.only4.cap4k.plugin.pipeline.api.KspMetadataSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.RequestKind
import com.only4.cap4k.plugin.pipeline.api.RequestModel
import com.only4.cap4k.plugin.pipeline.api.RepositoryModel
import com.only4.cap4k.plugin.pipeline.api.SchemaModel
import com.only4.cap4k.plugin.pipeline.api.SourceSnapshot
import java.util.Locale

class DefaultCanonicalAssembler : CanonicalAssembler {
    override fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalModel {
        val designSnapshot = snapshots.filterIsInstance<DesignSpecSnapshot>().firstOrNull()
        val irSnapshot = snapshots.filterIsInstance<IrAnalysisSnapshot>().firstOrNull()
        val dbTables = snapshots.filterIsInstance<DbSchemaSnapshot>().flatMap { it.tables }

        val aggregateLookup = snapshots
            .filterIsInstance<KspMetadataSnapshot>()
            .flatMap { it.aggregates }
            .associateBy { it.aggregateName }

        val requests = designSnapshot?.entries.orEmpty().mapNotNull { entry ->
            val kind = when (entry.tag.lowercase(Locale.ROOT)) {
                "cmd", "command" -> RequestKind.COMMAND
                "qry", "query" -> RequestKind.QUERY
                else -> return@mapNotNull null
            }
            val aggregateName = entry.aggregates.firstOrNull()
            val aggregate = aggregateName?.let { aggregateLookup[it] }
            RequestModel(
                kind = kind,
                packageName = entry.packageName,
                typeName = if (kind == RequestKind.COMMAND) "${entry.name}Cmd" else "${entry.name}Qry",
                description = entry.description,
                aggregateName = aggregateName,
                aggregatePackageName = aggregate?.rootPackageName,
                requestFields = entry.requestFields,
                responseFields = entry.responseFields,
            )
        }

        val analysisGraph = irSnapshot?.let { snapshot ->
            AnalysisGraphModel(
                inputDirs = snapshot.inputDirs,
                nodes = snapshot.nodes.map { node ->
                    AnalysisNodeModel(
                        id = node.id,
                        name = node.name,
                        fullName = node.fullName,
                        type = node.type,
                    )
                },
                edges = snapshot.edges.map { edge ->
                    AnalysisEdgeModel(
                        fromId = edge.fromId,
                        toId = edge.toId,
                        type = edge.type,
                        label = edge.label,
                    )
                },
            )
        }

        val aggregateModels = dbTables.map { table ->
            require(table.primaryKey.isNotEmpty()) { "db table ${table.tableName} must define a primary key" }
            require(table.primaryKey.size == 1) { "db table ${table.tableName} must define a single-column primary key" }

            val entityName = AggregateNaming.entityName(table.tableName)
            val schemaName = AggregateNaming.schemaName(table.tableName)
            val repositoryName = AggregateNaming.repositoryName(table.tableName)
            val segment = AggregateNaming.tableSegment(table.tableName)
            val fields = table.columns.map {
                FieldModel(
                    name = it.name,
                    type = it.kotlinType,
                    nullable = it.nullable,
                    defaultValue = it.defaultValue,
                )
            }
            val idField = fields.first { it.name == table.primaryKey.first() }

            Triple(
                SchemaModel(
                    name = schemaName,
                    packageName = "${config.basePackage}.domain._share.meta.$segment",
                    entityName = entityName,
                    comment = table.comment,
                    fields = fields,
                ),
                EntityModel(
                    name = entityName,
                    packageName = "${config.basePackage}.domain.aggregates.$segment",
                    tableName = table.tableName,
                    comment = table.comment,
                    fields = fields,
                    idField = idField,
                ),
                RepositoryModel(
                    name = repositoryName,
                    packageName = "${config.basePackage}.adapter.domain.repositories",
                    entityName = entityName,
                    idType = idField.type,
                ),
            )
        }

        return CanonicalModel(
            requests = requests,
            schemas = aggregateModels.map { it.first },
            entities = aggregateModels.map { it.second },
            repositories = aggregateModels.map { it.third },
            analysisGraph = analysisGraph,
        )
    }
}
```

- [ ] **Step 4: Run the core tests**

Run: `./gradlew :cap4k-plugin-pipeline-core:test --rerun-tasks`

Expected: `BUILD SUCCESSFUL`; the new IR mapping tests pass and the existing design plus aggregate assembler tests still pass unchanged.

- [ ] **Step 5: Commit the canonical assembler update**

```bash
git add \
  cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt \
  cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: map ir analysis into canonical graph"
```

### Task 4: Add the Flow Generator Module

**Files:**
- Create: `cap4k/cap4k-plugin-pipeline-generator-flow/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlanner.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowGraphSupport.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-flow/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlannerTest.kt`

- [ ] **Step 1: Write the failing flow planner tests**

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.flow

import com.only4.cap4k.plugin.pipeline.api.AnalysisEdgeModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisGraphModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisNodeModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlowArtifactPlannerTest {

    @Test
    fun `plans json mermaid and index artifacts from allowed entry graph`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    AnalysisNodeModel("OrderController::submit", "OrderController::submit", "OrderController::submit", "controllermethod"),
                    AnalysisNodeModel("SubmitOrderCmd", "SubmitOrderCmd", "SubmitOrderCmd", "command"),
                    AnalysisNodeModel("SubmitOrderHandler", "SubmitOrderHandler", "SubmitOrderHandler", "commandhandler"),
                    AnalysisNodeModel("IgnoredAggregate", "IgnoredAggregate", "IgnoredAggregate", "aggregate"),
                ),
                edges = listOf(
                    AnalysisEdgeModel("OrderController::submit", "SubmitOrderCmd", "ControllerMethodToCommand"),
                    AnalysisEdgeModel("SubmitOrderCmd", "SubmitOrderHandler", "CommandToCommandHandler"),
                    AnalysisEdgeModel("SubmitOrderHandler", "IgnoredAggregate", "CommandHandlerToAggregate"),
                ),
            )
        )

        val plan = planner.plan(config(), model)

        assertEquals(3, plan.size)
        assertEquals("flow/entry.json.peb", plan[0].templateId)
        assertEquals("flows/OrderController_submit.json", plan[0].outputPath)
        assertEquals("flow/entry.mmd.peb", plan[1].templateId)
        assertEquals("flows/OrderController_submit.mmd", plan[1].outputPath)
        assertEquals("flow/index.json.peb", plan[2].templateId)
        assertEquals("flows/index.json", plan[2].outputPath)
        assertTrue((plan[0].context["jsonContent"] as String).contains("\"edgeCount\": 2"))
        assertTrue((plan[1].context["mermaidText"] as String).contains("flowchart TD"))
        assertTrue((plan[2].context["jsonContent"] as String).contains("\"flowCount\": 1"))
        assertTrue(!(plan[0].context["jsonContent"] as String).contains("IgnoredAggregate"))
    }

    @Test
    fun `adds digest suffix when slugified entry ids collide`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    AnalysisNodeModel("OrderController::submit", "OrderController::submit", "OrderController::submit", "controllermethod"),
                    AnalysisNodeModel("OrderController submit", "OrderController submit", "OrderController submit", "controllermethod"),
                ),
                edges = emptyList(),
            )
        )

        val plan = planner.plan(config(), model)
        val jsonOutputs = plan.filter { it.templateId == "flow/entry.json.peb" }.map { it.outputPath }

        assertEquals(2, jsonOutputs.size)
        assertEquals("flows/OrderController_submit.json", jsonOutputs.first())
        assertTrue(Regex("""flows/OrderController_submit_[0-9a-f]{8}\.json""").matches(jsonOutputs.last()))
    }

    private fun config(): ProjectConfig {
        return ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = emptyMap(),
            generators = mapOf(
                "flow" to GeneratorConfig(
                    enabled = true,
                    options = mapOf("outputDir" to "flows"),
                )
            ),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
    }
}
```

- [ ] **Step 2: Run the planner test and confirm it fails**

Run: `./gradlew :cap4k-plugin-pipeline-generator-flow:test --tests "com.only4.cap4k.plugin.pipeline.generator.flow.FlowArtifactPlannerTest" --rerun-tasks`

Expected: Gradle fails because the flow generator module, planner, and helper types do not exist yet.

- [ ] **Step 3: Implement the flow planner and helpers**

```kotlin
// cap4k/cap4k-plugin-pipeline-generator-flow/build.gradle.kts
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

// cap4k/cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlanner.kt
package com.only4.cap4k.plugin.pipeline.generator.flow

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import java.nio.file.InvalidPathException
import java.nio.file.Path

class FlowArtifactPlanner : GeneratorProvider {
    override val id: String = "flow"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val graph = model.analysisGraph ?: return emptyList()
        val outputDir = requireRelativeOutputDir(config)
        val plannedFlows = buildPlannedFlows(graph)

        val entryArtifacts = plannedFlows.entries.flatMap { flow ->
            listOf(
                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "project",
                    templateId = "flow/entry.json.peb",
                    outputPath = "$outputDir/${flow.slug}.json",
                    context = mapOf("jsonContent" to flow.jsonContent),
                    conflictPolicy = config.templates.conflictPolicy,
                ),
                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "project",
                    templateId = "flow/entry.mmd.peb",
                    outputPath = "$outputDir/${flow.slug}.mmd",
                    context = mapOf("mermaidText" to flow.mermaidText),
                    conflictPolicy = config.templates.conflictPolicy,
                ),
            )
        }

        return entryArtifacts + ArtifactPlanItem(
            generatorId = id,
            moduleRole = "project",
            templateId = "flow/index.json.peb",
            outputPath = "$outputDir/index.json",
            context = mapOf("jsonContent" to plannedFlows.indexJsonContent),
            conflictPolicy = config.templates.conflictPolicy,
        )
    }

    private fun requireRelativeOutputDir(config: ProjectConfig): String {
        val rawValue = config.generators[id]?.options?.get("outputDir")?.toString()?.trim().orEmpty().ifBlank { "flows" }
        val path = try {
            Path.of(rawValue)
        } catch (ex: InvalidPathException) {
            throw IllegalArgumentException("flow outputDir must be a valid relative filesystem path: $rawValue", ex)
        }
        require(!path.isAbsolute) { "flow outputDir must be a valid relative filesystem path: $rawValue" }
        require(path.normalize().toString() == rawValue || !path.normalize().startsWith("..")) {
            "flow outputDir must be a valid relative filesystem path: $rawValue"
        }
        return rawValue.replace('\\', '/').trimEnd('/')
    }
}

// cap4k/cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowGraphSupport.kt
package com.only4.cap4k.plugin.pipeline.generator.flow

import com.google.gson.GsonBuilder
import com.only4.cap4k.plugin.pipeline.api.AnalysisEdgeModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisGraphModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisNodeModel
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

internal data class PlannedFlowEntry(
    val slug: String,
    val jsonContent: String,
    val mermaidText: String,
    val indexEntry: FlowIndexEntryPayload,
)

internal data class PlannedFlowSet(
    val entries: List<PlannedFlowEntry>,
    val indexJsonContent: String,
)

private data class FlowNodePayload(
    val id: String,
    val name: String,
    val fullName: String,
    val type: String,
)

private data class FlowEdgePayload(
    val fromId: String,
    val toId: String,
    val type: String,
    val label: String?,
)

private data class FlowEntryPayload(
    val entryId: String,
    val entryType: String,
    val nodeCount: Int,
    val edgeCount: Int,
    val nodes: List<FlowNodePayload>,
    val edges: List<FlowEdgePayload>,
)

internal data class FlowIndexEntryPayload(
    val entryId: String,
    val entryType: String,
    val nodeCount: Int,
    val edgeCount: Int,
    val json: String,
    val mermaid: String,
)

private data class FlowIndexPayload(
    val generatedAt: String,
    val inputDirs: List<String>,
    val entryTypes: List<String>,
    val entryTypeCounts: Map<String, Int>,
    val nodeCount: Int,
    val edgeCount: Int,
    val flowCount: Int,
    val flows: List<FlowIndexEntryPayload>,
)

private val gson = GsonBuilder()
    .setPrettyPrinting()
    .disableHtmlEscaping()
    .create()

private val allowedEdgeTypes = setOf(
    "ControllerMethodToCommand",
    "ControllerMethodToQuery",
    "ControllerMethodToCli",
    "CommandSenderMethodToCommand",
    "QuerySenderMethodToQuery",
    "CliSenderMethodToCli",
    "ValidatorToQuery",
    "CommandToCommandHandler",
    "QueryToQueryHandler",
    "CliToCliHandler",
    "CommandHandlerToEntityMethod",
    "EntityMethodToEntityMethod",
    "EntityMethodToDomainEvent",
    "DomainEventToHandler",
    "DomainEventHandlerToCommand",
    "DomainEventHandlerToQuery",
    "DomainEventHandlerToCli",
    "DomainEventToIntegrationEvent",
    "IntegrationEventToHandler",
    "IntegrationEventHandlerToCommand",
    "IntegrationEventHandlerToQuery",
    "IntegrationEventHandlerToCli",
)

private val entryNodeTypes = setOf(
    "controllermethod",
    "commandsendermethod",
    "querysendermethod",
    "clisendermethod",
    "validator",
    "integrationevent",
)

internal fun buildPlannedFlows(graph: AnalysisGraphModel): PlannedFlowSet {
    val nodesById = graph.nodes.associateBy { it.id }
    val edges = graph.edges
        .filter { it.type in allowedEdgeTypes }
        .distinctBy { listOf(it.fromId, it.toId, it.type, it.label) }
    val adjacency = edges.groupBy { it.fromId }
    val entryNodes = graph.nodes
        .filter { it.type.lowercase() in entryNodeTypes }
        .sortedBy { it.id }

    val usedSlugs = linkedSetOf<String>()
    val plannedEntries = entryNodes.map { entry ->
        val flowGraph = collectFlow(entry.id, nodesById, adjacency)
        val slug = slugify(entry.id, usedSlugs)
        val payload = FlowEntryPayload(
            entryId = entry.id,
            entryType = if (entry.id == "<anonymous>") "<anonymous>" else entry.type,
            nodeCount = flowGraph.nodes.size,
            edgeCount = flowGraph.edges.size,
            nodes = flowGraph.nodes.map { FlowNodePayload(it.id, it.name, it.fullName, it.type) },
            edges = flowGraph.edges.map { FlowEdgePayload(it.fromId, it.toId, it.type, it.label) },
        )
        PlannedFlowEntry(
            slug = slug,
            jsonContent = gson.toJson(payload),
            mermaidText = renderMermaid(flowGraph.nodes, flowGraph.edges),
            indexEntry = FlowIndexEntryPayload(
                entryId = payload.entryId,
                entryType = payload.entryType,
                nodeCount = payload.nodeCount,
                edgeCount = payload.edgeCount,
                json = "$slug.json",
                mermaid = "$slug.mmd",
            ),
        )
    }

    val entryTypeCounts = plannedEntries
        .groupingBy { it.indexEntry.entryType }
        .eachCount()
        .toSortedMap()
    val indexPayload = FlowIndexPayload(
        generatedAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString(),
        inputDirs = graph.inputDirs,
        entryTypes = entryTypeCounts.keys.toList(),
        entryTypeCounts = entryTypeCounts,
        nodeCount = nodesById.size,
        edgeCount = edges.size,
        flowCount = plannedEntries.size,
        flows = plannedEntries.map { it.indexEntry },
    )
    return PlannedFlowSet(
        entries = plannedEntries,
        indexJsonContent = gson.toJson(indexPayload),
    )
}

private data class FlowGraph(
    val nodes: List<AnalysisNodeModel>,
    val edges: List<AnalysisEdgeModel>,
)

private fun collectFlow(
    entryId: String,
    nodesById: Map<String, AnalysisNodeModel>,
    adjacency: Map<String, List<AnalysisEdgeModel>>,
): FlowGraph {
    val visitedNodes = linkedSetOf(entryId)
    val visitedEdges = linkedSetOf<List<String?>>()
    val stack = ArrayDeque<String>()
    stack.add(entryId)

    while (stack.isNotEmpty()) {
        val current = stack.removeLast()
        adjacency[current].orEmpty().forEach { edge ->
            val edgeKey = listOf(edge.fromId, edge.toId, edge.type, edge.label)
            if (!visitedEdges.add(edgeKey)) {
                return@forEach
            }
            if (visitedNodes.add(edge.toId)) {
                stack.add(edge.toId)
            }
        }
    }

    val nodes = visitedNodes.map { nodeId ->
        nodesById[nodeId] ?: AnalysisNodeModel(
            id = nodeId,
            name = nodeId.substringAfterLast("::", nodeId).substringAfterLast('.'),
            fullName = nodeId,
            type = "unknown",
        )
    }.sortedBy { it.id }
    val edges = visitedEdges.map { key ->
        AnalysisEdgeModel(
            fromId = key[0]!!,
            toId = key[1]!!,
            type = key[2]!!,
            label = key[3],
        )
    }.sortedWith(compareBy<AnalysisEdgeModel> { it.fromId }.thenBy { it.toId }.thenBy { it.type })

    return FlowGraph(nodes = nodes, edges = edges)
}

private fun renderMermaid(nodes: List<AnalysisNodeModel>, edges: List<AnalysisEdgeModel>): String {
    val idMap = linkedMapOf<String, String>()
    val lines = mutableListOf("flowchart TD")

    nodes.forEachIndexed { index, node ->
        val localId = "N${index + 1}"
        idMap[node.id] = localId
        val label = node.name.ifBlank { node.id }
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "'")
        lines.add("  $localId[$label]")
    }

    edges.forEach { edge ->
        val fromId = idMap[edge.fromId]
        val toId = idMap[edge.toId]
        if (fromId != null && toId != null) {
            lines.add("  $fromId -->|${edge.type}| $toId")
        }
    }

    return lines.joinToString("\n", postfix = "\n")
}

private fun slugify(text: String, used: MutableSet<String>): String {
    var slug = text.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
    if (slug.isEmpty()) slug = "entry"
    slug = slug.take(80)
    if (used.contains(slug)) {
        val digest = MessageDigest.getInstance("MD5")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        slug = "${slug}_${digest.take(8)}"
    }
    used.add(slug)
    return slug
}
```

- [ ] **Step 4: Run the generator module tests**

Run: `./gradlew :cap4k-plugin-pipeline-generator-flow:test --rerun-tasks`

Expected: `BUILD SUCCESSFUL` and the planner tests prove allowed-edge filtering, per-entry artifact planning, Mermaid generation, and collision-safe slugging.

- [ ] **Step 5: Commit the flow generator**

```bash
git add \
  cap4k-plugin-pipeline-generator-flow/build.gradle.kts \
  cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlanner.kt \
  cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowGraphSupport.kt \
  cap4k-plugin-pipeline-generator-flow/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlannerTest.kt
git commit -m "feat: add pipeline flow generator"
```

### Task 5: Add Flow Pebble Templates and Renderer Coverage

**Files:**
- Create: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/flow/entry.json.peb`
- Create: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/flow/entry.mmd.peb`
- Create: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/flow/index.json.peb`
- Modify: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Add the failing flow renderer test**

```kotlin
    @Test
    fun `falls back to preset flow templates and renders flow artifacts`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-flow")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "flow",
                    moduleRole = "project",
                    templateId = "flow/entry.json.peb",
                    outputPath = "flows/OrderController_submit.json",
                    context = mapOf("jsonContent" to "{\n  \"entryId\": \"OrderController::submit\"\n}"),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "flow",
                    moduleRole = "project",
                    templateId = "flow/entry.mmd.peb",
                    outputPath = "flows/OrderController_submit.mmd",
                    context = mapOf("mermaidText" to "flowchart TD\n  N1[OrderController::submit]\n"),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "flow",
                    moduleRole = "project",
                    templateId = "flow/index.json.peb",
                    outputPath = "flows/index.json",
                    context = mapOf("jsonContent" to "{\n  \"flowCount\": 1\n}"),
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

        assertTrue(rendered[0].content.contains("\"entryId\": \"OrderController::submit\""))
        assertTrue(rendered[1].content.contains("flowchart TD"))
        assertTrue(rendered[2].content.contains("\"flowCount\": 1"))
    }
```

- [ ] **Step 2: Run the renderer test and confirm it fails**

Run: `./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --rerun-tasks`

Expected: the renderer fails with `Template not found: presets/ddd-default/flow/...` because the flow preset templates do not exist yet.

- [ ] **Step 3: Add the flow preset templates**

```twig
{# cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/flow/entry.json.peb #}
{{ jsonContent }}

{# cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/flow/entry.mmd.peb #}
{{ mermaidText }}

{# cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/flow/index.json.peb #}
{{ jsonContent }}
```

- [ ] **Step 4: Run the renderer suite**

Run: `./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --rerun-tasks`

Expected: `BUILD SUCCESSFUL`; existing design and aggregate renderer coverage still passes and the new flow renderer test proves preset fallback for JSON and Mermaid artifacts.

- [ ] **Step 5: Commit the renderer template changes**

```bash
git add \
  cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/flow/entry.json.peb \
  cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/flow/entry.mmd.peb \
  cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/flow/index.json.peb \
  cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: add flow renderer templates"
```

### Task 6: Wire Gradle Integration and Add a TestKit Flow Fixture

**Files:**
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/build.gradle.kts`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelineExtension.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/settings.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/analysis/app/build/cap4k-code-analysis/nodes.json`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/analysis/app/build/cap4k-code-analysis/rels.json`

- [ ] **Step 1: Add the failing Gradle functional tests**

```kotlin
    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan and cap4kGenerate produce flow artifacts from ir analysis fixture`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-flow")
        copyFixture(projectDir, "flow-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan", "cap4kGenerate")
            .build()

        val planFile = projectDir.resolve("build/cap4k/plan.json")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planFile.toFile().exists())
        assertTrue(planFile.readText().contains("\"templateId\": \"flow/index.json.peb\""))
        assertTrue(projectDir.resolve("flows/OrderController_submit.json").toFile().exists())
        assertTrue(projectDir.resolve("flows/OrderController_submit.mmd").toFile().exists())
        assertTrue(projectDir.resolve("flows/index.json").toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan fails clearly when ir analysis fixture misses rels json`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-flow-invalid")
        copyFixture(projectDir, "flow-sample")
        projectDir.resolve("analysis/app/build/cap4k-code-analysis/rels.json").toFile().delete()

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(result.output.contains("ir-analysis inputDir is missing nodes.json or rels.json"))
        assertFalse(projectDir.resolve("build/cap4k/plan.json").toFile().exists())
    }
```

- [ ] **Step 2: Run the Gradle functional tests and confirm they fail**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --rerun-tasks`

Expected: the new fixture tests fail because the Gradle extension, config builder, and runner do not know about `irAnalysisInputDirs`, `flowOutputDir`, `IrAnalysisSourceProvider`, or `FlowArtifactPlanner`.

- [ ] **Step 3: Wire the new source and generator into the Gradle plugin and add the fixture**

```kotlin
// cap4k/cap4k-plugin-pipeline-gradle/build.gradle.kts
dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation(project(":cap4k-plugin-pipeline-api"))
    implementation(project(":cap4k-plugin-pipeline-core"))
    implementation(project(":cap4k-plugin-pipeline-generator-aggregate"))
    implementation(project(":cap4k-plugin-pipeline-generator-design"))
    implementation(project(":cap4k-plugin-pipeline-generator-flow"))
    implementation(project(":cap4k-plugin-pipeline-renderer-api"))
    implementation(project(":cap4k-plugin-pipeline-renderer-pebble"))
    implementation(project(":cap4k-plugin-pipeline-source-db"))
    implementation(project(":cap4k-plugin-pipeline-source-design-json"))
    implementation(project(":cap4k-plugin-pipeline-source-ir-analysis"))
    implementation(project(":cap4k-plugin-pipeline-source-ksp-metadata"))
    implementation(libs.gson)
    runtimeOnly(libs.h2)
    testImplementation(gradleTestKit())
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelineExtension.kt
open class PipelineExtension @Inject constructor(objects: ObjectFactory) {
    val basePackage: Property<String> = objects.property(String::class.java)
    val applicationModulePath: Property<String> = objects.property(String::class.java)
    val domainModulePath: Property<String> = objects.property(String::class.java)
    val adapterModulePath: Property<String> = objects.property(String::class.java)
    val designFiles: ConfigurableFileCollection = objects.fileCollection()
    val kspMetadataDir: Property<String> = objects.property(String::class.java)
    val irAnalysisInputDirs: ConfigurableFileCollection = objects.fileCollection()
    val flowOutputDir: Property<String> = objects.property(String::class.java)
    val dbUrl: Property<String> = objects.property(String::class.java)
    val dbUsername: Property<String> = objects.property(String::class.java)
    val dbPassword: Property<String> = objects.property(String::class.java)
    val dbSchema: Property<String> = objects.property(String::class.java)
    val dbIncludeTables: ListProperty<String> = objects.listProperty(String::class.java)
    val dbExcludeTables: ListProperty<String> = objects.listProperty(String::class.java)
    val templateOverrideDir: Property<String> = objects.property(String::class.java)
}

// cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt
import com.only4.cap4k.plugin.pipeline.generator.flow.FlowArtifactPlanner
import com.only4.cap4k.plugin.pipeline.source.ir.IrAnalysisSourceProvider

internal fun buildConfig(project: Project, extension: PipelineExtension): ProjectConfig {
    val aggregateConfig = aggregateConfigState(extension)
    validateAggregateConfig(aggregateConfig)

    val modules = buildMap {
        extension.applicationModulePath.optionalValue()?.let { put("application", it) }
        aggregateConfig.domainModulePath?.let { put("domain", it) }
        aggregateConfig.adapterModulePath?.let { put("adapter", it) }
    }
    val designJsonEnabled = extension.designFiles.files.isNotEmpty()
    val kspMetadataDir = extension.kspMetadataDir.optionalValue()
    val irInputDirs = extension.irAnalysisInputDirs.files.map { it.absolutePath }.sorted()
    val aggregateEnabled = aggregateConfig.dbUrl != null && "domain" in modules && "adapter" in modules
    val flowEnabled = irInputDirs.isNotEmpty()

    return ProjectConfig(
        basePackage = extension.basePackage.get(),
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = buildMap {
            if (designJsonEnabled) {
                put("design-json", SourceConfig(true, mapOf("files" to extension.designFiles.files.map { it.absolutePath })))
            }
            if (kspMetadataDir != null) {
                put("ksp-metadata", SourceConfig(true, mapOf("inputDir" to project.file(kspMetadataDir).absolutePath)))
            }
            if (aggregateConfig.dbUrl != null) {
                put(
                    "db",
                    SourceConfig(
                        enabled = true,
                        options = mapOf(
                            "url" to aggregateConfig.dbUrl,
                            "username" to extension.dbUsername.orNull.orEmpty(),
                            "password" to extension.dbPassword.orNull.orEmpty(),
                            "schema" to extension.dbSchema.orNull.orEmpty(),
                            "includeTables" to extension.dbIncludeTables.orNull.orEmpty(),
                            "excludeTables" to extension.dbExcludeTables.orNull.orEmpty(),
                        )
                    )
                )
            }
            if (flowEnabled) {
                put(
                    "ir-analysis",
                    SourceConfig(
                        enabled = true,
                        options = mapOf("inputDirs" to irInputDirs),
                    )
                )
            }
        },
        generators = buildMap {
            if (designJsonEnabled) {
                put("design", GeneratorConfig(enabled = true))
            }
            if (aggregateEnabled) {
                put("aggregate", GeneratorConfig(enabled = true))
            }
            if (flowEnabled) {
                put(
                    "flow",
                    GeneratorConfig(
                        enabled = true,
                        options = mapOf(
                            "outputDir" to extension.flowOutputDir.optionalValue().orEmpty().ifBlank { "flows" }
                        )
                    )
                )
            }
        },
        templates = TemplateConfig(
            preset = "ddd-default",
            overrideDirs = listOf(project.file(extension.templateOverrideDir.get()).absolutePath),
            conflictPolicy = ConflictPolicy.SKIP,
        ),
    )
}

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
            AggregateArtifactPlanner(),
            FlowArtifactPlanner(),
        ),
        assembler = DefaultCanonicalAssembler(),
        renderer = PebbleArtifactRenderer(
            PresetTemplateResolver(
                preset = config.templates.preset,
                overrideDirs = config.templates.overrideDirs,
            )
        ),
        exporter = if (exportEnabled) FilesystemArtifactExporter(project.projectDir.toPath()) else NoopArtifactExporter(),
    )
}

// cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/settings.gradle.kts
rootProject.name = "flow-sample"

// cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/build.gradle.kts
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4kPipeline {
    basePackage.set("com.acme.demo")
    irAnalysisInputDirs.from("analysis/app/build/cap4k-code-analysis")
    flowOutputDir.set("flows")
    templateOverrideDir.set("template-overrides")
}

// cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/analysis/app/build/cap4k-code-analysis/nodes.json
[
  {
    "id": "OrderController::submit",
    "name": "OrderController::submit",
    "fullName": "com.acme.demo.adapter.web.OrderController::submit",
    "type": "controllermethod"
  },
  {
    "id": "SubmitOrderCmd",
    "name": "SubmitOrderCmd",
    "fullName": "com.acme.demo.application.commands.SubmitOrderCmd",
    "type": "command"
  },
  {
    "id": "SubmitOrderHandler",
    "name": "SubmitOrderHandler",
    "fullName": "com.acme.demo.application.commands.SubmitOrderHandler",
    "type": "commandhandler"
  }
]

// cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/analysis/app/build/cap4k-code-analysis/rels.json
[
  {
    "fromId": "OrderController::submit",
    "toId": "SubmitOrderCmd",
    "type": "ControllerMethodToCommand"
  },
  {
    "fromId": "SubmitOrderCmd",
    "toId": "SubmitOrderHandler",
    "type": "CommandToCommandHandler"
  }
]
```

- [ ] **Step 4: Run the Gradle plugin test suite**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --rerun-tasks`

Expected: `BUILD SUCCESSFUL`; existing design and aggregate fixtures still pass, the new flow fixture proves `cap4kPlan` plus `cap4kGenerate`, and the missing-`rels.json` case fails with the source error message.

- [ ] **Step 5: Commit the Gradle/TestKit wiring**

```bash
git add \
  cap4k-plugin-pipeline-gradle/build.gradle.kts \
  cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelineExtension.kt \
  cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt \
  cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt \
  cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/settings.gradle.kts \
  cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/build.gradle.kts \
  cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/analysis/app/build/cap4k-code-analysis/nodes.json \
  cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/analysis/app/build/cap4k-code-analysis/rels.json
git commit -m "feat: wire flow pipeline into gradle plugin"
```

## Final Verification

- [ ] Run: `./gradlew :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-source-ir-analysis:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-flow:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test --rerun-tasks`
- [ ] Expected: `BUILD SUCCESSFUL`
- [ ] Run: `git status --short`
- [ ] Expected: clean working tree except for intentionally untracked planning docs
```
