package com.only4.cap4k.plugin.pipeline.gradle

import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.gradle.FunctionalFixtureSupport.copyCompileFixture
import com.only4.cap4k.plugin.pipeline.gradle.FunctionalFixtureSupport.copyFixture
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
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
        assertTrue(planFile.readText().contains("\n  \"items\""))
        assertTrue(planFile.readText().contains("\"diagnostics\""))
        assertTrue(planFile.readText().contains("\"templateId\": \"design/command.kt.peb\""))
        assertTrue(planFile.readText().contains("\"templateId\": \"design/query.kt.peb\""))
        assertFalse(planFile.readText().contains("\"templateId\": \"design/query_" + "list.kt.peb\""))
        assertFalse(planFile.readText().contains("\"templateId\": \"design/query_" + "page.kt.peb\""))
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
        assertTrue(commandContent.contains("val mirroredSubmittedAt: LocalDateTime"))
        assertTrue(commandContent.contains("val externalId: UUID"))
        assertTrue(commandContent.contains("val trackingId: UUID"))
        assertTrue(commandContent.contains("val requestStatus: com.foo.Status"))
        assertTrue(commandContent.contains("val address: Address?"))
        assertFalse(commandContent.contains("val address: Address??"))
        assertTrue(commandContent.contains("data class Address("))
        assertTrue(commandContent.contains("val city: String"))
        assertTrue(commandContent.contains("val addressId: UUID"))
        assertTrue(commandContent.contains("data class Response("))
        assertTrue(commandContent.contains("val result: Result?"))
        assertFalse(commandContent.contains("val result: Result??"))
        assertTrue(commandContent.contains("val responseStatus: com.bar.Status"))
        assertTrue(commandContent.contains("data class Result("))
        assertTrue(commandContent.contains("val receiptId: UUID"))

        assertTrue(queryContent.contains("object FindOrderQry"))
        assertTrue(queryContent.contains("import com.only4.cap4k.ddd.core.application.RequestParam"))
        assertTrue(queryContent.contains("import java.time.LocalDateTime"))
        assertTrue(queryContent.contains("import java.util.UUID"))
        assertFalse(queryContent.contains("import com.foo.Status"))
        assertFalse(queryContent.contains("import com.bar.Status"))
        assertTrue(queryContent.contains("data class Request("))
        assertTrue(queryContent.contains(") : RequestParam<Response>"))
        assertTrue(queryContent.contains("val orderId: Long"))
        assertTrue(queryContent.contains("val lookupId: UUID"))
        assertTrue(queryContent.contains("val lookupMirrorId: UUID"))
        assertTrue(queryContent.contains("data class Response("))
        assertTrue(queryContent.contains("val snapshot: Snapshot?"))
        assertFalse(queryContent.contains("val snapshot: Snapshot??"))
        assertTrue(queryContent.contains("val requestStatus: com.foo.Status"))
        assertTrue(queryContent.contains("val responseStatus: com.bar.Status"))
        assertTrue(queryContent.contains("data class Snapshot("))
        assertTrue(queryContent.contains("val updatedAt: LocalDateTime"))
        assertTrue(queryContent.contains("val publishedAt: LocalDateTime"))
        assertTrue(queryContent.contains("val snapshotId: UUID"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate renders contract first list and page query envelopes`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-generate-list-page")
        copyFixture(projectDir)

        val metadataFile = projectDir.resolve("domain/build/generated/ksp/main/resources/metadata/aggregate-Order.json")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(metadataFile.toFile().exists())

        val listQueryFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderListQry.kt"
        )
        val pageQueryFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderPageQry.kt"
        )
        val listQueryContent = listQueryFile.readText()
        val pageQueryContent = pageQueryFile.readText()

        assertTrue(listQueryFile.toFile().exists())
        assertTrue(pageQueryFile.toFile().exists())

        assertTrue(listQueryContent.contains("import com.only4.cap4k.ddd.core.application.RequestParam"))
        assertFalse(listQueryContent.contains("import com.foo.Status"))
        assertTrue(listQueryContent.contains("class Request : RequestParam<Response>"))
        assertTrue(listQueryContent.contains("data class Response("))
        assertTrue(listQueryContent.contains("val items: List<Item>"))
        assertTrue(listQueryContent.contains("data class Item("))
        assertTrue(listQueryContent.contains("val responseStatus: Status"))
        assertTrue(listQueryContent.contains("val summary: Summary?"))
        assertFalse(listQueryContent.contains("val summary: Summary??"))
        assertTrue(listQueryContent.contains("data class Summary("))
        assertTrue(listQueryContent.contains("val updatedAt: LocalDateTime"))
        assertTrue(listQueryContent.contains("val summaryId: UUID"))
        assertFalse(listQueryContent.contains("List" + "QueryParam"))
        assertFalse(listQueryContent.contains("List" + "Query<"))

        assertTrue(pageQueryContent.contains("import com.only4.cap4k.ddd.core.application.RequestParam"))
        assertTrue(pageQueryContent.contains("import com.only4.cap4k.ddd.core.application.query.PageRequest"))
        assertTrue(pageQueryContent.contains("import com.only4.cap4k.ddd.core.share.PageData"))
        assertFalse(pageQueryContent.contains("import com.foo.Status"))
        assertTrue(pageQueryContent.contains("data class Request("))
        assertTrue(pageQueryContent.contains("override val pageNum: Int = 1"))
        assertTrue(pageQueryContent.contains("override val pageSize: Int"))
        assertTrue(pageQueryContent.contains(") : PageRequest, RequestParam<Response>"))
        assertTrue(pageQueryContent.contains("val keyword: String"))
        assertTrue(pageQueryContent.contains("val createdAfter: LocalDateTime"))
        assertTrue(pageQueryContent.contains("val requestStatus: com.foo.Status"))
        assertTrue(pageQueryContent.contains("data class Response("))
        assertTrue(pageQueryContent.contains("val page: PageData<Item>"))
        assertTrue(pageQueryContent.contains("data class Item("))
        assertTrue(pageQueryContent.contains("val responseStatus: com.bar.Status"))
        assertTrue(pageQueryContent.contains("val snapshot: Snapshot?"))
        assertFalse(pageQueryContent.contains("val snapshot: Snapshot??"))
        assertTrue(pageQueryContent.contains("data class Snapshot("))
        assertTrue(pageQueryContent.contains("val publishedAt: LocalDateTime"))
        assertTrue(pageQueryContent.contains("val snapshotId: UUID"))
        assertFalse(pageQueryContent.contains("Page" + "QueryParam"))
        assertFalse(pageQueryContent.contains("Page" + "Query<"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate renders explicit default values in generated design source`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-default-values")
        copyFixture(projectDir, "design-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        val generatedFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt"
        )
        val content = generatedFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedFile.toFile().exists())
        assertTrue(content.contains("val title: String = \"demo\""))
        assertTrue(content.contains("val orderId: Long = 1L"))
        assertTrue(content.contains("val enabled: Boolean = true"))
        assertTrue(content.contains("val tags: List<String> = emptyList()"))
        assertTrue(content.contains("val createdAt: LocalDateTime = java.time.LocalDateTime.MIN"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate fails fast for invalid design default value`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-invalid-default-value")
        copyFixture(projectDir, "design-default-value-invalid-sample")
        val generatedFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/video/post/InvalidVideoPostCmd.kt"
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .buildAndFail()

        assertTrue(result.output.contains("invalid default value for field enabled"))
        assertTrue(result.output.contains("Boolean defaults must be true or false"))
        assertFalse(generatedFile.toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate supports unified query template override for all query names`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-list-page-override")
        copyFixture(projectDir)

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                """
                |        designQuery {
                |            enabled.set(true)
                |        }
                """.trimMargin(),
                """
                |        designQuery {
                |            enabled.set(true)
                |        }
                |        templates {
                |            overrideDirs.from("codegen/templates")
                |        }
                """.trimMargin()
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        val listQueryFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderListQry.kt"
        )
        val pageQueryFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderPageQry.kt"
        )
        val defaultQueryFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt"
        )
        val defaultQueryContent = defaultQueryFile.readText()
        val listQueryContent = listQueryFile.readText()
        val pageQueryContent = pageQueryFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(defaultQueryFile.toFile().exists())
        assertTrue(listQueryFile.toFile().exists())
        assertTrue(pageQueryFile.toFile().exists())

        assertTrue(defaultQueryContent.contains("// override: representative default query migration template"))
        assertTrue(listQueryContent.contains("// override: representative default query migration template"))
        assertTrue(pageQueryContent.contains("// override: representative default query migration template"))
        assertTrue(listQueryContent.contains("import com.only4.cap4k.ddd.core.application.RequestParam"))
        assertTrue(listQueryContent.contains("class Request : RequestParam<Response>"))
        assertFalse(listQueryContent.contains("import com.foo.Status"))
        assertTrue(listQueryContent.contains("val items: List<Item>"))
        assertTrue(listQueryContent.contains("val responseStatus: Status"))
        assertTrue(listQueryContent.contains("data class Summary("))
        assertTrue(listQueryContent.contains("val updatedAt: LocalDateTime"))
        assertTrue(listQueryContent.contains("val summaryId: UUID"))

        assertTrue(pageQueryContent.contains("import com.only4.cap4k.ddd.core.application.RequestParam"))
        assertTrue(pageQueryContent.contains("import com.only4.cap4k.ddd.core.share.PageData"))
        assertTrue(pageQueryContent.contains("data class Request("))
        assertTrue(pageQueryContent.contains(") : RequestParam<Response>"))
        assertFalse(pageQueryContent.contains("import com.foo.Status"))
        assertTrue(pageQueryContent.contains("val keyword: String"))
        assertTrue(pageQueryContent.contains("val createdAfter: LocalDateTime"))
        assertTrue(pageQueryContent.contains("val requestStatus: com.foo.Status"))
        assertTrue(pageQueryContent.contains("val page: PageData<Item>"))
        assertTrue(pageQueryContent.contains("val responseStatus: com.bar.Status"))
        assertTrue(pageQueryContent.contains("data class Snapshot("))
        assertTrue(pageQueryContent.contains("val publishedAt: LocalDateTime"))
        assertTrue(pageQueryContent.contains("val snapshotId: UUID"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate supports migration friendly override design templates`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-helper-override")
        copyFixture(projectDir)

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                """
                |        designQuery {
                |            enabled.set(true)
                |        }
                """.trimMargin(),
                """
                |        designQuery {
                |            enabled.set(true)
                |        }
                |        templates {
                |            overrideDirs.from("codegen/templates")
                |        }
                """.trimMargin()
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        val commandFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt"
        )
        val queryFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt"
        )
        val commandContent = commandFile.readText()
        val queryContent = queryFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(commandFile.toFile().exists())
        assertTrue(queryFile.toFile().exists())
        assertTrue(commandContent.contains("// override: migration-friendly design template"))
        assertTrue(queryContent.contains("// override: representative default query migration template"))
        assertTrue(commandContent.contains("import java.io.Serializable"))
        assertTrue(commandContent.contains("object SubmitOrderCmd : Serializable"))
        assertTrue(commandContent.contains("data class Request("))
        assertTrue(commandContent.contains("data class Response("))
        assertTrue(commandContent.contains("import java.time.LocalDateTime"))
        assertTrue(commandContent.contains("import java.util.UUID"))
        assertFalse(commandContent.contains("import com.foo.Status"))
        assertFalse(commandContent.contains("import com.bar.Status"))
        assertTrue(commandContent.contains("val orderId: Long = 1L"))
        assertTrue(commandContent.contains("val title: String = \"demo\""))
        assertTrue(commandContent.contains("val createdAt: LocalDateTime = java.time.LocalDateTime.MIN"))
        assertTrue(commandContent.contains("val mirroredSubmittedAt: LocalDateTime"))
        assertTrue(commandContent.contains("val trackingId: UUID"))
        assertTrue(commandContent.contains("val requestStatus: com.foo.Status"))
        assertTrue(commandContent.contains("val responseStatus: com.bar.Status"))
        assertTrue(commandContent.contains("val address: Address?"))
        assertFalse(commandContent.contains("val address: Address??"))
        assertTrue(commandContent.contains("val result: Result?"))
        assertFalse(commandContent.contains("val result: Result??"))
        assertTrue(commandContent.contains("data class Address("))
        assertTrue(commandContent.contains("val city: String"))
        assertTrue(commandContent.contains("val addressId: UUID"))
        assertTrue(commandContent.contains("data class Result("))
        assertTrue(commandContent.contains("val receiptId: UUID"))
        assertTrue(queryContent.contains("object FindOrderQry"))
        assertTrue(queryContent.contains("data class Request("))
        assertTrue(queryContent.contains("data class Response("))
        assertTrue(queryContent.contains("import java.time.LocalDateTime"))
        assertTrue(queryContent.contains("import java.util.UUID"))
        assertFalse(queryContent.contains("import com.foo.Status"))
        assertFalse(queryContent.contains("import com.bar.Status"))
        assertTrue(queryContent.contains("val lookupId: UUID"))
        assertTrue(queryContent.contains("val lookupMirrorId: UUID"))
        assertTrue(queryContent.contains("val requestStatus: com.foo.Status"))
        assertTrue(queryContent.contains("val responseStatus: com.bar.Status"))
        assertTrue(queryContent.contains("val snapshot: Snapshot?"))
        assertFalse(queryContent.contains("val snapshot: Snapshot??"))
        assertTrue(queryContent.contains("data class Snapshot("))
        assertTrue(queryContent.contains("val updatedAt: LocalDateTime"))
        assertTrue(queryContent.contains("val publishedAt: LocalDateTime"))
        assertTrue(queryContent.contains("val snapshotId: UUID"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan includes query handler artifacts when enabled`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-query-handler-plan")
        copyFixture(projectDir, "design-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText()
                .replace("\r\n", "\n")
                .replace(
                    """
                    |        designQuery {
                    |            enabled.set(true)
                    |        }
                    """.trimMargin(),
                    """
                    |        designQuery {
                    |            enabled.set(true)
                    |        }
                    |        designQueryHandler {
                    |            enabled.set(true)
                    |        }
                    """.trimMargin()
                )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .build()

        val planFile = projectDir.resolve("build/cap4k/plan.json").toFile()
        val content = planFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planFile.exists())
        assertTrue(content.contains("\"templateId\": \"design/query_handler.kt.peb\""))
        assertFalse(content.contains("\"templateId\": \"design/query_" + "list_handler.kt.peb\""))
        assertFalse(content.contains("\"templateId\": \"design/query_" + "page_handler.kt.peb\""))
        assertTrue(content.contains("demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt"))
        assertTrue(content.contains("demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderListQry.kt"))
        assertTrue(content.contains("demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderPageQry.kt"))
        assertTrue(content.contains("demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/queries/order/read/FindOrderQryHandler.kt"))
        assertTrue(content.contains("demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/queries/order/read/FindOrderListQryHandler.kt"))
        assertTrue(content.contains("demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/queries/order/read/FindOrderPageQryHandler.kt"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate renders query handlers with the unified query contract`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-query-handler-generate")
        copyFixture(projectDir, "design-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText()
                .replace("\r\n", "\n")
                .replace(
                    """
                    |        designQuery {
                    |            enabled.set(true)
                    |        }
                    """.trimMargin(),
                    """
                    |        designQuery {
                    |            enabled.set(true)
                    |        }
                    |        designQueryHandler {
                    |            enabled.set(true)
                    |        }
                    """.trimMargin()
                )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        val defaultHandlerFile = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/queries/order/read/FindOrderQryHandler.kt"
        )
        val listHandlerFile = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/queries/order/read/FindOrderListQryHandler.kt"
        )
        val pageHandlerFile = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/queries/order/read/FindOrderPageQryHandler.kt"
        )
        val defaultQueryFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt"
        )
        val listQueryFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderListQry.kt"
        )
        val pageQueryFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderPageQry.kt"
        )

        val defaultContent = defaultHandlerFile.readText()
        val listContent = listHandlerFile.readText()
        val pageContent = pageHandlerFile.readText()
        val defaultQueryContent = defaultQueryFile.readText()
        val listQueryContent = listQueryFile.readText()
        val pageQueryContent = pageQueryFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(defaultHandlerFile.toFile().exists())
        assertTrue(listHandlerFile.toFile().exists())
        assertTrue(pageHandlerFile.toFile().exists())
        assertTrue(defaultQueryFile.toFile().exists())
        assertTrue(listQueryFile.toFile().exists())
        assertTrue(pageQueryFile.toFile().exists())

        assertTrue(defaultContent.contains("import com.only4.cap4k.ddd.core.application.query.Query"))
        assertTrue(defaultContent.contains("import com.acme.demo.application.queries.order.read.FindOrderQry"))
        assertTrue(defaultContent.contains("class FindOrderQryHandler : Query<FindOrderQry.Request, FindOrderQry.Response>"))
        assertTrue(defaultContent.contains("responseStatus = TODO(\"set responseStatus\")"))
        assertTrue(defaultContent.contains("snapshot = TODO(\"set snapshot\")"))

        assertTrue(listContent.contains("import com.only4.cap4k.ddd.core.application.query.Query"))
        assertTrue(listContent.contains("import com.acme.demo.application.queries.order.read.FindOrderListQry"))
        assertTrue(listContent.contains("class FindOrderListQryHandler : Query<FindOrderListQry.Request, FindOrderListQry.Response>"))
        assertTrue(listContent.contains(" : Query<"))
        assertTrue(listContent.contains(".Request, "))
        assertTrue(listContent.contains(".Response>"))
        assertTrue(listContent.contains("items = TODO(\"set items\")"))
        assertFalse(listContent.contains("List" + "Query<"))
        assertFalse(listContent.contains("Page" + "Query<"))
        assertFalse(listContent.contains("List<FindOrder" + "ListQry.Response>"))
        assertFalse(listContent.contains("PageData<FindOrder" + "PageQry.Response>"))

        assertTrue(pageContent.contains("import com.only4.cap4k.ddd.core.application.query.Query"))
        assertTrue(pageContent.contains("import com.acme.demo.application.queries.order.read.FindOrderPageQry"))
        assertTrue(pageContent.contains("class FindOrderPageQryHandler : Query<FindOrderPageQry.Request, FindOrderPageQry.Response>"))
        assertTrue(pageContent.contains(" : Query<"))
        assertTrue(pageContent.contains(".Request, "))
        assertTrue(pageContent.contains(".Response>"))
        assertTrue(pageContent.contains("page = TODO(\"set page\")"))
        assertFalse(pageContent.contains("List" + "Query<"))
        assertFalse(pageContent.contains("Page" + "Query<"))
        assertFalse(pageContent.contains("List<FindOrder" + "ListQry.Response>"))
        assertFalse(pageContent.contains("PageData<FindOrder" + "PageQry.Response>"))

        assertTrue(defaultQueryContent.contains("object FindOrderQry"))
        assertTrue(defaultQueryContent.contains("data class Request("))
        assertTrue(defaultQueryContent.contains(") : RequestParam<Response>"))
        assertTrue(listQueryContent.contains("object FindOrderListQry"))
        assertTrue(listQueryContent.contains("class Request : RequestParam<Response>"))
        assertTrue(pageQueryContent.contains("object FindOrderPageQry"))
        assertTrue(pageQueryContent.contains("data class Request("))
        assertTrue(pageQueryContent.contains(") : PageRequest, RequestParam<Response>"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate supports override query handler templates`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-query-handler-override")
        copyFixture(projectDir, "design-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                """
                |    generators {
                |        designCommand {
                |            enabled.set(true)
                |        }
                |        designQuery {
                |            enabled.set(true)
                |        }
                |        designClient {
                |            enabled.set(true)
                |        }
                |        designClientHandler {
                |            enabled.set(true)
                |        }
                |    }
                """.trimMargin(),
                """
                |    generators {
                |        designCommand {
                |            enabled.set(true)
                |        }
                |        designQuery {
                |            enabled.set(true)
                |        }
                |        designClient {
                |            enabled.set(true)
                |        }
                |        designClientHandler {
                |            enabled.set(true)
                |        }
                |        designQueryHandler {
                |            enabled.set(true)
                |        }
                |    }
                |    templates {
                |        overrideDirs.from("codegen/templates")
                |    }
                """.trimMargin()
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        val defaultContent = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/queries/order/read/FindOrderQryHandler.kt"
        ).readText()
        val listContent = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/queries/order/read/FindOrderListQryHandler.kt"
        ).readText()
        val pageContent = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/queries/order/read/FindOrderPageQryHandler.kt"
        ).readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(defaultContent.contains("// override: representative default query handler migration template"))
        assertTrue(listContent.contains("// override: representative default query handler migration template"))
        assertTrue(pageContent.contains("// override: representative default query handler migration template"))
        assertTrue(listContent.contains("class FindOrderListQryHandler : Query<FindOrderListQry.Request, FindOrderListQry.Response>"))
        assertTrue(pageContent.contains("class FindOrderPageQryHandler : Query<FindOrderPageQry.Request, FindOrderPageQry.Response>"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan fails fast when design query handler lacks adapter module path`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-query-handler-missing-adapter")
        copyFixture(projectDir, "design-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent
                .replace("        adapterModulePath.set(\"demo-adapter\")", "        adapterModulePath.set(\"\")")
                .replace(
                    """
                    |        designQuery {
                    |            enabled.set(true)
                    |        }
                    """.trimMargin(),
                    """
                    |        designQuery {
                    |            enabled.set(true)
                    |        }
                    |        designQueryHandler {
                    |            enabled.set(true)
                    |        }
                    """.trimMargin()
                )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(result.output.contains("project.adapterModulePath is required when designQueryHandler is enabled."))
        assertFalse(projectDir.resolve("build/cap4k/plan.json").toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan fails fast when design query handler is enabled without design query`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-query-handler-without-design")
        copyFixture(projectDir, "design-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent
                .replace(
                    """
                    |        designQuery {
                    |            enabled.set(true)
                    |        }
                    """.trimMargin(),
                    """
                    |        designQuery {
                    |            enabled.set(false)
                    |        }
                    |        designQueryHandler {
                    |            enabled.set(true)
                    |        }
                    """.trimMargin()
                )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(result.output.contains("designQueryHandler generator requires enabled designQuery generator."))
        assertFalse(projectDir.resolve("build/cap4k/plan.json").toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan includes client and client handler artifacts from fixture`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-client-plan")
        copyFixture(projectDir, "design-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .build()

        val planFile = projectDir.resolve("build/cap4k/plan.json").toFile()
        val content = planFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planFile.exists())
        assertTrue(content.contains("\"templateId\": \"design/client.kt.peb\""))
        assertTrue(content.contains("\"templateId\": \"design/client_handler.kt.peb\""))
        assertTrue(
            content.contains(
                "demo-application/src/main/kotlin/com/acme/demo/application/distributed/clients/authorize/IssueTokenCli.kt"
            )
        )
        assertTrue(
            content.contains(
                "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/distributed/clients/authorize/IssueTokenCliHandler.kt"
            )
        )
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate renders client and client handler files from fixture`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-client-generate")
        copyFixture(projectDir, "design-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        val clientFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/distributed/clients/authorize/IssueTokenCli.kt"
        )
        val handlerFile = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/distributed/clients/authorize/IssueTokenCliHandler.kt"
        )
        val clientContent = clientFile.readText()
        val handlerContent = handlerFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(clientFile.toFile().exists())
        assertTrue(handlerFile.toFile().exists())
        assertTrue(clientContent.contains("import com.only4.cap4k.ddd.core.application.RequestParam"))
        assertTrue(clientContent.contains("object IssueTokenCli"))
        assertTrue(clientContent.contains(") : RequestParam<Response>"))
        assertTrue(clientContent.contains("val account: String"))
        assertTrue(clientContent.contains("val token: String"))
        assertTrue(handlerContent.contains("import com.only4.cap4k.ddd.core.application.RequestHandler"))
        assertTrue(handlerContent.contains("import com.acme.demo.application.distributed.clients.authorize.IssueTokenCli"))
        assertTrue(
            handlerContent.contains(
                "class IssueTokenCliHandler : RequestHandler<IssueTokenCli.Request, IssueTokenCli.Response>"
            )
        )
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate supports override client and client handler templates`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-client-override")
        copyFixture(projectDir, "design-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                """
                |    generators {
                |        designCommand {
                |            enabled.set(true)
                |        }
                |        designQuery {
                |            enabled.set(true)
                |        }
                |        designClient {
                |            enabled.set(true)
                |        }
                |        designClientHandler {
                |            enabled.set(true)
                |        }
                |    }
                """.trimMargin(),
                """
                |    generators {
                |        designCommand {
                |            enabled.set(true)
                |        }
                |        designQuery {
                |            enabled.set(true)
                |        }
                |        designClient {
                |            enabled.set(true)
                |        }
                |        designClientHandler {
                |            enabled.set(true)
                |        }
                |    }
                |    templates {
                |        overrideDirs.from("codegen/templates")
                |    }
                """.trimMargin()
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        val clientContent = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/distributed/clients/authorize/IssueTokenCli.kt"
        ).readText()
        val handlerContent = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/distributed/clients/authorize/IssueTokenCliHandler.kt"
        ).readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(clientContent.contains("// override: representative client migration template"))
        assertTrue(handlerContent.contains("// override: representative client handler migration template"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan fails fast when design client lacks application module path`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-client-missing-application")
        copyFixture(projectDir, "design-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                "        applicationModulePath.set(\"demo-application\")",
                "        applicationModulePath.set(\"\")",
            ).replace(
                """
                |        designCommand {
                |            enabled.set(true)
                |        }
                |        designQuery {
                |            enabled.set(true)
                |        }
                """.trimMargin(),
                """
                |        designCommand {
                |            enabled.set(false)
                |        }
                |        designQuery {
                |            enabled.set(false)
                |        }
                """.trimMargin()
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(result.output.contains("project.applicationModulePath is required when designClient is enabled."))
        assertFalse(projectDir.resolve("build/cap4k/plan.json").toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan fails fast when design client handler is enabled without design client`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-client-handler-without-client")
        copyFixture(projectDir, "design-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                """
                |        designClient {
                |            enabled.set(true)
                |        }
                """.trimMargin(),
                """
                |        designClient {
                |            enabled.set(false)
                |        }
                """.trimMargin()
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(result.output.contains("designClientHandler generator requires enabled designClient generator."))
        assertFalse(projectDir.resolve("build/cap4k/plan.json").toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `plain design generation does not require adapter module path`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-without-adapter-module")
        copyFixture(projectDir, "design-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent
                .replace("        adapterModulePath.set(\"demo-adapter\")", "        adapterModulePath.set(\"\")")
                .replace(
                    """
                    |        designClient {
                    |            enabled.set(true)
                    |        }
                    """.trimMargin(),
                    """
                    |        designClient {
                    |            enabled.set(false)
                    |        }
                    """.trimMargin()
                )
                .replace(
                    """
                    |        designClientHandler {
                    |            enabled.set(true)
                    |        }
                    """.trimMargin(),
                    """
                    |        designClientHandler {
                    |            enabled.set(false)
                    |        }
                    """.trimMargin()
                )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        val defaultQueryFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt"
        )
        val listQueryFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderListQry.kt"
        )
        val pageQueryFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderPageQry.kt"
        )

        val defaultQueryContent = defaultQueryFile.readText()
        val listQueryContent = listQueryFile.readText()
        val pageQueryContent = pageQueryFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(defaultQueryFile.toFile().exists())
        assertTrue(listQueryFile.toFile().exists())
        assertTrue(pageQueryFile.toFile().exists())
        assertTrue(defaultQueryContent.contains(") : RequestParam<Response>"))
        assertTrue(listQueryContent.contains("class Request : RequestParam<Response>"))
        assertTrue(pageQueryContent.contains(") : PageRequest, RequestParam<Response>"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate resolves short type from project type registry`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-type-registry")
        copyFixture(projectDir, "design-type-registry-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        val generatedFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/video/publish/PublishVideoCmd.kt"
        )
        val content = generatedFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedFile.toFile().exists())
        assertTrue(content.contains("import com.acme.demo.domain.video.VideoStatus"))
        assertTrue(content.contains("val targetStatus: VideoStatus"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate fails when same-package sibling request name is used as a short type`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-sibling-short-type")
        copyFixture(projectDir, "design-type-registry-sample")
        projectDir.resolve("iterate/design/registry_design.json").writeText(
            """
            [
              {
                "tag": "command",
                "package": "video.publish",
                "name": "StartVideoProcessing",
                "desc": "start video processing",
                "aggregates": ["Video"],
                "requestFields": [
                  { "name": "fileSpec", "type": "VideoPostProcessingFileSpecQry" }
                ],
                "responseFields": []
              },
              {
                "tag": "query",
                "package": "video.publish",
                "name": "VideoPostProcessingFileSpec",
                "desc": "video processing file spec",
                "aggregates": ["Video"],
                "requestFields": [],
                "responseFields": []
              }
            ]
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .buildAndFail()
        assertTrue(result.output.contains("failed to resolve type for field fileSpec: VideoPostProcessingFileSpecQry"))
        assertTrue(result.output.contains("sibling design-entry references are not supported"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate fails on ambiguous short type`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-ambiguous-short-type")
        copyFixture(projectDir, "design-sample")

        val designFile = projectDir.resolve("design/design.json")
        designFile.writeText(
            designFile.readText().replace(
                """{ "name": "requestStatus", "type": "com.foo.Status" },""",
                """
                { "name": "requestStatus", "type": "com.foo.Status" },
                  { "name": "ambiguousStatus", "type": "Status" },
                """.trimIndent()
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .buildAndFail()

        assertTrue(result.output.contains("failed to resolve type for field ambiguousStatus: Status"))
        assertTrue(result.output.contains("ambiguous short type: Status -> com.foo.Status, com.bar.Status"))
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
    fun `cap4kPlan and cap4kGenerate support manifest driven design inputs`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-manifest")
        copyFixture(projectDir, "design-manifest-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan", "cap4kGenerate")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(projectDir.resolve("build/cap4k/plan.json").readText().contains("\"diagnostics\""))
        assertTrue(
            projectDir.resolve(
                "demo-application/src/main/kotlin/com/acme/demo/application/commands/video/encrypt/GenerateVideoHlsKeyCmd.kt"
            ).toFile().exists()
        )
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
        val factoryFile = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt"
        )
        val specificationFile = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/specification/VideoPostSpecification.kt"
        )
        val wrapperFile = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggVideoPost.kt"
        )
        val behaviorFile = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostBehavior.kt"
        )
        val uniqueQueryFile = projectDir.resolve(
            generatedSource(
                "demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/UniqueVideoPostSlugQry.kt"
            )
        )
        val uniqueQueryHandlerFile = projectDir.resolve(
            generatedSource(
                "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video_post/unique/UniqueVideoPostSlugQryHandler.kt"
            )
        )
        val uniqueValidatorFile = projectDir.resolve(
            generatedSource(
                "demo-application/src/main/kotlin/com/acme/demo/application/validators/video_post/unique/UniqueVideoPostSlug.kt"
            )
        )

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planFile.exists())
        assertTrue(
            File(
                projectDir.toFile(),
                generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPost.kt")
            ).exists()
        )
        assertTrue(
            File(
                projectDir.toFile(),
                generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
            ).exists()
        )
        assertTrue(
            File(
                projectDir.toFile(),
                generatedSource("demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/repositories/VideoPostRepository.kt")
            ).exists()
        )
        assertTrue(factoryFile.toFile().exists())
        assertTrue(specificationFile.toFile().exists())
        assertTrue(wrapperFile.toFile().exists())
        assertTrue(behaviorFile.toFile().exists())
        assertTrue(uniqueQueryFile.toFile().exists())
        assertTrue(uniqueQueryHandlerFile.toFile().exists())
        assertTrue(uniqueValidatorFile.toFile().exists())
        val factoryContent = factoryFile.readText()
        val specificationContent = specificationFile.readText()
        val wrapperContent = wrapperFile.readText()
        val uniqueQueryContent = uniqueQueryFile.readText()
        val uniqueQueryHandlerContent = uniqueQueryHandlerFile.readText()
        val uniqueValidatorContent = uniqueValidatorFile.readText()
        assertTrue(planFile.readText().contains("\"items\""))
        assertTrue(planFile.readText().contains("\"diagnostics\""))
        assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/entity.kt.peb\""))
        assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/factory.kt.peb\""))
        assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/specification.kt.peb\""))
        assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/wrapper.kt.peb\""))
        assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/unique_query.kt.peb\""))
        assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/unique_query_handler.kt.peb\""))
        assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/unique_validator.kt.peb\""))
        assertTrue(
            factoryContent.contains("class VideoPostFactory : AggregateFactory<VideoPostFactory.Payload, VideoPost>")
        )
        assertTrue(factoryContent.contains("import com.acme.demo.domain.aggregates.video_post.VideoPost"))
        assertTrue(specificationContent.contains("class VideoPostSpecification : Specification<VideoPost>"))
        assertTrue(specificationContent.contains("return Result.pass()"))
        assertTrue(wrapperContent.contains("import com.acme.demo.domain.aggregates.video_post.factory.VideoPostFactory"))
        assertTrue(wrapperContent.contains("class AggVideoPost("))
        assertTrue(behaviorFile.readText().contains("Place behavior for VideoPost and its owned entities here."))
        assertTrue(wrapperContent.contains("payload: VideoPostFactory.Payload? = null"))
        assertTrue(wrapperContent.contains(") : Aggregate.Default<VideoPost>(payload)"))
        assertTrue(wrapperContent.contains("val id by lazy { root.id }"))
        assertTrue(
            wrapperContent.contains(
                "class Id(key: Long) : com.only4.cap4k.ddd.core.domain.aggregate.Id.Default<AggVideoPost, Long>(key)"
            )
        )
        assertTrue(uniqueQueryContent.contains("object UniqueVideoPostSlugQry"))
        assertTrue(uniqueQueryContent.contains(") : RequestParam<Response>"))
        assertTrue(uniqueQueryContent.contains("val excludeVideoPostId: Long?"))
        assertTrue(uniqueQueryContent.contains("val exists: Boolean"))
        assertFalse(uniqueQueryContent.contains("val deleted"))
        assertFalse(uniqueQueryContent.contains("val version"))
        assertTrue(uniqueQueryHandlerContent.contains("class UniqueVideoPostSlugQryHandler"))
        assertTrue(uniqueQueryHandlerContent.contains("class UniqueVideoPostSlugQryHandler("))
        assertTrue(
            uniqueQueryHandlerContent.contains(
                "override fun exec(request: UniqueVideoPostSlugQry.Request): UniqueVideoPostSlugQry.Response"
            )
        )
        assertTrue(uniqueQueryHandlerContent.contains("return UniqueVideoPostSlugQry.Response("))
        assertFalse(uniqueQueryHandlerContent.contains("request.deleted"))
        assertFalse(uniqueQueryHandlerContent.contains("request.version"))
        assertTrue(uniqueValidatorContent.contains("annotation class UniqueVideoPostSlug"))
        assertTrue(
            uniqueValidatorContent.contains(
                "@Constraint(validatedBy = [UniqueVideoPostSlug.Validator::class])"
            )
        )
        assertTrue(uniqueValidatorContent.contains("class Validator : ConstraintValidator<UniqueVideoPostSlug, Any>"))
        assertTrue(uniqueValidatorContent.contains("slug = slugTrimmed!!"))
        assertTrue(uniqueValidatorContent.contains("excludeVideoPostId = excludeId"))
        assertFalse(uniqueValidatorContent.contains("deletedField"))
        assertFalse(uniqueValidatorContent.contains("versionField"))
        assertTrue(
            uniqueValidatorContent.contains(
                "import com.acme.demo.application.queries.video_post.unique.UniqueVideoPostSlugQry"
            )
        )
        val planContent = planFile.readText()
        assertUniqueArtifactPlanItemMetadata(
            planContent = planContent,
            templateId = "aggregate/unique_query.kt.peb",
            outputPathSuffix = generatedSource(
                "demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/UniqueVideoPostSlugQry.kt"
            ),
        )
        assertUniqueArtifactPlanItemMetadata(
            planContent = planContent,
            templateId = "aggregate/unique_query_handler.kt.peb",
            outputPathSuffix = generatedSource(
                "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video_post/unique/UniqueVideoPostSlugQryHandler.kt"
            ),
        )
        assertUniqueArtifactPlanItemMetadata(
            planContent = planContent,
            templateId = "aggregate/unique_validator.kt.peb",
            outputPathSuffix = generatedSource(
                "demo-application/src/main/kotlin/com/acme/demo/application/validators/video_post/unique/UniqueVideoPostSlug.kt"
            ),
        )
        assertPlanItemMetadata(
            planContent = planContent,
            templateId = "aggregate/entity.kt.peb",
            outputPathSuffix = generatedSource(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
            ),
            outputKind = "GENERATED_SOURCE",
            resolvedOutputRoot = "demo-domain/build/generated/cap4k/main/kotlin",
            conflictPolicy = "OVERWRITE",
        )
        assertPlanItemMetadata(
            planContent = planContent,
            templateId = "aggregate/behavior.kt.peb",
            outputPathSuffix = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostBehavior.kt",
            outputKind = "CHECKED_IN_SOURCE",
            resolvedOutputRoot = "demo-domain/src/main/kotlin",
            conflictPolicy = "SKIP",
        )
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `aggregate generator defaults to minimal aggregate artifacts`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-minimal")
        copyFixture(projectDir, "aggregate-minimal-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan", "cap4kGenerate")
            .build()

        val planContent = projectDir.resolve("build/cap4k/plan.json").readText()
        val schemaFile = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPost.kt")
        )

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planContent.contains("\"templateId\": \"aggregate/entity.kt.peb\""))
        assertTrue(planContent.contains("\"templateId\": \"aggregate/schema.kt.peb\""))
        assertTrue(planContent.contains("\"templateId\": \"aggregate/repository.kt.peb\""))
        assertTrue(planContent.contains("\"templateId\": \"aggregate/behavior.kt.peb\""))
        assertFalse(planContent.contains("\"templateId\": \"aggregate/factory.kt.peb\""))
        assertFalse(planContent.contains("\"templateId\": \"aggregate/specification.kt.peb\""))
        assertFalse(planContent.contains("\"templateId\": \"aggregate/wrapper.kt.peb\""))
        assertFalse(planContent.contains("\"templateId\": \"aggregate/unique_query.kt.peb\""))
        assertFalse(planContent.contains("\"templateId\": \"aggregate/unique_query_handler.kt.peb\""))
        assertFalse(planContent.contains("\"templateId\": \"aggregate/unique_validator.kt.peb\""))
        assertFalse(planContent.contains("\"templateId\": \"aggregate/enum_translation.kt.peb\""))
        assertFalse(planContent.contains("\"templateId\": \"aggregate/schema_base.kt.peb\""))
        assertTrue(
            projectDir.resolve(
                generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
            ).toFile().exists()
        )
        assertTrue(schemaFile.toFile().exists())
        assertTrue(
            projectDir.resolve(
                generatedSource("demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/repositories/VideoPostRepository.kt")
            ).toFile().exists()
        )
        assertTrue(
            projectDir.resolve(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostBehavior.kt"
            ).toFile().exists()
        )
        assertFalse(
            projectDir.resolve(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt"
            ).toFile().exists()
        )
        assertFalse(
            projectDir.resolve(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/specification/VideoPostSpecification.kt"
            ).toFile().exists()
        )
        assertFalse(
            projectDir.resolve(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggVideoPost.kt"
            ).toFile().exists()
        )
        assertFalse(
            projectDir.resolve(
                generatedSource(
                    "demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/UniqueVideoPostSlugQry.kt"
                )
            ).toFile().exists()
        )
        assertFalse(
            projectDir.resolve(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/Schema.kt"
            ).toFile().exists()
        )
        val schemaContent = schemaFile.readText()
        assertTrue(schemaContent.contains("fun predicateById(id: Any): JpaPredicate<VideoPost>"))
        assertFalse(schemaContent.contains("AggregatePredicate"))
        assertFalse(schemaContent.contains("AggVideoPost"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerateSources writes only generated source and cap4kGenerate preserves behavior scaffold`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-generated-sources")
        copyFixture(projectDir, "aggregate-minimal-sample")

        val generatedEntityFile = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        )
        val generatedSchemaFile = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPost.kt")
        )
        val generatedRepositoryFile = projectDir.resolve(
            generatedSource("demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/repositories/VideoPostRepository.kt")
        )
        val behaviorFile = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostBehavior.kt"
        )

        val generateSourcesResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerateSources")
            .build()

        assertTrue(generateSourcesResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedEntityFile.toFile().exists())
        assertTrue(generatedSchemaFile.toFile().exists())
        assertTrue(generatedRepositoryFile.toFile().exists())
        assertFalse(behaviorFile.toFile().exists())

        generatedEntityFile.writeText("sentinel")

        val secondGenerateSourcesResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerateSources")
            .build()

        assertTrue(secondGenerateSourcesResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedEntityFile.readText().contains("class VideoPost("))
        assertFalse(generatedEntityFile.readText().contains("sentinel"))
        assertFalse(behaviorFile.toFile().exists())

        val generateResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .build()

        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(behaviorFile.toFile().exists())
        behaviorFile.writeText("sentinel behavior")

        val secondGenerateResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .build()

        assertTrue(secondGenerateResult.output.contains("BUILD SUCCESSFUL"))
        assertEquals("sentinel behavior", behaviorFile.readText())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerateSources filters checked in aggregate artifacts before rendering`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-generated-sources-render-filter")
        copyFixture(projectDir, "aggregate-minimal-sample")
        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText() +
                """

                cap4k {
                    templates {
                        overrideDirs.from("codegen/templates")
                    }
                }
                """.trimIndent()
        )
        val brokenBehaviorTemplate = projectDir.resolve("codegen/templates/aggregate/behavior.kt.peb")
        Files.createDirectories(brokenBehaviorTemplate.parent)
        brokenBehaviorTemplate.writeText("{{ use() }}")

        val generatedEntityFile = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        )
        val behaviorFile = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostBehavior.kt"
        )

        val result = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerateSources")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedEntityFile.toFile().exists())
        assertFalse(behaviorFile.toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerateSources does not depend on design ksp metadata`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-generated-sources-no-ksp")
        copyFixture(projectDir, "aggregate-minimal-sample")
        val designFile = projectDir.resolve("design/design.json")
        Files.createDirectories(designFile.parent)
        designFile.writeText(
            """
            [
              {
                "tag": "query",
                "package": "video_post.read",
                "name": "FindVideoPost",
                "requestFields": [
                  { "name": "id", "type": "Long" }
                ],
                "responseFields": [
                  { "name": "title", "type": "String" }
                ]
              }
            ]
            """.trimIndent()
        )
        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText() +
                """

                val generatedKspMetadataDir = project(":demo-domain").layout.buildDirectory
                    .dir("generated/ksp/main/resources/metadata")
                    .get()
                    .asFile
                    .absolutePath
                    .replace("\\", "/")

                project(":demo-domain") {
                    tasks.register("kspKotlin") {
                        doLast {
                            throw org.gradle.api.GradleException("cap4kGenerateSources must not run kspKotlin")
                        }
                    }
                }

                cap4k {
                    sources {
                        designJson {
                            enabled.set(true)
                            files.from("design/design.json")
                        }
                        kspMetadata {
                            enabled.set(true)
                            inputDir.set(generatedKspMetadataDir)
                        }
                    }
                    generators {
                        designQuery {
                            enabled.set(true)
                        }
                    }
                }
                """.trimIndent()
        )

        val result = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerateSources")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertFalse(result.output.contains("cap4kGenerateSources must not run kspKotlin"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerateSources does not become up to date for live db input`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-generated-sources-live-db")
        copyFixture(projectDir, "aggregate-minimal-sample")
        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText() +
                """

                cap4k {
                    sources {
                        db {
                            url.set("jdbc:mysql://localhost:3306/demo")
                            username.set("cap4k")
                            password.set("secret")
                        }
                    }
                }

                tasks.named("cap4kGenerateSources") {
                    setActions(emptyList())
                    doLast {
                        outputs.files.files.forEachIndexed { index, outputDir ->
                            outputDir.mkdirs()
                            outputDir.resolve("live-db-up-to-date-${'$'}index.marker").writeText("ran")
                        }
                    }
                }
                """.trimIndent()
        )

        val firstResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerateSources")
            .build()
        val secondResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerateSources")
            .build()

        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":cap4kGenerateSources")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, secondResult.task(":cap4kGenerateSources")?.outcome)
        assertFalse(secondResult.output.contains(":cap4kGenerateSources UP-TO-DATE"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate emits representative aggregate relation artifacts`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-relation")
        copyFixture(projectDir, "aggregate-relation-sample")

        val result = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .build()

        val rootEntityFile = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        )
        val childEntityFile = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostItem.kt")
        )
        val rootEntityContent = rootEntityFile.readText()
        val childEntityContent = childEntityFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(rootEntityFile.toFile().exists())
        assertTrue(childEntityFile.toFile().exists())
        assertFalse(
            projectDir.resolve("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
                .toFile()
                .exists()
        )
        assertTrue(rootEntityContent.contains("title: String"))
        assertTrue(rootEntityContent.contains("class VideoPost("))
        assertFalse(rootEntityContent.contains("data class VideoPost("))
        assertTrue(rootEntityContent.contains("import jakarta.persistence.CascadeType"))
        assertTrue(
            rootEntityContent.contains(
                "@OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE], orphanRemoval = true)"
            )
        )
        assertFalse(rootEntityContent.contains("CascadeType.ALL"))
        assertTrue(rootEntityContent.contains("@JoinColumn(name = \"video_post_id\", nullable = false)"))
        assertTrue(rootEntityContent.contains("val items: MutableList<VideoPostItem> = mutableListOf()"))
        assertTrue(rootEntityContent.contains("@ManyToOne(fetch = FetchType.LAZY)"))
        assertTrue(rootEntityContent.contains("@JoinColumn(name = \"author_id\", nullable = false)"))
        assertTrue(rootEntityContent.contains("lateinit var author: UserProfile"))
        assertFalse(rootEntityContent.contains("@Column(name = \"author_id\")"))
        assertFalse(rootEntityContent.contains("val author_id:"))
        assertTrue(rootEntityContent.contains("@OneToOne(fetch = FetchType.EAGER)"))
        assertTrue(rootEntityContent.contains("@JoinColumn(name = \"cover_profile_id\", nullable = true)"))
        assertTrue(rootEntityContent.contains("var coverProfile: UserProfile? = null"))
        assertFalse(rootEntityContent.contains("mappedBy ="))
        assertFalse(rootEntityContent.contains("ManyToMany"))
        assertTrue(childEntityContent.contains("@ManyToOne(fetch = FetchType.LAZY)"))
        assertFalse(
            childEntityContent.contains(
                "@JoinColumn(name = \"video_post_id\", nullable = false, insertable = false, updatable = false)"
            )
        )
        assertTrue(childEntityContent.contains("lateinit var videoPost: VideoPost"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate derives inverse read only many to one relation when parent anchor stays scalar`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-inverse-relation")
        copyFixture(projectDir, "aggregate-relation-sample")
        val schemaFile = projectDir.resolve("schema.sql")
        schemaFile.writeText(
            schemaFile.readText().replace(
                "video_post_id bigint not null comment '@Reference=video_post;@Lazy=true;',",
                "video_post_id bigint not null,",
            )
        )

        val result = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .build()

        val rootEntityFile = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        )
        val childEntityFile = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostItem.kt")
        )
        val rootEntityContent = rootEntityFile.readText()
        val childEntityContent = childEntityFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(rootEntityFile.toFile().exists())
        assertTrue(childEntityFile.toFile().exists())
        assertTrue(
            rootEntityContent.contains(
                "@OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE], orphanRemoval = true)"
            )
        )
        assertFalse(rootEntityContent.contains("CascadeType.ALL"))
        assertTrue(rootEntityContent.contains("@JoinColumn(name = \"video_post_id\", nullable = false)"))
        assertTrue(rootEntityContent.contains("val items: MutableList<VideoPostItem> = mutableListOf()"))
        assertTrue(childEntityContent.contains("@Column(name = \"video_post_id\")"))
        assertTrue(childEntityContent.contains("var videoPostId: Long = videoPostId"))
        assertTrue(childEntityContent.contains("@ManyToOne(fetch = FetchType.LAZY)"))
        assertTrue(
            childEntityContent.contains(
                "@JoinColumn(name = \"video_post_id\", nullable = false, insertable = false, updatable = false)"
            )
        )
        assertTrue(childEntityContent.contains("lateinit var videoPost: VideoPost"))
        assertFalse(childEntityContent.contains("mappedBy ="))
        assertFalse(childEntityContent.contains("JoinTable"))
        assertFalse(childEntityContent.contains("ManyToMany"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `aggregate persistence field behavior generation renders explicit field controls`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-persistence-generate")
        copyFixture(projectDir, "aggregate-persistence-sample")
        val domainBuildFile = projectDir.resolve("demo-domain/build.gradle.kts").readText().trim()
        val applicationBuildFile = projectDir.resolve("demo-application/build.gradle.kts").readText().trim()
        val adapterBuildFile = projectDir.resolve("demo-adapter/build.gradle.kts").readText().trim()

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        val generatedEntity = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        ).readText()

        assertTrue(domainBuildFile == "// Functional fixture module.")
        assertTrue(applicationBuildFile == "// Functional fixture module.")
        assertTrue(adapterBuildFile == "// Functional fixture module.")
        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedEntity.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
        assertTrue(generatedEntity.contains("@Version"))
        assertTrue(generatedEntity.contains("@Column(name = \"created_by\", insertable = false, updatable = true)"))
        assertTrue(generatedEntity.contains("@Column(name = \"updated_by\", insertable = true, updatable = false)"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `aggregate provider specific persistence generation renders bounded controls`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-provider-persistence-generate")
        copyFixture(projectDir, "aggregate-provider-persistence-sample")
        val domainBuildFile = projectDir.resolve("demo-domain/build.gradle.kts").readText().trim()
        val applicationBuildFile = projectDir.resolve("demo-application/build.gradle.kts").readText().trim()
        val adapterBuildFile = projectDir.resolve("demo-adapter/build.gradle.kts").readText().trim()

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        val generatedVideoPost = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        ).readText()
        val generatedAuditLog = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/audit_log/AuditLog.kt")
        ).readText()

        assertTrue(domainBuildFile == "// Functional fixture module.")
        assertTrue(applicationBuildFile == "// Functional fixture module.")
        assertTrue(adapterBuildFile == "// Functional fixture module.")
        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedVideoPost.contains("@DynamicInsert"))
        assertTrue(generatedVideoPost.contains("@DynamicUpdate"))
        assertTrue(
            generatedVideoPost.contains(
                "@SQLDelete(sql = \"update `video_post` set `deleted` = 1 where `id` = ? and `version` = ?\")"
            )
        )
        assertTrue(generatedVideoPost.contains("@Where(clause = \"`deleted` = 0\")"))
        assertFalse(generatedVideoPost.contains("@GenericGenerator"))
        assertTrue(
            generatedAuditLog.contains(
                "@SQLDelete(sql = \"update `audit_log` set `deleted` = 1 where `id` = ?\")"
            )
        )
        assertTrue(generatedAuditLog.contains("@Where(clause = \"`deleted` = 0\")"))
        assertFalse(generatedAuditLog.contains("@DynamicInsert"))
        assertFalse(generatedAuditLog.contains("@DynamicUpdate"))
        assertFalse(generatedAuditLog.contains("@GenericGenerator"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `aggregate provider persistence generation supports application-side and identity id policies`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-provider-persistence-mixed-id-generate")
        copyFixture(projectDir, "aggregate-provider-persistence-sample")
        val schemaFile = projectDir.resolve("schema.sql")
        schemaFile.writeText(
            schemaFile.readText().replaceFirst(
                "id bigint primary key comment '@GeneratedValue=IDENTITY;',",
                "id bigint primary key,",
            )
        )
        val buildFile = projectDir.resolve("build.gradle.kts")
        val patchedBuildFile = buildFile.readText().replace(
            Regex("""aggregate\s*\{\s*enabled\.set\(true\)\s*}"""),
            """
            |aggregate {
            |            enabled.set(true)
            |            specialFields {
            |                idDefaultStrategy.set("snowflake-long")
            |            }
            |        }
            """.trimMargin(),
        )
        buildFile.writeText(patchedBuildFile)
        assertTrue(patchedBuildFile.contains("""idDefaultStrategy.set("snowflake-long")"""))

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()
        val generatedVideoPost = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        ).readText()
        val generatedAuditLog = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/audit_log/AuditLog.kt")
        ).readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedVideoPost.contains("@field:ApplicationSideId(strategy = \"snowflake-long\")"))
        assertTrue(generatedVideoPost.contains("id: Long = 0L"))
        assertFalse(generatedVideoPost.contains("@GeneratedValue(generator ="))
        assertFalse(generatedVideoPost.contains("@GenericGenerator"))
        assertFalse(generatedVideoPost.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
        assertTrue(generatedAuditLog.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
        assertFalse(generatedAuditLog.contains("GenericGenerator"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `aggregate provider persistence generation supports native uuid application-side ids`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-provider-persistence-uuid-id-generate")
        copyFixture(projectDir, "aggregate-provider-persistence-sample")
        val schemaFile = projectDir.resolve("schema.sql")
        schemaFile.writeText(
            schemaFile.readText().replaceFirst(
                "id bigint primary key comment '@GeneratedValue=IDENTITY;',",
                "id uuid primary key,",
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()
        val generatedVideoPost = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        ).readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedVideoPost.contains("import java.util.UUID"))
        assertTrue(generatedVideoPost.contains("@field:ApplicationSideId(strategy = \"uuid7\")"))
        assertTrue(generatedVideoPost.contains("id: UUID = UUID(0L, 0L)"))
        assertFalse(generatedVideoPost.contains("@GeneratedValue(generator ="))
        assertFalse(generatedVideoPost.contains("@GenericGenerator"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `aggregate provider persistence generation fails fast when uuid7 is applied to Long id`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-provider-persistence-invalid-uuid7-generate")
        copyFixture(projectDir, "aggregate-provider-persistence-sample")
        val schemaFile = projectDir.resolve("schema.sql")
        schemaFile.writeText(
            """
            create table video (
                id bigint primary key,
                title varchar(128) not null
            );
            comment on table video is '@AggregateRoot=true;';

            create table audit_log (
                id bigint primary key comment '@GeneratedValue=IDENTITY;',
                deleted int not null comment '@Deleted;',
                content varchar(128) not null
            );
            comment on table audit_log is '@AggregateRoot=true;';
            """.trimIndent()
        )
        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace(
                "includeTables.set(listOf(\"video_post\", \"audit_log\"))",
                "includeTables.set(listOf(\"video\", \"audit_log\"))",
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .buildAndFail()

        assertTrue(
            result.output.contains(
                "ID strategy uuid7 cannot be applied to aggregate video.Video id field id: generated ID type is Long"
            )
        )
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan and cap4kGenerate preserve aggregate composite unique constraint order end to end`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-composite-unique")
        copyFixture(projectDir, "aggregate-sample")

        projectDir.resolve("schema.sql").writeText(
            """
            create table if not exists video_post (
                id bigint primary key,
                slug varchar(128) not null unique,
                tenant_id bigint not null,
                title varchar(255) not null,
                published boolean default false,
                constraint uq_video_post_tenant_slug unique (tenant_id, slug)
            );
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan", "cap4kGenerate")
            .build()

        val planContent = projectDir.resolve("build/cap4k/plan.json").readText()
        val compositeQueryFile = projectDir.resolve(
            generatedSource(
                "demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/UniqueVideoPostTenantIdSlugQry.kt"
            )
        )
        val compositeQueryHandlerFile = projectDir.resolve(
            generatedSource(
                "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video_post/unique/UniqueVideoPostTenantIdSlugQryHandler.kt"
            )
        )
        val compositeValidatorFile = projectDir.resolve(
            generatedSource(
                "demo-application/src/main/kotlin/com/acme/demo/application/validators/video_post/unique/UniqueVideoPostTenantIdSlug.kt"
            )
        )

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planContent.contains("UniqueVideoPostTenantIdSlugQry"))
        assertTrue(planContent.contains("UniqueVideoPostTenantIdSlugQryHandler"))
        assertTrue(planContent.contains("UniqueVideoPostTenantIdSlug"))
        assertTrue(compositeQueryFile.toFile().exists())
        assertTrue(compositeQueryHandlerFile.toFile().exists())
        assertTrue(compositeValidatorFile.toFile().exists())
        val compositeQueryContent = compositeQueryFile.readText()
        val tenantParamIndex = compositeQueryContent.indexOf("val tenantId: Long")
        val slugParamIndex = compositeQueryContent.indexOf("val slug: String")
        assertTrue(tenantParamIndex >= 0)
        assertTrue(slugParamIndex >= 0)
        assertTrue(tenantParamIndex < slugParamIndex)
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan and cap4kGenerate produce shared and local aggregate enum artifacts`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-enum")
        copyFixture(projectDir, "aggregate-enum-sample")

        val planResult = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .build()
        val generateResult = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()
        val planFile = projectDir.resolve("build/cap4k/plan.json")
        val generatedEntity = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        ).readText()

        assertTrue(planResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planFile.toFile().exists())
        assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/enum.kt.peb\""))
        assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/enum_translation.kt.peb\""))
        assertTrue(
            projectDir.resolve(
                generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/shared/enums/Status.kt")
            ).toFile().exists()
        )
        assertTrue(
            projectDir.resolve(
                generatedSource(
                    "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/enums/VideoPostVisibility.kt"
                )
            ).toFile().exists()
        )
        assertTrue(
            projectDir.resolve(
                generatedSource("demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/translation/shared/StatusTranslation.kt")
            ).toFile().exists()
        )
        assertTrue(
            projectDir.resolve(
                generatedSource(
                    "demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/translation/video_post/VideoPostVisibilityTranslation.kt"
                )
            ).toFile().exists()
        )
        assertTrue(generatedEntity.contains("@Entity"))
        assertTrue(generatedEntity.contains("@Table(name = \"video_post\")"))
        assertTrue(generatedEntity.contains("@Id"))
        assertTrue(generatedEntity.contains("@Column(name = \"id\""))
        assertTrue(generatedEntity.contains("@Column(name = \"status\")"))
        assertTrue(
            generatedEntity.contains(
                "@Convert(converter = com.acme.demo.domain.shared.enums.Status.Converter::class)"
            )
        )
        assertTrue(
            projectDir.resolve(
                generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/shared/enums/Status.kt")
            ).readText().contains("@jakarta.persistence.Converter(autoApply = false)")
        )
        assertTrue(
            projectDir.resolve(
                generatedSource(
                    "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/enums/VideoPostVisibility.kt"
                )
            ).readText().contains("@jakarta.persistence.Converter(autoApply = false)")
        )
        assertFalse(generatedEntity.contains("@GeneratedValue"))
        assertFalse(generatedEntity.contains("@Version"))
        assertFalse(generatedEntity.contains("@DynamicInsert"))
        assertTrue(generatedEntity.contains("var status: com.acme.demo.domain.shared.enums.Status = status"))
        assertFalse(generatedEntity.contains("class Status("))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan skips unsupported tables when aggregate policy is skip`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-skip")
        copyFixture(projectDir, "aggregate-policy-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan", "cap4kGenerate")
            .build()

        val planJson = projectDir.resolve("build/cap4k/plan.json").readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planJson.contains("\"unsupportedTables\""))
        assertTrue(planJson.contains("\"tableName\": \"audit_log\""))
        assertTrue(planJson.contains("\"reason\": \"composite_primary_key\""))
        assertTrue(
            projectDir.resolve(
                generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
            ).toFile().exists()
        )
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan includes aggregate special-field defaults and resolved policies`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-special-field-plan")
        copyFixture(projectDir, "aggregate-provider-persistence-sample")

        val schemaFile = projectDir.resolve("schema.sql")
        schemaFile.writeText(
            schemaFile.readText().replaceFirst(
                "id bigint primary key comment '@GeneratedValue=IDENTITY;',",
                "id bigint primary key,",
            )
        )

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                Regex("""aggregate\s*\{\s*enabled\.set\(true\)\s*}"""),
                """
                |aggregate {
                |            enabled.set(true)
                |            specialFields {
                |                idDefaultStrategy.set(" snowflake-long ")
                |            }
                |        }
                """.trimMargin(),
            ),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .build()

        val planJson = projectDir.resolve("build/cap4k/plan.json").readText()
        val planObject = JsonParser.parseString(planJson).asJsonObject
        val defaults = planObject.getAsJsonObject("aggregateSpecialFieldDefaults")
        val resolvedPolicies = planObject.getAsJsonArray("aggregateSpecialFieldResolvedPolicies")
            .map { it.asJsonObject }
            .associateBy { it.get("tableName").asString }
        val videoPostPolicy = resolvedPolicies.getValue("video_post")
        val auditLogPolicy = resolvedPolicies.getValue("audit_log")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertFalse(planObject.has("aggregateIdPolicy"))
        assertEquals("snowflake-long", defaults.get("idDefaultStrategy").asString)
        assertEquals("", defaults.get("deletedDefaultColumn").asString)
        assertEquals("", defaults.get("versionDefaultColumn").asString)
        assertEquals(2, resolvedPolicies.size)
        assertEquals("DSL_DEFAULT", videoPostPolicy.getAsJsonObject("id").get("source").asString)
        assertEquals("snowflake-long", videoPostPolicy.getAsJsonObject("id").get("strategy").asString)
        assertEquals("DB_EXPLICIT", videoPostPolicy.getAsJsonObject("deleted").get("source").asString)
        assertEquals("DB_EXPLICIT", videoPostPolicy.getAsJsonObject("version").get("source").asString)
        assertEquals("DB_EXPLICIT", auditLogPolicy.getAsJsonObject("id").get("source").asString)
        assertEquals("NONE", auditLogPolicy.getAsJsonObject("version").get("source").asString)
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan writes diagnostics envelope before failing on unsupported aggregate table`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-fail-diagnostics")
        copyFixture(projectDir, "aggregate-sample")
        projectDir.resolve("build.gradle.kts").writeText(
            projectDir.resolve("build.gradle.kts").readText().replace(
                "            includeTables.set(listOf(\"video_post\"))",
                "            includeTables.set(listOf(\"video_post\", \"audit_log\"))",
            )
        )
        projectDir.resolve("schema.sql").writeText(
            projectDir.resolve("schema.sql").readText() +
                """

                create table audit_log (
                  tenant_id bigint not null,
                  event_id varchar(64) not null,
                  constraint pk_audit_log primary key (tenant_id, event_id)
                );
                """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .buildAndFail()

        val planFile = projectDir.resolve("build/cap4k/plan.json").toFile()

        assertTrue(result.output.contains("db table audit_log is unsupported for aggregate generation: composite_primary_key"))
        assertTrue(planFile.exists())
        assertTrue(planFile.readText().contains("\"items\": []"))
        assertTrue(planFile.readText().contains("\"diagnostics\""))
        assertTrue(planFile.readText().contains("\"unsupportedTables\""))
        assertTrue(planFile.readText().contains("\"tableName\": \"audit_log\""))
        assertTrue(planFile.readText().contains("\"reason\": \"composite_primary_key\""))
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
                "project.domainModulePath, project.applicationModulePath, and project.adapterModulePath are required when aggregate is enabled."
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
    fun `cap4kAnalysisPlan and cap4kAnalysisGenerate produce flow artifacts from ir analysis fixture`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-analysis-flow")
        copyFixture(projectDir, "flow-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kAnalysisPlan", "cap4kAnalysisGenerate")
            .build()

        val analysisPlanFile = projectDir.resolve("build/cap4k/analysis-plan.json")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(analysisPlanFile.toFile().exists())
        assertTrue(analysisPlanFile.readText().contains("\"templateId\": \"flow/index.json.peb\""))
        assertTrue(projectDir.resolve("flows/OrderController_submit.json").toFile().exists())
        assertTrue(projectDir.resolve("flows/OrderController_submit.mmd").toFile().exists())
        assertTrue(projectDir.resolve("flows/index.json").toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kAnalysisGenerate flow artifacts support custom layout output root`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-analysis-flow-layout")
        copyFixture(projectDir, "flow-sample")
        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace(
                """outputRoot.set("flows")""",
                """outputRoot.set("build/cap4k/flows")""",
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kAnalysisPlan", "cap4kAnalysisGenerate")
            .build()

        val analysisPlanFile = projectDir.resolve("build/cap4k/analysis-plan.json")
        val analysisPlanContent = analysisPlanFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(analysisPlanContent.contains("\"outputPath\": \"build/cap4k/flows/OrderController_submit.json\""))
        assertTrue(projectDir.resolve("build/cap4k/flows/OrderController_submit.json").toFile().exists())
        assertTrue(projectDir.resolve("build/cap4k/flows/OrderController_submit.mmd").toFile().exists())
        assertTrue(projectDir.resolve("build/cap4k/flows/index.json").toFile().exists())
        assertFalse(projectDir.resolve("flows/index.json").toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan and cap4kGenerate ignore flow and drawing board generators`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-main-ignores-analysis")
        copyFixture(projectDir, "flow-sample")
        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace("\r\n", "\n").replace(
                """
                layout {
                    flow {
                        outputRoot.set("flows")
                    }
                }
                """.trimIndent(),
                """
                layout {
                    flow {
                        outputRoot.set("flows")
                    }
                    drawingBoard {
                        outputRoot.set("design")
                    }
                }
                """.trimIndent()
            ).replace(
                """
                generators {
                    flow {
                        enabled.set(true)
                    }
                }
                """.trimIndent(),
                """
                generators {
                    flow {
                        enabled.set(true)
                    }
                    drawingBoard {
                        enabled.set(true)
                    }
                }
                """.trimIndent()
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan", "cap4kGenerate")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertFalse(projectDir.resolve("flows/index.json").toFile().exists())
        assertFalse(projectDir.resolve("design/drawing_board_client.json").toFile().exists())
        assertFalse(projectDir.resolve("design/drawing_board_command.json").toFile().exists())
        assertFalse(projectDir.resolve("build/cap4k/analysis-plan.json").toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kAnalysisPlan and cap4kAnalysisGenerate produce drawing board artifacts from ir analysis fixture`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-analysis-drawing-board")
        copyFixture(projectDir, "drawing-board-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kAnalysisPlan", "cap4kAnalysisGenerate")
            .build()

        val analysisPlanFile = projectDir.resolve("build/cap4k/analysis-plan.json")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(analysisPlanFile.toFile().exists())
        assertTrue(analysisPlanFile.readText().contains("\"templateId\": \"drawing-board/document.json.peb\""))
        assertTrue(projectDir.resolve("design/drawing_board_client.json").toFile().exists())
        assertTrue(projectDir.resolve("design/drawing_board_command.json").toFile().exists())
        val queryContent = projectDir.resolve("design/drawing_board_query.json").readText()
        val payloadContent = projectDir.resolve("design/drawing_board_api_payload.json").readText()
        assertTrue(queryContent.contains("\"traits\": [\"page\"]"))
        assertTrue(payloadContent.contains("\"traits\": [\"page\"]"))
        val domainEventFile = projectDir.resolve("design/drawing_board_domain_event.json")
        assertTrue(domainEventFile.toFile().exists())
        val domainEventContent = domainEventFile.readText()
        assertTrue(domainEventContent.contains("\"tag\": \"domain_event\""))
        assertTrue(domainEventContent.contains("\"name\": \"reason\""))
        assertFalse(domainEventContent.contains("\"name\": \"entity\""))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kAnalysisGenerate validator drawing board can feed cap4kGenerate`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-analysis-validator-roundtrip")
        copyCompileFixture(projectDir, "analysis-validator-roundtrip-sample")

        val analysisResult = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kAnalysisGenerate")
            .build()

        val drawingBoardValidator = projectDir.resolve("design/drawing_board_validator.json")
        val drawingBoardContent = drawingBoardValidator.readText()

        val generateResult = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()
        val compileResult = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(":demo-application:compileKotlin")
            .build()

        val generatedValidator = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/validators/danmuku/DanmukuDeletePermission.kt"
        )
        val generatedContent = generatedValidator.readText()

        assertTrue(analysisResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(drawingBoardValidator.toFile().exists())
        assertTrue(drawingBoardContent.contains("\"tag\": \"validator\""))
        assertTrue(drawingBoardContent.contains("\"targets\": [\"CLASS\"]"))
        assertTrue(drawingBoardContent.contains("\"valueType\": \"Any\""))
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedContent.contains("annotation class DanmukuDeletePermission"))
        assertTrue(generatedContent.contains("ConstraintValidator<DanmukuDeletePermission, Any>"))
        assertTrue(generatedContent.contains("val danmukuIdField: String = \"danmukuId\""))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kAnalysisPlan depends on compileKotlin when flow input is produced during compilation`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-flow-compile")
        copyFixture(projectDir, "flow-compile-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kAnalysisPlan")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(projectDir.resolve("build/cap4k-code-analysis/nodes.json").toFile().exists())
        assertTrue(projectDir.resolve("build/cap4k/analysis-plan.json").toFile().exists())
        assertTrue(
            projectDir.resolve("build/cap4k/analysis-plan.json")
                .readText()
                .contains("\"templateId\": \"flow/index.json.peb\"")
        )
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `wrapper task depending on cap4kAnalysisGenerate still infers compileKotlin dependency`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-flow-wrapper")
        copyFixture(projectDir, "flow-compile-sample")
        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace("\r\n", "\n") +
                """

                tasks.register("cap4kAnalysisGenerateWrapper") {
                    dependsOn("cap4kAnalysisGenerate")
                }
                """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kAnalysisGenerateWrapper")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(projectDir.resolve("build/cap4k-code-analysis/nodes.json").toFile().exists())
        assertTrue(projectDir.resolve("flows/OrderController_submit.json").toFile().exists())
        assertTrue(projectDir.resolve("flows/OrderController_submit.mmd").toFile().exists())
        assertTrue(projectDir.resolve("flows/index.json").toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kAnalysisPlan fails clearly when ir analysis fixture misses rels json`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-flow-invalid")
        copyFixture(projectDir, "flow-sample")
        projectDir.resolve("analysis/app/build/cap4k-code-analysis/rels.json").toFile().delete()

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kAnalysisPlan")
            .buildAndFail()

        assertTrue(result.output.contains("ir-analysis inputDir is missing nodes.json or rels.json"))
        assertFalse(projectDir.resolve("build/cap4k/analysis-plan.json").toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan api payload flow emits design api payload template`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-api-payload-plan")
        copyFixture(projectDir, "design-api-payload-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .build()

        val planFile = projectDir.resolve("build/cap4k/plan.json")
        val planContent = planFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planFile.toFile().exists())
        assertTrue(planContent.contains("\"templateId\": \"design/api_payload.kt.peb\""))
        assertTrue(planContent.contains("adapter/portal/api/payload/account/BatchSaveAccountList.kt"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate api payload flow writes payload under adapter portal api payload`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-api-payload-generate")
        copyFixture(projectDir, "design-api-payload-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        val payloadFile = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/portal/api/payload/account/BatchSaveAccountList.kt"
        )
        val content = payloadFile.readText()
        val responseIndex = content.indexOf("    data class Response(")
        val requestSection = content.substring(
            startIndex = content.indexOf("    data class Request("),
            endIndex = responseIndex
        )
        val responseSection = content.substring(responseIndex)

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(payloadFile.toFile().exists())
        assertTrue(content.contains("package com.acme.demo.adapter.portal.api.payload.account"))
        assertTrue(content.contains("object BatchSaveAccountList"))
        assertTrue(content.contains("val address: Address?"))
        assertFalse(content.contains("val address: Address??"))
        assertTrue(requestSection.contains("data class Address("))
        assertTrue(requestSection.contains("val city: String"))
        assertTrue(requestSection.contains("val zipCode: String = \"000000\""))
        assertFalse(requestSection.contains("data class Result("))
        assertTrue(content.contains("val result: Result?"))
        assertFalse(content.contains("val result: Result??"))
        assertTrue(responseSection.contains("data class Result("))
        assertTrue(responseSection.contains("val success: Boolean = true"))
        assertFalse(responseSection.contains("data class Address("))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate api payload flow supports override template replacement`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-api-payload-override")
        copyFixture(projectDir, "design-api-payload-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace("\r\n", "\n") +
                """

                cap4k {
                    templates {
                        overrideDirs.from("codegen/templates")
                    }
                }
                """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        val payloadFile = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/portal/api/payload/account/BatchSaveAccountList.kt"
        )
        val content = payloadFile.readText()
        val responseIndex = content.indexOf("    data class Response(")
        val requestSection = content.substring(
            startIndex = content.indexOf("    data class Request("),
            endIndex = responseIndex
        )
        val responseSection = content.substring(responseIndex)

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(payloadFile.toFile().exists())
        assertTrue(content.contains("// override: representative api payload migration template"))
        assertTrue(content.contains("object BatchSaveAccountList"))
        assertTrue(requestSection.contains("data class Address("))
        assertTrue(requestSection.contains("val zipCode: String = \"000000\""))
        assertFalse(requestSection.contains("data class Result("))
        assertTrue(responseSection.contains("data class Result("))
        assertTrue(responseSection.contains("val success: Boolean = true"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan api payload flow fails when design api payload misses adapter module path`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-api-payload-no-adapter")
        copyFixture(projectDir, "design-api-payload-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                "        adapterModulePath.set(\"demo-adapter\")\n",
                "",
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(result.output.contains("project.adapterModulePath is required when designApiPayload is enabled."))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan api payload flow fails when design api payload has no enabled design json source`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-api-payload-no-designjson")
        copyFixture(projectDir, "design-api-payload-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                """
                |    sources {
                |        designJson {
                |            enabled.set(true)
                |            files.from("design/design.json")
                |        }
                |    }
                """.trimMargin(),
                """
                |    sources {
                |        designJson {
                |            enabled.set(false)
                |            files.from("design/design.json")
                |        }
                |    }
                """.trimMargin(),
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(result.output.contains("designApiPayload generator requires enabled designJson source."))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan validator flow emits design validator template`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-validator-plan")
        copyFixture(projectDir, "design-validator-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .build()

        val planFile = projectDir.resolve("build/cap4k/plan.json")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planFile.toFile().exists())
        assertTrue(planFile.readText().contains("\"templateId\": \"design/validator.kt.peb\""))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate validator flow writes validator under application validators`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-validator-generate")
        copyFixture(projectDir, "design-validator-sample")
        val designFile = projectDir.resolve("design/design.json")
        designFile.writeText(
            designFile.readText().replace(
                "\"desc\": \"issue token validator\"",
                "\"desc\": \"issue */ validator\"",
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        val validatorFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/validators/authorize/IssueToken.kt"
        )
        val content = validatorFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(validatorFile.toFile().exists())
        assertTrue(content.contains("annotation class IssueToken"))
        assertTrue(content.contains("ConstraintValidator<IssueToken, Long>"))
        assertTrue(content.contains("* issue * / validator"))
        assertFalse(content.contains("* issue */ validator"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate validator flow supports override validator template replacement`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-validator-override")
        copyFixture(projectDir, "design-validator-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace("\r\n", "\n") +
                """

                cap4k {
                    templates {
                        overrideDirs.from("codegen/templates")
                    }
                }
                """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        val validatorFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/validators/authorize/IssueToken.kt"
        )
        val content = validatorFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(validatorFile.toFile().exists())
        assertTrue(content.contains("// override: representative validator migration template"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan validator flow fails when design validator misses application module path`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-validator-no-application")
        copyFixture(projectDir, "design-validator-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                "        applicationModulePath.set(\"demo-application\")\n",
                "",
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(result.output.contains("project.applicationModulePath is required when designValidator is enabled."))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan validator flow fails when design validator has no enabled design json source`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-validator-no-designjson")
        copyFixture(projectDir, "design-validator-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                """
                |    sources {
                |        designJson {
                |            enabled.set(true)
                |            files.from("design/design.json")
                |        }
                |    }
                """.trimMargin(),
                """
                |    sources {
                |        designJson {
                |            enabled.set(false)
                |            files.from("design/design.json")
                |        }
                |    }
                """.trimMargin(),
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(result.output.contains("designValidator generator requires enabled designJson source."))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan domain event flow emits domain event and domain event handler templates`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-domain-event-plan")
        copyFixture(projectDir, "design-domain-event-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .build()

        val planFile = projectDir.resolve("build/cap4k/plan.json")
        val planContent = planFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planFile.toFile().exists())
        assertTrue(planContent.contains("\"templateId\": \"design/domain_event.kt.peb\""))
        assertTrue(planContent.contains("\"templateId\": \"design/domain_event_handler.kt.peb\""))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate domain event flow writes domain event and domain event subscriber artifacts`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-domain-event-generate")
        copyFixture(projectDir, "design-domain-event-sample")
        val designFile = projectDir.resolve("design/design.json")
        designFile.writeText(
            designFile.readText().replace(
                "\"desc\": \"order created event\"",
                "\"desc\": \"order */ \\\"created\\\" event\"",
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        val eventFile = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/events/OrderCreatedDomainEvent.kt"
        )
        val handlerFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/domain/order/OrderCreatedDomainEventSubscriber.kt"
        )
        val eventContent = eventFile.readText()
        val handlerContent = handlerFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(eventFile.toFile().exists())
        assertTrue(handlerFile.toFile().exists())
        assertTrue(eventContent.contains("@DomainEvent"))
        assertTrue(eventContent.contains("@Aggregate("))
        assertTrue(eventContent.contains("aggregate = \"Order\""))
        assertTrue(eventContent.contains("name = \"OrderCreatedDomainEvent\""))
        assertTrue(eventContent.contains("type = Aggregate.TYPE_DOMAIN_EVENT"))
        assertTrue(eventContent.contains("* order * / \"created\" event"))
        assertFalse(eventContent.contains("* order */ \"created\" event"))
        assertTrue(eventContent.contains("description = \"order */ \\\"created\\\" event\""))
        assertFalse(eventContent.contains("&quot;"))
        assertTrue(eventContent.contains("import com.acme.demo.domain.order.Order"))
        assertTrue(eventContent.contains("import java.util.UUID"))
        assertTrue(eventContent.contains("class OrderCreatedDomainEvent("))
        assertTrue(eventContent.contains("val entity: Order"))
        assertTrue(eventContent.contains("data class Snapshot("))
        assertTrue(eventContent.contains("val traceId: UUID"))
        assertTrue(handlerContent.contains("@Service"))
        assertTrue(handlerContent.contains("@EventListener(OrderCreatedDomainEvent::class)"))
        assertTrue(handlerContent.contains("* order * / \"created\" event"))
        assertFalse(handlerContent.contains("* order */ \"created\" event"))
        assertTrue(handlerContent.contains("class OrderCreatedDomainEventSubscriber"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate domain event flow supports custom Kotlin package root`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-domain-event-layout")
        copyFixture(projectDir, "design-domain-event-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace("\r\n", "\n").replace(
                "cap4k {\n",
                """
                cap4k {
                    layout {
                        designDomainEvent {
                            packageRoot.set("domain.model")
                            packageSuffix.set("events")
                        }
                    }
                """.trimIndent() + "\n",
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan", "cap4kGenerate")
            .build()

        val eventFile = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/model/order/events/OrderCreatedDomainEvent.kt"
        )
        val eventContent = eventFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(eventFile.toFile().exists())
        assertTrue(eventContent.contains("package com.acme.demo.domain.model.order.events"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate domain event flow supports override template replacement for event and handler`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-domain-event-override")
        copyFixture(projectDir, "design-domain-event-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace("\r\n", "\n") +
                """

                cap4k {
                    templates {
                        overrideDirs.from("codegen/templates")
                    }
                }
                """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate")
            .build()

        val eventFile = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/events/OrderCreatedDomainEvent.kt"
        )
        val handlerFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/domain/order/OrderCreatedDomainEventSubscriber.kt"
        )
        val eventContent = eventFile.readText()
        val handlerContent = handlerFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(eventFile.toFile().exists())
        assertTrue(handlerFile.toFile().exists())
        assertTrue(eventContent.contains("// override: representative domain event migration template"))
        assertTrue(handlerContent.contains("// override: representative domain event handler migration template"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan domain event flow fails when design domain event misses domain module path`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-domain-event-no-domain")
        copyFixture(projectDir, "design-domain-event-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                "        domainModulePath.set(\"demo-domain\")\n",
                "",
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(result.output.contains("project.domainModulePath is required when designDomainEvent is enabled."))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan domain event flow fails when design domain event handler misses application module path`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-domain-event-handler-no-application")
        copyFixture(projectDir, "design-domain-event-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                "        applicationModulePath.set(\"demo-application\")\n",
                "",
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(result.output.contains("project.applicationModulePath is required when designDomainEventHandler is enabled."))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan domain event flow fails when design domain event is disabled and handler is enabled`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-domain-event-disabled")
        copyFixture(projectDir, "design-domain-event-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                """
                |        designDomainEvent {
                |            enabled.set(true)
                |        }
                """.trimMargin(),
                """
                |        designDomainEvent {
                |            enabled.set(false)
                |        }
                """.trimMargin(),
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(result.output.contains("designDomainEventHandler generator requires enabled designDomainEvent generator."))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan domain event flow fails when design json source is disabled`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-domain-event-no-designjson")
        copyFixture(projectDir, "design-domain-event-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                """
                |        designJson {
                |            enabled.set(true)
                |            files.from("design/design.json")
                |        }
                """.trimMargin(),
                """
                |        designJson {
                |            enabled.set(false)
                |            files.from("design/design.json")
                |        }
                """.trimMargin(),
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(result.output.contains("designDomainEvent generator requires enabled designJson source."))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan domain event flow succeeds without ksp metadata when aggregate source data exists`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-domain-event-aggregate-fallback")
        copyFixture(projectDir, "design-domain-event-sample")
        val designFile = projectDir.resolve("design/design.json")
        designFile.writeText(designFile.readText().replace("\"Order\"", "\"VideoPost\""))
        projectDir.resolve("schema.sql").writeText(
            """
            create table if not exists video_post (
                id bigint primary key,
                title varchar(255) not null
            );
            """.trimIndent()
        )

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        val buildFileWithDbVars = buildFileContent.replace(
            "cap4k {",
            """
            val schemaScriptPath = layout.projectDirectory.file("schema.sql").asFile.absolutePath.replace("\\", "/")
            val dbFilePath = layout.buildDirectory.file("h2/demo").get().asFile.absolutePath.replace("\\", "/")

            cap4k {
            """.trimIndent()
        )
        buildFile.writeText(
            buildFileWithDbVars.replace(
                """
                |        kspMetadata {
                |            enabled.set(true)
                |            inputDir.set("design/metadata")
                |        }
                """.trimMargin(),
                """
                |        kspMetadata {
                |            enabled.set(false)
                |            inputDir.set("design/metadata")
                |        }
                |        db {
                |            enabled.set(true)
                |            url.set("jdbc:h2:file:${'$'}dbFilePath;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;INIT=RUNSCRIPT FROM '${'$'}schemaScriptPath'")
                |            username.set("sa")
                |            password.set("secret")
                |            schema.set("PUBLIC")
                |            includeTables.set(listOf("video_post"))
                |            excludeTables.set(emptyList())
                |        }
                """.trimMargin(),
            )
                .replace(
                    """
                    |        designDomainEventHandler {
                    |            enabled.set(true)
                    |        }
                    """.trimMargin(),
                    """
                    |        designDomainEventHandler {
                    |            enabled.set(true)
                    |        }
                    |        aggregate {
                    |            specialFields {
                    |                idDefaultStrategy.set("snowflake-long")
                    |            }
                    |        }
                    """.trimMargin(),
                )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(projectDir.resolve("build/cap4k/plan.json").toFile().exists())
        assertTrue(projectDir.resolve("build/cap4k/plan.json").readText().contains("\"templateId\": \"design/domain_event.kt.peb\""))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan domain event flow still fails clearly when neither aggregate data nor ksp metadata can resolve aggregate`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-domain-event-missing-aggregate-metadata")
        copyFixture(projectDir, "design-domain-event-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                """
                |        kspMetadata {
                |            enabled.set(true)
                |            inputDir.set("design/metadata")
                |        }
                """.trimMargin(),
                """
                |        kspMetadata {
                |            enabled.set(false)
                |            inputDir.set("design/metadata")
                |        }
                """.trimMargin(),
            )
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(result.output.contains("domain_event OrderCreated references missing aggregate metadata: Order"))
    }

    @Test
    fun `cap4kGenerate removes known only danmaku next generator bugs`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-known-bug-parity")
        copyCompileFixture(projectDir, "known-bug-parity-sample")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerate", "build")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))

        val repositoryFile = projectDir.generatedFile(
            generatedSource("demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/repositories/UserMessageRepository.kt")
        )
        val entityFile = projectDir.generatedFile(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/user_message/UserMessage.kt")
        )
        val schemaBaseFile = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/Schema.kt"
        )
        val schemaFile = projectDir.generatedFile(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/user_message/SUserMessage.kt")
        )
        val uniqueValidatorFile = projectDir.generatedFile(
            generatedSource(
                "demo-application/src/main/kotlin/com/acme/demo/application/validators/user_message/unique/UniqueUserMessageMessageKey.kt"
            )
        )
        val uniqueHandlerFile = projectDir.generatedFile(
            generatedSource(
                "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/user_message/unique/UniqueUserMessageMessageKeyQryHandler.kt"
            )
        )
        val queryFile = projectDir.generatedFile(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/message/read/FindUserMessageQry.kt"
        )
        val commandFile = projectDir.generatedFile(
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/message/create/CreateUserMessageCmd.kt"
        )
        val clientFile = projectDir.generatedFile(
            "demo-application/src/main/kotlin/com/acme/demo/application/distributed/clients/message/delivery/PublishUserMessageCli.kt"
        )
        val clientHandlerFile = projectDir.generatedFile(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/distributed/clients/message/delivery/PublishUserMessageCliHandler.kt"
        )
        val queryHandlerFile = projectDir.generatedFile(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/queries/message/read/FindUserMessageQryHandler.kt"
        )
        val payloadFile = projectDir.generatedFile(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/portal/api/payload/message/CreateUserMessagePayload.kt"
        )
        val domainEventFile = projectDir.generatedFile(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/user_message/events/UserMessageCreatedDomainEvent.kt"
        )
        val domainEventHandlerFile = projectDir.generatedFile(
            "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/domain/user_message/UserMessageCreatedDomainEventSubscriber.kt"
        )

        listOf(
            repositoryFile,
            entityFile,
            schemaFile,
            uniqueValidatorFile,
            uniqueHandlerFile,
            queryFile,
            commandFile,
            clientFile,
            clientHandlerFile,
            queryHandlerFile,
            payloadFile,
            domainEventFile,
            domainEventHandlerFile,
        ).forEach(::assertNoFormattingRegression)

        assertFalse(schemaBaseFile.toFile().exists(), "Schema runtime must be provided by the framework module.")

        val entityContent = entityFile.readText()
        assertTrue(entityContent.contains("var messageKey: String = messageKey"))
        assertTrue(entityContent.contains("@Column(name = \"message_key\")"))
        assertFalse(entityContent.contains("val message_key"))

        val repositoryContent = repositoryFile.readText()
        assertTrue(
            repositoryContent.contains(
                "interface UserMessageRepository : JpaRepository<UserMessage, Long>, JpaSpecificationExecutor<UserMessage>"
            )
        )
        assertTrue(repositoryContent.contains("class UserMessageJpaRepositoryAdapter("))

        val schemaContent = schemaFile.readText()
        assertTrue(schemaContent.contains("class SUserMessage("))
        assertTrue(schemaContent.contains("fun specify(builder: PredicateBuilder<SUserMessage>): Specification<UserMessage>"))
        assertTrue(schemaContent.contains("import com.only4.cap4k.ddd.domain.repo.schema.Field"))
        assertTrue(schemaContent.contains("import com.only4.cap4k.ddd.domain.repo.schema.PredicateBuilder"))
        assertTrue(schemaContent.contains("val messageKey: Field<String>"))
        assertFalse(schemaContent.contains("val message_key"))

        val uniqueValidatorContent = uniqueValidatorFile.readText()
        assertTrue(uniqueValidatorContent.contains("ConstraintValidator<UniqueUserMessageMessageKey, Any>"))
        assertTrue(uniqueValidatorContent.contains("Mediator.queries.send("))
        assertTrue(uniqueValidatorContent.contains("return !result.exists"))
        assertFalse(
            uniqueValidatorContent.contains(
                "ConstraintValidator<UniqueUserMessageMessageKey, UniqueUserMessageMessageKeyQry.Request>"
            )
        )

        val uniqueHandlerContent = uniqueHandlerFile.readText()
        assertTrue(uniqueHandlerContent.contains("private val repository: UserMessageRepository"))
        assertTrue(uniqueHandlerContent.contains("repository.exists("))
        assertTrue(uniqueHandlerContent.contains("SUserMessage.specify"))
        assertFalse(uniqueHandlerContent.contains("exists = false"))

        val queryContent = queryFile.readText()
        assertTrue(queryContent.contains(") : RequestParam<Response>"))
        assertTrue(queryContent.replace("\r\n", "\n").contains(") : RequestParam<Response>\n\n    data class Response("))

        val commandContent = commandFile.readText()
        assertTrue(commandContent.contains(") : RequestParam<Response>"))
        assertTrue(commandContent.contains("class Handler : Command<Request, Response>"))
        assertTrue(commandContent.contains("Mediator.uow.save()"))
        assertTrue(commandContent.replace("\r\n", "\n").contains(") : RequestParam<Response>\n\n    data class Response("))

        val clientContent = clientFile.readText()
        assertTrue(clientContent.contains(") : RequestParam<Response>"))
        assertTrue(clientContent.replace("\r\n", "\n").contains(") : RequestParam<Response>\n\n    data class Response("))

        val clientHandlerContent = clientHandlerFile.readText().replace("\r\n", "\n")
        assertTrue(
            clientHandlerContent.contains(
                "        return PublishUserMessageCli.Response(\n" +
                    "            published = TODO(\"set published\")\n" +
                    "        )"
            )
        )

        val queryHandlerContent = queryHandlerFile.readText().replace("\r\n", "\n")
        assertTrue(
            queryHandlerContent.contains(
                "        return FindUserMessageQry.Response(\n" +
                    "            messageKey = TODO(\"set messageKey\"),\n" +
                    "            content = TODO(\"set content\")\n" +
                    "        )"
            )
        )

        val payloadContent = payloadFile.readText()
        assertTrue(payloadContent.replace("\r\n", "\n").contains("    }\n\n    data class Response("))
        val requestIndex = payloadContent.indexOf("data class Request(")
        val responseIndex = payloadContent.indexOf("data class Response(")
        assertTrue(requestIndex >= 0, "Request class must be rendered.")
        assertTrue(responseIndex >= 0, "Response class must be rendered.")
        val requestSection = payloadContent.substring(requestIndex, responseIndex)
        val responseSection = payloadContent.substring(responseIndex)
        assertTrue(requestSection.contains("        data class Body("))
        assertTrue(requestSection.contains("val content: String"))
        assertFalse(requestSection.contains("data class Receipt("))
        assertTrue(responseSection.contains("        data class Receipt("))
        assertTrue(responseSection.contains("val messageKey: String"))
        assertFalse(responseSection.contains("data class Body("))

        val domainEventContent = domainEventFile.readText()
        assertTrue(domainEventContent.contains("package com.acme.demo.domain.aggregates.user_message.events"))
        assertTrue(domainEventContent.contains("import com.acme.demo.domain.aggregates.user_message.UserMessage"))
        assertTrue(domainEventContent.contains("class UserMessageCreatedDomainEvent("))
        assertFalse(
            projectDir.resolve(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/message/events/UserMessageCreatedDomainEvent.kt"
            ).toFile().exists(),
            "Domain event must not be routed by the design package."
        )

        val domainEventHandlerContent = domainEventHandlerFile.readText()
        assertTrue(domainEventHandlerContent.contains("package com.acme.demo.application.subscribers.domain.user_message"))
        assertTrue(
            domainEventHandlerContent.contains(
                "import com.acme.demo.domain.aggregates.user_message.events.UserMessageCreatedDomainEvent"
            )
        )
    }

    private fun Path.generatedFile(relativePath: String): Path {
        val file = resolve(relativePath)
        assertTrue(file.toFile().exists(), "Expected generated file to exist: $relativePath")
        return file
    }

    private fun assertPlanItemMetadata(
        planContent: String,
        templateId: String,
        outputPathSuffix: String,
        outputKind: String,
        resolvedOutputRoot: String,
        conflictPolicy: String,
    ) {
        val item = JsonParser.parseString(planContent)
            .asJsonObject
            .getAsJsonArray("items")
            .map { it.asJsonObject }
            .single {
                it.get("templateId").asString == templateId &&
                    it.get("outputPath").asString.endsWith(outputPathSuffix)
            }

        assertEquals(outputKind, item.get("outputKind").asString)
        assertEquals(resolvedOutputRoot, item.get("resolvedOutputRoot").asString)
        assertEquals(conflictPolicy, item.get("conflictPolicy").asString)
    }

    private fun assertUniqueArtifactPlanItemMetadata(
        planContent: String,
        templateId: String,
        outputPathSuffix: String,
    ) {
        val context = JsonParser.parseString(planContent)
            .asJsonObject
            .getAsJsonArray("items")
            .map { it.asJsonObject }
            .single {
                it.get("templateId").asString == templateId &&
                    it.get("outputPath").asString.endsWith(outputPathSuffix)
            }
            .getAsJsonObject("context")

        assertEquals(
            listOf("slug"),
            context.getAsJsonArray("uniqueSelectedBusinessFields").map { it.asString },
        )
        assertEquals(
            listOf("deleted", "version"),
            context.getAsJsonArray("uniqueFilteredControlFields").map { it.asString },
        )
        assertEquals("uk_v_slug", context.get("uniqueNormalizedName").asString)
        assertEquals("Slug", context.get("uniqueResolvedSuffix").asString)
        val uniquePhysicalName = context.get("uniquePhysicalName").asString
        assertTrue(uniquePhysicalName.startsWith("video_post_uk_v_slug"))
        assertTrue(uniquePhysicalName.contains("_INDEX_"))
    }

    private fun generatedSource(relativePath: String): String =
        relativePath.replace("/src/main/kotlin/", "/build/generated/cap4k/main/kotlin/")

    private fun assertNoFormattingRegression(file: Path) {
        val content = file.readText()
        assertFalse(
            content.lineSequence().any { it.endsWith(" ") || it.endsWith("\t") },
            "Expected no trailing whitespace in $file"
        )
        assertFalse(
            Regex("""\n{3,}""").containsMatchIn(content),
            "Expected no runs of three or more consecutive newlines in $file"
        )
    }

}
