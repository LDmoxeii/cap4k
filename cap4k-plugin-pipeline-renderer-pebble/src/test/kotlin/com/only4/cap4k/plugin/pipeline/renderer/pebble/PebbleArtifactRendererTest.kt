package com.only4.cap4k.plugin.pipeline.renderer.pebble

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
        assertTrue(content.contains("class FindOrderQry"))
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
