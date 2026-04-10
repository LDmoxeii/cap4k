package com.only4.cap4k.plugin.pipeline.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PipelineModelsTest {

    @Test
    fun `ir analysis snapshot preserves input dirs nodes and edges`() {
        val snapshot = IrAnalysisSnapshot(
            inputDirs = listOf("app/build/cap4k-code-analysis"),
            nodes = listOf(
                IrNodeSnapshot(
                    id = "OrderController::submit",
                    name = "OrderController::submit",
                    fullName = "com.acme.demo.adapter.web.OrderController::submit",
                    type = "controllermethod",
                )
            ),
            edges = listOf(
                IrEdgeSnapshot(
                    fromId = "OrderController::submit",
                    toId = "SubmitOrderCmd",
                    type = "ControllerMethodToCommand",
                    label = null,
                )
            ),
        )

        assertEquals("ir-analysis", snapshot.id)
        assertEquals("OrderController::submit", snapshot.nodes.single().id)
        assertEquals("ControllerMethodToCommand", snapshot.edges.single().type)
    }

    @Test
    fun `canonical model keeps optional analysis graph alongside existing slices`() {
        val graph = AnalysisGraphModel(
            inputDirs = listOf("app/build/cap4k-code-analysis"),
            nodes = listOf(
                AnalysisNodeModel(
                    id = "OrderController::submit",
                    name = "OrderController::submit",
                    fullName = "com.acme.demo.adapter.web.OrderController::submit",
                    type = "controllermethod",
                )
            ),
            edges = listOf(
                AnalysisEdgeModel(
                    fromId = "OrderController::submit",
                    toId = "SubmitOrderCmd",
                    type = "ControllerMethodToCommand",
                    label = null,
                )
            ),
        )

        val model = CanonicalModel(
            requests = listOf(
                RequestModel(
                    kind = RequestKind.COMMAND,
                    packageName = "order.submit",
                    typeName = "SubmitOrderCmd",
                    description = "submit order",
                )
            ),
            analysisGraph = graph,
        )

        assertEquals("SubmitOrderCmd", model.requests.single().typeName)
        assertEquals("OrderController::submit", model.analysisGraph!!.nodes.single().id)
        assertEquals("ControllerMethodToCommand", model.analysisGraph!!.edges.single().type)
    }

    @Test
    fun `canonical model defaults analysis graph to null`() {
        val model = CanonicalModel()
        assertNull(model.analysisGraph)
    }

    @Test
    fun `canonical model keeps aggregate slices`() {
        val schema = SchemaModel(
            name = "SVideoPost",
            packageName = "com.acme.demo.domain._share.meta.video_post",
            entityName = "VideoPost",
            comment = "Video post schema",
            fields = listOf(FieldModel(name = "id", type = "Long")),
        )
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "Video post",
            fields = listOf(FieldModel(name = "id", type = "Long")),
            idField = FieldModel(name = "id", type = "Long"),
        )
        val repository = RepositoryModel(
            name = "VideoPostRepository",
            packageName = "com.acme.demo.adapter.domain.repositories",
            entityName = "VideoPost",
            idType = "Long",
        )

        val model = CanonicalModel(
            schemas = listOf(schema),
            entities = listOf(entity),
            repositories = listOf(repository),
        )

        assertEquals(listOf(schema), model.schemas)
        assertEquals(listOf(entity), model.entities)
        assertEquals(listOf(repository), model.repositories)
    }

    @Test
    fun `db schema snapshot preserves normalized table metadata`() {
        val snapshot = DbSchemaSnapshot(
            tables = listOf(
                DbTableSnapshot(
                    tableName = "video_post",
                    comment = "Video posts",
                    columns = listOf(
                        DbColumnSnapshot(
                            name = "id",
                            dbType = "BIGINT",
                            kotlinType = "Long",
                            nullable = false,
                            defaultValue = null,
                            comment = "primary key",
                            isPrimaryKey = true,
                        )
                    ),
                    primaryKey = listOf("id"),
                    uniqueConstraints = listOf(listOf("id")),
                )
            )
        )

        assertEquals("video_post", snapshot.tables.single().tableName)
        assertEquals(listOf("id"), snapshot.tables.single().primaryKey)
        assertEquals("Long", snapshot.tables.single().columns.single().kotlinType)
    }
}
