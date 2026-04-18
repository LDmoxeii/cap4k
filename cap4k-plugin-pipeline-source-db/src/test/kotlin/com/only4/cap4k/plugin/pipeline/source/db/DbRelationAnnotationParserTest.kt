package com.only4.cap4k.plugin.pipeline.source.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DbRelationAnnotationParserTest {

    @Test
    fun `parses table parent and value object annotations`() {
        val metadata = DbRelationAnnotationParser().parseTable("@Parent=video_post;@VO;")

        assertEquals("video_post", metadata.parentTable)
        assertEquals(false, metadata.aggregateRoot)
        assertEquals(true, metadata.valueObject)
    }

    @Test
    fun `parses short table aliases and long value object annotation`() {
        val metadata = DbRelationAnnotationParser().parseTable("@P=video_post;@Root=false;@ValueObject;")

        assertEquals("video_post", metadata.parentTable)
        assertEquals(false, metadata.aggregateRoot)
        assertEquals(true, metadata.valueObject)
    }

    @Test
    fun `parses column reference relation and lazy annotations`() {
        val metadata = DbRelationAnnotationParser().parseColumn(
            "@Reference=user_profile;@Relation=OneToOne;@Lazy=true;@Count=single;"
        )

        assertEquals("user_profile", metadata.referenceTable)
        assertEquals("ONE_TO_ONE", metadata.explicitRelationType)
        assertEquals(true, metadata.lazy)
        assertEquals("single", metadata.countHint)
    }

    @Test
    fun `parses short column aliases and count hint`() {
        val metadata = DbRelationAnnotationParser().parseColumn("@Ref=account;@Rel=*:1;@L=false;@C=many;")

        assertEquals("account", metadata.referenceTable)
        assertEquals("MANY_TO_ONE", metadata.explicitRelationType)
        assertEquals(false, metadata.lazy)
        assertEquals("many", metadata.countHint)
    }

    @Test
    fun `rejects many to many in the first relation slice`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@Reference=tag;@Relation=ManyToMany;")
        }

        assertEquals("unsupported relation type in first slice: ManyToMany", error.message)
    }

    @Test
    fun `rejects conflicting aggregate root aliases`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseTable("@AggregateRoot=true;@R=false;")
        }

        assertEquals("conflicting @AggregateRoot/@Root/@R annotations on the same table comment.", error.message)
    }

    @Test
    fun `rejects blank parent value`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseTable("@Parent=;")
        }

        assertEquals("blank @Parent/@P value is not allowed.", error.message)
    }

    @Test
    fun `rejects valued value object annotation`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseTable("@VO=false;")
        }

        assertEquals("invalid @ValueObject/@VO annotation: explicit values are not supported.", error.message)
    }

    @Test
    fun `rejects parent combined with explicit aggregate root true`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseTable("@Parent=video_post;@AggregateRoot=true;")
        }

        assertEquals("conflicting table relation annotations: @Parent/@P cannot be combined with @AggregateRoot=true.", error.message)
    }

    @Test
    fun `rejects conflicting reference aliases`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@Reference=user;@Ref=account;")
        }

        assertEquals("conflicting @Reference/@Ref annotations on the same column comment.", error.message)
    }

    @Test
    fun `rejects blank reference value`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@Reference=;")
        }

        assertEquals("blank @Reference/@Ref value is not allowed.", error.message)
    }

    @Test
    fun `rejects blank relation value`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@Relation=;")
        }

        assertEquals("blank @Relation/@Rel value is not allowed.", error.message)
    }

    @Test
    fun `rejects blank count value`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@Count=;")
        }

        assertEquals("blank @Count/@C value is not allowed.", error.message)
    }

    @Test
    fun `rejects malformed aggregate root boolean`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseTable("@AggregateRoot=maybe;")
        }

        assertEquals("invalid @AggregateRoot/@Root/@R boolean value: maybe", error.message)
    }

    @Test
    fun `rejects malformed lazy boolean`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@Lazy=TRUE;")
        }

        assertEquals("invalid @Lazy/@L boolean value: TRUE", error.message)
    }
}
