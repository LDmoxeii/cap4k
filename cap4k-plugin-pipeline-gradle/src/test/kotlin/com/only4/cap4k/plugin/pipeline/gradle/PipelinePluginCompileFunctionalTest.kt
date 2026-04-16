package com.only4.cap4k.plugin.pipeline.gradle

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
        val buildContent = projectDir.resolve("build.gradle.kts").readText()
        val applicationBuildContent = projectDir.resolve("demo-application/build.gradle.kts").readText()
        val adapterBuildContent = projectDir.resolve("demo-adapter/build.gradle.kts").readText()
        val designContent = projectDir.resolve("design/design.json").readText()

        assertTrue(settingsContent.contains("https://maven.aliyun.com/repository/public"))
        assertFalse(buildContent.contains("designQueryHandler"))
        assertFalse(buildContent.contains("designClient"))
        assertFalse(buildContent.contains("designClientHandler"))
        assertTrue(applicationBuildContent.contains("jvmToolchain(17)"))
        assertTrue(adapterBuildContent.contains("jvmToolchain(17)"))
        assertTrue(designContent.contains("\"tag\": \"command\""))
        assertTrue(designContent.contains("\"tag\": \"query\""))
        assertFalse(designContent.contains("\"tag\": \"cmd\""))
        assertFalse(designContent.contains("\"tag\": \"qry\""))
        assertTrue(designContent.contains("\"desc\": \"submit order\""))
        assertTrue(designContent.contains("\"desc\": \"find order\""))
        assertFalse(designContent.contains("\"accepted\""))
        assertFalse(designContent.contains("\"found\""))

        val beforeGenerateCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-application:compileKotlin")
            .buildAndFail()
        assertTrue(beforeGenerateCompileResult.output.contains("BUILD FAILED"))
        assertTrue(
            beforeGenerateCompileResult.output.contains("Unresolved reference") ||
                beforeGenerateCompileResult.output.contains("Cannot access")
        )

        val (generateResult, compileResult) = FunctionalFixtureSupport.generateThenCompile(
            projectDir,
            ":demo-application:compileKotlin"
        )
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }
}
