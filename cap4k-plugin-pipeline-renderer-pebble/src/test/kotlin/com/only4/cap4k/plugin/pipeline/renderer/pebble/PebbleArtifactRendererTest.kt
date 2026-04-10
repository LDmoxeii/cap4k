package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.*
import java.nio.file.Files
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PebbleArtifactRendererTest {

    @Test
    fun `prefers override template over preset template`() {
        val overrideDir = Files.createTempDirectory("cap4k-override")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                package {{ packageName }}
                class {{ typeName }}Override
                """.trimIndent()
            )

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.queries",
                        "typeName" to "FindOrderQry",
                        "imports" to emptyList<String>(),
                        "requestFields" to listOf(
                            mapOf("name" to "orderId", "type" to "Long", "nullable" to false),
                        ),
                        "requestNestedTypes" to emptyList<Map<String, Any?>>(),
                        "responseFields" to listOf(
                            mapOf("name" to "status", "type" to "String", "nullable" to false),
                        ),
                        "responseNestedTypes" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        assertTrue(rendered.single().content.contains("FindOrderQryOverride"))
    }

    @Test
    fun `falls back to preset template when override does not exist`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.queries",
                        "typeName" to "FindOrderQry"
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("package com.acme.demo.application.queries"))
        assertTrue(content.contains("object FindOrderQry"))
    }

    @Test
    fun `falls back to preset design templates and renders nested types with imports`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-rich")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design",
                    moduleRole = "application",
                    templateId = "design/command.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.commands.order.submit",
                        "typeName" to "SubmitOrderCmd",
                        "imports" to listOf("java.time.LocalDateTime"),
                        "requestFields" to listOf(
                            mapOf("name" to "orderId", "type" to "Long", "nullable" to false),
                            mapOf("name" to "address", "type" to "Address", "nullable" to true),
                            mapOf("name" to "createdAt", "type" to "LocalDateTime", "nullable" to false),
                        ),
                        "requestNestedTypes" to listOf(
                            mapOf(
                                "name" to "Address",
                                "fields" to listOf(
                                    mapOf("name" to "city", "type" to "String", "nullable" to false),
                                    mapOf("name" to "zipCode", "type" to "String", "nullable" to false),
                                ),
                            ),
                        ),
                        "responseFields" to listOf(
                            mapOf("name" to "item", "type" to "Item", "nullable" to true),
                        ),
                        "responseNestedTypes" to listOf(
                            mapOf(
                                "name" to "Item",
                                "fields" to listOf(
                                    mapOf("name" to "id", "type" to "Long", "nullable" to false),
                                ),
                            ),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("import java.time.LocalDateTime"))
        assertTrue(content.contains("object SubmitOrderCmd"))
        assertTrue(content.contains("data class Request("))
        assertTrue(content.contains("val address: Address?"))
        assertTrue(content.contains("val createdAt: LocalDateTime"))
        assertTrue(content.contains("data class Address("))
        assertTrue(content.contains("val city: String"))
        assertTrue(content.contains("data class Response("))
        assertTrue(content.contains("val item: Item?"))
        assertTrue(content.contains("data class Item("))
    }

    @Test
    fun `renders empty request and response as stable objects`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-empty")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.queries.order.read",
                        "typeName" to "FindOrderQry",
                        "imports" to emptyList<String>(),
                        "requestFields" to emptyList<Map<String, Any?>>(),
                        "requestNestedTypes" to emptyList<Map<String, Any?>>(),
                        "responseFields" to emptyList<Map<String, Any?>>(),
                        "responseNestedTypes" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("object FindOrderQry"))
        assertTrue(content.contains("data object Request"))
        assertTrue(content.contains("data object Response"))
    }

    @Test
    fun `falls back to preset aggregate templates and renders aggregate content`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/schema.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/OrderSchema.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.order",
                        "typeName" to "OrderSchema",
                        "entityName" to "Order",
                        "fields" to listOf(
                            FieldModel("id", "Long"),
                            FieldModel("orderNo", "String", nullable = true)
                        )
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/Order.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.order",
                        "typeName" to "Order",
                        "comment" to "Order aggregate",
                        "idField" to FieldModel("id", "Long"),
                        "fields" to listOf(
                            FieldModel("id", "Long"),
                            FieldModel("orderNo", "String", nullable = true)
                        )
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "adapter",
                    templateId = "aggregate/repository.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/repositories/OrderRepository.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.domain.repositories",
                        "typeName" to "OrderRepository",
                        "entityName" to "Order",
                        "idType" to "Long"
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val schemaContent = rendered[0].content
        val entityContent = rendered[1].content
        val repositoryContent = rendered[2].content

        assertTrue(schemaContent.contains("object OrderSchema"))
        assertTrue(schemaContent.contains("const val id = \"id\""))
        assertTrue(schemaContent.contains("const val orderNo = \"orderNo\""))
        assertTrue(entityContent.contains("data class Order("))
        assertTrue(entityContent.contains("val orderNo: String?"))
        assertTrue(repositoryContent.contains("interface OrderRepository"))
    }

    @Test
    fun `falls back to preset flow templates and renders flow artifacts`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-flow")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "flow",
                    moduleRole = "project",
                    templateId = "flow/entry.json.peb",
                    outputPath = "flows/OrderController_submit.json",
                    context = mapOf(
                        "jsonContent" to """{"entryId":"OrderController::submit","edgeCount":2}"""
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "flow",
                    moduleRole = "project",
                    templateId = "flow/entry.mmd.peb",
                    outputPath = "flows/OrderController_submit.mmd",
                    context = mapOf(
                        "mermaidText" to """
                            flowchart TD
                              N1[OrderController::submit]
                              N1 -->|ControllerMethodToCommand| N2[SubmitOrderCmd]
                        """.trimIndent()
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "flow",
                    moduleRole = "project",
                    templateId = "flow/index.json.peb",
                    outputPath = "flows/index.json",
                    context = mapOf(
                        "jsonContent" to """{"flowCount":1,"inputDirs":["app/build/cap4k-code-analysis"]}"""
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = mapOf(
                    "flow" to GeneratorConfig(
                        enabled = true,
                        options = mapOf("outputDir" to "flows"),
                    ),
                ),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        assertEquals("""{"entryId":"OrderController::submit","edgeCount":2}""", rendered[0].content)
        assertEquals(
            """
            flowchart TD
              N1[OrderController::submit]
              N1 -->|ControllerMethodToCommand| N2[SubmitOrderCmd]
            """.trimIndent(),
            rendered[1].content,
        )
        assertEquals("""{"flowCount":1,"inputDirs":["app/build/cap4k-code-analysis"]}""", rendered[2].content)
    }

    @Test
    fun `falls back to preset drawing board template and renders valid json`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-drawing-board")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "drawing-board",
                    moduleRole = "project",
                    templateId = "drawing-board/document.json.peb",
                    outputPath = "design/cmd.json",
                    context = mapOf(
                        "drawingBoardTag" to "cmd",
                        "elements" to listOf(
                            DrawingBoardElementModel(
                                tag = "cmd",
                                packageName = "orders.api",
                                name = "Submit\"Order",
                                description = "line1\nline2",
                                aggregates = listOf("Order", "Ops\\Audit"),
                                entity = "OrderEntity",
                                persist = true,
                                requestFields = listOf(
                                    DrawingBoardFieldModel(
                                        name = "remark",
                                        type = "String",
                                        nullable = true,
                                        defaultValue = "say \"hi\""
                                    )
                                ),
                                responseFields = listOf(
                                    DrawingBoardFieldModel(
                                        name = "status",
                                        type = "String",
                                    )
                                ),
                            )
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        val element = JsonParser.parseString(content).asJsonArray.single().asJsonObject

        assertEquals("cmd", element["tag"].asString)
        assertEquals("orders.api", element["package"].asString)
        assertEquals("Submit\"Order", element["name"].asString)
        assertEquals("line1\nline2", element["desc"].asString)
        assertEquals("Ops\\Audit", element["aggregates"].asJsonArray[1].asString)
        assertEquals(true, element["persist"].asBoolean)
        assertEquals("say \"hi\"", element["requestFields"].asJsonArray[0].asJsonObject["defaultValue"].asString)
        assertEquals("status", element["responseFields"].asJsonArray[0].asJsonObject["name"].asString)
    }

    @Test
    fun `throws clear error when template is missing`() {
        val resolver = PresetTemplateResolver(
            preset = "ddd-default",
            overrideDirs = emptyList()
        )

        val exception = assertThrows<IllegalStateException> {
            resolver.resolve("design/not-exists.kt.peb")
        }

        assertTrue(exception.message!!.contains("Template not found: presets/ddd-default/design/not-exists.kt.peb"))
    }

    @Test
    fun `preserves outputPath and conflictPolicy in rendered artifact`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-meta")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText("class {{ typeName }}")

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt"
        val conflictPolicy = ConflictPolicy.FAIL
        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = outputPath,
                    context = mapOf("typeName" to "FindOrderQry"),
                    conflictPolicy = conflictPolicy
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val artifact = rendered.single()
        assertTrue(artifact.outputPath == outputPath)
        assertTrue(artifact.conflictPolicy == conflictPolicy)
    }

    @Test
    fun `renders drawing board json with optional entity persist and field metadata`() {
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = emptyList()
            )
        )

        val outputPath = "design/cmd.json"
        val conflictPolicy = ConflictPolicy.SKIP
        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "drawing-board",
                    moduleRole = "project",
                    templateId = "drawing-board/document.json.peb",
                    outputPath = outputPath,
                    context = mapOf(
                        "elements" to listOf(
                            DrawingBoardElementModel(
                                tag = "cmd",
                                packageName = "orders",
                                name = "SubmitOrder",
                                description = "submit order",
                                aggregates = listOf("Order"),
                                entity = "Order",
                                persist = true,
                                requestFields = listOf(
                                    DrawingBoardFieldModel(
                                        name = "id",
                                        type = "Long",
                                        nullable = false,
                                        defaultValue = null
                                    )
                                ),
                                responseFields = listOf(
                                    DrawingBoardFieldModel(
                                        name = "accepted",
                                        type = "Boolean",
                                        nullable = true,
                                        defaultValue = "false"
                                    )
                                )
                            ),
                            DrawingBoardElementModel(
                                tag = "qry",
                                packageName = "orders",
                                name = "FindOrder",
                                description = "find order",
                                aggregates = emptyList(),
                                requestFields = emptyList(),
                                responseFields = emptyList()
                            )
                        )
                    ),
                    conflictPolicy = conflictPolicy
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = emptyList(),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val artifact = rendered.single()
        assertTrue(artifact.outputPath == outputPath)
        assertTrue(artifact.conflictPolicy == conflictPolicy)

        val content = artifact.content
        assertTrue(content.startsWith("["))
        assertTrue(content.contains("\"tag\": \"cmd\""))
        assertTrue(content.contains("\"package\": \"orders\""))
        assertTrue(content.contains("\"name\": \"SubmitOrder\""))
        assertTrue(content.contains("\"desc\": \"submit order\""))
        assertTrue(content.contains("\"aggregates\": [\"Order\"]"))
        assertTrue(content.contains("\"entity\": \"Order\""))
        assertTrue(content.contains("\"persist\": true"))
        assertTrue(content.contains("\"requestFields\": ["))
        assertTrue(content.contains("\"name\": \"id\""))
        assertTrue(content.contains("\"nullable\": false"))
        assertTrue(content.contains("\"responseFields\": ["))
        assertTrue(content.contains("\"name\": \"accepted\""))
        assertTrue(content.contains("\"nullable\": true"))
        assertTrue(content.contains("\"defaultValue\": \"false\""))
        assertTrue(!content.contains("\"entity\": null"))
        assertTrue(!content.contains("\"persist\": null"))
        assertTrue(!content.contains("\"defaultValue\": null"))
    }
}
