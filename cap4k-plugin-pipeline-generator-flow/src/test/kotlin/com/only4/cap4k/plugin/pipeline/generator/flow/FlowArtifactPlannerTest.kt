package com.only4.cap4k.plugin.pipeline.generator.flow

import com.only4.cap4k.plugin.pipeline.api.AnalysisEdgeModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisGraphModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisNodeModel
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.OutputRootLayout
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class FlowArtifactPlannerTest {

    @Test
    fun `plans json mermaid and index artifacts from allowed entry graph`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    AnalysisNodeModel(
                        id = "OrderController::submit",
                        name = "OrderController::submit",
                        fullName = "OrderController::submit",
                        type = "controllermethod",
                    ),
                    AnalysisNodeModel(
                        id = "SubmitOrderCmd",
                        name = "SubmitOrderCmd",
                        fullName = "SubmitOrderCmd",
                        type = "command",
                    ),
                    AnalysisNodeModel(
                        id = "SubmitOrderHandler",
                        name = "SubmitOrderHandler",
                        fullName = "SubmitOrderHandler",
                        type = "commandhandler",
                    ),
                    AnalysisNodeModel(
                        id = "IgnoredAggregate",
                        name = "IgnoredAggregate",
                        fullName = "IgnoredAggregate",
                        type = "aggregate",
                    ),
                ),
                edges = listOf(
                    AnalysisEdgeModel("OrderController::submit", "SubmitOrderCmd", "ControllerMethodToCommand"),
                    AnalysisEdgeModel("SubmitOrderCmd", "SubmitOrderHandler", "CommandToCommandHandler"),
                    AnalysisEdgeModel("SubmitOrderHandler", "IgnoredAggregate", "CommandHandlerToAggregate"),
                ),
            ),
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
                    AnalysisNodeModel(
                        id = "OrderController::submit",
                        name = "OrderController::submit",
                        fullName = "OrderController::submit",
                        type = "controllermethod",
                    ),
                    AnalysisNodeModel(
                        id = "OrderController submit",
                        name = "OrderController submit",
                        fullName = "OrderController submit",
                        type = "controllermethod",
                    ),
                ),
                edges = emptyList(),
            ),
        )

        val plan = planner.plan(config(), model)
        val jsonOutputs = plan
            .filter { it.templateId == "flow/entry.json.peb" }
            .map { it.outputPath }

        assertEquals(2, jsonOutputs.size)
        assertEquals("flows/OrderController_submit.json", jsonOutputs.first())
        assertTrue(Regex("""flows/OrderController_submit_[0-9a-f]{8}\.json""").matches(jsonOutputs.last()))
    }

    @Test
    fun `returns empty plan when analysis graph is absent`() {
        val planner = FlowArtifactPlanner()

        val plan = planner.plan(config(), CanonicalModel())

        assertEquals(emptyList<Any>(), plan)
    }

    @Test
    fun `supports custom flow output root`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    AnalysisNodeModel(
                        id = "OrderController::submit",
                        name = "OrderController::submit",
                        fullName = "OrderController::submit",
                        type = "controllermethod",
                    ),
                ),
                edges = emptyList(),
            ),
        )

        val plan = planner.plan(config(outputRoot = "build/cap4k/flows"), model)

        assertEquals("build/cap4k/flows/OrderController_submit.json", plan.first().outputPath)
    }

    @Test
    fun `rejects absolute and parent traversing flow output root`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    AnalysisNodeModel(
                        id = "OrderController::submit",
                        name = "OrderController::submit",
                        fullName = "OrderController::submit",
                        type = "controllermethod",
                    ),
                ),
                edges = emptyList(),
            ),
        )

        val absolutePath = Path.of("flows").toAbsolutePath().toString()
        val absoluteEx = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(config(outputRoot = absolutePath), model)
        }
        assertEquals(
            "flow outputRoot must be a valid relative filesystem path: $absolutePath",
            absoluteEx.message,
        )

        val traversalEx = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(config(outputRoot = "../flows"), model)
        }
        assertEquals(
            "flow outputRoot must be a valid relative filesystem path: ../flows",
            traversalEx.message,
        )
    }

    @Test
    fun `keeps index json deterministic`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    AnalysisNodeModel(
                        id = "OrderController::submit",
                        name = "OrderController::submit",
                        fullName = "OrderController::submit",
                        type = "controllermethod",
                    ),
                ),
                edges = emptyList(),
            ),
        )

        val indexJson = planner.plan(config(), model).last().context["jsonContent"] as String

        assertTrue(!indexJson.contains("generatedAt"))
    }

    @Test
    fun `deduplicates duplicate entry node ids before planning artifacts`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    AnalysisNodeModel(
                        id = "OrderController::submit",
                        name = "OrderController::submit",
                        fullName = "OrderController::submit",
                        type = "controllermethod",
                    ),
                    AnalysisNodeModel(
                        id = "OrderController::submit",
                        name = "OrderController::submit duplicate",
                        fullName = "OrderController::submit duplicate",
                        type = "controllermethod",
                    ),
                    AnalysisNodeModel(
                        id = "SubmitOrderCmd",
                        name = "SubmitOrderCmd",
                        fullName = "SubmitOrderCmd",
                        type = "command",
                    ),
                ),
                edges = listOf(
                    AnalysisEdgeModel("OrderController::submit", "SubmitOrderCmd", "ControllerMethodToCommand"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)
        val jsonEntries = plan.filter { it.templateId == "flow/entry.json.peb" }
        val mermaidEntries = plan.filter { it.templateId == "flow/entry.mmd.peb" }
        val indexJson = plan.last().context["jsonContent"] as String

        assertEquals(1, jsonEntries.size)
        assertEquals(1, mermaidEntries.size)
        assertEquals("flows/OrderController_submit.json", jsonEntries.single().outputPath)
        assertTrue(indexJson.contains("\"flowCount\": 1"))
        assertTrue(indexJson.contains("\"controllermethod\": 1"))
    }

    private fun config(outputRoot: String = "flows"): ProjectConfig =
        ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = emptyMap(),
            generators = mapOf(
                "flow" to GeneratorConfig(
                    enabled = true,
                ),
            ),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            artifactLayout = ArtifactLayoutConfig(flow = OutputRootLayout(outputRoot)),
        )
}
