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
    fun `generated bootstrap project accepts help and tasks commands and keeps slot files in generated subtree`() {
        val fixtureDir = Files.createTempDirectory("bootstrap-generated-project-smoke")
        FunctionalFixtureSupport.copyFixture(fixtureDir, "bootstrap-generated-project-smoke-sample")

        val (bootstrapResult, generatedBuildResult) = FunctionalFixtureSupport.bootstrapThenRunGeneratedProject(
            fixtureDir,
            projectName = "only-danmuku",
            "help",
            "tasks",
        )
        val generatedReadme = fixtureDir.resolve("only-danmuku/README.md")
        val generatedMarker = fixtureDir.resolve(
            "only-danmuku/only-danmuku-domain/src/main/kotlin/edu/only4/danmuku/SmokeDomainMarker.kt"
        )
        val rootReadme = fixtureDir.resolve("README.md")
        val generatedFlatMarker = fixtureDir.resolve(
            "only-danmuku/only-danmuku-domain/src/main/kotlin/SmokeDomainMarker.kt"
        )

        assertTrue(bootstrapResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedBuildResult.output.contains("Welcome to Gradle"))
        assertTrue(generatedBuildResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedBuildResult.output.contains("Tasks runnable from root project"))
        assertTrue(generatedReadme.toFile().exists())
        assertTrue(generatedMarker.toFile().exists())
        assertFalse(rootReadme.toFile().exists())
        assertFalse(generatedFlatMarker.toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `generated bootstrap project domain application and adapter modules compile`() {
        val fixtureDir = Files.createTempDirectory("bootstrap-generated-project-compile")
        FunctionalFixtureSupport.copyFixture(fixtureDir, "bootstrap-generated-project-smoke-sample")

        val (bootstrapResult, compileResult) = FunctionalFixtureSupport.bootstrapThenRunGeneratedProject(
            fixtureDir,
            projectName = "only-danmuku",
            ":only-danmuku-domain:compileKotlin",
            ":only-danmuku-application:compileKotlin",
            ":only-danmuku-adapter:compileKotlin",
        )

        assertTrue(bootstrapResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `generated bootstrap project remains usable with fixed template override and slots`() {
        val fixtureDir = Files.createTempDirectory("bootstrap-generated-project-override")
        FunctionalFixtureSupport.copyFixture(fixtureDir, "bootstrap-generated-project-override-sample")

        val (bootstrapResult, generatedBuildResult) = FunctionalFixtureSupport.bootstrapThenRunGeneratedProject(
            fixtureDir,
            projectName = "only-danmuku",
            "help",
            ":only-danmuku-domain:compileKotlin",
        )
        val generatedRootBuild = fixtureDir.resolve("only-danmuku/build.gradle.kts").readText()
        val generatedReadme = fixtureDir.resolve("only-danmuku/README.md").readText()
        val generatedDomainMarkerPath = fixtureDir.resolve(
            "only-danmuku/only-danmuku-domain/src/main/kotlin/edu/only4/danmuku/OverrideDomainMarker.kt"
        )
        val generatedDomainMarker = generatedDomainMarkerPath.readText()

        assertTrue(bootstrapResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedBuildResult.output.contains("Welcome to Gradle"))
        assertTrue(generatedBuildResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedRootBuild.contains("// override: bootstrap generated-project hardening"))
        assertTrue(generatedReadme.contains("# only-danmuku"))
        assertTrue(generatedDomainMarkerPath.toFile().exists())
        assertTrue(generatedDomainMarker.contains("package edu.only4.danmuku.domain"))
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
