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
    fun `parser extracts generated value marker deleted version and write controls from comment`() {
        val metadata = DbColumnAnnotationParser.parse(
            "audit field @GeneratedValue;@Deleted;@Version;@Insertable=false;@Updatable=false;"
        )

        assertEquals(true, metadata.generatedValueDeclared)
        assertEquals(null, metadata.generatedValueStrategy)
        assertEquals(true, metadata.deleted)
        assertEquals(true, metadata.version)
        assertEquals(false, metadata.insertable)
        assertEquals(false, metadata.updatable)
    }

    @Test
    fun `parser supports explicit generated value strategies and alias`() {
        val uuid7 = DbColumnAnnotationParser.parse("@GeneratedValue=uuid7;")
        val snowflake = DbColumnAnnotationParser.parse("@GeneratedValue=SNOWFLAKE-LONG;")
        val identity = DbColumnAnnotationParser.parse("@GeneratedValue=IDENTITY;")
        val databaseIdentity = DbColumnAnnotationParser.parse("@GeneratedValue=database-identity;")

        assertEquals("uuid7", uuid7.generatedValueStrategy)
        assertEquals("snowflake-long", snowflake.generatedValueStrategy)
        assertEquals("identity", identity.generatedValueStrategy)
        assertEquals("identity", databaseIdentity.generatedValueStrategy)
    }

    @Test
    fun `parser rejects unsupported generated value strategy`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@GeneratedValue=SEQUENCE;")
        }

        assertEquals("unsupported @GeneratedValue strategy in this slice: SEQUENCE", error.message)
    }

    @Test
    fun `parser keeps deleted and version null when source is silent`() {
        val metadata = DbColumnAnnotationParser.parse("plain comment")

        assertEquals(null, metadata.version)
        assertEquals(null, metadata.deleted)
        assertEquals(null, metadata.managed)
        assertEquals(null, metadata.exposed)
        assertEquals(false, metadata.generatedValueDeclared)
    }

    @Test
    fun `parser rejects valued deleted and version markers`() {
        val versionError = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@Version=true;")
        }
        val deletedError = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@Deleted=false;")
        }

        assertEquals("invalid @Version annotation: explicit values are not supported.", versionError.message)
        assertEquals("invalid @Deleted annotation: explicit values are not supported.", deletedError.message)
    }

    @Test
    fun `parser supports managed and exposed markers`() {
        val managed = DbColumnAnnotationParser.parse("@Managed;")
        val exposed = DbColumnAnnotationParser.parse("@Exposed;")

        assertEquals(true, managed.managed)
        assertEquals(null, managed.exposed)
        assertEquals(null, exposed.managed)
        assertEquals(true, exposed.exposed)
    }

    @Test
    fun `parser rejects valued managed exposed and mutual exclusion`() {
        val managedError = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@Managed=true;")
        }
        val exposedBooleanError = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@Exposed=false;")
        }
        val exposedNumericError = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@Exposed=1;")
        }
        val conflictError = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@Managed;@Exposed;")
        }

        assertEquals("invalid @Managed annotation: explicit values are not supported.", managedError.message)
        assertEquals("invalid @Exposed annotation: explicit values are not supported.", exposedBooleanError.message)
        assertEquals("invalid @Exposed annotation: explicit values are not supported.", exposedNumericError.message)
        assertEquals("conflicting @Managed/@Exposed annotations on the same column comment.", conflictError.message)
    }

    @Test
    fun `parser rejects invalid insertable updatable boolean annotation values`() {
        val insertableError = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@Insertable=maybe;")
        }
        val updatableError = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@Updatable=maybe;")
        }

        assertEquals("invalid @Insertable boolean value in this slice: maybe", insertableError.message)
        assertEquals("invalid @Updatable boolean value in this slice: maybe", updatableError.message)
    }
}
