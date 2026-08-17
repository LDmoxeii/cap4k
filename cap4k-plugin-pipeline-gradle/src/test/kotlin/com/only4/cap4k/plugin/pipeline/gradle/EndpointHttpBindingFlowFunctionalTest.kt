package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.json.PipelineJson
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.readText

class EndpointHttpBindingFlowFunctionalTest {
    private val jsonMapper = PipelineJson.newMapper()

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `multi module endpoint http application runs routes and projects command flow only`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-endpoint-http-binding")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "endpoint-http-binding-flow-compile-sample")

        val applicationResult = FunctionalFixtureSupport.runner(
            projectDir,
            ":provider-adapter:test",
            "--tests",
            "com.acme.endpoint.adapter.EndpointHttpBindingApplicationTest",
            "--no-parallel",
            "-Pkotlin.compiler.execution.strategy=out-of-process",
        ).build()
        assertEquals(TaskOutcome.SUCCESS, applicationResult.task(":provider-adapter:test")?.outcome)
        assertTrue(applicationResult.output.contains("BUILD SUCCESSFUL"), applicationResult.output)

        val analysisResult = FunctionalFixtureSupport.runner(
            projectDir,
            "cap4kAnalysisPlan",
            "cap4kAnalysisGenerate",
            "--no-parallel",
        ).build()
        assertTrue(analysisResult.output.contains("BUILD SUCCESSFUL"), analysisResult.output)

        val adapterAnalysisDir = projectDir.resolve("provider-adapter/build/cap4k-code-analysis")
        val nodesText = adapterAnalysisDir.resolve("nodes.json").readText()
        val relsText = adapterAnalysisDir.resolve("rels.json").readText()
        assertTrue(nodesText.contains("\"id\":\"endpoint-http:booking.create\""), nodesText)
        assertTrue(nodesText.contains("\"id\":\"endpoint-http:resource.get\""), nodesText)
        assertTrue(nodesText.contains("\"type\":\"endpointhttpbinding\""), nodesText)
        assertTrue(nodesText.contains("booking.create [POST /api/bookings]"), nodesText)
        assertTrue(nodesText.contains("resource.get [GET /file/getResource]"), nodesText)
        assertTrue(relsText.contains("\"type\":\"EndpointHttpBindingToCommand\""), relsText)
        assertTrue(relsText.contains("\"type\":\"EndpointHttpBindingToQuery\""), relsText)
        assertFalse(nodesText.contains("apipayload"), nodesText)

        val flowDir = projectDir.resolve("flows")
        val commandFlowFile = flowDir.resolve("endpoint_http_booking_create.json")
        val commandMermaidFile = flowDir.resolve("endpoint_http_booking_create.mmd")
        val indexFile = flowDir.resolve("index.json")
        assertTrue(commandFlowFile.toFile().exists())
        assertTrue(commandMermaidFile.toFile().exists())
        assertTrue(indexFile.toFile().exists())
        assertFalse(flowDir.resolve("endpoint_http_resource_get.json").toFile().exists())

        val commandFlow = jsonMapper.readTree(commandFlowFile.toFile())
        val index = jsonMapper.readTree(indexFile.toFile())
        val mermaid = commandMermaidFile.readText()
        assertEquals("endpoint-http:booking.create", commandFlow.get("entryId").asText())
        assertEquals("endpointhttpbinding", commandFlow.get("entryType").asText())
        assertEquals(2, commandFlow.get("nodeCount").asInt())
        assertEquals(1, commandFlow.get("edgeCount").asInt())
        assertEquals("EndpointHttpBindingToCommand", commandFlow.get("edges").single().get("type").asText())
        assertEquals(1, index.get("flowCount").asInt())
        assertEquals(1, index.get("entryTypeCounts").get("endpointhttpbinding").asInt())
        assertTrue(mermaid.lineSequence().any { it == "  N2[\"booking.create [POST /api/bookings]\"]" }, mermaid)
        assertTrue(mermaid.contains("EndpointHttpBindingToCommand"), mermaid)
        assertFalse(mermaid.contains("EndpointHandler"), mermaid)
        assertFalse(commandFlow.toString().contains("resource.get"), commandFlow.toString())

        val contractBuild = projectDir.resolve("endpoint-contract/build.gradle.kts").readText()
        assertFalse(contractBuild.contains("spring", ignoreCase = true), contractBuild)
        val generatedAdapterKotlin = Files.walk(projectDir.resolve("provider-adapter/build")).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                .map { projectDir.relativize(it).toString().replace('\\', '/') }
                .toList()
        }
        assertEquals(emptyList<String>(), generatedAdapterKotlin)
        assertFalse(flowDir.resolve("process.json").toFile().exists())
        assertFalse(flowDir.resolve("process-index.json").toFile().exists())
    }
}
