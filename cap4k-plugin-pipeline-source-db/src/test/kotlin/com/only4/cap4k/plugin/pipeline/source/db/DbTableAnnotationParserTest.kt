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

    @Test
    fun `parser rejects non strict provider boolean casing`() {
        val insertError = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@DynamicInsert=TRUE;")
        }
        val updateError = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@DynamicUpdate=FALSE;")
        }

        assertEquals("invalid @DynamicInsert value: TRUE", insertError.message)
        assertEquals("invalid @DynamicUpdate value: FALSE", updateError.message)
    }

    @Test
    fun `parser rejects conflicting duplicate dynamic insert annotations`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@DynamicInsert=true;@DynamicInsert=false;")
        }

        assertEquals("conflicting @DynamicInsert annotations on the same table comment.", error.message)
    }

    @Test
    fun `parser rejects conflicting duplicate dynamic update annotations`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@DynamicUpdate=true;@DynamicUpdate=false;")
        }

        assertEquals("conflicting @DynamicUpdate annotations on the same table comment.", error.message)
    }

    @Test
    fun `parser rejects conflicting duplicate soft delete column annotations`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@SoftDeleteColumn=deleted;@SoftDeleteColumn=is_deleted;")
        }

        assertEquals("conflicting @SoftDeleteColumn annotations on the same table comment.", error.message)
    }
}
