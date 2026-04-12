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
    fun `numeric built in defaults preserve kotlin literals`() {
        assertEquals(
            "1",
            DefaultValueFormatter.format(
                rawDefaultValue = "1",
                renderedType = "Int",
                nullable = false,
                fieldName = "retryCount",
            ),
        )
        assertEquals(
            "1.0",
            DefaultValueFormatter.format(
                rawDefaultValue = "1.0",
                renderedType = "Double",
                nullable = false,
                fieldName = "ratio",
            ),
        )
        assertEquals(
            "1f",
            DefaultValueFormatter.format(
                rawDefaultValue = "1f",
                renderedType = "Float",
                nullable = false,
                fieldName = "scale",
            ),
        )
    }

    @Test
    fun `numeric built in defaults reject invalid literals`() {
        val intEx = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "1.0",
                renderedType = "Int",
                nullable = false,
                fieldName = "retryCount",
            )
        }
        assertEquals(
            "invalid default value for field retryCount: 1.0 is not a valid Int literal",
            intEx.message,
        )

        val doubleEx = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "1f",
                renderedType = "Double",
                nullable = false,
                fieldName = "ratio",
            )
        }
        assertEquals(
            "invalid default value for field ratio: 1f is not a valid Double literal",
            doubleEx.message,
        )

        val floatEx = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "1",
                renderedType = "Float",
                nullable = false,
                fieldName = "scale",
            )
        }
        assertEquals(
            "invalid default value for field scale: 1 is not a valid Float literal",
            floatEx.message,
        )
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
    fun `incompatible empty collection expressions are rejected`() {
        val mutableListEx = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "emptyList()",
                renderedType = "MutableList<String>",
                nullable = false,
                fieldName = "tags",
            )
        }
        assertEquals(
            "invalid default value for field tags: emptyList() is incompatible with rendered type MutableList<String>",
            mutableListEx.message,
        )

        val setEx = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "mutableListOf()",
                renderedType = "Set<String>",
                nullable = false,
                fieldName = "tags",
            )
        }
        assertEquals(
            "invalid default value for field tags: mutableListOf() is incompatible with rendered type Set<String>",
            setEx.message,
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
