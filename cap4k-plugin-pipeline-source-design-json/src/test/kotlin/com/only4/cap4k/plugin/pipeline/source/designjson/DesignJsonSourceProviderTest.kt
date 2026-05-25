package com.only4.cap4k.plugin.pipeline.source.designjson

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.RequestTrait
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
        assertEquals("command", snapshot.entries.first().tag)
        assertEquals("order.submit", snapshot.entries.first().packageName)
        assertEquals("submit order command", snapshot.entries.first().description)
        assertEquals(listOf("Order"), snapshot.entries.first().aggregates)
        assertEquals(1, snapshot.entries.first().requestFields.size)
        assertEquals("orderId", snapshot.entries.first().requestFields.first().name)
        assertEquals("Long", snapshot.entries.first().requestFields.first().type)
        assertEquals(1, snapshot.entries.first().responseFields.size)
        assertEquals("accepted", snapshot.entries.first().responseFields.first().name)
        assertEquals("Boolean", snapshot.entries.first().responseFields.first().type)
        assertEquals("query", snapshot.entries.last().tag)
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
                "tag": "command",
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
    fun `rejects domain event request field named entity`() {
        val tempFile = tempDir.resolve("domain-event-reserved-entity.json")
        val content = """
            [
              {
                "tag": "domain_event",
                "name": "OrderCreated",
                "desc": "order created event",
                "aggregates": ["Order"],
                "requestFields": [
                  { "name": "entity", "type": "Order" },
                  { "name": "reason", "type": "String" }
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

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(config)
        }

        assertEquals(
            "domain_event OrderCreated request field 'entity' is reserved and derived from aggregates[0].",
            error.message,
        )
    }

    @Test
    fun `reads page traits for query and api payload entries`() {
        val tempFile = tempDir.resolve("page-traits.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "query",
                    "package": "order.read",
                    "name": "FindOrderPage",
                    "desc": "find order page",
                    "traits": ["page"],
                    "requestFields": [],
                    "responseFields": [
                      { "name": "page", "type": "com.only4.cap4k.ddd.core.share.PageData<Item>" },
                      { "name": "page.list[].orderId", "type": "Long" }
                    ]
                  },
                  {
                    "tag": "api_payload",
                    "package": "order.read",
                    "name": "FindOrderPage",
                    "desc": "find order page payload",
                    "traits": ["PAGE"],
                    "requestFields": [],
                    "responseFields": [
                      { "name": "page", "type": "com.only4.cap4k.ddd.core.share.PageData<Item>" },
                      { "name": "page.list[].orderId", "type": "Long" }
                    ]
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val snapshot = DesignJsonSourceProvider().collect(configFor(tempFile.toString())) as DesignSpecSnapshot

        assertEquals(setOf(RequestTrait.PAGE), snapshot.entries[0].traits)
        assertEquals(setOf(RequestTrait.PAGE), snapshot.entries[1].traits)
    }

    @Test
    fun `reads integration event role and event name`() {
        val tempFile = tempDir.resolve("integration-event.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "integration_event",
                    "package": "content",
                    "name": "ContentPublishedIntegrationEvent",
                    "desc": "content published integration event",
                    "role": "OUTBOUND",
                    "eventName": "content.published",
                    "requestFields": [
                      { "name": "contentId", "type": "Long" }
                    ],
                    "responseFields": []
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val snapshot = DesignJsonSourceProvider().collect(configFor(tempFile.toString())) as DesignSpecSnapshot
        val entry = snapshot.entries.single()

        assertEquals("integration_event", entry.tag)
        assertEquals("outbound", entry.role)
        assertEquals("content.published", entry.eventName)
        assertEquals("contentId", entry.requestFields.single().name)
    }

    @Test
    fun `rejects integration event missing role`() {
        val tempFile = tempDir.resolve("integration-event-missing-role.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "integration_event",
                    "package": "content",
                    "name": "ContentPublishedIntegrationEvent",
                    "desc": "content published integration event",
                    "eventName": "content.published",
                    "requestFields": [],
                    "responseFields": []
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
        }

        assertEquals(
            "integration_event ContentPublishedIntegrationEvent must declare role inbound or outbound.",
            error.message,
        )
    }

    @Test
    fun `rejects integration event unsupported role`() {
        val tempFile = tempDir.resolve("integration-event-unsupported-role.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "integration_event",
                    "package": "content",
                    "name": "ContentPublishedIntegrationEvent",
                    "desc": "content published integration event",
                    "role": "producer",
                    "eventName": "content.published",
                    "requestFields": [],
                    "responseFields": []
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
        }

        assertEquals(
            "integration_event ContentPublishedIntegrationEvent has unsupported role: producer",
            error.message,
        )
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
                    "desc": "content published integration event",
                    "role": "inbound",
                    "requestFields": [],
                    "responseFields": []
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
    fun `rejects integration event response fields`() {
        val tempFile = tempDir.resolve("integration-event-response-fields.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "integration_event",
                    "package": "content",
                    "name": "ContentPublishedIntegrationEvent",
                    "desc": "content published integration event",
                    "role": "outbound",
                    "eventName": "content.published",
                    "requestFields": [],
                    "responseFields": [
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
            "integration_event ContentPublishedIntegrationEvent must not declare responseFields.",
            error.message,
        )
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
                    "desc": "publication policy",
                    "aggregates": ["Content"]
                  },
                  {
                    "tag": "saga",
                    "package": "content.workflow",
                    "name": "PublishContentSaga",
                    "desc": "publish content",
                    "requestFields": [{ "name": "contentId", "type": "ContentId" }],
                    "responseFields": [{ "name": "accepted", "type": "Boolean" }]
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val snapshot = DesignJsonSourceProvider().collect(configFor(tempFile.toString())) as DesignSpecSnapshot

        assertEquals(listOf("domain_service", "saga"), snapshot.entries.map { it.tag })
        assertEquals(listOf("Content"), snapshot.entries[0].aggregates)
        assertEquals("contentId", snapshot.entries[1].requestFields.single().name)
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
                        "desc": "legacy tag",
                        "requestFields": [],
                        "responseFields": []
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
                        "desc": "non canonical tag",
                        "requestFields": [],
                        "responseFields": []
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
    fun `rejects unknown request trait`() {
        val tempFile = tempDir.resolve("unknown-trait.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "query",
                    "package": "order.read",
                    "name": "FindOrder",
                    "desc": "find order",
                    "traits": ["cursor"],
                    "requestFields": [],
                    "responseFields": []
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
        }

        assertEquals("design entry FindOrder has unsupported trait: cursor", error.message)
    }

    @Test
    fun `rejects request traits on unsupported tags`() {
        val tempFile = tempDir.resolve("trait-on-command.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "command",
                    "package": "order.submit",
                    "name": "SubmitOrder",
                    "desc": "submit order",
                    "traits": ["page"],
                    "requestFields": [],
                    "responseFields": []
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
        }

        assertEquals("design entry SubmitOrder cannot use request traits on tag: command", error.message)
    }

    @Test
    fun `rejects unsupported request traits on unsupported tags before parsing trait values`() {
        val tempFile = tempDir.resolve("unsupported-trait-on-command.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "command",
                    "package": "order.submit",
                    "name": "SubmitOrder",
                    "desc": "submit order",
                    "traits": ["cursor"],
                    "requestFields": [],
                    "responseFields": []
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
        }

        assertEquals("design entry SubmitOrder cannot use request traits on tag: command", error.message)
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
                    "desc": "get category tree",
                    "requestFields": [],
                    "responseFields": [
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
    fun `rejects self in generic design field types regardless of case`() {
        val cases = listOf(
            "Map<String, Self>",
            "SELF",
        )

        cases.forEachIndexed { index, type ->
            val tempFile = tempDir.resolve("self-recursion-${index}.json")
            Files.writeString(
                tempFile,
                """
                    [
                      {
                        "tag": "api_payload",
                        "package": "category",
                        "name": "GetCategoryTree",
                        "desc": "get category tree",
                        "requestFields": [],
                        "responseFields": [
                          { "name": "nodes", "type": "$type" }
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
                "design entry GetCategoryTree field nodes must use an explicit type name instead of self",
                error.message,
            )
        }
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
                    "desc": "get category tree",
                    "requestFields": [
                      { "name": "owner", "type": "myself" }
                    ],
                    "responseFields": [
                      { "name": "image", "type": "Selfie" }
                    ]
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val snapshot = DesignJsonSourceProvider().collect(configFor(tempFile.toString())) as DesignSpecSnapshot

        assertEquals("myself", snapshot.entries.single().requestFields.single().type)
        assertEquals("Selfie", snapshot.entries.single().responseFields.single().type)
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
                    "tag": "query",
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
                    "tag": "command",
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
                    "tag": "command",
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

    private fun configFor(vararg files: String): ProjectConfig =
        ProjectConfig(
            basePackage = "com.only4.cap4k",
            layout = ProjectLayout.SINGLE_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "design-json" to SourceConfig(
                    enabled = true,
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
}
