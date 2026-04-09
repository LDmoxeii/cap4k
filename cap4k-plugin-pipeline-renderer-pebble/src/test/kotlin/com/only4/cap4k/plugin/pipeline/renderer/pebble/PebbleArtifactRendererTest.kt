package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.only4.cap4k.plugin.pipeline.api.*
import java.nio.file.Files
import kotlin.io.path.writeText
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
    fun `throws clear error when template is missing`() {
        val resolver = PresetTemplateResolver(
            preset = "ddd-default",
            overrideDirs = emptyList()
        )

        val exception = assertThrows<IllegalArgumentException> {
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
}
