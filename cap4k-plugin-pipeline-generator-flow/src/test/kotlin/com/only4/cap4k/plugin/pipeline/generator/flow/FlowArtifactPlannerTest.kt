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
    fun `defaults blank flow output dir to flows`() {
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

        val plan = planner.plan(config(outputDir = " "), model)

        assertEquals("flows/OrderController_submit.json", plan.first().outputPath)
    }

    @Test
    fun `rejects absolute and parent traversing flow output dir`() {
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
            planner.plan(config(outputDir = absolutePath), model)
        }
        assertEquals(
            "flow outputDir must be a valid relative filesystem path: $absolutePath",
            absoluteEx.message,
        )

        val traversalEx = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(config(outputDir = "../flows"), model)
        }
        assertEquals(
            "flow outputDir must be a valid relative filesystem path: ../flows",
            traversalEx.message,
        )
    }

    private fun config(outputDir: String = "flows"): ProjectConfig =
        ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = emptyMap(),
            generators = mapOf(
                "flow" to GeneratorConfig(
                    enabled = true,
                    options = mapOf("outputDir" to outputDir),
                ),
            ),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
}
