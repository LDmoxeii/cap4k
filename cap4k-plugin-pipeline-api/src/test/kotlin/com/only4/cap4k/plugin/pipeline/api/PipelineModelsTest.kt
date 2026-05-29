package com.only4.cap4k.plugin.pipeline.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `canonical model defaults design blocks to empty list`() {
        val model = CanonicalModel()

        assertEquals(emptyList<DesignBlockModel>(), model.designBlocks)
    }

    @Test
    fun `artifact addon provider can create plan items from canonical context`() {
        val provider = object : ArtifactAddonProvider {
            override val id: String = "sample-addon"

            override fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem> =
                listOf(
                    ArtifactPlanItem(
                        generatorId = id,
                        moduleRole = "adapter",
                        templateId = "addons/sample-addon/sample.kt.peb",
                        outputPath = "demo-adapter/src/main/kotlin/com/acme/Sample.kt",
                        context = mapOf(
                            "basePackage" to context.config.basePackage,
                            "enumTypeName" to context.model.sharedEnums.single().typeName,
                            "featureName" to context.options["featureName"],
                        ),
                        conflictPolicy = ConflictPolicy.SKIP,
                    )
                )
        }

        val config = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = mapOf("adapter" to "demo-adapter"),
            sources = emptyMap(),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "ddd-default",
                overrideDirs = emptyList(),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )

        val model = CanonicalModel(
            sharedEnums = listOf(
                SharedEnumDefinition(
                    typeName = "SampleStatus",
                    packageName = "com.acme.demo.shared.enums",
                    items = emptyList(),
                )
            )
        )

        val items = provider.plan(
            ArtifactAddonContext(
                config = config,
                model = model,
                options = mapOf("featureName" to "sample-feature"),
            )
        )
        val item = items.single()

        assertEquals("sample-addon", provider.id)
        assertEquals("sample-addon", item.generatorId)
        assertEquals("addons/sample-addon/sample.kt.peb", item.templateId)
        assertEquals("com.acme.demo", item.context["basePackage"])
        assertEquals("SampleStatus", item.context["enumTypeName"])
        assertEquals("sample-feature", item.context["featureName"])
    }

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
    fun `canonical model carries value objects domain services and sagas`() {
        val project = ProjectModel(group = "com.acme", name = "demo")
        val aggregate = AggregateModel(
            name = "Content",
            packageName = "content.domain",
            description = "content aggregate",
        )
        val command = CommandModel(
            packageName = "content.commands",
            typeName = "PublishContentCmd",
            description = "publish content",
            aggregateRef = AggregateRef(name = "Content", packageName = "content.domain"),
            requestFields = listOf(FieldModel(name = "contentId", type = "ContentId")),
            variant = CommandVariant.DEFAULT,
        )
        val query = QueryModel(
            packageName = "content.queries",
            typeName = "FindContentQry",
            description = "find content",
            aggregateRef = AggregateRef(name = "Content", packageName = "content.domain"),
            responseFields = listOf(FieldModel(name = "title", type = "String")),
        )
        val client = ClientModel(
            packageName = "content.clients",
            typeName = "NotifyFollowersCli",
            description = "notify followers",
            requestFields = listOf(FieldModel(name = "contentId", type = "ContentId")),
        )
        val apiPayload = ApiPayloadModel(
            packageName = "content.payload",
            typeName = "PublishContentPayload",
            description = "publish content payload",
            requestFields = listOf(FieldModel(name = "contentId", type = "ContentId")),
        )
        val domainEvent = DomainEventModel(
            packageName = "content.events",
            typeName = "ContentPublished",
            description = "content published",
            aggregateName = "Content",
            aggregatePackageName = "content.domain",
            persist = true,
            fields = listOf(FieldModel(name = "contentId", type = "ContentId")),
        )
        val integrationEvent = IntegrationEventModel(
            packageName = "content.integration",
            typeName = "ContentPublishedIntegrationEvent",
            description = "content published integration event",
            role = IntegrationEventRole.OUTBOUND,
            eventName = "content.published",
            fields = listOf(FieldModel(name = "contentId", type = "ContentId")),
        )
        val strongId = StrongIdModel(
            typeName = "ContentId",
            packageName = "content.ids",
            kind = StrongIdKind.AGGREGATE_ROOT,
            ownerAggregateName = "Content",
            ownerAggregatePackageName = "content.domain",
        )
        val sharedEnum = SharedEnumDefinition(
            typeName = "ContentStatus",
            packageName = "shared.enums",
            items = listOf(EnumItemModel(value = 1, name = "PUBLISHED", description = "Published")),
        )
        val valueObject = ValueObjectModel(
            name = "Money",
            packageName = "shared.values",
            scope = ValueObjectScope.SHARED,
            aggregate = null,
            storage = ValueObjectStorage.JSON,
            fields = listOf(FieldModel(name = "amount", type = "BigDecimal")),
        )
        val domainService = DomainServiceModel(
            name = "ContentPublicationPolicy",
            packageName = "content.domain",
            description = "publication policy",
            aggregates = listOf("Content"),
        )
        val saga = SagaModel(
            name = "PublishContentSaga",
            packageName = "content.workflow",
            description = "publish content",
            requestFields = listOf(FieldModel(name = "contentId", type = "ContentId")),
            responseFields = listOf(FieldModel(name = "accepted", type = "Boolean")),
        )
        val typeRegistry = TypeRegistryModel.empty()

        val model = CanonicalModel(
            project = project,
            aggregates = listOf(aggregate),
            commands = listOf(command),
            queries = listOf(query),
            clients = listOf(client),
            apiPayloads = listOf(apiPayload),
            domainEvents = listOf(domainEvent),
            integrationEvents = listOf(integrationEvent),
            strongIds = listOf(strongId),
            sharedEnums = listOf(sharedEnum),
            valueObjects = listOf(valueObject),
            domainServices = listOf(domainService),
            sagas = listOf(saga),
            typeRegistry = typeRegistry,
        )

        assertEquals(project, model.project)
        assertEquals(listOf(aggregate), model.aggregates)
        assertEquals(listOf(command), model.commands)
        assertEquals(listOf(query), model.queries)
        assertEquals(listOf(client), model.clients)
        assertEquals(listOf(apiPayload), model.apiPayloads)
        assertEquals(listOf(domainEvent), model.domainEvents)
        assertEquals(listOf(integrationEvent), model.integrationEvents)
        assertEquals(listOf(strongId), model.strongIds)
        assertEquals(listOf(sharedEnum), model.sharedEnums)
        assertEquals(listOf(valueObject), model.valueObjects)
        assertEquals(ValueObjectScope.SHARED, model.valueObjects.single().scope)
        assertEquals(ValueObjectStorage.JSON, model.valueObjects.single().storage)
        assertEquals(listOf(FieldModel(name = "amount", type = "BigDecimal")), model.valueObjects.single().fields)
        assertEquals(listOf(domainService), model.domainServices)
        assertEquals("ContentPublicationPolicy", model.domainServices.single().name)
        assertEquals(listOf(saga), model.sagas)
        assertEquals("PublishContentSaga", model.sagas.single().name)
        assertEquals(listOf(FieldModel(name = "contentId", type = "ContentId")), model.sagas.single().requestFields)
        assertEquals(listOf(FieldModel(name = "accepted", type = "Boolean")), model.sagas.single().responseFields)
        assertEquals(typeRegistry, model.typeRegistry)
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
    fun `canonical model keeps dedicated api payload slice alongside commands and aggregate slices`() {
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
            apiPayloads = listOf(payload),
            schemas = listOf(schema),
            entities = listOf(entity),
            repositories = listOf(repository),
        )

        assertEquals(listOf(command), model.commands)
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
            managedDefaultColumns = listOf("created_at", "updated_at"),
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
                writePolicy = SpecialFieldWritePolicy.READ_ONLY,
            ),
            deleted = ResolvedMarkerPolicy(
                enabled = true,
                fieldName = "deleted",
                columnName = "deleted",
                source = SpecialFieldSource.DB_EXPLICIT,
                writePolicy = SpecialFieldWritePolicy.SYSTEM_TRANSITION_ONLY,
            ),
            version = ResolvedMarkerPolicy(
                enabled = true,
                fieldName = "version",
                columnName = "version",
                source = SpecialFieldSource.DB_EXPLICIT,
                writePolicy = SpecialFieldWritePolicy.READ_ONLY,
            ),
            managedFields = listOf(
                ResolvedManagedFieldPolicy(
                    fieldName = "createdAt",
                    columnName = "created_at",
                    writePolicy = SpecialFieldWritePolicy.READ_ONLY,
                    source = SpecialFieldSource.DSL_DEFAULT,
                ),
                ResolvedManagedFieldPolicy(
                    fieldName = "updatedAt",
                    columnName = "updated_at",
                    writePolicy = SpecialFieldWritePolicy.SYSTEM_TRANSITION_ONLY,
                    source = SpecialFieldSource.DSL_DEFAULT,
                )
            ),
            writeSurface = ResolvedWriteSurfacePolicy(
                createAllowedFields = listOf("id", "title"),
                updateAllowedFields = listOf("title"),
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
        assertEquals(listOf("created_at", "updated_at"), report.aggregateSpecialFieldDefaults!!.managedDefaultColumns)
        assertEquals(listOf(resolvedPolicy), report.aggregateSpecialFieldResolvedPolicies)
        assertEquals(listOf(resolvedPolicy), result.aggregateSpecialFieldResolvedPolicies)
        assertEquals(listOf(resolvedPolicy), model.aggregateSpecialFieldResolvedPolicies)
        assertEquals(
            SpecialFieldWritePolicy.SYSTEM_TRANSITION_ONLY,
            report.aggregateSpecialFieldResolvedPolicies.single().managedFields[1].writePolicy,
        )
        assertEquals(
            listOf("title"),
            model.aggregateSpecialFieldResolvedPolicies.single().writeSurface.updateAllowedFields,
        )
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
                            managed = false,
                            exposed = true,
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
        assertEquals(false, snapshot.tables.single().columns.single().managed)
        assertEquals(true, snapshot.tables.single().columns.single().exposed)
    }
}
