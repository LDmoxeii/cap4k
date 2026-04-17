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
    fun `derives entity and q entity fqcn without package prefix when package is blank`() {
        val references = AggregateDerivedTypeReferences.from(
            CanonicalModel(
                entities = listOf(
                    EntityModel(
                        name = "VideoPost",
                        packageName = "",
                        tableName = "video_post",
                        comment = "Video post entity",
                        fields = listOf(FieldModel("id", "Long")),
                        idField = FieldModel("id", "Long"),
                    )
                )
            )
        )

        assertEquals("VideoPost", references.entityFqn("VideoPost"))
        assertEquals("QVideoPost", references.qEntityFqn("VideoPost"))
    }

    @Test
    fun `returns null for ambiguous simple entity names while preserving entity-aware resolution`() {
        val primaryEntity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.primary_video_post",
            tableName = "primary_video_post",
            comment = "Primary video post entity",
            fields = listOf(FieldModel("id", "Long")),
            idField = FieldModel("id", "Long"),
        )
        val secondaryEntity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.secondary_video_post",
            tableName = "secondary_video_post",
            comment = "Secondary video post entity",
            fields = listOf(FieldModel("id", "Long")),
            idField = FieldModel("id", "Long"),
        )
        val references = AggregateDerivedTypeReferences.from(
            CanonicalModel(entities = listOf(primaryEntity, secondaryEntity))
        )

        assertNull(references.entityFqn("VideoPost"))
        assertNull(references.qEntityFqn("VideoPost"))
        assertEquals(
            "com.acme.demo.domain.aggregates.primary_video_post.VideoPost",
            references.entityFqn(primaryEntity),
        )
        assertEquals(
            "com.acme.demo.domain.aggregates.secondary_video_post.VideoPost",
            references.entityFqn(secondaryEntity),
        )
    }
}
