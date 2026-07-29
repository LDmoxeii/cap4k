package com.only4.cap4k.plugin.pipeline.source.valueobject

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class ValueObjectManifestSourceProviderTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `parses shared non persistent and aggregate owned json value objects`() {
        val file = tempDir.resolve("value-objects.json")
        file.writeText(
            """
            [
              {
                "name": "Money",
                "package": "shared.values",
                "aggregates": [],
                "fields": [
                  { "name": "amount", "type": "BigDecimal" },
                  { "name": "currency", "type": "String" }
                ]
              },
              {
                "name": "PublishWindow",
                "package": "content.values",
                "persistence": { "kind": "json" },
                "aggregates": ["Content"],
                "fields": [
                  { "name": "startAt", "type": "Instant?" }
                ]
              }
            ]
            """.trimIndent()
        )

        val snapshot = ValueObjectManifestSourceProvider().load(listOf(file))

        assertEquals(2, snapshot.declarations.size)
        assertEquals(emptyList<String>(), snapshot.declarations[0].aggregates)
        assertEquals(null, snapshot.declarations[0].persistence)
        assertEquals(listOf("Content"), snapshot.declarations[1].aggregates)
        assertEquals("json", snapshot.declarations[1].persistence?.kind)
        assertEquals("Instant?", snapshot.declarations[1].fields.single().typeExpression)
    }

    @Test
    fun `omitted aggregates defaults to shared value object`() {
        val file = tempDir.resolve("value-objects.json")
        file.writeText(
            """
            [
              {
                "name": "Money",
                "package": "shared.values",
                "fields": []
              }
            ]
            """.trimIndent()
        )

        val snapshot = ValueObjectManifestSourceProvider().load(listOf(file))

        assertEquals(emptyList<String>(), snapshot.declarations.single().aggregates)
        assertEquals(null, snapshot.declarations.single().persistence)
    }

    @Test
    fun `rejects removed scope and aggregate fields`() {
        val file = tempDir.resolve("value-objects.json")
        file.writeText(
            """
            [
              { "name": "Money", "package": "shared.values", "scope": "shared", "aggregate": "Order", "fields": [] }
            ]
            """.trimIndent()
        )

        val error = assertThrows<IllegalArgumentException> {
            ValueObjectManifestSourceProvider().load(listOf(file))
        }

        assertTrue(error.message!!.contains("value object Money fields scope and aggregate are removed; use aggregates instead"))
    }

    @Test
    fun `rejects removed storage even when it declares json`() {
        val removedStorage = tempDir.resolve("removed-storage.json")
        removedStorage.writeText(
            """
            [
              { "name": "Money", "package": "shared.values", "storage": "json", "fields": [] }
            ]
            """.trimIndent()
        )
        val storageError = assertThrows<IllegalArgumentException> {
            ValueObjectManifestSourceProvider().load(listOf(removedStorage))
        }
        assertTrue(storageError.message!!.contains("field storage is removed; use persistence instead"))
    }

    @Test
    fun `rejects unknown persistence kind options and null projection`() {
        listOf(
            """{ "name": "Money", "package": "shared.values", "persistence": { "kind": "table" }, "fields": [] }""" to
                "persistence.kind is unsupported: table",
            """{ "name": "Money", "package": "shared.values", "persistence": { "kind": "json", "column": "payload" }, "fields": [] }""" to
                "persistence has unsupported options: column",
            """{ "name": "Money", "package": "shared.values", "persistence": null, "fields": [] }""" to
                "persistence must be an object",
        ).forEachIndexed { index, (declaration, expectedMessage) ->
            val file = tempDir.resolve("invalid-persistence-$index.json")
            file.writeText("[$declaration]")

            val error = assertThrows<IllegalArgumentException> {
                ValueObjectManifestSourceProvider().load(listOf(file))
            }

            assertTrue(error.message!!.contains("value object Money $expectedMessage"))
        }
    }

    @Test
    fun `fields require name and type`() {
        val file = tempDir.resolve("value-objects.json")
        file.writeText(
            """
            [
              {
                "name": "Money",
                "package": "shared.values",
                "persistence": { "kind": "json" },
                "fields": [
                  { "name": "amount" }
                ]
              }
            ]
            """.trimIndent()
        )

        val error = assertThrows<IllegalArgumentException> {
            ValueObjectManifestSourceProvider().load(listOf(file))
        }

        assertTrue(error.message!!.contains("field type is required"))
    }

    @Test
    fun `rejects removed field nullable property`() {
        val file = tempDir.resolve("removed-nullable.json")
        file.writeText(
            """
            [
              {
                "name": "Money",
                "package": "shared.values",
                "fields": [
                  { "name": "amount", "type": "BigDecimal", "nullable": true }
                ]
              }
            ]
            """.trimIndent()
        )

        val error = assertThrows<IllegalArgumentException> {
            ValueObjectManifestSourceProvider().load(listOf(file))
        }

        assertTrue(error.message!!.contains("value object Money field amount property nullable is removed"))
    }

    @Test
    fun `duplicate shared names fail globally and aggregate names fail within aggregate`() {
        val duplicateShared = tempDir.resolve("duplicate-shared.json")
        duplicateShared.writeText(
            """
            [
              { "name": "Money", "package": "shared.values", "fields": [] },
              { "name": "Money", "package": "shared.other", "fields": [] }
            ]
            """.trimIndent()
        )
        val sharedError = assertThrows<IllegalArgumentException> {
            ValueObjectManifestSourceProvider().load(listOf(duplicateShared))
        }
        assertTrue(sharedError.message!!.contains("duplicate shared value object definition: Money"))

        val duplicateAggregate = tempDir.resolve("duplicate-aggregate.json")
        duplicateAggregate.writeText(
            """
            [
              { "name": "Window", "aggregates": ["Content"], "package": "content.values", "fields": [] },
              { "name": "Window", "aggregates": ["Content"], "package": "content.other", "fields": [] },
              { "name": "Window", "aggregates": ["Campaign"], "package": "campaign.values", "fields": [] }
            ]
            """.trimIndent()
        )
        val aggregateError = assertThrows<IllegalArgumentException> {
            ValueObjectManifestSourceProvider().load(listOf(duplicateAggregate))
        }
        assertTrue(aggregateError.message!!.contains("duplicate aggregate value object definition: Window in Content"))
    }

    @Test
    fun `value object manifest rejects multiple aggregate owners`() {
        val file = tempDir.resolve("multiple-aggregates.json")
        file.writeText(
            """
            [
              { "name": "Money", "package": "shared.values", "aggregates": ["Order", "Payment"], "fields": [] }
            ]
            """.trimIndent()
        )

        val error = assertThrows<IllegalArgumentException> {
            ValueObjectManifestSourceProvider().load(listOf(file))
        }

        assertEquals("value object Money may declare at most one aggregate", error.message)
    }
}
