package com.only4.cap4k.plugin.pipeline.source.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DbTableAnnotationParserTest {

    @Test
    fun `parser extracts provider specific table controls from comment`() {
        val metadata = DbTableAnnotationParser.parse(
            "@AggregateRoot=true;@DynamicInsert=true;@DynamicUpdate=true;@SoftDeleteColumn=deleted;"
        )

        assertEquals(true, metadata.aggregateRoot)
        assertEquals(true, metadata.dynamicInsert)
        assertEquals(true, metadata.dynamicUpdate)
        assertEquals("deleted", metadata.softDeleteColumn)
    }

    @Test
    fun `parser rejects malformed dynamic insert value`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@DynamicInsert=maybe;")
        }

        assertEquals("invalid @DynamicInsert value: maybe", error.message)
    }
}
