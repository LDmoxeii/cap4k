package com.only4.cap4k.plugin.pipeline.source.enummanifest

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.writeText

class EnumManifestSourceProviderTest {

    @Test
    fun `collects shared enum manifest definitions`() {
        val projectDir = Files.createTempDirectory("enum-manifest-source")
        val manifest = projectDir.resolve("shared-enums.json")
        manifest.writeText(
            """
            [
              {
                "name": "Status",
                "package": "shared",
                "generateTranslation": true,
                "items": [
                  { "value": 0, "name": "DRAFT", "desc": "Draft" },
                  { "value": 1, "name": "PUBLISHED", "desc": "Published" }
                ]
              }
            ]
            """.trimIndent()
        )

        val snapshot = EnumManifestSourceProvider().collect(
            ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                typeRegistry = emptyMap(),
                sources = mapOf(
                    "enum-manifest" to SourceConfig(
                        enabled = true,
                        options = mapOf("files" to listOf(manifest.toAbsolutePath().toString()))
                    )
                ),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        )

        assertEquals("enum-manifest", snapshot.id)
        assertEquals(listOf("Status"), snapshot.definitions.map { it.typeName })
        assertEquals(true, snapshot.definitions.single().generateTranslation)
        assertEquals(listOf("DRAFT", "PUBLISHED"), snapshot.definitions.single().items.map { it.name })
    }

    @Test
    fun `duplicate shared enum type names fail fast`() {
        val projectDir = Files.createTempDirectory("enum-manifest-source-duplicate")
        val manifest = projectDir.resolve("shared-enums.json")
        manifest.writeText(
            """
            [
              { "name": "Status", "package": "shared", "items": [ { "value": 0, "name": "DRAFT", "desc": "Draft" } ] },
              { "name": "Status", "package": "shared", "items": [ { "value": 1, "name": "PUBLISHED", "desc": "Published" } ] }
            ]
            """.trimIndent()
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            EnumManifestSourceProvider().collect(
                ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    typeRegistry = emptyMap(),
                    sources = mapOf(
                        "enum-manifest" to SourceConfig(
                            enabled = true,
                            options = mapOf("files" to listOf(manifest.toAbsolutePath().toString()))
                        )
                    ),
                    generators = emptyMap(),
                    templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
                )
            )
        }

        assertEquals("duplicate shared enum definition: Status", error.message)
    }
}
