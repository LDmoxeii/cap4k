package com.only4.cap4k.plugin.pipeline.source.designjson

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.SemanticFieldSnapshot
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
        assertEquals(null, snapshot.entries.first().artifacts)
        assertEquals(1, snapshot.entries.first().fields.size)
        assertEquals("orderId", snapshot.entries.first().fields.first().name)
        assertEquals("Long", snapshot.entries.first().fields.first().typeExpression)
        assertEquals(emptyList<SemanticFieldSnapshot>(), snapshot.entries.first().resultFields)
        assertEquals("query", snapshot.entries.last().tag)
        assertEquals("FindOrder", snapshot.entries.last().name)
        assertEquals(null, snapshot.entries.last().artifacts)
        assertEquals("orderId", snapshot.entries.last().fields.first().name)
        assertEquals("Long", snapshot.entries.last().fields.first().typeExpression)
        assertEquals("status", snapshot.entries.last().resultFields.first().name)
        assertEquals("String", snapshot.entries.last().resultFields.first().typeExpression)
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
                    "fields": [{ "name": "keyword", "type": "String?" }],
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
        assertEquals("keyword", entry.fields.single().name)
        assertEquals("String?", entry.fields.single().typeExpression)
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
    fun `parses command result fields as command response payload`() {
        val tempFile = tempDir.resolve("command-result-fields.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "command",
                    "package": "order.submit",
                    "name": "SubmitOrder",
                    "description": "submit order",
                    "aggregates": ["Order"],
                    "fields": [{ "name": "orderId", "type": "OrderId" }],
                    "resultFields": [
                      { "name": "accepted", "type": "Boolean" },
                      { "name": "receiptId", "type": "String?", "defaultValue": "null" }
                    ]
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val snapshot = DesignJsonSourceProvider().collect(configFor(tempFile.toString())) as DesignSpecSnapshot
        val entry = snapshot.entries.single()

        assertEquals("command", entry.tag)
        assertEquals("SubmitOrder", entry.name)
        assertEquals(listOf("Order"), entry.aggregates)
        assertEquals(
            listOf(SemanticFieldSnapshot(name = "orderId", typeExpression = "OrderId", sourcePath = "fields.orderId")),
            entry.fields,
        )
        assertEquals(
            listOf(
                SemanticFieldSnapshot(name = "accepted", typeExpression = "Boolean", sourcePath = "resultFields.accepted"),
                SemanticFieldSnapshot(
                    name = "receiptId",
                    typeExpression = "String?",
                    defaultValue = "null",
                    sourcePath = "resultFields.receiptId",
                ),
            ),
            entry.resultFields,
        )
    }

    @Test
    fun `rejects explicit empty artifact selections`() {
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

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
        }

        assertEquals("design entry FindOrder artifacts must not be empty.", error.message)
    }

    @Test
    fun `rejects malformed artifact selection shape with stable entry scoped message`() {
        val tempFile = tempDir.resolve("malformed-artifacts.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "query",
                    "package": "order.read",
                    "name": "FindOrder",
                    "description": "find order",
                    "artifacts": [
                      "query"
                    ],
                    "fields": []
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
        }

        assertTrue(error.message?.contains("design entry FindOrder") == true)
        assertTrue(error.message?.contains("artifacts[0]") == true)
    }

    @Test
    fun `rejects malformed artifact family values with stable entry scoped message`() {
        val tempFile = tempDir.resolve("malformed-artifact-family.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "query",
                    "package": "order.read",
                    "name": "FindOrder",
                    "description": "find order",
                    "artifacts": [
                      { "family": " " }
                    ],
                    "fields": []
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
        }

        assertTrue(error.message?.contains("design entry FindOrder") == true)
        assertTrue(error.message?.contains("artifact family") == true)
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
                    "traits": ["page"],
                    "entity": "Order"
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
        }

        assertEquals(
            "design entry FindOrder uses removed fields: desc, requestFields, responseFields, traits, entity",
            error.message,
        )
    }

    @Test
    fun `rejects malformed top level design json shape with stable message`() {
        val rootObject = tempDir.resolve("root-object.json")
        Files.writeString(rootObject, """{"tag":"query"}""", StandardCharsets.UTF_8)

        val rootError = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(rootObject.toString()))
        }

        assertTrue(rootError.message!!.contains("root must be an array"))

        val entryString = tempDir.resolve("entry-string.json")
        Files.writeString(entryString, """["query"]""", StandardCharsets.UTF_8)

        val entryError = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(entryString.toString()))
        }

        assertTrue(entryError.message!!.contains("design entry[0] must be an object"))
    }

    @Test
    fun `rejects missing field type`() {
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

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
        }

        assertEquals(
            "design entry CreateOrder fields[0] field type must be a nonblank string.",
            error.message,
        )
    }

    @Test
    fun `rejects legacy nullable field flag and requires nullability in type expression`() {
        val tempFile = tempDir.resolve("legacy-nullable-field.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "query",
                    "package": "order.read",
                    "name": "FindOrder",
                    "description": "find order",
                    "fields": [
                      { "name": "keyword", "type": "String", "nullable": true }
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
            "design entry FindOrder fields[0] field nullable is removed; encode nullability in type",
            error.message,
        )
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
                    "eventName": "order.created",
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
    fun `rejects persisted domain event without an event name`() {
        val tempFile = tempDir.resolve("persisted-domain-event-without-name.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "domain_event",
                    "name": "OrderCreated",
                    "description": "order created event",
                    "aggregates": ["Order"],
                    "persist": true,
                    "fields": []
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
        }

        assertEquals("persisted domain_event OrderCreated must declare eventName.", error.message)
    }

    @Test
    fun `rejects fields and result fields on metadata only domain services`() {
        listOf("fields", "resultFields").forEach { fieldGroup ->
            val tempFile = tempDir.resolve("domain-service-$fieldGroup.json")
            Files.writeString(
                tempFile,
                """
                    [
                      {
                        "tag": "domain_service",
                        "name": "OrderPolicyService",
                        "$fieldGroup": [
                          { "name": "value", "type": "String" }
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
                "domain_service OrderPolicyService is metadata-only and must not declare fields or resultFields.",
                error.message,
            )
        }
    }

    @Test
    fun `page variants reject derived page fields while non page variants allow them`() {
        val pageFile = tempDir.resolve("page-derived-field.json")
        Files.writeString(
            pageFile,
            """
                [
                  {
                    "tag": "query",
                    "package": "order.read",
                    "name": "FindOrderPage",
                    "artifacts": [{ "family": "query", "variant": "page" }],
                    "fields": [{ "name": "pageNum", "type": "Int" }]
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val pageError = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(pageFile.toString()))
        }
        assertEquals(
            "design entry FindOrderPage page variant derives pageNum; remove the explicit field.",
            pageError.message,
        )

        val nonPageFile = tempDir.resolve("non-page-business-fields.json")
        Files.writeString(
            nonPageFile,
            """
                [
                  {
                    "tag": "query",
                    "package": "order.read",
                    "name": "FindOrder",
                    "artifacts": [{ "family": "query" }],
                    "fields": [
                      { "name": "pageNum", "type": "Int" },
                      { "name": "pageSize", "type": "Int" }
                    ]
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val snapshot = DesignJsonSourceProvider().collect(configFor(nonPageFile.toString())) as DesignSpecSnapshot
        assertEquals(listOf("pageNum", "pageSize"), snapshot.entries.single().fields.map { it.name })
    }

    @Test
    fun `page variants validate the root segment of nested field paths`() {
        listOf(
            "pageNum.value" to "pageNum",
            "pageSize[].value" to "pageSize",
        ).forEachIndexed { index, (fieldPath, pageFieldName) ->
            val invalidFile = tempDir.resolve("page-derived-nested-field-$index.json")
            Files.writeString(
                invalidFile,
                """
                    [
                      {
                        "tag": "query",
                        "package": "order.read",
                        "name": "FindOrderPage",
                        "artifacts": [{ "family": "query", "variant": "page" }],
                        "fields": [{ "name": "$fieldPath", "type": "Int" }]
                      }
                    ]
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )

            val error = assertThrows(IllegalArgumentException::class.java) {
                DesignJsonSourceProvider().collect(configFor(invalidFile.toString()))
            }
            assertEquals(
                "design entry FindOrderPage page variant derives $pageFieldName; remove the explicit field.",
                error.message,
            )
        }

        val nestedBusinessFile = tempDir.resolve("page-nested-business-fields.json")
        Files.writeString(
            nestedBusinessFile,
            """
                [
                  {
                    "tag": "query",
                    "package": "order.read",
                    "name": "FindOrderPage",
                    "artifacts": [{ "family": "query", "variant": "page" }],
                    "fields": [
                      { "name": "filter.pageNum", "type": "Int" },
                      { "name": "filters[].pageSize", "type": "Int" }
                    ]
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val nestedSnapshot = DesignJsonSourceProvider().collect(
            configFor(nestedBusinessFile.toString()),
        ) as DesignSpecSnapshot
        assertEquals(
            listOf("filter.pageNum", "filters[].pageSize"),
            nestedSnapshot.entries.single().fields.map { it.name },
        )
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
    fun `allows domain event field named entity because payload safety is type based`() {
        val tempFile = tempDir.resolve("domain-event-named-entity.json")
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

        val snapshot = DesignJsonSourceProvider().collect(configFor(tempFile.toString())) as DesignSpecSnapshot

        assertEquals(
            listOf(
                SemanticFieldSnapshot("entity", "Order", sourcePath = "fields.entity"),
                SemanticFieldSnapshot("reason", "String", sourcePath = "fields.reason"),
            ),
            snapshot.entries.single().fields,
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
    fun `structural and automation tags are unsupported as normal design tags`() {
        val unsupportedTags = listOf(
            "repository" to "OrderRepository",
            "validator" to "ValidAuthor",
            "scheduled_reaction" to "MediaProcessingPollingFallback",
            "job" to "MediaProcessingPollingFallbackJob",
        )

        unsupportedTags.forEach { (tag, name) ->
            val tempFile = tempDir.resolve("$tag.json")
            Files.writeString(
                tempFile,
                """[{ "tag": "$tag", "package": "content.validation", "name": "$name" }]""",
                StandardCharsets.UTF_8,
            )

            val error = assertThrows(IllegalArgumentException::class.java) {
                DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
            }

            assertEquals("Unsupported design tag: $tag", error.message)
            assertFalse(error.message!!.contains("migration"))
            assertFalse(error.message!!.contains("deprecated"))
        }
    }

    @Test
    fun `rejects legacy design tag aliases`() {
        val legacyTags = listOf("cmd", "qry", "cli", "capabilities", "payload", "de", "query_list", "query_page")

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

        assertEquals("myself", snapshot.entries.single().fields.single().typeExpression)
        assertEquals("Selfie", snapshot.entries.single().resultFields.single().typeExpression)
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
