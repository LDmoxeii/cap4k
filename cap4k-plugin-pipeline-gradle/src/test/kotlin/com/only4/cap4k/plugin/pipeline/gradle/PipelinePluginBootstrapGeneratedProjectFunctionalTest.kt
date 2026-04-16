package com.only4.cap4k.plugin.pipeline.gradle

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.readText

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
        assertTrue(tasksResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(tasksResult.output.contains("Tasks runnable from root project"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `generated bootstrap project keeps slot files inside generated subtree`() {
        val fixtureDir = Files.createTempDirectory("bootstrap-generated-project-slot-smoke")
        FunctionalFixtureSupport.copyFixture(fixtureDir, "bootstrap-generated-project-smoke-sample")

        FunctionalFixtureSupport.runner(fixtureDir, "cap4kBootstrap").build()

        val generatedReadme = fixtureDir.resolve("only-danmuku/README.md")
        val generatedMarker = fixtureDir.resolve(
            "only-danmuku/only-danmuku-domain/src/main/kotlin/SmokeDomainMarker.kt"
        )
        val rootReadme = fixtureDir.resolve("README.md")
        val rootMarker = fixtureDir.resolve(
            "only-danmuku-domain/src/main/kotlin/SmokeDomainMarker.kt"
        )

        assertTrue(generatedReadme.toFile().exists())
        assertTrue(generatedMarker.toFile().exists())
        assertFalse(rootReadme.toFile().exists())
        assertFalse(rootMarker.toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `generated bootstrap project domain application and adapter modules compile`() {
        val fixtureDir = Files.createTempDirectory("bootstrap-generated-project-compile")
        FunctionalFixtureSupport.copyFixture(fixtureDir, "bootstrap-generated-project-smoke-sample")

        val (bootstrapResult, domainCompile) = FunctionalFixtureSupport.bootstrapThenRunGeneratedProject(
            fixtureDir,
            projectName = "only-danmuku",
            ":only-danmuku-domain:compileKotlin",
        )
        val applicationCompile = FunctionalFixtureSupport.generatedProjectRunner(
            fixtureDir,
            projectName = "only-danmuku",
            ":only-danmuku-application:compileKotlin",
        ).build()
        val adapterCompile = FunctionalFixtureSupport.generatedProjectRunner(
            fixtureDir,
            projectName = "only-danmuku",
            ":only-danmuku-adapter:compileKotlin",
        ).build()

        assertTrue(bootstrapResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(domainCompile.output.contains("BUILD SUCCESSFUL"))
        assertTrue(applicationCompile.output.contains("BUILD SUCCESSFUL"))
        assertTrue(adapterCompile.output.contains("BUILD SUCCESSFUL"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `generated bootstrap project remains usable with fixed template override and slots`() {
        val fixtureDir = Files.createTempDirectory("bootstrap-generated-project-override")
        FunctionalFixtureSupport.copyFixture(fixtureDir, "bootstrap-generated-project-override-sample")

        val (bootstrapResult, helpResult) = FunctionalFixtureSupport.bootstrapThenRunGeneratedProject(
            fixtureDir,
            projectName = "only-danmuku",
            "help",
        )
        val domainCompile = FunctionalFixtureSupport.generatedProjectRunner(
            fixtureDir,
            projectName = "only-danmuku",
            ":only-danmuku-domain:compileKotlin",
        ).build()
        val generatedRootBuild = fixtureDir.resolve("only-danmuku/build.gradle.kts").readText()
        val generatedReadme = fixtureDir.resolve("only-danmuku/README.md").readText()

        assertTrue(bootstrapResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(helpResult.output.contains("Welcome to Gradle"))
        assertTrue(domainCompile.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedRootBuild.contains("// override: bootstrap generated-project hardening"))
        assertTrue(generatedReadme.contains("# only-danmuku"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `generated-project verification does not mask invalid bootstrap configuration`() {
        val fixtureDir = Files.createTempDirectory("bootstrap-generated-project-invalid")
        FunctionalFixtureSupport.copyFixture(fixtureDir, "bootstrap-generated-project-invalid-sample")

        val result = FunctionalFixtureSupport.runner(fixtureDir, "cap4kBootstrapPlan").buildAndFail()

        assertTrue(result.output.contains("unsupported bootstrap slot role"))
    }
}
