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
        val commandFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt"
        )
        val queryFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt"
        )
        val commandContent = commandFile.readText()
        val queryContent = queryFile.readText()

        assertTrue(commandFile.toFile().exists())
        assertTrue(queryFile.toFile().exists())
        assertTrue(commandContent.contains("import java.time.LocalDateTime"))
        assertTrue(commandContent.contains("import java.util.UUID"))
        assertFalse(commandContent.contains("import com.foo.Status"))
        assertFalse(commandContent.contains("import com.bar.Status"))
        assertTrue(commandContent.contains("object SubmitOrderCmd"))
        assertTrue(commandContent.contains("data class Request("))
        assertTrue(commandContent.contains("val orderId: Long"))
        assertTrue(commandContent.contains("val submittedAt: LocalDateTime"))
        assertTrue(commandContent.contains("val externalId: UUID"))
        assertTrue(commandContent.contains("val requestStatus: com.foo.Status"))
        assertTrue(commandContent.contains("val address: Address"))
        assertFalse(commandContent.contains("val address: Address??"))
        assertTrue(commandContent.contains("data class Address("))
        assertTrue(commandContent.contains("val city: String"))
        assertTrue(commandContent.contains("val addressId: UUID"))
        assertTrue(commandContent.contains("data class Response("))
        assertTrue(commandContent.contains("val result: Result"))
        assertFalse(commandContent.contains("val result: Result??"))
        assertTrue(commandContent.contains("val responseStatus: com.bar.Status"))
        assertTrue(commandContent.contains("data class Result("))
        assertTrue(commandContent.contains("val receiptId: UUID"))

        assertTrue(queryContent.contains("object FindOrderQry"))
        assertTrue(queryContent.contains("import java.time.LocalDateTime"))
        assertTrue(queryContent.contains("import java.util.UUID"))
        assertFalse(queryContent.contains("import com.foo.Status"))
        assertFalse(queryContent.contains("import com.bar.Status"))
        assertTrue(queryContent.contains("data class Request("))
        assertTrue(queryContent.contains("val orderId: Long"))
        assertTrue(queryContent.contains("val lookupId: UUID"))
        assertTrue(queryContent.contains("data class Response("))
        assertTrue(queryContent.contains("val snapshot: Snapshot"))
        assertFalse(queryContent.contains("val snapshot: Snapshot??"))
        assertTrue(queryContent.contains("val requestStatus: com.foo.Status"))
        assertTrue(queryContent.contains("val responseStatus: com.bar.Status"))
        assertTrue(queryContent.contains("data class Snapshot("))
        assertTrue(queryContent.contains("val updatedAt: LocalDateTime"))
        assertTrue(queryContent.contains("val snapshotId: UUID"))
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
                "        adapterModulePath.set(\"demo-adapter\")",
                "        adapterModulePath.set(\"\")",
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(
            result.output.contains(
                "project.domainModulePath and project.adapterModulePath are required when aggregate is enabled."
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
            buildFile.readText()
                .replace("\r\n", "\n")
                .replace(
                    """
                    |    }
                    |    generators {
                    """.trimMargin(),
                    """
                    |        db {
                    |            enabled.set(true)
                    |            schema.set("PUBLIC")
                    |        }
                    |    }
                    |    generators {
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
                "sources.db.url is required when db is enabled."
            )
        )
        assertFalse(metadataFile.toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan ignores blank db include and exclude tables without triggering aggregate validation`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-blank-db-lists")
        copyFixture(projectDir, "design-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText()
                .replace("\r\n", "\n")
                .replace(
                    """
                    |    }
                    |    generators {
                    """.trimMargin(),
                    """
                    |        db {
                    |            includeTables.set(listOf("   "))
                    |            excludeTables.set(listOf(""))
                    |        }
                    |    }
                    |    generators {
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
