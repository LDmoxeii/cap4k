package com.only4.cap4k.plugin.pipeline.source.designjson

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DesignJsonSourceProviderTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `loads command and query entries from configured files`() {
        val fixture = File("src/test/resources/fixtures/design/design.json").path
        val config = ProjectConfig(
            basePackage = "com.only4.cap4k",
            layout = ProjectLayout.SINGLE_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "design-json" to SourceConfig(
                    enabled = true,
                    options = mapOf("files" to listOf(fixture)),
                ),
            ),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "default",
                overrideDirs = emptyList(),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )

        val provider = DesignJsonSourceProvider()
        val snapshot = provider.collect(config) as DesignSpecSnapshot

        assertEquals(2, snapshot.entries.size)
        assertEquals("cmd", snapshot.entries.first().tag)
        assertEquals("order.submit", snapshot.entries.first().packageName)
        assertEquals("submit order command", snapshot.entries.first().description)
        assertEquals(listOf("Order"), snapshot.entries.first().aggregates)
        assertEquals(1, snapshot.entries.first().requestFields.size)
        assertEquals("orderId", snapshot.entries.first().requestFields.first().name)
        assertEquals("Long", snapshot.entries.first().requestFields.first().type)
        assertEquals(1, snapshot.entries.first().responseFields.size)
        assertEquals("accepted", snapshot.entries.first().responseFields.first().name)
        assertEquals("Boolean", snapshot.entries.first().responseFields.first().type)
        assertEquals("FindOrder", snapshot.entries.last().name)
        assertEquals("orderId", snapshot.entries.last().requestFields.first().name)
        assertEquals("Long", snapshot.entries.last().requestFields.first().type)
        assertEquals("status", snapshot.entries.last().responseFields.first().name)
        assertEquals("String", snapshot.entries.last().responseFields.first().type)
    }

    @Test
    fun `defaults missing field type to kotlin String`() {
        val tempFile = tempDir.resolve("default-type-design.json")
        val content = """
            [
              {
                "tag": "cmd",
                "package": "order.submit",
                "name": "CreateOrder",
                "desc": "中文描述",
                "aggregates": ["Order"],
                "requestFields": [
                  { "name": "note" }
                ],
                "responseFields": []
              }
            ]
        """.trimIndent()
        Files.writeString(tempFile, content, StandardCharsets.UTF_8)

        val config = ProjectConfig(
            basePackage = "com.only4.cap4k",
            layout = ProjectLayout.SINGLE_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "design-json" to SourceConfig(
                    enabled = true,
                    options = mapOf("files" to listOf(tempFile.toString())),
                ),
            ),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "default",
                overrideDirs = emptyList(),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )

        val provider = DesignJsonSourceProvider()
        val snapshot = provider.collect(config) as DesignSpecSnapshot

        assertEquals("中文描述", snapshot.entries.first().description)
        assertEquals("kotlin.String", snapshot.entries.first().requestFields.first().type)
    }

    @Test
    fun `reads optional persist boolean for domain event entries`() {
        val tempFile = tempDir.resolve("domain-event-persist.json")
        val content = """
            [
              {
                "tag": "domain_event",
                "package": "order",
                "name": "OrderCreated",
                "desc": "order created event",
                "aggregates": ["Order"],
                "persist": true,
                "requestFields": [],
                "responseFields": []
              },
              {
                "tag": "domain_event",
                "package": "order",
                "name": "OrderArchived",
                "desc": "order archived event",
                "aggregates": ["Order"],
                "persist": false,
                "requestFields": [],
                "responseFields": []
              }
            ]
        """.trimIndent()
        Files.writeString(tempFile, content, StandardCharsets.UTF_8)

        val config = ProjectConfig(
            basePackage = "com.only4.cap4k",
            layout = ProjectLayout.SINGLE_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "design-json" to SourceConfig(
                    enabled = true,
                    options = mapOf("files" to listOf(tempFile.toString())),
                ),
            ),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "default",
                overrideDirs = emptyList(),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )

        val snapshot = DesignJsonSourceProvider().collect(config) as DesignSpecSnapshot

        assertEquals(listOf(true, false), snapshot.entries.map { it.persist })
    }

    @Test
    fun `allows domain event entry without package`() {
        val tempFile = tempDir.resolve("domain-event-without-package.json")
        val content = """
            [
              {
                "tag": "domain_event",
                "name": "OrderCreated",
                "desc": "order created event",
                "aggregates": ["Order"],
                "persist": false,
                "requestFields": [],
                "responseFields": []
              }
            ]
        """.trimIndent()
        Files.writeString(tempFile, content, StandardCharsets.UTF_8)

        val config = ProjectConfig(
            basePackage = "com.only4.cap4k",
            layout = ProjectLayout.SINGLE_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "design-json" to SourceConfig(
                    enabled = true,
                    options = mapOf("files" to listOf(tempFile.toString())),
                ),
            ),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "default",
                overrideDirs = emptyList(),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )

        val snapshot = DesignJsonSourceProvider().collect(config) as DesignSpecSnapshot

        assertEquals("", snapshot.entries.single().packageName)
        assertEquals(listOf("Order"), snapshot.entries.single().aggregates)
    }

    @Test
    fun `declares utf8 charset when reading design files`() {
        val sourceFile = File(
            "src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt",
        )
        val source = sourceFile.readText(StandardCharsets.UTF_8)
        assertTrue(source.contains("Charsets.UTF_8"))
    }

    @Test
    fun `collects design entries from manifest file`() {
        val projectDir = tempDir.resolve("project")
        val designDir = projectDir.resolve("design")
        Files.createDirectories(designDir)

        val firstDesign = designDir.resolve("first.json")
        Files.writeString(
            firstDesign,
            """
                [
                  {
                    "tag": "cmd",
                    "package": "order.submit",
                    "name": "SubmitOrder",
                    "desc": "submit order",
                    "requestFields": [],
                    "responseFields": []
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val secondDesign = designDir.resolve("second.json")
        Files.writeString(
            secondDesign,
            """
                [
                  {
                    "tag": "qry",
                    "package": "order.query",
                    "name": "FindOrder",
                    "desc": "find order",
                    "requestFields": [],
                    "responseFields": []
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val manifestFile = projectDir.resolve("design-manifest.json")
        Files.writeString(
            manifestFile,
            """
                [
                  "design/first.json",
                  "design/second.json"
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val config = ProjectConfig(
            basePackage = "com.only4.cap4k",
            layout = ProjectLayout.SINGLE_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "design-json" to SourceConfig(
                    enabled = true,
                    options = mapOf(
                        "manifestFile" to manifestFile.toString(),
                        "projectDir" to projectDir.toString(),
                    ),
                ),
            ),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "default",
                overrideDirs = emptyList(),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )

        val snapshot = DesignJsonSourceProvider().collect(config) as DesignSpecSnapshot

        assertEquals(2, snapshot.entries.size)
        assertEquals("SubmitOrder", snapshot.entries.first().name)
        assertEquals("FindOrder", snapshot.entries.last().name)
    }

    @Test
    fun `fails when manifest contains duplicate file entries`() {
        val projectDir = tempDir.resolve("project")
        val designDir = projectDir.resolve("design")
        Files.createDirectories(designDir)

        val designFile = designDir.resolve("first.json")
        Files.writeString(
            designFile,
            """
                [
                  {
                    "tag": "cmd",
                    "package": "order.submit",
                    "name": "SubmitOrder",
                    "desc": "submit order",
                    "requestFields": [],
                    "responseFields": []
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val manifestFile = projectDir.resolve("design-manifest.json")
        Files.writeString(
            manifestFile,
            """
                [
                  "design/first.json",
                  "design/first.json"
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val config = ProjectConfig(
            basePackage = "com.only4.cap4k",
            layout = ProjectLayout.SINGLE_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "design-json" to SourceConfig(
                    enabled = true,
                    options = mapOf(
                        "manifestFile" to manifestFile.toString(),
                        "projectDir" to projectDir.toString(),
                    ),
                ),
            ),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "default",
                overrideDirs = emptyList(),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(config)
        }

        assertTrue(error.message?.contains("duplicate design manifest entry") == true)
    }

    @Test
    fun `fails when manifest option exists but is blank`() {
        val fixture = File("src/test/resources/fixtures/design/design.json").path
        val config = ProjectConfig(
            basePackage = "com.only4.cap4k",
            layout = ProjectLayout.SINGLE_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "design-json" to SourceConfig(
                    enabled = true,
                    options = mapOf(
                        "manifestFile" to "   ",
                        "projectDir" to tempDir.toString(),
                        "files" to listOf(fixture),
                    ),
                ),
            ),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "default",
                overrideDirs = emptyList(),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(config)
        }

        assertTrue(error.message?.contains("manifestFile") == true)
    }

    @Test
    fun `fails when manifest entry escapes project dir boundary`() {
        val workspaceDir = tempDir.resolve("workspace")
        val projectDir = workspaceDir.resolve("project")
        Files.createDirectories(projectDir)

        val outsideFile = workspaceDir.resolve("outside.json")
        Files.writeString(
            outsideFile,
            """
                [
                  {
                    "tag": "cmd",
                    "package": "order.submit",
                    "name": "Outside",
                    "desc": "outside",
                    "requestFields": [],
                    "responseFields": []
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val manifestFile = projectDir.resolve("design-manifest.json")
        Files.writeString(
            manifestFile,
            """
                [
                  "../outside.json"
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val config = ProjectConfig(
            basePackage = "com.only4.cap4k",
            layout = ProjectLayout.SINGLE_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "design-json" to SourceConfig(
                    enabled = true,
                    options = mapOf(
                        "manifestFile" to manifestFile.toString(),
                        "projectDir" to projectDir.toString(),
                    ),
                ),
            ),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "default",
                overrideDirs = emptyList(),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(config)
        }

        assertTrue(error.message?.contains("escapes projectDir") == true)
    }

    @Test
    fun `fails when manifest entry is blank`() {
        val projectDir = tempDir.resolve("project")
        Files.createDirectories(projectDir)

        val manifestFile = projectDir.resolve("design-manifest.json")
        Files.writeString(
            manifestFile,
            """
                [
                  "   "
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val config = ProjectConfig(
            basePackage = "com.only4.cap4k",
            layout = ProjectLayout.SINGLE_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "design-json" to SourceConfig(
                    enabled = true,
                    options = mapOf(
                        "manifestFile" to manifestFile.toString(),
                        "projectDir" to projectDir.toString(),
                    ),
                ),
            ),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "default",
                overrideDirs = emptyList(),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(config)
        }

        assertTrue(error.message?.contains("blank design manifest entry") == true)
    }
}
