package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively

class PipelinePluginFunctionalTest {

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan writes pretty printed plan json`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-plan")
        copyFixture(projectDir)

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .build()

        val planFile = projectDir.resolve("build/cap4k/plan.json").toFile()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planFile.exists())
        assertTrue(planFile.readText().contains("\n  {"))
        assertTrue(planFile.readText().contains("\"templateId\": \"design/command.kt.peb\""))
        assertTrue(planFile.readText().contains("\"templateId\": \"design/query.kt.peb\""))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate renders command and query files from repository config`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-generate")
        copyFixture(projectDir)

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(
            File(
                projectDir.toFile(),
                "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt"
            ).exists()
        )
        assertTrue(
            File(
                projectDir.toFile(),
                "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt"
            ).exists()
        )
    }

    @OptIn(ExperimentalPathApi::class)
    private fun copyFixture(targetDir: Path) {
        val sourceDir = Path.of(
            requireNotNull(javaClass.getResource("/functional/design-sample")) {
                "Missing functional fixture directory"
            }.toURI()
        )
        sourceDir.copyToRecursively(targetDir, followLinks = false)
    }
}
