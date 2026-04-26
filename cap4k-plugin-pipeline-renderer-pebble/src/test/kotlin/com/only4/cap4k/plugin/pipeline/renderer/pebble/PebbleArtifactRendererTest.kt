package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PebbleArtifactRendererTest {

    private fun assertReadableKotlin(content: String) {
        assertFalse(Regex("""(?m)[ \t]+$""").containsMatchIn(content), "Generated Kotlin must not contain trailing whitespace.")
        assertFalse(Regex("""\n{3,}""").containsMatchIn(content), "Generated Kotlin must not contain three or more consecutive newlines.")
    }

    private fun assertMaintainableTemplateSource(templateId: String) {
        val content = Files.readString(Path.of("src/main/resources/presets/ddd-default", templateId))
        val compressedPatterns = listOf(
            Regex("""(?m)^\s*\{%-?\s*endif\s*-?%}\{%-?\s*else\s*-?%}"""),
            Regex("""(?m)^\s*\{%-?\s*endif\s*-?%}\{%-?\s*if"""),
            Regex("""(?m)^\s*\{%-?\s*if [^%]+-?%}[ \t]*\S"""),
            Regex("""(?m)^\s*\{%-?\s*else\s*-?%}[ \t]*\S"""),
            Regex("""(?m)^\s*\{%-?\s*for [^%]+-?%}[ \t]*\S"""),
            Regex("""(?m)^\s*\{%-?\s*endfor\s*-?%}[ \t]*\S"""),
        )

        compressedPatterns.forEach { pattern ->
            assertFalse(
                pattern.containsMatchIn(content),
                "Template $templateId must keep Pebble control tags separate from Kotlin output.",
            )
        }
    }

    private fun String.normalizedLineEndings(): String = replace("\r\n", "\n")

    @Test
    fun `renderer normalizes kotlin artifact whitespace without mutating template semantics`() {
        val overrideDir = Files.createTempDirectory("cap4k-renderer-kotlin-hygiene")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb").writeText(
            "package demo\r\n\r\n\r\nimport java.util.UUID   \r\n\r\nclass Demo(   \r\n    val id: UUID   \r\n)\r\n\r\n\r\n"
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
                    generatorId = "design-query",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/Demo.kt",
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

        val content = rendered.single().content
        assertReadableKotlin(content)
        assertEquals(
            "package demo\n\nimport java.util.UUID\n\nclass Demo(\n    val id: UUID\n)\n",
            content
        )
    }

    @Test
    fun `renderer keeps non kotlin artifacts free from kotlin specific whitespace cleanup`() {
        val overrideDir = Files.createTempDirectory("cap4k-renderer-non-kotlin-hygiene")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb").writeText("{\r\n  \"value\": 1  \r\n\r\n\r\n}\r\n")

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design-query",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/resources/demo.json",
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

        assertEquals(
            "{\n  \"value\": 1  \n\n\n}\n",
            rendered.single().content
        )
    }

    @Test
    fun `aggregate wrapper template does not emit trailing whitespace when comment is blank`() {
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver("ddd-default", emptyList())
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/wrapper.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/AggOrder.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.order",
                        "typeName" to "AggOrder",
                        "entityName" to "Order",
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.order.Order",
                        "factoryTypeName" to "OrderFactory",
                        "factoryTypeFqn" to "com.acme.demo.domain.aggregates.order.factory.OrderFactory",
                        "idType" to "Long",
                        "comment" to "",
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
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        )

        val trailingWhitespaceLine = Regex("""(?m)[ \t]+$""")

        assertFalse(
            trailingWhitespaceLine.containsMatchIn(rendered.single().content),
            "Generated Kotlin must not contain trailing whitespace.",
        )
    }

    @Test
    fun `aggregate repository and schema templates restore default contracts`() {
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver("ddd-default", emptyList())
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "adapter",
                    templateId = "aggregate/repository.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/repositories/UserMessageRepository.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.domain.repositories",
                        "typeName" to "UserMessageRepository",
                        "entityName" to "UserMessage",
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.user_message.UserMessage",
                        "aggregateName" to "UserMessage",
                        "idType" to "Long",
                        "supportQuerydsl" to false,
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/schema.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/user_message/SUserMessage.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain._share.meta.user_message",
                        "typeName" to "SUserMessage",
                        "entityName" to "UserMessage",
                        "schemaRuntimePackage" to "com.only4.cap4k.ddd.domain.repo.schema",
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.user_message.UserMessage",
                        "qEntityTypeFqn" to "com.acme.demo.domain.aggregates.user_message.QUserMessage",
                        "aggregateTypeFqn" to "com.acme.demo.domain.aggregates.user_message.AggUserMessage",
                        "isAggregateRoot" to true,
                        "wrapperEnabled" to true,
                        "repositorySupportQuerydsl" to false,
                        "fields" to listOf(
                            mapOf(
                                "name" to "messageKey",
                                "fieldName" to "messageKey",
                                "columnName" to "message_key",
                                "fieldType" to "String",
                                "type" to "String",
                                "comment" to "message key",
                            )
                        ),
                        "relationFields" to listOf(
                            mapOf(
                                "name" to "sender",
                                "targetTypeRef" to "UserProfile",
                            )
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        )

        val repositoryContent = rendered.single { it.outputPath.endsWith("UserMessageRepository.kt") }.content
        val schemaContent = rendered.single { it.outputPath.endsWith("SUserMessage.kt") }.content

        assertTrue(repositoryContent.contains("@Repository"))
        assertTrue(
            repositoryContent.contains(
                "interface UserMessageRepository : JpaRepository<UserMessage, Long>, JpaSpecificationExecutor<UserMessage>"
            )
        )
        assertTrue(repositoryContent.contains("class UserMessageJpaRepositoryAdapter("))
        assertTrue(repositoryContent.contains("AbstractJpaRepository<UserMessage, Long>"))
        assertTrue(schemaContent.contains("import com.only4.cap4k.ddd.domain.repo.schema.SchemaSpecification"))
        assertTrue(schemaContent.contains("import com.only4.cap4k.ddd.domain.repo.schema.Field"))
        assertTrue(schemaContent.contains("import com.acme.demo.domain.aggregates.user_message.UserMessage"))
        assertTrue(schemaContent.contains("import com.acme.demo.domain.aggregates.user_message.AggUserMessage"))
        assertFalse(schemaContent.contains("import {{"))
        assertTrue(schemaContent.contains("class SUserMessage("))
        assertTrue(schemaContent.contains("fun specify(builder: PredicateBuilder<SUserMessage>): Specification<UserMessage>"))
        assertTrue(schemaContent.contains("fun predicateById(id: Any): AggregatePredicate<AggUserMessage, UserMessage>"))
        assertTrue(schemaContent.contains("val messageKey: Field<String>"))
        assertFalse(schemaContent.contains("val message_key"))
        assertFalse(schemaContent.contains("Field<Any>"))
    }

    @Test
    fun `aggregate schema template omits wrapper references when wrapper is disabled`() {
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver("ddd-default", emptyList())
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/schema.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/user_message/SUserMessage.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain._share.meta.user_message",
                        "typeName" to "SUserMessage",
                        "entityName" to "UserMessage",
                        "schemaRuntimePackage" to "com.only4.cap4k.ddd.domain.repo.schema",
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.user_message.UserMessage",
                        "qEntityTypeFqn" to "com.acme.demo.domain.aggregates.user_message.QUserMessage",
                        "aggregateTypeFqn" to "",
                        "isAggregateRoot" to true,
                        "wrapperEnabled" to false,
                        "repositorySupportQuerydsl" to false,
                        "fields" to listOf(
                            mapOf(
                                "name" to "messageKey",
                                "fieldName" to "messageKey",
                                "columnName" to "message_key",
                                "fieldType" to "String",
                                "type" to "String",
                                "comment" to "message key",
                            )
                        ),
                        "relationFields" to emptyList<Map<String, Any?>>(),
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
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        )

        val schemaContent = rendered.single().content

        assertFalse(schemaContent.contains("AggUserMessage"))
        assertFalse(schemaContent.contains("AggregatePredicate"))
        assertTrue(schemaContent.contains("fun predicateById(id: Any): JpaPredicate<UserMessage>"))
        assertTrue(schemaContent.contains("fun predicate(builder: PredicateBuilder<SUserMessage>): JpaPredicate<UserMessage>"))
    }

    @Test
    fun `aggregate child schema keeps criteria helpers without root predicate helpers`() {
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver("ddd-default", emptyList())
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/schema.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video/SVideoFile.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain._share.meta.video",
                        "typeName" to "SVideoFile",
                        "entityName" to "VideoFile",
                        "schemaRuntimePackage" to "com.only4.cap4k.ddd.domain.repo.schema",
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.video.VideoFile",
                        "qEntityTypeFqn" to "com.acme.demo.domain.aggregates.video.QVideoFile",
                        "aggregateTypeFqn" to "",
                        "isAggregateRoot" to false,
                        "wrapperEnabled" to true,
                        "repositorySupportQuerydsl" to false,
                        "fields" to listOf(
                            mapOf(
                                "name" to "videoId",
                                "fieldName" to "videoId",
                                "columnName" to "video_id",
                                "fieldType" to "Long",
                                "type" to "Long",
                                "comment" to "video id",
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
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        )

        val schemaContent = rendered.single().content

        assertTrue(schemaContent.contains("class SVideoFile("))
        assertTrue(schemaContent.contains("fun specify(builder: PredicateBuilder<SVideoFile>): Specification<VideoFile>"))
        assertTrue(schemaContent.contains("val videoId: Field<Long>"))
        assertFalse(schemaContent.contains("JpaPredicate"))
        assertFalse(schemaContent.contains("AggregatePredicate"))
        assertFalse(schemaContent.contains("AggVideoFile"))
        assertFalse(schemaContent.contains("fun predicateById("))
        assertFalse(schemaContent.contains("fun predicate(builder: PredicateBuilder<SVideoFile>)"))
    }

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
                    generatorId = "design-command",
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
                    generatorId = "design-command",
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
                    generatorId = "design-command",
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
                    generatorId = "design-command",
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
                        generatorId = "design-command",
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
                        generatorId = "design-command",
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
                        generatorId = "design-command",
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
                    generatorId = "design-command",
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
                    generatorId = "design-command",
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
                    generatorId = "design-command",
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
    fun `design templates render readable request response classes`() {
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver("ddd-default", emptyList())
        )
        val templateIds = listOf(
            "design/query.kt.peb",
            "design/command.kt.peb",
            "design/client.kt.peb",
            "design/query_list.kt.peb",
            "design/query_page.kt.peb",
            "design/api_payload.kt.peb",
        )
        val commonRequestFields = listOf(
            mapOf("name" to "messageKey", "renderedType" to "String", "nullable" to false),
        )
        val commonResponseFields = listOf(
            mapOf("name" to "content", "renderedType" to "String", "nullable" to false),
        )
        val commonFields = mapOf(
            "imports" to emptyList<String>(),
            "requestFields" to commonRequestFields,
            "requestNestedTypes" to emptyList<Map<String, Any?>>(),
            "responseFields" to commonResponseFields,
            "responseNestedTypes" to emptyList<Map<String, Any?>>(),
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design-query",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/edu/only4/danmaku/application/queries/message/read/FindUserMessageQry.kt",
                    context = commonFields + mapOf(
                        "packageName" to "edu.only4.danmaku.application.queries.message.read",
                        "typeName" to "FindUserMessageQry",
                    ),
                    conflictPolicy = ConflictPolicy.OVERWRITE
                ),
                ArtifactPlanItem(
                    generatorId = "design-command",
                    moduleRole = "application",
                    templateId = "design/command.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/edu/only4/danmaku/application/commands/message/create/CreateUserMessageCmd.kt",
                    context = commonFields + mapOf(
                        "packageName" to "edu.only4.danmaku.application.commands.message.create",
                        "typeName" to "CreateUserMessageCmd",
                    ),
                    conflictPolicy = ConflictPolicy.OVERWRITE
                ),
                ArtifactPlanItem(
                    generatorId = "design-client",
                    moduleRole = "application",
                    templateId = "design/client.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/edu/only4/danmaku/application/distributed/clients/message/delivery/PublishUserMessageCli.kt",
                    context = commonFields + mapOf(
                        "packageName" to "edu.only4.danmaku.application.distributed.clients.message.delivery",
                        "typeName" to "PublishUserMessageCli",
                    ),
                    conflictPolicy = ConflictPolicy.OVERWRITE
                ),
                ArtifactPlanItem(
                    generatorId = "design-api-payload",
                    moduleRole = "adapter",
                    templateId = "design/api_payload.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/edu/only4/danmaku/adapter/portal/api/payload/message/CreateUserMessagePayload.kt",
                    context = mapOf(
                        "packageName" to "edu.only4.danmaku.adapter.portal.api.payload.message",
                        "typeName" to "CreateUserMessagePayload",
                        "imports" to emptyList<String>(),
                        "requestFields" to listOf(
                            mapOf("name" to "messageKey", "renderedType" to "String", "nullable" to false),
                            mapOf("name" to "body", "renderedType" to "Body?", "nullable" to true),
                        ),
                        "requestNestedTypes" to listOf(
                            mapOf(
                                "name" to "Body",
                                "fields" to listOf(
                                    mapOf("name" to "content", "renderedType" to "String", "nullable" to false),
                                ),
                            ),
                        ),
                        "responseFields" to listOf(
                            mapOf("name" to "receipt", "renderedType" to "Receipt?", "nullable" to true),
                        ),
                        "responseNestedTypes" to listOf(
                            mapOf(
                                "name" to "Receipt",
                                "fields" to listOf(
                                    mapOf("name" to "messageKey", "renderedType" to "String", "nullable" to false),
                                ),
                            ),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.OVERWRITE
                ),
            ),
            config = ProjectConfig(
                basePackage = "edu.only4.danmaku",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.OVERWRITE),
            )
        )

        templateIds.forEach(::assertMaintainableTemplateSource)
        rendered.forEach {
            assertReadableKotlin(it.content)
            assertFalse(Regex("""package .+import """).containsMatchIn(it.content))
            assertFalse(Regex("""}\s*object """).containsMatchIn(it.content))
            assertFalse(Regex("""\) : RequestParam<Response>[ \t]+data class Response""").containsMatchIn(it.content))
        }

        val renderedByFile = rendered.associateBy { it.outputPath.substringAfterLast("/") }
        val queryContent = renderedByFile.getValue("FindUserMessageQry.kt").content
        val commandContent = renderedByFile.getValue("CreateUserMessageCmd.kt").content
        val clientContent = renderedByFile.getValue("PublishUserMessageCli.kt").content
        val apiPayloadContent = renderedByFile.getValue("CreateUserMessagePayload.kt").content

        listOf(queryContent, commandContent, clientContent).forEach { content ->
            assertTrue(content.contains("import com.only4.cap4k.ddd.core.application.RequestParam"))
            assertTrue(content.contains(") : RequestParam<Response>"))
            assertTrue(content.normalizedLineEndings().contains(") : RequestParam<Response>\n\n    data class Response("))
            assertFalse(content.contains("\n\n\n"))
        }

        assertTrue(commandContent.contains("import com.only4.cap4k.ddd.core.Mediator"))
        assertTrue(commandContent.contains("import com.only4.cap4k.ddd.core.application.command.Command"))
        assertTrue(commandContent.contains("import org.springframework.stereotype.Service"))
        assertTrue(commandContent.contains("@Service\n    class Handler : Command<Request, Response>"))
        assertTrue(commandContent.contains("Mediator.uow.save()"))
        assertTrue(
            commandContent.normalizedLineEndings().contains(
                "            return Response(\n" +
                    "                content = TODO(\"set content\")\n" +
                    "            )"
            )
        )

        val requestIndex = apiPayloadContent.indexOf("data class Request(")
        val responseIndex = apiPayloadContent.indexOf("data class Response(")
        assertTrue(requestIndex >= 0, "Request class must be rendered.")
        assertTrue(responseIndex >= 0, "Response class must be rendered.")
        val requestSection = apiPayloadContent.substring(requestIndex, responseIndex)
        val responseSection = apiPayloadContent.substring(responseIndex)
        assertTrue(requestSection.contains("        data class Body("))
        assertTrue(requestSection.contains("val content: String"))
        assertFalse(requestSection.contains("data class Receipt("))
        assertTrue(responseSection.contains("        data class Receipt("))
        assertTrue(responseSection.contains("val messageKey: String"))
        assertFalse(Regex("^ {4}data class Body\\(", RegexOption.MULTILINE).containsMatchIn(apiPayloadContent))
        assertFalse(Regex("^ {4}data class Receipt\\(", RegexOption.MULTILINE).containsMatchIn(apiPayloadContent))
        assertTrue(apiPayloadContent.normalizedLineEndings().contains("    }\n\n    data class Response("))
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
                    generatorId = "design-command",
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
                    generatorId = "design-command",
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
    fun `default query preset uses request param contract`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-query-contract")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design-command",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.queries",
                        "typeName" to "FindOrderQry",
                        "imports" to listOf(
                            "java.time.LocalDateTime",
                            "java.util.UUID",
                        ),
                        "requestFields" to listOf(
                            mapOf("name" to "lookupId", "renderedType" to "UUID", "nullable" to false),
                            mapOf("name" to "requestStatus", "renderedType" to "com.foo.Status", "nullable" to false),
                            mapOf("name" to "createdAfter", "renderedType" to "LocalDateTime", "nullable" to false),
                        ),
                        "requestNestedTypes" to emptyList<Map<String, Any?>>(),
                        "responseFields" to listOf(
                            mapOf("name" to "responseStatus", "renderedType" to "com.bar.Status", "nullable" to false),
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

        val content = rendered.single().content
        assertTrue(content.contains("import com.only4.cap4k.ddd.core.application.RequestParam"))
        assertTrue(content.contains("import java.time.LocalDateTime"))
        assertTrue(content.contains("import java.util.UUID"))
        assertFalse(content.contains("import com.foo.Status"))
        assertFalse(content.contains("import com.bar.Status"))
        assertTrue(content.contains("object FindOrderQry"))
        assertTrue(content.contains("data class Request("))
        assertTrue(content.contains(") : RequestParam<Response>"))
        assertTrue(content.contains("val lookupId: UUID"))
        assertTrue(content.contains("val requestStatus: com.foo.Status"))
        assertTrue(content.contains("val responseStatus: com.bar.Status"))
    }

    @Test
    fun `bounded query presets render list and page request contracts`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-bounded-query-contracts")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design-command",
                    moduleRole = "application",
                    templateId = "design/query_list.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderListQry.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.queries",
                        "typeName" to "FindOrderListQry",
                        "imports" to listOf(
                            "java.util.UUID",
                        ),
                        "requestFields" to listOf(
                            mapOf("name" to "listCursorId", "renderedType" to "UUID", "nullable" to false),
                            mapOf("name" to "requestStatus", "renderedType" to "com.foo.Status", "nullable" to false),
                        ),
                        "requestNestedTypes" to emptyList<Map<String, Any?>>(),
                        "responseFields" to listOf(
                            mapOf("name" to "responseStatus", "renderedType" to "com.bar.Status", "nullable" to false),
                        ),
                        "responseNestedTypes" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "design-command",
                    moduleRole = "application",
                    templateId = "design/query_page.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderPageQry.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.queries",
                        "typeName" to "FindOrderPageQry",
                        "imports" to listOf(
                            "java.time.LocalDateTime",
                        ),
                        "requestFields" to listOf(
                            mapOf("name" to "createdAfter", "renderedType" to "LocalDateTime", "nullable" to false),
                            mapOf("name" to "requestStatus", "renderedType" to "com.foo.Status", "nullable" to false),
                        ),
                        "requestNestedTypes" to emptyList<Map<String, Any?>>(),
                        "responseFields" to listOf(
                            mapOf("name" to "responseStatus", "renderedType" to "com.bar.Status", "nullable" to false),
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

        val listContent = rendered[0].content
        assertTrue(listContent.contains("import com.only4.cap4k.ddd.core.application.query.ListQueryParam"))
        assertTrue(listContent.contains("data class Request("))
        assertTrue(listContent.contains(") : ListQueryParam<Response>"))
        assertTrue(listContent.contains("val listCursorId: UUID"))
        assertFalse(listContent.contains("import com.foo.Status"))
        assertFalse(listContent.contains("import com.bar.Status"))
        assertTrue(listContent.contains("val requestStatus: com.foo.Status"))
        assertTrue(listContent.contains("val responseStatus: com.bar.Status"))

        val pageContent = rendered[1].content
        assertTrue(pageContent.contains("import com.only4.cap4k.ddd.core.application.query.PageQueryParam"))
        assertTrue(pageContent.contains("data class Request("))
        assertTrue(pageContent.contains(") : PageQueryParam<Response>()"))
        assertTrue(pageContent.contains("val createdAfter: LocalDateTime"))
        assertFalse(pageContent.contains("import com.foo.Status"))
        assertFalse(pageContent.contains("import com.bar.Status"))
        assertTrue(pageContent.contains("val requestStatus: com.foo.Status"))
        assertTrue(pageContent.contains("val responseStatus: com.bar.Status"))
    }

    @Test
    fun `bounded query presets render empty request contracts and nested types`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-bounded-query-empty")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design-command",
                    moduleRole = "application",
                    templateId = "design/query_list.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderListQry.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.queries",
                        "typeName" to "FindOrderListQry",
                        "imports" to emptyList<String>(),
                        "requestFields" to emptyList<Map<String, Any?>>(),
                        "requestNestedTypes" to emptyList<Map<String, Any?>>(),
                        "responseFields" to emptyList<Map<String, Any?>>(),
                        "responseNestedTypes" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "design-command",
                    moduleRole = "application",
                    templateId = "design/query_page.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderPageQry.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.queries",
                        "typeName" to "FindOrderPageQry",
                        "imports" to emptyList<String>(),
                        "requestFields" to listOf(
                            mapOf("name" to "createdAfter", "renderedType" to "LocalDateTime", "nullable" to false),
                        ),
                        "requestNestedTypes" to listOf(
                            mapOf(
                                "name" to "Criteria",
                                "fields" to listOf(
                                    mapOf("name" to "origin", "renderedType" to "String", "nullable" to false),
                                ),
                            ),
                        ),
                        "responseFields" to listOf(
                            mapOf("name" to "items", "renderedType" to "List<Item>", "nullable" to false),
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

        val listContent = rendered[0].content
        assertTrue(listContent.contains("import com.only4.cap4k.ddd.core.application.query.ListQueryParam"))
        assertTrue(listContent.contains("class Request : ListQueryParam<Response>"))
        assertTrue(listContent.contains("data object Response"))

        val pageContent = rendered[1].content
        assertTrue(pageContent.contains("import com.only4.cap4k.ddd.core.application.query.PageQueryParam"))
        assertTrue(pageContent.contains("data class Request("))
        assertTrue(pageContent.contains(") : PageQueryParam<Response>()"))
        assertTrue(pageContent.contains("data class Criteria("))
        assertTrue(pageContent.contains("val origin: String"))
        assertTrue(pageContent.contains("data class Response("))
        assertTrue(pageContent.contains("val items: List<Item>"))
        assertTrue(pageContent.contains("data class Item("))
        assertTrue(pageContent.contains("val id: Long"))
    }

    @Test
    fun `renders empty request as contract class and response as stable object`() {
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
                    generatorId = "design-command",
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
        assertTrue(content.contains("import com.only4.cap4k.ddd.core.application.RequestParam"))
        assertTrue(content.contains("class Request : RequestParam<Response>"))
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
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/order/SOrder.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain._share.meta.order",
                        "typeName" to "SOrder",
                        "entityName" to "Order",
                        "schemaRuntimePackage" to "com.only4.cap4k.ddd.domain.repo.schema",
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.order.Order",
                        "qEntityTypeFqn" to "com.acme.demo.domain.aggregates.order.QOrder",
                        "aggregateTypeFqn" to "com.acme.demo.domain.aggregates.order.AggOrder",
                        "wrapperEnabled" to true,
                        "repositorySupportQuerydsl" to false,
                        "fields" to listOf(
                            mapOf(
                                "name" to "id",
                                "fieldName" to "id",
                                "columnName" to "id",
                                "fieldType" to "Long",
                                "type" to "Long",
                                "nullable" to false,
                            ),
                            mapOf(
                                "name" to "orderNo",
                                "fieldName" to "orderNo",
                                "columnName" to "order_no",
                                "fieldType" to "String",
                                "type" to "String",
                                "nullable" to true,
                            )
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
                        "jpaImports" to emptyList<String>(),
                        "imports" to emptyList<String>(),
                        "scalarFields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false),
                            mapOf("name" to "orderNo", "type" to "String", "nullable" to true)
                        ),
                        "fields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false),
                            mapOf("name" to "orderNo", "type" to "String", "nullable" to true)
                        ),
                        "relationFields" to emptyList<Map<String, Any?>>()
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
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.order.Order",
                        "aggregateName" to "Order",
                        "idType" to "Long",
                        "supportQuerydsl" to false,
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/factory.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/factory/OrderFactory.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.order.factory",
                        "typeName" to "OrderFactory",
                        "payloadTypeName" to "Payload",
                        "entityName" to "Order",
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.order.Order",
                        "aggregateName" to "Order",
                        "comment" to "Order aggregate",
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/specification.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/specification/OrderSpecification.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.order.specification",
                        "typeName" to "OrderSpecification",
                        "entityName" to "Order",
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.order.Order",
                        "aggregateName" to "Order",
                        "comment" to "Order aggregate",
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/wrapper.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/AggOrder.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.order",
                        "typeName" to "AggOrder",
                        "entityName" to "Order",
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.order.Order",
                        "factoryTypeName" to "OrderFactory",
                        "factoryTypeFqn" to "com.acme.demo.domain.aggregates.order.factory.OrderFactory",
                        "idType" to "Long",
                        "comment" to "Order aggregate",
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "application",
                    templateId = "aggregate/unique_query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/UniqueVideoPostTenantIdSlugQry.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.queries.video_post.unique",
                        "typeName" to "UniqueVideoPostTenantIdSlugQry",
                        "entityName" to "VideoPost",
                        "requestProps" to listOf(
                            mapOf("name" to "tenantId", "type" to "Long", "nullable" to false),
                            mapOf("name" to "slug", "type" to "String", "nullable" to true),
                        ),
                        "idType" to "Long",
                        "excludeIdParamName" to "excludeVideoPostId",
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "adapter",
                    templateId = "aggregate/unique_query_handler.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video_post/unique/UniqueVideoPostTenantIdSlugQryHandler.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.queries.video_post.unique",
                        "typeName" to "UniqueVideoPostTenantIdSlugQryHandler",
                        "queryTypeName" to "UniqueVideoPostTenantIdSlugQry",
                        "queryTypeFqn" to "com.acme.demo.application.queries.video_post.unique.UniqueVideoPostTenantIdSlugQry",
                        "repositoryTypeName" to "VideoPostRepository",
                        "repositoryTypeFqn" to "com.acme.demo.adapter.domain.repositories.VideoPostRepository",
                        "schemaTypeName" to "SVideoPost",
                        "schemaTypeFqn" to "com.acme.demo.domain._share.meta.video_post.SVideoPost",
                        "entityTypeName" to "VideoPost",
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPost",
                        "whereProps" to listOf("tenantId", "slug"),
                        "idPropName" to "id",
                        "excludeIdParamName" to "excludeVideoPostId",
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "application",
                    templateId = "aggregate/unique_validator.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/validators/video_post/unique/UniqueVideoPostTenantIdSlug.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.validators.video_post.unique",
                        "typeName" to "UniqueVideoPostTenantIdSlug",
                        "queryTypeName" to "UniqueVideoPostTenantIdSlugQry",
                        "queryTypeFqn" to "com.acme.demo.application.queries.video_post.unique.UniqueVideoPostTenantIdSlugQry",
                        "requestProps" to listOf(
                            mapOf(
                                "name" to "tenantId",
                                "type" to "Long",
                                "isString" to false,
                                "param" to "tenantIdField",
                                "varName" to "tenantIdProperty",
                            ),
                            mapOf(
                                "name" to "slug",
                                "type" to "String",
                                "isString" to true,
                                "param" to "slugField",
                                "varName" to "slugProperty",
                            ),
                        ),
                        "fieldParams" to listOf(
                            mapOf("param" to "tenantIdField", "default" to "tenantId"),
                            mapOf("param" to "slugField", "default" to "slug"),
                        ),
                        "idType" to "Long",
                        "excludeIdParamName" to "excludeVideoPostId",
                        "entityIdParam" to "videoPostIdField",
                        "entityIdDefault" to "videoPostId",
                        "entityIdVar" to "videoPostIdProperty",
                        "entityName" to "VideoPost",
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

        val aggregateArtifacts = rendered.reversed()

        assertEquals(9, aggregateArtifacts.size)

        fun contentFor(pathSuffix: String): String = aggregateArtifacts.single {
            it.outputPath.endsWith(pathSuffix)
        }.content

        val schemaContent = contentFor("/_share/meta/order/SOrder.kt")
        val entityContent = contentFor("/aggregates/order/Order.kt")
        val repositoryContent = contentFor("/adapter/domain/repositories/OrderRepository.kt")
        val factoryContent = contentFor("/factory/OrderFactory.kt")
        val specificationContent = contentFor("/specification/OrderSpecification.kt")
        val wrapperContent = contentFor("/aggregates/order/AggOrder.kt")
        val uniqueQueryContent = contentFor("/application/queries/video_post/unique/UniqueVideoPostTenantIdSlugQry.kt")
        val uniqueHandlerContent = contentFor("/adapter/queries/video_post/unique/UniqueVideoPostTenantIdSlugQryHandler.kt")
        val uniqueValidatorContent = contentFor("/application/validators/video_post/unique/UniqueVideoPostTenantIdSlug.kt")

        assertTrue(schemaContent.contains("import com.only4.cap4k.ddd.domain.repo.schema.SchemaSpecification"))
        assertTrue(schemaContent.contains("import com.only4.cap4k.ddd.domain.repo.schema.Field"))
        assertTrue(schemaContent.contains("class SOrder("))
        assertTrue(schemaContent.contains("fun specify(builder: PredicateBuilder<SOrder>): Specification<Order>"))
        assertTrue(schemaContent.contains("val orderNo: Field<String>"))
        assertTrue(entityContent.contains("data class Order("))
        assertTrue(entityContent.contains("val orderNo: String?"))
        assertFalse(entityContent.contains("jakarta.persistence"))
        assertTrue(repositoryContent.contains("@Repository"))
        assertTrue(repositoryContent.contains("interface OrderRepository : JpaRepository<Order, Long>, JpaSpecificationExecutor<Order>"))
        assertTrue(factoryContent.contains("import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory"))
        assertTrue(factoryContent.contains("import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload"))
        assertTrue(factoryContent.contains("import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate"))
        assertTrue(factoryContent.contains("import org.springframework.stereotype.Service"))
        assertTrue(factoryContent.contains("import com.acme.demo.domain.aggregates.order.Order"))
        assertTrue(factoryContent.contains("class OrderFactory : AggregateFactory<OrderFactory.Payload, Order>"))
        assertTrue(factoryContent.contains("""aggregate = "Order""""))
        assertTrue(factoryContent.contains("""name = "OrderFactory""""))
        assertTrue(factoryContent.contains("type = Aggregate.TYPE_FACTORY"))
        assertTrue(factoryContent.contains("""name = "Payload""""))
        assertTrue(factoryContent.contains("type = Aggregate.TYPE_FACTORY_PAYLOAD"))
        assertTrue(factoryContent.contains("""TODO("Implement aggregate construction")"""))
        assertTrue(factoryContent.contains("data class Payload("))
        assertTrue(specificationContent.contains("import com.only4.cap4k.ddd.core.domain.aggregate.Specification"))
        assertTrue(specificationContent.contains("import com.only4.cap4k.ddd.core.domain.aggregate.Specification.Result"))
        assertTrue(specificationContent.contains("import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate"))
        assertTrue(specificationContent.contains("import org.springframework.stereotype.Service"))
        assertTrue(specificationContent.contains("import com.acme.demo.domain.aggregates.order.Order"))
        assertTrue(specificationContent.contains("class OrderSpecification : Specification<Order>"))
        assertTrue(specificationContent.contains("""aggregate = "Order""""))
        assertTrue(specificationContent.contains("""name = "OrderSpecification""""))
        assertTrue(specificationContent.contains("type = Aggregate.TYPE_SPECIFICATION"))
        assertTrue(specificationContent.contains("return Result.pass()"))
        assertTrue(wrapperContent.contains("import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate"))
        assertTrue(wrapperContent.contains("import com.acme.demo.domain.aggregates.order.Order"))
        assertTrue(wrapperContent.contains("import com.acme.demo.domain.aggregates.order.factory.OrderFactory"))
        assertTrue(wrapperContent.contains("class AggOrder("))
        assertTrue(wrapperContent.contains("payload: OrderFactory.Payload? = null"))
        assertTrue(wrapperContent.contains(") : Aggregate.Default<Order>(payload)"))
        assertTrue(wrapperContent.contains("val id by lazy { root.id }"))
        assertTrue(
            wrapperContent.contains(
                "class Id(key: Long) : com.only4.cap4k.ddd.core.domain.aggregate.Id.Default<AggOrder, Long>(key)"
            )
        )
        assertTrue(uniqueQueryContent.contains("object UniqueVideoPostTenantIdSlugQry"))
        assertTrue(uniqueQueryContent.contains("import com.only4.cap4k.ddd.core.application.RequestParam"))
        assertTrue(uniqueQueryContent.contains("val tenantId: Long"))
        assertTrue(uniqueQueryContent.contains("val slug: String?"))
        assertTrue(uniqueQueryContent.contains("val excludeVideoPostId: Long?"))
        assertTrue(uniqueHandlerContent.contains("class UniqueVideoPostTenantIdSlugQryHandler"))
        assertTrue(uniqueHandlerContent.contains("import com.only4.cap4k.ddd.core.application.query.Query"))
        assertTrue(uniqueHandlerContent.contains("import com.acme.demo.application.queries.video_post.unique.UniqueVideoPostTenantIdSlugQry"))
        assertTrue(uniqueHandlerContent.contains("import com.acme.demo.adapter.domain.repositories.VideoPostRepository"))
        assertTrue(uniqueHandlerContent.contains("import com.acme.demo.domain._share.meta.video_post.SVideoPost"))
        assertTrue(uniqueHandlerContent.contains("private val repository: VideoPostRepository"))
        assertTrue(uniqueHandlerContent.contains("val exists = repository.exists("))
        assertTrue(uniqueHandlerContent.contains("SVideoPost.specify"))
        assertTrue(uniqueHandlerContent.contains("schema.tenantId eq request.tenantId"))
        assertTrue(uniqueHandlerContent.contains("schema.slug eq request.slug"))
        assertFalse(uniqueHandlerContent.contains("exists = false"))
        assertTrue(uniqueValidatorContent.contains("annotation class UniqueVideoPostTenantIdSlug"))
        assertTrue(uniqueValidatorContent.contains("import jakarta.validation.Constraint"))
        assertTrue(uniqueValidatorContent.contains("import com.acme.demo.application.queries.video_post.unique.UniqueVideoPostTenantIdSlugQry"))
        assertTrue(uniqueValidatorContent.contains("import com.only4.cap4k.ddd.core.Mediator"))
        assertTrue(
            uniqueValidatorContent.contains(
                "class Validator : ConstraintValidator<UniqueVideoPostTenantIdSlug, Any>"
            )
        )
        assertTrue(
            uniqueValidatorContent.contains(
                "override fun isValid(value: Any?, context: ConstraintValidatorContext): Boolean"
            )
        )
        assertTrue(uniqueValidatorContent.contains("value::class.memberProperties.associateBy"))
        assertTrue(uniqueValidatorContent.contains("Mediator.queries.send("))
        assertTrue(uniqueValidatorContent.contains("return !result.exists"))
        assertFalse(
            uniqueValidatorContent.contains(
                "ConstraintValidator<UniqueVideoPostTenantIdSlug, UniqueVideoPostTenantIdSlugQry.Request>"
            )
        )
    }

    @Test
    fun `aggregate entity preset renders bounded relation-side jpa controls`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-relation")
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
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "jpaImports" to listOf(
                            "jakarta.persistence.CascadeType",
                            "jakarta.persistence.FetchType",
                            "jakarta.persistence.JoinColumn",
                            "jakarta.persistence.ManyToOne",
                            "jakarta.persistence.OneToMany",
                            "jakarta.persistence.OneToOne",
                        ),
                        "imports" to listOf(
                            "com.acme.demo.domain.identity.user.UserProfile",
                            "com.acme.demo.domain.identity.user.CoverProfile",
                            "com.acme.demo.domain.aggregates.video_post.item.VideoPostItem",
                        ),
                        "scalarFields" to listOf(
                            mapOf(
                                "name" to "id",
                                "type" to "Long",
                                "nullable" to false,
                                "columnName" to "id",
                                "isId" to true,
                                "converterTypeRef" to null,
                            )
                        ),
                        "fields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false)
                        ),
                        "relationFields" to listOf(
                            mapOf(
                                "name" to "author",
                                "targetType" to "UserProfile",
                                "targetTypeRef" to "UserProfile",
                                "targetPackageName" to "com.acme.demo.domain.identity.user",
                                "relationType" to "MANY_TO_ONE",
                                "fetchType" to "LAZY",
                                "joinColumn" to "author_id",
                                "nullable" to true,
                                "joinColumnNullable" to false,
                            ),
                            mapOf(
                                "name" to "coverProfile",
                                "targetType" to "CoverProfile",
                                "targetTypeRef" to "CoverProfile",
                                "targetPackageName" to "com.acme.demo.domain.identity.user",
                                "relationType" to "ONE_TO_ONE",
                                "fetchType" to "LAZY",
                                "joinColumn" to "cover_profile_id",
                                "nullable" to true,
                                "joinColumnNullable" to true,
                            ),
                            mapOf(
                                "name" to "items",
                                "targetType" to "VideoPostItem",
                                "targetTypeRef" to "VideoPostItem",
                                "targetPackageName" to "com.acme.demo.domain.aggregates.video_post.item",
                                "relationType" to "ONE_TO_MANY",
                                "fetchType" to "LAZY",
                                "joinColumn" to "video_post_id",
                                "cascadeAll" to true,
                                "orphanRemoval" to true,
                                "joinColumnNullable" to false,
                            )
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
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
        val constructorSection = content.substringBefore(") {")
        val bodySection = content.substringAfter(") {")
        assertTrue(content.contains("import jakarta.persistence.CascadeType"))
        assertTrue(content.contains("import jakarta.persistence.FetchType"))
        assertTrue(content.contains("import jakarta.persistence.JoinColumn"))
        assertTrue(content.contains("import jakarta.persistence.ManyToOne"))
        assertTrue(content.contains("import jakarta.persistence.OneToMany"))
        assertTrue(content.contains("import jakarta.persistence.OneToOne"))
        assertTrue(content.contains("@Entity"))
        assertTrue(content.contains("@Table(name = \"video_post\")"))
        assertTrue(content.contains("import com.acme.demo.domain.identity.user.UserProfile"))
        assertTrue(content.contains("import com.acme.demo.domain.identity.user.CoverProfile"))
        assertTrue(content.contains("import com.acme.demo.domain.aggregates.video_post.item.VideoPostItem"))
        assertTrue(content.contains("class VideoPost("))
        assertFalse(content.contains("data class VideoPost("))
        assertTrue(content.contains(") {"))
        assertTrue(constructorSection.contains("val id: Long"))
        assertFalse(constructorSection.contains("author"))
        assertFalse(constructorSection.contains("coverProfile"))
        assertFalse(constructorSection.contains("items"))
        assertTrue(content.contains("@ManyToOne(fetch = FetchType.LAZY)"))
        assertTrue(content.contains("@JoinColumn(name = \"author_id\", nullable = false)"))
        assertTrue(bodySection.contains("var author: UserProfile? = null"))
        assertTrue(content.contains("@OneToOne(fetch = FetchType.LAZY)"))
        assertTrue(bodySection.contains("@JoinColumn(name = \"cover_profile_id\", nullable = true)"))
        assertTrue(bodySection.contains("var coverProfile: CoverProfile? = null"))
        assertTrue(content.contains("@OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)"))
        assertTrue(content.contains("@JoinColumn(name = \"video_post_id\", nullable = false)"))
        assertFalse(content.contains("mappedBy ="))
        assertTrue(bodySection.contains("var items: List<VideoPostItem> = emptyList()"))
    }

    @Test
    fun `aggregate entity preset renders inverse read only many to one relation controls`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-inverse-relation")
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
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post_item/VideoPostItem.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post_item",
                        "typeName" to "VideoPostItem",
                        "comment" to "video post item",
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post_item",
                        ),
                        "jpaImports" to listOf(
                            "jakarta.persistence.FetchType",
                            "jakarta.persistence.JoinColumn",
                            "jakarta.persistence.ManyToOne",
                        ),
                        "imports" to listOf("com.acme.demo.domain.aggregates.video_post.VideoPost"),
                        "scalarFields" to listOf(
                            mapOf(
                                "name" to "id",
                                "type" to "Long",
                                "nullable" to false,
                                "columnName" to "id",
                                "isId" to true,
                                "converterTypeRef" to null,
                            ),
                            mapOf(
                                "name" to "videoPostId",
                                "type" to "Long",
                                "nullable" to false,
                                "columnName" to "video_post_id",
                                "isId" to false,
                                "converterTypeRef" to null,
                            ),
                        ),
                        "fields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false),
                            mapOf("name" to "videoPostId", "type" to "Long", "nullable" to false),
                        ),
                        "relationFields" to listOf(
                            mapOf(
                                "name" to "videoPost",
                                "targetType" to "VideoPost",
                                "targetTypeRef" to "VideoPost",
                                "targetPackageName" to "com.acme.demo.domain.aggregates.video_post",
                                "relationType" to "MANY_TO_ONE",
                                "fetchType" to "LAZY",
                                "joinColumn" to "video_post_id",
                                "nullable" to false,
                                "joinColumnNullable" to false,
                                "readOnly" to true,
                                "insertable" to false,
                                "updatable" to false,
                            )
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
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

        assertTrue(content.contains("@Column(name = \"video_post_id\")"))
        assertTrue(content.contains("val videoPostId: Long"))
        assertTrue(content.contains("@ManyToOne(fetch = FetchType.LAZY)"))
        assertTrue(
            content.contains(
                "@JoinColumn(name = \"video_post_id\", nullable = false, insertable = false, updatable = false)"
            )
        )
        assertTrue(content.contains("lateinit var videoPost: VideoPost"))
        assertFalse(content.contains("mappedBy ="))
        assertFalse(content.contains("JoinTable"))
        assertFalse(content.contains("ManyToMany"))
    }

    @Test
    fun `aggregate entity preset does not render cascade type import for direct-only relation controls`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-direct-relation")
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
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "jpaImports" to listOf(
                            "jakarta.persistence.FetchType",
                            "jakarta.persistence.JoinColumn",
                            "jakarta.persistence.ManyToOne",
                            "jakarta.persistence.OneToOne",
                        ),
                        "imports" to listOf(
                            "com.acme.demo.domain.identity.user.UserProfile",
                            "com.acme.demo.domain.identity.user.CoverProfile",
                        ),
                        "scalarFields" to listOf(
                            mapOf(
                                "name" to "id",
                                "type" to "Long",
                                "nullable" to false,
                                "columnName" to "id",
                                "isId" to true,
                                "converterTypeRef" to null,
                            )
                        ),
                        "fields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false)
                        ),
                        "relationFields" to listOf(
                            mapOf(
                                "name" to "author",
                                "targetType" to "UserProfile",
                                "targetTypeRef" to "UserProfile",
                                "targetPackageName" to "com.acme.demo.domain.identity.user",
                                "relationType" to "MANY_TO_ONE",
                                "fetchType" to "LAZY",
                                "joinColumn" to "author_id",
                                "nullable" to false,
                                "joinColumnNullable" to false,
                            ),
                            mapOf(
                                "name" to "coverProfile",
                                "targetType" to "CoverProfile",
                                "targetTypeRef" to "CoverProfile",
                                "targetPackageName" to "com.acme.demo.domain.identity.user",
                                "relationType" to "ONE_TO_ONE",
                                "fetchType" to "LAZY",
                                "joinColumn" to "cover_profile_id",
                                "nullable" to true,
                                "joinColumnNullable" to true,
                            )
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
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

        assertTrue(content.contains("import jakarta.persistence.ManyToOne"))
        assertTrue(content.contains("import jakarta.persistence.OneToOne"))
        assertFalse(content.contains("import jakarta.persistence.CascadeType"))
        assertTrue(content.contains("@JoinColumn(name = \"author_id\", nullable = false)"))
        assertTrue(content.contains("@JoinColumn(name = \"cover_profile_id\", nullable = true)"))
        assertFalse(content.contains("@OneToMany("))
    }

    @Test
    fun `aggregate entity preset renders one-to-many controls from planner flags`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-collection-controls")
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
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "jpaImports" to listOf(
                            "jakarta.persistence.FetchType",
                            "jakarta.persistence.JoinColumn",
                            "jakarta.persistence.OneToMany",
                        ),
                        "imports" to listOf(
                            "com.acme.demo.domain.aggregates.video_post.item.VideoPostItem",
                        ),
                        "scalarFields" to listOf(
                            mapOf(
                                "name" to "id",
                                "type" to "Long",
                                "nullable" to false,
                                "columnName" to "id",
                                "isId" to true,
                                "converterTypeRef" to null,
                            )
                        ),
                        "fields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false)
                        ),
                        "relationFields" to listOf(
                            mapOf(
                                "name" to "items",
                                "targetType" to "VideoPostItem",
                                "targetTypeRef" to "VideoPostItem",
                                "targetPackageName" to "com.acme.demo.domain.aggregates.video_post.item",
                                "relationType" to "ONE_TO_MANY",
                                "fetchType" to "LAZY",
                                "joinColumn" to "video_post_id",
                                "cascadeAll" to false,
                                "orphanRemoval" to false,
                                "joinColumnNullable" to false,
                            )
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
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

        assertFalse(content.contains("import jakarta.persistence.CascadeType"))
        assertTrue(content.contains("@OneToMany(fetch = FetchType.LAZY, orphanRemoval = false)"))
        assertFalse(content.contains("cascade = [CascadeType.ALL]"))
        assertTrue(content.contains("@JoinColumn(name = \"video_post_id\", nullable = false)"))
    }

    @Test
    fun `aggregate entity preset does not double map relation join columns as scalar fields`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-relation-scalar-boundary")
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
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "hasConverterFields" to false,
                        "jpaImports" to listOf(
                            "jakarta.persistence.FetchType",
                            "jakarta.persistence.JoinColumn",
                            "jakarta.persistence.ManyToOne",
                        ),
                        "imports" to listOf("com.acme.demo.domain.identity.user.UserProfile"),
                        "scalarFields" to listOf(
                            mapOf(
                                "name" to "id",
                                "type" to "Long",
                                "nullable" to false,
                                "columnName" to "id",
                                "isId" to true,
                                "converterTypeRef" to null,
                            ),
                            mapOf(
                                "name" to "title",
                                "type" to "String",
                                "nullable" to false,
                                "columnName" to "title",
                                "isId" to false,
                                "converterTypeRef" to null,
                            ),
                        ),
                        "fields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false),
                            mapOf("name" to "title", "type" to "String", "nullable" to false),
                        ),
                        "relationFields" to listOf(
                            mapOf(
                                "name" to "author",
                                "targetType" to "UserProfile",
                                "targetTypeRef" to "UserProfile",
                                "targetPackageName" to "com.acme.demo.domain.identity.user",
                                "relationType" to "MANY_TO_ONE",
                                "fetchType" to "LAZY",
                                "joinColumn" to "author_id",
                                "nullable" to false,
                                "joinColumnNullable" to false,
                            ),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
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

        assertTrue(content.contains("@JoinColumn(name = \"author_id\", nullable = false)"))
        assertTrue(content.contains("lateinit var author: UserProfile"))
        assertFalse(content.contains("@Column(name = \"author_id\")"))
        assertFalse(content.contains("val author_id:"))
    }

    @Test
    fun `aggregate entity preset does not double map join column when scalar field name differs from column name`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-relation-column-boundary")
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
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "hasConverterFields" to false,
                        "jpaImports" to listOf(
                            "jakarta.persistence.FetchType",
                            "jakarta.persistence.JoinColumn",
                            "jakarta.persistence.ManyToOne",
                        ),
                        "imports" to listOf("com.acme.demo.domain.identity.user.UserProfile"),
                        "scalarFields" to listOf(
                            mapOf(
                                "name" to "id",
                                "type" to "Long",
                                "nullable" to false,
                                "columnName" to "id",
                                "isId" to true,
                                "converterTypeRef" to null,
                            ),
                            mapOf(
                                "name" to "title",
                                "type" to "String",
                                "nullable" to false,
                                "columnName" to "title",
                                "isId" to false,
                                "converterTypeRef" to null,
                            ),
                        ),
                        "fields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false),
                            mapOf("name" to "title", "type" to "String", "nullable" to false),
                        ),
                        "relationFields" to listOf(
                            mapOf(
                                "name" to "author",
                                "targetType" to "UserProfile",
                                "targetTypeRef" to "UserProfile",
                                "targetPackageName" to "com.acme.demo.domain.identity.user",
                                "relationType" to "MANY_TO_ONE",
                                "fetchType" to "LAZY",
                                "joinColumn" to "author_id",
                                "nullable" to false,
                                "joinColumnNullable" to false,
                            ),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
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

        assertTrue(content.contains("@JoinColumn(name = \"author_id\", nullable = false)"))
        assertTrue(content.contains("lateinit var author: UserProfile"))
        assertFalse(content.contains("@Column(name = \"author_id\")"))
        assertFalse(content.contains("val authorId:"))
    }

    @Test
    fun `aggregate entity preset does not render unsupported relation types`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-unsupported-relation")
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
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "tableName" to "video_post",
                        "jpaImports" to emptyList<String>(),
                        "imports" to listOf("com.acme.demo.domain.identity.user.UserProfile"),
                        "scalarFields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false)
                        ),
                        "fields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false)
                        ),
                        "relationFields" to listOf(
                            mapOf(
                                "name" to "authors",
                                "targetType" to "UserProfile",
                                "targetTypeRef" to "UserProfile",
                                "targetPackageName" to "com.acme.demo.domain.identity.user",
                                "relationType" to "MANY_TO_MANY",
                                "fetchType" to "LAZY",
                                "joinColumn" to "author_id",
                            )
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
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
        assertFalse(content.contains("val authors:"))
        assertFalse(content.contains("MANY_TO_MANY"))
    }

    @Test
    fun `aggregate entity preset renders bounded Jakarta baseline annotations`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-jakarta-baseline")
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
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "hasConverterFields" to true,
                        "scalarFields" to listOf(
                            mapOf(
                                "name" to "id",
                                "type" to "Long",
                                "nullable" to false,
                                "columnName" to "id",
                                "isId" to true,
                                "converterTypeRef" to null,
                                "converterClassRef" to null,
                            ),
                            mapOf(
                                "name" to "status",
                                "type" to "com.acme.demo.domain.shared.enums.Status",
                                "nullable" to false,
                                "columnName" to "status",
                                "isId" to false,
                                "converterTypeRef" to "com.acme.demo.domain.shared.enums.Status",
                                "converterClassRef" to "com.acme.demo.domain.shared.enums.Status.Converter",
                            ),
                        ),
                        "relationFields" to emptyList<Map<String, Any?>>(),
                        "imports" to emptyList<String>(),
                        "jpaImports" to emptyList<String>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
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

        assertTrue(content.contains("@Entity"))
        assertTrue(content.contains("@Table(name = \"video_post\")"))
        assertTrue(content.contains("@Id"))
        assertTrue(content.contains("@Column(name = \"id\")"))
        assertTrue(content.contains("@Column(name = \"status\")"))
        assertTrue(content.contains("import jakarta.persistence.Convert"))
        assertTrue(content.contains("@Convert(converter = com.acme.demo.domain.shared.enums.Status.Converter::class)"))
        assertTrue(content.contains("data class VideoPost("))
        assertFalse(content.contains("\nclass VideoPost("))
        assertFalse(content.contains("@GeneratedValue"))
        assertFalse(content.contains("@Version"))
        assertFalse(content.contains("@DynamicInsert"))
        assertFalse(content.contains("@DynamicUpdate"))
        assertFalse(content.contains("@SQLDelete"))
        assertFalse(content.contains("@Where"))
        assertFalse(content.contains("@GenericGenerator"))
    }

    @Test
    fun `aggregate entity template renders explicit persistence field behavior`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-persistence-field-behavior")
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
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "hasConverterFields" to false,
                        "hasGeneratedValueFields" to true,
                        "hasVersionFields" to true,
                        "scalarFields" to listOf(
                            mapOf(
                                "fieldName" to "id",
                                "fieldType" to "Long",
                                "name" to "id",
                                "type" to "Long",
                                "columnName" to "id",
                                "isId" to true,
                                "generatedValueStrategy" to "IDENTITY",
                            ),
                            mapOf(
                                "fieldName" to "version",
                                "fieldType" to "Long",
                                "name" to "version",
                                "type" to "Long",
                                "columnName" to "version",
                                "isVersion" to true,
                            ),
                            mapOf(
                                "fieldName" to "title",
                                "fieldType" to "String",
                                "name" to "title",
                                "type" to "String",
                                "columnName" to "title",
                                "generatedValueStrategy" to "IDENTITY",
                            ),
                            mapOf(
                                "fieldName" to "created_by",
                                "fieldType" to "String",
                                "name" to "created_by",
                                "type" to "String",
                                "columnName" to "created_by",
                                "insertable" to false,
                                "updatable" to true,
                            ),
                            mapOf(
                                "fieldName" to "updated_by",
                                "fieldType" to "String",
                                "name" to "updated_by",
                                "type" to "String",
                                "columnName" to "updated_by",
                                "insertable" to true,
                                "updatable" to false,
                            ),
                        ),
                        "fields" to listOf(
                            mapOf("fieldName" to "id", "fieldType" to "Long"),
                            mapOf("fieldName" to "version", "fieldType" to "Long"),
                            mapOf("fieldName" to "title", "fieldType" to "String"),
                            mapOf("fieldName" to "created_by", "fieldType" to "String"),
                            mapOf("fieldName" to "updated_by", "fieldType" to "String"),
                        ),
                        "relationFields" to emptyList<Map<String, Any?>>(),
                        "imports" to emptyList<String>(),
                        "jpaImports" to emptyList<String>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
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

        assertFalse(content.contains("\n\n\n"), content)
        assertFalse(Regex("(?m)^\\s+$").containsMatchIn(content), content)
        assertTrue(content.startsWith("package com.acme.demo.domain.aggregates.video_post\n\nimport "))
        assertTrue(content.contains("\n\n@Entity"), content)
        assertTrue(content.contains("import jakarta.persistence.GeneratedValue"))
        assertTrue(content.contains("import jakarta.persistence.GenerationType"))
        assertFalse(content.contains("import org.hibernate.annotations.GenericGenerator"))
        assertTrue(content.contains("import jakarta.persistence.Version"))
        assertTrue(content.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
        assertFalse(content.contains("@GeneratedValue(generator ="))
        assertFalse(content.contains("@GenericGenerator("))
        assertTrue(content.contains("@Version"))
        assertTrue(content.contains("@Column(name = \"version\")"))
        assertTrue(content.contains("@Column(name = \"title\")"))
        assertTrue(content.contains("@Column(name = \"created_by\", insertable = false, updatable = true)"))
        assertTrue(content.contains("@Column(name = \"updated_by\", insertable = true, updatable = false)"))
        assertFalse(content.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)\n    @Column(name = \"title\")"))
    }

    @Test
    fun `aggregate entity template renders bounded custom generator annotations`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-custom-generator")
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
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "hasConverterFields" to false,
                        "hasGeneratedValueFields" to false,
                        "hasGenericGeneratorFields" to true,
                        "hasVersionFields" to false,
                        "scalarFields" to listOf(
                            mapOf(
                                "fieldName" to "id",
                                "fieldType" to "Long",
                                "name" to "id",
                                "type" to "Long",
                                "columnName" to "id",
                                "isId" to true,
                                "generatedValueStrategy" to "IDENTITY",
                                "generatedValueGenerator" to "snowflakeIdGenerator",
                                "genericGeneratorName" to "snowflakeIdGenerator",
                                "genericGeneratorStrategy" to "snowflakeIdGenerator",
                            ),
                            mapOf(
                                "fieldName" to "title",
                                "fieldType" to "String",
                                "name" to "title",
                                "type" to "String",
                                "columnName" to "title",
                            ),
                        ),
                        "fields" to listOf(
                            mapOf("fieldName" to "id", "fieldType" to "Long"),
                            mapOf("fieldName" to "title", "fieldType" to "String"),
                        ),
                        "relationFields" to emptyList<Map<String, Any?>>(),
                        "imports" to emptyList<String>(),
                        "jpaImports" to emptyList<String>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
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

        assertTrue(content.contains("import jakarta.persistence.GeneratedValue"))
        assertTrue(content.contains("import org.hibernate.annotations.GenericGenerator"))
        assertFalse(content.contains("import jakarta.persistence.GenerationType"))
        assertTrue(content.contains("@GeneratedValue(generator = \"snowflakeIdGenerator\")"))
        assertTrue(content.contains("@GenericGenerator(name = \"snowflakeIdGenerator\", strategy = \"snowflakeIdGenerator\")"))
        assertFalse(content.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
    }

    @Test
    fun `aggregate entity template keeps bounded imports and plain column when persistence controls are absent`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-persistence-import-gating")
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
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "hasConverterFields" to false,
                        "hasGeneratedValueFields" to false,
                        "hasVersionFields" to false,
                        "scalarFields" to listOf(
                            mapOf(
                                "fieldName" to "id",
                                "fieldType" to "Long",
                                "name" to "id",
                                "type" to "Long",
                                "columnName" to "id",
                                "isId" to true,
                            ),
                            mapOf(
                                "fieldName" to "title",
                                "fieldType" to "String",
                                "name" to "title",
                                "type" to "String",
                                "columnName" to "title",
                            ),
                        ),
                        "fields" to listOf(
                            mapOf("fieldName" to "id", "fieldType" to "Long"),
                            mapOf("fieldName" to "title", "fieldType" to "String"),
                        ),
                        "relationFields" to emptyList<Map<String, Any?>>(),
                        "imports" to emptyList<String>(),
                        "jpaImports" to emptyList<String>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
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

        assertFalse(content.contains("import jakarta.persistence.GeneratedValue"))
        assertFalse(content.contains("import jakarta.persistence.GenerationType"))
        assertFalse(content.contains("import jakarta.persistence.Version"))
        assertFalse(content.contains("import org.hibernate.annotations.DynamicInsert"))
        assertFalse(content.contains("import org.hibernate.annotations.DynamicUpdate"))
        assertFalse(content.contains("import org.hibernate.annotations.SQLDelete"))
        assertFalse(content.contains("import org.hibernate.annotations.Where"))
        assertFalse(content.contains("@GeneratedValue"))
        assertFalse(content.contains("@Version"))
        assertTrue(content.contains("@Column(name = \"title\")"))
    }

    @Test
    fun `aggregate entity template renders provider specific persistence controls`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-provider-persistence")
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
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "hasConverterFields" to false,
                        "hasGeneratedValueFields" to false,
                        "hasVersionFields" to false,
                        "dynamicInsert" to true,
                        "dynamicUpdate" to true,
                        "softDeleteSql" to "update \"video_post\" set \"deleted\" = 1 where \"id\" = ? and \"version\" = ?",
                        "softDeleteWhereClause" to "\"deleted\" = 0",
                        "scalarFields" to emptyList<Map<String, Any?>>(),
                        "fields" to emptyList<Map<String, Any?>>(),
                        "relationFields" to emptyList<Map<String, Any?>>(),
                        "imports" to emptyList<String>(),
                        "jpaImports" to emptyList<String>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
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

        assertTrue(content.contains("import org.hibernate.annotations.DynamicInsert"))
        assertTrue(content.contains("import org.hibernate.annotations.DynamicUpdate"))
        assertTrue(content.contains("import org.hibernate.annotations.SQLDelete"))
        assertTrue(content.contains("import org.hibernate.annotations.Where"))
        assertTrue(content.contains("@DynamicInsert"))
        assertTrue(content.contains("@DynamicUpdate"))
        assertTrue(content.contains("@SQLDelete(sql = \"update \\\"video_post\\\" set \\\"deleted\\\" = 1 where \\\"id\\\" = ? and \\\"version\\\" = ?\")"))
        assertTrue(content.contains("@Where(clause = \"\\\"deleted\\\" = 0\")"))
    }

    @Test
    fun `falls back to preset aggregate enum and translation templates`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-enum")
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
                    templateId = "aggregate/enum.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/shared/enums/Status.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.shared.enums",
                        "typeName" to "Status",
                        "aggregateName" to null,
                        "items" to listOf(
                            mapOf("value" to 0, "name" to "DRAFT", "description" to "Draft"),
                            mapOf("value" to 1, "name" to "PUBLISHED", "description" to "Published"),
                        ),
                        "translationTypeName" to "StatusTranslation",
                        "translationEnabled" to true,
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                ),
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "adapter",
                    templateId = "aggregate/enum_translation.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/domain/translation/shared/StatusTranslation.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.translation.shared",
                        "typeName" to "StatusTranslation",
                        "enumTypeName" to "Status",
                        "enumTypeFqn" to "com.acme.demo.domain.shared.enums.Status",
                        "translationTypeConst" to "STATUS_CODE_TO_DESC",
                        "translationTypeValue" to "status_code_to_desc",
                        "enumNameField" to "description",
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                ),
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

        val enumContent = rendered.single { it.outputPath.endsWith("/domain/shared/enums/Status.kt") }.content
        val translationContent = rendered.single {
            it.outputPath.endsWith("/domain/translation/shared/StatusTranslation.kt")
        }.content

        assertTrue(enumContent.contains("enum class Status("))
        assertTrue(enumContent.contains("DRAFT(0, \"Draft\")"))
        assertTrue(enumContent.contains("import jakarta.persistence.AttributeConverter"))
        assertFalse(enumContent.contains("import jakarta.persistence.Converter"))
        assertTrue(enumContent.contains("@jakarta.persistence.Converter(autoApply = false)"))
        assertTrue(enumContent.contains("class Converter : AttributeConverter<Status, Int>"))
        assertTrue(enumContent.contains("override fun convertToDatabaseColumn(attribute: Status?): Int?"))
        assertTrue(enumContent.contains("return attribute?.value"))
        assertTrue(enumContent.contains("override fun convertToEntityAttribute(dbData: Int?): Status?"))
        assertTrue(enumContent.contains("return valueOfOrNull(dbData)"))
        assertTrue(translationContent.contains("class StatusTranslation"))
        assertTrue(translationContent.contains("import com.acme.demo.domain.shared.enums.Status"))
        assertTrue(translationContent.contains("@TranslationType(type = \"status_code_to_desc\")"))
        assertTrue(translationContent.contains("const val STATUS_CODE_TO_DESC = \"status_code_to_desc\""))
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
                    generatorId = "design-command",
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
                    generatorId = "design-command",
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

        assertEquals("""["java.time.LocalDateTime","java.util.UUID"]""", rendered.single().content.trim())
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
                    generatorId = "design-command",
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

        assertEquals("""["java.time.LocalDateTime","java.util.UUID"]""", rendered.single().content.trim())
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
                    generatorId = "design-command",
                    moduleRole = "application",
                    templateId = "design/first.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/First.kt",
                    context = emptyMap(),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "design-command",
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
    fun `regular aggregate override templates can use helper`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-aggregate")
        val overrideAggregateDir = Files.createDirectories(overrideDir.resolve("aggregate"))
        overrideAggregateDir.resolve("schema.kt.peb")
            .writeText(
                """
                {{ use("java.time.LocalDateTime") -}}
                {% for importValue in imports %}
                import {{ importValue }}
                {% endfor %}
                class {{ typeName }}
                """.trimIndent()
            )

        val rendered = PebbleArtifactRenderer(
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
                        "entityName" to "Order",
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

        val content = rendered.single().content
        assertTrue(content.contains("import java.time.LocalDateTime"))
        assertTrue(content.contains("import java.util.UUID"))
        assertTrue(content.contains("class OrderSchema"))
    }

    @Test
    fun `regular aggregate use helper renders deterministically sorted imports`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-aggregate-sorted")
        val overrideAggregateDir = Files.createDirectories(overrideDir.resolve("aggregate"))
        overrideAggregateDir.resolve("schema.kt.peb")
            .writeText(
                """
                {{ use("java.time.ZonedDateTime") -}}
                {{ use("java.time.LocalDateTime") -}}
                {% for importValue in imports %}
                {{ importValue }}
                {% endfor %}
                """.trimIndent()
            )

        val rendered = PebbleArtifactRenderer(
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
                        "entityName" to "Order",
                        "imports" to listOf(
                            "java.util.UUID",
                            " java.time.Instant ",
                            "java.time.LocalDateTime",
                            "java.util.UUID",
                            ""
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

        val imports = rendered.single().content
            .lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotEmpty() }
            .toList()

        assertEquals(
            listOf(
                "java.time.Instant",
                "java.time.LocalDateTime",
                "java.time.ZonedDateTime",
                "java.util.UUID"
            ),
            imports
        )
    }

    @Test
    fun `regular aggregate use helper fails fast on import conflicts`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-aggregate-conflict")
        val overrideAggregateDir = Files.createDirectories(overrideDir.resolve("aggregate"))
        overrideAggregateDir.resolve("schema.kt.peb")
            .writeText("""{{ use("com.bar.Status") -}}{{ imports(imports) | json | raw }}""")

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
                            "entityName" to "Order",
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
    fun `regular aggregate ignores non-list imports payload when helper contract is unused`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-aggregate-compat-plain")
        val overrideAggregateDir = Files.createDirectories(overrideDir.resolve("aggregate"))
        overrideAggregateDir.resolve("schema.kt.peb")
            .writeText(
                """
                package {{ packageName }}
                class {{ typeName }}
                """.trimIndent()
            )

        val rendered = PebbleArtifactRenderer(
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
                        "entityName" to "Order",
                        "imports" to "not-a-list"
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
        assertTrue(content.contains("package com.acme.demo.domain.aggregates.order"))
        assertTrue(content.contains("class OrderSchema"))
    }

    @Test
    fun `regular aggregate map carrier preserves extra payload while merging collected imports`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-aggregate-map-shape")
        val overrideAggregateDir = Files.createDirectories(overrideDir.resolve("aggregate"))
        overrideAggregateDir.resolve("schema.kt.peb")
            .writeText(
                """
                {{ use("java.time.LocalDateTime") -}}
                extra={{ imports.extra }}
                merged={{ imports(imports) | json | raw }}
                """.trimIndent()
            )

        val rendered = PebbleArtifactRenderer(
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
                        "entityName" to "Order",
                        "imports" to mapOf(
                            "imports" to listOf("java.util.UUID"),
                            "extra" to "expected-extra"
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

        val content = rendered.single().content
        assertTrue(content.contains("extra=expected-extra"))
        assertTrue(content.contains("""merged=["java.time.LocalDateTime","java.util.UUID"]"""))
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
                    generatorId = "design-command",
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
                    generatorId = "design-command",
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
                    generatorId = "design-command",
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
        assertFalse(content.contains("import com.foo.Status"))
        assertFalse(content.contains("import com.bar.Status"))
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
                        generatorId = "design-command",
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
                        generatorId = "design-command",
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
                        generatorId = "design-command",
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
                        generatorId = "design-command",
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
                        generatorId = "design-command",
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
                        generatorId = "design-command",
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
                        generatorId = "design-command",
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

    @Test
    fun `default query handler preset renders service stub`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-query-handler-contract")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design-query-handler",
                    moduleRole = "adapter",
                    templateId = "design/query_handler.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderQryHandler.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.queries.order.read",
                        "typeName" to "FindOrderQryHandler",
                        "description" to "find order query",
                        "queryTypeName" to "FindOrderQry",
                        "imports" to listOf("com.acme.demo.application.queries.order.read.FindOrderQry"),
                        "responseFields" to listOf(
                            mapOf("name" to "responseStatus"),
                            mapOf("name" to "snapshot"),
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
        assertTrue(content.contains("import org.springframework.stereotype.Service"))
        assertTrue(content.contains("import com.only4.cap4k.ddd.core.application.query.Query"))
        assertTrue(content.contains("import com.acme.demo.application.queries.order.read.FindOrderQry"))
        assertTrue(content.contains("class FindOrderQryHandler : Query<FindOrderQry.Request, FindOrderQry.Response>"))
        assertTrue(content.contains("responseStatus = TODO(\"set responseStatus\")"))
        assertTrue(content.contains("snapshot = TODO(\"set snapshot\")"))
        val normalizedContent = content.normalizedLineEndings()
        assertTrue(normalizedContent.contains("package com.acme.demo.adapter.queries.order.read\n\nimport"))
        assertTrue(
            normalizedContent.contains(
                "        return FindOrderQry.Response(\n" +
                    "            responseStatus = TODO(\"set responseStatus\"),\n" +
                    "            snapshot = TODO(\"set snapshot\")\n" +
                    "        )"
            )
        )
        assertFalse(Regex("""Response\(\n\s*\n""").containsMatchIn(normalizedContent))
        assertFalse(Regex("""TODO\("set responseStatus"\),\n\s*\n\s*snapshot""").containsMatchIn(normalizedContent))
        assertFalse(Regex("""TODO\("set snapshot"\)\n\s*\n\s*\)""").containsMatchIn(normalizedContent))
    }

    @Test
    fun `bounded query handler presets render list and page contracts`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-bounded-query-handler-contracts")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design-query-handler",
                    moduleRole = "adapter",
                    templateId = "design/query_list_handler.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderListQryHandler.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.queries.order.read",
                        "typeName" to "FindOrderListQryHandler",
                        "description" to "find order list query",
                        "queryTypeName" to "FindOrderListQry",
                        "imports" to listOf("com.acme.demo.application.queries.order.read.FindOrderListQry"),
                        "responseFields" to listOf(
                            mapOf("name" to "responseStatus"),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "design-query-handler",
                    moduleRole = "adapter",
                    templateId = "design/query_page_handler.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderPageQryHandler.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.queries.order.read",
                        "typeName" to "FindOrderPageQryHandler",
                        "description" to "find order page query",
                        "queryTypeName" to "FindOrderPageQry",
                        "imports" to listOf("com.acme.demo.application.queries.order.read.FindOrderPageQry"),
                        "responseFields" to listOf(
                            mapOf("name" to "responseStatus"),
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

        val listContent = rendered[0].content
        assertTrue(listContent.normalizedLineEndings().contains("package com.acme.demo.adapter.queries.order.read\n\nimport"))
        assertTrue(listContent.contains("import com.only4.cap4k.ddd.core.application.query.ListQuery"))
        assertTrue(listContent.contains("import com.acme.demo.application.queries.order.read.FindOrderListQry"))
        assertTrue(listContent.contains("class FindOrderListQryHandler : ListQuery<FindOrderListQry.Request, FindOrderListQry.Response>"))
        assertTrue(listContent.contains("override fun exec(request: FindOrderListQry.Request): List<FindOrderListQry.Response>"))
        assertTrue(listContent.contains("return listOf("))
        assertTrue(listContent.contains("responseStatus = TODO(\"set responseStatus\")"))
        assertTrue(
            listContent.normalizedLineEndings().contains(
                "            FindOrderListQry.Response(\n" +
                    "                responseStatus = TODO(\"set responseStatus\")\n" +
                    "            )"
            )
        )

        val pageContent = rendered[1].content
        assertTrue(pageContent.normalizedLineEndings().contains("package com.acme.demo.adapter.queries.order.read\n\nimport"))
        assertTrue(pageContent.contains("import com.only4.cap4k.ddd.core.share.PageData"))
        assertTrue(pageContent.contains("import com.only4.cap4k.ddd.core.application.query.PageQuery"))
        assertTrue(pageContent.contains("import com.acme.demo.application.queries.order.read.FindOrderPageQry"))
        assertTrue(pageContent.contains("class FindOrderPageQryHandler : PageQuery<FindOrderPageQry.Request, FindOrderPageQry.Response>"))
        assertTrue(pageContent.contains("override fun exec(request: FindOrderPageQry.Request): PageData<FindOrderPageQry.Response>"))
        assertTrue(pageContent.contains("return PageData.create(request, 1L, listOf("))
        assertTrue(pageContent.contains("responseStatus = TODO(\"set responseStatus\")"))
        assertTrue(
            pageContent.normalizedLineEndings().contains(
                "            FindOrderPageQry.Response(\n" +
                    "                responseStatus = TODO(\"set responseStatus\")\n" +
                    "            )"
            )
        )
    }

    @Test
    fun `query handler presets return object response when response fields are empty`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-query-handler-empty-response")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design-query-handler",
                    moduleRole = "adapter",
                    templateId = "design/query_handler.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderQryHandler.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.queries.order.read",
                        "typeName" to "FindOrderQryHandler",
                        "description" to "find order query",
                        "queryTypeName" to "FindOrderQry",
                        "imports" to listOf("com.acme.demo.application.queries.order.read.FindOrderQry"),
                        "responseFields" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "design-query-handler",
                    moduleRole = "adapter",
                    templateId = "design/query_list_handler.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderListQryHandler.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.queries.order.read",
                        "typeName" to "FindOrderListQryHandler",
                        "description" to "find order list query",
                        "queryTypeName" to "FindOrderListQry",
                        "imports" to listOf("com.acme.demo.application.queries.order.read.FindOrderListQry"),
                        "responseFields" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "design-query-handler",
                    moduleRole = "adapter",
                    templateId = "design/query_page_handler.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderPageQryHandler.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.queries.order.read",
                        "typeName" to "FindOrderPageQryHandler",
                        "description" to "find order page query",
                        "queryTypeName" to "FindOrderPageQry",
                        "imports" to listOf("com.acme.demo.application.queries.order.read.FindOrderPageQry"),
                        "responseFields" to emptyList<Map<String, Any?>>(),
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

        val defaultContent = rendered[0].content
        assertTrue(defaultContent.contains("return FindOrderQry.Response"))
        assertFalse(defaultContent.contains("return FindOrderQry.Response("))

        val listContent = rendered[1].content
        assertTrue(listContent.contains("return emptyList()"))

        val pageContent = rendered[2].content
        assertTrue(pageContent.contains("return PageData.empty(pageSize = request.pageSize, pageNum = request.pageNum)"))
    }

    @Test
    fun `default client preset uses request param contract and helper-driven fields`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-client-contract")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design-client",
                    moduleRole = "application",
                    templateId = "design/client.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/distributed/clients/authorize/IssueTokenCli.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.distributed.clients.authorize",
                        "typeName" to "IssueTokenCli",
                        "imports" to listOf(
                            "java.time.LocalDateTime",
                            "java.util.UUID",
                        ),
                        "requestFields" to listOf(
                            mapOf("name" to "account", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"guest\""),
                            mapOf("name" to "issuedAt", "renderedType" to "LocalDateTime", "nullable" to false),
                            mapOf("name" to "requestStatus", "renderedType" to "com.foo.Status", "nullable" to false),
                            mapOf("name" to "profile", "renderedType" to "Profile?", "nullable" to true),
                        ),
                        "requestNestedTypes" to listOf(
                            mapOf(
                                "name" to "Profile",
                                "fields" to listOf(
                                    mapOf("name" to "profileId", "renderedType" to "UUID", "nullable" to false),
                                    mapOf("name" to "source", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"web\""),
                                ),
                            ),
                        ),
                        "responseFields" to listOf(
                            mapOf("name" to "token", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"demo-token\""),
                            mapOf("name" to "expiresAt", "renderedType" to "LocalDateTime", "nullable" to false),
                            mapOf("name" to "responseStatus", "renderedType" to "com.bar.Status", "nullable" to false),
                            mapOf("name" to "payload", "renderedType" to "Payload?", "nullable" to true),
                        ),
                        "responseNestedTypes" to listOf(
                            mapOf(
                                "name" to "Payload",
                                "fields" to listOf(
                                    mapOf("name" to "traceId", "renderedType" to "UUID", "nullable" to false),
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
        assertTrue(content.contains("import com.only4.cap4k.ddd.core.application.RequestParam"))
        assertTrue(content.contains("import java.time.LocalDateTime"))
        assertTrue(content.contains("import java.util.UUID"))
        assertFalse(content.contains("import com.foo.Status"))
        assertFalse(content.contains("import com.bar.Status"))
        assertTrue(content.contains("object IssueTokenCli"))
        assertTrue(content.contains(") : RequestParam<Response>"))
        assertTrue(content.contains("val account: String = \"guest\""))
        assertTrue(content.contains("val issuedAt: LocalDateTime"))
        assertTrue(content.contains("val requestStatus: com.foo.Status"))
        assertTrue(content.contains("val profile: Profile?"))
        assertFalse(content.contains("val profile: Profile??"))
        assertTrue(content.contains("data class Profile("))
        assertTrue(content.contains("val profileId: UUID"))
        assertTrue(content.contains("val source: String = \"web\""))
        assertTrue(content.contains("val token: String = \"demo-token\""))
        assertTrue(content.contains("val expiresAt: LocalDateTime"))
        assertTrue(content.contains("val responseStatus: com.bar.Status"))
        assertTrue(content.contains("val payload: Payload?"))
        assertFalse(content.contains("val payload: Payload??"))
        assertTrue(content.contains("data class Payload("))
        assertTrue(content.contains("val traceId: UUID"))
    }

    @Test
    fun `default client handler preset renders request handler contract and import list type`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-client-handler-contract")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design-client-handler",
                    moduleRole = "adapter",
                    templateId = "design/client_handler.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/distributed/clients/authorize/IssueTokenCliHandler.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.application.distributed.clients.authorize",
                        "typeName" to "IssueTokenCliHandler",
                        "clientTypeName" to "IssueTokenCli",
                        "imports" to listOf("com.acme.demo.application.distributed.clients.authorize.IssueTokenCli"),
                        "responseFields" to listOf(
                            mapOf("name" to "token"),
                            mapOf("name" to "expiresAt"),
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
        assertTrue(content.contains("import org.springframework.stereotype.Service"))
        assertTrue(content.contains("import com.only4.cap4k.ddd.core.application.RequestHandler"))
        assertTrue(content.contains("import com.acme.demo.application.distributed.clients.authorize.IssueTokenCli"))
        assertTrue(content.contains("class IssueTokenCliHandler : RequestHandler<IssueTokenCli.Request, IssueTokenCli.Response>"))
        assertTrue(content.contains("token = TODO(\"set token\")"))
        assertTrue(content.contains("expiresAt = TODO(\"set expiresAt\")"))
        val normalizedContent = content.normalizedLineEndings()
        assertTrue(normalizedContent.contains("package com.acme.demo.adapter.application.distributed.clients.authorize\n\nimport"))
        assertTrue(
            normalizedContent.contains(
                "        return IssueTokenCli.Response(\n" +
                    "            token = TODO(\"set token\"),\n" +
                    "            expiresAt = TODO(\"set expiresAt\")\n" +
                    "        )"
            )
        )
        assertFalse(Regex("""Response\(\n\s*\n""").containsMatchIn(normalizedContent))
        assertFalse(Regex("""TODO\("set token"\),\n\s*\n\s*expiresAt""").containsMatchIn(normalizedContent))
        assertFalse(Regex("""TODO\("set expiresAt"\)\n\s*\n\s*\)""").containsMatchIn(normalizedContent))
    }

    @Test
    fun `client presets keep empty response output valid for request side and handler side`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-client-empty-response")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design-client",
                    moduleRole = "application",
                    templateId = "design/client.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/distributed/clients/authorize/IssueTokenCli.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.distributed.clients.authorize",
                        "typeName" to "IssueTokenCli",
                        "imports" to emptyList<String>(),
                        "requestFields" to listOf(
                            mapOf("name" to "account", "renderedType" to "String", "nullable" to false),
                        ),
                        "requestNestedTypes" to emptyList<Map<String, Any?>>(),
                        "responseFields" to emptyList<Map<String, Any?>>(),
                        "responseNestedTypes" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "design-client-handler",
                    moduleRole = "adapter",
                    templateId = "design/client_handler.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/distributed/clients/authorize/IssueTokenCliHandler.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.application.distributed.clients.authorize",
                        "typeName" to "IssueTokenCliHandler",
                        "clientTypeName" to "IssueTokenCli",
                        "imports" to listOf("com.acme.demo.application.distributed.clients.authorize.IssueTokenCli"),
                        "responseFields" to emptyList<Map<String, Any?>>(),
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

        val clientContent = rendered[0].content
        assertTrue(clientContent.contains(") : RequestParam<Response>"))
        assertTrue(clientContent.contains("data object Response"))

        val handlerContent = rendered[1].content
        assertTrue(handlerContent.contains("class IssueTokenCliHandler : RequestHandler<IssueTokenCli.Request, IssueTokenCli.Response>"))
        assertTrue(handlerContent.contains("return IssueTokenCli.Response"))
        assertFalse(handlerContent.contains("return IssueTokenCli.Response("))
    }

    @Test
    fun `api payload preset renders outer object helper imports nested request and response hierarchy and defaults`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-api-payload-contract")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design-api-payload",
                    moduleRole = "adapter",
                    templateId = "design/api_payload.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/portal/api/payload/account/BatchSaveAccountList.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.portal.api.payload.account",
                        "typeName" to "BatchSaveAccountList",
                        "imports" to listOf(
                            "  java.time.LocalDateTime  ",
                            "\tjava.util.UUID",
                            "java.time.LocalDateTime",
                            "java.util.UUID  ",
                            "  ",
                        ),
                        "requestFields" to listOf(
                            mapOf("name" to "address", "renderedType" to "Address?", "nullable" to true),
                            mapOf("name" to "note", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"demo\""),
                            mapOf("name" to "requestedAt", "renderedType" to "LocalDateTime", "nullable" to false),
                        ),
                        "requestNestedTypes" to listOf(
                            mapOf(
                                "name" to "Address",
                                "fields" to listOf(
                                    mapOf("name" to "city", "renderedType" to "String", "nullable" to false),
                                    mapOf("name" to "zipCode", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"000000\""),
                                ),
                            ),
                        ),
                        "responseFields" to listOf(
                            mapOf("name" to "result", "renderedType" to "Result?", "nullable" to true),
                            mapOf("name" to "code", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"ok\""),
                            mapOf("name" to "responseId", "renderedType" to "UUID", "nullable" to false),
                        ),
                        "responseNestedTypes" to listOf(
                            mapOf(
                                "name" to "Result",
                                "fields" to listOf(
                                    mapOf("name" to "success", "renderedType" to "Boolean", "nullable" to false, "defaultValue" to "true"),
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
        val importLines = Regex("^import .+$", RegexOption.MULTILINE).findAll(content).map { it.value }.toList()
        val responseIndex = content.indexOf("    data class Response(")
        val requestSection = content.substring(
            startIndex = content.indexOf("    data class Request("),
            endIndex = responseIndex
        )
        val responseSection = content.substring(responseIndex)
        val nestedAddressCount = Regex("^ {8}data class Address\\(", RegexOption.MULTILINE).findAll(content).count()
        val outerAddressCount = Regex("^ {4}data class Address\\(", RegexOption.MULTILINE).findAll(content).count()
        val nestedResultCount = Regex("^ {8}data class Result\\(", RegexOption.MULTILINE).findAll(content).count()
        val outerResultCount = Regex("^ {4}data class Result\\(", RegexOption.MULTILINE).findAll(content).count()

        assertTrue(content.contains("package com.acme.demo.adapter.portal.api.payload.account"))
        assertEquals(
            listOf(
                "import java.time.LocalDateTime",
                "import java.util.UUID",
            ),
            importLines
        )
        assertTrue(content.contains("object BatchSaveAccountList"))
        assertTrue(responseIndex >= 0)
        assertTrue(content.contains("val address: Address?"))
        assertFalse(content.contains("val address: Address??"))
        assertTrue(content.contains("val note: String = \"demo\""))
        assertTrue(content.contains("val requestedAt: LocalDateTime"))
        assertTrue(content.contains("val result: Result?"))
        assertFalse(content.contains("val result: Result??"))
        assertTrue(content.contains("val code: String = \"ok\""))
        assertTrue(content.contains("val responseId: UUID"))
        assertEquals(1, nestedAddressCount)
        assertEquals(0, outerAddressCount)
        assertTrue(requestSection.contains("data class Address("))
        assertTrue(requestSection.contains("val city: String"))
        assertTrue(requestSection.contains("val zipCode: String = \"000000\""))
        assertFalse(requestSection.contains("data class Result("))
        assertEquals(1, nestedResultCount)
        assertEquals(0, outerResultCount)
        assertTrue(responseSection.contains("data class Result("))
        assertTrue(responseSection.contains("val success: Boolean = true"))
        assertFalse(responseSection.contains("data class Address("))
    }

    @Test
    fun `api payload preset supports override template resolution for design api payload`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-design-api-payload")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("api_payload.kt.peb").writeText(
            """
            // override: renderer api payload template
            package {{ packageName }}
            object {{ typeName }}Override
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
                    generatorId = "design-api-payload",
                    moduleRole = "adapter",
                    templateId = "design/api_payload.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/portal/api/payload/account/BatchSaveAccountList.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.portal.api.payload.account",
                        "typeName" to "BatchSaveAccountList",
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
        assertTrue(content.contains("// override: renderer api payload template"))
        assertTrue(content.contains("object BatchSaveAccountListOverride"))
    }

    @Test
    fun `domain event preset resolves domain event template and renders helper-first contract`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-domain-event")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design-domain-event",
                    moduleRole = "domain",
                    templateId = "design/domain_event.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/order/events/OrderCreatedDomainEvent.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.order.events",
                        "typeName" to "OrderCreatedDomainEvent",
                        "description" to "order */ \"created\" event",
                        "descriptionText" to "order */ \"created\" event",
                        "descriptionCommentText" to "order * / \"created\" event",
                        "descriptionKotlinStringLiteral" to "\"order */ \\\"created\\\" event\"",
                        "aggregateName" to "Order",
                        "aggregateType" to "com.acme.demo.domain.order.Order",
                        "persist" to true,
                        "imports" to listOf("java.util.UUID"),
                        "fields" to listOf(
                            mapOf("name" to "reason", "renderedType" to "String", "nullable" to false),
                            mapOf("name" to "snapshot", "renderedType" to "Snapshot?", "nullable" to true),
                        ),
                        "nestedTypes" to listOf(
                            mapOf(
                                "name" to "Snapshot",
                                "fields" to listOf(
                                    mapOf("name" to "traceId", "renderedType" to "UUID", "nullable" to false),
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
        val normalizedContent = content.replace("\r\n", "\n")
        assertTrue(content.contains("@DomainEvent"))
        assertTrue(content.contains("@Aggregate"))
        assertTrue(content.contains("* order * / \"created\" event"))
        assertFalse(content.contains("* order */ \"created\" event"))
        assertTrue(content.contains("description = \"order */ \\\"created\\\" event\""))
        assertFalse(content.contains("&quot;"))
        assertTrue(content.contains("class OrderCreatedDomainEvent("))
        assertTrue(content.contains("val entity: Order"))
        assertTrue(content.indexOf("val entity: Order") < content.indexOf("val reason: String"))
        assertTrue(content.contains("import com.acme.demo.domain.order.Order"))
        assertTrue(content.contains("data class Snapshot("))
        assertTrue(content.contains("val traceId: UUID"))
        assertTrue(
            normalizedContent.contains("package com.acme.demo.domain.order.events\n\nimport"),
            "domain event should keep one blank line between package and imports"
        )
        val importBlock = normalizedContent.substringAfter("package com.acme.demo.domain.order.events\n").substringBefore("/**")
        assertFalse(importBlock.contains("\n\nimport"), "domain event imports should not contain blank lines between imports")
        assertFalse(
            normalizedContent.contains("val entity: Order,\n\n    val reason: String"),
            "domain event constructor fields should not contain blank lines between parameters"
        )
    }

    @Test
    fun `domain event preset resolves domain event handler template and renders event listener contract`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-domain-event-handler")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design-domain-event-handler",
                    moduleRole = "application",
                    templateId = "design/domain_event_handler.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/order/events/OrderCreatedDomainEventSubscriber.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.order.events",
                        "typeName" to "OrderCreatedDomainEventSubscriber",
                        "domainEventTypeName" to "OrderCreatedDomainEvent",
                        "domainEventType" to "com.acme.demo.domain.order.events.OrderCreatedDomainEvent",
                        "aggregateName" to "Order",
                        "description" to "order */ created event",
                        "descriptionCommentText" to "order * / created event",
                        "imports" to listOf("com.acme.demo.domain.order.events.OrderCreatedDomainEvent"),
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
        val normalizedContent = content.replace("\r\n", "\n")
        assertTrue(content.contains("@Service"))
        assertTrue(content.contains("@EventListener(OrderCreatedDomainEvent::class)"))
        assertTrue(content.contains("* order * / created event"))
        assertFalse(content.contains("* order */ created event"))
        assertTrue(content.contains("class OrderCreatedDomainEventSubscriber"))
        assertTrue(content.contains("import com.acme.demo.domain.order.events.OrderCreatedDomainEvent"))
        assertTrue(
            normalizedContent.contains("package com.acme.demo.application.order.events\n\nimport"),
            "domain event handler should keep one blank line between package and imports"
        )
        assertFalse(
            normalizedContent.contains("OrderCreatedDomainEvent\n\nimport"),
            "domain event handler imports should not contain blank lines between imports"
        )
    }

    @Test
    fun `domain event presets support override template resolution for event and handler templates`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-design-domain-event-family")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("domain_event.kt.peb").writeText(
            """
            // override: renderer domain event template
            package {{ packageName }}
            class {{ typeName }}Override
            """.trimIndent()
        )
        overrideDesignDir.resolve("domain_event_handler.kt.peb").writeText(
            """
            // override: renderer domain event handler template
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
                    generatorId = "design-domain-event",
                    moduleRole = "domain",
                    templateId = "design/domain_event.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/order/events/OrderCreatedDomainEvent.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.order.events",
                        "typeName" to "OrderCreatedDomainEvent",
                        "description" to "order created event",
                        "aggregateName" to "Order",
                        "aggregateType" to "com.acme.demo.domain.order.Order",
                        "persist" to true,
                        "imports" to emptyList<String>(),
                        "fields" to emptyList<Map<String, Any?>>(),
                        "nestedTypes" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "design-domain-event-handler",
                    moduleRole = "application",
                    templateId = "design/domain_event_handler.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/order/events/OrderCreatedDomainEventSubscriber.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.order.events",
                        "typeName" to "OrderCreatedDomainEventSubscriber",
                        "domainEventTypeName" to "OrderCreatedDomainEvent",
                        "domainEventType" to "com.acme.demo.domain.order.events.OrderCreatedDomainEvent",
                        "aggregateName" to "Order",
                        "description" to "order created event",
                        "imports" to emptyList<String>(),
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

        val eventContent = rendered[0].content
        val handlerContent = rendered[1].content
        assertTrue(eventContent.contains("// override: renderer domain event template"))
        assertTrue(eventContent.contains("class OrderCreatedDomainEventOverride"))
        assertTrue(handlerContent.contains("// override: renderer domain event handler template"))
        assertTrue(handlerContent.contains("class OrderCreatedDomainEventSubscriberOverride"))
    }

    @Test
    fun `validator preset resolves design validator template and renders constraint contract`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-validator")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design-validator",
                    moduleRole = "application",
                    templateId = "design/validator.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/validators/authorize/IssueToken.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.validators.authorize",
                        "typeName" to "IssueToken",
                        "description" to "issue */ token validator",
                        "descriptionCommentText" to "issue * / token validator",
                        "valueType" to "Long",
                        "imports" to listOf("java.util.UUID"),
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
        assertTrue(content.contains("package com.acme.demo.application.validators.authorize"))
        assertTrue(content.contains("import java.util.UUID"))
        assertTrue(content.contains("* issue * / token validator"))
        assertFalse(content.contains("* issue */ token validator"))
        assertTrue(content.contains("@Constraint"))
        assertTrue(content.contains("annotation class IssueToken("))
        assertTrue(content.contains("val message: String"))
        assertTrue(content.contains("val groups: Array<KClass<*>>"))
        assertTrue(content.contains("val payload: Array<KClass<out Payload>>"))
        assertTrue(content.contains("class Validator : ConstraintValidator<IssueToken, Long>"))
        assertTrue(content.contains("override fun isValid(value: Long?, context: ConstraintValidatorContext): Boolean = true"))
    }

    @Test
    fun `validator preset supports override template resolution for design validator`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-design-validator")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("validator.kt.peb").writeText(
            """
            // override: renderer validator template
            package {{ packageName }}
            annotation class {{ typeName }}
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
                    generatorId = "design-validator",
                    moduleRole = "application",
                    templateId = "design/validator.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/validators/authorize/IssueToken.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.validators.authorize",
                        "typeName" to "IssueToken",
                        "description" to "issue token validator",
                        "valueType" to "Long",
                        "imports" to emptyList<String>(),
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
        assertTrue(content.contains("// override: renderer validator template"))
        assertTrue(content.contains("annotation class IssueToken"))
    }

    @Test
    fun `unique templates render business validator and repository backed handler semantics`() {
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver("ddd-default", emptyList())
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "adapter",
                    templateId = "aggregate/unique_query_handler.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/user_message/unique/UniqueUserMessageMessageKeyQryHandler.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.queries.user_message.unique",
                        "typeName" to "UniqueUserMessageMessageKeyQryHandler",
                        "queryTypeName" to "UniqueUserMessageMessageKeyQry",
                        "queryTypeFqn" to "com.acme.demo.application.queries.user_message.unique.UniqueUserMessageMessageKeyQry",
                        "repositoryTypeName" to "UserMessageRepository",
                        "repositoryTypeFqn" to "com.acme.demo.adapter.domain.repositories.UserMessageRepository",
                        "schemaTypeName" to "SUserMessage",
                        "schemaTypeFqn" to "com.acme.demo.domain._share.meta.user_message.SUserMessage",
                        "entityTypeName" to "UserMessage",
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.user_message.UserMessage",
                        "whereProps" to listOf("messageKey"),
                        "idPropName" to "id",
                        "excludeIdParamName" to "excludeUserMessageId",
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "application",
                    templateId = "aggregate/unique_validator.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/validators/user_message/unique/UniqueUserMessageMessageKey.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.validators.user_message.unique",
                        "typeName" to "UniqueUserMessageMessageKey",
                        "queryTypeName" to "UniqueUserMessageMessageKeyQry",
                        "queryTypeFqn" to "com.acme.demo.application.queries.user_message.unique.UniqueUserMessageMessageKeyQry",
                        "requestProps" to listOf(
                            mapOf(
                                "name" to "messageKey",
                                "type" to "String",
                                "isString" to true,
                                "param" to "messageKeyField",
                                "varName" to "messageKeyProperty",
                            )
                        ),
                        "fieldParams" to listOf(
                            mapOf(
                                "param" to "messageKeyField",
                                "default" to "messageKey",
                            )
                        ),
                        "idType" to "Long",
                        "excludeIdParamName" to "excludeUserMessageId",
                        "entityIdParam" to "userMessageIdField",
                        "entityIdDefault" to "userMessageId",
                        "entityIdVar" to "userMessageIdProperty",
                        "entityName" to "UserMessage",
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        )

        val handlerContent = rendered.single {
            it.outputPath.endsWith("UniqueUserMessageMessageKeyQryHandler.kt")
        }.content
        val validatorContent = rendered.single {
            it.outputPath.endsWith("UniqueUserMessageMessageKey.kt")
        }.content

        assertTrue(validatorContent.contains("ConstraintValidator<UniqueUserMessageMessageKey, Any>"))
        assertTrue(validatorContent.contains("value::class.memberProperties.associateBy"))
        assertTrue(validatorContent.contains("Mediator.queries.send("))
        assertTrue(validatorContent.contains("return !result.exists"))
        assertFalse(
            validatorContent.contains(
                "ConstraintValidator<UniqueUserMessageMessageKey, UniqueUserMessageMessageKeyQry.Request>"
            )
        )
        assertTrue(
            validatorContent.contains(
                "import com.acme.demo.application.queries.user_message.unique.UniqueUserMessageMessageKeyQry"
            )
        )
        assertFalse(validatorContent.contains("message_key"))

        assertTrue(handlerContent.contains("private val repository: UserMessageRepository"))
        assertTrue(handlerContent.contains("val exists = repository.exists("))
        assertTrue(handlerContent.contains("SUserMessage.specify"))
        assertTrue(handlerContent.contains("schema.messageKey eq request.messageKey"))
        assertTrue(
            handlerContent.contains(
                "import com.acme.demo.application.queries.user_message.unique.UniqueUserMessageMessageKeyQry"
            )
        )
        assertTrue(handlerContent.contains("import com.acme.demo.adapter.domain.repositories.UserMessageRepository"))
        assertTrue(handlerContent.contains("import com.acme.demo.domain._share.meta.user_message.SUserMessage"))
        assertFalse(handlerContent.contains("exists = false"))
        assertFalse(handlerContent.contains("message_key"))
    }

    @Test
    fun `unique child handler renders entity manager backed query without child repository`() {
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver("ddd-default", emptyList())
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "adapter",
                    templateId = "aggregate/unique_query_handler.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video/unique/UniqueVideoFileVideoIdQryHandler.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.queries.video.unique",
                        "typeName" to "UniqueVideoFileVideoIdQryHandler",
                        "queryTypeName" to "UniqueVideoFileVideoIdQry",
                        "queryTypeFqn" to "com.acme.demo.application.queries.video.unique.UniqueVideoFileVideoIdQry",
                        "repositoryTypeName" to null,
                        "repositoryTypeFqn" to null,
                        "schemaTypeName" to "SVideoFile",
                        "schemaTypeFqn" to "com.acme.demo.domain._share.meta.video.SVideoFile",
                        "entityTypeName" to "VideoFile",
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.video.VideoFile",
                        "whereProps" to listOf("videoId"),
                        "idPropName" to "id",
                        "excludeIdParamName" to "excludeVideoFileId",
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
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        )

        val handlerContent = rendered.single().content

        assertTrue(handlerContent.contains("import jakarta.persistence.EntityManager"))
        assertTrue(handlerContent.contains("import com.acme.demo.domain.aggregates.video.VideoFile"))
        assertTrue(handlerContent.contains("private val entityManager: EntityManager"))
        assertTrue(handlerContent.contains("val criteriaBuilder = entityManager.criteriaBuilder"))
        assertTrue(handlerContent.contains("val root = criteriaQuery.from(VideoFile::class.java)"))
        assertTrue(handlerContent.contains("SVideoFile.specify"))
        assertTrue(handlerContent.contains("schema.videoId eq request.videoId"))
        assertFalse(handlerContent.contains("private val repository"))
        assertFalse(handlerContent.contains("repository.exists("))
    }
}

private data class RenderedTypeCarrier(
    val renderedType: String
)
