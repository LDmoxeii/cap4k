package com.only4.cap4k.plugin.pipeline.gradle

import com.google.gson.JsonParser
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.writeText

class Cap4kAgentSnapshotFunctionalTest {
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
        val manifest = JsonParser.parseString(output.resolve("manifest.json").toFile().readText()).asJsonObject
        val analysis = JsonParser.parseString(output.resolve("analysis.json").toFile().readText()).asJsonObject
        assertEquals("partial", manifest.get("status").asString)
        assertEquals("partial", analysis.get("status").asString)
        assertEquals("fresh", analysis.getAsJsonObject("evidence").get("freshness").asString)
        assertTrue(analysis.getAsJsonArray("plannedOutputPaths").size() > 0)
        assertEquals(0, analysis.getAsJsonArray("availableOutputPaths").size())
        assertEquals("cap4kAnalysisGenerate", analysis.get("nextAction").asString)

        val generated = FunctionalFixtureSupport.runner(
            projectDir,
            "cap4kAnalysisGenerate",
            "cap4kAgentSnapshot",
        ).build()

        assertEquals(TaskOutcome.SUCCESS, generated.task(":cap4kAnalysisGenerate")?.outcome)
        val generatedAnalysis = JsonParser.parseString(
            output.resolve("analysis.json").toFile().readText()
        ).asJsonObject
        assertEquals("ok", generatedAnalysis.get("status").asString)
        assertEquals(
            generatedAnalysis.getAsJsonArray("plannedOutputPaths").size(),
            generatedAnalysis.getAsJsonArray("availableOutputPaths").size(),
        )
        assertTrue(generatedAnalysis.get("nextAction").isJsonNull)
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
        val manifest = JsonParser.parseString(output.resolve("manifest.json").toFile().readText()).asJsonObject
        val diagnostics = output.resolve("diagnostics.json").toFile().readText()
        assertEquals("invalid", manifest.get("status").asString)
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
        val manifest = JsonParser.parseString(output.resolve("manifest.json").toFile().readText()).asJsonObject
        val diagnostics = output.resolve("diagnostics.json").toFile().readText()
        assertEquals("partial", manifest.get("status").asString)
        assertTrue(diagnostics.contains("plan-evidence-invalid"))
        assertTrue(diagnostics.contains("items must be a JSON array"))
    }
}
