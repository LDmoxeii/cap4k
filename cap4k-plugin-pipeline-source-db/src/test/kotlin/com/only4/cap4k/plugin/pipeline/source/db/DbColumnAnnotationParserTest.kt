package com.only4.cap4k.plugin.pipeline.source.db

import com.only4.cap4k.plugin.pipeline.api.DbIdStrategy
import com.only4.cap4k.plugin.pipeline.api.DbManagedRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class DbColumnAnnotationParserTest {

    @Test
    fun `column parser accepts parent ref managed role`() {
        val metadata = DbColumnAnnotationParser.parse("@ParentRef;@Managed=scope;")

        assertTrue(metadata.parentRef)
        assertEquals(DbManagedRole.SCOPE, metadata.managedRole)
        assertNull(metadata.idStrategy)
    }

    @Test
    fun `column parser accepts db identity id strategy`() {
        val metadata = DbColumnAnnotationParser.parse("@IdStrategy=db_identity;")

        assertEquals(DbIdStrategy.DB_IDENTITY, metadata.idStrategy)
    }

    @Test
    fun `column parser accepts type ref aggregate managed and inherited`() {
        val metadata = DbColumnAnnotationParser.parse(
            "status @Type=VideoPostVisibility;@RefAggregate=VideoPost;@Managed=system;@Inherited;"
        )

        assertEquals("VideoPostVisibility", metadata.typeBinding)
        assertTrue(metadata.enumItems.isEmpty())
        assertEquals("VideoPost", metadata.refAggregate)
        assertNull(metadata.refId)
        assertEquals(DbManagedRole.SYSTEM, metadata.managedRole)
        assertEquals(true, metadata.inherited)
        assertEquals("status", metadata.cleanedComment)
    }

    @Test
    fun `column parser accepts ref id`() {
        val metadata = DbColumnAnnotationParser.parse("@RefId=VideoPostId;")

        assertNull(metadata.refAggregate)
        assertEquals("VideoPostId", metadata.refId)
    }

    @Test
    fun `column parser rejects removed relation annotations through one generic path`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@Reference=user_profile;@Relation=ManyToOne;")
        }

        assertEquals(
            "unsupported column annotation @Reference. Supported column annotations: @ParentRef, @Type, @RefAggregate, @RefId, @IdStrategy=db_identity, @Managed=system|scope|deleted|version, @Inherited.",
            error.message,
        )
    }

    @TestFactory
    fun `rejects old column annotations through generic path`() = listOf(
        "@T=Status;",
        "@TYPE=Status;",
        "@E=0:A:a;",
        "@ENUM=0:A:a;",
        "@Deleted;",
        "@Version;",
        "@GeneratedValue=identity;",
        "@Reference=video_post;",
        "@Ref=video_post;",
        "@Relation=ManyToOne;",
        "@Rel=*:1;",
        "@Lazy=true;",
        "@L=true;",
        "@Count=one;",
        "@C=one;",
        "@One;",
        "@Exposed;",
        "@Insertable=false;",
        "@Updatable=false;",
    ).map { comment ->
        DynamicTest.dynamicTest(comment) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                DbColumnAnnotationParser.parse(comment)
            }

            assertTrue(error.message!!.startsWith("unsupported column annotation @"))
        }
    }

    @Test
    fun `column parser rejects unsupported column annotation generically`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("status @CustomMarker;")
        }

        assertEquals(
            "unsupported column annotation @CustomMarker. Supported column annotations: @ParentRef, @Type, @RefAggregate, @RefId, @IdStrategy=db_identity, @Managed=system|scope|deleted|version, @Inherited.",
            error.message,
        )
    }

    @Test
    fun `column parser rejects multiple managed annotations`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@Managed=system;@Managed=scope;")
        }

        assertEquals("multiple @Managed annotations are not allowed.", error.message)
    }

    @Test
    fun `column parser rejects valueless managed annotation`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@Managed;")
        }

        assertEquals("invalid @Managed annotation: value is required.", error.message)
    }

    @Test
    fun `column parser rejects unsupported id strategy value`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@IdStrategy=sequence;")
        }

        assertEquals("unsupported @IdStrategy value: sequence", error.message)
    }

    @Test
    fun `column parser rejects parent ref combinations`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@ParentRef;@RefAggregate=VideoPost;")
        }

        assertEquals("@ParentRef cannot be combined with @RefAggregate, @RefId, or @IdStrategy.", error.message)
    }

    @Test
    fun `column parser rejects parent ref with ref id`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@ParentRef;@RefId=VideoPostId;")
        }

        assertEquals("@ParentRef cannot be combined with @RefAggregate, @RefId, or @IdStrategy.", error.message)
    }

    @Test
    fun `column parser rejects parent ref with id strategy`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@ParentRef;@IdStrategy=db_identity;")
        }

        assertEquals("@ParentRef cannot be combined with @RefAggregate, @RefId, or @IdStrategy.", error.message)
    }

    @Test
    fun `column parser rejects ref aggregate with ref id`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@RefAggregate=VideoPost;@RefId=VideoPostId;")
        }

        assertEquals("conflicting @RefAggregate and @RefId annotations on the same column comment.", error.message)
    }

    @Test
    fun `column parser rejects inherited without managed role`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@Inherited;")
        }

        assertEquals(
            "@Inherited is valid only with @Managed=system, @Managed=scope, @Managed=deleted, or @Managed=version.",
            error.message,
        )
    }

    @Test
    fun `column parser keeps nullable metadata empty when source is silent`() {
        val metadata = DbColumnAnnotationParser.parse("plain comment")

        assertNull(metadata.typeBinding)
        assertTrue(metadata.enumItems.isEmpty())
        assertNull(metadata.managedRole)
        assertNull(metadata.idStrategy)
        assertEquals("plain comment", metadata.cleanedComment)
    }
}
