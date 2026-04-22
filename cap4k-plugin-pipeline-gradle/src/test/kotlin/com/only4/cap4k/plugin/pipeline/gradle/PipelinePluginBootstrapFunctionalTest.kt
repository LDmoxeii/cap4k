package com.only4.cap4k.plugin.pipeline.gradle

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.readText

class PipelinePluginBootstrapFunctionalTest {

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kBootstrapPlan writes in place output paths by default`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-bootstrap-plan")
        FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-sample")

        val result = FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrapPlan").build()
        val planFile = projectDir.resolve("build/cap4k/bootstrap-plan.json")
        val planContent = planFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planContent.contains("\"templateId\": \"bootstrap/root/settings.gradle.kts.peb\""))
        assertTrue(planContent.contains("\"slotId\": \"root\""))
        assertTrue(planContent.contains("\"slotId\": \"module-package:domain\""))
        assertTrue(planContent.contains("\"outputPath\": \"settings.gradle.kts\""))
        assertTrue(planContent.contains("\"outputPath\": \"only-danmuku-domain/build.gradle.kts\""))
        assertTrue(planContent.contains("\"outputPath\": \"README.md\""))
        assertTrue(!planContent.contains("\"outputPath\": \"only-danmuku/settings.gradle.kts\""))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kBootstrap supports fixed template override dirs`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-bootstrap-override")
        FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-override-sample")

        val result = FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrap").build()
        val generatedRootBuild = projectDir.resolve("bootstrap-preview/build.gradle.kts").readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedRootBuild.contains("// override: bootstrap root build template"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kBootstrapPlan fails for unsupported slot role`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-bootstrap-invalid-role")
        FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-invalid-slot-sample")

        val result = FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrapPlan").buildAndFail()

        assertTrue(result.output.contains("unsupported bootstrap slot role"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan and cap4kGenerate still require project base package`() {
        listOf("cap4kPlan", "cap4kGenerate").forEach { taskName ->
            val projectDir = Files.createTempDirectory("pipeline-functional-bootstrap-regular-$taskName")
            FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-sample")

            val result = FunctionalFixtureSupport.runner(projectDir, taskName).buildAndFail()

            assertTrue(result.output.contains("project.basePackage is required"))
        }
    }
}
