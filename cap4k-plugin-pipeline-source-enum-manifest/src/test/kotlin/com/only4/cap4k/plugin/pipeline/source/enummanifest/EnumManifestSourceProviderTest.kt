package com.only4.cap4k.plugin.pipeline.source.enummanifest

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryConfig
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
                "items": [
                  { "value": 0, "name": "DRAFT", "desc": "Draft" },
                  { "value": 1, "name": "PUBLISHED", "desc": "Published" }
                ]
              }
            ]
            """.trimIndent()
        )

        val snapshot = EnumManifestSourceProvider().collect(configFor(manifest.toAbsolutePath().toString()))

        assertEquals("enum-manifest", snapshot.id)
        assertEquals(listOf("Status"), snapshot.definitions.map { it.typeName })
        assertEquals(listOf("DRAFT", "PUBLISHED"), snapshot.definitions.single().items.map { it.name })
        assertEquals(emptyList<String>(), snapshot.definitions.single().aggregates)
    }

    @Test
    fun `parses shared and aggregate owned enum manifest definitions`() {
        val projectDir = Files.createTempDirectory("enum-manifest-source-ownership")
        val manifest = projectDir.resolve("enums.json")
        manifest.writeText(
            """
            [
              {
                "name": "SharedStatus",
                "package": "shared.enums",
                "items": [
                  { "value": 1, "name": "ACTIVE", "desc": "Active" }
                ],
                "aggregates": []
              },
              {
                "name": "OrderStatus",
                "package": "order",
                "items": [
                  { "value": 1, "name": "PAID", "desc": "Paid" }
                ],
                "aggregates": ["Order"]
              }
            ]
            """.trimIndent()
        )

        val snapshot = EnumManifestSourceProvider().collect(configFor(manifest.toAbsolutePath().toString()))

        assertEquals(listOf("SharedStatus", "OrderStatus"), snapshot.definitions.map { it.typeName })
        assertEquals(emptyList<String>(), snapshot.definitions[0].aggregates)
        assertEquals(listOf("Order"), snapshot.definitions[1].aggregates)
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
            EnumManifestSourceProvider().collect(configFor(manifest.toAbsolutePath().toString()))
        }

        assertEquals("duplicate shared enum definition: Status", error.message)
    }

    @Test
    fun `duplicate aggregate owned enum names fail only within same aggregate`() {
        val projectDir = Files.createTempDirectory("enum-manifest-source-duplicate-aggregate")
        val manifest = projectDir.resolve("enums.json")
        manifest.writeText(
            """
            [
              { "name": "Status", "package": "order", "aggregates": ["Order"], "items": [ { "value": 0, "name": "DRAFT", "desc": "Draft" } ] },
              { "name": "Status", "package": "payment", "aggregates": ["Payment"], "items": [ { "value": 1, "name": "PAID", "desc": "Paid" } ] }
            ]
            """.trimIndent()
        )

        val snapshot = EnumManifestSourceProvider().collect(configFor(manifest.toAbsolutePath().toString()))

        assertEquals(listOf(listOf("Order"), listOf("Payment")), snapshot.definitions.map { it.aggregates })
    }

    @Test
    fun `enum manifest rejects duplicate aggregate owned enum names within same aggregate`() {
        val projectDir = Files.createTempDirectory("enum-manifest-source-duplicate-same-aggregate")
        val manifest = projectDir.resolve("enums.json")
        manifest.writeText(
            """
            [
              { "name": "Status", "package": "order", "aggregates": ["Order"], "items": [ { "value": 0, "name": "DRAFT", "desc": "Draft" } ] },
              { "name": "Status", "package": "order.other", "aggregates": ["Order"], "items": [ { "value": 1, "name": "PAID", "desc": "Paid" } ] }
            ]
            """.trimIndent()
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            EnumManifestSourceProvider().collect(configFor(manifest.toAbsolutePath().toString()))
        }

        assertEquals("duplicate aggregate enum definition: Status in Order", error.message)
    }

    @Test
    fun `enum manifest rejects multiple aggregate owners`() {
        val projectDir = Files.createTempDirectory("enum-manifest-source-multiple-aggregates")
        val manifest = projectDir.resolve("enums.json")
        manifest.writeText(
            """
            [
              {
                "name": "OrderStatus",
                "package": "order",
                "items": [
                  { "value": 1, "name": "PAID", "desc": "Paid" }
                ],
                "aggregates": ["Order", "Payment"]
              }
            ]
            """.trimIndent()
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            EnumManifestSourceProvider().collect(configFor(manifest.toAbsolutePath().toString()))
        }

        assertEquals("enum OrderStatus may declare at most one aggregate", error.message)
    }

    @Test
    fun `enum manifest rejects deprecated translation generation flag`() {
        val projectDir = Files.createTempDirectory("enum-manifest-source-deprecated-translation")
        val manifest = projectDir.resolve("shared-enums.json")
        val removedFlag = "generate" + "Translation"
        manifest.writeText(
            """
            [
              {
                "name": "OrderStatus",
                "package": "shared",
                "$removedFlag": true,
                "items": [
                  { "value": 1, "name": "OPEN", "desc": "open" }
                ]
              }
            ]
            """.trimIndent()
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            EnumManifestSourceProvider().collect(configFor(manifest.toAbsolutePath().toString()))
        }

        assertEquals(
            "enum manifest field $removedFlag is removed; install an enum translation addon instead.",
            error.message,
        )
    }


    @Test
    fun `collects ordered typed enum property snapshots`() {
        val projectDir = Files.createTempDirectory("enum-manifest-source-fields")
        val manifest = projectDir.resolve("enums.json")
        manifest.writeText(
            """
            [{
              "name":"Disposition", "package":"shared",
              "fields":[{"name":"group","type":"String"},{"name":"terminal","type":"Boolean"}],
              "items":[{"value":0,"name":"RECEIVED","desc":"received","group":"pending","terminal":false}]
            }]
            """.trimIndent()
        )
        val declaration = EnumManifestSourceProvider().collect(configFor(manifest.toString())).declarations.single()
        assertEquals(listOf("group", "terminal"), declaration.fields.map { it.name })
        assertEquals(setOf("group", "terminal"), declaration.items.single().propertyValues.keys)
    }

    @Test
    fun `rejects missing unknown and non integral enum data`() {
        val cases = listOf(
            """[{"name":"Status","package":"shared","fields":[{"name":"group","type":"String"}],"items":[{"value":0,"name":"A","desc":"a"}]}]""" to "missing properties group",
            """[{"name":"Status","package":"shared","items":[{"value":0,"name":"A","desc":"a","group":"x"}]}]""" to "unknown fields group",
            """[{"name":"Status","package":"shared","items":[{"value":1.5,"name":"A","desc":"a"}]}]""" to "must be a JSON integral number",
        )
        cases.forEachIndexed { index, (json, evidence) ->
            val manifest = Files.createTempDirectory("enum-invalid-$index").resolve("enums.json")
            manifest.writeText(json)
            val error = assertThrows(IllegalArgumentException::class.java) {
                EnumManifestSourceProvider().collect(configFor(manifest.toString()))
            }
            check(error.message.orEmpty().contains(evidence)) { error.message.orEmpty() }
        }
    }

    @Test
    fun `typed declarations are not projected into lossy legacy definitions`() {
        val manifest = Files.createTempDirectory("enum-fields-compat").resolve("enums.json")
        manifest.writeText("""[{"name":"Status","package":"shared","fields":[{"name":"label","type":"String"}],"items":[{"value":1,"name":"ACTIVE","desc":"active","label":"Active"}]}]""")
        val snapshot = EnumManifestSourceProvider().collect(configFor(manifest.toString()))
        assertEquals(1, snapshot.declarations.size)
        assertEquals(emptyList<String>(), snapshot.definitions.map { it.typeName })
    }

    @Test
    fun `legacy definitions remain available for declarations without fields`() {
        val manifest = Files.createTempDirectory("enum-legacy-compat").resolve("enums.json")
        manifest.writeText("""[{"name":"Status","package":"shared","items":[{"value":1,"name":"ACTIVE","desc":"active"}]}]""")
        val snapshot = EnumManifestSourceProvider().collect(configFor(manifest.toString()))
        assertEquals(listOf("Status"), snapshot.definitions.map { it.typeName })
    }

    @Test
    fun `duplicate JSON members fail with manifest path`() {
        val manifest = Files.createTempDirectory("enum-duplicate-json").resolve("enums.json")
        manifest.writeText("""[{"name":"Status","name":"Other","package":"shared","items":[{"value":1,"name":"ACTIVE","desc":"active"}]}]""")
        val error = assertThrows(IllegalArgumentException::class.java) {
            EnumManifestSourceProvider().collect(configFor(manifest.toString()))
        }
        check(error.message.orEmpty().startsWith("enum manifest $manifest: invalid JSON:")) { error.message.orEmpty() }
        check(error.message.orEmpty().contains("Duplicate field 'name'")) { error.message.orEmpty() }
    }

    @Test
    fun `enum constants reject generated member names`() {
        listOf("Converter", "valueOfOrNull", "entries", "values", "valueOf").forEach { reserved ->
            val manifest = Files.createTempDirectory("enum-reserved-constant").resolve("enums.json")
            manifest.writeText("""[{"name":"Status","package":"shared","items":[{"value":1,"name":"$reserved","desc":"reserved"}]}]""")
            val error = assertThrows(IllegalArgumentException::class.java) {
                EnumManifestSourceProvider().collect(configFor(manifest.toString()))
            }
            check(error.message.orEmpty().contains("enum Status item $reserved")) { error.message.orEmpty() }
        }
    }
    private fun configFor(filePath: String): ProjectConfig =
        ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            typeRegistry = TypeRegistryConfig(),
            sources = mapOf(
                "enum-manifest" to SourceConfig(
                    options = mapOf("files" to listOf(filePath))
                )
            ),
            generators = emptyMap(),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
}
