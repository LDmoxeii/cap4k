package com.only4.cap4k.plugin.pipeline.source.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DbColumnAnnotationParserTest {

    @Test
    fun `parser extracts type binding and enum items from comment`() {
        val metadata = DbColumnAnnotationParser.parse(
            "status field @T=VideoPostVisibility;@E=0:HIDDEN:Hidden|1:PUBLIC:Public;"
        )

        assertEquals("VideoPostVisibility", metadata.typeBinding)
        assertEquals(listOf("HIDDEN", "PUBLIC"), metadata.enumItems.map { it.name })
        assertEquals(listOf(0, 1), metadata.enumItems.map { it.value })
    }

    @Test
    fun `parser keeps type-only binding without enum generation`() {
        val metadata = DbColumnAnnotationParser.parse("shared status @T=Status;")

        assertEquals("Status", metadata.typeBinding)
        assertEquals(emptyList<Any>(), metadata.enumItems)
    }

    @Test
    fun `enum definition without type binding fails`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@E=0:DRAFT:Draft|1:PUBLISHED:Published;")
        }

        assertEquals("@E requires @T on the same column comment.", error.message)
    }

    @Test
    fun `conflicting type annotations fail fast`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@T=Status;@TYPE=VideoPostStatus;")
        }

        assertEquals("conflicting @T/@TYPE annotations on the same column comment.", error.message)
    }

    @Test
    fun `conflicting enum annotations fail fast`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@T=Status;@E=0:DRAFT:Draft;@ENUM=1:PUBLISHED:Published;")
        }

        assertEquals("conflicting @E/@ENUM annotations on the same column comment.", error.message)
    }

    @Test
    fun `parser extracts generated value version and write controls from comment`() {
        val metadata = DbColumnAnnotationParser.parse(
            "audit field @GeneratedValue=IDENTITY;@Version=true;@Insertable=false;@Updatable=false;"
        )

        assertEquals("IDENTITY", metadata.generatedValueStrategy)
        assertEquals(true, metadata.version)
        assertEquals(false, metadata.insertable)
        assertEquals(false, metadata.updatable)
    }

    @Test
    fun `parser rejects unsupported generated value strategy`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@GeneratedValue=SEQUENCE;")
        }

        assertEquals("unsupported @GeneratedValue strategy in this slice: SEQUENCE", error.message)
    }
}
