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
    fun `parses column reference relation and lazy annotations`() {
        val metadata = DbRelationAnnotationParser().parseColumn("@Reference=user_profile;@Relation=OneToOne;@Lazy=true;")

        assertEquals("user_profile", metadata.referenceTable)
        assertEquals("ONE_TO_ONE", metadata.explicitRelationType)
        assertEquals(true, metadata.lazy)
    }

    @Test
    fun `rejects many to many in the first relation slice`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbRelationAnnotationParser().parseColumn("@Reference=tag;@Relation=ManyToMany;")
        }

        assertEquals("unsupported relation type in first slice: ManyToMany", error.message)
    }
}
