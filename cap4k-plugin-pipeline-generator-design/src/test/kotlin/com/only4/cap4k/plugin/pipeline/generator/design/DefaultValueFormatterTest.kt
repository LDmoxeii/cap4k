package com.only4.cap4k.plugin.pipeline.generator.design

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DefaultValueFormatterTest {

    @Test
    fun `raw string defaults are quoted`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "demo",
            renderedType = "String",
            nullable = false,
            fieldName = "title",
        )

        assertEquals("\"demo\"", actual)
    }

    @Test
    fun `quoted string remains unchanged`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "\"demo\"",
            renderedType = "String",
            nullable = false,
            fieldName = "title",
        )

        assertEquals("\"demo\"", actual)
    }

    @Test
    fun `long literal gets suffix when needed`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "1",
            renderedType = "Long",
            nullable = false,
            fieldName = "retryCount",
        )

        assertEquals("1L", actual)
    }

    @Test
    fun `explicit constant expression is preserved`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "java.time.LocalDateTime.MIN",
            renderedType = "java.time.LocalDateTime",
            nullable = false,
            fieldName = "createdAt",
        )

        assertEquals("java.time.LocalDateTime.MIN", actual)
    }

    @Test
    fun `constant expression is preserved for compatible custom type`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "VideoStatus.PROCESSING",
            renderedType = "VideoStatus",
            nullable = false,
            fieldName = "status",
        )

        assertEquals("VideoStatus.PROCESSING", actual)
    }

    @Test
    fun `supported empty collection expressions are preserved`() {
        assertEquals(
            "emptyList()",
            DefaultValueFormatter.format(
                rawDefaultValue = "emptyList()",
                renderedType = "List<String>",
                nullable = false,
                fieldName = "tags",
            ),
        )
        assertEquals(
            "emptySet()",
            DefaultValueFormatter.format(
                rawDefaultValue = "emptySet()",
                renderedType = "Set<String>",
                nullable = false,
                fieldName = "tags",
            ),
        )
        assertEquals(
            "mutableListOf()",
            DefaultValueFormatter.format(
                rawDefaultValue = "mutableListOf()",
                renderedType = "MutableList<String>",
                nullable = false,
                fieldName = "tags",
            ),
        )
        assertEquals(
            "mutableSetOf()",
            DefaultValueFormatter.format(
                rawDefaultValue = "mutableSetOf()",
                renderedType = "MutableSet<String>",
                nullable = false,
                fieldName = "tags",
            ),
        )
    }

    @Test
    fun `nullable field accepts null`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "null",
            renderedType = "String",
            nullable = true,
            fieldName = "title",
        )

        assertEquals("null", actual)
    }

    @Test
    fun `null default is rejected for non-nullable field`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "null",
                renderedType = "String",
                nullable = false,
                fieldName = "title",
            )
        }

        assertEquals(
            "invalid default value for field title: null is only allowed for nullable fields",
            ex.message,
        )
    }

    @Test
    fun `escapes embedded backslashes and quotes in raw string defaults`() {
        assertEquals(
            "\"C:\\\\tmp\"",
            DefaultValueFormatter.format(
                rawDefaultValue = "C:\\tmp",
                renderedType = "String",
                nullable = false,
                fieldName = "path",
            ),
        )
        assertEquals(
            "\"he said \\\"x\\\"\"",
            DefaultValueFormatter.format(
                rawDefaultValue = "he said \"x\"",
                renderedType = "String",
                nullable = false,
                fieldName = "message",
            ),
        )
    }

    @Test
    fun `escapes newline tab carriage return and dollar in raw string defaults`() {
        assertEquals(
            "\"line1\\nline2\\t\\r\\\$value\"",
            DefaultValueFormatter.format(
                rawDefaultValue = "line1\nline2\t\r\$value",
                renderedType = "String",
                nullable = false,
                fieldName = "message",
            ),
        )
    }

    @Test
    fun `dotted expression is rejected for string field`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "VideoStatus.PROCESSING",
                renderedType = "String",
                nullable = false,
                fieldName = "title",
            )
        }

        assertEquals(
            "invalid default value for field title: unsupported default value expression: VideoStatus.PROCESSING",
            ex.message,
        )
    }

    @Test
    fun `invalid long literal is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "12abc",
                renderedType = "Long",
                nullable = false,
                fieldName = "retryCount",
            )
        }

        assertEquals(
            "invalid default value for field retryCount: 12abc is not a valid Long literal",
            ex.message,
        )
    }

    @Test
    fun `dotted expression is rejected for long field`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "java.time.LocalDateTime.MIN",
                renderedType = "Long",
                nullable = false,
                fieldName = "retryCount",
            )
        }

        assertEquals(
            "invalid default value for field retryCount: unsupported default value expression: java.time.LocalDateTime.MIN",
            ex.message,
        )
    }

    @Test
    fun `dotted expression is rejected for boolean field`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "FeatureFlags.ENABLED",
                renderedType = "Boolean",
                nullable = false,
                fieldName = "enabled",
            )
        }

        assertEquals(
            "invalid default value for field enabled: unsupported default value expression: FeatureFlags.ENABLED",
            ex.message,
        )
    }

    @Test
    fun `unsupported expression is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "foo(bar)",
                renderedType = "VideoStatus",
                nullable = false,
                fieldName = "status",
            )
        }

        assertEquals(
            "invalid default value for field status: unsupported default value expression: foo(bar)",
            ex.message,
        )
    }

    @Test
    fun `invalid boolean literal is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "yes",
                renderedType = "Boolean",
                nullable = false,
                fieldName = "enabled",
            )
        }

        assertEquals(
            "invalid default value for field enabled: Boolean defaults must be true or false",
            ex.message,
        )
    }
}
