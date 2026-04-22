package com.only4.cap4k.plugin.pipeline.gradle

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.readText

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
        val hostRootReadme = projectDir.resolve("README.md")
        val hostRootMarker = projectDir.resolve(
            "only-danmuku-domain/src/main/kotlin/edu/only4/danmuku/PreviewSlotMarker.kt"
        )

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedSettings.contains("rootProject.name = \"only-danmuku\""))
        assertTrue(generatedReadme.contains("# only-danmuku preview"))
        assertTrue(generatedMarker.toFile().exists())
        assertFalse(hostRootReadme.toFile().exists())
        assertFalse(hostRootMarker.toFile().exists())
    }
}
