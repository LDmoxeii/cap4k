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
    fun `quoted string with supported escapes remains unchanged`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "\"line1\\nline2\\t\\r\\\$value\\\\\\\"\"",
            renderedType = "String",
            nullable = false,
            fieldName = "title",
        )

        assertEquals("\"line1\\nline2\\t\\r\\\$value\\\\\\\"\"", actual)
    }

    @Test
    fun `quoted string with backspace escape remains unchanged`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "\"line1\\bline2\"",
            renderedType = "String",
            nullable = false,
            fieldName = "title",
        )

        assertEquals("\"line1\\bline2\"", actual)
    }

    @Test
    fun `quoted string with apostrophe escape remains unchanged`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "\"it\\'s fine\"",
            renderedType = "String",
            nullable = false,
            fieldName = "title",
        )

        assertEquals("\"it\\'s fine\"", actual)
    }

    @Test
    fun `quoted string with unicode escape remains unchanged`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "\"\\u0041\"",
            renderedType = "String",
            nullable = false,
            fieldName = "title",
        )

        assertEquals("\"\\u0041\"", actual)
    }

    @Test
    fun `quoted string containing a raw newline is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = """
                    |"line1
                    |line2"
                """.trimMargin(),
                renderedType = "String",
                nullable = false,
                fieldName = "title",
            )
        }

        assertEquals(
            "invalid default value for field title: invalid String literal: \"line1\nline2\"",
            ex.message,
        )
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
    fun `int literal is preserved`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "42",
            renderedType = "Int",
            nullable = false,
            fieldName = "retryCount",
        )

        assertEquals("42", actual)
    }

    @Test
    fun `double literal is preserved`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "3.14",
            renderedType = "Double",
            nullable = false,
            fieldName = "threshold",
        )

        assertEquals("3.14", actual)
    }

    @Test
    fun `double exponent literals are preserved`() {
        assertEquals(
            "1e3",
            DefaultValueFormatter.format(
                rawDefaultValue = "1e3",
                renderedType = "Double",
                nullable = false,
                fieldName = "threshold",
            ),
        )
        assertEquals(
            "1.0e3",
            DefaultValueFormatter.format(
                rawDefaultValue = "1.0e3",
                renderedType = "Double",
                nullable = false,
                fieldName = "threshold",
            ),
        )
    }

    @Test
    fun `float literal with suffix is preserved`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "2.5f",
            renderedType = "Float",
            nullable = false,
            fieldName = "ratio",
        )

        assertEquals("2.5f", actual)
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
    fun `matching simple constant expression owner is preserved`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "LocalDateTime.MIN",
            renderedType = "LocalDateTime",
            nullable = false,
            fieldName = "createdAt",
        )

        assertEquals("LocalDateTime.MIN", actual)
    }

    @Test
    fun `matching fqcn constant expression owner is preserved`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "com.foo.Status.OK",
            renderedType = "com.foo.Status",
            nullable = false,
            fieldName = "status",
        )

        assertEquals("com.foo.Status.OK", actual)
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
    fun `nullable compatible empty collection expression is preserved`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "emptyList()",
            renderedType = "List<String>?",
            nullable = true,
            fieldName = "tags",
        )

        assertEquals("emptyList()", actual)
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
    fun `malformed quoted string is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "\"bad\\q\"",
                renderedType = "String",
                nullable = false,
                fieldName = "title",
            )
        }

        assertEquals(
            "invalid default value for field title: invalid String literal: \"bad\\q\"",
            ex.message,
        )
    }

    @Test
    fun `quoted string with bare inner quote is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "\"a\"b\"",
                renderedType = "String",
                nullable = false,
                fieldName = "title",
            )
        }

        assertEquals(
            "invalid default value for field title: invalid String literal: \"a\"b\"",
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
    fun `invalid int literal is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "12abc",
                renderedType = "Int",
                nullable = false,
                fieldName = "retryCount",
            )
        }

        assertEquals(
            "invalid default value for field retryCount: 12abc is not a valid Int literal",
            ex.message,
        )
    }

    @Test
    fun `int overflow literal is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "2147483648",
                renderedType = "Int",
                nullable = false,
                fieldName = "retryCount",
            )
        }

        assertEquals(
            "invalid default value for field retryCount: 2147483648 is not a valid Int literal",
            ex.message,
        )
    }

    @Test
    fun `invalid double literal is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "3.14d",
                renderedType = "Double",
                nullable = false,
                fieldName = "threshold",
            )
        }

        assertEquals(
            "invalid default value for field threshold: 3.14d is not a valid Double literal",
            ex.message,
        )
    }

    @Test
    fun `invalid float literal is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "2.5",
                renderedType = "Float",
                nullable = false,
                fieldName = "ratio",
            )
        }

        assertEquals(
            "invalid default value for field ratio: 2.5 is not a valid Float literal",
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
    fun `long overflow literal is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "9223372036854775808",
                renderedType = "Long",
                nullable = false,
                fieldName = "retryCount",
            )
        }

        assertEquals(
            "invalid default value for field retryCount: 9223372036854775808 is not a valid Long literal",
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
    fun `incompatible empty collection expressions are rejected`() {
        val listEx = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "emptyList()",
                renderedType = "Set<String>",
                nullable = false,
                fieldName = "tags",
            )
        }
        assertEquals(
            "invalid default value for field tags: emptyList() is not compatible with Set<String>",
            listEx.message,
        )

        val setEx = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "mutableSetOf()",
                renderedType = "MutableList<String>",
                nullable = false,
                fieldName = "tags",
            )
        }
        assertEquals(
            "invalid default value for field tags: mutableSetOf() is not compatible with MutableList<String>",
            setEx.message,
        )
    }

    @Test
    fun `collection constant expression is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "VideoStatus.PROCESSING",
                renderedType = "List<String>",
                nullable = false,
                fieldName = "tags",
            )
        }

        assertEquals(
            "invalid default value for field tags: unsupported default value expression: VideoStatus.PROCESSING",
            ex.message,
        )
    }

    @Test
    fun `mismatched constant expression owner is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "VideoStatus.PROCESSING",
                renderedType = "LocalDateTime",
                nullable = false,
                fieldName = "createdAt",
            )
        }

        assertEquals(
            "invalid default value for field createdAt: unsupported default value expression: VideoStatus.PROCESSING",
            ex.message,
        )
    }

    @Test
    fun `mismatched fqcn constant expression owner is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "com.bar.Status.OK",
                renderedType = "com.foo.Status",
                nullable = false,
                fieldName = "status",
            )
        }

        assertEquals(
            "invalid default value for field status: unsupported default value expression: com.bar.Status.OK",
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
