package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.*
import java.nio.file.Files
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PebbleArtifactRendererTest {

    @Test
    fun `type helper reads renderedType from object and passes through string input`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-type")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ type(field) | raw }}
                {{ type("String") | raw }}
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
                        "field" to RenderedTypeCarrier("List<com.foo.Status?>")
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

        assertEquals(
            "List<com.foo.Status?>",
            rendered.single().content.substringBefore("String").trim()
        )
        assertTrue(rendered.single().content.contains("String"))
    }

    @Test
    fun `imports helper accepts direct list input and normalizes whitespace variants`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-imports")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ imports(importValues) | json | raw }}
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
                        "importValues" to listOf(
                            "  java.time.LocalDateTime  ",
                            "\tjava.util.UUID",
                            "java.time.LocalDateTime",
                            "java.util.UUID  ",
                            "  ",
                        )
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

        assertEquals(
            """["java.time.LocalDateTime","java.util.UUID"]""",
            rendered.single().content.trim()
        )
    }

    @Test
    fun `imports helper preserves order and removes blank and duplicate values from carrier map`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-imports-map")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ imports(importCarrier) | json | raw }}
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
                        "importCarrier" to mapOf(
                            "imports" to listOf(
                                "java.time.LocalDateTime",
                                "",
                                "java.util.UUID",
                                "java.time.LocalDateTime",
                                "  ",
                                "java.util.UUID",
                            )
                        )
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

        assertEquals(
            """["java.time.LocalDateTime","java.util.UUID"]""",
            rendered.single().content.trim()
        )
    }

    @Test
    fun `imports helper returns empty list for null and empty carrier input`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-imports-empty")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ imports(emptyCarrier) | json | raw }}|{{ imports(maybeImports) | json | raw }}
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
                        "emptyCarrier" to emptyMap<String, Any?>(),
                        "maybeImports" to null,
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

        assertEquals("[]|[]", rendered.single().content.trim())
    }

    @Test
    fun `imports helper fails fast when argument is missing`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-imports-missing")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText("""{{ imports() }}""")

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "design",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = emptyMap(),
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
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("imports() requires an argument."))
    }

    @Test
    fun `type helper fails fast on unsupported input`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-type-invalid")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText("""{{ type(badValue) }}""")

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "design",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = mapOf("badValue" to mapOf("name" to "missingRenderedType")),
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
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("type()"))
    }

    @Test
    fun `imports helper fails fast on unsupported input`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-imports-invalid")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText("""{{ imports(badValue) }}""")

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "design",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = mapOf("badValue" to 123),
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
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("imports()"))
    }

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
    fun `falls back to preset design templates and renders imports rendered types and nested types`() {
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
                        "imports" to listOf("java.time.LocalDateTime", "java.util.UUID"),
                        "requestFields" to listOf(
                            mapOf("name" to "orderId", "renderedType" to "Long", "nullable" to false),
                            mapOf("name" to "address", "renderedType" to "Address?", "nullable" to true),
                            mapOf("name" to "createdAt", "renderedType" to "LocalDateTime", "nullable" to false),
                            mapOf("name" to "requestStatus", "renderedType" to "com.foo.Status", "nullable" to false),
                        ),
                        "requestNestedTypes" to listOf(
                            mapOf(
                                "name" to "Address",
                                "fields" to listOf(
                                    mapOf("name" to "city", "renderedType" to "String", "nullable" to false),
                                    mapOf("name" to "trackingId", "renderedType" to "UUID", "nullable" to false),
                                ),
                            ),
                        ),
                        "responseFields" to listOf(
                            mapOf("name" to "item", "renderedType" to "Item?", "nullable" to true),
                            mapOf("name" to "responseStatus", "renderedType" to "com.bar.Status", "nullable" to false),
                        ),
                        "responseNestedTypes" to listOf(
                            mapOf(
                                "name" to "Item",
                                "fields" to listOf(
                                    mapOf("name" to "id", "renderedType" to "Long", "nullable" to false),
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
        assertFalse(content.contains("package com.acme.demo.application.commands.order.submitimport "))
        assertTrue(content.contains("package com.acme.demo.application.commands.order.submit"))
        assertTrue(content.contains("import java.time.LocalDateTime"))
        assertTrue(content.contains("import java.util.UUID"))
        assertFalse(content.contains("import com.foo.Status"))
        assertFalse(content.contains("import com.bar.Status"))
        assertTrue(content.contains("object SubmitOrderCmd"))
        assertTrue(content.contains("data class Request("))
        assertFalse(content.contains("val orderId: Long,        val address: Address?"))
        assertFalse(content.contains("val address: Address?,        val createdAt: LocalDateTime"))
        assertTrue(content.contains("val address: Address?"))
        assertFalse(content.contains("val address: Address??"))
        assertTrue(content.contains("val createdAt: LocalDateTime"))
        assertTrue(content.contains("val requestStatus: com.foo.Status"))
        assertTrue(content.contains("data class Address("))
        assertTrue(content.contains("val trackingId: UUID"))
        assertTrue(content.contains("data class Response("))
        assertTrue(content.contains("val item: Item?"))
        assertFalse(content.contains("val item: Item??"))
        assertTrue(content.contains("val responseStatus: com.bar.Status"))
        assertTrue(content.contains("data class Item("))
    }

    @Test
    fun `renders field default values in preset command design template`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-command-defaults")
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
                        "imports" to emptyList<String>(),
                        "requestFields" to listOf(
                            mapOf("name" to "title", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"demo\""),
                            mapOf("name" to "retryCount", "renderedType" to "Long", "nullable" to false, "defaultValue" to "1L"),
                        ),
                        "requestNestedTypes" to listOf(
                            mapOf(
                                "name" to "Metadata",
                                "fields" to listOf(
                                    mapOf("name" to "source", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"api\""),
                                ),
                            ),
                        ),
                        "responseFields" to listOf(
                            mapOf("name" to "enabled", "renderedType" to "Boolean", "nullable" to false, "defaultValue" to "true"),
                        ),
                        "responseNestedTypes" to listOf(
                            mapOf(
                                "name" to "Result",
                                "fields" to listOf(
                                    mapOf("name" to "status", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"OK\""),
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
        assertTrue(content.contains("val title: String = \"demo\""))
        assertTrue(content.contains("val retryCount: Long = 1L"))
        assertTrue(content.contains("val source: String = \"api\""))
        assertTrue(content.contains("val enabled: Boolean = true"))
        assertTrue(content.contains("val status: String = \"OK\""))
    }

    @Test
    fun `renders field default values in preset query design template`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-query-defaults")
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
                            mapOf("name" to "status", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"ACTIVE\""),
                        ),
                        "requestNestedTypes" to listOf(
                            mapOf(
                                "name" to "Criteria",
                                "fields" to listOf(
                                    mapOf("name" to "priority", "renderedType" to "Long", "nullable" to false, "defaultValue" to "1L"),
                                ),
                            ),
                        ),
                        "responseFields" to listOf(
                            mapOf("name" to "fallback", "renderedType" to "Boolean", "nullable" to false, "defaultValue" to "false"),
                        ),
                        "responseNestedTypes" to listOf(
                            mapOf(
                                "name" to "Result",
                                "fields" to listOf(
                                    mapOf("name" to "code", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"DONE\""),
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
        assertTrue(content.contains("val status: String = \"ACTIVE\""))
        assertTrue(content.contains("val priority: Long = 1L"))
        assertTrue(content.contains("val fallback: Boolean = false"))
        assertTrue(content.contains("val code: String = \"DONE\""))
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

        assertEquals("""{"entryId":"OrderController::submit","edgeCount":2}""", rendered[0].content.trim())
        assertEquals(
            """
            flowchart TD
              N1[OrderController::submit]
              N1 -->|ControllerMethodToCommand| N2[SubmitOrderCmd]
            """.trimIndent(),
            rendered[1].content.trim(),
        )
        assertEquals("""{"flowCount":1,"inputDirs":["app/build/cap4k-code-analysis"]}""", rendered[2].content.trim())
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

    @Test
    fun `use helper merges explicit imports with computed imports`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-merge")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ imports(imports) | json | raw }}
                {{ use("java.time.LocalDateTime") -}}
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
                        "imports" to listOf("java.util.UUID")
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

        assertEquals("""["java.util.UUID","java.time.LocalDateTime"]""", rendered.single().content.trim())
    }

    @Test
    fun `use helper merges explicit imports with carrier map imports`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-map-merge")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ use("java.time.LocalDateTime") -}}
                {{ imports(importCarrier) | json | raw }}
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
                        "importCarrier" to mapOf(
                            "imports" to listOf("java.util.UUID")
                        )
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

        assertEquals("""["java.util.UUID","java.time.LocalDateTime"]""", rendered.single().content.trim())
    }

    @Test
    fun `design helper session is cleared between artifacts`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-design-session")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("first.kt.peb")
            .writeText("""{{ use("java.time.LocalDateTime") -}}""")
        overrideDesignDir.resolve("second.kt.peb")
            .writeText(
                """
                {{ imports(importCarrier) | json | raw }}
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
                    templateId = "design/first.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/First.kt",
                    context = emptyMap(),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "design",
                    moduleRole = "application",
                    templateId = "design/second.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/Second.kt",
                    context = mapOf(
                        "importCarrier" to mapOf(
                            "imports" to listOf("java.util.UUID")
                        )
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

        assertEquals(2, rendered.size)
        assertEquals("", rendered[0].content.trim())
        assertEquals("""["java.util.UUID"]""", rendered[1].content.trim())
    }

    @Test
    fun `use helper is unavailable in aggregate templates`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-non-design")
        val overrideAggregateDir = Files.createDirectories(overrideDir.resolve("aggregate"))
        overrideAggregateDir.resolve("schema.kt.peb")
            .writeText("""{{ use("java.time.LocalDateTime") }}""")

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "aggregate",
                        moduleRole = "domain",
                        templateId = "aggregate/schema.kt.peb",
                        outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/OrderSchema.kt",
                        context = mapOf(
                            "packageName" to "com.acme.demo.domain.aggregates.order",
                            "typeName" to "OrderSchema",
                            "entityName" to "Order"
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
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument == null)
        assertTrue(exception.message?.contains("use") == true)
    }

    @Test
    fun `use helper deduplicates repeated explicit imports`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-dedupe")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ use("java.time.LocalDateTime") -}}
                {{ use("java.time.LocalDateTime") -}}
                {{ imports(imports) | json | raw }}
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
                        "imports" to emptyList<String>()
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

        assertEquals("""["java.time.LocalDateTime"]""", rendered.single().content.trim())
    }

    @Test
    fun `use helper does not affect type helper output`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-type")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ use("java.time.LocalDateTime") -}}
                {{ type(field) | raw }}
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
                        "field" to RenderedTypeCarrier("List<com.foo.Status?>")
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

        assertEquals("List<com.foo.Status?>", rendered.single().content.trim())
    }

    @Test
    fun `migration style override template composes helper contract`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-migration-contract")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("command.kt.peb")
            .writeText(
                """
                {{ use("java.io.Serializable") -}}
                {% for importValue in imports(imports) %}
                import {{ importValue }}
                {% endfor %}
                {% for field in requestFields %}
                val {{ field.name }}: {{ type(field) | raw }} = {{ field.defaultValue }}
                {% endfor %}
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
                    templateId = "design/command.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/commands/SubmitOrderCmd.kt",
                    context = mapOf(
                        "imports" to listOf(
                            "java.time.LocalDateTime",
                            "java.util.UUID",
                        ),
                        "requestFields" to listOf(
                            mapOf("name" to "retryCount", "renderedType" to "Long", "nullable" to false, "defaultValue" to "1L"),
                            mapOf("name" to "createdAt", "renderedType" to "LocalDateTime", "nullable" to false, "defaultValue" to "java.time.LocalDateTime.MIN"),
                            mapOf("name" to "enabled", "renderedType" to "Boolean", "nullable" to false, "defaultValue" to "true"),
                            mapOf("name" to "requestStatus", "renderedType" to "com.foo.Status", "nullable" to false, "defaultValue" to "com.foo.Status.ACTIVE"),
                            mapOf("name" to "responseStatus", "renderedType" to "com.bar.Status", "nullable" to false, "defaultValue" to "com.bar.Status.PENDING"),
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
        assertTrue(content.contains("import java.io.Serializable"))
        assertTrue(content.contains("import java.time.LocalDateTime"))
        assertTrue(content.contains("import java.util.UUID"))
        assertTrue(content.contains("val retryCount: Long = 1L"))
        assertTrue(content.contains("val createdAt: LocalDateTime = java.time.LocalDateTime.MIN"))
        assertTrue(content.contains("val enabled: Boolean = true"))
        assertTrue(content.contains("val requestStatus: com.foo.Status = com.foo.Status.ACTIVE"))
        assertTrue(content.contains("val responseStatus: com.bar.Status = com.bar.Status.PENDING"))
    }

    @Test
    fun `use helper fails fast when argument is missing`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-missing")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText("""{{ use() }}""")

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "design",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = emptyMap(),
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
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("use() requires exactly one argument"))
    }

    @Test
    fun `use helper fails fast when argument is not a string`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-non-string")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText("""{{ use(badValue) }}""")

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "design",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = mapOf("badValue" to 123),
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
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("use() requires a string fully qualified type name"))
    }

    @Test
    fun `use helper fails fast when argument is a short name`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-short-name")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText("""{{ use("LocalDateTime") }}""")

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "design",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = emptyMap(),
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
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("use() requires a fully qualified type name"))
    }

    @Test
    fun `use helper fails fast when argument is a wildcard import`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-wildcard")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText("""{{ use("java.time.*") }}""")

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "design",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = emptyMap(),
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
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("use() requires a fully qualified type name"))
    }

    @Test
    fun `use helper fails fast when import name is malformed`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-malformed")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText("""{{ use("java.time.Local-DateTime") }}""")

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "design",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = emptyMap(),
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
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("use() requires a fully qualified type name"))
    }

    @Test
    fun `use helper fails fast on collisions with computed imports`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-computed-collision")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ use("com.bar.Status") -}}
                {{ imports(imports) | json | raw }}
                """.trimIndent()
            )

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "design",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = mapOf(
                            "imports" to listOf("com.foo.Status")
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
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("use() import conflict"))
    }

    @Test
    fun `use helper fails fast on collisions between explicit imports`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-explicit-collision")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ use("com.foo.Status") -}}
                {{ use("com.bar.Status") -}}
                {{ imports(imports) | json | raw }}
                """.trimIndent()
            )

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "design",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = mapOf(
                            "imports" to emptyList<String>()
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
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("use() import conflict"))
    }
}

private data class RenderedTypeCarrier(
    val renderedType: String
)
