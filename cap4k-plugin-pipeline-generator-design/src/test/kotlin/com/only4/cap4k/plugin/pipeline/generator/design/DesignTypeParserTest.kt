package com.only4.cap4k.plugin.pipeline.generator.design

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DesignTypeParserTest {

    @Test
    fun `parses simple type token`() {
        assertParsed(
            raw = "String",
            expected = designType(
                tokenText = "String",
            ),
        )
    }

    @Test
    fun `parses nullable simple type token`() {
        assertParsed(
            raw = "String?",
            expected = designType(
                tokenText = "String",
                nullable = true,
            ),
        )
    }

    @Test
    fun `parses single generic argument in order`() {
        assertParsed(
            raw = "List<String>",
            expected = designType(
                tokenText = "List",
                arguments = listOf(
                    designType(tokenText = "String"),
                ),
            ),
        )
    }

    @Test
    fun `parses multiple generic arguments in order`() {
        assertParsed(
            raw = "Map<String, Long>",
            expected = designType(
                tokenText = "Map",
                arguments = listOf(
                    designType(tokenText = "String"),
                    designType(tokenText = "Long"),
                ),
            ),
        )
    }

    @Test
    fun `parses fqcn child type with nullability`() {
        assertParsed(
            raw = "List<com.foo.Status?>",
            expected = designType(
                tokenText = "List",
                arguments = listOf(
                    designType(
                        tokenText = "com.foo.Status",
                        nullable = true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `fails on mismatched angle brackets`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DesignTypeParser.parse("List<String")
        }

        assertEquals("mismatched angle brackets in type: List<String", ex.message)
    }

    @Test
    fun `fails on empty generic argument`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DesignTypeParser.parse("Map<String,,Long>")
        }

        assertEquals("empty generic argument in type: Map<String,,Long>", ex.message)
    }

    @Test
    fun `fails on trailing generic comma`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DesignTypeParser.parse("Map<String, Long,>")
        }

        assertEquals("empty generic argument in type: Map<String, Long,>", ex.message)
    }

    private fun assertParsed(raw: String, expected: DesignTypeModel) {
        assertEquals(expected, DesignTypeParser.parse(raw))
    }

    private fun designType(
        tokenText: String,
        nullable: Boolean = false,
        arguments: List<DesignTypeModel> = emptyList(),
    ) = DesignTypeModel(
        tokenText = tokenText,
        nullable = nullable,
        arguments = arguments,
    )
}
