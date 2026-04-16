package com.only4.cap4k.plugin.pipeline.gradle

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi

class PipelinePluginBootstrapGeneratedProjectFunctionalTest {

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `generated bootstrap project accepts help and tasks commands`() {
        val fixtureDir = Files.createTempDirectory("bootstrap-generated-project-smoke")
        FunctionalFixtureSupport.copyFixture(fixtureDir, "bootstrap-generated-project-smoke-sample")

        val (bootstrapResult, helpResult) = FunctionalFixtureSupport.bootstrapThenRunGeneratedProject(
            fixtureDir,
            projectName = "only-danmuku",
            "help",
        )
        val tasksResult = FunctionalFixtureSupport.generatedProjectRunner(
            fixtureDir,
            projectName = "only-danmuku",
            "tasks",
        ).build()

        assertTrue(bootstrapResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(helpResult.output.contains("Welcome to Gradle"))
        assertTrue(tasksResult.output.contains("Build tasks"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `generated bootstrap project keeps slot files inside generated subtree`() {
        val fixtureDir = Files.createTempDirectory("bootstrap-generated-project-slot-smoke")
        FunctionalFixtureSupport.copyFixture(fixtureDir, "bootstrap-generated-project-smoke-sample")

        FunctionalFixtureSupport.runner(fixtureDir, "cap4kBootstrap").build()

        val generatedReadme = fixtureDir.resolve("only-danmuku/README.md")
        val generatedMarker = fixtureDir.resolve(
            "only-danmuku/only-danmuku-domain/src/main/kotlin/edu/only4/danmuku/domain/SmokeDomainMarker.kt"
        )

        assertTrue(generatedReadme.toFile().exists())
        assertTrue(generatedMarker.toFile().exists())
    }
}
