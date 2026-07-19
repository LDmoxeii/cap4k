package com.only4.cap4k.plugin.pipeline.source.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DbRelationAnnotationParserTest {

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
    fun `parses column ref aggregate annotation without jpa reference metadata`() {
        val metadata = DbRelationAnnotationParser().parseColumn("task id @RefAggregate=MediaProcessingTask;")

        assertEquals("MediaProcessingTask", metadata.refAggregate)
        assertEquals(null, metadata.referenceTable)
        assertEquals(null, metadata.explicitRelationType)
    }

    @Test
    fun `rejects blank ref aggregate value`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@RefAggregate=;")
        }

        assertEquals("blank @RefAggregate value is not allowed.", error.message)
    }

    @Test
    fun `rejects valueless ref aggregate annotation`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@RefAggregate;")
        }

        assertEquals("missing value for @RefAggregate annotation.", error.message)
    }

    @Test
    fun `rejects ref aggregate with reference annotations`() {
        val referenceError = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@RefAggregate=MediaProcessingTask;@Reference=media_processing_task;")
        }
        val refError = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@RefAggregate=MediaProcessingTask;@Ref=media_processing_task;")
        }

        assertEquals(
            "conflicting @RefAggregate and @Reference/@Ref annotations on the same column comment.",
            referenceError.message,
        )
        assertEquals(
            "conflicting @RefAggregate and @Reference/@Ref annotations on the same column comment.",
            refError.message,
        )
    }

    @Test
    fun `rejects ref aggregate with ref id annotation`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@RefAggregate=MediaProcessingTask;@RefId=MediaProcessingTaskId;")
        }

        assertEquals(
            "conflicting @RefAggregate and @RefId annotations on the same column comment.",
            error.message,
        )
    }

    @Test
    fun `rejects many to many in the first relation slice`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@Reference=tag;@Relation=ManyToMany;")
        }

        assertEquals("unsupported relation type in first slice: ManyToMany", error.message)
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
    fun `rejects valueless reference annotation`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@Reference;")
        }

        assertEquals("missing value for @Reference/@Ref annotation.", error.message)
    }

    @Test
    fun `rejects relation without reference`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@Relation=OneToOne;")
        }

        assertEquals("@Relation/@Rel requires @Reference/@Ref on the same column comment.", error.message)
    }

    @Test
    fun `rejects blank relation value`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@Relation=;")
        }

        assertEquals("blank @Relation/@Rel value is not allowed.", error.message)
    }

    @Test
    fun `rejects valueless relation annotation`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@Relation;")
        }

        assertEquals("missing value for @Relation/@Rel annotation.", error.message)
    }

    @Test
    fun `rejects blank count value`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@Count=;")
        }

        assertEquals("blank @Count/@C value is not allowed.", error.message)
    }

    @Test
    fun `rejects valueless count annotation`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@Count;")
        }

        assertEquals("missing value for @Count/@C annotation.", error.message)
    }

    @Test
    fun `rejects malformed lazy boolean`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@Lazy=TRUE;")
        }

        assertEquals("invalid @Lazy/@L boolean value: TRUE", error.message)
    }

    @Test
    fun `rejects lazy without reference`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@Lazy=true;")
        }

        assertEquals("@Lazy/@L requires @Reference/@Ref on the same column comment.", error.message)
    }

    @Test
    fun `rejects count without reference`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@Count=one;")
        }

        assertEquals("@Count/@C requires @Reference/@Ref on the same column comment.", error.message)
    }
}
