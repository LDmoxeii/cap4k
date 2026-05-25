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
    fun `parser extracts ref id annotation from comment`() {
        val metadata = DbColumnAnnotationParser.parse("author id @RefId=AuthorId;")

        assertEquals("AuthorId", metadata.refId)
    }

    @Test
    fun `parser rejects blank ref id value`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@RefId=;")
        }

        assertEquals("blank @RefId value is not allowed.", error.message)
    }

    @Test
    fun `parser rejects valueless ref id annotation`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@RefId;")
        }

        assertEquals("missing value for @RefId annotation.", error.message)
    }

    @Test
    fun `parser rejects conflicting ref id annotations`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@RefId=AuthorId;@RefId=UserId;")
        }

        assertEquals("conflicting @RefId annotations on the same column comment.", error.message)
    }

    @Test
    fun `parser rejects duplicate ref id annotations`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@RefId=AuthorId;@RefId=AuthorId;")
        }

        assertEquals("conflicting @RefId annotations on the same column comment.", error.message)
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
    fun `parser extracts deleted and version markers from comment`() {
        val metadata = DbColumnAnnotationParser.parse(
            "audit field @Deleted;@Version;"
        )

        assertEquals(true, metadata.deleted)
        assertEquals(true, metadata.version)
    }

    @Test
    fun `parser supports database generated value strategies and alias`() {
        val identity = DbColumnAnnotationParser.parse("@GeneratedValue=IDENTITY;")
        val databaseIdentity = DbColumnAnnotationParser.parse("@GeneratedValue=database-identity;")

        assertEquals("identity", identity.generatedValueStrategy)
        assertEquals("identity", databaseIdentity.generatedValueStrategy)
    }

    @Test
    fun `parser rejects unsupported generated value strategy`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@GeneratedValue=SEQUENCE;")
        }

        assertEquals("unsupported @GeneratedValue strategy: SEQUENCE", error.message)
    }

    @Test
    fun `parser keeps deleted and version null when source is silent`() {
        val metadata = DbColumnAnnotationParser.parse("plain comment")

        assertEquals(null, metadata.version)
        assertEquals(null, metadata.deleted)
        assertEquals(null, metadata.managed)
        assertEquals(null, metadata.exposed)
        assertEquals(null, metadata.inherited)
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
    fun `parser supports managed marker`() {
        val managed = DbColumnAnnotationParser.parse("@Managed;")

        assertEquals(true, managed.managed)
        assertEquals(null, managed.exposed)
    }

    @Test
    fun `parser rejects valued managed marker`() {
        val managedError = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@Managed=true;")
        }

        assertEquals("invalid @Managed annotation: explicit values are not supported.", managedError.message)
    }

    @Test
    fun `parser supports inherited marker`() {
        val metadata = DbColumnAnnotationParser.parse("created at @Inherited;")

        assertEquals(true, metadata.inherited)
    }

    @Test
    fun `parser rejects valued inherited marker`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@Inherited=true;")
        }

        assertEquals("invalid @Inherited annotation: explicit values are not supported.", error.message)
    }

    @Test
    fun `parser rejects removed generated value strategies and marker`() {
        val markerError = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@GeneratedValue;")
        }
        val uuidError = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@GeneratedValue=uuid7;")
        }
        val snowflakeError = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@GeneratedValue=snowflake-long;")
        }

        assertEquals("invalid @GeneratedValue annotation: explicit database strategy is required.", markerError.message)
        assertEquals("unsupported @GeneratedValue strategy: uuid7", uuidError.message)
        assertEquals("unsupported @GeneratedValue strategy: snowflake-long", snowflakeError.message)
    }

    @Test
    fun `parser rejects removed exposed and jpa mutability annotations`() {
        val exposedError = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@Exposed;")
        }
        val insertableError = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@Insertable=false;")
        }
        val updatableError = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@Updatable=false;")
        }

        assertEquals(
            "unsupported column annotation @Exposed: remove broad managed defaults or stop marking this field managed.",
            exposedError.message,
        )
        assertEquals(
            "unsupported column annotation @Insertable: use template overrides for JPA-specific mutability.",
            insertableError.message,
        )
        assertEquals(
            "unsupported column annotation @Updatable: use template overrides for JPA-specific mutability.",
            updatableError.message,
        )
    }
}
