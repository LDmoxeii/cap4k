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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TemporalTriggerFlowTest {
    @Test
    fun `temporal trigger method produces one entry json mermaid and index record`() {
        val plan = plan(
            nodes = listOf(
                node("demo.CatalogSchedule::refresh", "temporaltriggermethod"),
                node("demo.RefreshCatalogCmd", "command"),
            ),
            edges = listOf(
                edge(
                    "demo.CatalogSchedule::refresh",
                    "demo.RefreshCatalogCmd",
                    "TemporalTriggerMethodToCommand",
                ),
            ),
        )

        val entryJson = plan.single { it.templateId == "flow/entry.json.peb" }.context.getValue("jsonContent") as String
        val mermaid = plan.single { it.templateId == "flow/entry.mmd.peb" }.context.getValue("mermaidText") as String
        val index = plan.single { it.templateId == "flow/index.json.peb" }.context.getValue("jsonContent") as String

        assertEquals(3, plan.size)
        assertTrue(entryJson.contains("\"entryId\": \"demo.CatalogSchedule::refresh\""), entryJson)
        assertTrue(entryJson.contains("\"entryType\": \"temporaltriggermethod\""), entryJson)
        assertTrue(entryJson.contains("\"TemporalTriggerMethodToCommand\""), entryJson)
        assertTrue(mermaid.contains("CatalogSchedule::refresh"), mermaid)
        assertTrue(mermaid.contains("TemporalTriggerMethodToCommand"), mermaid)
        assertTrue(index.contains("\"flowCount\": 1"), index)
        assertTrue(index.contains("\"temporaltriggermethod\": 1"), index)
    }

    @Test
    fun `temporal trigger method with only query and capability evidence produces zero flows`() {
        val plan = plan(
            nodes = listOf(
                node("demo.CatalogSchedule::inspect", "temporaltriggermethod"),
                node("demo.ReadCatalogQuery", "query"),
                node("demo.RefreshSearchCapability", "capability"),
            ),
            edges = listOf(
                edge("demo.CatalogSchedule::inspect", "demo.ReadCatalogQuery", "QuerySenderMethodToQuery"),
                edge(
                    "demo.CatalogSchedule::inspect",
                    "demo.RefreshSearchCapability",
                    "CapabilitySenderMethodToCapability",
                ),
            ),
        )

        assertEquals(listOf("flow/index.json.peb"), plan.map { it.templateId })
        assertTrue((plan.single().context.getValue("jsonContent") as String).contains("\"flowCount\": 0"))
    }

    @Test
    fun `sender shaped observation is not a concrete trigger entry`() {
        val plan = plan(
            nodes = listOf(
                node("demo.OrderRpc::submit", "rpcsendermethod"),
                node("demo.SubmitOrderCmd", "command"),
            ),
            edges = listOf(
                edge(
                    "demo.OrderRpc::submit",
                    "demo.SubmitOrderCmd",
                    "RpcSenderMethodToCommand",
                ),
            ),
        )

        assertEquals(listOf("flow/index.json.peb"), plan.map { it.templateId })
        val index = plan.single().context.getValue("jsonContent") as String
        assertTrue(index.contains("\"flowCount\": 0"), index)
    }

    @Test
    fun `trigger relationship prefix must match the concrete source node type`() {
        val plan = plan(
            nodes = listOf(
                node("demo.CatalogSchedule::refresh", "temporaltriggermethod"),
                node("demo.RefreshCatalogCmd", "command"),
            ),
            edges = listOf(
                edge(
                    "demo.CatalogSchedule::refresh",
                    "demo.RefreshCatalogCmd",
                    "ControllerMethodToCommand",
                ),
            ),
        )

        assertEquals(listOf("flow/index.json.peb"), plan.map { it.templateId })
        assertTrue((plan.single().context.getValue("jsonContent") as String).contains("\"flowCount\": 0"))
    }

    @Test
    fun `unknown adapter relationship does not become a flow trigger by suffix`() {
        val plan = plan(
            nodes = listOf(
                node("demo.OrderCli::submit", "climethod"),
                node("demo.SubmitOrderCmd", "command"),
            ),
            edges = listOf(
                edge("demo.OrderCli::submit", "demo.SubmitOrderCmd", "CliMethodToCommand"),
            ),
        )

        assertEquals(listOf("flow/index.json.peb"), plan.map { it.templateId })
        assertTrue((plan.single().context.getValue("jsonContent") as String).contains("\"flowCount\": 0"))
    }

    @Test
    fun `endpoint http command binding is one actor root while query binding is graph only`() {
        val commandPlan = plan(
            nodes = listOf(node("endpoint-http:booking.create", "endpointhttpbinding"), node("demo.CreateBookingCmd", "command")),
            edges = listOf(edge("endpoint-http:booking.create", "demo.CreateBookingCmd", "EndpointHttpBindingToCommand")),
        )
        assertEquals(3, commandPlan.size)
        val commandIndex = commandPlan.single { it.templateId == "flow/index.json.peb" }.context.getValue("jsonContent") as String
        assertTrue(commandIndex.contains("\"flowCount\": 1"), commandIndex)
        assertTrue(commandIndex.contains("\"endpointhttpbinding\": 1"), commandIndex)

        val queryPlan = plan(
            nodes = listOf(node("endpoint-http:booking.read", "endpointhttpbinding"), node("demo.ReadBookingQuery", "query")),
            edges = listOf(edge("endpoint-http:booking.read", "demo.ReadBookingQuery", "EndpointHttpBindingToQuery")),
        )
        assertEquals(listOf("flow/index.json.peb"), queryPlan.map { it.templateId })
        assertTrue((queryPlan.single().context.getValue("jsonContent") as String).contains("\"flowCount\": 0"))
    }

    private fun plan(
        nodes: List<AnalysisNodeModel>,
        edges: List<AnalysisEdgeModel>,
    ) = FlowArtifactPlanner().plan(
        ProjectConfig(
            basePackage = "demo",
            layout = ProjectLayout.SINGLE_MODULE,
            modules = emptyMap(),
            sources = emptyMap(),
            generators = mapOf("flow" to GeneratorConfig()),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            artifactLayout = ArtifactLayoutConfig(flow = OutputRootLayout("flows")),
        ),
        CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("build/cap4k-code-analysis"),
                nodes = nodes,
                edges = edges,
            ),
        ),
    )

    private fun node(id: String, type: String) = AnalysisNodeModel(id, id, id, type)

    private fun edge(fromId: String, toId: String, type: String) = AnalysisEdgeModel(fromId, toId, type)
}
