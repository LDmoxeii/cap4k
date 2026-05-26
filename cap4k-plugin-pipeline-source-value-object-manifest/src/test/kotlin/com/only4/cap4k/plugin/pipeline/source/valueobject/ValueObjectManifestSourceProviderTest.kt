package com.only4.cap4k.plugin.pipeline.source.valueobject

import com.only4.cap4k.plugin.pipeline.api.ValueObjectScope
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
    fun `parses shared and aggregate json value objects`() {
        val file = tempDir.resolve("value-objects.json")
        file.writeText(
            """
            [
              {
                "name": "Money",
                "scope": "shared",
                "package": "shared.values",
                "storage": "json",
                "fields": [
                  { "name": "amount", "type": "BigDecimal" },
                  { "name": "currency", "type": "String" }
                ]
              },
              {
                "name": "PublishWindow",
                "scope": "aggregate",
                "aggregate": "Content",
                "package": "content.values",
                "storage": "json",
                "fields": [
                  { "name": "startAt", "type": "Instant", "nullable": true }
                ]
              }
            ]
            """.trimIndent()
        )

        val snapshot = ValueObjectManifestSourceProvider().load(listOf(file))

        assertEquals(2, snapshot.valueObjects.size)
        assertEquals(ValueObjectScope.SHARED, snapshot.valueObjects[0].scope)
        assertEquals(ValueObjectScope.AGGREGATE, snapshot.valueObjects[1].scope)
        assertEquals("Content", snapshot.valueObjects[1].aggregate)
    }

    @Test
    fun `aggregate scope requires aggregate name`() {
        val file = tempDir.resolve("value-objects.json")
        file.writeText(
            """
            [
              {
                "name": "PublishWindow",
                "scope": "aggregate",
                "package": "content.values",
                "storage": "json",
                "fields": []
              }
            ]
            """.trimIndent()
        )

        val error = assertThrows<IllegalArgumentException> {
            ValueObjectManifestSourceProvider().load(listOf(file))
        }

        assertTrue(error.message!!.contains("aggregate scope requires aggregate"))
    }

    @Test
    fun `shared scope rejects aggregate name`() {
        val file = tempDir.resolve("value-objects.json")
        file.writeText(
            """
            [
              {
                "name": "Money",
                "scope": "shared",
                "aggregate": "Content",
                "package": "shared.values",
                "storage": "json",
                "fields": []
              }
            ]
            """.trimIndent()
        )

        val error = assertThrows<IllegalArgumentException> {
            ValueObjectManifestSourceProvider().load(listOf(file))
        }

        assertTrue(error.message!!.contains("shared scope must not set aggregate"))
    }

    @Test
    fun `rejects unsupported scope and storage`() {
        val invalidScope = tempDir.resolve("invalid-scope.json")
        invalidScope.writeText(
            """
            [
              { "name": "Money", "scope": "global", "package": "shared.values", "storage": "json", "fields": [] }
            ]
            """.trimIndent()
        )
        val scopeError = assertThrows<IllegalArgumentException> {
            ValueObjectManifestSourceProvider().load(listOf(invalidScope))
        }
        assertTrue(scopeError.message!!.contains("scope must be shared or aggregate"))

        val invalidStorage = tempDir.resolve("invalid-storage.json")
        invalidStorage.writeText(
            """
            [
              { "name": "Money", "scope": "shared", "package": "shared.values", "storage": "table", "fields": [] }
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
                "scope": "shared",
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
              { "name": "Money", "scope": "shared", "package": "shared.values", "storage": "json", "fields": [] },
              { "name": "Money", "scope": "shared", "package": "shared.other", "storage": "json", "fields": [] }
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
              { "name": "Window", "scope": "aggregate", "aggregate": "Content", "package": "content.values", "storage": "json", "fields": [] },
              { "name": "Window", "scope": "aggregate", "aggregate": "Content", "package": "content.other", "storage": "json", "fields": [] },
              { "name": "Window", "scope": "aggregate", "aggregate": "Campaign", "package": "campaign.values", "storage": "json", "fields": [] }
            ]
            """.trimIndent()
        )
        val aggregateError = assertThrows<IllegalArgumentException> {
            ValueObjectManifestSourceProvider().load(listOf(duplicateAggregate))
        }
        assertTrue(aggregateError.message!!.contains("duplicate aggregate value object definition: Window in Content"))
    }
}
