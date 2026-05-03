package com.only4.cap4k.plugin.pipeline.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PipelineModelsTest {

    @Test
    fun `artifact plan item defaults to checked in source ownership`() {
        val item = ArtifactPlanItem(
            generatorId = "aggregate",
            moduleRole = "domain",
            templateId = "aggregate/entity.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/Category.kt",
            conflictPolicy = ConflictPolicy.SKIP,
        )

        assertEquals(ArtifactOutputKind.CHECKED_IN_SOURCE, item.outputKind)
        assertEquals("", item.resolvedOutputRoot)
    }

    @Test
    fun `artifact plan item can carry generated source ownership`() {
        val item = ArtifactPlanItem(
            generatorId = "aggregate",
            moduleRole = "domain",
            templateId = "aggregate/entity.kt.peb",
            outputPath = "demo-domain/build/generated/cap4k/main/kotlin/com/acme/demo/Category.kt",
            conflictPolicy = ConflictPolicy.OVERWRITE,
            outputKind = ArtifactOutputKind.GENERATED_SOURCE,
            resolvedOutputRoot = "demo-domain/build/generated/cap4k/main/kotlin",
        )

        assertEquals(ArtifactOutputKind.GENERATED_SOURCE, item.outputKind)
        assertEquals("demo-domain/build/generated/cap4k/main/kotlin", item.resolvedOutputRoot)
    }

    @Test
    fun `rendered artifact can carry output ownership`() {
        val artifact = RenderedArtifact(
            outputPath = "demo-domain/build/generated/cap4k/main/kotlin/com/acme/demo/Category.kt",
            content = "class Category",
            conflictPolicy = ConflictPolicy.SKIP,
            outputKind = ArtifactOutputKind.GENERATED_SOURCE,
            resolvedOutputRoot = "demo-domain/build/generated/cap4k/main/kotlin",
        )

        assertEquals(ArtifactOutputKind.GENERATED_SOURCE, artifact.outputKind)
        assertEquals("demo-domain/build/generated/cap4k/main/kotlin", artifact.resolvedOutputRoot)
    }

    @Test
    fun `canonical model stores commands queries api payloads and clients separately`() {
        val model = CanonicalModel(
            commands = listOf(
                CommandModel(
                    packageName = "orders",
                    typeName = "CreateOrderCmd",
                    description = "create order",
                    aggregateRef = AggregateRef(
                        name = "Order",
                        packageName = "com.acme.demo.domain.aggregates.order",
                    ),
                    requestFields = listOf(FieldModel(name = "id", type = "Long")),
                    responseFields = emptyList(),
                    variant = CommandVariant.DEFAULT,
                )
            ),
            queries = listOf(
                QueryModel(
                    packageName = "orders",
                    typeName = "FindOrderPageQry",
                    description = "find order page",
                    aggregateRef = AggregateRef(
                        name = "Order",
                        packageName = "com.acme.demo.domain.aggregates.order",
                    ),
                    requestFields = emptyList(),
                    responseFields = emptyList(),
                    traits = setOf(RequestTrait.PAGE),
                )
            ),
            apiPayloads = listOf(
                ApiPayloadModel(
                    packageName = "orders",
                    typeName = "FindOrderPage",
                    description = "find order page payload",
                    traits = setOf(RequestTrait.PAGE),
                )
            ),
            clients = listOf(
                ClientModel(
                    packageName = "remote",
                    typeName = "SyncStockCli",
                    description = "sync stock",
                    aggregateRef = null,
                    requestFields = emptyList(),
                    responseFields = emptyList(),
                )
            ),
        )

        assertEquals(1, model.commands.size)
        assertEquals(setOf(RequestTrait.PAGE), model.queries.single().traits)
        assertEquals(setOf(RequestTrait.PAGE), model.apiPayloads.single().traits)
        assertEquals(1, model.clients.size)
    }

    @Test
    fun `ir analysis snapshot preserves input dirs nodes edges and design elements`() {
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
            designElements = listOf(
                DesignElementSnapshot(
                    tag = "cmd",
                    packageName = "com.acme.demo.app.command",
                    name = "SubmitOrderCmd",
                    description = "submit order",
                    aggregates = listOf("Order"),
                    entity = "Order",
                    persist = true,
                    requestFields = listOf(
                        DesignFieldSnapshot(name = "id", type = "Long")
                    ),
                    responseFields = listOf(
                        DesignFieldSnapshot(name = "success", type = "Boolean", nullable = false)
                    ),
                )
            ),
        )

        assertEquals("ir-analysis", snapshot.id)
        assertEquals(listOf("app/build/cap4k-code-analysis"), snapshot.inputDirs)
        assertEquals("OrderController::submit", snapshot.nodes.single().id)
        assertEquals("ControllerMethodToCommand", snapshot.edges.single().type)
        assertEquals("cmd", snapshot.designElements.single().tag)
        assertEquals("SubmitOrderCmd", snapshot.designElements.single().name)
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
            commands = listOf(
                CommandModel(
                    packageName = "order.submit",
                    typeName = "SubmitOrderCmd",
                    description = "submit order",
                    aggregateRef = null,
                    variant = CommandVariant.DEFAULT,
                )
            ),
            analysisGraph = graph,
        )

        assertEquals("SubmitOrderCmd", model.commands.single().typeName)
        assertEquals("OrderController::submit", model.analysisGraph!!.nodes.single().id)
        assertEquals("ControllerMethodToCommand", model.analysisGraph!!.edges.single().type)
    }

    @Test
    fun `canonical model defaults analysis graph to null`() {
        val model = CanonicalModel()
        assertNull(model.analysisGraph)
    }

    @Test
    fun `canonical model keeps optional drawing board alongside existing slices`() {
        val element = DrawingBoardElementModel(
            tag = "cmd",
            packageName = "com.acme.demo.app.command",
            name = "SubmitOrderCmd",
            description = "submit order",
            aggregates = listOf("Order"),
            entity = "Order",
            persist = true,
            requestFields = listOf(
                DrawingBoardFieldModel(name = "id", type = "Long")
            ),
            responseFields = listOf(
                DrawingBoardFieldModel(name = "success", type = "Boolean", nullable = false)
            ),
        )
        val board = DrawingBoardModel(elements = listOf(element))

        val model = CanonicalModel(
            commands = listOf(
                CommandModel(
                    packageName = "order.submit",
                    typeName = "SubmitOrderCmd",
                    description = "submit order",
                    aggregateRef = null,
                    variant = CommandVariant.DEFAULT,
                )
            ),
            drawingBoard = board,
        )

        assertEquals("SubmitOrderCmd", model.commands.single().typeName)
        assertEquals("cmd", model.drawingBoard!!.elements.single().tag)
        assertEquals("SubmitOrderCmd", model.drawingBoard!!.elementsByTag["cmd"]!!.single().name)
    }

    @Test
    fun `drawing board model rejects mismatched elements by tag`() {
        val element = DrawingBoardElementModel(
            tag = "cmd",
            packageName = "com.acme.demo.app.command",
            name = "SubmitOrderCmd",
            description = "submit order",
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            DrawingBoardModel(
                elements = listOf(element),
                elementsByTag = emptyMap(),
            )
        }

        assertEquals("elementsByTag must match elements grouped by tag", exception.message)
    }

    @Test
    fun `canonical model defaults drawing board to null`() {
        val model = CanonicalModel()
        assertNull(model.drawingBoard)
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
    fun `canonical model keeps dedicated validators slice alongside commands`() {
        val command = CommandModel(
            packageName = "order.submit",
            typeName = "SubmitOrderCmd",
            description = "submit order",
            aggregateRef = null,
            variant = CommandVariant.DEFAULT,
        )
        val validator = ValidatorModel(
            packageName = "auth.validator",
            typeName = "IssueToken",
            description = "issue token validator",
            message = "校验未通过",
            targets = listOf("FIELD", "VALUE_PARAMETER"),
            valueType = "Long",
        )

        val model = CanonicalModel(
            commands = listOf(command),
            validators = listOf(validator),
        )

        assertEquals(listOf(command), model.commands)
        assertEquals(listOf(validator), model.validators)
    }

    @Test
    fun `canonical model keeps dedicated api payload slice alongside commands validators and aggregate slices`() {
        val payload = ApiPayloadModel(
            packageName = "auth.payload",
            typeName = "BatchSaveAccountList",
            description = "batch save account payload",
            requestFields = listOf(FieldModel(name = "accountIds", type = "List<Long>")),
            responseFields = listOf(FieldModel(name = "saved", type = "Int")),
        )
        val command = CommandModel(
            packageName = "order.submit",
            typeName = "SubmitOrderCmd",
            description = "submit order",
            aggregateRef = null,
            variant = CommandVariant.DEFAULT,
        )
        val validator = ValidatorModel(
            packageName = "auth.validator",
            typeName = "IssueToken",
            description = "issue token validator",
            message = "校验未通过",
            targets = listOf("FIELD", "VALUE_PARAMETER"),
            valueType = "Long",
        )
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
            commands = listOf(command),
            validators = listOf(validator),
            apiPayloads = listOf(payload),
            schemas = listOf(schema),
            entities = listOf(entity),
            repositories = listOf(repository),
        )

        assertEquals(listOf(command), model.commands)
        assertEquals(listOf(validator), model.validators)
        assertEquals(listOf(payload), model.apiPayloads)
        assertEquals(listOf(schema), model.schemas)
        assertEquals(listOf(entity), model.entities)
        assertEquals(listOf(repository), model.repositories)
    }

    @Test
    fun `plan report and pipeline result carry aggregate special field defaults and resolved policies`() {
        val defaults = AggregateSpecialFieldDefaultsConfig(
            idDefaultStrategy = "snowflake-long",
            deletedDefaultColumn = "deleted",
            versionDefaultColumn = "version",
        )
        val resolvedPolicy = AggregateSpecialFieldResolvedPolicy(
            entityName = "VideoPost",
            entityPackageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            id = ResolvedIdPolicy(
                fieldName = "id",
                columnName = "id",
                strategy = "uuid7",
                kind = AggregateIdPolicyKind.APPLICATION_SIDE,
                source = SpecialFieldSource.DSL_DEFAULT,
            ),
            deleted = ResolvedMarkerPolicy(
                enabled = true,
                fieldName = "deleted",
                columnName = "deleted",
                source = SpecialFieldSource.DB_EXPLICIT,
            ),
            version = ResolvedMarkerPolicy(
                enabled = true,
                fieldName = "version",
                columnName = "version",
                source = SpecialFieldSource.DB_EXPLICIT,
            ),
        )

        val report = PlanReport(
            items = emptyList(),
            aggregateSpecialFieldDefaults = defaults,
            aggregateSpecialFieldResolvedPolicies = listOf(resolvedPolicy),
        )
        val result = PipelineResult(
            aggregateSpecialFieldResolvedPolicies = listOf(resolvedPolicy),
        )
        val model = CanonicalModel(
            aggregateSpecialFieldResolvedPolicies = listOf(resolvedPolicy),
        )

        assertEquals(defaults, report.aggregateSpecialFieldDefaults)
        assertEquals(listOf(resolvedPolicy), report.aggregateSpecialFieldResolvedPolicies)
        assertEquals(listOf(resolvedPolicy), result.aggregateSpecialFieldResolvedPolicies)
        assertEquals(listOf(resolvedPolicy), model.aggregateSpecialFieldResolvedPolicies)
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
                    uniqueConstraints = listOf(
                        UniqueConstraintModel(
                            physicalName = "uk_v_id",
                            columns = listOf("id"),
                        )
                    ),
                )
            )
        )

        assertEquals("video_post", snapshot.tables.single().tableName)
        assertEquals(listOf("id"), snapshot.tables.single().primaryKey)
        assertEquals("uk_v_id", snapshot.tables.single().uniqueConstraints.single().physicalName)
        assertEquals(listOf("id"), snapshot.tables.single().uniqueConstraints.single().columns)
        assertEquals("Long", snapshot.tables.single().columns.single().kotlinType)
    }
}
