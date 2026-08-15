package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.json.PipelineJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.readText

class TemporalTriggerFlowFunctionalTest {
    private val jsonMapper = PipelineJson.newMapper()

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `compiled scheduled method produces deterministic temporal flow artifacts`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-temporal-trigger")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "temporal-trigger-flow-compile-sample")

        val result = FunctionalFixtureSupport.runner(
            projectDir,
            "cap4kAnalysisPlan",
            "cap4kAnalysisGenerate",
        ).build()

        val analysisDir = projectDir.resolve("build/cap4k-code-analysis")
        val nodesText = analysisDir.resolve("nodes.json").readText()
        val relsText = analysisDir.resolve("rels.json").readText()
        val entryFile = projectDir.resolve("flows/demo_CatalogSchedule_refresh.json")
        val mermaidFile = projectDir.resolve("flows/demo_CatalogSchedule_refresh.mmd")
        val indexFile = projectDir.resolve("flows/index.json")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        assertTrue(nodesText.contains("\"id\":\"demo.CatalogSchedule::refresh\""), nodesText)
        assertTrue(nodesText.contains("\"type\":\"temporaltriggermethod\""), nodesText)
        assertTrue(relsText.contains("TemporalTriggerMethodToCommand"), relsText)
        assertFalse(nodesText.contains("commandsendermethod"), nodesText)
        assertFalse(relsText.contains("CommandSenderMethodToCommand"), relsText)
        assertFalse(nodesText.contains("demo.CatalogSchedule::helper"), nodesText)
        assertFalse(relsText.contains("demo.CatalogSchedule::helper"), relsText)
        assertFalse(relsText.contains("demo.CatalogSchedule::inspect\",\"toId\":\"demo.RefreshCatalogCmd"), relsText)

        assertTrue(entryFile.toFile().exists())
        assertTrue(mermaidFile.toFile().exists())
        assertTrue(indexFile.toFile().exists())

        val entry = jsonMapper.readTree(entryFile.toFile())
        val index = jsonMapper.readTree(indexFile.toFile())
        val mermaid = mermaidFile.readText()

        assertEquals("demo.CatalogSchedule::refresh", entry.get("entryId").asText())
        assertEquals("temporaltriggermethod", entry.get("entryType").asText())
        assertEquals(2, entry.get("nodeCount").asInt())
        assertEquals(1, entry.get("edgeCount").asInt())
        assertEquals("TemporalTriggerMethodToCommand", entry.get("edges").single().get("type").asText())
        assertEquals(1, index.get("flowCount").asInt())
        assertEquals(1, index.get("entryTypeCounts").get("temporaltriggermethod").asInt())
        assertTrue(mermaid.contains("CatalogSchedule::refresh"), mermaid)
        assertTrue(mermaid.contains("TemporalTriggerMethodToCommand"), mermaid)
        assertFalse(projectDir.resolve("flows/process.json").toFile().exists())
        assertFalse(projectDir.resolve("flows/process-index.json").toFile().exists())
        assertFalse(result.output.contains("cap4kFlow"), result.output)
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `endpoint contract metadata and local dispatch do not create actor flows`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-endpoint-flow-negative")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "endpoint-flow-negative-compile-sample")

        val result = FunctionalFixtureSupport.runner(
            projectDir,
            "cap4kAnalysisPlan",
            "cap4kAnalysisGenerate",
        ).build()

        val analysisDir = projectDir.resolve("build/cap4k-code-analysis")
        val nodesText = analysisDir.resolve("nodes.json").readText()
        val relsText = analysisDir.resolve("rels.json").readText()
        val designText = analysisDir.resolve("design-elements.json").readText()
        val index = jsonMapper.readTree(projectDir.resolve("flows/index.json").toFile())

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        assertTrue(designText.contains("\"tag\":\"endpoint\""), designText)
        assertTrue(designText.contains("\"operationName\":\"booking.create\""), designText)
        assertFalse(nodesText.contains("CreateBookingEndpoint"), nodesText)
        assertFalse(nodesText.contains("\"type\":\"actor\""), nodesText)
        assertFalse(relsText.contains("Endpoint"), relsText)
        assertEquals(0, index.get("flowCount").asInt())
        assertFalse(projectDir.resolve("flows/demo_CreateBookingEndpoint.json").toFile().exists())
        assertEquals(listOf("index.json"), Files.list(projectDir.resolve("flows")).use { stream ->
            stream.map { it.fileName.toString() }.sorted().toList()
        })
    }
}
