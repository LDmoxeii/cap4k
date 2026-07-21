package com.only4.cap4k.plugin.pipeline.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PipelineModelsTest {
    @Test
    fun `db column snapshot carries parent ref managed role and id strategy`() {
        val column = DbColumnSnapshot(
            name = "parent_id",
            dbType = "BIGINT",
            kotlinType = "Long",
            nullable = false,
            parentRef = true,
            managedRole = DbManagedRole.SCOPE,
            idStrategy = DbIdStrategy.DB_IDENTITY,
        )

        assertTrue(column.parentRef)
        assertEquals(DbManagedRole.SCOPE, column.managedRole)
        assertEquals(DbIdStrategy.DB_IDENTITY, column.idStrategy)
    }

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

    @Test
    fun `aggregate persistence provider control carries semantic soft delete policy`() {
        val softDelete = AggregateSoftDeletePolicy(
            fieldName = "deleted",
            columnName = "deleted",
            activeValue = "0",
            tombstoneStrategy = SoftDeleteTombstoneStrategy.SELF_ID,
            activePredicateSql = "\"deleted\" = 0",
            deleteAssignmentSql = "\"deleted\" = \"id\"",
        )
        val control = AggregatePersistenceProviderControl(
            entityName = "VideoPost",
            entityPackageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            softDelete = softDelete,
            idFieldName = "id",
            versionFieldName = "version",
        )

        assertEquals(softDelete, control.softDelete)
        assertEquals(SoftDeleteTombstoneStrategy.SELF_ID, control.softDelete?.tombstoneStrategy)
        assertEquals("\"deleted\" = \"id\"", control.softDelete?.deleteAssignmentSql)
    }
}
