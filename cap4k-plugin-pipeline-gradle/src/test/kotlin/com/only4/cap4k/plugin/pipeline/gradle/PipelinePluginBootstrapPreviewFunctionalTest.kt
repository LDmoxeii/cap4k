package com.only4.cap4k.plugin.pipeline.gradle

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.readText
import kotlin.io.path.writeText

class PipelinePluginBootstrapPreviewFunctionalTest {

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kBootstrap writes explicit preview output under previewDir while keeping project identity`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-bootstrap-preview")
        FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-preview-sample")

        val result = FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrap").build()
        val generatedSettings = projectDir.resolve("bootstrap-preview/settings.gradle.kts").readText()
        val generatedReadme = projectDir.resolve("bootstrap-preview/README.md").readText()
        val generatedMarker = projectDir.resolve(
            "bootstrap-preview/only-danmuku-domain/src/main/kotlin/edu/only4/danmuku/PreviewSlotMarker.kt"
        )

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedSettings.contains("rootProject.name = \"only-danmuku\""))
        assertTrue(generatedReadme.contains("# only-danmuku preview"))
        assertTrue(generatedMarker.toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `generated preview root can rerun cap4kBootstrapPlan`() {
        val fixtureDir = Files.createTempDirectory("pipeline-functional-bootstrap-preview-generated")
        FunctionalFixtureSupport.copyFixture(fixtureDir, "bootstrap-preview-sample")

        val bootstrapResult = FunctionalFixtureSupport.runner(fixtureDir, "cap4kBootstrap").build()
        val generatedDir = FunctionalFixtureSupport.generatedProjectDir(
            fixtureDir,
            projectName = "only-danmuku",
            generatedDirName = "bootstrap-preview",
        )
        val generatedBuildFile = generatedDir.resolve("build.gradle.kts")
        generatedBuildFile.writeText(
            generatedBuildFile.readText() +
                """

                plugins {
                    id("com.only4.cap4k.plugin.pipeline")
                }

                cap4k {
                    bootstrap {
                        enabled.set(true)
                        preset.set("ddd-multi-module")
                        projectName.set("only-danmuku")
                        basePackage.set("edu.only4.danmuku")
                        modules {
                            domainModuleName.set("only-danmuku-domain")
                            applicationModuleName.set("only-danmuku-application")
                            adapterModuleName.set("only-danmuku-adapter")
                        }
                    }
                }
                """.trimIndent()
        )
        val generatedPlanResult = FunctionalFixtureSupport.generatedProjectRunner(
            fixtureDir,
            projectName = "only-danmuku",
            generatedDirName = "bootstrap-preview",
            "cap4kBootstrapPlan",
        ).build()
        val generatedPlanFile = generatedDir.resolve("build/cap4k/bootstrap-plan.json").readText()

        assertTrue(bootstrapResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedPlanResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedPlanFile.contains("\"outputPath\": \"settings.gradle.kts\""))
    }
}
