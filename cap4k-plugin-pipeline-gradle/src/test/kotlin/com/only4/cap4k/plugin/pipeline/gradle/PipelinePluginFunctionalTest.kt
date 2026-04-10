package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.readText
import kotlin.io.path.writeText

class PipelinePluginFunctionalTest {

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan writes pretty printed plan json`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-plan")
        copyFixture(projectDir)

        val metadataFile = projectDir.resolve("domain/build/generated/ksp/main/resources/metadata/aggregate-Order.json")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .build()

        val planFile = projectDir.resolve("build/cap4k/plan.json").toFile()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(metadataFile.toFile().exists())
        assertTrue(planFile.exists())
        assertTrue(planFile.readText().contains("\n  {"))
        assertTrue(planFile.readText().contains("\"templateId\": \"design/command.kt.peb\""))
        assertTrue(planFile.readText().contains("\"templateId\": \"design/query.kt.peb\""))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate renders command and query files from repository config`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-generate")
        copyFixture(projectDir)

        val metadataFile = projectDir.resolve("domain/build/generated/ksp/main/resources/metadata/aggregate-Order.json")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(metadataFile.toFile().exists())
        assertTrue(
            File(
                projectDir.toFile(),
                "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt"
            ).exists()
        )
        assertTrue(
            File(
                projectDir.toFile(),
                "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt"
            ).exists()
        )
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate keeps existing files on rerun because default conflict policy is skip`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-rerun")
        copyFixture(projectDir)

        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        val commandFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt"
        )
        commandFile.writeText("sentinel")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(commandFile.readText() == "sentinel")
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan and cap4kGenerate produce aggregate artifacts from db schema`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate")
        copyFixture(projectDir, "aggregate-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan", "cap4kGenerate")
            .build()

        val planFile = projectDir.resolve("build/cap4k/plan.json").toFile()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planFile.exists())
        assertTrue(
            File(
                projectDir.toFile(),
                "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPost.kt"
            ).exists()
        )
        assertTrue(
            File(
                projectDir.toFile(),
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
            ).exists()
        )
        assertTrue(
            File(
                projectDir.toFile(),
                "demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/repositories/VideoPostRepository.kt"
            ).exists()
        )
        assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/entity.kt.peb\""))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan fails fast on partial aggregate configuration`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-invalid")
        copyFixture(projectDir, "aggregate-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace(
                "    adapterModulePath.set(\"demo-adapter\")",
                "    adapterModulePath.set(\"\")",
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(
            result.output.contains(
                "Aggregate pipeline config requires dbUrl, domainModulePath, and adapterModulePath when any are set."
            )
        )
        assertFalse(projectDir.resolve("build/cap4k/plan.json").toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan fails during configuration when auxiliary db field is set without aggregate trio`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-invalid")
        copyFixture(projectDir, "design-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace(
                "    templateOverrideDir.set(\"codegen/templates\")",
                """
                |    templateOverrideDir.set("codegen/templates")
                |    dbSchema.set("PUBLIC")
                """.trimMargin()
            )
        )

        val metadataFile = projectDir.resolve("domain/build/generated/ksp/main/resources/metadata/aggregate-Order.json")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(
            result.output.contains(
                "Aggregate pipeline config requires dbUrl, domainModulePath, and adapterModulePath when any are set."
            )
        )
        assertTrue(result.output.contains("Missing: dbUrl, domainModulePath, adapterModulePath."))
        assertFalse(metadataFile.toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan ignores blank db include and exclude tables without triggering aggregate validation`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-blank-db-lists")
        copyFixture(projectDir, "design-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace(
                "    templateOverrideDir.set(\"codegen/templates\")",
                """
                |    templateOverrideDir.set("codegen/templates")
                |    dbIncludeTables.set(listOf("   "))
                |    dbExcludeTables.set(listOf(""))
                """.trimMargin()
            )
        )

        val metadataFile = projectDir.resolve("domain/build/generated/ksp/main/resources/metadata/aggregate-Order.json")
        val planFile = projectDir.resolve("build/cap4k/plan.json").toFile()

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(metadataFile.toFile().exists())
        assertTrue(planFile.exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan and cap4kGenerate produce flow artifacts from ir analysis fixture`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-flow")
        copyFixture(projectDir, "flow-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan", "cap4kGenerate")
            .build()

        val planFile = projectDir.resolve("build/cap4k/plan.json")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planFile.toFile().exists())
        assertTrue(planFile.readText().contains("\"templateId\": \"flow/index.json.peb\""))
        assertTrue(projectDir.resolve("flows/OrderController_submit.json").toFile().exists())
        assertTrue(projectDir.resolve("flows/OrderController_submit.mmd").toFile().exists())
        assertTrue(projectDir.resolve("flows/index.json").toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan and cap4kGenerate produce drawing board artifacts from ir analysis fixture`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-drawing-board")
        copyFixture(projectDir, "drawing-board-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan", "cap4kGenerate")
            .build()

        val planFile = projectDir.resolve("build/cap4k/plan.json")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planFile.toFile().exists())
        assertTrue(planFile.readText().contains("\"templateId\": \"drawing-board/document.json.peb\""))
        assertTrue(projectDir.resolve("design/drawing_board_cli.json").toFile().exists())
        assertTrue(projectDir.resolve("design/drawing_board_cmd.json").toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan depends on compileKotlin when flow input is produced during compilation`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-flow-compile")
        copyFixture(projectDir, "flow-compile-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(projectDir.resolve("build/cap4k-code-analysis/nodes.json").toFile().exists())
        assertTrue(projectDir.resolve("build/cap4k/plan.json").toFile().exists())
        assertTrue(projectDir.resolve("build/cap4k/plan.json").readText().contains("\"templateId\": \"flow/index.json.peb\""))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan fails clearly when ir analysis fixture misses rels json`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-flow-invalid")
        copyFixture(projectDir, "flow-sample")
        projectDir.resolve("analysis/app/build/cap4k-code-analysis/rels.json").toFile().delete()

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(result.output.contains("ir-analysis inputDir is missing nodes.json or rels.json"))
        assertFalse(projectDir.resolve("build/cap4k/plan.json").toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    private fun copyFixture(targetDir: Path, fixtureName: String = "design-sample") {
        val sourceDir = Path.of(
            requireNotNull(javaClass.getResource("/functional/$fixtureName")) {
                "Missing functional fixture directory"
            }.toURI()
        )
        sourceDir.copyToRecursively(targetDir, followLinks = false)
    }
}
