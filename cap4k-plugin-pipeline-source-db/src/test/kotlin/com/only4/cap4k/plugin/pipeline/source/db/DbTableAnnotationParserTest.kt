package com.only4.cap4k.plugin.pipeline.source.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DbTableAnnotationParserTest {

    @Test
    fun `parser extracts provider specific table controls from comment`() {
        val metadata = DbTableAnnotationParser.parse(
            "@AggregateRoot=true;@DynamicInsert=true;@DynamicUpdate=true;"
        )

        assertEquals(true, metadata.aggregateRoot)
        assertEquals(true, metadata.dynamicInsert)
        assertEquals(true, metadata.dynamicUpdate)
    }

    @Test
    fun `parser extracts table ignore marker from comment`() {
        val metadata = DbTableAnnotationParser.parse("Framework table @I;")

        assertEquals(true, metadata.ignored)
        assertEquals("Framework table", metadata.cleanedComment)
    }

    @Test
    fun `parser rejects valued table ignore marker`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@I=true;")
        }

        assertEquals("invalid @Ignore/@I annotation: explicit values are not supported.", error.message)
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
    fun `parser rejects legacy soft delete column annotation with migration message`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@SoftDeleteColumn=deleted;")
        }

        assertEquals(
            "unsupported table annotation @SoftDeleteColumn: use @Deleted marker on the delete column instead",
            error.message,
        )
    }

    @Test
    fun `parser rejects legacy id generator annotation`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("Video post root @AggregateRoot=true;@IdGenerator=snowflakeIdGenerator;")
        }

        assertEquals(
            "unsupported table annotation @IdGenerator: use @GeneratedValue on the ID column instead",
            error.message,
        )
    }

    @Test
    fun `parser rejects legacy id generator alias annotation`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("Video post root @AggregateRoot=true;@IG=snowflakeIdGenerator;")
        }

        assertEquals(
            "unsupported table annotation @IG: use @GeneratedValue on the ID column instead",
            error.message,
        )
    }
}
