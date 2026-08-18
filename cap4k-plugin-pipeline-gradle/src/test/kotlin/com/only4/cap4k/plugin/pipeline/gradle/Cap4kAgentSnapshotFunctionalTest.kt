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
        assertEquals("cap4k.agent.analysis.v2", analysis.get("schema").asText())
        assertEquals("partial", analysis.get("status").asText())
        assertEquals("fresh", analysis.requireObjectNode("evidence").get("freshness").asText())
        val partitions = analysis.get("partitions").requireArrayNode()
        assertEquals(
            setOf("aggregateStructure", "designProjection", "graph"),
            partitions.map { partition -> partition.get("id").asText() }.toSet(),
        )
        val graph = partitions.single { partition -> partition.get("id").asText() == "graph" }
        assertTrue(graph.get("requested").asBoolean())
        assertEquals("partial", graph.get("status").asText())
        assertTrue(graph.get("plannedOutputPaths").requireArrayNode().size() > 0)
        assertEquals(0, graph.get("availableOutputPaths").requireArrayNode().size())
        assertEquals("cap4kAnalysisGenerate", graph.get("nextAction").asText())
        val graphSource = graph.get("sources").requireArrayNode().single()
        assertEquals("project:analysis/app/build/cap4k-code-analysis", graphSource.get("id").asText())
        assertEquals("analysis/app/build/cap4k-code-analysis", graphSource.get("path").asText())
        partitions
            .filterNot { partition -> partition.get("id").asText() == "graph" }
            .forEach { partition ->
                assertTrue(!partition.get("requested").asBoolean())
                assertEquals("unavailable", partition.get("status").asText())
                assertEquals("unknown", partition.get("freshness").asText())
                assertTrue(partition.get("counts").fields().asSequence().all { (_, count) -> count.asInt() == 0 })
                assertEquals(0, partition.get("sources").requireArrayNode().size())
                assertEquals(0, partition.get("plannedOutputPaths").requireArrayNode().size())
                assertEquals(0, partition.get("availableOutputPaths").requireArrayNode().size())
                assertEquals(0, partition.get("diagnosticIds").requireArrayNode().size())
                assertTrue(partition.get("nextAction").isNull)
            }

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
        val generatedGraph = generatedAnalysis.get("partitions").requireArrayNode()
            .single { partition -> partition.get("id").asText() == "graph" }
        assertEquals(
            generatedGraph.get("plannedOutputPaths").requireArrayNode().size(),
            generatedGraph.get("availableOutputPaths").requireArrayNode().size(),
        )
        assertTrue(generatedGraph.get("nextAction").isNull)
    }

    @Test
    fun `generated managed policy plan publishes ownership without plan evidence diagnostics`() {
        val projectDir = Files.createTempDirectory("agent-functional-plan-evidence")
        FunctionalFixtureSupport.copyFixture(projectDir, "aggregate-minimal-sample")

        val result = FunctionalFixtureSupport.runner(
            projectDir,
            "cap4kPlan",
            "cap4kAgentSnapshot",
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":cap4kPlan")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":cap4kAgentSnapshot")?.outcome)

        val plan = jsonMapper.readTree(
            projectDir.resolve("build/cap4k/plan.json").toFile().readText()
        ).requireObjectNode()
        assertTrue(plan.get("managedFieldPolicies").requireArrayNode().size() > 0)

        val output = projectDir.resolve("build/cap4k/agent")
        val ownership = jsonMapper.readTree(output.resolve("ownership.json").toFile().readText()).requireObjectNode()
        assertEquals("partial", ownership.get("status").asText())
        assertTrue(ownership.get("items").requireArrayNode().size() > 0)
        val planEvidence = ownership.get("evidence").requireArrayNode().single()
        assertEquals("unknown", planEvidence.get("freshness").asText())
        assertTrue(planEvidence.get("reason").asText().contains("live external source"))
        assertTrue(ownership.get("reason").asText().contains("live external source"))
        assertTrue(!output.resolve("diagnostics.json").toFile().readText().contains("plan-evidence-invalid"))
    }

    @Test
    fun `enum ownership snapshot reports checked in authoring source`() {
        val projectDir = Files.createTempDirectory("agent-functional-enum-ownership")
        FunctionalFixtureSupport.copyFixture(projectDir, "aggregate-enum-sample")

        val result = FunctionalFixtureSupport.runner(projectDir, "cap4kPlan", "cap4kAgentSnapshot").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":cap4kPlan")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":cap4kAgentSnapshot")?.outcome)
        val ownership = jsonMapper.readTree(
            projectDir.resolve("build/cap4k/agent/ownership.json").toFile().readText()
        ).requireObjectNode()
        val enumItem = ownership.get("items").requireArrayNode()
            .single { item ->
                item.get("generatorId").asText() == "enum" &&
                    item.get("outputPath").asText().endsWith("domain/shared/enums/Status.kt")
            }
        assertEquals("checked_in_source", enumItem.get("outputKind").asText())
        assertEquals("skip", enumItem.get("conflictPolicy").asText())
        assertEquals("demo-domain/src/main/kotlin", enumItem.get("resolvedOutputRoot").asText())
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
