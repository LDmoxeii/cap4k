package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateMetadataRecord
import com.only4.cap4k.plugin.pipeline.api.CommandVariant
import com.only4.cap4k.plugin.pipeline.api.AggregateCascadeType
import com.only4.cap4k.plugin.pipeline.api.AggregateFetchType
import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyKind
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.AggregateSpecialFieldDefaultsConfig
import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignSpecEntry
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.DesignElementSnapshot
import com.only4.cap4k.plugin.pipeline.api.DesignFieldSnapshot
import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import com.only4.cap4k.plugin.pipeline.api.EnumManifestSnapshot
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.IrAnalysisSnapshot
import com.only4.cap4k.plugin.pipeline.api.IrEdgeSnapshot
import com.only4.cap4k.plugin.pipeline.api.IrNodeSnapshot
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardElementModel
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardFieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.IntegrationEventRole
import com.only4.cap4k.plugin.pipeline.api.KspMetadataSnapshot
import com.only4.cap4k.plugin.pipeline.api.PipelineDiagnosticsException
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout.MULTI_MODULE
import com.only4.cap4k.plugin.pipeline.api.PackageLayout
import com.only4.cap4k.plugin.pipeline.api.RequestTrait
import com.only4.cap4k.plugin.pipeline.api.SpecialFieldSource
import com.only4.cap4k.plugin.pipeline.api.SpecialFieldWritePolicy
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryConverter
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryEntry
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryModel
import com.only4.cap4k.plugin.pipeline.api.UniqueConstraintModel
import com.only4.cap4k.plugin.pipeline.api.ValueObjectManifestSnapshot
import com.only4.cap4k.plugin.pipeline.api.ValueObjectModel
import com.only4.cap4k.plugin.pipeline.api.ValueObjectScope
import com.only4.cap4k.plugin.pipeline.api.ValueObjectStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultCanonicalAssemblerTest {

    @Test
    fun `query design block defaults to query and query handler artifacts`() {
        val model = DefaultCanonicalAssembler().assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "query",
                            packageName = "order.read",
                            name = "FindOrder",
                            description = "find order",
                            aggregates = listOf("Order"),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                            fields = listOf(FieldModel(name = "orderNo", type = "String")),
                            resultFields = listOf(FieldModel(name = "status", type = "String")),
                        ),
                    ),
                ),
            ),
        ).model

        val block = model.designBlocks.single()
        assertEquals("query", block.tag)
        assertEquals("FindOrder", block.name)
        assertEquals(
            listOf(
                ArtifactSelectionModel("query"),
                ArtifactSelectionModel("query-handler"),
            ),
            block.artifacts,
        )
        assertEquals(listOf("orderNo"), block.fields.map { it.name })
        assertEquals(listOf("status"), block.resultFields.map { it.name })
    }

    @Test
    fun `integration event design block defaults to outbound integration event`() {
        val model = DefaultCanonicalAssembler().assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "integration_event",
                            packageName = "order.events",
                            name = "OrderCreated",
                            description = "order created",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                            fields = listOf(FieldModel(name = "orderId", type = "Long")),
                            eventName = "order.created",
                        ),
                    ),
                ),
            ),
        ).model

        assertEquals(
            listOf(ArtifactSelectionModel("integration-event", "outbound")),
            model.designBlocks.single().artifacts,
        )
    }

    @Test
    fun `explicit empty integration event artifacts keep design block empty and skip typed integration events`() {
        val model = DefaultCanonicalAssembler().assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "integration_event",
                            packageName = "order.events",
                            name = "OrderCreated",
                            description = "order created",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                            artifacts = emptyList(),
                            fields = listOf(FieldModel(name = "orderId", type = "Long")),
                            eventName = "order.created",
                        ),
                    ),
                ),
            ),
        ).model

        assertEquals(emptyList<ArtifactSelectionModel>(), model.designBlocks.single().artifacts)
        assertEquals(0, model.integrationEvents.size)
    }

    @Test
    fun `integration subscriber requires explicit inbound integration event`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                config = baseConfig(),
                snapshots = listOf(
                    DesignSpecSnapshot(
                        entries = listOf(
                            DesignSpecEntry(
                                tag = "integration_event",
                                packageName = "order.events",
                                name = "OrderCreated",
                                description = "order created",
                                aggregates = emptyList(),
                                requestFields = emptyList(),
                                responseFields = emptyList(),
                                fields = listOf(FieldModel(name = "orderId", type = "Long")),
                                eventName = "order.created",
                                artifacts = listOf(
                                    ArtifactSelectionModel("integration-event", "outbound"),
                                    ArtifactSelectionModel("integration-subscriber"),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals(
            "integration_event OrderCreated integration-subscriber requires integration-event:inbound.",
            error.message,
        )
    }

    @Test
    fun `integration subscriber is valid with explicit inbound integration event`() {
        val model = DefaultCanonicalAssembler().assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "integration_event",
                            packageName = "order.events",
                            name = "OrderCreated",
                            description = "order created",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                            fields = listOf(FieldModel(name = "orderId", type = "Long")),
                            eventName = "order.created",
                            artifacts = listOf(
                                ArtifactSelectionModel("integration-event", "inbound"),
                                ArtifactSelectionModel("integration-subscriber"),
                            ),
                        ),
                    ),
                ),
            ),
        ).model

        assertEquals(
            listOf(
                ArtifactSelectionModel("integration-event", "inbound"),
                ArtifactSelectionModel("integration-subscriber"),
            ),
            model.designBlocks.single().artifacts,
        )
    }

    @Test
    fun `explicit query page artifact does not add query handler default`() {
        val model = DefaultCanonicalAssembler().assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "query",
                            packageName = "order.read",
                            name = "FindOrderPage",
                            description = "find order page",
                            aggregates = listOf("Order"),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                            artifacts = listOf(ArtifactSelectionModel("query", "page")),
                        ),
                    ),
                ),
            ),
        ).model

        assertEquals(
            listOf(ArtifactSelectionModel("query", "page")),
            model.designBlocks.single().artifacts,
        )
    }

    @Test
    fun `explicit empty artifact selections do not use default expansion`() {
        val model = DefaultCanonicalAssembler().assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "query",
                            packageName = "order.read",
                            name = "FindOrder",
                            description = "find order",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                            artifacts = emptyList(),
                        ),
                    ),
                ),
            ),
        ).model

        assertEquals(emptyList<ArtifactSelectionModel>(), model.designBlocks.single().artifacts)
    }

    @Test
    fun `design block validation rejects unsupported families variants and duplicate selections`() {
        val cases = listOf(
            Triple(
                listOf(ArtifactSelectionModel("unsupported")),
                "unsupported design artifact family on FindOrder: unsupported",
                "unsupported-family",
            ),
            Triple(
                listOf(ArtifactSelectionModel("query", "stream")),
                "design entry FindOrder artifact query has unsupported variant: stream",
                "unsupported-variant",
            ),
            Triple(
                listOf(ArtifactSelectionModel("query"), ArtifactSelectionModel("query")),
                "design entry FindOrder has duplicate artifact selection: query",
                "duplicate",
            ),
            Triple(
                listOf(ArtifactSelectionModel("query"), ArtifactSelectionModel("query", "page")),
                "design entry FindOrder has conflicting query variants",
                "conflicting-query-variant",
            ),
        )

        cases.forEach { (artifacts, expectedMessage, _) ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                DefaultCanonicalAssembler().assemble(
                    config = baseConfig(),
                    snapshots = listOf(
                        DesignSpecSnapshot(
                            entries = listOf(
                                DesignSpecEntry(
                                    tag = "query",
                                    packageName = "order.read",
                                    name = "FindOrder",
                                    description = "find order",
                                    aggregates = emptyList(),
                                    requestFields = emptyList(),
                                    responseFields = emptyList(),
                                    artifacts = artifacts,
                                ),
                            ),
                        ),
                    ),
                )
            }

            assertEquals(expectedMessage, error.message)
        }
    }

    @Test
    fun `design block validation rejects result fields on tags without result payloads`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                config = baseConfig(),
                snapshots = listOf(
                    DesignSpecSnapshot(
                        entries = listOf(
                            DesignSpecEntry(
                                tag = "command",
                                packageName = "order",
                                name = "SubmitOrder",
                                description = "submit order",
                                aggregates = emptyList(),
                                requestFields = emptyList(),
                                responseFields = emptyList(),
                                resultFields = listOf(FieldModel(name = "accepted", type = "Boolean")),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals("design entry SubmitOrder cannot declare resultFields on tag: command", error.message)
    }

    @Test
    fun `assembler splits canonical command query client into typed canonical collections`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "command",
                            packageName = "order",
                            name = "CreateOrder",
                            description = "create order",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "query",
                            packageName = "order",
                            name = "FindOrderList",
                            description = "list order",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "client",
                            packageName = "remote",
                            name = "SyncStock",
                            description = "sync stock",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                    )
                ),
            ),
        ).model

        assertEquals(listOf("CreateOrderCmd"), model.commands.map { it.typeName })
        assertEquals(listOf("FindOrderListQry"), model.queries.map { it.typeName })
        assertEquals(listOf("SyncStockCli"), model.clients.map { it.typeName })
        assertEquals(CommandVariant.DEFAULT, model.commands.single().variant)
    }

    @Test
    fun `query names with list and page suffixes do not imply request traits`() {
        val assembler = DefaultCanonicalAssembler()

        val result = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "query",
                            packageName = "order.read",
                            name = "FindOrderList",
                            description = "find order list",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "query",
                            packageName = "order.read",
                            name = "FindOrderPage",
                            description = "find order page",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                    )
                ),
            ),
        )

        assertEquals(listOf("FindOrderListQry", "FindOrderPageQry"), result.model.queries.map { it.typeName })
        assertEquals(listOf(emptySet<RequestTrait>(), emptySet<RequestTrait>()), result.model.queries.map { it.traits })
    }

    @Test
    fun `assembler carries page traits on query and api payload canonical models`() {
        val assembler = DefaultCanonicalAssembler()
        val result = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "query",
                            packageName = "order.read",
                            name = "FindOrderPage",
                            description = "find order page",
                            aggregates = emptyList(),
                            traits = setOf(RequestTrait.PAGE),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "api_payload",
                            packageName = "order.read",
                            name = "FindOrderPage",
                            description = "find order page payload",
                            aggregates = emptyList(),
                            traits = setOf(RequestTrait.PAGE),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(setOf(RequestTrait.PAGE), result.model.queries.single().traits)
        assertEquals(setOf(RequestTrait.PAGE), result.model.apiPayloads.single().traits)
    }

    @Test
    fun `assembler carries integration events into canonical model`() {
        val assembler = DefaultCanonicalAssembler()

        val result = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "integration_event",
                            packageName = "order.events",
                            name = "order_created",
                            description = "order created inbound",
                            aggregates = emptyList(),
                            requestFields = listOf(
                                FieldModel(name = "orderId", type = "Long"),
                                FieldModel(name = "buyerId", type = "Long"),
                            ),
                            responseFields = emptyList(),
                            role = "inbound",
                            eventName = "order.created",
                        ),
                        DesignSpecEntry(
                            tag = "integration_event",
                            packageName = "order.events",
                            name = "OrderPaid",
                            description = "order paid outbound",
                            aggregates = emptyList(),
                            requestFields = listOf(
                                FieldModel(name = "paymentId", type = "String"),
                            ),
                            responseFields = emptyList(),
                            role = "outbound",
                            eventName = "order.paid",
                        ),
                    ),
                ),
            ),
        )

        val integrationEvents = result.model.integrationEvents
        assertEquals(2, integrationEvents.size)
        assertEquals(listOf(IntegrationEventRole.INBOUND, IntegrationEventRole.OUTBOUND), integrationEvents.map { it.role })
        assertEquals(listOf("order.created", "order.paid"), integrationEvents.map { it.eventName })
        assertEquals(listOf("OrderCreatedIntegrationEvent", "OrderPaidIntegrationEvent"), integrationEvents.map { it.typeName })
        assertEquals(listOf("orderId", "buyerId"), integrationEvents.first().fields.map { it.name })
        assertEquals(listOf("paymentId"), integrationEvents[1].fields.map { it.name })
    }

    @Test
    fun `assembles domain services sagas and value objects`() {
        val design = DesignSpecSnapshot(
            entries = listOf(
                DesignSpecEntry(
                    tag = "domain_service",
                    packageName = "content.domain",
                    name = "ContentPublicationPolicy",
                    description = "publication policy",
                    aggregates = listOf("Content"),
                    requestFields = emptyList(),
                    responseFields = emptyList(),
                ),
                DesignSpecEntry(
                    tag = "saga",
                    packageName = "content.workflow",
                    name = "PublishContentSaga",
                    description = "publish content",
                    aggregates = emptyList(),
                    requestFields = listOf(FieldModel(name = "contentId", type = "ContentId")),
                    responseFields = listOf(FieldModel(name = "accepted", type = "Boolean")),
                ),
            )
        )
        val valueObjects = ValueObjectManifestSnapshot(
            valueObjects = listOf(
                ValueObjectModel(
                    name = "Money",
                    packageName = "shared.values",
                    scope = ValueObjectScope.SHARED,
                    storage = ValueObjectStorage.JSON,
                    fields = listOf(FieldModel(name = "amount", type = "BigDecimal")),
                )
            )
        )
        val typeRegistry = TypeRegistryModel(
            entries = mapOf("ContentId" to TypeRegistryEntry(fqn = "content.types.ContentId"))
        )

        val model = assemble(design = design, valueObjects = valueObjects, typeRegistry = typeRegistry)

        assertEquals("ContentPublicationPolicy", model.domainServices.single().name)
        assertEquals(listOf("Content"), model.domainServices.single().aggregates)
        assertEquals("PublishContentSaga", model.sagas.single().name)
        assertEquals(listOf("contentId"), model.sagas.single().requestFields.map { it.name })
        assertEquals(listOf("accepted"), model.sagas.single().responseFields.map { it.name })
        assertEquals("Money", model.valueObjects.single().name)
        assertEquals(typeRegistry.entries, model.typeRegistry.entries)
    }

    @Test
    fun `same aggregate local enum repeated with same definition does not fail duplicate simple-name validation`() {
        val model = assemble(
            db = DbSchemaSnapshot(
                tables = listOf(
                    DbTableSnapshot(
                        tableName = "video_post",
                        comment = "",
                        columns = listOf(
                            DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            DbColumnSnapshot(
                                name = "visibility",
                                dbType = "INT",
                                kotlinType = "Int",
                                nullable = false,
                                typeBinding = "Visibility",
                                enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden")),
                            ),
                            DbColumnSnapshot(
                                name = "default_visibility",
                                dbType = "INT",
                                kotlinType = "Int",
                                nullable = false,
                                typeBinding = "Visibility",
                                enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden")),
                            ),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                    )
                )
            )
        )

        assertEquals(listOf("VideoPost"), model.entities.map { it.name })
    }

    @Test
    fun `same aggregate local enum repeated with different definitions fails duplicate simple-name validation`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            assemble(
                db = DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot(
                                    name = "visibility",
                                    dbType = "INT",
                                    kotlinType = "Int",
                                    nullable = false,
                                    typeBinding = "Visibility",
                                    enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden")),
                                ),
                                DbColumnSnapshot(
                                    name = "default_visibility",
                                    dbType = "INT",
                                    kotlinType = "Int",
                                    nullable = false,
                                    typeBinding = "Visibility",
                                    enumItems = listOf(EnumItemModel(1, "PUBLIC", "Public")),
                                ),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        )
                    )
                )
            )
        }

        assertTrue(error.message!!.contains("Duplicate type simple name: Visibility"))
    }

    @Test
    fun `different aggregate owners can define local enums with the same simple name`() {
        val model = assemble(
            db = DbSchemaSnapshot(
                tables = listOf(
                    DbTableSnapshot(
                        tableName = "content",
                        comment = "",
                        columns = listOf(
                            DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            DbColumnSnapshot(
                                name = "status",
                                dbType = "INT",
                                kotlinType = "Int",
                                nullable = false,
                                typeBinding = "Status",
                                enumItems = listOf(EnumItemModel(0, "DRAFT", "Draft")),
                            ),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                    ),
                    DbTableSnapshot(
                        tableName = "review",
                        comment = "",
                        columns = listOf(
                            DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            DbColumnSnapshot(
                                name = "status",
                                dbType = "INT",
                                kotlinType = "Int",
                                nullable = false,
                                typeBinding = "Status",
                                enumItems = listOf(EnumItemModel(0, "PENDING", "Pending")),
                            ),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                    ),
                )
            )
        )

        assertEquals(listOf("Content", "Review"), model.entities.map { it.name })
    }

    @Test
    fun `aggregate-local value objects with same simple name in different aggregates do not fail`() {
        val valueObjects = ValueObjectManifestSnapshot(
            valueObjects = listOf(
                ValueObjectModel(
                    name = "Snapshot",
                    packageName = "content.values",
                    scope = ValueObjectScope.AGGREGATE,
                    aggregate = "Content",
                ),
                ValueObjectModel(
                    name = "Snapshot",
                    packageName = "review.values",
                    scope = ValueObjectScope.AGGREGATE,
                    aggregate = "Review",
                ),
            )
        )

        val model = assemble(valueObjects = valueObjects)

        assertEquals(listOf("Content", "Review"), model.valueObjects.map { it.aggregate })
    }

    @Test
    fun `same aggregate local enum and value object with same simple name fail duplicate validation`() {
        val valueObjects = ValueObjectManifestSnapshot(
            valueObjects = listOf(
                ValueObjectModel(
                    name = "Status",
                    packageName = "content.values",
                    scope = ValueObjectScope.AGGREGATE,
                    aggregate = "Content",
                ),
            )
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            assemble(
                db = DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "content",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot(
                                    name = "status",
                                    dbType = "INT",
                                    kotlinType = "Int",
                                    nullable = false,
                                    typeBinding = "Status",
                                    enumItems = listOf(EnumItemModel(0, "DRAFT", "Draft")),
                                ),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        )
                    )
                ),
                valueObjects = valueObjects,
            )
        }

        assertTrue(error.message!!.contains("Duplicate type simple name: Status"))
    }

    @Test
    fun `fails on duplicate simple type names across enum value object and registry`() {
        val valueObjects = ValueObjectManifestSnapshot(
            valueObjects = listOf(
                ValueObjectModel(
                    name = "Status",
                    packageName = "shared.values",
                    scope = ValueObjectScope.SHARED,
                    storage = ValueObjectStorage.JSON,
                )
            )
        )
        val typeRegistry = TypeRegistryModel(
            entries = mapOf("Status" to TypeRegistryEntry(fqn = "com.acme.Status"))
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            assemble(valueObjects = valueObjects, typeRegistry = typeRegistry)
        }

        assertTrue(error.message!!.contains("Duplicate type simple name: Status"))
    }

    @Test
    fun `assembler rejects integration event with blank event name`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                config = baseConfig(),
                snapshots = listOf(
                    DesignSpecSnapshot(
                        entries = listOf(
                            DesignSpecEntry(
                                tag = "integration_event",
                                packageName = "order.events",
                                name = "OrderCreated",
                                description = "order created inbound",
                                aggregates = emptyList(),
                                requestFields = emptyList(),
                                responseFields = emptyList(),
                                role = "inbound",
                                eventName = " ",
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals("integration_event OrderCreated must declare eventName.", error.message)
    }

    @Test
    fun `assembler rejects integration event without request fields`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                config = baseConfig(),
                snapshots = listOf(
                    DesignSpecSnapshot(
                        entries = listOf(
                            DesignSpecEntry(
                                tag = "integration_event",
                                packageName = "order.events",
                                name = "OrderCreated",
                                description = "order created inbound",
                                aggregates = emptyList(),
                                requestFields = emptyList(),
                                responseFields = emptyList(),
                                role = "inbound",
                                eventName = "order.created",
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals("integration_event OrderCreated must declare at least one requestField.", error.message)
    }

    @Test
    fun `client design tags map into canonical clients`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "client_legacy_alias",
                            packageName = "auth.client",
                            name = "IssueToken",
                            description = "issue token",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "client",
                            packageName = "auth.client",
                            name = "RefreshToken",
                            description = "refresh token",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "clients_legacy_alias",
                            packageName = "auth.client",
                            name = "RevokeToken",
                            description = "revoke token",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                    )
                ),
            ),
        ).model

        assertEquals(
            listOf("RefreshTokenCli"),
            model.clients.map { it.typeName },
        )
        assertEquals(
            listOf("auth.client"),
            model.clients.map { it.packageName },
        )
    }

    @Test
    fun `client naming keeps command and query mappings unchanged`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "command",
                            packageName = "order.submit",
                            name = "SubmitOrder",
                            description = "submit order",
                            aggregates = listOf("Order"),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "client",
                            packageName = "order.remote",
                            name = "IssueToken",
                            description = "issue token",
                            aggregates = listOf("Order"),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "query",
                            packageName = "order.read",
                            name = "FindOrder",
                            description = "find order",
                            aggregates = listOf("Order"),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                    )
                ),
                KspMetadataSnapshot(
                    aggregates = listOf(
                        AggregateMetadataRecord(
                            aggregateName = "Order",
                            rootQualifiedName = "com.acme.demo.domain.aggregates.order.Order",
                            rootPackageName = "com.acme.demo.domain.aggregates.order",
                            rootClassName = "Order",
                        )
                    )
                ),
            ),
        ).model

        assertEquals(listOf("SubmitOrderCmd"), model.commands.map { it.typeName })
        assertEquals(listOf("IssueTokenCli"), model.clients.map { it.typeName })
        assertEquals(listOf("FindOrderQry"), model.queries.map { it.typeName })
        assertEquals("Order", model.commands.single().aggregateRef?.name)
        assertEquals("Order", model.clients.single().aggregateRef?.name)
        assertEquals("Order", model.queries.single().aggregateRef?.name)
        assertEquals(
            "com.acme.demo.domain.aggregates.order",
            model.commands.single().aggregateRef?.packageName,
        )
        assertEquals(
            "com.acme.demo.domain.aggregates.order",
            model.clients.single().aggregateRef?.packageName,
        )
        assertEquals(
            "com.acme.demo.domain.aggregates.order",
            model.queries.single().aggregateRef?.packageName,
        )
    }

    @Test
    fun `api payload entries assemble into dedicated api payload slice and keep command assembly unchanged`() {
        val assembler = DefaultCanonicalAssembler()

        val requestFields = listOf(FieldModel(name = "accountIds", type = "List<Long>"))
        val responseFields = listOf(FieldModel(name = "saved", type = "Int"))

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "api_payload",
                            packageName = "auth.payload",
                            name = "batchSaveAccountList",
                            description = "batch save account payload",
                            aggregates = emptyList(),
                            requestFields = requestFields,
                            responseFields = responseFields,
                        ),
                        DesignSpecEntry(
                            tag = "command",
                            packageName = "order.submit",
                            name = "SubmitOrder",
                            description = "submit order",
                            aggregates = listOf("Order"),
                            requestFields = listOf(FieldModel(name = "orderId", type = "Long")),
                            responseFields = listOf(FieldModel(name = "accepted", type = "Boolean")),
                        ),
                    )
                ),
            ),
        ).model

        assertEquals(1, model.apiPayloads.size)
        val payload = model.apiPayloads.single()
        assertEquals("auth.payload", payload.packageName)
        assertEquals("BatchSaveAccountList", payload.typeName)
        assertEquals("batch save account payload", payload.description)
        assertEquals(requestFields, payload.requestFields)
        assertEquals(responseFields, payload.responseFields)

        assertEquals(1, model.commands.size)
        val command = model.commands.single()
        assertEquals("order.submit", command.packageName)
        assertEquals("SubmitOrderCmd", command.typeName)
        assertEquals("submit order", command.description)
        assertNull(command.aggregateRef)
        assertEquals(listOf(FieldModel(name = "orderId", type = "Long")), command.requestFields)
        assertEquals(listOf(FieldModel(name = "accepted", type = "Boolean")), command.responseFields)
    }

    @Test
    fun `api payload slice skips legacy payload aliases`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "payload_legacy_alias",
                            packageName = "auth.payload",
                            name = "LegacyPayload",
                            description = "legacy payload alias",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "request_payload",
                            packageName = "auth.payload",
                            name = "LegacyRequestPayload",
                            description = "legacy request payload alias",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "req_payload",
                            packageName = "auth.payload",
                            name = "LegacyReqPayload",
                            description = "legacy req payload alias",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "request",
                            packageName = "auth.payload",
                            name = "LegacyRequest",
                            description = "legacy request alias",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "req",
                            packageName = "auth.payload",
                            name = "LegacyReq",
                            description = "legacy req alias",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "api_payload",
                            packageName = "auth.payload",
                            name = "BatchSaveAccountList",
                            description = "canonical payload",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                    )
                ),
            ),
        ).model

        assertEquals(1, model.apiPayloads.size)
        assertEquals("BatchSaveAccountList", model.apiPayloads.single().typeName)
    }

    @Test
    fun `domain event entries assemble into dedicated canonical domain events with old compatible naming`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "domain_event",
                            packageName = "order",
                            name = "OrderCreated",
                            description = "order created event",
                            aggregates = listOf("Order"),
                            persist = true,
                            requestFields = listOf(
                                FieldModel(name = "reason", type = "String"),
                                FieldModel(name = "snapshot", type = "Snapshot", nullable = true),
                                FieldModel(name = "snapshot.traceId", type = "UUID"),
                            ),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "domain_event",
                            packageName = "order",
                            name = "OrderCreatedEvt",
                            description = "evt naming keeps suffix",
                            aggregates = listOf("Order"),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "domain_event",
                            packageName = "order",
                            name = "OrderCreatedEvent",
                            description = "event naming keeps suffix",
                            aggregates = listOf("Order"),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "domain_event",
                            packageName = "order",
                            name = "order_created",
                            description = "snake case",
                            aggregates = listOf("Order"),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "domain_event",
                            packageName = "order",
                            name = "order-created",
                            description = "kebab case",
                            aggregates = listOf("Order"),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "domain_event",
                            packageName = "order",
                            name = "order created event",
                            description = "space separated words",
                            aggregates = listOf("Order"),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "domain_event",
                            packageName = "order",
                            name = "orderCreated",
                            description = "lower camel",
                            aggregates = listOf("Order"),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "domain-event",
                            packageName = "order",
                            name = "IgnoredOrderCreated",
                            description = "unsupported alias",
                            aggregates = listOf("Order"),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                    )
                ),
                KspMetadataSnapshot(
                    aggregates = listOf(
                        AggregateMetadataRecord(
                            aggregateName = "Order",
                            rootQualifiedName = "com.acme.demo.domain.aggregates.order.Order",
                            rootPackageName = "com.acme.demo.domain.aggregates.order",
                            rootClassName = "Order",
                        )
                    )
                ),
            ),
        ).model

        assertEquals(7, model.domainEvents.size)
        assertEquals(
            listOf(
                "OrderCreatedDomainEvent",
                "OrderCreatedEvt",
                "OrderCreatedEvent",
                "OrderCreatedDomainEvent",
                "OrderCreatedDomainEvent",
                "OrderCreatedEventDomainEvent",
                "OrderCreatedDomainEvent",
            ),
            model.domainEvents.map { it.typeName },
        )
        assertEquals(List(7) { "Order" }, model.domainEvents.map { it.aggregateName })
        assertEquals(
            listOf(
                "com.acme.demo.domain.aggregates.order",
                "com.acme.demo.domain.aggregates.order",
                "com.acme.demo.domain.aggregates.order",
                "com.acme.demo.domain.aggregates.order",
                "com.acme.demo.domain.aggregates.order",
                "com.acme.demo.domain.aggregates.order",
                "com.acme.demo.domain.aggregates.order",
            ),
            model.domainEvents.map { it.aggregatePackageName },
        )
        assertEquals(listOf(true, false, false, false, false, false, false), model.domainEvents.map { it.persist })
        assertEquals(
            listOf(
                FieldModel(name = "reason", type = "String"),
                FieldModel(name = "snapshot", type = "Snapshot", nullable = true),
                FieldModel(name = "snapshot.traceId", type = "UUID"),
            ),
            model.domainEvents.first().fields,
        )
        assertEquals(false, model.domainEvents.first().fields.any { it.name == "entity" })
    }

    @Test
    fun `domain event package key is derived from aggregate package group instead of design package`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "domain_event",
                            packageName = "message",
                            name = "UserMessageCreated",
                            description = "user message created",
                            aggregates = listOf("UserMessage"),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                    )
                ),
                KspMetadataSnapshot(
                    aggregates = listOf(
                        AggregateMetadataRecord(
                            aggregateName = "UserMessage",
                            rootQualifiedName = "com.acme.demo.domain.aggregates.user_message.UserMessage",
                            rootPackageName = "com.acme.demo.domain.aggregates.user_message",
                            rootClassName = "UserMessage",
                        )
                    )
                ),
            ),
        ).model

        val event = model.domainEvents.single()
        assertEquals("user_message", event.packageName)
        assertEquals("UserMessage", event.aggregateName)
        assertEquals("com.acme.demo.domain.aggregates.user_message", event.aggregatePackageName)
    }

    @Test
    fun `domain event resolves aggregate package from canonical aggregate entities before ksp metadata`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "domain_event",
                            packageName = "order",
                            name = "OrderCreated",
                            description = "order created event",
                            aggregates = listOf("Order"),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                    )
                ),
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "order",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                    )
                ),
                KspMetadataSnapshot(
                    aggregates = listOf(
                        AggregateMetadataRecord(
                            aggregateName = "Order",
                            rootQualifiedName = "com.acme.legacy.order.Order",
                            rootPackageName = "com.acme.legacy.order",
                            rootClassName = "Order",
                        )
                    )
                ),
            ),
        ).model

        assertEquals("Order", model.domainEvents.single().aggregateName)
        assertEquals("com.acme.demo.domain.aggregates.order", model.domainEvents.single().aggregatePackageName)
    }

    @Test
    fun `domain event falls back to ksp metadata when canonical aggregate data is absent`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "domain_event",
                            packageName = "order",
                            name = "OrderCreated",
                            description = "order created event",
                            aggregates = listOf("Order"),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                    )
                ),
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                    )
                ),
                KspMetadataSnapshot(
                    aggregates = listOf(
                        AggregateMetadataRecord(
                            aggregateName = "Order",
                            rootQualifiedName = "com.acme.legacy.order.Order",
                            rootPackageName = "com.acme.legacy.order",
                            rootClassName = "Order",
                        )
                    )
                ),
            ),
        ).model

        assertEquals("Order", model.domainEvents.single().aggregateName)
        assertEquals("com.acme.legacy.order", model.domainEvents.single().aggregatePackageName)
    }

    @Test
    fun `domain event assembly fails when aggregates is empty`() {
        val assembler = DefaultCanonicalAssembler()

        val error = assertThrows(IllegalArgumentException::class.java) {
            assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "domain_event",
                            packageName = "order",
                            name = "NoAggregate",
                            description = "missing aggregate",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                    )
                ),
                KspMetadataSnapshot(
                    aggregates = listOf(
                        AggregateMetadataRecord(
                            aggregateName = "Order",
                            rootQualifiedName = "com.acme.demo.domain.aggregates.order.Order",
                            rootPackageName = "com.acme.demo.domain.aggregates.order",
                            rootClassName = "Order",
                        )
                    )
                ),
            ),
        )
        }

        assertEquals("domain_event NoAggregate must declare exactly one aggregate, but found 0.", error.message)
    }

    @Test
    fun `domain event assembly fails when aggregates has more than one item`() {
        val assembler = DefaultCanonicalAssembler()

        val error = assertThrows(IllegalArgumentException::class.java) {
            assembler.assemble(
                config = baseConfig(),
                snapshots = listOf(
                    DesignSpecSnapshot(
                        entries = listOf(
                            DesignSpecEntry(
                                tag = "domain_event",
                                packageName = "order",
                                name = "TooManyAggregates",
                                description = "multiple aggregates",
                                aggregates = listOf("Order", "Customer"),
                                requestFields = emptyList(),
                                responseFields = emptyList(),
                            ),
                        )
                    ),
                    KspMetadataSnapshot(
                        aggregates = listOf(
                            AggregateMetadataRecord(
                                aggregateName = "Order",
                                rootQualifiedName = "com.acme.demo.domain.aggregates.order.Order",
                                rootPackageName = "com.acme.demo.domain.aggregates.order",
                                rootClassName = "Order",
                            )
                        )
                    ),
                ),
            )
        }

        assertEquals("domain_event TooManyAggregates must declare exactly one aggregate, but found 2.", error.message)
    }

    @Test
    fun `domain event assembly fails when aggregate metadata is missing`() {
        val assembler = DefaultCanonicalAssembler()

        val error = assertThrows(IllegalArgumentException::class.java) {
            assembler.assemble(
                config = baseConfig(),
                snapshots = listOf(
                    DesignSpecSnapshot(
                        entries = listOf(
                            DesignSpecEntry(
                                tag = "domain_event",
                                packageName = "order",
                                name = "UnknownAggregate",
                                description = "unknown aggregate",
                                aggregates = listOf("Unknown"),
                                requestFields = emptyList(),
                                responseFields = emptyList(),
                            ),
                        )
                    ),
                    KspMetadataSnapshot(
                        aggregates = listOf(
                            AggregateMetadataRecord(
                                aggregateName = "Order",
                                rootQualifiedName = "com.acme.demo.domain.aggregates.order.Order",
                                rootPackageName = "com.acme.demo.domain.aggregates.order",
                                rootClassName = "Order",
                            )
                        )
                    ),
                ),
            )
        }

        assertEquals("domain_event UnknownAggregate references missing aggregate metadata: Unknown", error.message)
    }

    @Test
    fun `maps design entries and ksp aggregates into typed canonical interactions`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = MULTI_MODULE,
                modules = mapOf("application" to "demo-application"),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            ),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "command",
                            packageName = "order.submit",
                            name = "SubmitOrder",
                            description = "submit order",
                            aggregates = listOf("Order"),
                            requestFields = listOf(FieldModel(name = "orderId", type = "Long")),
                            responseFields = listOf(FieldModel(name = "accepted", type = "Boolean")),
                        ),
                        DesignSpecEntry(
                            tag = "query",
                            packageName = "order.read",
                            name = "FindOrder",
                            description = "find order",
                            aggregates = listOf("Order"),
                            requestFields = listOf(FieldModel(name = "orderId", type = "Long")),
                            responseFields = listOf(FieldModel(name = "status", type = "String")),
                        ),
                    )
                ),
                KspMetadataSnapshot(
                    aggregates = listOf(
                        AggregateMetadataRecord(
                            aggregateName = "Order",
                            rootQualifiedName = "com.acme.demo.domain.aggregates.order.Order",
                            rootPackageName = "com.acme.demo.domain.aggregates.order",
                            rootClassName = "Order",
                        )
                    )
                ),
            ),
        ).model

        assertEquals(1, model.commands.size)
        val command = model.commands.single()
        assertEquals("SubmitOrderCmd", command.typeName)
        assertEquals("order.submit", command.packageName)
        assertEquals("submit order", command.description)
        assertEquals("Order", command.aggregateRef?.name)
        assertEquals("com.acme.demo.domain.aggregates.order", command.aggregateRef?.packageName)
        assertEquals(listOf(FieldModel(name = "orderId", type = "Long")), command.requestFields)
        assertEquals(listOf(FieldModel(name = "accepted", type = "Boolean")), command.responseFields)

        assertEquals(1, model.queries.size)
        val query = model.queries.single()
        assertEquals("FindOrderQry", query.typeName)
        assertEquals("order.read", query.packageName)
        assertEquals("find order", query.description)
        assertEquals("Order", query.aggregateRef?.name)
        assertEquals("com.acme.demo.domain.aggregates.order", query.aggregateRef?.packageName)
        assertEquals(emptySet<RequestTrait>(), query.traits)
    }

    @Test
    fun `skips entries with unsupported tags`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "evt",
                            packageName = "order.events",
                            name = "OrderCreated",
                            description = "order created event",
                            aggregates = listOf("Order"),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "command",
                            packageName = "order.submit",
                            name = "SubmitOrder",
                            description = "submit order",
                            aggregates = listOf("Order"),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                    )
                ),
            ),
        ).model

        assertEquals(1, model.commands.size)
        assertEquals("SubmitOrderCmd", model.commands.first().typeName)
        assertEquals(emptyList<String>(), model.queries.map { it.typeName })
        assertEquals(emptyList<String>(), model.clients.map { it.typeName })
    }

    @Test
    fun `design spec assembly ignores non exact canonical tags and historical aliases`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "COMMAND",
                            packageName = "order.submit",
                            name = "UpperCommand",
                            description = "upper command",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "Query",
                            packageName = "order.read",
                            name = "MixedQuery",
                            description = "mixed query",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "Client",
                            packageName = "order.remote",
                            name = "MixedClient",
                            description = "mixed client",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "API_PAYLOAD",
                            packageName = "order.payload",
                            name = "UpperPayload",
                            description = "upper payload",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "DOMAIN_EVENT",
                            packageName = "order.events",
                            name = "UpperDomainEvent",
                            description = "upper domain event",
                            aggregates = listOf("Order"),
                            persist = true,
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "cmd",
                            packageName = "order.submit",
                            name = "LegacyCommand",
                            description = "legacy command",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "qry",
                            packageName = "order.read",
                            name = "LegacyQuery",
                            description = "legacy query",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "cli",
                            packageName = "order.remote",
                            name = "LegacyClient",
                            description = "legacy client",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "clients",
                            packageName = "order.remote",
                            name = "LegacyClients",
                            description = "legacy clients",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "payload",
                            packageName = "order.payload",
                            name = "LegacyPayload",
                            description = "legacy payload",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "de",
                            packageName = "order.events",
                            name = "LegacyDomainEvent",
                            description = "legacy domain event",
                            aggregates = listOf("Order"),
                            persist = true,
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                    )
                ),
                KspMetadataSnapshot(
                    aggregates = listOf(
                        AggregateMetadataRecord(
                            aggregateName = "Order",
                            rootQualifiedName = "com.acme.demo.domain.aggregates.order.Order",
                            rootPackageName = "com.acme.demo.domain.aggregates.order",
                            rootClassName = "Order",
                        )
                    )
                ),
            ),
        ).model

        assertEquals(emptyList<String>(), model.commands.map { it.typeName })
        assertEquals(emptyList<String>(), model.queries.map { it.typeName })
        assertEquals(emptyList<String>(), model.clients.map { it.typeName })
        assertEquals(emptyList<String>(), model.apiPayloads.map { it.typeName })
        assertEquals(emptyList<String>(), model.domainEvents.map { it.typeName })
    }

    @Test
    fun `returns empty model when design snapshot is missing`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                KspMetadataSnapshot(
                    aggregates = listOf(
                        AggregateMetadataRecord(
                            aggregateName = "Order",
                            rootQualifiedName = "com.acme.demo.domain.aggregates.order.Order",
                            rootPackageName = "com.acme.demo.domain.aggregates.order",
                            rootClassName = "Order",
                        )
                    )
                ),
            ),
        ).model

        assertEquals(0, model.commands.size)
        assertEquals(0, model.queries.size)
        assertEquals(0, model.clients.size)
    }

    @Test
    fun `leaves request aggregate ref null when ksp metadata has no match`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "query",
                            packageName = "order.read",
                            name = "FindOrder",
                            description = "find order",
                            aggregates = listOf("Order"),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                    )
                ),
                KspMetadataSnapshot(
                    aggregates = listOf(
                        AggregateMetadataRecord(
                            aggregateName = "Customer",
                            rootQualifiedName = "com.acme.demo.domain.aggregates.customer.Customer",
                            rootPackageName = "com.acme.demo.domain.aggregates.customer",
                            rootClassName = "Customer",
                        )
                    )
                ),
            ),
        ).model

        assertEquals(1, model.queries.size)
        assertNull(model.queries.first().aggregateRef)
    }

    @Test
    fun `maps ir analysis snapshot into canonical analysis graph`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                IrAnalysisSnapshot(
                    inputDirs = listOf("app/build/cap4k-code-analysis"),
                    nodes = listOf(
                        IrNodeSnapshot(
                            id = "OrderController::submit",
                            name = "OrderController::submit",
                            fullName = "com.acme.demo.adapter.web.OrderController::submit",
                            type = "controllermethod",
                        ),
                        IrNodeSnapshot(
                            id = "SubmitOrderCmd",
                            name = "SubmitOrderCmd",
                            fullName = "com.acme.demo.application.commands.SubmitOrderCmd",
                            type = "command",
                        ),
                    ),
                    edges = listOf(
                        IrEdgeSnapshot(
                            fromId = "OrderController::submit",
                            toId = "SubmitOrderCmd",
                            type = "ControllerMethodToCommand",
                        )
                    ),
                )
            ),
        ).model

        assertEquals(listOf("app/build/cap4k-code-analysis"), model.analysisGraph!!.inputDirs)
        assertEquals(2, model.analysisGraph!!.nodes.size)
        assertEquals("controllermethod", model.analysisGraph!!.nodes.first().type)
        assertEquals("ControllerMethodToCommand", model.analysisGraph!!.edges.single().type)
    }

    @Test
    fun `assembles drawing board as generate ready design json contract`() {
        val assembler = DefaultCanonicalAssembler()

        val supportedField = DesignFieldSnapshot(name = "orderId", type = "Long", nullable = false, defaultValue = "0")
        val responseField = DesignFieldSnapshot(name = "accepted", type = "Boolean")
        val duplicateField = DesignFieldSnapshot(name = "ignored", type = "String")
        val entityField = DesignFieldSnapshot(name = "entity", type = "Order")
        val reasonField = DesignFieldSnapshot(name = "reason", type = "String")

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                IrAnalysisSnapshot(
                    inputDirs = listOf("app/build/cap4k-code-analysis"),
                    nodes = emptyList(),
                    edges = emptyList(),
                    designElements = listOf(
                        DesignElementSnapshot(
                            tag = "command",
                            packageName = "order.submit",
                            name = "SubmitOrder",
                            description = "submit order",
                            aggregates = listOf("Order"),
                            entity = "Order",
                            persist = true,
                            requestFields = listOf(supportedField),
                            responseFields = listOf(responseField),
                        ),
                        DesignElementSnapshot(
                            tag = "command",
                            packageName = "order.submit",
                            name = "SubmitOrder",
                            description = "duplicate submit order",
                            aggregates = listOf("Ignored"),
                            entity = "Ignored",
                            persist = false,
                            requestFields = listOf(duplicateField),
                            responseFields = listOf(duplicateField),
                        ),
                        DesignElementSnapshot(
                            tag = "client",
                            packageName = "order.delivery",
                            name = "PublishOrder",
                            description = "publish order",
                        ),
                        DesignElementSnapshot(
                            tag = "query",
                            packageName = "order.read",
                            name = "FindOrder",
                            description = "find order",
                            aggregates = listOf("Order"),
                            traits = setOf(RequestTrait.PAGE),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignElementSnapshot(
                            tag = "api_payload",
                            packageName = "order.payload",
                            name = "CreateOrderPayload",
                            description = "create order payload",
                            traits = setOf(RequestTrait.PAGE),
                        ),
                        DesignElementSnapshot(
                            tag = "domain_event",
                            packageName = "order.events",
                            name = "OrderCreatedDomainEvent",
                            description = "order created",
                            aggregates = listOf("Order"),
                            entity = "Order",
                            persist = false,
                            requestFields = listOf(entityField, reasonField),
                        ),
                        DesignElementSnapshot(
                            tag = "evt",
                            packageName = "order.events",
                            name = "OrderCreated",
                            description = "order created",
                        ),
                    ),
                ),
            ),
        ).model

        val drawingBoard = model.drawingBoard
        assertEquals(5, drawingBoard!!.elements.size)
        assertEquals(
            DrawingBoardElementModel(
                tag = "command",
                packageName = "order.submit",
                name = "SubmitOrder",
                description = "submit order",
                aggregates = listOf("Order"),
                entity = "Order",
                persist = true,
                requestFields = listOf(DrawingBoardFieldModel(name = "orderId", type = "Long", nullable = false, defaultValue = "0")),
                responseFields = listOf(DrawingBoardFieldModel(name = "accepted", type = "Boolean")),
            ),
            drawingBoard.elements.first(),
        )
        assertEquals(
            listOf("command", "client", "query", "api_payload", "domain_event"),
            drawingBoard.elementsByTag.keys.toList(),
        )
        assertEquals(1, drawingBoard.elementsByTag.getValue("command").size)
        val query = drawingBoard.elementsByTag.getValue("query").single()
        assertEquals("FindOrder", query.name)
        assertEquals(setOf(RequestTrait.PAGE), query.traits)
        val apiPayload = drawingBoard.elementsByTag.getValue("api_payload").single()
        assertEquals("CreateOrderPayload", apiPayload.name)
        assertEquals(setOf(RequestTrait.PAGE), apiPayload.traits)

        val domainEvent = drawingBoard.elementsByTag.getValue("domain_event").single()
        assertEquals(null, domainEvent.entity)
        assertEquals(listOf(DrawingBoardFieldModel(name = "reason", type = "String")), domainEvent.requestFields)
    }

    @Test
    fun `drawing board projection accepts only canonical design tags`() {
        val assembler = DefaultCanonicalAssembler()
        val result = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                IrAnalysisSnapshot(
                    inputDirs = emptyList(),
                    nodes = emptyList(),
                    edges = emptyList(),
                    designElements = listOf(
                        DesignElementSnapshot(
                            tag = "query",
                            packageName = "order.read",
                            name = "FindOrder",
                            description = "find order",
                        ),
                        DesignElementSnapshot(
                            tag = "q" + "ry",
                            packageName = "order.read",
                            name = "LegacyFindOrder",
                            description = "legacy find order",
                        ),
                    ),
                ),
            ),
        )

        val board = requireNotNull(result.model.drawingBoard)
        assertEquals(listOf("FindOrder"), board.elements.map { it.name })
        assertEquals(listOf("query"), board.elementsByTag.keys.toList())
    }

    @Test
    fun `drawing board accepts integration event elements`() {
        val assembler = DefaultCanonicalAssembler()

        val result = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                IrAnalysisSnapshot(
                    inputDirs = emptyList(),
                    nodes = emptyList(),
                    edges = emptyList(),
                    designElements = listOf(
                        DesignElementSnapshot(
                            tag = "integration_event",
                            packageName = "order.events",
                            name = "OrderCreated",
                            description = "order created integration event",
                            requestFields = listOf(
                                DesignFieldSnapshot(name = "orderId", type = "Long"),
                                DesignFieldSnapshot(name = "buyerId", type = "Long"),
                            ),
                            role = "inbound",
                            eventName = "order.created",
                        ),
                    ),
                ),
            ),
        )

        val board = requireNotNull(result.model.drawingBoard)
        val integrationEvent = board.elements.single()
        assertEquals("integration_event", integrationEvent.tag)
        assertEquals("inbound", integrationEvent.role)
        assertEquals("order.created", integrationEvent.eventName)
        assertEquals(listOf("orderId", "buyerId"), integrationEvent.requestFields.map { it.name })
        assertEquals(listOf("integration_event"), board.elementsByTag.keys.toList())
    }

    @Test
    fun `leaves drawing board null when no supported design elements exist`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                IrAnalysisSnapshot(
                    inputDirs = listOf("app/build/cap4k-code-analysis"),
                    nodes = emptyList(),
                    edges = emptyList(),
                    designElements = listOf(
                        DesignElementSnapshot(
                            tag = "evt",
                            packageName = "order.events",
                            name = "OrderCreated",
                            description = "order created",
                        ),
                        DesignElementSnapshot(
                            tag = "unknown",
                            packageName = "order.events",
                            name = "OrderArchived",
                            description = "order archived",
                        ),
                    ),
                ),
            ),
        ).model

        assertNull(model.drawingBoard)
    }

    @Test
    fun `keeps analysis graph null when ir snapshot is absent`() {
        val assembler = DefaultCanonicalAssembler()
        val model = assembler.assemble(config = baseConfig(), snapshots = emptyList()).model
        assertNull(model.analysisGraph)
    }

    @Test
    fun `assembly carries shared enum definitions and db field enum metadata`() {
        val assembler = DefaultCanonicalAssembler()

        val result = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                EnumManifestSnapshot(
                    definitions = listOf(
                        SharedEnumDefinition(
                            typeName = "Status",
                            packageName = "shared",
                            items = listOf(
                                EnumItemModel(0, "DRAFT", "Draft"),
                                EnumItemModel(1, "PUBLISHED", "Published"),
                            ),
                        )
                    )
                ),
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot(
                                    name = "id",
                                    dbType = "BIGINT",
                                    kotlinType = "Long",
                                    nullable = false,
                                ),
                                DbColumnSnapshot(
                                    name = "status",
                                    dbType = "INT",
                                    kotlinType = "Int",
                                    nullable = false,
                                    typeBinding = "Status",
                                ),
                                DbColumnSnapshot(
                                    name = "visibility",
                                    dbType = "INT",
                                    kotlinType = "Int",
                                    nullable = false,
                                    typeBinding = "VideoPostVisibility",
                                    enumItems = listOf(
                                        EnumItemModel(0, "HIDDEN", "Hidden"),
                                        EnumItemModel(1, "PUBLIC", "Public"),
                                    ),
                                ),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        )
                    )
                )
            )
        ).model

        assertEquals(listOf("Status"), result.sharedEnums.map { it.typeName })
        val entity = result.entities.single()
        assertEquals("Status", entity.fields.first { it.name == "status" }.typeBinding)
        assertEquals("VideoPostVisibility", entity.fields.first { it.name == "visibility" }.typeBinding)
    }

    @Test
    fun `assembler routes aggregate canonical packages through custom artifact layout`() {
        val result = DefaultCanonicalAssembler().assemble(
            config = baseAggregateConfig(
                artifactLayout = ArtifactLayoutConfig(
                    aggregate = PackageLayout("domain.model"),
                    aggregateSchema = PackageLayout("domain.meta"),
                    aggregateRepository = PackageLayout("adapter.persistence.repositories"),
                    aggregateSharedEnum = PackageLayout(
                        packageRoot = "domain.model",
                        defaultPackage = "shared",
                        packageSuffix = "enums",
                    ),
                )
            ),
            snapshots = listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "user_profile",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "user_message",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot(
                                    name = "author_id",
                                    dbType = "BIGINT",
                                    kotlinType = "Long",
                                    nullable = false,
                                    referenceTable = "user_profile",
                                ),
                                DbColumnSnapshot(
                                    name = "status",
                                    dbType = "INT",
                                    kotlinType = "Int",
                                    nullable = false,
                                    typeBinding = "MessageStatus",
                                ),
                                DbColumnSnapshot(
                                    name = "kind",
                                    dbType = "INT",
                                    kotlinType = "Int",
                                    nullable = false,
                                    typeBinding = "MessageKind",
                                ),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                    )
                ),
                EnumManifestSnapshot(
                    definitions = listOf(
                        SharedEnumDefinition(
                            typeName = "MessageStatus",
                            packageName = "shared",
                            items = listOf(EnumItemModel(0, "DRAFT", "Draft")),
                        ),
                        SharedEnumDefinition(
                            typeName = "MessageKind",
                            packageName = "com.external.enums",
                            items = listOf(EnumItemModel(0, "DIRECT", "Direct")),
                        )
                    )
                )
            )
        ).model

        val messageEntity = result.entities.single { it.name == "UserMessage" }
        val messageSchema = result.schemas.single { it.name == "SUserMessage" }
        val messageRepository = result.repositories.single { it.name == "UserMessageRepository" }
        val relation = result.aggregateRelations.single {
            it.ownerEntityName == "UserMessage" && it.targetEntityName == "UserProfile"
        }
        val messageJpa = result.aggregateEntityJpa.single { it.entityName == "UserMessage" }

        assertEquals("com.acme.demo.domain.model.user_message", messageEntity.packageName)
        assertEquals("com.acme.demo.domain.meta.user_message", messageSchema.packageName)
        assertEquals("com.acme.demo.adapter.persistence.repositories", messageRepository.packageName)
        assertEquals("com.acme.demo.domain.model.user_message", relation.ownerEntityPackageName)
        assertEquals("com.acme.demo.domain.model.user_profile", relation.targetEntityPackageName)
        assertEquals(
            "com.acme.demo.domain.model.shared.enums.MessageStatus",
            messageJpa.columns.single { it.fieldName == "status" }.converterTypeFqn,
        )
        assertEquals(
            "com.external.enums.MessageKind",
            messageJpa.columns.single { it.fieldName == "kind" }.converterTypeFqn,
        )
    }

    @Test
    fun `assembler records entity table and scalar column jpa metadata`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("title", "VARCHAR", "String", false),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        )
                    )
                )
            )
        )

        val entity = result.model.entities.single()
        val jpa = result.model.aggregateEntityJpa.single { it.entityName == "VideoPost" }

        assertEquals("video_post", entity.tableName)
        assertEquals(true, jpa.entityEnabled)
        assertEquals("video_post", jpa.tableName)
        assertEquals("id", jpa.columns.single { it.fieldName == "id" }.columnName)
        assertEquals(true, jpa.columns.single { it.fieldName == "id" }.isId)
        assertEquals("title", jpa.columns.single { it.fieldName == "title" }.columnName)
        assertEquals(false, jpa.columns.single { it.fieldName == "title" }.isId)
    }

    @Test
    fun `assembler only assigns converter metadata to stable enum-backed fields`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig().copy(
                typeRegistry = TypeRegistryConfig(entries = mapOf(
                    "SubmitPayload" to TypeRegistryEntry(
                        fqn = "com.acme.demo.payload.SubmitPayload",
                        converter = TypeRegistryConverter.none(),
                    )
                ))
            ),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot(
                                    name = "status",
                                    dbType = "INT",
                                    kotlinType = "Int",
                                    nullable = false,
                                    typeBinding = "Status",
                                ),
                                DbColumnSnapshot(
                                    name = "payload",
                                    dbType = "JSON",
                                    kotlinType = "String",
                                    nullable = false,
                                    typeBinding = "SubmitPayload",
                                ),
                                DbColumnSnapshot(
                                    name = "visibility",
                                    dbType = "INT",
                                    kotlinType = "Int",
                                    nullable = false,
                                    typeBinding = "Visibility",
                                    enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden")),
                                ),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        )
                    )
                ),
                EnumManifestSnapshot(
                    definitions = listOf(
                        SharedEnumDefinition(
                            typeName = "Status",
                            packageName = "shared",
                            items = listOf(EnumItemModel(0, "DRAFT", "Draft")),
                        )
                    )
                )
            )
        )

        val entityJpa = result.model.aggregateEntityJpa.single { it.entityName == "VideoPost" }

        assertEquals(
            "com.acme.demo.domain.shared.enums.Status",
            entityJpa.columns.single { it.fieldName == "status" }.converterTypeFqn
        )
        assertEquals(
            null,
            entityJpa.columns.single { it.fieldName == "payload" }.converterTypeFqn
        )
        assertEquals(
            "com.acme.demo.domain.aggregates.video_post.enums.Visibility",
            entityJpa.columns.single { it.fieldName == "visibility" }.converterTypeFqn
        )
    }

    @Test
    fun `assembler assigns converter metadata to registry backed type binding`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig().copy(
                typeRegistry = TypeRegistryConfig(entries = mapOf(
                    "UserType" to TypeRegistryEntry("com.acme.demo.domain.aggregates.user.enums.UserType"),
                ))
            ),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "user_login_log",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot(
                                    name = "user_type",
                                    dbType = "INT",
                                    kotlinType = "Int",
                                    nullable = false,
                                    typeBinding = "UserType",
                                ),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        )
                    )
                )
            )
        )

        val entityJpa = result.model.aggregateEntityJpa.single { it.entityName == "UserLoginLog" }

        assertEquals(
            "com.acme.demo.domain.aggregates.user.enums.UserType",
            entityJpa.columns.single { it.fieldName == "userType" }.converterTypeFqn,
        )
    }

    @Test
    fun `assembler fails fast when shared enum and registry use the same type binding`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                aggregateProjectConfig().copy(
                    typeRegistry = TypeRegistryConfig(entries = mapOf(
                        "Status" to TypeRegistryEntry("com.acme.demo.domain.shared.enums.StatusAlias"),
                    ))
                ),
                listOf(
                    DbSchemaSnapshot(
                        tables = listOf(
                            DbTableSnapshot(
                                tableName = "video_post",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                    DbColumnSnapshot(
                                        name = "status",
                                        dbType = "INT",
                                        kotlinType = "Int",
                                        nullable = false,
                                        typeBinding = "Status",
                                    ),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                            )
                        )
                    ),
                    EnumManifestSnapshot(
                        definitions = listOf(
                            SharedEnumDefinition(
                                typeName = "Status",
                                packageName = "shared",
                                items = listOf(EnumItemModel(0, "DRAFT", "Draft")),
                            )
                        )
                    )
                )
            )
        }

        assertTrue(error.message!!.contains("Duplicate type simple name: Status"))
    }

    @Test
    fun `assembler fails fast when enum converter ownership is ambiguous between shared and local`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                aggregateProjectConfig(),
                listOf(
                    DbSchemaSnapshot(
                        tables = listOf(
                            DbTableSnapshot(
                                tableName = "video_post",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                    DbColumnSnapshot(
                                        name = "status",
                                        dbType = "INT",
                                        kotlinType = "Int",
                                        nullable = false,
                                        typeBinding = "Status",
                                        enumItems = listOf(EnumItemModel(0, "DRAFT", "Draft")),
                                    ),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                            )
                        )
                    ),
                    EnumManifestSnapshot(
                        definitions = listOf(
                            SharedEnumDefinition(
                                typeName = "Status",
                                packageName = "shared",
                                items = listOf(EnumItemModel(0, "DRAFT", "Draft")),
                            )
                        )
                    )
                )
            )
        }

        assertTrue(error.message!!.contains("Duplicate type simple name: Status"))
    }

    @Test
    fun `assembler assigns local enum converter to reused type binding within the same aggregate`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot(
                                    name = "visibility",
                                    dbType = "INT",
                                    kotlinType = "Int",
                                    nullable = false,
                                    typeBinding = "Visibility",
                                    enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden")),
                                ),
                                DbColumnSnapshot(
                                    name = "default_visibility",
                                    dbType = "INT",
                                    kotlinType = "Int",
                                    nullable = false,
                                    typeBinding = "Visibility",
                                ),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        )
                    )
                )
            )
        )

        val entityJpa = result.model.aggregateEntityJpa.single { it.entityName == "VideoPost" }
        val expectedConverter = "com.acme.demo.domain.aggregates.video_post.enums.Visibility"

        assertEquals(
            expectedConverter,
            entityJpa.columns.single { it.fieldName == "visibility" }.converterTypeFqn
        )
        assertEquals(
            expectedConverter,
            entityJpa.columns.single { it.fieldName == "defaultVisibility" }.converterTypeFqn
        )
    }

    @Test
    fun `assembler binds aggregate local value object before shared value object with same simple name`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "content",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot(
                                    name = "publish_window",
                                    dbType = "JSON",
                                    kotlinType = "String",
                                    nullable = false,
                                    typeBinding = "PublishWindow",
                                ),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        )
                    )
                ),
                ValueObjectManifestSnapshot(
                    valueObjects = listOf(
                        ValueObjectModel(
                            name = "PublishWindow",
                            packageName = "com.acme.demo.domain.shared.values",
                            scope = ValueObjectScope.SHARED,
                        ),
                        ValueObjectModel(
                            name = "PublishWindow",
                            packageName = "com.acme.demo.domain.aggregates.content.values",
                            scope = ValueObjectScope.AGGREGATE,
                            aggregate = "Content",
                        ),
                    )
                )
            )
        )

        val entityJpa = result.model.aggregateEntityJpa.single { it.entityName == "Content" }

        assertEquals(
            "com.acme.demo.domain.aggregates.content.values.PublishWindow",
            entityJpa.columns.single { it.fieldName == "publishWindow" }.converterTypeFqn,
        )
        assertEquals(
            "com.acme.demo.domain.aggregates.content.values.PublishWindow.Converter",
            entityJpa.columns.single { it.fieldName == "publishWindow" }.converterClassFqn,
        )
    }

    @Test
    fun `assembler binds shared value object without registry entry`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "content",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot(
                                    name = "publish_window",
                                    dbType = "JSON",
                                    kotlinType = "String",
                                    nullable = false,
                                    typeBinding = "PublishWindow",
                                ),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        )
                    )
                ),
                ValueObjectManifestSnapshot(
                    valueObjects = listOf(
                        ValueObjectModel(
                            name = "PublishWindow",
                            packageName = "com.acme.demo.domain.shared.values",
                            scope = ValueObjectScope.SHARED,
                        ),
                    )
                )
            )
        )

        val entityJpa = result.model.aggregateEntityJpa.single { it.entityName == "Content" }

        assertEquals(
            "com.acme.demo.domain.shared.values.PublishWindow",
            entityJpa.columns.single { it.fieldName == "publishWindow" }.converterTypeFqn,
        )
        assertEquals(
            "com.acme.demo.domain.shared.values.PublishWindow.Converter",
            entityJpa.columns.single { it.fieldName == "publishWindow" }.converterClassFqn,
        )
    }

    @Test
    fun `assembler binds child entity field to aggregate root local value object`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "content",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            aggregateRoot = true,
                        ),
                        DbTableSnapshot(
                            tableName = "content_schedule",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot(
                                    name = "content_id",
                                    dbType = "BIGINT",
                                    kotlinType = "Long",
                                    nullable = false,
                                    referenceTable = "content",
                                ),
                                DbColumnSnapshot(
                                    name = "publish_window",
                                    dbType = "JSON",
                                    kotlinType = "String",
                                    nullable = false,
                                    typeBinding = "PublishWindow",
                                ),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            parentTable = "content",
                            aggregateRoot = false,
                            valueObject = true,
                        ),
                    )
                ),
                ValueObjectManifestSnapshot(
                    valueObjects = listOf(
                        ValueObjectModel(
                            name = "PublishWindow",
                            packageName = "com.acme.demo.domain.aggregates.content.values",
                            scope = ValueObjectScope.AGGREGATE,
                            aggregate = "Content",
                        ),
                    )
                )
            )
        )

        val entityJpa = result.model.aggregateEntityJpa.single { it.entityName == "ContentSchedule" }

        assertEquals(
            "com.acme.demo.domain.aggregates.content.values.PublishWindow",
            entityJpa.columns.single { it.fieldName == "publishWindow" }.converterTypeFqn,
        )
        assertEquals(
            "com.acme.demo.domain.aggregates.content.values.PublishWindow.Converter",
            entityJpa.columns.single { it.fieldName == "publishWindow" }.converterClassFqn,
        )
    }

    @Test
    fun `assembler fails fast on ambiguous shared value object type override`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            assemble(
                valueObjects = ValueObjectManifestSnapshot(
                    valueObjects = listOf(
                        ValueObjectModel(
                            name = "PublishWindow",
                            packageName = "content.values.primary",
                            scope = ValueObjectScope.SHARED,
                        ),
                        ValueObjectModel(
                            name = "PublishWindow",
                            packageName = "content.values.secondary",
                            scope = ValueObjectScope.SHARED,
                        ),
                    )
                )
            )
        }

        assertTrue(error.message!!.contains("Ambiguous value object type override: PublishWindow"))
    }

    @Test
    fun `assembler fails fast on ambiguous aggregate local value object type override`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                aggregateProjectConfig(),
                listOf(
                    DbSchemaSnapshot(
                        tables = listOf(
                            DbTableSnapshot(
                                tableName = "content",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                    DbColumnSnapshot(
                                        name = "publish_window",
                                        dbType = "JSON",
                                        kotlinType = "String",
                                        nullable = false,
                                        typeBinding = "PublishWindow",
                                    ),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                            )
                        )
                    ),
                    ValueObjectManifestSnapshot(
                        valueObjects = listOf(
                            ValueObjectModel(
                                name = "PublishWindow",
                                packageName = "content.values.primary",
                                scope = ValueObjectScope.AGGREGATE,
                                aggregate = "Content",
                            ),
                            ValueObjectModel(
                                name = "PublishWindow",
                                packageName = "content.values.secondary",
                                scope = ValueObjectScope.AGGREGATE,
                                aggregate = "Content",
                            ),
                        )
                    )
                )
            )
        }

        assertTrue(error.message!!.contains("Ambiguous value object type override: PublishWindow"))
    }

    @Test
    fun `assembler records explicit aggregate persistence field controls`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "@AggregateRoot=true;",
                            columns = listOf(
                                DbColumnSnapshot(
                                    "id",
                                    "BIGINT",
                                    "Long",
                                    false,
                                    isPrimaryKey = true,
                                    generatedValueStrategy = "IDENTITY"
                                ),
                                DbColumnSnapshot("version", "BIGINT", "Long", false, version = true),
                                DbColumnSnapshot("created_by", "VARCHAR", "String", false, insertable = false),
                                DbColumnSnapshot("updated_by", "VARCHAR", "String", false, updatable = false),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        )
                    )
                )
            )
        )

        val entity = result.model.entities.single()
        val controls = result.model.aggregatePersistenceFieldControls

        assertEquals(entity.packageName, controls.single { it.fieldName == "id" }.entityPackageName)
        assertEquals(entity.fields.single { it.name == "id" }.name, controls.single { it.columnName == "id" }.fieldName)
        assertEquals(entity.fields.single { it.name == "version" }.name, controls.single { it.columnName == "version" }.fieldName)
        assertEquals(entity.fields.single { it.name == "createdBy" }.name, controls.single { it.columnName == "created_by" }.fieldName)
        assertEquals(entity.fields.single { it.name == "updatedBy" }.name, controls.single { it.columnName == "updated_by" }.fieldName)
        assertEquals("IDENTITY", controls.single { it.fieldName == "id" }.generatedValueStrategy)
        assertEquals(true, controls.single { it.fieldName == "version" }.version)
        assertEquals(false, controls.single { it.fieldName == "createdBy" }.insertable)
        assertEquals(false, controls.single { it.fieldName == "updatedBy" }.updatable)
    }

    @Test
    fun `assembler does not infer persistence field controls when source is silent`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "@AggregateRoot=true;",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("version", "BIGINT", "Long", false),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        )
                    )
                )
            )
        )

        assertEquals(emptyList<com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceFieldControl>(), result.model.aggregatePersistenceFieldControls)
    }

    @Test
    fun `assembler preserves explicit false version persistence control`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "@AggregateRoot=true;",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("version", "BIGINT", "Long", false, version = false),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        )
                    )
                )
            )
        )

        val controls = result.model.aggregatePersistenceFieldControls

        assertEquals(1, controls.size)
        assertEquals(result.model.entities.single().packageName, controls.single().entityPackageName)
        assertEquals(false, controls.single().version)
        assertEquals("version", controls.single().fieldName)
    }

    @Test
    fun `assembler records explicit managed and exposed persistence controls`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "@AggregateRoot=true;",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("created_by", "VARCHAR", "String", false, managed = true),
                                DbColumnSnapshot("display_name", "VARCHAR", "String", false, exposed = true),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        )
                    )
                )
            )
        )

        val controls = result.model.aggregatePersistenceFieldControls

        assertEquals(2, controls.size)
        assertEquals(setOf("createdBy", "displayName"), controls.map { it.fieldName }.toSet())
        assertEquals(setOf("created_by", "display_name"), controls.map { it.columnName }.toSet())
    }

    @Test
    fun `db inherited columns remain canonical fields with inherited flag`() {
        val snapshot = DbSchemaSnapshot(
            tables = listOf(
                DbTableSnapshot(
                    tableName = "content",
                    comment = "",
                    columns = listOf(
                        DbColumnSnapshot("id", "VARCHAR", "String", false, isPrimaryKey = true),
                        DbColumnSnapshot("created_at", "TIMESTAMP", "java.time.Instant", false, inherited = true, managed = true),
                    ),
                    primaryKey = listOf("id"),
                    uniqueConstraints = emptyList(),
                )
            )
        )

        val model = DefaultCanonicalAssembler().assemble(baseConfig(), listOf(snapshot)).model
        val field = model.entities.single().fields.single { it.name == "createdAt" }

        assertEquals(true, field.inherited)
    }

    @Test
    fun `assembler records explicit aggregate provider persistence controls`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "@AggregateRoot=true;",
                            columns = listOf(
                                DbColumnSnapshot(
                                    "id",
                                    "BIGINT",
                                    "Long",
                                    false,
                                    isPrimaryKey = true,
                                    generatedValueStrategy = "IDENTITY"
                                ),
                                DbColumnSnapshot("version", "BIGINT", "Long", false, version = true),
                                DbColumnSnapshot("deleted", "INT", "Int", false, deleted = true),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            dynamicInsert = true,
                            dynamicUpdate = true,
                        )
                    )
                )
            )
        )

        val control = result.model.aggregatePersistenceProviderControls.single()

        assertEquals("VideoPost", control.entityName)
        assertEquals(true, control.dynamicInsert)
        assertEquals(true, control.dynamicUpdate)
        assertEquals("deleted", control.softDeleteColumn)
        assertEquals("id", control.idFieldName)
        assertEquals("version", control.versionFieldName)
    }

    @Test
    fun `assembler does not infer provider controls when source is silent`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "@AggregateRoot=true;",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("title", "VARCHAR", "String", false),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        )
                    )
                )
            )
        )

        assertEquals(emptyList<com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceProviderControl>(), result.model.aggregatePersistenceProviderControls)
    }

    @Test
    fun `assembler infers provider control for explicit version column without provider specific settings`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "@AggregateRoot=true;",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("version", "BIGINT", "Long", false, version = true),
                                DbColumnSnapshot("slug", "VARCHAR", "String", false),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        )
                    )
                )
            )
        )

        val control = result.model.aggregatePersistenceProviderControls.single()
        assertEquals("VideoPost", control.entityName)
        assertEquals("id", control.idFieldName)
        assertEquals("version", control.versionFieldName)
        assertEquals(null, control.softDeleteColumn)
        assertEquals(null, control.dynamicInsert)
        assertEquals(null, control.dynamicUpdate)
    }

    @Test
    fun `aggregate root id defaults to strong id metadata and field type`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "uuid7"),
            tables = listOf(
                table(
                    name = "content",
                    columns = listOf(
                        column("id", "VARCHAR", "String", false, primaryKey = true),
                        column("title", "VARCHAR", "String", false),
                    ),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        val entity = result.model.entities.single()
        val strongId = result.model.strongIds.single()

        assertEquals("ContentId", strongId.typeName)
        assertEquals("com.acme.demo.domain.aggregates.content", strongId.packageName)
        assertEquals("String", strongId.valueType)
        assertEquals(StrongIdKind.AGGREGATE_ROOT, strongId.kind)
        assertEquals("Content", strongId.ownerAggregateName)
        assertEquals("com.acme.demo.domain.aggregates.content", strongId.ownerAggregatePackageName)
        assertEquals("ContentId", entity.idField.type)
        assertEquals("ContentId", entity.fields.single { it.name == "id" }.type)
        assertEquals("ContentId", result.model.repositories.single().idType)
        assertTrue(result.model.aggregateIdPolicyControls.isEmpty())
    }

    @Test
    fun `ref aggregate resolves to referenced aggregate id type`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "uuid7"),
            tables = listOf(
                table(
                    name = "media_processing_task",
                    columns = listOf(column("id", "VARCHAR", "String", false, primaryKey = true)),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                ),
                table(
                    name = "content",
                    columns = listOf(
                        column("id", "VARCHAR", "String", false, primaryKey = true),
                        column(
                            "media_processing_task_id",
                            "VARCHAR",
                            "String",
                            true,
                            refAggregate = "MediaProcessingTask",
                        ),
                    ),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        val content = result.model.entities.single { it.name == "Content" }

        assertEquals("MediaProcessingTaskId", content.fields.single { it.name == "mediaProcessingTaskId" }.type)
        assertEquals(
            listOf("MediaProcessingTaskId", "ContentId"),
            result.model.strongIds.map { it.typeName },
        )
    }

    @Test
    fun `missing ref aggregate fails fast`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            assembleAggregate(
                config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "uuid7"),
                tables = listOf(
                    table(
                        name = "content",
                        columns = listOf(
                            column("id", "VARCHAR", "String", false, primaryKey = true),
                            column(
                                "media_processing_task_id",
                                "VARCHAR",
                                "String",
                                true,
                                refAggregate = "MediaProcessingTask",
                            ),
                        ),
                        primaryKey = listOf("id"),
                        aggregateRoot = true,
                    )
                )
            )
        }

        assertTrue(error.message!!.contains("@RefAggregate=MediaProcessingTask does not match a generated aggregate root"))
    }

    @Test
    fun `ref aggregate to primitive generated root fails fast`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            assembleAggregate(
                config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "uuid7"),
                tables = listOf(
                    table(
                        name = "media_processing_task",
                        columns = listOf(
                            column(
                                "id",
                                "BIGINT",
                                "Long",
                                false,
                                primaryKey = true,
                                generatedValueStrategy = "identity",
                            )
                        ),
                        primaryKey = listOf("id"),
                        aggregateRoot = true,
                    ),
                    table(
                        name = "content",
                        columns = listOf(
                            column("id", "VARCHAR", "String", false, primaryKey = true),
                            column(
                                "media_processing_task_id",
                                "BIGINT",
                                "Long",
                                true,
                                refAggregate = "MediaProcessingTask",
                            ),
                        ),
                        primaryKey = listOf("id"),
                        aggregateRoot = true,
                    )
                )
            )
        }

        assertTrue(error.message!!.contains("@RefAggregate=MediaProcessingTask does not match a generated aggregate root"))
    }

    @Test
    fun `ref id creates shared reference strong id without aggregate`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "uuid7"),
            tables = listOf(
                table(
                    name = "content",
                    columns = listOf(
                        column("id", "VARCHAR", "String", false, primaryKey = true),
                        column("author_id", "VARCHAR", "String", false, refId = "AuthorId"),
                    ),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        val content = result.model.entities.single { it.name == "Content" }
        val authorId = result.model.strongIds.single { it.typeName == "AuthorId" }

        assertEquals("AuthorId", content.fields.single { it.name == "authorId" }.type)
        assertEquals("com.acme.demo.domain.shared.ids", authorId.packageName)
        assertEquals("String", authorId.valueType)
        assertEquals(StrongIdKind.REFERENCE, authorId.kind)
        assertNull(authorId.ownerAggregateName)
        assertNull(authorId.ownerAggregatePackageName)
        assertTrue(result.model.entities.none { it.name == "Author" })
    }

    @Test
    fun `ref aggregate and ref id cannot share a column in canonical metadata`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            assembleAggregate(
                config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "uuid7"),
                tables = listOf(
                    table(
                        name = "content",
                        columns = listOf(
                            column("id", "VARCHAR", "String", false, primaryKey = true),
                            column(
                                "task_id",
                                "VARCHAR",
                                "String",
                                false,
                                refAggregate = "MediaProcessingTask",
                                refId = "MediaProcessingTaskId",
                            ),
                        ),
                        primaryKey = listOf("id"),
                        aggregateRoot = true,
                    )
                )
            )
        }

        assertEquals(
            "conflicting @RefAggregate and @RefId annotations on the same column metadata.",
            error.message,
        )
    }

    @Test
    fun `design command can declare generated reference strong id type`() {
        val result = DefaultCanonicalAssembler().assemble(
            config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "uuid7"),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "command",
                            packageName = "content",
                            name = "CreateContent",
                            description = "create content",
                            aggregates = emptyList(),
                            requestFields = listOf(FieldModel("authorId", "AuthorId")),
                            responseFields = emptyList(),
                        )
                    )
                ),
                DbSchemaSnapshot(
                    tables = listOf(
                        table(
                            name = "content",
                            columns = listOf(
                                column("id", "VARCHAR", "String", false, primaryKey = true),
                                column("author_id", "VARCHAR", "String", false, refId = "AuthorId"),
                            ),
                            primaryKey = listOf("id"),
                            aggregateRoot = true,
                        )
                    )
                ),
            ),
        )

        val command = result.model.commands.single { it.typeName == "CreateContentCmd" }

        assertEquals("AuthorId", command.requestFields.single { it.name == "authorId" }.type)
        assertEquals("AuthorId", result.model.strongIds.single { it.typeName == "AuthorId" }.typeName)
        assertEquals(1, result.model.commands.size)
    }

    @Test
    fun `default uuid7 strategy does not emit primitive aggregate id policy`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "uuid7"),
            tables = listOf(
                table(
                    name = "user_message",
                    columns = listOf(column("id", "UUID", "UUID", false, primaryKey = true)),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        val resolved = result.model.aggregateSpecialFieldResolvedPolicies.single()

        assertTrue(result.model.aggregateIdPolicyControls.isEmpty())
        assertEquals("UserMessageId", result.model.entities.single().idField.type)
        assertEquals(SpecialFieldSource.DSL_DEFAULT, resolved.id.source)
    }

    @Test
    fun `non root silent primitive ids keep non strong id policy semantics`() {
        listOf("identity", "uuid7").forEach { defaultStrategy ->
            val result = assembleAggregate(
                config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = defaultStrategy),
                tables = listOf(
                    table(
                        name = "video",
                        columns = listOf(column("id", "VARCHAR", "String", false, primaryKey = true)),
                        primaryKey = listOf("id"),
                        aggregateRoot = true,
                    ),
                    table(
                        name = "video_file",
                        columns = listOf(
                            column("id", "BIGINT", "Long", false, primaryKey = true),
                            column("video_id", "VARCHAR", "String", false, referenceTable = "video"),
                        ),
                        primaryKey = listOf("id"),
                        aggregateRoot = false,
                        parentTable = "video",
                    )
                )
            )

            val child = result.model.entities.single { it.name == "VideoFile" }
            val policy = result.model.aggregateSpecialFieldResolvedPolicies.single { it.entityName == "VideoFile" }

            assertEquals("Long", child.idField.type)
            assertEquals("identity", policy.id.strategy)
            assertEquals(AggregateIdPolicyKind.DATABASE_SIDE, policy.id.kind)
            assertEquals(SpecialFieldWritePolicy.READ_ONLY, policy.id.writePolicy)
            assertEquals(SpecialFieldSource.DSL_DEFAULT, policy.id.source)
        }
    }

    @Test
    fun `generated default aggregate root ignores primitive identity default for strong id policy`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "identity"),
            tables = listOf(
                table(
                    name = "content",
                    columns = listOf(
                        column("id", "VARCHAR", "String", false, primaryKey = true),
                        column("title", "VARCHAR", "String", false),
                    ),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        val entity = result.model.entities.single()
        val resolved = result.model.aggregateSpecialFieldResolvedPolicies.single()

        assertEquals("ContentId", entity.idField.type)
        assertEquals("ContentId", entity.fields.single { it.name == "id" }.type)
        assertEquals("ContentId", result.model.strongIds.single().typeName)
        assertEquals("uuid7", resolved.id.strategy)
        assertEquals(AggregateIdPolicyKind.APPLICATION_SIDE, resolved.id.kind)
        assertEquals(SpecialFieldWritePolicy.CREATE_ONLY, resolved.id.writePolicy)
        assertEquals(SpecialFieldSource.DSL_DEFAULT, resolved.id.source)
        assertTrue(result.model.aggregateIdPolicyControls.isEmpty())
    }

    @Test
    fun `explicit generated aggregate root keeps primitive id and does not emit strong id metadata`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "uuid7"),
            tables = listOf(
                table(
                    name = "audit_log",
                    columns = listOf(
                        column(
                            "id",
                            "BIGINT",
                            "Long",
                            false,
                            primaryKey = true,
                            generatedValueStrategy = "identity",
                        ),
                        column("message", "VARCHAR", "String", false),
                    ),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        val entity = result.model.entities.single()

        assertEquals("Long", entity.idField.type)
        assertEquals("Long", result.model.repositories.single().idType)
        assertTrue(result.model.strongIds.none { it.typeName == "AuditLogId" })
    }

    @Test
    fun `application-side id resolves create-only write policy`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(
                idDefaultStrategy = "uuid7",
                deletedDefaultColumn = "",
                versionDefaultColumn = "",
                managedDefaultColumns = emptyList(),
            ),
            tables = listOf(
                table(
                    name = "category",
                    columns = listOf(column("id", "UUID", "UUID", false, primaryKey = true)),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        val policy = result.model.aggregateSpecialFieldResolvedPolicies.single()

        assertEquals(SpecialFieldWritePolicy.CREATE_ONLY, policy.id.writePolicy)
    }

    @Test
    fun `default uuid7 strategy allows primitive source id column behind strong id field`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "uuid7"),
            tables = listOf(
                table(
                    name = "video",
                    columns = listOf(column("id", "BIGINT", "Long", false, primaryKey = true)),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        assertEquals("VideoId", result.model.entities.single().idField.type)
        assertTrue(result.model.aggregateIdPolicyControls.isEmpty())
    }

    @Test
    fun `aggregate projection only does not validate write model id strategy`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "uuid7").copy(
                generators = mapOf("aggregate-projection" to GeneratorConfig()),
            ),
            tables = listOf(
                table(
                    name = "video",
                    columns = listOf(column("id", "BIGINT", "Long", false, primaryKey = true)),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        assertEquals(listOf("Video"), result.model.entities.map { it.name })
        assertTrue(result.model.aggregateSpecialFieldResolvedPolicies.isEmpty())
        assertTrue(result.model.aggregateIdPolicyControls.isEmpty())
    }

    @Test
    fun `generated value marker uses DSL default strategy with DB explicit source`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "snowflake-long"),
            tables = listOf(
                table(
                    name = "audit_log",
                    columns = listOf(
                        column("id", "BIGINT", "Long", false, primaryKey = true, generatedValueDeclared = true),
                    ),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        val control = result.model.aggregateIdPolicyControls.single()
        val policy = result.model.aggregateSpecialFieldResolvedPolicies.single()

        assertEquals("snowflake-long", control.strategy)
        assertEquals(AggregateIdPolicyKind.APPLICATION_SIDE, control.kind)
        assertEquals("snowflake-long", policy.id.strategy)
        assertEquals(SpecialFieldSource.DB_EXPLICIT, policy.id.source)
    }

    @Test
    fun `mixed id strategies across entities are allowed`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "uuid7"),
            tables = listOf(
                table(
                    name = "video",
                    columns = listOf(column("id", "UUID", "UUID", false, primaryKey = true)),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                ),
                table(
                    name = "video_file",
                    columns = listOf(
                        column(
                            "id",
                            "BIGINT",
                            "Long",
                            false,
                            primaryKey = true,
                            generatedValueStrategy = "identity",
                        ),
                        column("video_id", "UUID", "UUID", false, referenceTable = "video"),
                    ),
                    primaryKey = listOf("id"),
                    aggregateRoot = false,
                    parentTable = "video",
                )
            )
        )

        assertTrue(result.model.aggregateIdPolicyControls.none { it.entityName == "Video" })
        assertEquals("identity", result.model.aggregateIdPolicyControls.single { it.entityName == "VideoFile" }.strategy)
    }

    @Test
    fun `missing DSL deleted and version default columns do not fail and stay disabled`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(
                idDefaultStrategy = "uuid7",
                deletedDefaultColumn = "deleted",
                versionDefaultColumn = "version",
            ),
            tables = listOf(
                table(
                    name = "video",
                    columns = listOf(column("id", "UUID", "UUID", false, primaryKey = true)),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        val policy = result.model.aggregateSpecialFieldResolvedPolicies.single()

        assertEquals(false, policy.deleted.enabled)
        assertEquals(false, policy.version.enabled)
        assertEquals(SpecialFieldSource.NONE, policy.deleted.source)
        assertEquals(SpecialFieldSource.NONE, policy.version.source)
    }

    @Test
    fun `missing managed defaults on entity keep protected id managed and add no extra managed fields`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(
                idDefaultStrategy = "identity",
                deletedDefaultColumn = "",
                versionDefaultColumn = "",
                managedDefaultColumns = listOf("create_user_id"),
            ),
            tables = listOf(
                table(
                    name = "audit_log",
                    columns = listOf(
                        column(
                            "id",
                            "BIGINT",
                            "Long",
                            false,
                            primaryKey = true,
                            generatedValueDeclared = true,
                            generatedValueStrategy = "identity",
                        ),
                    ),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        val policy = result.model.aggregateSpecialFieldResolvedPolicies.single()

        assertEquals(listOf("id"), policy.managedFields.map { it.columnName })
        assertEquals(emptyList<String>(), policy.managedFields.filterNot { it.columnName == "id" }.map { it.columnName })
    }

    @Test
    fun `non protected managed column becomes read only and is removed from update write surface`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(
                idDefaultStrategy = "identity",
                deletedDefaultColumn = "",
                versionDefaultColumn = "",
                managedDefaultColumns = emptyList(),
            ),
            tables = listOf(
                table(
                    name = "video_post",
                    columns = listOf(
                        column(
                            name = "id",
                            dbType = "BIGINT",
                            kotlinType = "Long",
                            nullable = false,
                            primaryKey = true,
                            generatedValueDeclared = true,
                            generatedValueStrategy = "identity",
                        ),
                        column("created_by", "VARCHAR", "String", false, managed = true),
                        column("title", "VARCHAR", "String", false),
                    ),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        val policy = result.model.aggregateSpecialFieldResolvedPolicies.single()
        val createdByPolicy = policy.managedFields.single { it.columnName == "created_by" }

        assertEquals(SpecialFieldWritePolicy.READ_ONLY, createdByPolicy.writePolicy)
        assertEquals(listOf("id", "created_by"), policy.managedFields.map { it.columnName })
        assertEquals(listOf("title"), policy.writeSurface.createAllowedFields)
        assertEquals(listOf("title"), policy.writeSurface.updateAllowedFields)
    }

    @Test
    fun `non protected exposed column overrides dsl managed default and reopens write surface`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(
                idDefaultStrategy = "identity",
                deletedDefaultColumn = "",
                versionDefaultColumn = "",
                managedDefaultColumns = listOf("created_by"),
            ),
            tables = listOf(
                table(
                    name = "video_post",
                    columns = listOf(
                        column(
                            name = "id",
                            dbType = "BIGINT",
                            kotlinType = "Long",
                            nullable = false,
                            primaryKey = true,
                            generatedValueDeclared = true,
                            generatedValueStrategy = "identity",
                        ),
                        column("created_by", "VARCHAR", "String", false, exposed = true),
                        column("title", "VARCHAR", "String", false),
                    ),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        val policy = result.model.aggregateSpecialFieldResolvedPolicies.single()

        assertEquals(listOf("id"), policy.managedFields.map { it.columnName })
        assertEquals(listOf("createdBy", "title"), policy.writeSurface.createAllowedFields)
        assertEquals(listOf("createdBy", "title"), policy.writeSurface.updateAllowedFields)
    }

    @Test
    fun `explicit deleted marker overrides DSL default column name`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(
                idDefaultStrategy = "snowflake-long",
                deletedDefaultColumn = "deleted",
            ),
            tables = listOf(
                table(
                    name = "video_post",
                    columns = listOf(
                        column(name = "id", dbType = "BIGINT", kotlinType = "Long", nullable = false, primaryKey = true),
                        column(name = "is_deleted", dbType = "INT", kotlinType = "Int", nullable = false, deleted = true),
                        column(name = "deleted", dbType = "INT", kotlinType = "Int", nullable = false),
                    ),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        val policy = result.model.aggregateSpecialFieldResolvedPolicies.single()
        val providerControl = result.model.aggregatePersistenceProviderControls.single()

        assertEquals(true, policy.deleted.enabled)
        assertEquals("isDeleted", policy.deleted.fieldName)
        assertEquals("is_deleted", policy.deleted.columnName)
        assertEquals(SpecialFieldSource.DB_EXPLICIT, policy.deleted.source)
        assertEquals("is_deleted", providerControl.softDeleteColumn)
    }

    @Test
    fun `identity id remains database identity without explicit strategy`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "uuid7"),
            tables = listOf(
                table(
                    name = "audit_log",
                    columns = listOf(
                        column(
                            name = "id",
                            dbType = "BIGINT",
                            kotlinType = "Long",
                            nullable = false,
                            primaryKey = true,
                            generatedValueStrategy = "identity",
                        )
                    ),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        val control = result.model.aggregateIdPolicyControls.single()
        val policy = result.model.aggregateSpecialFieldResolvedPolicies.single()

        assertEquals("identity", control.strategy)
        assertEquals(AggregateIdPolicyKind.DATABASE_SIDE, control.kind)
        assertEquals(SpecialFieldSource.DB_EXPLICIT, policy.id.source)
    }

    @Test
    fun `exposed on resolved version field fails fast`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            assembleAggregate(
                config = projectConfigWithSpecialFieldDefaults(
                    idDefaultStrategy = "identity",
                    deletedDefaultColumn = "",
                    versionDefaultColumn = "",
                    managedDefaultColumns = emptyList(),
                ),
                tables = listOf(
                    table(
                        name = "category",
                        columns = listOf(
                            column(
                                "id",
                                "BIGINT",
                                "Long",
                                false,
                                primaryKey = true,
                                generatedValueDeclared = true,
                                generatedValueStrategy = "identity",
                            ),
                            column("version", "BIGINT", "Long", false, version = true, exposed = true),
                        ),
                        primaryKey = listOf("id"),
                        aggregateRoot = true,
                    )
                )
            )
        }

        assertEquals("@Exposed cannot be applied to protected special field: category.version", error.message)
    }

    @Test
    fun `assembler fails fast when generated value marker is declared on a non id column`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "uuid7"),
                listOf(
                    DbSchemaSnapshot(
                        tables = listOf(
                            DbTableSnapshot(
                                tableName = "audit_log",
                                comment = "@AggregateRoot=true;",
                                columns = listOf(
                                    DbColumnSnapshot("id", "UUID", "UUID", false, isPrimaryKey = true),
                                    DbColumnSnapshot(
                                        "created_at",
                                        "TIMESTAMP",
                                        "java.time.Instant",
                                        false,
                                        generatedValueDeclared = true,
                                    ),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                            )
                        )
                    )
                )
            )
        }

        assertEquals("generated value annotation can only be declared on id column: audit_log.created_at", error.message)
    }

    @Test
    fun `assembler fails fast when multiple deleted columns are marked explicitly`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "snowflake-long"),
                listOf(
                    DbSchemaSnapshot(
                        tables = listOf(
                            DbTableSnapshot(
                                tableName = "video_post",
                                comment = "@AggregateRoot=true;",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                    DbColumnSnapshot("deleted", "INT", "Int", false, deleted = true),
                                    DbColumnSnapshot("is_deleted", "INT", "Int", false, deleted = true),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                            )
                        )
                    )
                )
            )
        }

        assertEquals("multiple explicit deleted columns found for table video_post", error.message)
    }

    @Test
    fun `assembler fails fast when multiple version columns are marked explicitly`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                aggregateProjectConfig(),
                listOf(
                    DbSchemaSnapshot(
                        tables = listOf(
                            DbTableSnapshot(
                                tableName = "video_post",
                                comment = "@AggregateRoot=true;",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                    DbColumnSnapshot("version", "BIGINT", "Long", false, version = true),
                                    DbColumnSnapshot("lock_version", "BIGINT", "Long", false, version = true),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                                dynamicUpdate = true,
                            )
                        )
                    )
                )
            )
        }

        assertEquals("multiple explicit version columns found for table video_post", error.message)
    }

    @Test
    fun `assembler keeps direct parent binding out of owner side child relations`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            aggregateRoot = true,
                        ),
                        DbTableSnapshot(
                            tableName = "video_post_item",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("video_post_id", "BIGINT", "Long", false, referenceTable = "video_post"),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            parentTable = "video_post",
                            aggregateRoot = false,
                            valueObject = true,
                        ),
                    )
                )
            )
        )

        assertEquals(
            listOf("VideoPost|items|VideoPostItem|ONE_TO_MANY"),
            result.model.aggregateRelations
                .map { "${it.ownerEntityName}|${it.fieldName}|${it.targetEntityName}|${it.relationType}" }
                .sorted(),
        )
        assertEquals(
            listOf("VideoPostItem|videoPost|VideoPost|MANY_TO_ONE"),
            result.model.aggregateInverseRelations
                .map { "${it.ownerEntityName}|${it.fieldName}|${it.targetEntityName}|${it.relationType}" }
                .sorted(),
        )

        val root = result.model.entities.first { it.name == "VideoPost" }
        assertEquals(true, root.aggregateRoot)
        assertEquals(false, root.valueObject)
        assertEquals(null, root.parentEntityName)

        val child = result.model.entities.first { it.name == "VideoPostItem" }
        assertEquals(false, child.aggregateRoot)
        assertEquals(true, child.valueObject)
        assertEquals("VideoPost", child.parentEntityName)
    }

    @Test
    fun `assembler groups child tables under aggregate root package and emits repositories only for roots`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video",
                            comment = "video aggregate",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            aggregateRoot = true,
                        ),
                        DbTableSnapshot(
                            tableName = "video_file",
                            comment = "video file entity",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("video_id", "BIGINT", "Long", false, referenceTable = "video"),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            parentTable = "video",
                            aggregateRoot = false,
                        ),
                        DbTableSnapshot(
                            tableName = "video_file_variant",
                            comment = "video file variant entity",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("file_id", "BIGINT", "Long", false, referenceTable = "video_file"),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            parentTable = "video_file",
                            aggregateRoot = false,
                        ),
                    )
                )
            )
        ).model

        val entities = result.entities.associateBy { it.name }
        assertEquals("com.acme.demo.domain.aggregates.video", entities.getValue("Video").packageName)
        assertEquals("com.acme.demo.domain.aggregates.video", entities.getValue("VideoFile").packageName)
        assertEquals("com.acme.demo.domain.aggregates.video", entities.getValue("VideoFileVariant").packageName)
        assertEquals(null, entities.getValue("Video").parentEntityName)
        assertEquals("Video", entities.getValue("VideoFile").parentEntityName)
        assertEquals("VideoFile", entities.getValue("VideoFileVariant").parentEntityName)

        val schemas = result.schemas.associateBy { it.name }
        assertEquals("com.acme.demo.domain._share.meta.video", schemas.getValue("SVideo").packageName)
        assertEquals("com.acme.demo.domain._share.meta.video", schemas.getValue("SVideoFile").packageName)
        assertEquals("com.acme.demo.domain._share.meta.video", schemas.getValue("SVideoFileVariant").packageName)

        assertEquals(listOf("VideoRepository"), result.repositories.map { it.name })
    }

    @Test
    fun `assembler enriches parent child one to many with bounded relation controls`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            aggregateRoot = true,
                        ),
                        DbTableSnapshot(
                            tableName = "video_post_item",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("video_post_id", "BIGINT", "Long", false, referenceTable = "video_post"),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            parentTable = "video_post",
                            aggregateRoot = false,
                            valueObject = true,
                        ),
                    )
                )
            )
        )

        val oneToMany = result.model.aggregateRelations.first { it.relationType == AggregateRelationType.ONE_TO_MANY }
        assertEquals(
            listOf(AggregateCascadeType.PERSIST, AggregateCascadeType.MERGE, AggregateCascadeType.REMOVE),
            oneToMany.cascadeTypes,
        )
        assertEquals(true, oneToMany.orphanRemoval)
        assertEquals(false, oneToMany.joinColumnNullable)
    }

    @Test
    fun `assembler derives inverse read only parent relation from parent child truth`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("title", "VARCHAR", "String", false),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "video_post_item",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("video_post_id", "BIGINT", "Long", false),
                                DbColumnSnapshot("label", "VARCHAR", "String", false),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            parentTable = "video_post",
                            aggregateRoot = false,
                            valueObject = true,
                        ),
                    )
                )
            )
        )

        val inverse = result.model.aggregateInverseRelations.single()

        assertEquals("VideoPostItem", inverse.ownerEntityName)
        assertEquals("videoPost", inverse.fieldName)
        assertEquals("VideoPost", inverse.targetEntityName)
        assertEquals(AggregateRelationType.MANY_TO_ONE, inverse.relationType)
        assertEquals("video_post_id", inverse.joinColumn)
        assertEquals(AggregateFetchType.LAZY, inverse.fetchType)
        assertEquals(false, inverse.nullable)
        assertEquals(false, inverse.insertable)
        assertEquals(false, inverse.updatable)
    }

    @Test
    fun `assembler keeps owned direct parent ref inside parent owned relation contract`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("title", "VARCHAR", "String", false),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "video_post_item",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot(
                                    name = "video_post_id",
                                    dbType = "BIGINT",
                                    kotlinType = "Long",
                                    nullable = false,
                                    referenceTable = "video_post",
                                    explicitRelationType = "MANY_TO_ONE",
                                ),
                                DbColumnSnapshot("label", "VARCHAR", "String", false),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            parentTable = "video_post",
                            aggregateRoot = false,
                            valueObject = true,
                        ),
                    )
                )
            )
        )

        assertEquals(
            1,
            result.model.aggregateRelations.size,
        )
        assertEquals(
            AggregateRelationModel(
                ownerEntityName = "VideoPost",
                ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                fieldName = "items",
                targetEntityName = "VideoPostItem",
                targetEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                relationType = AggregateRelationType.ONE_TO_MANY,
                joinColumn = "video_post_id",
                fetchType = AggregateFetchType.LAZY,
                nullable = false,
                cascadeTypes = listOf(
                    AggregateCascadeType.PERSIST,
                    AggregateCascadeType.MERGE,
                    AggregateCascadeType.REMOVE,
                ),
                orphanRemoval = true,
                joinColumnNullable = false,
            ),
            result.model.aggregateRelations.single(),
        )
        assertEquals(
            "VideoPostItem|videoPost|VideoPost|MANY_TO_ONE|video_post_id|LAZY|false|false",
            result.model.aggregateInverseRelations.single().let { inverse ->
                "${inverse.ownerEntityName}|${inverse.fieldName}|${inverse.targetEntityName}|${inverse.relationType}|${inverse.joinColumn}|${inverse.fetchType}|${inverse.insertable}|${inverse.updatable}"
            },
        )
    }

    @Test
    fun `assembler rejects local lazy override on owned direct parent binding`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                aggregateProjectConfig(),
                listOf(
                    DbSchemaSnapshot(
                        tables = listOf(
                            DbTableSnapshot(
                                tableName = "video_post",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                    DbColumnSnapshot("title", "VARCHAR", "String", false),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                            ),
                            DbTableSnapshot(
                                tableName = "video_post_item",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                    DbColumnSnapshot(
                                        name = "video_post_id",
                                        dbType = "BIGINT",
                                        kotlinType = "Long",
                                        nullable = false,
                                        referenceTable = "video_post",
                                        lazy = true,
                                    ),
                                    DbColumnSnapshot("label", "VARCHAR", "String", false),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                                parentTable = "video_post",
                                aggregateRoot = false,
                                valueObject = true,
                            ),
                        )
                    )
                )
            )
        }

        assertEquals(
            "owned parent-child direct parent binding does not allow local lazy override: video_post_item.video_post_id",
            error.message,
        )
    }

    @Test
    fun `inverse inference keeps owned direct parent binding derived when explicit ref matches parent case insensitively`() {
        val parentId = FieldModel(name = "id", type = "Long")
        val childId = FieldModel(name = "id", type = "Long")

        val inverseRelations = AggregateInverseRelationInference.infer(
            entities = listOf(
                EntityModel(
                    name = "VideoPost",
                    packageName = "com.acme.demo.domain.aggregates.video_post",
                    tableName = "video_post",
                    comment = "",
                    fields = listOf(parentId),
                    idField = parentId,
                ),
                EntityModel(
                    name = "VideoPostItem",
                    packageName = "com.acme.demo.domain.aggregates.video_post_item",
                    tableName = "video_post_item",
                    comment = "",
                    fields = listOf(
                        childId,
                        FieldModel(name = "label", type = "String"),
                    ),
                    idField = childId,
                    aggregateRoot = false,
                    valueObject = true,
                    parentEntityName = "VideoPost",
                ),
            ),
            relations = listOf(
                AggregateRelationModel(
                    ownerEntityName = "VideoPost",
                    ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                    fieldName = "items",
                    targetEntityName = "VideoPostItem",
                    targetEntityPackageName = "com.acme.demo.domain.aggregates.video_post_item",
                    relationType = AggregateRelationType.ONE_TO_MANY,
                    joinColumn = "video_post_id",
                    fetchType = AggregateFetchType.LAZY,
                    nullable = false,
                    cascadeTypes = listOf(
                        AggregateCascadeType.PERSIST,
                        AggregateCascadeType.MERGE,
                        AggregateCascadeType.REMOVE,
                    ),
                    orphanRemoval = true,
                    joinColumnNullable = false,
                ),
            ),
            tables = listOf(
                DbTableSnapshot(
                    tableName = "video_post",
                    comment = "",
                    columns = listOf(
                        DbColumnSnapshot(name = "id", dbType = "BIGINT", kotlinType = "Long", nullable = false, isPrimaryKey = true),
                    ),
                    primaryKey = listOf("id"),
                    uniqueConstraints = emptyList(),
                ),
                DbTableSnapshot(
                    tableName = "video_post_item",
                    comment = "",
                    columns = listOf(
                        DbColumnSnapshot(name = "id", dbType = "BIGINT", kotlinType = "Long", nullable = false, isPrimaryKey = true),
                        DbColumnSnapshot(
                            name = "VIDEO_POST_ID",
                            dbType = "BIGINT",
                            kotlinType = "Long",
                            nullable = false,
                            referenceTable = "video_post",
                            explicitRelationType = "MANY_TO_ONE",
                        ),
                    ),
                    primaryKey = listOf("id"),
                    uniqueConstraints = emptyList(),
                    parentTable = "video_post",
                    aggregateRoot = false,
                    valueObject = true,
                ),
            ),
        )

        assertEquals(
            listOf("VideoPostItem|videoPost|VideoPost|MANY_TO_ONE|video_post_id"),
            inverseRelations.map { "${it.ownerEntityName}|${it.fieldName}|${it.targetEntityName}|${it.relationType}|${it.joinColumn}" },
        )
    }

    @Test
    fun `assembler fails fast when derived inverse field collides with scalar field`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                aggregateProjectConfig(),
                listOf(
                    DbSchemaSnapshot(
                        tables = listOf(
                            DbTableSnapshot(
                                tableName = "video_post",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                            ),
                            DbTableSnapshot(
                                tableName = "video_post_item",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                    DbColumnSnapshot("videoPost", "VARCHAR", "String", false),
                                    DbColumnSnapshot("video_post_id", "BIGINT", "Long", false),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                                parentTable = "video_post",
                                aggregateRoot = false,
                                valueObject = true,
                            ),
                        )
                    )
                )
            )
        }

        assertEquals(
            "aggregate inverse relation field collides with scalar field: com.acme.demo.domain.aggregates.video_post.VideoPostItem.videoPost",
            error.message,
        )
    }

    @Test
    fun `assembler fails fast when derived inverse field collides with owner relation field`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                aggregateProjectConfig(),
                listOf(
                    DbSchemaSnapshot(
                        tables = listOf(
                            DbTableSnapshot(
                                tableName = "video_post",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                            ),
                            DbTableSnapshot(
                                tableName = "video_post_archive",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                            ),
                            DbTableSnapshot(
                                tableName = "video_post_item",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                    DbColumnSnapshot("video_post_id", "BIGINT", "Long", false),
                                    DbColumnSnapshot("video_post", "BIGINT", "Long", false, referenceTable = "video_post_archive"),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                                parentTable = "video_post",
                                aggregateRoot = false,
                                valueObject = true,
                            ),
                        )
                    )
                )
            )
        }

        assertEquals(
            "aggregate inverse relation field collides with owner relation field: com.acme.demo.domain.aggregates.video_post.VideoPostItem.videoPost",
            error.message,
        )
    }

    @Test
    fun `inverse inference fails fast on duplicate derived field names for the same child entity`() {
        val childId = FieldModel(name = "id", type = "Long")

        val error = assertThrows(IllegalArgumentException::class.java) {
            AggregateInverseRelationInference.infer(
                entities = listOf(
                    EntityModel(
                        name = "VideoPostItem",
                        packageName = "com.acme.demo.domain.aggregates.video_post_item",
                        tableName = "video_post_item",
                        comment = "",
                        fields = listOf(
                            childId,
                            FieldModel(name = "label", type = "String"),
                        ),
                        idField = childId,
                        aggregateRoot = false,
                        valueObject = true,
                    )
                ),
                relations = listOf(
                    AggregateRelationModel(
                        ownerEntityName = "VideoPost",
                        ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        fieldName = "items",
                        targetEntityName = "VideoPostItem",
                        targetEntityPackageName = "com.acme.demo.domain.aggregates.video_post_item",
                        relationType = AggregateRelationType.ONE_TO_MANY,
                        joinColumn = "video_post_id",
                        fetchType = AggregateFetchType.LAZY,
                        nullable = false,
                        cascadeTypes = listOf(
                            AggregateCascadeType.PERSIST,
                            AggregateCascadeType.MERGE,
                            AggregateCascadeType.REMOVE,
                        ),
                        orphanRemoval = true,
                        joinColumnNullable = false,
                    ),
                    AggregateRelationModel(
                        ownerEntityName = "VideoPost",
                        ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post_archive",
                        fieldName = "archivedItems",
                        targetEntityName = "VideoPostItem",
                        targetEntityPackageName = "com.acme.demo.domain.aggregates.video_post_item",
                        relationType = AggregateRelationType.ONE_TO_MANY,
                        joinColumn = "video_post_archive_id",
                        fetchType = AggregateFetchType.LAZY,
                        nullable = false,
                        cascadeTypes = listOf(
                            AggregateCascadeType.PERSIST,
                            AggregateCascadeType.MERGE,
                            AggregateCascadeType.REMOVE,
                        ),
                        orphanRemoval = true,
                        joinColumnNullable = false,
                    ),
                ),
                tables = emptyList(),
            )
        }

        assertEquals(
            "aggregate inverse relation field collision: com.acme.demo.domain.aggregates.video_post_item.VideoPostItem.videoPost",
            error.message,
        )
    }

    @Test
    fun `assembler rejects ambiguous parent child join columns`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                aggregateProjectConfig(),
                listOf(
                    DbSchemaSnapshot(
                        tables = listOf(
                            DbTableSnapshot(
                                tableName = "video_post",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                            ),
                            DbTableSnapshot(
                                tableName = "video_post_item",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                    DbColumnSnapshot("video_post_id", "BIGINT", "Long", false, referenceTable = "video_post"),
                                    DbColumnSnapshot("source_video_post_id", "BIGINT", "Long", false, referenceTable = "video_post"),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                                parentTable = "video_post",
                                aggregateRoot = false,
                                valueObject = true,
                            ),
                        )
                    )
                )
            )
        }

        assertEquals("ambiguous parent reference columns for table video_post_item -> video_post: source_video_post_id, video_post_id", error.message)
    }

    @Test
    fun `assembler rejects conflicting explicit relation type on parent reference`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                aggregateProjectConfig(),
                listOf(
                    DbSchemaSnapshot(
                        tables = listOf(
                            DbTableSnapshot(
                                tableName = "video_post",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                            ),
                            DbTableSnapshot(
                                tableName = "video_post_item",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                    DbColumnSnapshot(
                                        name = "video_post_id",
                                        dbType = "BIGINT",
                                        kotlinType = "Long",
                                        nullable = false,
                                        explicitRelationType = "ONE_TO_ONE",
                                        referenceTable = "video_post",
                                    ),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                                parentTable = "video_post",
                                aggregateRoot = false,
                                valueObject = true,
                            ),
                        )
                    )
                )
            )
        }

        assertEquals(
            "parent reference relation type must be MANY_TO_ONE in first slice: video_post_item.video_post_id -> video_post = ONE_TO_ONE",
            error.message,
        )
    }

    @Test
    fun `assembler defaults reference without explicit relation to many to one`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("author_id", "BIGINT", "Long", false, referenceTable = "user_profile"),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "user_profile",
                            comment = "",
                            columns = listOf(DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true)),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                    )
                )
            )
        )

        val relation = result.model.aggregateRelations.single()
        assertEquals(AggregateRelationType.MANY_TO_ONE, relation.relationType)
        assertEquals("author", relation.fieldName)
        assertEquals("UserProfile", relation.targetEntityName)
        assertEquals(AggregateFetchType.EAGER, relation.fetchType)
        assertEquals(false, relation.nullable)
    }

    @Test
    fun `assembler preserves fk nullability on many to one relation`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("author_id", "BIGINT", "Long", true, referenceTable = "user_profile"),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "user_profile",
                            comment = "",
                            columns = listOf(DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true)),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                    )
                )
            )
        )

        assertEquals(true, result.model.aggregateRelations.single().nullable)
    }

    @Test
    fun `assembler strips camel case id suffixes without losing shape`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("authorId", "BIGINT", "Long", false, referenceTable = "author_profile"),
                                DbColumnSnapshot("userProfileId", "BIGINT", "Long", false, referenceTable = "user_profile"),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "author_profile",
                            comment = "",
                            columns = listOf(DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true)),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "user_profile",
                            comment = "",
                            columns = listOf(DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true)),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                    )
                )
            )
        )

        assertEquals(
            listOf(
                "author|AuthorProfile",
                "userProfile|UserProfile",
            ).sorted(),
            result.model.aggregateRelations
                .map { "${it.fieldName}|${it.targetEntityName}" }
                .sorted(),
        )
    }

    @Test
    fun `assembler does not treat reference columns as scalar field collisions`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("author", "BIGINT", "Long", false, referenceTable = "user_profile"),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "user_profile",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                    )
                )
            )
        )

        val relation = result.model.aggregateRelations.single()
        assertEquals("author", relation.fieldName)
        assertEquals("UserProfile", relation.targetEntityName)
    }

    @Test
    fun `assembler maps explicit one to one relation metadata`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot(
                                    name = "cover_Id",
                                    dbType = "BIGINT",
                                    kotlinType = "Long",
                                    nullable = false,
                                    explicitRelationType = "ONE_TO_ONE",
                                    lazy = true,
                                    referenceTable = "media_asset",
                                ),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "media_asset",
                            comment = "",
                            columns = listOf(DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true)),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                    )
                )
            )
        )

        val relation = result.model.aggregateRelations.single()
        assertEquals(AggregateRelationType.ONE_TO_ONE, relation.relationType)
        assertEquals("cover", relation.fieldName)
        assertEquals("MediaAsset", relation.targetEntityName)
        assertEquals(AggregateFetchType.LAZY, relation.fetchType)
        assertEquals(false, relation.nullable)
    }

    @Test
    fun `assembler preserves bounded join column nullability for explicit many to one and one to one`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot(
                                    name = "author_id",
                                    dbType = "BIGINT",
                                    kotlinType = "Long",
                                    nullable = false,
                                    explicitRelationType = "MANY_TO_ONE",
                                    referenceTable = "user_profile",
                                ),
                                DbColumnSnapshot(
                                    name = "cover_id",
                                    dbType = "BIGINT",
                                    kotlinType = "Long",
                                    nullable = true,
                                    explicitRelationType = "ONE_TO_ONE",
                                    referenceTable = "media_asset",
                                ),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "user_profile",
                            comment = "",
                            columns = listOf(DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true)),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "media_asset",
                            comment = "",
                            columns = listOf(DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true)),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                    )
                )
            )
        )

        val manyToOne = result.model.aggregateRelations.first { it.relationType == AggregateRelationType.MANY_TO_ONE }
        assertEquals(emptyList<AggregateCascadeType>(), manyToOne.cascadeTypes)
        assertEquals(false, manyToOne.orphanRemoval)
        assertEquals(false, manyToOne.joinColumnNullable)

        val oneToOne = result.model.aggregateRelations.first { it.relationType == AggregateRelationType.ONE_TO_ONE }
        assertEquals(emptyList<AggregateCascadeType>(), oneToOne.cascadeTypes)
        assertEquals(false, oneToOne.orphanRemoval)
        assertEquals(true, oneToOne.joinColumnNullable)
    }

    @Test
    fun `assembler derives parent child collection names from table token boundaries`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "account",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "accounting_entry",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("account_id", "BIGINT", "Long", false, referenceTable = "account"),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            parentTable = "account",
                            aggregateRoot = false,
                            valueObject = true,
                        ),
                        DbTableSnapshot(
                            tableName = "category",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "category_policy",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("category_id", "BIGINT", "Long", false, referenceTable = "category"),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            parentTable = "category",
                            aggregateRoot = false,
                            valueObject = true,
                        ),
                    )
                )
            )
        )

        assertEquals(
            listOf(
                "Account|accountingEntries|AccountingEntry|ONE_TO_MANY",
                "Category|policies|CategoryPolicy|ONE_TO_MANY",
            ).sorted(),
            result.model.aggregateRelations
                .filter { it.relationType == AggregateRelationType.ONE_TO_MANY }
                .map { "${it.ownerEntityName}|${it.fieldName}|${it.targetEntityName}|${it.relationType}" }
                .sorted(),
        )
        assertEquals(
            listOf(false, false),
            result.model.aggregateRelations
                .filter { it.relationType == AggregateRelationType.ONE_TO_MANY }
                .map { it.nullable }
                .sorted(),
        )
    }

    @Test
    fun `assembler keeps already plural child tokens unchanged`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "order",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "order_items",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("order_id", "BIGINT", "Long", false, referenceTable = "order"),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            parentTable = "order",
                            aggregateRoot = false,
                            valueObject = true,
                        ),
                        DbTableSnapshot(
                            tableName = "category",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "category_policies",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("category_id", "BIGINT", "Long", false, referenceTable = "category"),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            parentTable = "category",
                            aggregateRoot = false,
                            valueObject = true,
                        ),
                    )
                )
            )
        )

        assertEquals(
            listOf(
                "Category|policies|CategoryPolicies|ONE_TO_MANY",
                "Order|items|OrderItems|ONE_TO_MANY",
            ).sorted(),
            result.model.aggregateRelations
                .filter { it.relationType == AggregateRelationType.ONE_TO_MANY }
                .map { "${it.ownerEntityName}|${it.fieldName}|${it.targetEntityName}|${it.relationType}" }
                .sorted(),
        )
    }

    @Test
    fun `assembler pluralizes bounded common endings for parent child collections`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "tenant",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "tenant_status",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("tenant_id", "BIGINT", "Long", false, referenceTable = "tenant"),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            parentTable = "tenant",
                            aggregateRoot = false,
                            valueObject = true,
                        ),
                        DbTableSnapshot(
                            tableName = "warehouse",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "warehouse_box",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("warehouse_id", "BIGINT", "Long", false, referenceTable = "warehouse"),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            parentTable = "warehouse",
                            aggregateRoot = false,
                            valueObject = true,
                        ),
                        DbTableSnapshot(
                            tableName = "company",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "company_branch",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("company_id", "BIGINT", "Long", false, referenceTable = "company"),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            parentTable = "company",
                            aggregateRoot = false,
                            valueObject = true,
                        ),
                    )
                )
            )
        )

        assertEquals(
            listOf(
                "Company|branches|CompanyBranch|ONE_TO_MANY",
                "Tenant|statuses|TenantStatus|ONE_TO_MANY",
                "Warehouse|boxes|WarehouseBox|ONE_TO_MANY",
            ).sorted(),
            result.model.aggregateRelations
                .filter { it.relationType == AggregateRelationType.ONE_TO_MANY }
                .map { "${it.ownerEntityName}|${it.fieldName}|${it.targetEntityName}|${it.relationType}" }
                .sorted(),
        )
    }

    @Test
    fun `assembler rejects unsupported many to many relation metadata`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                aggregateProjectConfig(),
                listOf(
                    DbSchemaSnapshot(
                        tables = listOf(
                            DbTableSnapshot(
                                tableName = "video_post",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot(
                                        name = "tag_id",
                                        dbType = "BIGINT",
                                        kotlinType = "Long",
                                        nullable = false,
                                        explicitRelationType = "MANY_TO_MANY",
                                        referenceTable = "tag",
                                    )
                                ),
                                primaryKey = listOf("tag_id"),
                                uniqueConstraints = emptyList(),
                            ),
                        )
                    )
                )
            )
        }

        assertEquals("unsupported aggregate relation type in first slice: MANY_TO_MANY", error.message)
    }

    @Test
    fun `maps db schema snapshot into schema entity and repository models`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseAggregateConfig(),
            snapshots = listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "Video post",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, null, "", true),
                                DbColumnSnapshot("title", "VARCHAR", "String", false, null, "", false),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = listOf(
                                UniqueConstraintModel(
                                    physicalName = "video_post_uk_v_title",
                                    columns = listOf("title"),
                                )
                            ),
                        )
                    )
                )
            ),
        ).model

        assertEquals("SVideoPost", model.schemas.single().name)
        assertEquals("com.acme.demo.domain._share.meta.video_post", model.schemas.single().packageName)
        assertEquals("VideoPost", model.entities.single().name)
        assertEquals("com.acme.demo.domain.aggregates.video_post", model.entities.single().packageName)
        val unique = model.entities.single().uniqueConstraints.single()
        assertEquals("video_post_uk_v_title", unique.physicalName)
        assertEquals(listOf("title"), unique.columns)
        assertEquals("VideoPostRepository", model.repositories.single().name)
        assertEquals("com.acme.demo.adapter.domain.repositories", model.repositories.single().packageName)
        assertEquals("VideoPostId", model.repositories.single().idType)
    }

    @Test
    fun `db columns become lower camel kotlin fields while JPA metadata keeps original column names`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "user_message",
                            comment = "user message",
                            primaryKey = listOf("id"),
                            uniqueConstraints = listOf(
                                UniqueConstraintModel(
                                    physicalName = "uk_v_message_key",
                                    columns = listOf("message_key"),
                                )
                            ),
                            columns = listOf(
                                DbColumnSnapshot("id", "bigint", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("message_key", "varchar", "String", false),
                                DbColumnSnapshot("room_id", "varchar", "String", false),
                                DbColumnSnapshot("published", "boolean", "Boolean", true),
                            ),
                        )
                    )
                )
            ),
        ).model

        val entity = model.entities.single()
        assertEquals(listOf("id", "messageKey", "roomId", "published"), entity.fields.map { it.name })
        assertEquals("messageKey", entity.fields.single { it.columnName == "message_key" }.name)
        assertEquals("roomId", entity.fields.single { it.columnName == "room_id" }.name)
        assertEquals("id", entity.idField.name)
        assertEquals("id", entity.idField.columnName)

        val jpa = model.aggregateEntityJpa.single()
        assertEquals(
            listOf("id" to "id", "messageKey" to "message_key", "roomId" to "room_id", "published" to "published"),
            jpa.columns.map { it.fieldName to it.columnName },
        )
    }

    @Test
    fun `normalizes uppercase jdbc table names into aggregate models`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseAggregateConfig(),
            snapshots = listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "VIDEO_POST",
                            comment = "Video post",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, null, "", true),
                                DbColumnSnapshot("title", "VARCHAR", "String", false, null, "", false),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = listOf(
                                UniqueConstraintModel(
                                    physicalName = "uk_v_title",
                                    columns = listOf("title"),
                                )
                            ),
                        )
                    )
                )
            ),
        ).model

        assertEquals("SVideoPost", model.schemas.single().name)
        assertEquals("VideoPost", model.entities.single().name)
        assertEquals("VideoPostRepository", model.repositories.single().name)
    }

    @Test
    fun `preserves mixed case jdbc table names in aggregate models`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseAggregateConfig(),
            snapshots = listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "VideoPost",
                            comment = "Video post",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, null, "", true),
                                DbColumnSnapshot("title", "VARCHAR", "String", false, null, "", false),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = listOf(
                                UniqueConstraintModel(
                                    physicalName = "uk_v_title",
                                    columns = listOf("title"),
                                )
                            ),
                        )
                    )
                )
            ),
        ).model

        assertEquals("SVideoPost", model.schemas.single().name)
        assertEquals("VideoPost", model.entities.single().name)
        assertEquals("VideoPostRepository", model.repositories.single().name)
    }

    @Test
    fun `normalizes uppercase jdbc table names with digits into aggregate models`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseAggregateConfig(),
            snapshots = listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "OAUTH2_CLIENT",
                            comment = "Oauth2 client",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, null, "", true),
                                DbColumnSnapshot("client_name", "VARCHAR", "String", false, null, "", false),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = listOf(
                                UniqueConstraintModel(
                                    physicalName = "uk_v_client_name",
                                    columns = listOf("client_name"),
                                )
                            ),
                        )
                    )
                )
            ),
        ).model

        assertEquals("SOauth2Client", model.schemas.single().name)
        assertEquals("Oauth2Client", model.entities.single().name)
        assertEquals("Oauth2ClientRepository", model.repositories.single().name)
    }

    @Test
    fun `fails fast when db table has no primary key`() {
        val assembler = DefaultCanonicalAssembler()

        val error = assertThrows(IllegalArgumentException::class.java) {
            assembler.assemble(
                config = baseAggregateConfig(),
                snapshots = listOf(
                    DbSchemaSnapshot(
                        tables = listOf(
                            DbTableSnapshot(
                                tableName = "audit_log",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("event_id", "VARCHAR", "String", false, null, "", false),
                                ),
                                primaryKey = emptyList(),
                                uniqueConstraints = emptyList(),
                            )
                        )
                    )
                ),
            )
        }

        assertEquals("db table audit_log is unsupported for aggregate generation: missing_primary_key", error.message)
    }

    @Test
    fun `fails when db table has composite primary key`() {
        val assembler = DefaultCanonicalAssembler()

        val error = assertThrows(IllegalArgumentException::class.java) {
            assembler.assemble(
                config = baseAggregateConfig(),
                snapshots = listOf(
                    DbSchemaSnapshot(
                        tables = listOf(
                            DbTableSnapshot(
                                tableName = "audit_log",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("tenant_id", "BIGINT", "Long", false, null, "", true),
                                    DbColumnSnapshot("event_id", "VARCHAR", "String", false, null, "", true),
                                ),
                                primaryKey = listOf("tenant_id", "event_id"),
                                uniqueConstraints = emptyList(),
                            )
                        )
                    )
                ),
            )
        }

        assertEquals("db table audit_log is unsupported for aggregate generation: composite_primary_key", error.message)
    }

    @Test
    fun `fail mode surfaces diagnostics when unsupported table is encountered`() {
        val assembler = DefaultCanonicalAssembler()

        val error = assertThrows(PipelineDiagnosticsException::class.java) {
            assembler.assemble(
                config = baseAggregateConfig(),
                snapshots = listOf(
                    DbSchemaSnapshot(
                        tables = listOf(
                            DbTableSnapshot(
                                tableName = "audit_log",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("tenant_id", "BIGINT", "Long", false, null, "", true),
                                    DbColumnSnapshot("event_id", "VARCHAR", "String", false, null, "", true),
                                ),
                                primaryKey = listOf("tenant_id", "event_id"),
                                uniqueConstraints = emptyList(),
                            ),
                            DbTableSnapshot(
                                tableName = "video_post",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, null, "", true),
                                    DbColumnSnapshot("title", "VARCHAR", "String", false, null, "", false),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                            ),
                        ),
                        discoveredTables = listOf("audit_log", "video_post"),
                        includedTables = listOf("audit_log", "video_post"),
                        excludedTables = emptyList(),
                    )
                ),
            )
        }

        assertEquals("db table audit_log is unsupported for aggregate generation: composite_primary_key", error.message)
        assertEquals(listOf("video_post"), error.diagnostics.aggregate!!.supportedTables)
        assertEquals("audit_log", error.diagnostics.aggregate!!.unsupportedTables.single().tableName)
        assertEquals("composite_primary_key", error.diagnostics.aggregate!!.unsupportedTables.single().reason)
    }

    @Test
    fun `skips composite key table when unsupported policy is skip`() {
        val assembly = DefaultCanonicalAssembler().assemble(
            config = baseAggregateConfig(
                generators = mapOf(
                    "aggregate" to GeneratorConfig(
                        options = mapOf("unsupportedTablePolicy" to "SKIP"),
                    )
                )
            ),
            snapshots = listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            "audit_log",
                            "",
                            columns = listOf(
                                DbColumnSnapshot("tenant_id", "BIGINT", "Long", false, null, "", true),
                                DbColumnSnapshot("event_id", "VARCHAR", "String", false, null, "", true),
                            ),
                            primaryKey = listOf("tenant_id", "event_id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            "video_post",
                            "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, null, "", true),
                                DbColumnSnapshot("title", "VARCHAR", "String", false, null, "", false),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                    ),
                    discoveredTables = listOf("audit_log", "video_post"),
                    includedTables = listOf("audit_log", "video_post"),
                    excludedTables = emptyList(),
                )
            ),
        )
        val model = assembly.model

        assertEquals(listOf("VideoPost"), model.entities.map { it.name })
        assertEquals(listOf("video_post"), assembly.diagnostics!!.aggregate!!.supportedTables)
        assertEquals("audit_log", assembly.diagnostics!!.aggregate!!.unsupportedTables.single().tableName)
    }

    @Test
    fun `skip policy drops relations targeting skipped unsupported tables`() {
        val assembly = DefaultCanonicalAssembler().assemble(
            config = baseAggregateConfig(
                generators = mapOf(
                    "aggregate" to GeneratorConfig(
                        options = mapOf("unsupportedTablePolicy" to "SKIP"),
                    )
                )
            ),
            snapshots = listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "audit_log",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("tenant_id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("event_id", "VARCHAR", "String", false, isPrimaryKey = true),
                            ),
                            primaryKey = listOf("tenant_id", "event_id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("audit_log_id", "BIGINT", "Long", false, referenceTable = "audit_log"),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                    )
                )
            ),
        )

        assertEquals(listOf("VideoPost"), assembly.model.entities.map { it.name })
        assertEquals(emptyList<String>(), assembly.model.aggregateRelations.map { it.fieldName })
        assertEquals("audit_log", assembly.diagnostics!!.aggregate!!.unsupportedTables.single().tableName)
    }

    @Test
    fun `skip policy clears parent entity name when parent table is skipped`() {
        val assembly = DefaultCanonicalAssembler().assemble(
            config = baseAggregateConfig(
                generators = mapOf(
                    "aggregate" to GeneratorConfig(
                        options = mapOf("unsupportedTablePolicy" to "SKIP"),
                    )
                )
            ),
            snapshots = listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("tenant_id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            ),
                            primaryKey = listOf("tenant_id", "id"),
                            uniqueConstraints = emptyList(),
                        ),
                        DbTableSnapshot(
                            tableName = "video_post_item",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("video_post_id", "BIGINT", "Long", false, referenceTable = "video_post"),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            parentTable = "video_post",
                            aggregateRoot = false,
                            valueObject = true,
                        ),
                    )
                )
            ),
        )

        val child = assembly.model.entities.single()
        assertEquals("VideoPostItem", child.name)
        assertEquals(null, child.parentEntityName)
        assertEquals(emptyList<String>(), assembly.model.aggregateRelations.map { it.fieldName })
        assertEquals("video_post", assembly.diagnostics!!.aggregate!!.unsupportedTables.single().tableName)
    }

    @Test
    fun `assembler rejects relation field name collisions`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                aggregateProjectConfig(),
                listOf(
                    DbSchemaSnapshot(
                        tables = listOf(
                            DbTableSnapshot(
                                tableName = "video_post",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                    DbColumnSnapshot("author_id", "BIGINT", "Long", false, referenceTable = "user_profile"),
                                    DbColumnSnapshot("author_ID", "BIGINT", "Long", false, referenceTable = "user_profile"),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                            ),
                            DbTableSnapshot(
                                tableName = "user_profile",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                            ),
                        )
                    )
                )
            )
        }

        assertEquals("aggregate relation field collision: VideoPost.author -> UserProfile [MANY_TO_ONE]", error.message)
    }

    @Test
    fun `assembler rejects relation field names that collide with entity fields`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                aggregateProjectConfig(),
                listOf(
                    DbSchemaSnapshot(
                        tables = listOf(
                            DbTableSnapshot(
                                tableName = "video_post",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                    DbColumnSnapshot("author", "VARCHAR", "String", false),
                                    DbColumnSnapshot("author_id", "BIGINT", "Long", false, referenceTable = "user_profile"),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                            ),
                            DbTableSnapshot(
                                tableName = "user_profile",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                            ),
                        )
                    )
                )
            )
        }

        assertEquals("aggregate relation field collides with entity field: VideoPost.author -> UserProfile [MANY_TO_ONE]", error.message)
    }

    @Test
    fun `assembler rejects relation field names that collide with db camelized entity fields`() {
        val scalarOnlyAssembly = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("cover_profile", "VARCHAR", "String", false),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                    )
                )
            )
        )
        val coverProfile = scalarOnlyAssembly.model.entities.single().fields.single { it.columnName == "cover_profile" }
        assertEquals("coverProfile", coverProfile.name)

        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                aggregateProjectConfig(),
                listOf(
                    DbSchemaSnapshot(
                        tables = listOf(
                            DbTableSnapshot(
                                tableName = "video_post",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                    DbColumnSnapshot("cover_profile", "VARCHAR", "String", false),
                                    DbColumnSnapshot("cover_profile_id", "BIGINT", "Long", false, referenceTable = "user_profile"),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                            ),
                            DbTableSnapshot(
                                tableName = "user_profile",
                                comment = "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                            ),
                        )
                    )
                )
            )
        }

        assertTrue(
            error.message!!.contains("relation field name coverProfile conflicts with scalar field on table video_post")
        )
    }

    @Test
    fun `filtered out relation targets are skipped instead of failing assembly`() {
        val assembly = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                                DbColumnSnapshot("author_id", "BIGINT", "Long", false, referenceTable = "user_profile"),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                        ),
                    ),
                    discoveredTables = listOf("video_post", "user_profile"),
                    includedTables = listOf("video_post"),
                    excludedTables = listOf("user_profile"),
                )
            )
        )

        assertEquals(listOf("VideoPost"), assembly.model.entities.map { it.name })
        assertEquals(emptyList<String>(), assembly.model.aggregateRelations.map { it.fieldName })
    }

    private fun assembleAggregate(
        config: ProjectConfig,
        tables: List<DbTableSnapshot>,
    ) = DefaultCanonicalAssembler().assemble(
        config = config,
        snapshots = listOf(DbSchemaSnapshot(tables = tables)),
    )

    private fun assemble(
        db: DbSchemaSnapshot? = null,
        design: DesignSpecSnapshot? = null,
        valueObjects: ValueObjectManifestSnapshot? = null,
        typeRegistry: TypeRegistryModel = TypeRegistryModel.empty(),
    ) = DefaultCanonicalAssembler().assemble(
        config = baseAggregateConfig().copy(typeRegistry = TypeRegistryConfig(entries = typeRegistry.entries)),
        snapshots = listOfNotNull(db, design, valueObjects),
    ).model

    private fun projectConfigWithSpecialFieldDefaults(
        idDefaultStrategy: String,
        deletedDefaultColumn: String = "",
        versionDefaultColumn: String = "",
        managedDefaultColumns: List<String> = emptyList(),
    ): ProjectConfig = baseAggregateConfig(
        artifactLayout = ArtifactLayoutConfig(
            aggregate = PackageLayout("domain.aggregates"),
        ),
    ).copy(
        aggregateSpecialFieldDefaults = AggregateSpecialFieldDefaultsConfig(
            idDefaultStrategy = idDefaultStrategy,
            deletedDefaultColumn = deletedDefaultColumn,
            versionDefaultColumn = versionDefaultColumn,
            managedDefaultColumns = managedDefaultColumns,
        )
    )

    private fun table(
        name: String,
        columns: List<DbColumnSnapshot>,
        primaryKey: List<String>,
        aggregateRoot: Boolean,
        parentTable: String? = null,
    ): DbTableSnapshot = DbTableSnapshot(
        tableName = name,
        comment = "",
        columns = columns,
        primaryKey = primaryKey,
        uniqueConstraints = emptyList(),
        parentTable = parentTable,
        aggregateRoot = aggregateRoot,
        valueObject = !aggregateRoot,
    )

    private fun column(
        name: String,
        dbType: String,
        kotlinType: String,
        nullable: Boolean,
        primaryKey: Boolean = false,
        referenceTable: String? = null,
        generatedValueDeclared: Boolean = false,
        generatedValueStrategy: String? = null,
        deleted: Boolean? = null,
        version: Boolean? = null,
        managed: Boolean? = null,
        exposed: Boolean? = null,
        refAggregate: String? = null,
        refId: String? = null,
    ): DbColumnSnapshot = DbColumnSnapshot(
        name = name,
        dbType = dbType,
        kotlinType = kotlinType,
        nullable = nullable,
        isPrimaryKey = primaryKey,
        referenceTable = referenceTable,
        generatedValueDeclared = generatedValueDeclared,
        generatedValueStrategy = generatedValueStrategy,
        deleted = deleted,
        version = version,
        managed = managed,
        exposed = exposed,
        refAggregate = refAggregate,
        refId = refId,
    )

    private fun baseConfig(): ProjectConfig {
        return ProjectConfig(
            basePackage = "com.acme.demo",
            layout = MULTI_MODULE,
            modules = mapOf("application" to "demo-application"),
            sources = emptyMap(),
            generators = emptyMap(),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            aggregateSpecialFieldDefaults = AggregateSpecialFieldDefaultsConfig(
                idDefaultStrategy = "snowflake-long",
            ),
        )
    }

    private fun baseAggregateConfig(
        generators: Map<String, GeneratorConfig> = emptyMap(),
        artifactLayout: ArtifactLayoutConfig = ArtifactLayoutConfig(),
    ): ProjectConfig {
        return ProjectConfig(
            basePackage = "com.acme.demo",
            layout = MULTI_MODULE,
            modules = mapOf(
                "domain" to "demo-domain",
                "adapter" to "demo-adapter",
            ),
            sources = emptyMap(),
            generators = generators,
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            artifactLayout = artifactLayout,
            aggregateSpecialFieldDefaults = AggregateSpecialFieldDefaultsConfig(
                idDefaultStrategy = "snowflake-long",
            ),
        )
    }

    private fun aggregateProjectConfig(): ProjectConfig = baseAggregateConfig()
}
