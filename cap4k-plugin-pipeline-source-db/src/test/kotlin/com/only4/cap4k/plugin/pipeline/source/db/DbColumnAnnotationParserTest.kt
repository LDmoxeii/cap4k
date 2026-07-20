package com.only4.cap4k.plugin.pipeline.source.db

import com.only4.cap4k.plugin.pipeline.api.DbIdStrategy
import com.only4.cap4k.plugin.pipeline.api.DbManagedRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertFailsWith

class DbColumnAnnotationParserTest {

    @Test
    fun `column parser accepts parent ref managed role and id strategy`() {
        val metadata = DbColumnAnnotationParser.parse("@ParentRef;@Managed=scope;@IdStrategy=db_identity;")

        assertTrue(metadata.parentRef)
        assertEquals(DbManagedRole.SCOPE, metadata.managedRole)
        assertEquals(DbIdStrategy.DB_IDENTITY, metadata.idStrategy)
    }

    @Test
    fun `column parser accepts type ref aggregate ref id managed and inherited`() {
        val metadata = DbColumnAnnotationParser.parse(
            "status @Type=VideoPostVisibility;@RefAggregate=VideoPost;@RefId=VideoPostId;@Managed=system;@Inherited;"
        )

        assertEquals("VideoPostVisibility", metadata.typeBinding)
        assertTrue(metadata.enumItems.isEmpty())
        assertEquals("VideoPost", metadata.refAggregate)
        assertEquals("VideoPostId", metadata.refId)
        assertEquals(DbManagedRole.SYSTEM, metadata.managedRole)
        assertEquals(true, metadata.inherited)
        assertEquals("status", metadata.cleanedComment)
    }

    @Test
    fun `column parser rejects removed relation annotations through one generic path`() {
        val error = assertFailsWith<IllegalArgumentException> {
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
            val error = assertFailsWith<IllegalArgumentException> {
                DbColumnAnnotationParser.parse(comment)
            }

            assertTrue(error.message!!.startsWith("unsupported column annotation @"))
        }
    }

    @Test
    fun `column parser rejects unsupported column annotation generically`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DbColumnAnnotationParser.parse("status @CustomMarker;")
        }

        assertEquals(
            "unsupported column annotation @CustomMarker. Supported column annotations: @ParentRef, @Type, @RefAggregate, @RefId, @IdStrategy=db_identity, @Managed=system|scope|deleted|version, @Inherited.",
            error.message,
        )
    }

    @Test
    fun `column parser rejects multiple managed annotations`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DbColumnAnnotationParser.parse("@Managed=system;@Managed=scope;")
        }

        assertEquals("multiple @Managed annotations are not allowed.", error.message)
    }

    @Test
    fun `column parser rejects valueless managed annotation`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DbColumnAnnotationParser.parse("@Managed;")
        }

        assertEquals("invalid @Managed annotation: value is required.", error.message)
    }

    @Test
    fun `column parser rejects unsupported id strategy value`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DbColumnAnnotationParser.parse("@IdStrategy=sequence;")
        }

        assertEquals("unsupported @IdStrategy value: sequence", error.message)
    }

    @Test
    fun `column parser rejects parent ref combinations`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DbColumnAnnotationParser.parse("@ParentRef;@RefAggregate=VideoPost;")
        }

        assertEquals("@ParentRef cannot be combined with @RefAggregate, @RefId, or @IdStrategy.", error.message)
    }

    @Test
    fun `column parser rejects inherited without managed role`() {
        val error = assertFailsWith<IllegalArgumentException> {
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
