package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.json.PipelineJson
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.writeText

class Cap4kAgentSnapshotFunctionalTest {
    private val jsonMapper = PipelineJson.newMapper(includeNulls = true)

    @Test
    fun `analysis plan and agent snapshot form a bounded manifest-first dry run`() {
        val projectDir = Files.createTempDirectory("agent-functional-analysis")
        FunctionalFixtureSupport.copyFixture(projectDir, "flow-sample")

        val result = FunctionalFixtureSupport.runner(
            projectDir,
            "cap4kAnalysisPlan",
            "cap4kAgentSnapshot",
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":cap4kAnalysisPlan")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":cap4kAgentSnapshot")?.outcome)
        val output = projectDir.resolve("build/cap4k/agent")
        val manifest = jsonMapper.readTree(output.resolve("manifest.json").toFile().readText()).requireObjectNode()
        val analysis = jsonMapper.readTree(output.resolve("analysis.json").toFile().readText()).requireObjectNode()
        assertEquals("partial", manifest.get("status").asText())
        assertEquals("partial", analysis.get("status").asText())
        assertEquals("fresh", analysis.requireObjectNode("evidence").get("freshness").asText())
        assertTrue(analysis.requireArrayNode("plannedOutputPaths").size() > 0)
        assertEquals(0, analysis.requireArrayNode("availableOutputPaths").size())
        assertEquals("cap4kAnalysisGenerate", analysis.get("nextAction").asText())

        val generated = FunctionalFixtureSupport.runner(
            projectDir,
            "cap4kAnalysisGenerate",
            "cap4kAgentSnapshot",
        ).build()

        assertEquals(TaskOutcome.SUCCESS, generated.task(":cap4kAnalysisGenerate")?.outcome)
        val generatedAnalysis = jsonMapper.readTree(
            output.resolve("analysis.json").toFile().readText()
        ).requireObjectNode()
        assertEquals("ok", generatedAnalysis.get("status").asText())
        assertEquals(
            generatedAnalysis.requireArrayNode("plannedOutputPaths").size(),
            generatedAnalysis.requireArrayNode("availableOutputPaths").size(),
        )
        assertTrue(generatedAnalysis.get("nextAction").isNull)
    }

    @Test
    fun `invalid consumer task fails after writing diagnostic snapshot`() {
        val projectDir = Files.createTempDirectory("agent-functional-invalid")
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"invalid-agent-sample\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.ldmoxeii.cap4k.pipeline")
            }
            """.trimIndent()
        )

        val result = FunctionalFixtureSupport.runner(projectDir, "cap4kAgentSnapshot").buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":cap4kAgentSnapshot")?.outcome)
        val output = projectDir.resolve("build/cap4k/agent")
        val manifest = jsonMapper.readTree(output.resolve("manifest.json").toFile().readText()).requireObjectNode()
        val diagnostics = output.resolve("diagnostics.json").toFile().readText()
        assertEquals("invalid", manifest.get("status").asText())
        assertTrue(diagnostics.contains("project-configuration-invalid"))
    }

    @Test
    fun `parseable corrupt saved plan does not prevent diagnostic snapshot publication`() {
        val projectDir = Files.createTempDirectory("agent-functional-corrupt-plan")
        FunctionalFixtureSupport.copyFixture(projectDir, "flow-sample")
        val planFile = projectDir.resolve("build/cap4k/plan.json")
        Files.createDirectories(planFile.parent)
        planFile.writeText("""{"outcome":"SUCCEEDED"}""")

        val result = FunctionalFixtureSupport.runner(projectDir, "cap4kAgentSnapshot").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":cap4kAgentSnapshot")?.outcome)
        val output = projectDir.resolve("build/cap4k/agent")
        val manifest = jsonMapper.readTree(output.resolve("manifest.json").toFile().readText()).requireObjectNode()
        val diagnostics = output.resolve("diagnostics.json").toFile().readText()
        assertEquals("partial", manifest.get("status").asText())
        assertTrue(diagnostics.contains("plan-evidence-invalid"))
        assertTrue(diagnostics.contains("items must be a JSON array"))
    }
}
