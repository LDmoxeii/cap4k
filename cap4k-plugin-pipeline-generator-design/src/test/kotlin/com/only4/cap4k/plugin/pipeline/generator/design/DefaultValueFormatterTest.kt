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
