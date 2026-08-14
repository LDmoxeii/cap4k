package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.json.PipelineJson
import com.only4.cap4k.plugin.pipeline.gradle.FunctionalFixtureSupport.copyCompileFixture
import com.only4.cap4k.plugin.pipeline.gradle.FunctionalFixtureSupport.copyFixture
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.readText
import kotlin.io.path.writeText

class PipelinePluginFunctionalTest {
    private val jsonMapper = PipelineJson.newMapper(includeNulls = true)

    private val legacyAggregateAnnotationFq =
        listOf("com.only4.cap4k.ddd.core.domain", "aggregate.annotation.Aggregate").joinToString(".")
    private val legacyAggregateCall = "@" + "Aggregate("
    private val legacyAggregateTypeEntity = listOf("Aggregate", "TYPE_ENTITY").joinToString(".")


    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan writes pretty printed plan json`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-plan")
        copyCompileFixture(projectDir, "design-integrated-compile-sample")

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kPlan")
            .build()

        val planFile = projectDir.resolve("build/cap4k/plan.json").toFile()
        val planText = planFile.readText()
        val planJson = jsonMapper.readTree(planText).requireObjectNode()
        val planItems = planJson.requireArrayNode("items").map { it.requireObjectNode() }
        val designItems = planItems.filter { item -> item.get("templateId").asText().startsWith("design/") }
        val commandItem = designItems.first { it.get("templateId").asText() == "design/command.kt.peb" }

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planFile.exists())
        assertTrue(planText.contains("\n  \"items\""))
        assertTrue(planJson.has("diagnostics"))
        val templateIds = planItems.map { item -> item.get("templateId").asText() }.toSet()
        assertTrue("design/command.kt.peb" in templateIds)
        assertTrue("design/query.kt.peb" in templateIds)
        assertTrue("design/domain_service.kt.peb" in templateIds)
        assertTrue("types/value-object" in templateIds)
        assertFalse(planItems.any { item -> item.get("generatorId").asText() == "design-validator" })
        assertFalse("design/validator.kt.peb" in templateIds)
        assertFalse("design/query_list.kt.peb" in templateIds)
        assertFalse("design/query_page.kt.peb" in templateIds)
        assertEquals(
            setOf(
                "command",
                "query",
                "query-handler",
                "capability",
                "capability-handler",
                "api-payload",
                "domain-event",
                "domain-subscriber",
                "integration-event",
                "integration-subscriber",
                "domain-service",
            ),
            designItems.map { item -> item.get("generatorId").asText() }.toSet(),
        )
        assertFalse(
            designItems.any { item ->
                val contractText = item.toString().lowercase()
                contractText.contains("scheduled") || contractText.contains("job") || contractText.contains("validator")
            },
        )
        assertEquals("command", commandItem.get("generatorId").asText())
        assertEquals("", commandItem.get("resolvedOutputRoot").asText())
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt",
            commandItem.get("outputPath").asText(),
        )
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate renders command and query files from repository config`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-generate")
        copyFixture(projectDir)

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kGenerate")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
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
        assertTrue(commandContent.contains("import com.foo.Status"))
        assertFalse(commandContent.contains("import com.bar.Status"))
        assertTrue(commandContent.contains("object SubmitOrderCmd"))
        assertTrue(commandContent.contains("data class Request("))
        assertTrue(commandContent.contains("val orderId: Long"))
        assertTrue(commandContent.contains("val submittedAt: LocalDateTime"))
        assertTrue(commandContent.contains("val mirroredSubmittedAt: LocalDateTime"))
        assertTrue(commandContent.contains("val externalId: UUID"))
        assertTrue(commandContent.contains("val trackingId: UUID"))
        assertTrue(commandContent.contains("val requestStatus: Status"))
        assertTrue(commandContent.contains("val address: Address?"))
        assertFalse(commandContent.contains("val address: Address??"))
        assertTrue(commandContent.contains("data class Address("))
        assertTrue(commandContent.contains("val city: String"))
        assertTrue(commandContent.contains("val addressId: UUID"))
        assertTrue(commandContent.contains("data object Response"))

        assertTrue(queryContent.contains("object FindOrderQry"))
        assertTrue(queryContent.contains("import com.only4.cap4k.ddd.core.application.query.Query"))
        assertTrue(queryContent.contains("import java.time.LocalDateTime"))
        assertTrue(queryContent.contains("import java.util.UUID"))
        assertFalse(queryContent.contains("import com.foo.Status"))
        assertFalse(queryContent.contains("import com.bar.Status"))
        assertTrue(queryContent.contains("data class Request("))
        assertTrue(queryContent.contains(") : Query<Response>"))
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

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kGenerate")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
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

        assertTrue(listQueryContent.contains("import com.only4.cap4k.ddd.core.application.query.Query"))
        assertFalse(listQueryContent.contains("import com.foo.Status"))
        assertTrue(listQueryContent.contains("import com.bar.Status"))
        assertTrue(listQueryContent.contains("class Request : Query<Response>"))
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

        assertTrue(pageQueryContent.contains("import com.only4.cap4k.ddd.core.application.query.Query"))
        assertTrue(pageQueryContent.contains("import com.only4.cap4k.ddd.core.application.query.PageRequest"))
        assertTrue(pageQueryContent.contains("import com.only4.cap4k.ddd.core.share.PageData"))
        assertFalse(pageQueryContent.contains("import com.foo.Status"))
        assertTrue(pageQueryContent.contains("data class Request("))
        assertTrue(pageQueryContent.contains("override val pageNum: Int = 1"))
        assertTrue(pageQueryContent.contains("override val pageSize: Int"))
        assertTrue(pageQueryContent.contains(") : PageRequest, Query<Response>"))
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

        val result = FunctionalFixtureSupport.runner(projectDir)
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

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kGenerate")
            .buildAndFail()

        assertTrue(result.output.contains("invalid default value for semantic field"))
        assertTrue(result.output.contains("enabled"))
        assertTrue(result.output.contains("Boolean defaults must be true or false"))
        assertFalse(generatedFile.toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate supports unified query template override for all query names`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-list-page-override")
        copyFixture(projectDir)

        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.appendTemplateOverrideBlock()

        val result = FunctionalFixtureSupport.runner(projectDir)
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
        assertTrue(listQueryContent.contains("import com.only4.cap4k.ddd.core.application.query.Query"))
        assertTrue(listQueryContent.contains("class Request : Query<Response>"))
        assertFalse(listQueryContent.contains("import com.foo.Status"))
        assertTrue(listQueryContent.contains("val items: List<Item>"))
        assertTrue(listQueryContent.contains("val responseStatus: Status"))
        assertTrue(listQueryContent.contains("data class Summary("))
        assertTrue(listQueryContent.contains("val updatedAt: LocalDateTime"))
        assertTrue(listQueryContent.contains("val summaryId: UUID"))

        assertTrue(pageQueryContent.contains("import com.only4.cap4k.ddd.core.application.query.Query"))
        assertTrue(pageQueryContent.contains("import com.only4.cap4k.ddd.core.share.PageData"))
        assertTrue(pageQueryContent.contains("data class Request("))
        assertTrue(pageQueryContent.contains(") : Query<Response>"))
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
        buildFile.appendTemplateOverrideBlock()

        val result = FunctionalFixtureSupport.runner(projectDir)
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
        assertTrue(commandContent.contains("class Response : Serializable"))
        assertTrue(commandContent.contains("import java.time.LocalDateTime"))
        assertTrue(commandContent.contains("import java.util.UUID"))
        assertTrue(commandContent.contains("import com.foo.Status"))
        assertFalse(commandContent.contains("import com.bar.Status"))
        assertTrue(commandContent.contains("val orderId: Long = 1L"))
        assertTrue(commandContent.contains("val title: String = \"demo\""))
        assertTrue(commandContent.contains("val createdAt: LocalDateTime = java.time.LocalDateTime.MIN"))
        assertTrue(commandContent.contains("val mirroredSubmittedAt: LocalDateTime"))
        assertTrue(commandContent.contains("val trackingId: UUID"))
        assertTrue(commandContent.contains("val requestStatus: Status"))
        assertFalse(commandContent.contains("val responseStatus: com.bar.Status"))
        assertTrue(commandContent.contains("val address: Address?"))
        assertFalse(commandContent.contains("val address: Address??"))
        assertFalse(commandContent.contains("val result: Result?"))
        assertFalse(commandContent.contains("val result: Result??"))
        assertTrue(commandContent.contains("data class Address("))
        assertTrue(commandContent.contains("val city: String"))
        assertTrue(commandContent.contains("val addressId: UUID"))
        assertFalse(commandContent.contains("data class Result("))
        assertFalse(commandContent.contains("val receiptId: UUID"))
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

        val result = FunctionalFixtureSupport.runner(projectDir)
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

        val result = FunctionalFixtureSupport.runner(projectDir)
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

        assertTrue(defaultContent.contains("import com.only4.cap4k.ddd.core.application.query.QueryHandler"))
        assertTrue(defaultContent.contains("import com.acme.demo.application.queries.order.read.FindOrderQry"))
        assertTrue(defaultContent.contains("class FindOrderQryHandler : QueryHandler<FindOrderQry.Request, FindOrderQry.Response>"))
        assertTrue(defaultContent.contains("override fun handle(query: FindOrderQry.Request)"))
        assertTrue(defaultContent.contains("responseStatus = TODO(\"set responseStatus\")"))
        assertTrue(defaultContent.contains("snapshot = TODO(\"set snapshot\")"))

        assertTrue(listContent.contains("import com.only4.cap4k.ddd.core.application.query.QueryHandler"))
        assertTrue(listContent.contains("import com.acme.demo.application.queries.order.read.FindOrderListQry"))
        assertTrue(listContent.contains("class FindOrderListQryHandler : QueryHandler<FindOrderListQry.Request, FindOrderListQry.Response>"))
        assertTrue(listContent.contains(" : QueryHandler<"))
        assertTrue(listContent.contains(".Request, "))
        assertTrue(listContent.contains(".Response>"))
        assertTrue(listContent.contains("items = TODO(\"set items\")"))
        assertFalse(listContent.contains("List" + "Query<"))
        assertFalse(listContent.contains("Page" + "Query<"))
        assertFalse(listContent.contains("List<FindOrder" + "ListQry.Response>"))
        assertFalse(listContent.contains("PageData<FindOrder" + "PageQry.Response>"))

        assertTrue(pageContent.contains("import com.only4.cap4k.ddd.core.application.query.QueryHandler"))
        assertTrue(pageContent.contains("import com.acme.demo.application.queries.order.read.FindOrderPageQry"))
        assertTrue(pageContent.contains("class FindOrderPageQryHandler : QueryHandler<FindOrderPageQry.Request, FindOrderPageQry.Response>"))
        assertTrue(pageContent.contains(" : QueryHandler<"))
        assertTrue(pageContent.contains(".Request, "))
        assertTrue(pageContent.contains(".Response>"))
        assertTrue(pageContent.contains("page = TODO(\"set page\")"))
        assertFalse(pageContent.contains("List" + "Query<"))
        assertFalse(pageContent.contains("Page" + "Query<"))
        assertFalse(pageContent.contains("List<FindOrder" + "ListQry.Response>"))
        assertFalse(pageContent.contains("PageData<FindOrder" + "PageQry.Response>"))

        assertTrue(defaultQueryContent.contains("object FindOrderQry"))
        assertTrue(defaultQueryContent.contains("data class Request("))
        assertTrue(defaultQueryContent.contains(") : Query<Response>"))
        assertTrue(listQueryContent.contains("object FindOrderListQry"))
        assertTrue(listQueryContent.contains("class Request : Query<Response>"))
        assertTrue(pageQueryContent.contains("object FindOrderPageQry"))
        assertTrue(pageQueryContent.contains("data class Request("))
        assertTrue(pageQueryContent.contains(") : PageRequest, Query<Response>"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate supports override query handler templates`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-query-handler-override")
        copyFixture(projectDir, "design-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.appendTemplateOverrideBlock()

        val result = FunctionalFixtureSupport.runner(projectDir)
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
        assertTrue(listContent.contains("class FindOrderListQryHandler : QueryHandler<FindOrderListQry.Request, FindOrderListQry.Response>"))
        assertTrue(pageContent.contains("class FindOrderPageQryHandler : QueryHandler<FindOrderPageQry.Request, FindOrderPageQry.Response>"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan fails fast when design query handler lacks adapter module path`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-query-handler-missing-adapter")
        copyFixture(projectDir, "design-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace("        adapterModulePath.set(\"demo-adapter\")", "        adapterModulePath.set(\"\")")
        )

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(
            result.output.contains(
                "adapter module is required"
            )
        )
        assertFalse(projectDir.resolve("build/cap4k/plan.json").toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan includes capability and capability handler artifacts from fixture`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-capability-plan")
        copyFixture(projectDir, "design-sample")

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kPlan")
            .build()

        val planFile = projectDir.resolve("build/cap4k/plan.json").toFile()
        val content = planFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planFile.exists())
        assertTrue(content.contains("\"templateId\": \"design/capability.kt.peb\""))
        assertTrue(content.contains("\"templateId\": \"design/capability_handler.kt.peb\""))
        assertTrue(
            content.contains(
                "demo-application/src/main/kotlin/com/acme/demo/application/capabilities/authorize/IssueToken.kt"
            )
        )
        assertTrue(
            content.contains(
                "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/capabilities/authorize/IssueTokenHandler.kt"
            )
        )
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate renders capability and capability handler files from fixture`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-capability-generate")
        copyFixture(projectDir, "design-sample")

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kGenerate")
            .build()

        val capabilityFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/capabilities/authorize/IssueToken.kt"
        )
        val handlerFile = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/capabilities/authorize/IssueTokenHandler.kt"
        )
        val capabilityContent = capabilityFile.readText()
        val handlerContent = handlerFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(capabilityFile.toFile().exists())
        assertTrue(handlerFile.toFile().exists())
        assertTrue(capabilityContent.contains("import com.only4.cap4k.ddd.core.application.capability.CapabilityCall"))
        assertTrue(capabilityContent.contains("object IssueToken"))
        assertTrue(capabilityContent.contains(") : CapabilityCall<Response>"))
        assertTrue(capabilityContent.contains("val account: String"))
        assertTrue(capabilityContent.contains("val token: String"))
        assertTrue(handlerContent.contains("import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler"))
        assertTrue(handlerContent.contains("import com.acme.demo.application.capabilities.authorize.IssueToken"))
        assertTrue(
            handlerContent.contains(
                "class IssueTokenHandler : CapabilityHandler<IssueToken.Request, IssueToken.Response>"
            )
        )
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate supports override capability and capability handler templates`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-capability-override")
        copyFixture(projectDir, "design-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.appendTemplateOverrideBlock()

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kGenerate")
            .build()

        val capabilityContent = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/capabilities/authorize/IssueToken.kt"
        ).readText()
        val handlerContent = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/capabilities/authorize/IssueTokenHandler.kt"
        ).readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(capabilityContent.contains("// override: representative capability migration template"))
        assertTrue(handlerContent.contains("// override: representative capability handler migration template"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan fails fast when design capability lacks application module path`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-capability-missing-application")
        copyFixture(projectDir, "design-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                "        applicationModulePath.set(\"demo-application\")",
                "        applicationModulePath.set(\"\")",
            )
        )

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(
            result.output.contains(
                "application module is required"
            )
        )
        assertFalse(projectDir.resolve("build/cap4k/plan.json").toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate resolves short type from project type registry`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-type-registry")
        copyFixture(projectDir, "design-type-registry-sample")

        val result = FunctionalFixtureSupport.runner(projectDir)
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
    fun `cap4kGenerate fails when same-package sibling application call name is used as a short type`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-sibling-short-type")
        copyFixture(projectDir, "design-type-registry-sample")
        projectDir.resolve("iterate/design/registry_design.json").writeText(
            """
            [
              {
                "tag": "command",
                "package": "video.publish",
                "name": "StartVideoProcessing",
                "description": "start video processing",
                "aggregates": ["Video"],
                "fields": [
                  { "name": "fileSpec", "type": "VideoPostProcessingFileSpecQry" }
                ]
              },
              {
                "tag": "query",
                "package": "video.publish",
                "name": "VideoPostProcessingFileSpec",
                "description": "video processing file spec",
                "aggregates": ["Video"],
                "fields": []
              }
            ]
            """.trimIndent()
        )

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kGenerate")
            .buildAndFail()
        assertTrue(result.output.contains("failed to resolve semantic type for field"))
        assertTrue(result.output.contains("fileSpec (VideoPostProcessingFileSpecQry)"))
        assertTrue(result.output.contains("unknown short type: VideoPostProcessingFileSpecQry"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate fails on ambiguous short type`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-ambiguous-short-type")
        copyFixture(projectDir, "design-sample")

        val designFile = projectDir.resolve("design/design.json")
        val designEntries = jsonMapper.readTree(designFile.readText()).requireArrayNode()
        val findOrder = designEntries
            .map { it.requireObjectNode() }
            .single { it["name"].asText() == "FindOrder" }
        findOrder["fields"].requireArrayNode().add(
            jsonMapper.createObjectNode().apply {
                put("name", "ambiguousStatus")
                put("type", "Status")
            }
        )
        designFile.writeText(designEntries.toString())

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kGenerate")
            .buildAndFail()

        assertTrue(result.output.contains("failed to resolve semantic type for field"))
        assertTrue(result.output.contains("ambiguousStatus (Status)"))
        assertTrue(result.output.contains("ambiguous short type: Status matches com.foo.Status, com.bar.Status"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate keeps existing files on rerun because default conflict policy is skip`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-rerun")
        copyFixture(projectDir)

        FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kGenerate")
            .build()

        val commandFile = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt"
        )
        commandFile.writeText("sentinel")

        val result = FunctionalFixtureSupport.runner(projectDir)
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

        val result = FunctionalFixtureSupport.runner(projectDir)
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

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kPlan", "cap4kGenerate")
            .build()

        val planFile = projectDir.resolve("build/cap4k/plan.json").toFile()
        val factoryFile = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt"
        )
        val behaviorFile = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostBehavior.kt"
        )
        val schemaFile = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPost.kt")
        )
        val entityFile = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        )
        val repositoryFile = projectDir.resolve(
            generatedSource("demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/repositories/VideoPostRepository.kt")
        )
        val strongIdFile = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostId.kt")
        )

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planFile.exists())
        assertTrue(schemaFile.toFile().exists())
        assertTrue(entityFile.toFile().exists())
        val generatedVideoPostContent = entityFile.readText()
        assertTrue(repositoryFile.toFile().exists())
        assertTrue(strongIdFile.toFile().exists())
        assertTrue(factoryFile.toFile().exists())
        assertTrue(behaviorFile.toFile().exists())
        val schemaContent = schemaFile.readText()
        val repositoryContent = repositoryFile.readText()
        val strongIdContent = strongIdFile.readText()
        val factoryContent = factoryFile.readText()
        assertFalse(generatedVideoPostContent.contains(legacyAggregateAnnotationFq))
        assertFalse(generatedVideoPostContent.contains(legacyAggregateCall))
        assertFalse(generatedVideoPostContent.contains(legacyAggregateTypeEntity))
        assertAggregateElementContent(
            generatedVideoPostContent,
            aggregate = "VideoPost",
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            type = "entity",
            root = true,
        )
        assertAggregateElementContent(
            schemaContent,
            aggregate = "VideoPost",
            name = "SVideoPost",
            packageName = "com.acme.demo.domain._share.meta.video_post",
            type = "schema",
            root = false,
        )
        assertAggregateElementContent(
            repositoryContent,
            aggregate = "VideoPost",
            name = "VideoPostRepository",
            packageName = "com.acme.demo.adapter.domain.repositories",
            type = "repository",
            root = false,
        )
        assertAggregateElementContent(
            strongIdContent,
            aggregate = "VideoPost",
            name = "VideoPostId",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            type = "strong-id",
            root = true,
        )
        assertAggregateElementContent(
            factoryContent,
            aggregate = "VideoPost",
            name = "VideoPostFactory",
            packageName = "com.acme.demo.domain.aggregates.video_post.factory",
            type = "factory",
            root = false,
        )
        assertTrue(planFile.readText().contains("\"items\""))
        assertTrue(planFile.readText().contains("\"diagnostics\""))
        assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/entity.kt.peb\""))
        assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/factory.kt.peb\""))
        assertFalse(planFile.readText().contains("\"templateId\": \"aggregate/wrapper.kt.peb\""))
        assertTrue(
            factoryContent.contains("class VideoPostFactory : AggregateFactory<VideoPostFactory.Payload, VideoPost>")
        )
        assertTrue(factoryContent.contains("import com.acme.demo.domain.aggregates.video_post.VideoPost"))
        assertTrue(behaviorFile.readText().contains("Place behavior for VideoPost and its owned entities here."))
        assertFalse(
            projectDir.resolve(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggVideoPost.kt"
            ).toFile().exists()
        )
        val planContent = planFile.readText()
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
    fun `cap4kPlan applies template conflict policy overrides predictably across mixed aggregate surfaces`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-conflict-policy")
        copyFixture(projectDir, "aggregate-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        val originalTemplatesBlock = """
            |    templates {
            |        overrideDirs.from("template-overrides")
            |    }
            """.trimMargin()
        require(buildFileContent.contains(originalTemplatesBlock)) {
            "aggregate-sample fixture templates block changed"
        }
        buildFile.writeText(
            buildFileContent.replace(
                originalTemplatesBlock,
                """
                |    templates {
                |        overrideDirs.from("template-overrides")
                |        templateConflictPolicies.put("aggregate/factory.kt.peb", "OVERWRITE")
                |        templateConflictPolicies.put("aggregate/behavior.kt.peb", "FAIL")
                |        templateConflictPolicies.put("aggregate/entity.kt.peb", "FAIL")
                |    }
                """.trimMargin(),
            )
        )

        val result = FunctionalFixtureSupport
            .runner(projectDir, "cap4kPlan")
            .build()

        val planContent = projectDir.resolve("build/cap4k/plan.json").readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertPlanItemMetadata(
            planContent = planContent,
            templateId = "aggregate/factory.kt.peb",
            outputPathSuffix = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt",
            outputKind = "CHECKED_IN_SOURCE",
            resolvedOutputRoot = "demo-domain/src/main/kotlin",
            conflictPolicy = "SKIP",
        )
        assertPlanItemMetadata(
            planContent = planContent,
            templateId = "aggregate/behavior.kt.peb",
            outputPathSuffix = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostBehavior.kt",
            outputKind = "CHECKED_IN_SOURCE",
            resolvedOutputRoot = "demo-domain/src/main/kotlin",
            conflictPolicy = "SKIP",
        )
        assertPlanItemMetadata(
            planContent = planContent,
            templateId = "aggregate/entity.kt.peb",
            outputPathSuffix = generatedSource(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
            ),
            outputKind = "GENERATED_SOURCE",
            resolvedOutputRoot = "demo-domain/build/generated/cap4k/main/kotlin",
            conflictPolicy = "FAIL",
        )
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `aggregate generator defaults to minimal aggregate artifacts`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-minimal")
        copyFixture(projectDir, "aggregate-minimal-sample")

        val result = FunctionalFixtureSupport.runner(projectDir)
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
        assertTrue(planContent.contains("\"templateId\": \"aggregate/factory.kt.peb\""))
        assertFalse(planContent.contains("\"templateId\": \"aggregate/wrapper.kt.peb\""))
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
        assertTrue(
            projectDir.resolve(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt"
            ).toFile().exists()
        )
        assertFalse(
            projectDir.resolve(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggVideoPost.kt"
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
        assertTrue(generatedEntityFile.readText().contains("class VideoPost internal constructor("))
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
    fun `removing json persistence deletes stale generated value object converter`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-value-object-projection-removal")
        copyCompileFixture(projectDir, "design-integrated-compile-sample")
        writeValueObjectProjectionOnlyBuild(projectDir, includeManifestSource = true)
        val manifest = projectDir.resolve("design/value-objects.json")
        manifest.writeText(
            manifest.readText().replace(
                "\"package\": \"com.acme.demo.domain.shared.values\",",
                "\"package\": \"com.acme.demo.domain.shared.values\",\n    \"persistence\": { \"kind\": \"json\" },",
            )
        )
        val converter = projectDir.resolve(
            generatedSource(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/shared/values/OrderAddressJsonAttributeConverter.kt"
            )
        )

        val initialResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate", "cap4kGenerateSources")
            .build()
        assertTrue(initialResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(converter.toFile().exists())

        manifest.writeText(
            manifest.readText().replace("    \"persistence\": { \"kind\": \"json\" },", "")
        )

        val refreshedResult = FunctionalFixtureSupport.runner(projectDir, "cap4kGenerateSources").build()

        assertTrue(refreshedResult.output.contains("BUILD SUCCESSFUL"))
        assertEquals(TaskOutcome.SUCCESS, refreshedResult.task(":cap4kGenerateSources")?.outcome)
        assertFalse(converter.toFile().exists())
        assertTrue(
            projectDir.resolve(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/shared/values/OrderAddress.kt"
            ).toFile().exists()
        )
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `removing value object manifest source cleans historically managed converter root`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-value-object-source-removal")
        copyCompileFixture(projectDir, "design-integrated-compile-sample")
        writeValueObjectProjectionOnlyBuild(projectDir, includeManifestSource = true)
        val manifest = projectDir.resolve("design/value-objects.json")
        manifest.writeText(
            manifest.readText().replace(
                "\"package\": \"com.acme.demo.domain.shared.values\",",
                "\"package\": \"com.acme.demo.domain.shared.values\",\n    \"persistence\": { \"kind\": \"json\" },",
            )
        )
        val generatedRoot = projectDir.resolve("demo-domain/build/generated/cap4k/main/kotlin")
        val converter = generatedRoot.resolve(
            "com/acme/demo/domain/shared/values/OrderAddressJsonAttributeConverter.kt"
        )

        val initialResult = FunctionalFixtureSupport.runner(projectDir, "cap4kGenerateSources").build()
        assertEquals(TaskOutcome.SUCCESS, initialResult.task(":cap4kGenerateSources")?.outcome)
        assertTrue(converter.toFile().exists())
        assertTrue(projectDir.resolve("build/cap4k/generated-source-managed-roots.json").toFile().exists())

        writeValueObjectProjectionOnlyBuild(projectDir, includeManifestSource = false)
        val refreshedResult = FunctionalFixtureSupport.runner(projectDir, "cap4kGenerateSources").build()

        assertTrue(refreshedResult.output.contains("BUILD SUCCESSFUL"))
        assertEquals(TaskOutcome.SUCCESS, refreshedResult.task(":cap4kGenerateSources")?.outcome)
        assertFalse(converter.toFile().exists())
        assertFalse(generatedRoot.toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerateSources writes aggregate projection generated source when enabled alone`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-projection-generated-sources")
        copyFixture(projectDir, "aggregate-minimal-sample")
        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText()
                .replace("\r\n", "\n")
                .replace(
                    """        aggregate {
        }""",
                    """        aggregateProjection {
            }""",
                )
        )

        val result = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerateSources")
            .build()

        val projectionFile = projectDir.resolve(
            generatedSource(
                "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/projections/video_post/VideoPostProjection.kt"
            )
        )
        val entityFile = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        )

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(projectionFile.toFile().exists())
        assertFalse(entityFile.toFile().exists())
        val projectionContent = projectionFile.readText()
        assertTrue(projectionContent.contains("package com.acme.demo.adapter.application.projections.video_post"))
        assertTrue(projectionContent.contains("@Entity"))
        assertTrue(projectionContent.contains("class VideoPostProjection("))
        assertTrue(projectionContent.contains("slug: String"))
        assertFalse(projectionContent.contains("ManyToOne"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerateSources uses explicit design input without auxiliary source tasks`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-generated-sources-design-only")
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
                "description": "find video post",
                "fields": [
                  { "name": "id", "type": "Long" }
                ],
                "resultFields": [
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

                cap4k {
                    sources {
                        designJson {
                            files.from("design/design.json")
                        }
                    }
                }
                """.trimIndent()
        )

        val result = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerateSources")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
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
    fun `cap4kGenerate keeps owned direct parent bindings forward only`() {
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
        val oneChildEntityFile = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostFile.kt")
        )
        val rootEntityContent = rootEntityFile.readText()
        val childEntityContent = childEntityFile.readText()
        val oneChildEntityContent = oneChildEntityFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(rootEntityFile.toFile().exists())
        assertTrue(childEntityFile.toFile().exists())
        assertTrue(oneChildEntityFile.toFile().exists())
        assertFalse(
            projectDir.resolve("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
                .toFile()
                .exists()
        )
        assertTrue(rootEntityContent.contains("title: String"))
        assertTrue(rootEntityContent.contains("class VideoPost internal constructor("))
        assertFalse(rootEntityContent.contains("data class VideoPost("))
        assertTrue(rootEntityContent.contains("import jakarta.persistence.CascadeType"))
        assertTrue(
            rootEntityContent.contains(
                "@OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE], orphanRemoval = true)"
            )
        )
        assertFalse(rootEntityContent.contains("CascadeType.ALL"))
        assertTrue(rootEntityContent.contains("@JoinColumn(name = \"video_post_id\", nullable = false)"))
        assertTrue(rootEntityContent.contains("import com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList"))
        assertTrue(rootEntityContent.contains("private var _items: MutableList<VideoPostItem> = mutableListOf()"))
        assertTrue(rootEntityContent.contains("val items: OwnedEntityList<VideoPostItem>"))
        assertTrue(rootEntityContent.contains("get() = OwnedEntityList.of(_items, VideoPostItem::class, \"VideoPost.items\")"))
        assertFalse(rootEntityContent.replace("\r\n", "\n").contains("\n    val items: MutableList<VideoPostItem> = mutableListOf()"))
        assertTrue(rootEntityContent.contains("import jakarta.persistence.Transient"))
        assertTrue(rootEntityContent.contains("private var _files: MutableList<VideoPostFile> = mutableListOf()"))
        assertFalse(rootEntityContent.replace("\r\n", "\n").contains("\n    val files: MutableList<VideoPostFile> = mutableListOf()"))
        assertTrue(rootEntityContent.contains("@get:Transient"))
        assertTrue(rootEntityContent.contains("var file: VideoPostFile?"))
        assertTrue(rootEntityContent.contains("get() = OwnedEntityList.of(_files, VideoPostFile::class, \"VideoPost.file\")"))
        assertTrue(rootEntityContent.contains(".singleOrNull()"))
        assertTrue(rootEntityContent.contains("OwnedEntityList.of(_files, VideoPostFile::class, \"VideoPost.file\")"))
        assertTrue(rootEntityContent.contains("set(value)"))
        assertTrue(rootEntityContent.contains(".replace(value)"))
        assertFalse(rootEntityContent.contains("_files.clear()"))
        assertFalse(rootEntityContent.contains("_files.add(value)"))
        assertTrue(rootEntityContent.contains("import com.acme.demo.domain.aggregates.user_profile.UserProfileId"))
        assertTrue(rootEntityContent.contains("var authorId: UserProfileId = authorId"))
        assertTrue(rootEntityContent.contains("var coverProfileId: UserProfileId? = coverProfileId"))
        assertFalse(rootEntityContent.contains("@JoinColumn(name = \"author_id\""))
        assertFalse(rootEntityContent.contains("lateinit var author: UserProfile"))
        assertFalse(rootEntityContent.contains("val author_id:"))
        assertFalse(rootEntityContent.contains("@OneToOne(fetch = FetchType.EAGER)"))
        assertFalse(rootEntityContent.contains("@JoinColumn(name = \"cover_profile_id\""))
        assertFalse(rootEntityContent.contains("var coverProfile: UserProfile? = null"))
        assertFalse(rootEntityContent.contains("mappedBy ="))
        assertFalse(rootEntityContent.contains("ManyToMany"))
        assertFalse(childEntityContent.contains("videoPostId"))
        assertFalse(childEntityContent.contains("@ManyToOne"))
        assertFalse(childEntityContent.contains("import jakarta.persistence.ManyToOne"))
        assertFalse(childEntityContent.contains("lateinit var videoPost: VideoPost"))
        assertFalse(oneChildEntityContent.contains("videoPostId"))
        assertFalse(oneChildEntityContent.contains("@ManyToOne"))
        assertFalse(oneChildEntityContent.contains("import jakarta.persistence.ManyToOne"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate fails fast when parent table has no parent ref`() {
        val result = runCap4kGenerateWithSchema(
            """
            create table video_post (id bigint primary key comment '@Managed=identifier.database-identity;');
            create table video_post_item (id bigint primary key comment '@Managed=identifier.database-identity;', video_post_id bigint not null);
            comment on table video_post_item is '@Parent=video_post;';
            """.trimIndent()
        )

        assertFalse(result.success)
        assertTrue(
            result.output.contains("table VIDEO_POST_ITEM declares @Parent=video_post but has no @ParentRef column."),
            result.output,
        )
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate rejects removed db annotations through generic path`() {
        val tableResult = runCap4kGenerateWithSchema(
            """
            create table video_post (
                id bigint primary key comment '@Managed=identifier.database-identity;',
                version bigint,
                deleted boolean
            );
            comment on table video_post is '@AggregateRoot=true;';
            """.trimIndent()
        )
        val columnResult = runCap4kGenerateWithSchema(
            """
            create table video_post (
                id bigint primary key comment '@Managed=identifier.database-identity;',
                version bigint,
                deleted boolean
            );
            comment on column video_post.version is '@Version;';
            comment on column video_post.deleted is '@Deleted;';
            """.trimIndent()
        )

        assertFalse(tableResult.success)
        assertFalse(columnResult.success)
        assertTrue(tableResult.output.contains("unsupported table annotation @AggregateRoot"), tableResult.output)
        assertTrue(
            columnResult.output.contains("unsupported column annotation @Version") ||
                columnResult.output.contains("unsupported column annotation @Deleted"),
            columnResult.output,
        )
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `aggregate managed persistence fields remain declared in generated entity`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-persistence-generate")
        copyFixture(projectDir, "aggregate-persistence-sample")
        val domainBuildFile = projectDir.resolve("demo-domain/build.gradle.kts").readText().trim()
        val applicationBuildFile = projectDir.resolve("demo-application/build.gradle.kts").readText().trim()
        val adapterBuildFile = projectDir.resolve("demo-adapter/build.gradle.kts").readText().trim()

        val result = FunctionalFixtureSupport.runner(projectDir)
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
        assertTrue(generatedEntity.contains("@Column(name = \"title\")"))
        assertTrue(generatedEntity.contains("createdBy"))
        assertTrue(generatedEntity.contains("updatedBy"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `aggregate provider specific persistence generation renders bounded controls`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-provider-persistence-generate")
        copyFixture(projectDir, "aggregate-provider-persistence-sample")
        val domainBuildFile = projectDir.resolve("demo-domain/build.gradle.kts").readText().trim()
        val applicationBuildFile = projectDir.resolve("demo-application/build.gradle.kts").readText().trim()
        val adapterBuildFile = projectDir.resolve("demo-adapter/build.gradle.kts").readText().trim()
        val fixtureBuildFile = projectDir.resolve("build.gradle.kts")

        val planResult = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kPlan")
            .build()
        fixtureBuildFile.writeText(fixtureBuildFile.readText().replace("h2/demo", "h2/generate"))

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kGenerate")
            .build()

        data class ApplicationSideCell(
            val tableName: String,
            val entityName: String,
            val backingType: String,
            val deletedProperty: String,
            val activeSqlLiteral: String,
            val strategy: String,
        ) {
            val packageName: String = "com.acme.demo.domain.aggregates.$tableName"
            val idType: String = "${entityName}Id"
            val factoryType: String = "${entityName}Factory"
        }

        val nilUuid = "00000000-0000-0000-0000-000000000000"
        val applicationSideCells = listOf(
            ApplicationSideCell(
                tableName = "uuid_string_record",
                entityName = "UuidStringRecord",
                backingType = "String",
                deletedProperty = "var deleted: String = \"$nilUuid\"",
                activeSqlLiteral = "'$nilUuid'",
                strategy = "uuid7",
            ),
            ApplicationSideCell(
                tableName = "uuid_native_record",
                entityName = "UuidNativeRecord",
                backingType = "UUID",
                deletedProperty = "var deleted: UUID = UUID(0L, 0L)",
                activeSqlLiteral = "CAST('$nilUuid' AS UUID)",
                strategy = "uuid7",
            ),
        )

        val generatedVideoPost = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        ).readText()
        val generatedEntities = applicationSideCells.associateWith { cell ->
            projectDir.generatedFile(
                generatedSource(
                    "demo-domain/src/main/kotlin/${cell.packageName.replace('.', '/')}/${cell.entityName}.kt"
                )
            ).readText()
        }
        val generatedStrongIds = applicationSideCells.associateWith { cell ->
            projectDir.generatedFile(
                generatedSource(
                    "demo-domain/src/main/kotlin/${cell.packageName.replace('.', '/')}/${cell.idType}.kt"
                )
            ).readText()
        }
        val generatedCatalog = projectDir.generatedFile(
            generatedSource(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/managed/ManagedFieldCatalogContribution.kt"
            )
        ).readText()
        val generatedFactories = applicationSideCells.associateWith { cell ->
            projectDir.generatedFile(
                "demo-domain/src/main/kotlin/${cell.packageName.replace('.', '/')}/factory/${cell.factoryType}.kt"
            ).readText()
        }
        val generatedIdentityFactory = projectDir.generatedFile(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt"
        ).readText()
        assertTrue(domainBuildFile == "// Functional fixture module.")
        assertTrue(applicationBuildFile == "// Functional fixture module.")
        assertTrue(adapterBuildFile == "// Functional fixture module.")
        assertTrue(planResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertFalse(generatedVideoPost.contains("@DynamicInsert"))
        assertFalse(generatedVideoPost.contains("@DynamicUpdate"))
        assertTrue(generatedVideoPost.contains("import org.hibernate.annotations.SQLDelete"))
        assertTrue(generatedVideoPost.contains("import org.hibernate.annotations.Where"))
        assertTrue(generatedVideoPost.contains("""@SQLDelete(sql = "update `video_post` set `deleted` = `id` where `id` = ? and `version` = ?")"""))
        assertTrue(generatedVideoPost.contains("""@Where(clause = "`deleted` = 0")"""))
        assertTrue(generatedVideoPost.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
        assertTrue(generatedVideoPost.contains("@Version"))
        assertTrue(
            generatedVideoPost.contains(
                "@org.hibernate.annotations.Generated(event = [org.hibernate.generator.EventType.INSERT, " +
                    "org.hibernate.generator.EventType.UPDATE])"
            )
        )
        assertTrue(
            generatedVideoPost.contains(
                "@Column(name = \"`db_updated_at`\", insertable = false, updatable = false)"
            )
        )
        assertFalse(internalConstructorParameters(generatedVideoPost).contains("dbUpdatedAt"))
        assertFalse(internalConstructorParameters(generatedVideoPost).contains("id"))
        assertFalse(internalConstructorParameters(generatedVideoPost).contains("version"))
        assertTrue(generatedVideoPost.contains("var id: Long? = null"))
        assertTrue(generatedVideoPost.contains("var version: Long? = null"))
        assertTrue(generatedVideoPost.contains("var deleted: Long = 0L"))
        assertFalse(internalConstructorParameters(generatedVideoPost).contains("deleted"))
        assertFalse(generatedVideoPost.contains("@GenericGenerator"))

        applicationSideCells.forEach { cell ->
            val entity = generatedEntities.getValue(cell)
            val strongId = generatedStrongIds.getValue(cell)
            val factory = generatedFactories.getValue(cell)
            val constructorParameters = internalConstructorParameters(entity)
            val allocationBackingType = when (cell.backingType) {
                "UUID" -> "java.util.UUID"
                else -> cell.backingType
            }

            assertTrue(entity.contains("@EmbeddedId"), cell.entityName)
            assertGeneratedOwnIdShape(entity, cell.idType)
            assertFalse(Regex("""\bid\s*:""").containsMatchIn(constructorParameters), cell.entityName)
            assertFalse(constructorParameters.contains("deleted"), cell.entityName)
            assertTrue(entity.contains(cell.deletedProperty), cell.entityName)
            assertFalse(entity.contains("var deleted: ${cell.idType}"), cell.entityName)
            assertTrue(
                entity.contains(
                    """@SQLDelete(sql = "update `${cell.tableName}` set `deleted` = `id` where `id` = ?")"""
                ),
                cell.entityName,
            )
            assertTrue(
                entity.contains("""@Where(clause = "`deleted` = ${cell.activeSqlLiteral}")"""),
                cell.entityName,
            )
            assertFalse(entity.contains("and `version` = ?"), cell.entityName)
            assertFalse(entity.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"), cell.entityName)
            assertTrue(strongId.contains("StrongId<${cell.backingType}>"), cell.idType)
            assertTrue(
                generatedCatalog.contains(
                    "Mediator.identifiers.next(\"${cell.strategy}\", $allocationBackingType::class)"
                ),
                cell.entityName,
            )
            assertTrue(
                generatedCatalog.contains("${cell.idType}.of("),
                cell.entityName,
            )
            assertTrue(
                generatedCatalog.contains("entityType = ${cell.packageName}.${cell.entityName}::class"),
                cell.entityName,
            )
            assertTrue(factory.contains("${cell.entityName}("), cell.factoryType)
            assertTrue(factory.contains("title = entityPayload.title"), cell.factoryType)
            assertTrue(factory.contains("val title: String"), cell.factoryType)
            assertFalse(factory.contains("TODO(\"Implement aggregate construction\")"), cell.factoryType)
            assertFalse(factory.contains("deleted"), cell.factoryType)
            assertFalse(factory.contains("val id:"), cell.factoryType)
            assertFalse(factory.contains(cell.idType), cell.factoryType)
        }

        assertFalse(generatedIdentityFactory.contains("TODO(\"Implement aggregate construction\")"))
        assertFalse(generatedIdentityFactory.contains("deleted"))
        assertTrue(generatedCatalog.contains("class ManagedFieldCatalogContribution : ManagedFieldCatalog"))
        assertFalse(generatedCatalog.contains("GeneratedOwnIdAccessor"))

        val allGeneratedEvidence = buildString {
            append(generatedVideoPost)
            generatedEntities.values.forEach { append(it) }
            generatedStrongIds.values.forEach { append(it) }
            append(generatedCatalog)
            generatedFactories.values.forEach { append(it) }
            append(generatedIdentityFactory)
        }
        assertFalse(allGeneratedEvidence.contains("ApplicationSideId"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `aggregate provider persistence generation keeps provider identity id policies`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-provider-persistence-mixed-id-generate")
        copyFixture(projectDir, "aggregate-provider-persistence-sample")
        val schemaFile = projectDir.resolve("schema.sql")
        schemaFile.writeText(
            schemaFile.readText().replaceFirst(
                "id bigint primary key comment '@Managed=identifier.database-identity;',",
                "id varchar(36) primary key comment '@Managed=identifier.uuid7;',",
            ).replaceFirst("@Managed=soft-delete;", "") +
                "\n\n" +
                """
                create table audit_log (
                    id bigint primary key comment '@Managed=identifier.database-identity;',
                    deleted bigint not null default 0 comment '@Managed=soft-delete;',
                    content varchar(128) not null
                );
                """.trimIndent()
        )
        val buildFile = projectDir.resolve("build.gradle.kts")
        val patchedBuildFile = buildFile.readText().replace("\r\n", "\n")
            .replace(
                """
                |                    "uuid_native_record",
                |                )
                """.trimMargin(),
                """
                |                    "uuid_native_record",
                |                    "audit_log",
                |                )
                """.trimMargin(),
            )
            .replace(
                """
                |    generators {
                """.trimMargin(),
                """
                |    managedFields {
                |        identifierDefaultPolicy.set("identifier.database-identity")
                |    }
                |    generators {
                """.trimMargin(),
            )
        buildFile.writeText(patchedBuildFile)
        assertTrue(patchedBuildFile.contains("""identifierDefaultPolicy.set("identifier.database-identity")"""))
        assertTrue(patchedBuildFile.contains("\"audit_log\""))

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kGenerate")
            .build()
        val generatedVideoPost = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        ).readText()
        val generatedAuditLog = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/audit_log/AuditLog.kt")
        ).readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertFalse(generatedVideoPost.contains("ApplicationSideId"))
        assertFalse(generatedVideoPost.contains("id: Long = 0L"))
        assertFalse(generatedVideoPost.contains("@GeneratedValue(generator ="))
        assertFalse(generatedVideoPost.contains("@GenericGenerator"))
        assertTrue(generatedVideoPost.contains("import com.acme.demo.domain.aggregates.video_post.VideoPostId"))
        assertTrue(generatedVideoPost.contains("@EmbeddedId"))
        assertGeneratedOwnIdShape(generatedVideoPost, "VideoPostId")
        assertFalse(generatedVideoPost.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
        assertTrue(generatedAuditLog.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
        assertFalse(generatedAuditLog.contains("GenericGenerator"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `aggregate provider persistence generation keeps native uuid ids without save-time assignment`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-provider-persistence-uuid-id-generate")
        copyFixture(projectDir, "aggregate-provider-persistence-sample")
        val schemaFile = projectDir.resolve("schema.sql")
        schemaFile.writeText(
            schemaFile.readText().replaceFirst(
                "id bigint primary key comment '@Managed=identifier.database-identity;',",
                "id varchar(36) primary key comment '@Managed=identifier.uuid7;',",
            ).replaceFirst("@Managed=soft-delete;", "")
        )

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kGenerate")
            .build()
        val generatedVideoPost = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        ).readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertFalse(generatedVideoPost.contains("ApplicationSideId"))
        assertFalse(generatedVideoPost.contains("UUID(" + "0L, 0L)"))
        assertTrue(generatedVideoPost.contains("import com.acme.demo.domain.aggregates.video_post.VideoPostId"))
        assertTrue(generatedVideoPost.contains("@EmbeddedId"))
        assertGeneratedOwnIdShape(generatedVideoPost, "VideoPostId")
        assertFalse(generatedVideoPost.contains("id: UUID"))
        assertFalse(generatedVideoPost.contains("@GeneratedValue(generator ="))
        assertFalse(generatedVideoPost.contains("@GenericGenerator"))
        assertFalse(generatedVideoPost.contains("@SQLDelete"))
        assertFalse(generatedVideoPost.contains("@Where"))
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
                id bigint primary key comment '@Managed=identifier.uuid7;',
                title varchar(128) not null
            );

            create table audit_log (
                id bigint primary key comment '@Managed=identifier.database-identity;',
                deleted bigint not null default 0 comment '@Managed=soft-delete;',
                content varchar(128) not null
            );
                        """.trimIndent()
        )
        val buildFile = projectDir.resolve("build.gradle.kts")
        val patchedBuildFile = buildFile.readText().replace(
            Regex(
                """includeTables\.set\(\s*listOf\(\s*"video_post",\s*"uuid_string_record",\s*"uuid_native_record",\s*\)\s*\)"""
            ),
            "includeTables.set(listOf(\"video\", \"audit_log\"))",
        )
        buildFile.writeText(patchedBuildFile)
        assertTrue(patchedBuildFile.contains("includeTables.set(listOf(\"video\", \"audit_log\"))"))

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kGenerate")
            .buildAndFail()

        assertTrue(
            result.output.contains(
                "unsupported UUID7 storage for video.id: jdbcType=-5, dbType=BIGINT, kotlinType=Long, columnSize=64"
            ),
            result.output,
        )
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `no addon means no enum translation artifacts`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-enum")
        copyFixture(projectDir, "aggregate-enum-sample")

        val planResult = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kPlan")
            .build()
        val generateResult = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kGenerate")
            .build()
        val planFile = projectDir.resolve("build/cap4k/plan.json")
        val generatedEntity = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        ).readText()

        assertTrue(planResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planFile.toFile().exists())
        val planContent = planFile.readText()
        assertTrue(planContent.contains("\"templateId\": \"aggregate/enum.kt.peb\""))
        assertFalse(planContent.contains("Translation.kt"))
        assertFalse(planContent.contains("only-engine-enum-translation"))
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
        assertTrue(generatedEntity.contains("@Entity"))
        assertTrue(generatedEntity.contains("@Table(name = \"video_post\")"))
        assertTrue(generatedEntity.contains("import com.acme.demo.domain.aggregates.video_post.VideoPostId"))
        assertTrue(generatedEntity.contains("import com.acme.demo.domain.shared.enums.Status"))
        assertTrue(generatedEntity.contains("@EmbeddedId"))
        assertGeneratedOwnIdShape(generatedEntity, "VideoPostId")
        assertFalse(generatedEntity.contains("@Id"))
        assertFalse(generatedEntity.contains("@Column(name = \"id\""))
        assertTrue(generatedEntity.contains("@Column(name = \"status\")"))
        assertTrue(
            generatedEntity.contains(
                "@Convert(converter = Status.Converter::class)"
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
        assertTrue(generatedEntity.contains("var status: Status = status"))
        assertFalse(generatedEntity.contains("class Status("))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kGenerate renders addon artifacts through normal plan semantics`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-addon")
        copyFixture(projectDir, "aggregate-enum-sample")
        functionalAddonJar(projectDir)

        val templateId = "addons/functional-test-addon/aggregate/addon_marker.kt.peb"

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent
                .replace(
                    """
                    |plugins {
                    |    id("io.github.ldmoxeii.cap4k.pipeline")
                    |}
                    """.trimMargin(),
                    """
                    |plugins {
                    |    id("io.github.ldmoxeii.cap4k.pipeline")
                    |}
                    |
                    |dependencies {
                    |    cap4kPipelineExtension(files("local-addons/functional-test-addon.jar"))
                    |}
                    """.trimMargin(),
                )
                .replace(
                    """
                    |    generators {
                    """.trimMargin(),
                    """
                    |    templates {
                    |        templateConflictPolicies.put("$templateId", "OVERWRITE")
                    |    }
                    |    generators {
                    """.trimMargin(),
                )
        )

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kPlan", "cap4kGenerate")
            .build()

        val planContent = projectDir.resolve("build/cap4k/plan.json").readText()
        val generatedAddonArtifact = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/addon/AddonGeneratedMarker.kt"
        )

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planContent.contains("functional-test-addon"))
        assertTrue(planContent.contains(templateId))
        assertPlanItemMetadata(
            planContent = planContent,
            templateId = templateId,
            outputPathSuffix = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/addon/AddonGeneratedMarker.kt",
            outputKind = "CHECKED_IN_SOURCE",
            resolvedOutputRoot = "demo-adapter/src/main/kotlin",
            conflictPolicy = "SKIP",
        )
        assertTrue(generatedAddonArtifact.toFile().exists())
        assertTrue(generatedAddonArtifact.readText().contains("val source: String = \"addon-jar\""))

        val overrideTemplate = projectDir.resolve("cap4k-templates").resolve(templateId)
        Files.createDirectories(overrideTemplate.parent)
        overrideTemplate.writeText(
            """
            |package {{ packageName }}
            |
            |class {{ typeName }} {
            |    val source: String = "project-override"
            |}
            """.trimMargin()
        )
        buildFile.writeText(
            buildFile.readText().replace(
                """
                |    templates {
                """.trimMargin(),
                """
                |    templates {
                |        overrideDirs.from("cap4k-templates")
                """.trimMargin(),
            )
        )

        val overrideResult = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kGenerate")
            .build()

        assertTrue(overrideResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedAddonArtifact.readText().contains("val source: String = \"addon-jar\""))
        assertFalse(generatedAddonArtifact.readText().contains("val source: String = \"project-override\""))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan skips unsupported tables when aggregate policy is skip`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-skip")
        copyFixture(projectDir, "aggregate-policy-sample")

        val result = FunctionalFixtureSupport.runner(projectDir)
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
    fun `cap4kPlan includes managed field defaults and resolved policies`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-managed-field-plan")
        copyFixture(projectDir, "aggregate-provider-persistence-sample")

        val schemaFile = projectDir.resolve("schema.sql")
        schemaFile.writeText(
            schemaFile.readText().replaceFirst(
                "id bigint primary key comment '@Managed=identifier.database-identity;',",
                "id bigint primary key comment '@Managed=identifier.database-identity;',",
            ).replaceFirst(
                "title varchar(128) not null",
                "created_by varchar(64) not null,\n    title varchar(128) not null",
            ) +
                "\n\n" +
                """
                create table audit_log (
                    id bigint primary key comment '@Managed=identifier.database-identity;',
                    deleted bigint not null default 0 comment '@Managed=soft-delete;',
                    content varchar(128) not null
                );
                """.trimIndent()
        )

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        val patchedBuildFile = buildFileContent
            .replace(
                """
                |                    "uuid_native_record",
                |                )
                """.trimMargin(),
                """
                |                    "uuid_native_record",
                |                    "audit_log",
                |                )
                """.trimMargin(),
            )
            .replace(
                """
                |    generators {
                """.trimMargin(),
                """
                |    managedFields {
                |        columnPolicyDefaults.put(" created_by ", "database.generated-always")
                |    }
                |    generators {
                """.trimMargin(),
            )
        buildFile.writeText(patchedBuildFile)
        assertTrue(patchedBuildFile.contains("\"audit_log\""))

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kPlan")
            .build()

        val planJson = projectDir.resolve("build/cap4k/plan.json").readText()
        val planObject = jsonMapper.readTree(planJson).requireObjectNode()
        val defaults = planObject.requireObjectNode("managedFieldDefaults")
        val resolvedPoliciesArray = planObject.requireArrayNode("managedFieldPolicies")
        val firstResolvedPolicy = resolvedPoliciesArray.first().requireObjectNode()
        val resolvedPolicies = resolvedPoliciesArray
            .map { it.requireObjectNode() }
            .associateBy { it.get("tableName").asText() }
        val videoPostPolicy = resolvedPolicies.getValue("video_post")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertFalse(planObject.has("aggregateIdPolicy"))
        assertEquals("identifier.uuid7", defaults.get("identifierDefaultPolicy").asText())
        assertEquals(
            "database.generated-always",
            defaults.requireObjectNode("columnPolicyDefaults").get("created_by").asText(),
        )
        assertEquals(4, resolvedPolicies.size)
        assertEquals(
            setOf(
                "video_post",
                "uuid_string_record",
                "uuid_native_record",
                "audit_log",
            ),
            resolvedPolicies.keys,
        )
        assertTrue(firstResolvedPolicy.has("fields"))
        assertTrue(firstResolvedPolicy.requireArrayNode("fields").size() > 0)
        assertTrue(firstResolvedPolicy.has("writeSurface"))
        assertTrue(firstResolvedPolicy.requireObjectNode("writeSurface").has("createAllowedFields"))
        assertTrue(firstResolvedPolicy.requireObjectNode("writeSurface").has("updateAllowedFields"))
        assertEquals("identifier.database-identity", videoPostPolicy.requireArrayNode("fields").single {
            it.requireObjectNode().get("columnName").asText() == "id"
        }.requireObjectNode().get("policyKey").asText())
        assertEquals("soft-delete", videoPostPolicy.requireArrayNode("fields").single {
            it.requireObjectNode().get("columnName").asText() == "deleted"
        }.requireObjectNode().get("policyKey").asText())
        assertEquals("version", videoPostPolicy.requireArrayNode("fields").single {
            it.requireObjectNode().get("columnName").asText() == "version"
        }.requireObjectNode().get("policyKey").asText())
        assertEquals(
            setOf("id", "deleted", "version", "db_updated_at", "created_by"),
            videoPostPolicy.requireArrayNode("fields").map {
                it.requireObjectNode().get("columnName").asText()
            }.toSet(),
        )
        assertEquals(
            listOf("title"),
            videoPostPolicy.requireObjectNode("writeSurface").requireArrayNode("createAllowedFields").map { it.asText() }
        )
        assertEquals(
            listOf("title"),
            videoPostPolicy.requireObjectNode("writeSurface").requireArrayNode("updateAllowedFields").map { it.asText() }
        )
        assertEquals("database.generated-always", videoPostPolicy.requireArrayNode("fields").single {
            it.requireObjectNode().get("columnName").asText() == "created_by"
        }.requireObjectNode().get("policyKey").asText())
        val auditLogPolicy = resolvedPolicies.getValue("audit_log")
        assertEquals("identifier.database-identity", auditLogPolicy.requireArrayNode("fields").single {
            it.requireObjectNode().get("columnName").asText() == "id"
        }.requireObjectNode().get("policyKey").asText())
        assertTrue(auditLogPolicy.requireArrayNode("fields").none {
            it.requireObjectNode().get("role").asText() == "VERSION"
        })
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

        val result = FunctionalFixtureSupport.runner(projectDir)
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

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(
            result.output.contains(
                "project.domainModulePath, project.applicationModulePath, and project.adapterModulePath are required when aggregate is configured."
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
                    |    sources {
                    """.trimMargin(),
                    """
                    |    sources {
                    |        db {
                    |            enabled.set(true)
                    |            schema.set("PUBLIC")
                    |        }
                    """.trimMargin()
                )
        )

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(
            result.output.contains(
                "sources.db.url is required when db is enabled."
            )
        )
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

        val planFile = projectDir.resolve("build/cap4k/plan.json").toFile()

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kPlan")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planFile.exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kAnalysisPlan and cap4kAnalysisGenerate produce flow artifacts from ir analysis fixture`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-analysis-flow")
        copyFixture(projectDir, "flow-sample")

        val result = FunctionalFixtureSupport.runner(projectDir)
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
    fun `cap4kAnalysisGenerate keeps the complete inbound causal chain in one flow`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-analysis-flow-causal-chain")
        copyFixture(projectDir, "flow-causal-chain-sample")

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kAnalysisPlan", "cap4kAnalysisGenerate")
            .build()

        val entryFile = projectDir.resolve("flows/MediaProcessingCompletedIntegrationEvent.json")
        val mermaidFile = projectDir.resolve("flows/MediaProcessingCompletedIntegrationEvent.mmd")
        val indexFile = projectDir.resolve("flows/index.json")
        val entry = jsonMapper.readTree(entryFile.toFile()).requireObjectNode()
        val index = jsonMapper.readTree(indexFile.toFile()).requireObjectNode()
        val visibleNodeIds = entry.requireArrayNode("nodes")
            .map { node -> node.get("id").asText() }
            .toSet()
        val visibleNodeTypes = entry.requireArrayNode("nodes")
            .map { node -> node.get("type").asText() }
            .toSet()
        val edgeTypes = entry.requireArrayNode("edges")
            .map { edge -> edge.get("type").asText() }
            .toSet()
        val mermaid = mermaidFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(projectDir.resolve("build/cap4k/analysis-plan.json").toFile().exists())
        assertTrue(entryFile.toFile().exists())
        assertTrue(mermaidFile.toFile().exists())
        assertTrue(indexFile.toFile().exists())
        assertEquals("MediaProcessingCompletedIntegrationEvent", entry.get("entryId").asText())
        assertEquals("integrationevent", entry.get("entryType").asText())
        assertEquals(4, entry.get("nodeCount").asInt())
        assertEquals(3, entry.get("edgeCount").asInt())
        assertEquals(
            setOf(
                "MediaProcessingCompletedIntegrationEvent",
                "RecordMediaProcessingCmd",
                "MediaProcessingRecorded",
                "PublishContentCmd",
            ),
            visibleNodeIds,
        )
        assertEquals(setOf("integrationevent", "command", "domainevent"), visibleNodeTypes)
        assertEquals(
            setOf("IntegrationEventToCommand", "CommandToDomainEvent", "DomainEventToCommand"),
            edgeTypes,
        )
        assertEquals(1, index.get("flowCount").asInt())
        assertEquals(1, index.get("entryTypeCounts").get("integrationevent").asInt())
        assertEquals(
            "MediaProcessingCompletedIntegrationEvent.json",
            index.requireArrayNode("flows").single().get("json").asText(),
        )
        assertTrue(mermaid.contains("MediaProcessingCompletedIntegrationEvent"))
        assertTrue(mermaid.contains("PublishContentCmd"))
        assertFalse(mermaid.contains("Handler"))
        assertFalse(mermaid.contains("MediaProcessing::"))
        assertFalse(projectDir.resolve("flows/process.json").toFile().exists())
        assertFalse(projectDir.resolve("flows/process-index.json").toFile().exists())
        assertFalse(projectDir.resolve("process").toFile().exists())
        assertFalse(result.output.contains("cap4kFlow"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kAnalysisPlan consumes graph even when aggregate structure metadata is incomplete`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-analysis-flow-missing-metadata")
        copyFixture(projectDir, "flow-sample")
        val nodesFile = projectDir.resolve("analysis/app/build/cap4k-code-analysis/nodes.json")
        val nodes = jsonMapper.readTree(nodesFile.toFile()).requireArrayNode()
        nodes.add(
            jsonMapper.createObjectNode().apply {
                put("id", "demo.domain.aggregates.order.Order")
                put("name", "Order")
                put("fullName", "demo.domain.aggregates.order.Order")
                put("type", "aggregate")
                putArray("missingMetadata")
                    .add("com.only4.cap4k.analysis.metadata.AggregateElementMetadata")
            }
        )
        nodesFile.writeText(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(nodes))

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kAnalysisPlan")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(projectDir.resolve("build/cap4k/analysis-plan.json").toFile().exists())
        assertTrue(projectDir.resolve("build/cap4k/analysis-plan.json").readText().contains("\"templateId\": \"flow/index.json.peb\""))
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

        val result = FunctionalFixtureSupport.runner(projectDir)
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
            )
        )

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kPlan", "cap4kGenerate")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertFalse(projectDir.resolve("flows/index.json").toFile().exists())
        assertFalse(projectDir.resolve("design/drawing_board_capability.json").toFile().exists())
        assertFalse(projectDir.resolve("design/drawing_board_command.json").toFile().exists())
        assertFalse(projectDir.resolve("build/cap4k/analysis-plan.json").toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kAnalysisPlan and cap4kAnalysisGenerate produce drawing board artifacts from ir analysis fixture`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-analysis-drawing-board")
        copyFixture(projectDir, "drawing-board-sample")

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kAnalysisPlan", "cap4kAnalysisGenerate")
            .build()

        val analysisPlanFile = projectDir.resolve("build/cap4k/analysis-plan.json")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(analysisPlanFile.toFile().exists())
        assertTrue(analysisPlanFile.readText().contains("\"templateId\": \"drawing-board/document.json.peb\""))
        assertTrue(projectDir.resolve("design/drawing_board_capability.json").toFile().exists())
        assertTrue(projectDir.resolve("design/drawing_board_command.json").toFile().exists())
        val queryContent = projectDir.resolve("design/drawing_board_query.json").readText()
        val payloadContent = projectDir.resolve("design/drawing_board_api_payload.json").readText()
        assertTrue(queryContent.contains("\"family\": \"query\""))
        assertTrue(queryContent.contains("\"variant\": \"page\""))
        assertTrue(payloadContent.contains("\"family\": \"api-payload\""))
        assertTrue(payloadContent.contains("\"variant\": \"page\""))
        assertFalse(queryContent.contains("\"traits\""))
        assertFalse(payloadContent.contains("\"traits\""))
        val domainEventFile = projectDir.resolve("design/drawing_board_domain_event.json")
        assertTrue(domainEventFile.toFile().exists())
        val domainEventContent = domainEventFile.readText()
        assertTrue(domainEventContent.contains("\"tag\": \"domain_event\""))
        assertTrue(domainEventContent.contains("\"name\": \"reason\""))
        assertFalse(domainEventContent.contains("\"name\": \"entity\""))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kAnalysisGenerate alone rejects incomplete drawing board metadata without replacing outputs`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-analysis-drawing-missing-metadata")
        copyFixture(projectDir, "drawing-board-sample")
        val nodesFile = projectDir.resolve("analysis/app/build/cap4k-code-analysis/nodes.json")
        nodesFile.writeText(
            """
            [
              {
                "id": "SubmitOrderCmd",
                "name": "SubmitOrderCmd",
                "fullName": "com.acme.demo.application.commands.SubmitOrderCmd",
                "type": "command",
                "missingMetadata": ["com.only4.cap4k.analysis.metadata.DesignBlockMetadata"]
              }
            ]
            """.trimIndent()
        )

        val outputDir = projectDir.resolve("design")
        Files.createDirectories(outputDir)
        val sentinel = outputDir.resolve("sentinel.txt")
        sentinel.writeText("unchanged")

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kAnalysisGenerate")
            .buildAndFail()

        assertTrue(result.output.contains("com.acme.demo.application.commands.SubmitOrderCmd"))
        assertTrue(result.output.contains("DesignBlockMetadata"))
        assertTrue(result.output.contains("Analyzer partition 'designProjection' is invalid"))
        assertTrue(result.output.contains("compileOnly classpath"))
        assertFalse(projectDir.resolve("design/drawing_board_command.json").toFile().exists())
        assertEquals("unchanged", sentinel.readText())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kAnalysisPlan depends on compileKotlin when flow input is produced during compilation`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-flow-compile")
        copyFixture(projectDir, "flow-compile-sample")

        val result = FunctionalFixtureSupport.runner(projectDir)
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

        val result = FunctionalFixtureSupport.runner(projectDir)
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

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kAnalysisPlan")
            .buildAndFail()

        assertTrue(result.output.contains("Analyzer partition 'graph' is invalid: Required rels.json is missing."))
        assertFalse(projectDir.resolve("build/cap4k/analysis-plan.json").toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan api payload flow emits design api payload template`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-api-payload-plan")
        copyFixture(projectDir, "design-api-payload-sample")

        val result = FunctionalFixtureSupport.runner(projectDir)
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

        val result = FunctionalFixtureSupport.runner(projectDir)
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

        val result = FunctionalFixtureSupport.runner(projectDir)
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
    fun `cap4kPlan domain event flow emits domain event and domain event handler templates`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-domain-event-plan")
        copyFixture(projectDir, "design-domain-event-sample")

        val result = FunctionalFixtureSupport.runner(projectDir)
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
        val designEntries = jsonMapper.readTree(designFile.readText()).requireArrayNode()
        designEntries.single().requireObjectNode().put("description", "order */ \"created\" \\event ${'$'}status")
        designFile.writeText(designEntries.toString())

        val result = FunctionalFixtureSupport.runner(projectDir)
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
        assertTrue(eventContent.contains("value = \"order.created\""))
        assertTrue(eventContent.contains("persist = true"))
        assertTrue(eventContent.contains("import com.only4.cap4k.analysis.metadata.DesignBlockMetadata"))
        assertTrue(eventContent.contains("@DesignBlockMetadata("))
        assertTrue(eventContent.contains("tag = \"domain_event\""))
        assertTrue(eventContent.contains("name = \"OrderCreated\""))
        assertTrue(eventContent.contains("packageName = \"order\""))
        assertTrue(eventContent.contains("description = \"order */ \\\"created\\\" \\\\event \\${'$'}status\""))
        assertTrue(eventContent.contains("aggregates = [\"Order\"]"))
        assertTrue(eventContent.contains("eventName = \"order.created\""))
        assertTrue(eventContent.contains("family = \"domain-event\""))
        assertFalse(eventContent.contains("variant = \"\""))
        assertFalse(eventContent.contains(legacyAggregateCall))
        assertFalse(eventContent.contains(legacyAggregateAnnotationFq))
        assertTrue(eventContent.contains("* order * / \"created\" \\event ${'$'}status"))
        assertFalse(eventContent.contains("* order */ \"created\" \\event ${'$'}status"))
        assertFalse(eventContent.contains("&quot;"))
        assertFalse(eventContent.contains("import com.acme.demo.domain.aggregates.order.Order"))
        assertTrue(eventContent.contains("import java.util.UUID"))
        assertTrue(eventContent.contains("class OrderCreatedDomainEvent("))
        assertTrue(eventContent.contains("val reason: String"))
        assertFalse(eventContent.contains("val entity:"))
        assertTrue(eventContent.contains("data class Snapshot("))
        assertTrue(eventContent.contains("val traceId: UUID"))
        assertTrue(handlerContent.contains("@Service"))
        assertTrue(handlerContent.contains("@EventListener(OrderCreatedDomainEvent::class)"))
        assertTrue(handlerContent.contains("* order * / \"created\" \\event ${'$'}status"))
        assertFalse(handlerContent.contains("* order */ \"created\" \\event ${'$'}status"))
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

        val result = FunctionalFixtureSupport.runner(projectDir)
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

        val result = FunctionalFixtureSupport.runner(projectDir)
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

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(
            result.output.contains(
                "domain module is required"
            )
        )
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan domain event flow fails when domain subscriber misses application module path`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-domain-subscriber-no-application")
        copyFixture(projectDir, "design-domain-event-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                "        applicationModulePath.set(\"demo-application\")\n",
                "",
            )
        )

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(
            result.output.contains(
                "application module is required"
            )
        )
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan domain event flow succeeds when aggregate source data exists`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-domain-event-aggregate-source")
        copyFixture(projectDir, "design-domain-event-sample")
        val designFile = projectDir.resolve("design/design.json")
        designFile.writeText(designFile.readText().replace("\"Order\"", "\"VideoPost\""))
        projectDir.resolve("schema.sql").writeText(
            """
            create table if not exists video_post (
                id bigint primary key comment '@Managed=identifier.database-identity;',
                title varchar(255) not null
            );
            """.trimIndent()
        )

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                "includeTables.set(listOf(\"order\"))",
                "includeTables.set(listOf(\"video_post\"))",
            ) +
                """

                cap4k {
                    project {
                        applicationModulePath.set("demo-application")
                        adapterModulePath.set("demo-adapter")
                    }
                    managedFields {
                        identifierDefaultPolicy.set("identifier.database-identity")
                    }
                }
                """.trimIndent()
        )

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kPlan")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(projectDir.resolve("build/cap4k/plan.json").toFile().exists())
        assertTrue(projectDir.resolve("build/cap4k/plan.json").readText().contains("\"templateId\": \"design/domain_event.kt.peb\""))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kPlan domain event flow fails clearly when aggregate source data cannot resolve aggregate`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-domain-event-missing-aggregate-metadata")
        copyFixture(projectDir, "design-domain-event-sample")

        val buildFile = projectDir.resolve("build.gradle.kts")
        val buildFileContent = buildFile.readText().replace("\r\n", "\n")
        buildFile.writeText(
            buildFileContent.replace(
                "includeTables.set(listOf(\"order\"))",
                "includeTables.set(listOf(\"video_post\"))",
            )
        )

        val result = FunctionalFixtureSupport.runner(projectDir)
            .withArguments("cap4kPlan")
            .buildAndFail()

        assertTrue(result.output.contains("domain_event OrderCreated references missing aggregate metadata: Order"))
    }

    @Test
    fun `cap4kGenerate removes known only danmaku next generator bugs`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-known-bug-parity")
        copyCompileFixture(projectDir, "known-bug-parity-sample")

        val result = FunctionalFixtureSupport.runner(projectDir)
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
        val queryFile = projectDir.generatedFile(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/message/read/FindUserMessageQry.kt"
        )
        val commandFile = projectDir.generatedFile(
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/message/create/CreateUserMessageCmd.kt"
        )
        val capabilityFile = projectDir.generatedFile(
            "demo-application/src/main/kotlin/com/acme/demo/application/capabilities/message/delivery/PublishUserMessage.kt"
        )
        val capabilityHandlerFile = projectDir.generatedFile(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/capabilities/message/delivery/PublishUserMessageHandler.kt"
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
            queryFile,
            commandFile,
            capabilityFile,
            capabilityHandlerFile,
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
        assertTrue(repositoryContent.contains("import com.acme.demo.domain.aggregates.user_message.UserMessageId"))
        assertTrue(repositoryContent.contains("internal open class UserMessageJpaRepositoryAdapter("))
        assertTrue(repositoryContent.contains("entityManager: EntityManager"))
        assertTrue(repositoryContent.contains("UserMessage::class.java"))
        assertTrue(repositoryContent.contains("AbstractJpaRepository<UserMessage, UserMessageId>"))
        assertFalse(repositoryContent.contains("interface UserMessageRepository"))
        assertFalse(repositoryContent.contains("org.springframework.data.jpa.repository.JpaRepository"))
        assertFalse(repositoryContent.contains("JpaSpecificationExecutor"))

        val schemaContent = schemaFile.readText()
        assertTrue(schemaContent.contains("class SUserMessage("))
        assertTrue(schemaContent.contains("fun specify(builder: PredicateBuilder<SUserMessage>): Specification<UserMessage>"))
        assertTrue(schemaContent.contains("import com.only4.cap4k.ddd.domain.repo.schema.Field"))
        assertTrue(schemaContent.contains("import com.only4.cap4k.ddd.domain.repo.schema.PredicateBuilder"))
        assertTrue(schemaContent.contains("val messageKey: Field<String>"))
        assertFalse(schemaContent.contains("val message_key"))

        val queryContent = queryFile.readText()
        assertTrue(queryContent.contains(") : Query<Response>"))
        assertTrue(queryContent.replace("\r\n", "\n").contains(") : Query<Response>\n\n    data class Response("))

        val commandContent = commandFile.readText()
        assertTrue(commandContent.contains(") : Command<Response>"))
        assertTrue(commandContent.contains("class Handler : CommandHandler<Request, Response>"))
        assertTrue(commandContent.contains("override fun handle(command: Request)"))
        assertFalse(commandContent.contains("Mediator.uow.save()"))
        assertTrue(commandContent.replace("\r\n", "\n").contains(") : Command<Response>\n\n    data object Response"))

        val capabilityContent = capabilityFile.readText()
        assertTrue(capabilityContent.contains(") : CapabilityCall<Response>"))
        assertTrue(capabilityContent.replace("\r\n", "\n").contains(") : CapabilityCall<Response>\n\n    data class Response("))

        val capabilityHandlerContent = capabilityHandlerFile.readText().replace("\r\n", "\n")
        assertTrue(
            capabilityHandlerContent.contains(
                "        return PublishUserMessage.Response(\n" +
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
        assertTrue(domainEventContent.contains("class UserMessageCreatedDomainEvent("))
        assertTrue(domainEventContent.contains("val reason: String"))
        assertFalse(domainEventContent.contains("import com.acme.demo.domain.aggregates.user_message.UserMessage"))
        assertFalse(domainEventContent.contains("val entity:"))
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
        val item = jsonMapper.readTree(planContent)
            .requireObjectNode()
            .requireArrayNode("items")
            .map { it.requireObjectNode() }
            .single {
                it.get("templateId").asText() == templateId &&
                    it.get("outputPath").asText().endsWith(outputPathSuffix)
            }

        assertEquals(outputKind, item.get("outputKind").asText())
        assertEquals(resolvedOutputRoot, item.get("resolvedOutputRoot").asText())
        assertEquals(conflictPolicy, item.get("conflictPolicy").asText())
    }

    private fun functionalAddonJar(projectDir: Path): Path {
        val jar = projectDir.resolve("local-addons/functional-test-addon.jar")
        Files.createDirectories(jar.parent)
        JarOutputStream(Files.newOutputStream(jar)).use { output ->
            output.writeClassEntry(FunctionalTestPipelineExtensionProvider::class.java)
            output.writeClassEntry(FunctionalTestArtifactAddonProvider::class.java)
            output.writeTextEntry(
                "META-INF/services/com.only4.cap4k.plugin.pipeline.api.PipelineExtensionProvider",
                FunctionalTestPipelineExtensionProvider::class.java.name,
            )
            output.writeTextEntry(
                "cap4k/addons/functional-test-addon/aggregate/addon_marker.kt.peb",
                """
                |package {{ packageName }}
                |
                |class {{ typeName }} {
                |    val source: String = "addon-jar"
                |}
                """.trimMargin(),
            )
        }
        return jar
    }

    private fun JarOutputStream.writeClassEntry(type: Class<*>) {
        val path = type.name.replace('.', '/') + ".class"
        val bytes = requireNotNull(type.classLoader.getResourceAsStream(path)) {
            "provider class resource not found: $path"
        }.readBytes()
        writeBytesEntry(path, bytes)
    }

    private fun JarOutputStream.writeTextEntry(path: String, content: String) {
        writeBytesEntry(path, content.toByteArray(Charsets.UTF_8))
    }

    private fun JarOutputStream.writeBytesEntry(path: String, bytes: ByteArray) {
        putNextEntry(JarEntry(path))
        write(bytes)
        closeEntry()
    }

    private fun writeValueObjectProjectionOnlyBuild(projectDir: Path, includeManifestSource: Boolean) {
        val manifestBlock = if (includeManifestSource) {
            """
            types {
                valueObjectManifest {
                    files.from("design/value-objects.json")
                }
            }
            """.trimIndent()
        } else {
            ""
        }
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.ldmoxeii.cap4k.pipeline")
            }

            cap4k {
                project {
                    basePackage.set("com.acme.demo")
                    domainModulePath.set("demo-domain")
                }
                $manifestBlock
            }
            """.trimIndent()
        )
    }

    private fun generatedSource(relativePath: String): String =
        relativePath.replace("/src/main/kotlin/", "/build/generated/cap4k/main/kotlin/")

    private fun runCap4kGenerateWithSchema(schema: String): GenerateResult {
        val projectDir = Files.createTempDirectory("pipeline-functional-db-comment-contract")
        copyFixture(projectDir, "aggregate-relation-sample")
        projectDir.resolve("schema.sql").writeText(schema)
        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace(
                """includeTables.set(listOf("video_post", "video_post_item", "user_profile"))""",
                """includeTables.set(listOf("video_post", "video_post_item"))""",
            )
        )

        val result = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .buildAndFail()

        return GenerateResult(success = false, output = result.output)
    }

    private data class GenerateResult(
        val success: Boolean,
        val output: String,
    )

    private fun assertAggregateElementContent(
        content: String,
        aggregate: String,
        name: String,
        packageName: String,
        type: String,
        root: Boolean,
    ) {
        assertTrue(content.contains("import com.only4.cap4k.analysis.metadata.AggregateElementMetadata"))
        assertTrue(content.contains("@AggregateElementMetadata("))
        assertTrue(content.contains("aggregate = \"$aggregate\""))
        assertTrue(content.contains("name = \"$name\""))
        assertTrue(content.contains("packageName = \"$packageName\""))
        assertTrue(content.contains("type = \"$type\""))
        assertTrue(content.contains("root = $root"))
    }

    private fun assertBuildingBlockSource(
        content: String,
        family: String,
        variant: String,
    ) {
        assertTrue(content.contains("import com.only4.cap4k.analysis.metadata.DesignBlockMetadata"))
        assertTrue(content.contains("@DesignBlockMetadata("))
        assertTrue(content.contains("family = \"$family\""))
        assertFalse(content.contains("eventName = "))
        if (variant.isBlank()) {
            assertFalse(content.contains("variant = \"\""))
        } else {
            assertTrue(content.contains("variant = \"$variant\""))
        }
        assertFalse(content.contains(legacyAggregateCall))
        assertFalse(content.contains(legacyAggregateAnnotationFq))
    }

    private fun Path.appendTemplateOverrideBlock() {
        writeText(
            readText().replace("\r\n", "\n") +
                """

                cap4k {
                    templates {
                        overrideDirs.from("codegen/templates")
                    }
                }
                """.trimIndent()
        )
    }

    private fun assertGeneratedOwnIdShape(generatedEntity: String, idType: String) {
        val normalizedEntity = generatedEntity.replace("\r\n", "\n")
        val expectedPropertyBlock = "    lateinit var id: $idType\n        internal set"
        assertTrue(
            normalizedEntity.contains(expectedPropertyBlock),
            "Expected generated own ID property block:\n$expectedPropertyBlock",
        )
        assertFalse(normalizedEntity.contains("var id: $idType = id"))

        val constructorParameters = internalConstructorParameters(normalizedEntity)
        assertFalse(
            Regex("""\bid\s*:\s*${Regex.escape(idType)}\b""").containsMatchIn(constructorParameters),
            "Expected internal constructor to exclude id: $idType, but parameters were:\n$constructorParameters",
        )
    }

    private fun internalConstructorParameters(generatedEntity: String): String {
        val constructorMarker = "internal constructor("
        val constructorStart = generatedEntity.indexOf(constructorMarker)
        if (constructorStart < 0) {
            throw AssertionError("Expected generated entity to declare an internal constructor")
        }

        val parameterStart = constructorStart + constructorMarker.length
        var depth = 1
        for (index in parameterStart until generatedEntity.length) {
            when (generatedEntity[index]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) {
                        return generatedEntity.substring(parameterStart, index)
                    }
                }
            }
        }

        throw AssertionError("Expected internal constructor parameters to have a matching closing parenthesis")
    }

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

class FunctionalTestArtifactAddonProvider : com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider {
    override val id: String = "functional-test-addon"

    override fun plan(
        context: com.only4.cap4k.plugin.pipeline.api.ArtifactAddonContext,
    ): List<com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem> {
        val adapterModule = context.config.modules["adapter"] ?: return emptyList()
        val templateId = "addons/functional-test-addon/aggregate/addon_marker.kt.peb"
        val packageName = "${context.config.basePackage}.adapter.addon"
        val typeName = "AddonGeneratedMarker"
        val sourceRoot = "$adapterModule/src/main/kotlin"
        return listOf(
            com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem(
                generatorId = id,
                moduleRole = "adapter",
                templateId = templateId,
                outputPath = "$sourceRoot/${packageName.replace('.', '/')}/$typeName.kt",
                context = mapOf(
                    "packageName" to packageName,
                    "typeName" to typeName,
                ),
                conflictPolicy = context.config.templates.templateConflictPolicies[templateId]
                    ?: context.config.templates.conflictPolicy,
                outputKind = com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind.CHECKED_IN_SOURCE,
                resolvedOutputRoot = sourceRoot,
            )
        )
    }
}

class FunctionalTestPipelineExtensionProvider :
    com.only4.cap4k.plugin.pipeline.api.PipelineExtensionProvider {
    override val descriptor = com.only4.cap4k.plugin.pipeline.api.PipelineExtensionDescriptor(
        id = "functional-test-extension",
        spiVersion = com.only4.cap4k.plugin.pipeline.api.PIPELINE_EXTENSION_SPI_VERSION,
    )
    override val contributions: List<com.only4.cap4k.plugin.pipeline.api.PipelineContribution> =
        listOf(FunctionalTestArtifactAddonProvider())
}
