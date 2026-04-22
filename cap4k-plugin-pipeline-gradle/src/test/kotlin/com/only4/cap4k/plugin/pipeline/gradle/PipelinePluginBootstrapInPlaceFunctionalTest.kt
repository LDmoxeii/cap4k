package com.only4.cap4k.plugin.pipeline.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteExisting
import kotlin.io.path.readText
import kotlin.io.path.writeText

class PipelinePluginBootstrapInPlaceFunctionalTest {

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kBootstrap upgrades a managed minimal host root in place`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-bootstrap-in-place")
        FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-sample")

        val result = FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrap").build()
        val buildFile = projectDir.resolve("build.gradle.kts").readText()
        val settingsFile = projectDir.resolve("settings.gradle.kts").readText()
        val expectedRootSlotPath = projectDir.resolve("codegen/bootstrap-slots/root").toAbsolutePath().normalize()
            .toString()
            .replace("\\", "/")
        val expectedDomainSlotPath = projectDir.resolve("codegen/bootstrap-slots/domain-package").toAbsolutePath().normalize()
            .toString()
            .replace("\\", "/")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(projectDir.resolve("only-danmuku-domain/build.gradle.kts").toFile().exists())
        assertTrue(projectDir.resolve("README.md").toFile().exists())
        assertTrue(buildFile.contains("// [cap4k-bootstrap:managed-begin:root-host]"))
        assertTrue(buildFile.contains("mode.set(BootstrapMode.IN_PLACE)"))
        assertTrue(buildFile.contains("templates {"))
        assertTrue(buildFile.contains("preset.set(\"ddd-default-bootstrap\")"))
        assertTrue(buildFile.contains("slots {"))
        assertTrue(buildFile.contains("root.from(\"codegen/bootstrap-slots/root\")"))
        assertFalse(buildFile.contains(expectedRootSlotPath))
        assertFalse(buildFile.contains(expectedRootSlotPath.replace("/", "\\")))
        assertTrue(buildFile.contains("modulePackage(\"domain\").from(\"codegen/bootstrap-slots/domain-package\")"))
        assertFalse(buildFile.contains(expectedDomainSlotPath))
        assertFalse(buildFile.contains(expectedDomainSlotPath.replace("/", "\\")))
        assertTrue(settingsFile.contains("rootProject.name = \"only-danmuku\""))
        assertTrue(settingsFile.contains("include(\":only-danmuku-domain\")"))
        assertTrue(buildFile.contains("val bootstrapHostBanner = \"host-owned-build-logic\""))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kBootstrap reruns in place and preserves host content outside managed sections`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-bootstrap-rerun")
        FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-sample")

        val firstRun = FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrap").build()
        val buildFile = projectDir.resolve("build.gradle.kts")
        val settingsFile = projectDir.resolve("settings.gradle.kts")
        val generatedReadme = projectDir.resolve("README.md")
        buildFile.writeText(
            buildFile.readText().replace(
                "val bootstrapHostBanner = \"host-owned-build-logic\"",
                "val bootstrapHostBanner = \"host-owned-build-logic\"\nval rerunHostValue = \"kept-on-rerun\""
            )
        )
        settingsFile.writeText(
            settingsFile.readText().replace(
                "dependencyResolutionManagement {",
                "// host-owned-settings-comment\ndependencyResolutionManagement {"
            )
        )
        generatedReadme.deleteExisting()

        val secondRun = FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrap").build()
        val rerunBuild = buildFile.readText()
        val rerunSettings = settingsFile.readText()

        assertTrue(firstRun.output.contains("BUILD SUCCESSFUL"))
        assertTrue(secondRun.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedReadme.toFile().exists())
        assertTrue(rerunBuild.contains("val rerunHostValue = \"kept-on-rerun\""))
        assertTrue(rerunSettings.contains("// host-owned-settings-comment"))
        assertTrue(rerunBuild.contains("// [cap4k-bootstrap:managed-begin:root-host]"))
        assertTrue(rerunBuild.contains("root.from(\"codegen/bootstrap-slots/root\")"))
        assertTrue(rerunBuild.contains("modulePackage(\"domain\").from(\"codegen/bootstrap-slots/domain-package\")"))
        assertEquals(1, Regex("include\\(\":only-danmuku-domain\"\\)").findAll(rerunSettings).count())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kBootstrap fails for unmanaged in place root`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-bootstrap-unmanaged")
        FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-unmanaged-root-sample")

        val result = FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrap").buildAndFail()

        assertFalse(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(result.output.contains("root-host"))
    }
}
