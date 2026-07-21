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
    fun `parses shared and aggregate owned json value objects`() {
        val file = tempDir.resolve("value-objects.json")
        file.writeText(
            """
            [
              {
                "name": "Money",
                "package": "shared.values",
                "storage": "json",
                "aggregates": [],
                "fields": [
                  { "name": "amount", "type": "BigDecimal" },
                  { "name": "currency", "type": "String" }
                ]
              },
              {
                "name": "PublishWindow",
                "package": "content.values",
                "storage": "json",
                "aggregates": ["Content"],
                "fields": [
                  { "name": "startAt", "type": "Instant", "nullable": true }
                ]
              }
            ]
            """.trimIndent()
        )

        val snapshot = ValueObjectManifestSourceProvider().load(listOf(file))

        assertEquals(2, snapshot.valueObjects.size)
        assertEquals(emptyList<String>(), snapshot.valueObjects[0].aggregates)
        assertEquals(listOf("Content"), snapshot.valueObjects[1].aggregates)
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
                "storage": "json",
                "fields": []
              }
            ]
            """.trimIndent()
        )

        val snapshot = ValueObjectManifestSourceProvider().load(listOf(file))

        assertEquals(emptyList<String>(), snapshot.valueObjects.single().aggregates)
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
    fun `rejects unsupported storage`() {
        val invalidStorage = tempDir.resolve("invalid-storage.json")
        invalidStorage.writeText(
            """
            [
              { "name": "Money", "package": "shared.values", "storage": "table", "fields": [] }
            ]
            """.trimIndent()
        )
        val storageError = assertThrows<IllegalArgumentException> {
            ValueObjectManifestSourceProvider().load(listOf(invalidStorage))
        }
        assertTrue(storageError.message!!.contains("storage must be json"))
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
                "storage": "json",
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
    fun `duplicate shared names fail globally and aggregate names fail within aggregate`() {
        val duplicateShared = tempDir.resolve("duplicate-shared.json")
        duplicateShared.writeText(
            """
            [
              { "name": "Money", "package": "shared.values", "storage": "json", "fields": [] },
              { "name": "Money", "package": "shared.other", "storage": "json", "fields": [] }
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
              { "name": "Window", "aggregates": ["Content"], "package": "content.values", "storage": "json", "fields": [] },
              { "name": "Window", "aggregates": ["Content"], "package": "content.other", "storage": "json", "fields": [] },
              { "name": "Window", "aggregates": ["Campaign"], "package": "campaign.values", "storage": "json", "fields": [] }
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
