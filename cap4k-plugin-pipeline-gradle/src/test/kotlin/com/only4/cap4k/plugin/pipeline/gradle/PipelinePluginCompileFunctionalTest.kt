package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.readText

class PipelinePluginCompileFunctionalTest {

    @Test
    fun `application compileKotlin succeeds for design compile sample`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "design-compile-sample")

        val settingsContent = projectDir.resolve("settings.gradle.kts").readText()
        assertFalse(settingsContent.contains("__CAP4K_REPO_ROOT__"))
        assertTrue(settingsContent.contains("includeBuild(\""))

        val beforeGenerateCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-application:compileKotlin")
            .buildAndFail()
        assertEquals(
            TaskOutcome.FAILED,
            beforeGenerateCompileResult.task(":demo-application:compileKotlin")?.outcome
        )

        val (generateResult, compileResult) = FunctionalFixtureSupport.generateThenCompile(
            projectDir,
            ":demo-application:compileKotlin"
        )
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }
}
