package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class PipelinePluginCompileFunctionalTest {

    @Test
    fun `request and query variants compile in the application module`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "design-compile-sample")
        disableHandlerGenerators(projectDir)

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
        assertGeneratedFilesExist(
            projectDir,
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderListQry.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderPageQry.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/distributed/clients/authorize/IssueTokenCli.kt",
        )
    }

    @Test
    fun `query-handler and client-handler variants compile in the adapter module`() {
        val redProjectDir = Files.createTempDirectory("pipeline-functional-design-compile-adapter-red")
        FunctionalFixtureSupport.copyCompileFixture(redProjectDir, "design-compile-sample")
        removeApplicationCompileSmokeSource(redProjectDir)

        val beforeGenerateCompileResult = FunctionalFixtureSupport
            .runner(redProjectDir, ":demo-adapter:compileKotlin")
            .buildAndFail()
        assertEquals(
            TaskOutcome.FAILED,
            beforeGenerateCompileResult.task(":demo-adapter:compileKotlin")?.outcome
        )
        assertTrue(beforeGenerateCompileResult.output.contains("FindOrderQryHandler"))

        val projectDir = Files.createTempDirectory("pipeline-functional-design-compile-adapter")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "design-compile-sample")
        val (generateResult, compileResult) = FunctionalFixtureSupport.generateThenCompile(
            projectDir,
            ":demo-adapter:compileKotlin"
        )

        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
        assertGeneratedFilesExist(
            projectDir,
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderQryHandler.kt",
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderListQryHandler.kt",
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderPageQryHandler.kt",
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/distributed/clients/authorize/IssueTokenCliHandler.kt",
        )
    }

    private fun assertGeneratedFilesExist(projectDir: Path, vararg relativePaths: String) {
        relativePaths.forEach { relativePath ->
            assertTrue(
                projectDir.resolve(relativePath).toFile().exists(),
                "Expected generated file to exist: $relativePath"
            )
        }
    }

    private fun disableHandlerGenerators(projectDir: Path) {
        val buildFile = projectDir.resolve("build.gradle.kts")
        val designQueryHandlerBlock = Regex(
            """designQueryHandler\s*\{\s*enabled\.set\(true\)\s*}""",
            setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL),
        )
        val designClientHandlerBlock = Regex(
            """designClientHandler\s*\{\s*enabled\.set\(true\)\s*}""",
            setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL),
        )
        val patchedContent = buildFile.readText()
            .replace(designQueryHandlerBlock, "designQueryHandler {\n            enabled.set(false)\n        }")
            .replace(designClientHandlerBlock, "designClientHandler {\n            enabled.set(false)\n        }")
        buildFile.writeText(patchedContent)
        assertTrue(patchedContent.contains("designQueryHandler {\n            enabled.set(false)\n        }"))
        assertTrue(patchedContent.contains("designClientHandler {\n            enabled.set(false)\n        }"))
    }

    private fun removeApplicationCompileSmokeSource(projectDir: Path) {
        val applicationCompileSmokePath = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/smoke/CompileSmoke.kt"
        )
        Files.deleteIfExists(applicationCompileSmokePath)
    }
}
