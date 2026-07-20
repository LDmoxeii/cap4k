package com.only4.cap4k.plugin.pipeline.source.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertFailsWith

class DbTableAnnotationParserTest {

    @Test
    fun `table parser accepts only parent and ignore`() {
        val metadata = DbTableAnnotationParser.parse("video_post @Parent=content; @Ignore;")

        assertEquals("content", metadata.parentTable)
        assertTrue(metadata.ignored)
        assertEquals("video_post", metadata.cleanedComment)
    }

    @Test
    fun `table parser rejects uppercase alias names`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DbTableAnnotationParser.parse("@P=content;")
        }

        assertEquals(
            "unsupported table annotation @P. Supported table annotations: @Parent=<table>, @Ignore.",
            error.message,
        )
    }

    @TestFactory
    fun `rejects old table annotations through generic path`() = listOf(
        "@AggregateRoot=true;",
        "@Root=true;",
        "@R=true;",
        "@DynamicInsert=true;",
        "@DynamicUpdate=true;",
    ).map { comment ->
        DynamicTest.dynamicTest(comment) {
            val error = assertFailsWith<IllegalArgumentException> {
                DbTableAnnotationParser.parse(comment)
            }

            assertTrue(error.message!!.startsWith("unsupported table annotation @"))
        }
    }

    @Test
    fun `table parser rejects unsupported table annotation generically`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DbTableAnnotationParser.parse("Video post @CustomMarker;")
        }

        assertEquals(
            "unsupported table annotation @CustomMarker. Supported table annotations: @Parent=<table>, @Ignore.",
            error.message,
        )
    }

    @Test
    fun `table parser rejects blank parent value`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DbTableAnnotationParser.parse("@Parent=;")
        }

        assertEquals("blank @Parent value is not allowed.", error.message)
    }

    @Test
    fun `table parser rejects valueless parent annotation`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DbTableAnnotationParser.parse("@Parent;")
        }

        assertEquals("missing value for @Parent annotation.", error.message)
    }

    @Test
    fun `table parser rejects valued ignore marker`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DbTableAnnotationParser.parse("@Ignore=true;")
        }

        assertEquals("invalid @Ignore annotation: explicit values are not supported.", error.message)
    }
}
