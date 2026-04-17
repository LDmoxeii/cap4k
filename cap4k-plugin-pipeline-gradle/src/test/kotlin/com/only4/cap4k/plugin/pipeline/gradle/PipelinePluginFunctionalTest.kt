package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.gradle.FunctionalFixtureSupport.copyFixture
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
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
        assertTrue(planFile.readText().contains("\"templateId\": \"design/query_list.kt.peb\""))
        assertTrue(planFile.readText().contains("\"templateId\": \"design/query_page.kt.peb\""))
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
    fun `cap4kGenerate renders list and page query variants from repository config`() {
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

        assertTrue(listQueryContent.contains("import com.only4.cap4k.ddd.core.application.query.ListQueryParam"))
        assertFalse(listQueryContent.contains("import com.foo.Status"))
        assertFalse(listQueryContent.contains("import com.bar.Status"))
        assertTrue(listQueryContent.contains("data class Request("))
        assertTrue(listQueryContent.contains(") : ListQueryParam<Response>"))
        assertTrue(listQueryContent.contains("val customerId: Long"))
        assertTrue(listQueryContent.contains("val listCursorId: UUID"))
        assertTrue(listQueryContent.contains("val requestStatus: com.foo.Status"))
        assertTrue(listQueryContent.contains("data class Response("))
        assertTrue(listQueryContent.contains("val responseStatus: com.bar.Status"))
        assertTrue(listQueryContent.contains("val summary: Summary?"))
        assertFalse(listQueryContent.contains("val summary: Summary??"))
        assertTrue(listQueryContent.contains("data class Summary("))
        assertTrue(listQueryContent.contains("val updatedAt: LocalDateTime"))
        assertTrue(listQueryContent.contains("val summaryId: UUID"))

        assertTrue(pageQueryContent.contains("import com.only4.cap4k.ddd.core.application.query.PageQueryParam"))
        assertFalse(pageQueryContent.contains("import com.foo.Status"))
        assertFalse(pageQueryContent.contains("import com.bar.Status"))
        assertTrue(pageQueryContent.contains("data class Request("))
        assertTrue(pageQueryContent.contains(") : PageQueryParam<Response>()"))
        assertTrue(pageQueryContent.contains("val keyword: String"))
        assertTrue(pageQueryContent.contains("val createdAfter: LocalDateTime"))
        assertTrue(pageQueryContent.contains("val requestStatus: com.foo.Status"))
        assertTrue(pageQueryContent.contains("data class Response("))
        assertTrue(pageQueryContent.contains("val responseStatus: com.bar.Status"))
        assertTrue(pageQueryContent.contains("val snapshot: Snapshot?"))
        assertFalse(pageQueryContent.contains("val snapshot: Snapshot??"))
        assertTrue(pageQueryContent.contains("data class Snapshot("))
        assertTrue(pageQueryContent.contains("val publishedAt: LocalDateTime"))
        assertTrue(pageQueryContent.contains("val snapshotId: UUID"))
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
    fun `cap4kGenerate supports override list and page query templates`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-list-page-override")
        copyFixture(projectDir)

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                """
                |        design {
                |            enabled.set(true)
                |        }
                """.trimMargin(),
                """
                |        design {
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
        val listQueryContent = listQueryFile.readText()
        val pageQueryContent = pageQueryFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(listQueryFile.toFile().exists())
        assertTrue(pageQueryFile.toFile().exists())

        assertTrue(listQueryContent.contains("// override: representative list query migration template"))
        assertTrue(listQueryContent.contains("import com.only4.cap4k.ddd.core.application.query.ListQueryParam"))
        assertTrue(listQueryContent.contains("data class Request("))
        assertTrue(listQueryContent.contains(") : ListQueryParam<Response>"))
        assertFalse(listQueryContent.contains("import com.foo.Status"))
        assertFalse(listQueryContent.contains("import com.bar.Status"))
        assertTrue(listQueryContent.contains("val customerId: Long"))
        assertTrue(listQueryContent.contains("val listCursorId: UUID"))
        assertTrue(listQueryContent.contains("val requestStatus: com.foo.Status"))
        assertTrue(listQueryContent.contains("val responseStatus: com.bar.Status"))
        assertTrue(listQueryContent.contains("data class Summary("))
        assertTrue(listQueryContent.contains("val updatedAt: LocalDateTime"))
        assertTrue(listQueryContent.contains("val summaryId: UUID"))

        assertTrue(pageQueryContent.contains("// override: representative page query migration template"))
        assertTrue(pageQueryContent.contains("import com.only4.cap4k.ddd.core.application.query.PageQueryParam"))
        assertTrue(pageQueryContent.contains("data class Request("))
        assertTrue(pageQueryContent.contains(") : PageQueryParam<Response>()"))
        assertFalse(pageQueryContent.contains("import com.foo.Status"))
        assertFalse(pageQueryContent.contains("import com.bar.Status"))
        assertTrue(pageQueryContent.contains("val keyword: String"))
        assertTrue(pageQueryContent.contains("val createdAfter: LocalDateTime"))
        assertTrue(pageQueryContent.contains("val requestStatus: com.foo.Status"))
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
                |        design {
                |            enabled.set(true)
                |        }
                """.trimMargin(),
                """
                |        design {
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
                    |        design {
                    |            enabled.set(true)
                    |        }
                    """.trimMargin(),
                    """
                    |        design {
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
        assertTrue(content.contains("\"templateId\": \"design/query_list_handler.kt.peb\""))
        assertTrue(content.contains("\"templateId\": \"design/query_page_handler.kt.peb\""))
        assertTrue(content.contains("demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt"))
        assertTrue(content.contains("demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderListQry.kt"))
        assertTrue(content.contains("demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderPageQry.kt"))
        assertTrue(content.contains("demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderQryHandler.kt"))
        assertTrue(content.contains("demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderListQryHandler.kt"))
        assertTrue(content.contains("demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderPageQryHandler.kt"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate renders query handler variants into adapter module`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-query-handler-generate")
        copyFixture(projectDir, "design-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText()
                .replace("\r\n", "\n")
                .replace(
                    """
                    |        design {
                    |            enabled.set(true)
                    |        }
                    """.trimMargin(),
                    """
                    |        design {
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
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderQryHandler.kt"
        )
        val listHandlerFile = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderListQryHandler.kt"
        )
        val pageHandlerFile = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderPageQryHandler.kt"
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

        assertTrue(listContent.contains("import com.only4.cap4k.ddd.core.application.query.ListQuery"))
        assertTrue(listContent.contains("import com.acme.demo.application.queries.order.read.FindOrderListQry"))
        assertTrue(listContent.contains("class FindOrderListQryHandler : ListQuery<FindOrderListQry.Request, FindOrderListQry.Response>"))
        assertTrue(listContent.contains("override fun exec(request: FindOrderListQry.Request): List<FindOrderListQry.Response>"))
        assertTrue(listContent.contains("return listOf("))
        assertTrue(listContent.contains("responseStatus = TODO(\"set responseStatus\")"))
        assertTrue(listContent.contains("summary = TODO(\"set summary\")"))

        assertTrue(pageContent.contains("import com.only4.cap4k.ddd.core.application.query.PageQuery"))
        assertTrue(pageContent.contains("import com.only4.cap4k.ddd.core.share.PageData"))
        assertTrue(pageContent.contains("import com.acme.demo.application.queries.order.read.FindOrderPageQry"))
        assertTrue(pageContent.contains("class FindOrderPageQryHandler : PageQuery<FindOrderPageQry.Request, FindOrderPageQry.Response>"))
        assertTrue(pageContent.contains("override fun exec(request: FindOrderPageQry.Request): PageData<FindOrderPageQry.Response>"))
        assertTrue(pageContent.contains("return PageData.create(request, 1L, listOf("))
        assertTrue(pageContent.contains("responseStatus = TODO(\"set responseStatus\")"))
        assertTrue(pageContent.contains("snapshot = TODO(\"set snapshot\")"))

        assertTrue(defaultQueryContent.contains("object FindOrderQry"))
        assertTrue(defaultQueryContent.contains("data class Request("))
        assertTrue(defaultQueryContent.contains(") : RequestParam<Response>"))
        assertTrue(listQueryContent.contains("object FindOrderListQry"))
        assertTrue(listQueryContent.contains("data class Request("))
        assertTrue(listQueryContent.contains(") : ListQueryParam<Response>"))
        assertTrue(pageQueryContent.contains("object FindOrderPageQry"))
        assertTrue(pageQueryContent.contains("data class Request("))
        assertTrue(pageQueryContent.contains(") : PageQueryParam<Response>()"))
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
                |        design {
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
                |        design {
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
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderQryHandler.kt"
        ).readText()
        val listContent = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderListQryHandler.kt"
        ).readText()
        val pageContent = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderPageQryHandler.kt"
        ).readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(defaultContent.contains("// override: representative default query handler migration template"))
        assertTrue(listContent.contains("// override: representative list query handler migration template"))
        assertTrue(pageContent.contains("// override: representative page query handler migration template"))
        assertTrue(listContent.contains("override fun exec(request: FindOrderListQry.Request): List<FindOrderListQry.Response>"))
        assertTrue(listContent.contains("return listOf("))
        assertTrue(pageContent.contains("override fun exec(request: FindOrderPageQry.Request): PageData<FindOrderPageQry.Response>"))
        assertTrue(pageContent.contains("return PageData.create(request, 1L, listOf("))
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
                    |        design {
                    |            enabled.set(true)
                    |        }
                    """.trimMargin(),
                    """
                    |        design {
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
    fun `cap4kPlan fails fast when design query handler is enabled without design`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-query-handler-without-design")
        copyFixture(projectDir, "design-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent
                .replace(
                    """
                    |        design {
                    |            enabled.set(true)
                    |        }
                    """.trimMargin(),
                    """
                    |        design {
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

        assertTrue(result.output.contains("designQueryHandler generator requires enabled design generator."))
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
                |        design {
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
                |        design {
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
                |        design {
                |            enabled.set(true)
                |        }
                """.trimMargin(),
                """
                |        design {
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
        assertTrue(listQueryContent.contains(") : ListQueryParam<Response>"))
        assertTrue(pageQueryContent.contains(") : PageQueryParam<Response>()"))
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
                "tag": "cmd",
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
                "tag": "qry",
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
        val uniqueQueryFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/UniqueVideoPostSlugQry.kt"
        )
        val uniqueQueryHandlerFile = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video_post/unique/UniqueVideoPostSlugQryHandler.kt"
        )
        val uniqueValidatorFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/validators/video_post/unique/UniqueVideoPostSlug.kt"
        )

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
        assertTrue(factoryFile.toFile().exists())
        assertTrue(specificationFile.toFile().exists())
        assertTrue(wrapperFile.toFile().exists())
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
        assertTrue(uniqueQueryHandlerContent.contains("class UniqueVideoPostSlugQryHandler"))
        assertTrue(
            uniqueQueryHandlerContent.contains(
                "class UniqueVideoPostSlugQryHandler : Query<UniqueVideoPostSlugQry.Request, UniqueVideoPostSlugQry.Response>"
            )
        )
        assertTrue(
            uniqueQueryHandlerContent.contains(
                "override fun exec(request: UniqueVideoPostSlugQry.Request): UniqueVideoPostSlugQry.Response"
            )
        )
        assertTrue(uniqueQueryHandlerContent.contains("return UniqueVideoPostSlugQry.Response("))
        assertTrue(uniqueValidatorContent.contains("annotation class UniqueVideoPostSlug"))
        assertTrue(
            uniqueValidatorContent.contains(
                "@Constraint(validatedBy = [UniqueVideoPostSlug.Validator::class])"
            )
        )
        assertTrue(
            uniqueValidatorContent.contains(
                "class Validator : ConstraintValidator<UniqueVideoPostSlug, UniqueVideoPostSlugQry.Request>"
            )
        )
        assertTrue(uniqueValidatorContent.contains("request.slug"))
        assertTrue(uniqueValidatorContent.contains("request.excludeVideoPostId"))
        assertTrue(
            uniqueValidatorContent.contains(
                "import com.acme.demo.application.queries.video_post.unique.UniqueVideoPostSlugQry"
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
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/UniqueVideoPostTenantIdSlugQry.kt"
        )
        val compositeQueryHandlerFile = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video_post/unique/UniqueVideoPostTenantIdSlugQryHandler.kt"
        )
        val compositeValidatorFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/validators/video_post/unique/UniqueVideoPostTenantIdSlug.kt"
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
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
        ).readText()

        assertTrue(planResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planFile.toFile().exists())
        assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/enum.kt.peb\""))
        assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/enum_translation.kt.peb\""))
        assertTrue(
            projectDir.resolve(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/shared/enums/Status.kt"
            ).toFile().exists()
        )
        assertTrue(
            projectDir.resolve(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/enums/VideoPostVisibility.kt"
            ).toFile().exists()
        )
        assertTrue(
            projectDir.resolve(
                "demo-adapter/src/main/kotlin/com/acme/demo/domain/translation/shared/StatusTranslation.kt"
            ).toFile().exists()
        )
        assertTrue(
            projectDir.resolve(
                "demo-adapter/src/main/kotlin/com/acme/demo/domain/translation/video_post/VideoPostVisibilityTranslation.kt"
            ).toFile().exists()
        )
        assertTrue(generatedEntity.contains("val status: com.acme.demo.domain.shared.enums.Status"))
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
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
            ).toFile().exists()
        )
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
    fun `wrapper task depending on cap4kGenerate still infers compileKotlin dependency`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-flow-wrapper")
        copyFixture(projectDir, "flow-compile-sample")
        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace("\r\n", "\n") +
                """

                tasks.register("cap4kGenerateWrapper") {
                    dependsOn("cap4kGenerate")
                }
                """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("cap4kGenerateWrapper")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(projectDir.resolve("build/cap4k-code-analysis/nodes.json").toFile().exists())
        assertTrue(projectDir.resolve("flows/OrderController_submit.json").toFile().exists())
        assertTrue(projectDir.resolve("flows/OrderController_submit.mmd").toFile().exists())
        assertTrue(projectDir.resolve("flows/index.json").toFile().exists())
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
            "demo-domain/src/main/kotlin/com/acme/demo/domain/order/events/OrderCreatedDomainEvent.kt"
        )
        val handlerFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/order/events/OrderCreatedDomainEventSubscriber.kt"
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
            "demo-domain/src/main/kotlin/com/acme/demo/domain/order/events/OrderCreatedDomainEvent.kt"
        )
        val handlerFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/order/events/OrderCreatedDomainEventSubscriber.kt"
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
    fun `cap4kPlan domain event flow fails when ksp metadata source is disabled`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-domain-event-no-ksp-metadata")
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

        assertTrue(result.output.contains("designDomainEvent generator requires enabled kspMetadata source."))
    }

}
