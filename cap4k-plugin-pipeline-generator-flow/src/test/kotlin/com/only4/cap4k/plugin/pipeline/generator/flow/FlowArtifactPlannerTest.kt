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
import org.junit.jupiter.api.Assertions.assertFalse
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
                    node("OrderController::submit", "controllermethod"),
                    node("SubmitOrderCmd", "command"),
                    node("SubmitOrderHandler", "commandhandler"),
                    node("Order::submit", "entitymethod"),
                    node("IgnoredAggregate", "aggregate"),
                ),
                edges = listOf(
                    edge("OrderController::submit", "SubmitOrderCmd", "ControllerMethodToCommand"),
                    edge("SubmitOrderCmd", "SubmitOrderHandler", "CommandToCommandHandler"),
                    edge("SubmitOrderHandler", "Order::submit", "CommandHandlerToEntityMethod"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)
        val jsonContent = plan[0].context["jsonContent"] as String

        assertEquals(3, plan.size)
        assertEquals("flow/entry.json.peb", plan[0].templateId)
        assertEquals("flows/OrderController_submit.json", plan[0].outputPath)
        assertEquals("flow/entry.mmd.peb", plan[1].templateId)
        assertEquals("flows/OrderController_submit.mmd", plan[1].outputPath)
        assertEquals("flow/index.json.peb", plan[2].templateId)
        assertEquals("flows/index.json", plan[2].outputPath)
        assertTrue(model.analysisGraph!!.nodes.any { it.type == "commandhandler" })
        assertTrue(model.analysisGraph!!.nodes.any { it.type == "entitymethod" && it.id == "Order::submit" })
        assertTrue(jsonContent.contains("Order::submit"))
        assertTrue(jsonContent.contains("\"edgeCount\": 2"))
        assertTrue(jsonContent.contains("\"CommandToEntityMethod\""))
        assertFalse(jsonContent.contains("SubmitOrderHandler"))
        assertFalse(jsonContent.contains("IgnoredAggregate"))
        assertTrue((plan[1].context["mermaidText"] as String).contains("flowchart TD"))
        assertTrue((plan[2].context["jsonContent"] as String).contains("\"flowCount\": 1"))
    }

    @Test
    fun `excludes query cli and validator paths from default causal chain`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    node("SearchOrdersQuerySender", "querysendermethod"),
                    node("SearchOrdersQuery", "query"),
                    node("SearchOrdersQueryHandler", "queryhandler"),
                    node("ExportOrdersCliSender", "clisendermethod"),
                    node("ExportOrdersCli", "cli"),
                    node("ExportOrdersCliHandler", "clihandler"),
                    node("SearchOrdersValidator", "validator"),
                ),
                edges = listOf(
                    edge("SearchOrdersQuerySender", "SearchOrdersQuery", "QuerySenderMethodToQuery"),
                    edge("SearchOrdersQuery", "SearchOrdersQueryHandler", "QueryToQueryHandler"),
                    edge("ExportOrdersCliSender", "ExportOrdersCli", "CliSenderMethodToCli"),
                    edge("ExportOrdersCli", "ExportOrdersCliHandler", "CliToCliHandler"),
                    edge("SearchOrdersValidator", "SearchOrdersQuery", "ValidatorToQuery"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)
        assertEquals(1, plan.size)
        val indexJson = plan.last().context["jsonContent"] as String

        assertEquals(listOf("flow/index.json.peb"), plan.map { it.templateId })
        assertEquals("flows/index.json", plan.last().outputPath)
        assertTrue(indexJson.contains("\"flowCount\": 0"))
        assertTrue(indexJson.contains("\"entryTypeCounts\": {}"))
        assertFalse(indexJson.contains("\"querysendermethod\": 1"))
        assertFalse(indexJson.contains("\"clisendermethod\": 1"))
        assertFalse(indexJson.contains("\"validator\": 1"))
    }

    @Test
    fun `does not emit integration event as separate flow when it has upstream causal edge`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    node("OrderController::submit", "controllermethod"),
                    node("SubmitOrderCmd", "command"),
                    node("Order::submit", "entitymethod"),
                    node("OrderUpdated", "domainevent"),
                    node("MediaProcessedIntegrationEvent", "integrationevent"),
                    node("MediaProcessedIntegrationEventHandler", "integrationeventhandler"),
                    node("MediaProcessedCmd", "command"),
                    node("Media::process", "entitymethod"),
                ),
                edges = listOf(
                    edge("OrderController::submit", "SubmitOrderCmd", "ControllerMethodToCommand"),
                    edge("SubmitOrderCmd", "Order::submit", "CommandToEntityMethod"),
                    edge("Order::submit", "OrderUpdated", "EntityMethodToDomainEvent"),
                    edge("OrderUpdated", "MediaProcessedIntegrationEvent", "DomainEventToIntegrationEvent"),
                    edge("MediaProcessedIntegrationEvent", "MediaProcessedIntegrationEventHandler", "IntegrationEventToHandler"),
                    edge("MediaProcessedIntegrationEventHandler", "MediaProcessedCmd", "IntegrationEventHandlerToCommand"),
                    edge("MediaProcessedCmd", "Media::process", "CommandToEntityMethod"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)

        assertEquals(3, plan.size)
        assertEquals(1, plan.count { it.templateId == "flow/entry.json.peb" })
        assertEquals("flows/OrderController_submit.json", plan.first { it.templateId == "flow/entry.json.peb" }.outputPath)
        assertFalse(plan.any { it.outputPath == "flows/MediaProcessedIntegrationEvent.json" })
        assertTrue((plan.last().context["jsonContent"] as String).contains("\"flowCount\": 1"))
    }

    @Test
    fun `keeps empty domain event handler visible and stops naturally`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    node("OrderController::submit", "controllermethod"),
                    node("SubmitOrderCmd", "command"),
                    node("SubmitOrderHandler", "commandhandler"),
                    node("Order::submit", "entitymethod"),
                    node("OrderUpdated", "domainevent"),
                    node("OrderUpdatedHandler", "domaineventhandler"),
                ),
                edges = listOf(
                    edge("OrderController::submit", "SubmitOrderCmd", "ControllerMethodToCommand"),
                    edge("SubmitOrderCmd", "Order::submit", "CommandToEntityMethod"),
                    edge("Order::submit", "OrderUpdated", "EntityMethodToDomainEvent"),
                    edge("OrderUpdated", "OrderUpdatedHandler", "DomainEventToHandler"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)
        val jsonContent = plan[0].context["jsonContent"] as String

        assertEquals(3, plan.size)
        assertTrue(jsonContent.contains("OrderUpdatedHandler"))
        assertTrue(jsonContent.contains("\"edgeCount\": 4"))
        assertFalse(jsonContent.contains("SubmitOrderHandler"))
    }

    @Test
    fun `keeps integration event handler visible when inbound event sends command`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    node("MediaProcessedIntegrationEvent", "integrationevent"),
                    node("MediaProcessedIntegrationEventHandler", "integrationeventhandler"),
                    node("MediaProcessedCmd", "command"),
                    node("MediaProcessedHandler", "commandhandler"),
                    node("Media::process", "entitymethod"),
                ),
                edges = listOf(
                    edge("MediaProcessedIntegrationEvent", "MediaProcessedIntegrationEventHandler", "IntegrationEventToHandler"),
                    edge("MediaProcessedIntegrationEventHandler", "MediaProcessedCmd", "IntegrationEventHandlerToCommand"),
                    edge("MediaProcessedCmd", "Media::process", "CommandToEntityMethod"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)
        val jsonContent = plan[0].context["jsonContent"] as String
        val indexJson = plan.last().context["jsonContent"] as String

        assertEquals("flows/MediaProcessedIntegrationEvent.json", plan[0].outputPath)
        assertTrue(indexJson.contains("\"integrationevent\": 1"))
        assertTrue(jsonContent.contains("MediaProcessedIntegrationEventHandler"))
        assertTrue(jsonContent.contains("MediaProcessedCmd"))
        assertTrue(jsonContent.contains("Media::process"))
        assertTrue(jsonContent.contains("\"CommandToEntityMethod\""))
        assertFalse(jsonContent.contains("MediaProcessedHandler"))
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

    private fun node(id: String, type: String): AnalysisNodeModel =
        AnalysisNodeModel(
            id = id,
            name = id,
            fullName = id,
            type = type,
        )

    private fun edge(fromId: String, toId: String, type: String, label: String? = null): AnalysisEdgeModel =
        AnalysisEdgeModel(
            fromId = fromId,
            toId = toId,
            type = type,
            label = label,
        )
}
