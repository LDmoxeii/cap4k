package com.only4.cap4k.plugin.pipeline.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PipelineModelsTest {
    @Test
    fun `design block stores artifact selections`() {
        val block = DesignBlockModel(
            tag = "query",
            packageName = "order.read",
            name = "FindOrderPage",
            description = "Find order page",
            aggregates = listOf("Order"),
            artifacts = listOf(
                ArtifactSelectionModel(family = "query", variant = "page"),
                ArtifactSelectionModel(family = "query-handler"),
            ),
            fields = listOf(FieldModel(name = "keyword", type = "String", nullable = true)),
            resultFields = listOf(FieldModel(name = "orderNo", type = "String")),
        )

        assertEquals("query", block.tag)
        assertEquals(listOf("Order"), block.aggregates)
        assertEquals("page", block.artifacts.first().variant)
        assertEquals("query-handler", block.artifacts.last().family)
        assertEquals("keyword", block.fields.single().name)
        assertEquals("orderNo", block.resultFields.single().name)
    }

    @Test
    fun `design block preserves explicit empty artifacts for drawing board output`() {
        val omittedArtifacts = DesignBlockModel(
            tag = "query",
            packageName = "order.read",
            name = "FindOrder",
            artifacts = listOf(
                ArtifactSelectionModel(family = "query"),
                ArtifactSelectionModel(family = "query-handler"),
            ),
            artifactsDeclared = false,
        )
        val explicitEmptyArtifacts = DesignBlockModel(
            tag = "query",
            packageName = "order.read",
            name = "FindOrder",
            artifacts = emptyList(),
            artifactsDeclared = true,
        )

        assertFalse(omittedArtifacts.includeDesignJsonArtifacts)
        assertTrue(explicitEmptyArtifacts.includeDesignJsonArtifacts)
        assertEquals(emptyList<ArtifactSelectionModel>(), explicitEmptyArtifacts.designJsonArtifacts)
    }

    @Test
    fun `design spec entry exposes public v2 fields and nullable artifact selections`() {
        val requestFields = listOf(FieldModel(name = "keyword", type = "String", nullable = true))
        val responseFields = listOf(FieldModel(name = "orderNo", type = "String"))
        val entryWithOmittedArtifacts = DesignSpecEntry(
            tag = "query",
            packageName = "order.read",
            name = "FindOrderPage",
            description = "find order page",
            aggregates = listOf("Order"),
            fields = requestFields,
            resultFields = responseFields,
        )
        val entryWithExplicitEmptyArtifacts = entryWithOmittedArtifacts.copy(artifacts = emptyList())

        assertNull(entryWithOmittedArtifacts.artifacts)
        assertEquals(requestFields, entryWithOmittedArtifacts.fields)
        assertEquals(responseFields, entryWithOmittedArtifacts.resultFields)
        assertEquals(emptyList<ArtifactSelectionModel>(), entryWithExplicitEmptyArtifacts.artifacts)
    }

    @Test
    fun `canonical model defaults design blocks to empty list`() {
        val model = CanonicalModel()

        assertEquals(emptyList<DesignBlockModel>(), model.designBlocks)
    }
}
