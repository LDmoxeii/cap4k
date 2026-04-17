package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AggregateDerivedTypeReferencesTest {

    @Test
    fun `derives entity and q entity fqcn from canonical entity model`() {
        val references = AggregateDerivedTypeReferences.from(
            CanonicalModel(
                entities = listOf(
                    EntityModel(
                        name = "VideoPost",
                        packageName = "com.acme.demo.domain.aggregates.video_post",
                        tableName = "video_post",
                        comment = "Video post entity",
                        fields = listOf(FieldModel("id", "Long")),
                        idField = FieldModel("id", "Long"),
                    )
                )
            )
        )

        assertEquals(
            "com.acme.demo.domain.aggregates.video_post.VideoPost",
            references.entityFqn("VideoPost"),
        )
        assertEquals(
            "com.acme.demo.domain.aggregates.video_post.QVideoPost",
            references.qEntityFqn("VideoPost"),
        )
    }

    @Test
    fun `returns null when entity name is unknown`() {
        val references = AggregateDerivedTypeReferences.from(CanonicalModel())

        assertNull(references.entityFqn("MissingEntity"))
        assertNull(references.qEntityFqn("MissingEntity"))
    }

    @Test
    fun `repeated lookups do not require mutation`() {
        val references = AggregateDerivedTypeReferences.from(
            CanonicalModel(
                entities = listOf(
                    EntityModel(
                        name = "VideoPost",
                        packageName = "com.acme.demo.domain.aggregates.video_post",
                        tableName = "video_post",
                        comment = "Video post entity",
                        fields = listOf(FieldModel("id", "Long")),
                        idField = FieldModel("id", "Long"),
                    )
                )
            )
        )

        assertEquals(references.entityFqn("VideoPost"), references.entityFqn("VideoPost"))
        assertEquals(references.qEntityFqn("VideoPost"), references.qEntityFqn("VideoPost"))
    }
}
