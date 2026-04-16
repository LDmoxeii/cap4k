package com.only4.cap4k.plugin.pipeline.gradle

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class PipelinePluginCompileFunctionalTest {

    @Test
    fun `application compileKotlin succeeds for design compile sample`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "design-compile-sample")

        val beforeGenerateCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-application:compileKotlin")
            .buildAndFail()
        assertTrue(beforeGenerateCompileResult.output.contains("BUILD FAILED"))
        assertTrue(
            beforeGenerateCompileResult.output.contains("Unresolved reference") ||
                beforeGenerateCompileResult.output.contains("Cannot access")
        )

        val generateResult = FunctionalFixtureSupport.runner(projectDir, "cap4kGenerate").build()
        val compileResult = FunctionalFixtureSupport.runner(projectDir, ":demo-application:compileKotlin").build()
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }
}
