package com.only4.cap4k.plugin.pipeline.source.designjson

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DesignJsonSourceProviderTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `loads canonical command and query entries from configured files`() {
        val fixture = File("src/test/resources/fixtures/design/design.json").path

        val snapshot = DesignJsonSourceProvider().collect(configFor(fixture)) as DesignSpecSnapshot

        assertEquals(2, snapshot.entries.size)
        assertEquals("command", snapshot.entries.first().tag)
        assertEquals("order.submit", snapshot.entries.first().packageName)
        assertEquals("submit order command", snapshot.entries.first().description)
        assertEquals(listOf("Order"), snapshot.entries.first().aggregates)
        assertEquals(emptyList<FieldModel>(), snapshot.entries.first().requestFields)
        assertEquals(emptyList<FieldModel>(), snapshot.entries.first().responseFields)
        assertEquals(null, snapshot.entries.first().artifacts)
        assertEquals(1, snapshot.entries.first().fields.size)
        assertEquals("orderId", snapshot.entries.first().fields.first().name)
        assertEquals("Long", snapshot.entries.first().fields.first().type)
        assertEquals(emptyList<FieldModel>(), snapshot.entries.first().resultFields)
        assertEquals("query", snapshot.entries.last().tag)
        assertEquals("FindOrder", snapshot.entries.last().name)
        assertEquals(null, snapshot.entries.last().artifacts)
        assertEquals("orderId", snapshot.entries.last().fields.first().name)
        assertEquals("Long", snapshot.entries.last().fields.first().type)
        assertEquals("status", snapshot.entries.last().resultFields.first().name)
        assertEquals("String", snapshot.entries.last().resultFields.first().type)
    }

    @Test
    fun `parses new public field names and explicit artifact selections`() {
        val tempFile = tempDir.resolve("new-design-block.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "query",
                    "package": "order.read",
                    "name": "FindOrderPage",
                    "description": "Find order page",
                    "aggregates": ["Order"],
                    "artifacts": [
                      { "family": "query", "variant": "page" },
                      { "family": "query-handler" }
                    ],
                    "fields": [{ "name": "keyword", "type": "String", "nullable": true }],
                    "resultFields": [{ "name": "orderNo", "type": "String" }]
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val snapshot = DesignJsonSourceProvider().collect(configFor(tempFile.toString())) as DesignSpecSnapshot
        val entry = snapshot.entries.single()

        assertEquals("Find order page", entry.description)
        assertEquals(listOf("Order"), entry.aggregates)
        assertEquals(emptyList<FieldModel>(), entry.requestFields)
        assertEquals(emptyList<FieldModel>(), entry.responseFields)
        assertEquals(emptyList<String>(), entry.targets)
        assertEquals(null, entry.message)
        assertEquals("keyword", entry.fields.single().name)
        assertEquals(true, entry.fields.single().nullable)
        assertEquals("orderNo", entry.resultFields.single().name)
        assertEquals(
            listOf(
                ArtifactSelectionModel("query", "page"),
                ArtifactSelectionModel("query-handler"),
            ),
            entry.artifacts,
        )
    }

    @Test
    fun `parses explicit empty artifact selections as authoritative empty list`() {
        val tempFile = tempDir.resolve("empty-artifacts.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "query",
                    "package": "order.read",
                    "name": "FindOrder",
                    "description": "find order",
                    "artifacts": [],
                    "fields": []
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val snapshot = DesignJsonSourceProvider().collect(configFor(tempFile.toString())) as DesignSpecSnapshot

        assertEquals(emptyList<ArtifactSelectionModel>(), snapshot.entries.single().artifacts)
    }

    @Test
    fun `rejects removed public fields with stable entry message`() {
        val tempFile = tempDir.resolve("old-fields.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "query",
                    "package": "order.read",
                    "name": "FindOrder",
                    "desc": "old",
                    "requestFields": [],
                    "responseFields": [],
                    "traits": ["page"]
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
        }

        assertEquals(
            "design entry FindOrder uses removed fields: desc, requestFields, responseFields, traits",
            error.message,
        )
    }

    @Test
    fun `defaults missing field type to kotlin String`() {
        val tempFile = tempDir.resolve("default-type-design.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "command",
                    "package": "order.submit",
                    "name": "CreateOrder",
                    "description": "create order",
                    "aggregates": ["Order"],
                    "fields": [
                      { "name": "note" }
                    ]
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val snapshot = DesignJsonSourceProvider().collect(configFor(tempFile.toString())) as DesignSpecSnapshot

        assertEquals("create order", snapshot.entries.first().description)
        assertEquals("kotlin.String", snapshot.entries.first().fields.first().type)
    }

    @Test
    fun `reads optional persist boolean for domain event entries`() {
        val tempFile = tempDir.resolve("domain-event-persist.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "domain_event",
                    "package": "order",
                    "name": "OrderCreated",
                    "description": "order created event",
                    "aggregates": ["Order"],
                    "persist": true,
                    "fields": []
                  },
                  {
                    "tag": "domain_event",
                    "package": "order",
                    "name": "OrderArchived",
                    "description": "order archived event",
                    "aggregates": ["Order"],
                    "persist": false,
                    "fields": []
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val snapshot = DesignJsonSourceProvider().collect(configFor(tempFile.toString())) as DesignSpecSnapshot

        assertEquals(listOf(true, false), snapshot.entries.map { it.persist })
    }

    @Test
    fun `allows domain event entry without package`() {
        val tempFile = tempDir.resolve("domain-event-without-package.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "domain_event",
                    "name": "OrderCreated",
                    "description": "order created event",
                    "aggregates": ["Order"],
                    "persist": false,
                    "fields": []
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val snapshot = DesignJsonSourceProvider().collect(configFor(tempFile.toString())) as DesignSpecSnapshot

        assertEquals("", snapshot.entries.single().packageName)
        assertEquals(listOf("Order"), snapshot.entries.single().aggregates)
    }

    @Test
    fun `rejects domain event field named entity`() {
        val tempFile = tempDir.resolve("domain-event-reserved-entity.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "domain_event",
                    "name": "OrderCreated",
                    "description": "order created event",
                    "aggregates": ["Order"],
                    "fields": [
                      { "name": "entity", "type": "Order" },
                      { "name": "reason", "type": "String" }
                    ]
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
        }

        assertEquals(
            "domain_event OrderCreated field 'entity' is reserved and derived from aggregates[0].",
            error.message,
        )
    }

    @Test
    fun `reads integration event event name without role`() {
        val tempFile = tempDir.resolve("integration-event.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "integration_event",
                    "package": "content",
                    "name": "ContentPublishedIntegrationEvent",
                    "description": "content published integration event",
                    "eventName": "content.published",
                    "fields": [
                      { "name": "contentId", "type": "Long" }
                    ]
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val snapshot = DesignJsonSourceProvider().collect(configFor(tempFile.toString())) as DesignSpecSnapshot
        val entry = snapshot.entries.single()

        assertEquals("integration_event", entry.tag)
        assertEquals(null, entry.role)
        assertEquals("content.published", entry.eventName)
        assertEquals("contentId", entry.fields.single().name)
    }

    @Test
    fun `rejects integration event missing event name`() {
        val tempFile = tempDir.resolve("integration-event-missing-event-name.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "integration_event",
                    "package": "content",
                    "name": "ContentPublishedIntegrationEvent",
                    "description": "content published integration event",
                    "fields": []
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
        }

        assertEquals(
            "integration_event ContentPublishedIntegrationEvent must declare eventName.",
            error.message,
        )
    }

    @Test
    fun `rejects integration event result fields`() {
        val tempFile = tempDir.resolve("integration-event-result-fields.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "integration_event",
                    "package": "content",
                    "name": "ContentPublishedIntegrationEvent",
                    "description": "content published integration event",
                    "eventName": "content.published",
                    "fields": [],
                    "resultFields": [
                      { "name": "accepted", "type": "Boolean" }
                    ]
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
        }

        assertEquals(
            "integration_event ContentPublishedIntegrationEvent must not declare resultFields.",
            error.message,
        )
    }

    @Test
    fun `rejects event name and persist on unsupported tags`() {
        val cases = listOf(
            "eventName" to """{ "tag": "query", "package": "order.read", "name": "FindOrder", "eventName": "order.find" }""",
            "persist" to """{ "tag": "query", "package": "order.read", "name": "FindOrder", "persist": true }""",
        )

        cases.forEach { (field, json) ->
            val tempFile = tempDir.resolve("unsupported-${field}.json")
            Files.writeString(tempFile, "[$json]", StandardCharsets.UTF_8)

            val error = assertThrows(IllegalArgumentException::class.java) {
                DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
            }

            assertEquals("design entry FindOrder cannot declare $field on tag: query", error.message)
        }
    }

    @Test
    fun `parses domain service and saga entries`() {
        val tempFile = tempDir.resolve("design.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "domain_service",
                    "package": "content.domain",
                    "name": "ContentPublicationPolicy",
                    "description": "publication policy",
                    "aggregates": ["Content"]
                  },
                  {
                    "tag": "saga",
                    "package": "content.workflow",
                    "name": "PublishContentSaga",
                    "description": "publish content",
                    "fields": [{ "name": "contentId", "type": "ContentId" }]
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val snapshot = DesignJsonSourceProvider().collect(configFor(tempFile.toString())) as DesignSpecSnapshot

        assertEquals(listOf("domain_service", "saga"), snapshot.entries.map { it.tag })
        assertEquals(listOf("Content"), snapshot.entries[0].aggregates)
        assertEquals("contentId", snapshot.entries[1].fields.single().name)
    }

    @Test
    fun `validator tag is unsupported as a normal design tag`() {
        val tempFile = tempDir.resolve("design.json")
        Files.writeString(
            tempFile,
            """[{ "tag": "validator", "package": "content.validation", "name": "ValidAuthor" }]""",
            StandardCharsets.UTF_8,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
        }

        assertTrue(error.message!!.contains("Unsupported design tag: validator"))
        assertFalse(error.message!!.contains("migration"))
        assertFalse(error.message!!.contains("deprecated"))
    }

    @Test
    fun `rejects legacy design tag aliases`() {
        val legacyTags = listOf("cmd", "qry", "cli", "clients", "payload", "de", "query_list", "query_page")

        legacyTags.forEach { legacyTag ->
            val tempFile = tempDir.resolve("legacy-${legacyTag}.json")
            Files.writeString(
                tempFile,
                """
                    [
                      {
                        "tag": "$legacyTag",
                        "package": "order.read",
                        "name": "LegacyTag",
                        "description": "legacy tag"
                      }
                    ]
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )

            val error = assertThrows(IllegalArgumentException::class.java) {
                DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
            }

            assertEquals("Unsupported design tag: $legacyTag", error.message)
        }
    }

    @Test
    fun `rejects non-canonical design tags exactly`() {
        val cases = listOf(
            Triple("Query", "FindOrder", "Unsupported design tag: Query"),
            Triple(" command ", "SubmitOrder", "Unsupported design tag:  command "),
        )

        cases.forEach { (tag, name, expectedMessage) ->
            val tempFile = tempDir.resolve("non-canonical-${name}.json")
            Files.writeString(
                tempFile,
                """
                    [
                      {
                        "tag": "$tag",
                        "package": "order",
                        "name": "$name",
                        "description": "non canonical tag"
                      }
                    ]
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )

            val error = assertThrows(IllegalArgumentException::class.java) {
                DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
            }

            assertEquals(expectedMessage, error.message)
        }
    }

    @Test
    fun `rejects self in design field types`() {
        val tempFile = tempDir.resolve("self-recursion.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "api_payload",
                    "package": "category",
                    "name": "GetCategoryTree",
                    "description": "get category tree",
                    "fields": [],
                    "resultFields": [
                      { "name": "nodes", "type": "List<Node>" },
                      { "name": "nodes[].children", "type": "List<self>" }
                    ]
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
        }

        assertEquals(
            "design entry GetCategoryTree field nodes[].children must use an explicit type name instead of self",
            error.message,
        )
    }

    @Test
    fun `allows self text embedded in explicit design field type names`() {
        val tempFile = tempDir.resolve("embedded-self-names.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "api_payload",
                    "package": "category",
                    "name": "GetCategoryTree",
                    "description": "get category tree",
                    "fields": [
                      { "name": "owner", "type": "myself" }
                    ],
                    "resultFields": [
                      { "name": "image", "type": "Selfie" }
                    ]
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val snapshot = DesignJsonSourceProvider().collect(configFor(tempFile.toString())) as DesignSpecSnapshot

        assertEquals("myself", snapshot.entries.single().fields.single().type)
        assertEquals("Selfie", snapshot.entries.single().resultFields.single().type)
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
                    "tag": "command",
                    "package": "order.submit",
                    "name": "SubmitOrder",
                    "description": "submit order"
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
                    "tag": "query",
                    "package": "order.query",
                    "name": "FindOrder",
                    "description": "find order"
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

        val snapshot = DesignJsonSourceProvider().collect(configForManifest(manifestFile, projectDir)) as DesignSpecSnapshot

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
                    "tag": "command",
                    "package": "order.submit",
                    "name": "SubmitOrder",
                    "description": "submit order"
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

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configForManifest(manifestFile, projectDir))
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
                    "tag": "command",
                    "package": "order.submit",
                    "name": "Outside",
                    "description": "outside"
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

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configForManifest(manifestFile, projectDir))
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

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configForManifest(manifestFile, projectDir))
        }

        assertTrue(error.message?.contains("blank design manifest entry") == true)
    }

    private fun configFor(vararg files: String): ProjectConfig =
        ProjectConfig(
            basePackage = "com.only4.cap4k",
            layout = ProjectLayout.SINGLE_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "design-json" to SourceConfig(
                    options = mapOf("files" to files.toList()),
                ),
            ),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "default",
                overrideDirs = emptyList(),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )

    private fun configForManifest(manifestFile: Path, projectDir: Path): ProjectConfig =
        ProjectConfig(
            basePackage = "com.only4.cap4k",
            layout = ProjectLayout.SINGLE_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "design-json" to SourceConfig(
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
}
