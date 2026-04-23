package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateMetadataRecord
import com.only4.cap4k.plugin.pipeline.api.CommandVariant
import com.only4.cap4k.plugin.pipeline.api.AggregateFetchType
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
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
import com.only4.cap4k.plugin.pipeline.api.KspMetadataSnapshot
import com.only4.cap4k.plugin.pipeline.api.PipelineDiagnosticsException
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout.MULTI_MODULE
import com.only4.cap4k.plugin.pipeline.api.QueryVariant
import com.only4.cap4k.plugin.pipeline.api.RequestKind
import com.only4.cap4k.plugin.pipeline.api.RequestModel
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultCanonicalAssemblerTest {

    @Test
    fun `assembler splits cmd qry cli into typed canonical collections`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "cmd",
                            packageName = "order",
                            name = "CreateOrder",
                            description = "create order",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "qry",
                            packageName = "order",
                            name = "FindOrderList",
                            description = "list order",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "cli",
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
        assertEquals(QueryVariant.LIST, model.queries.single().variant)
        assertEquals(CommandVariant.DEFAULT, model.commands.single().variant)
    }

    @Test
    fun `assembler resolves page list and default query variants canonically`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "qry",
                            packageName = "order",
                            name = "FindOrder",
                            description = "find order",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "qry",
                            packageName = "order",
                            name = "FindOrderList",
                            description = "list order",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "qry",
                            packageName = "order",
                            name = "FindOrderPage",
                            description = "page order",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                    )
                ),
            ),
        ).model

        assertEquals(
            listOf(QueryVariant.DEFAULT, QueryVariant.LIST, QueryVariant.PAGE),
            model.queries.map { it.variant },
        )
    }

    @Test
    fun `client design tags map into CLIENT request kind`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "cli",
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
                            tag = "clients",
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
            listOf(RequestKind.CLIENT, RequestKind.CLIENT, RequestKind.CLIENT),
            model.requests.map { it.kind },
        )
        assertEquals("IssueTokenCli", model.requests.first().typeName)
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
                            tag = "cmd",
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
                            tag = "qry",
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

        assertEquals(
            listOf(RequestKind.COMMAND, RequestKind.CLIENT, RequestKind.QUERY),
            model.requests.map { it.kind },
        )
        assertEquals(
            listOf("SubmitOrderCmd", "IssueTokenCli", "FindOrderQry"),
            model.requests.map { it.typeName },
        )
        assertEquals(
            listOf("Order", "Order", "Order"),
            model.requests.map { it.aggregateName },
        )
        assertEquals(
            listOf(
                "com.acme.demo.domain.aggregates.order",
                "com.acme.demo.domain.aggregates.order",
                "com.acme.demo.domain.aggregates.order",
            ),
            model.requests.map { it.aggregatePackageName },
        )
    }

    @Test
    fun `validator entries assemble into validators slice with normalized naming and fixed value type`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "validator",
                            packageName = "auth.validator",
                            name = "issueToken",
                            description = "issue token validator",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "validator",
                            packageName = "auth.validator",
                            name = "issue_token",
                            description = "issue token validator snake",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "validator",
                            packageName = "auth.validator",
                            name = "issue-token",
                            description = "issue token validator kebab",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "validators",
                            packageName = "auth.validator",
                            name = "pluralAlias",
                            description = "legacy alias",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "validater",
                            packageName = "auth.validator",
                            name = "misspelledAlias",
                            description = "legacy alias",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "validate",
                            packageName = "auth.validator",
                            name = "verbAlias",
                            description = "legacy alias",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                    )
                ),
            ),
        ).model

        assertEquals(3, model.validators.size)
        assertEquals(
            listOf("IssueToken", "IssueToken", "IssueToken"),
            model.validators.map { it.typeName },
        )
        assertEquals(listOf("auth.validator", "auth.validator", "auth.validator"), model.validators.map { it.packageName })
        assertEquals(listOf("Long", "Long", "Long"), model.validators.map { it.valueType })
        assertEquals(emptyList<com.only4.cap4k.plugin.pipeline.api.RequestModel>(), model.requests)
    }

    @Test
    fun `validator entries keep request assembly unchanged`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "validator",
                            packageName = "auth.validator",
                            name = "issueToken",
                            description = "issue token validator",
                            aggregates = emptyList(),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "cmd",
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

        assertEquals(1, model.requests.size)
        assertEquals(RequestKind.COMMAND, model.requests.single().kind)
        assertEquals("SubmitOrderCmd", model.requests.single().typeName)
        assertEquals(1, model.validators.size)
    }

    @Test
    fun `api payload entries assemble into dedicated api payload slice and keep request assembly unchanged`() {
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
                            tag = "cmd",
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

        assertEquals(1, model.requests.size)
        assertEquals(
            RequestModel(
                kind = RequestKind.COMMAND,
                packageName = "order.submit",
                typeName = "SubmitOrderCmd",
                description = "submit order",
                aggregateName = "Order",
                aggregatePackageName = null,
                requestFields = listOf(FieldModel(name = "orderId", type = "Long")),
                responseFields = listOf(FieldModel(name = "accepted", type = "Boolean")),
            ),
            model.requests.single(),
        )
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
                            tag = "payload",
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
    fun `maps design entries and ksp aggregates into canonical requests`() {
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
                            tag = "cmd",
                            packageName = "order.submit",
                            name = "SubmitOrder",
                            description = "submit order",
                            aggregates = listOf("Order"),
                            requestFields = listOf(FieldModel(name = "orderId", type = "Long")),
                            responseFields = listOf(FieldModel(name = "accepted", type = "Boolean")),
                        ),
                        DesignSpecEntry(
                            tag = "qry",
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

        assertEquals(2, model.requests.size)
        val firstRequest = model.requests.first()
        assertEquals(RequestKind.COMMAND, firstRequest.kind)
        assertEquals("SubmitOrderCmd", firstRequest.typeName)
        assertEquals("order.submit", firstRequest.packageName)
        assertEquals("submit order", firstRequest.description)
        assertEquals("Order", firstRequest.aggregateName)
        assertEquals("com.acme.demo.domain.aggregates.order", firstRequest.aggregatePackageName)
        assertEquals(listOf(FieldModel(name = "orderId", type = "Long")), firstRequest.requestFields)
        assertEquals(listOf(FieldModel(name = "accepted", type = "Boolean")), firstRequest.responseFields)
        assertEquals(RequestKind.QUERY, model.requests.last().kind)
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

        assertEquals(1, model.requests.size)
        assertEquals(RequestKind.COMMAND, model.requests.first().kind)
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

        assertEquals(0, model.requests.size)
    }

    @Test
    fun `preserves aggregate name without ksp match and leaves aggregate package null`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                DesignSpecSnapshot(
                    entries = listOf(
                        DesignSpecEntry(
                            tag = "qry",
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

        assertEquals(1, model.requests.size)
        assertEquals("Order", model.requests.first().aggregateName)
        assertNull(model.requests.first().aggregatePackageName)
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
    fun `assembles drawing board from design elements with supported tags and first wins deduplication`() {
        val assembler = DefaultCanonicalAssembler()

        val supportedField = DesignFieldSnapshot(name = "orderId", type = "Long", nullable = false, defaultValue = "0")
        val responseField = DesignFieldSnapshot(name = "accepted", type = "Boolean")
        val duplicateField = DesignFieldSnapshot(name = "ignored", type = "String")

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                IrAnalysisSnapshot(
                    inputDirs = listOf("app/build/cap4k-code-analysis"),
                    nodes = emptyList(),
                    edges = emptyList(),
                    designElements = listOf(
                        DesignElementSnapshot(
                            tag = "cmd",
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
                            tag = "cmd",
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
                            tag = "qry",
                            packageName = "order.read",
                            name = "FindOrder",
                            description = "find order",
                            aggregates = listOf("Order"),
                            requestFields = emptyList(),
                            responseFields = emptyList(),
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
        assertEquals(2, drawingBoard!!.elements.size)
        assertEquals(
            DrawingBoardElementModel(
                tag = "cmd",
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
        assertEquals(listOf("cmd", "qry"), drawingBoard.elementsByTag.keys.toList())
        assertEquals(1, drawingBoard.elementsByTag.getValue("cmd").size)
        assertEquals("FindOrder", drawingBoard.elementsByTag.getValue("qry").single().name)
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
                            generateTranslation = true,
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
                            generateTranslation = true,
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
                                generateTranslation = true,
                                items = listOf(EnumItemModel(0, "DRAFT", "Draft")),
                            )
                        )
                    )
                )
            )
        }

        assertEquals(
            "ambiguous enum ownership for Status: matches both shared enum and local enum in com.acme.demo.domain.aggregates.video_post",
            error.message
        )
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
            entityJpa.columns.single { it.fieldName == "default_visibility" }.converterTypeFqn
        )
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
        assertEquals(entity.fields.single { it.name == "created_by" }.name, controls.single { it.columnName == "created_by" }.fieldName)
        assertEquals(entity.fields.single { it.name == "updated_by" }.name, controls.single { it.columnName == "updated_by" }.fieldName)
        assertEquals("IDENTITY", controls.single { it.fieldName == "id" }.generatedValueStrategy)
        assertEquals(true, controls.single { it.fieldName == "version" }.version)
        assertEquals(false, controls.single { it.fieldName == "created_by" }.insertable)
        assertEquals(false, controls.single { it.fieldName == "updated_by" }.updatable)
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
                                DbColumnSnapshot("deleted", "INT", "Int", false),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            dynamicInsert = true,
                            dynamicUpdate = true,
                            softDeleteColumn = "deleted",
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
    fun `assembler derives aggregate id generator control for eligible entity`() {
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
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            entityIdGenerator = "snowflakeIdGenerator",
                        )
                    )
                )
            )
        )

        val control = result.model.aggregateIdGeneratorControls.single()

        assertEquals("VideoPost", control.entityName)
        assertEquals("com.acme.demo.domain.aggregates.video_post", control.entityPackageName)
        assertEquals("video_post", control.tableName)
        assertEquals("id", control.idFieldName)
        assertEquals("snowflakeIdGenerator", control.entityIdGenerator)
    }

    @Test
    fun `assembler trims aggregate id generator control value for eligible entity`() {
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
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            entityIdGenerator = "  snowflakeIdGenerator  ",
                        )
                    )
                )
            )
        )

        val control = result.model.aggregateIdGeneratorControls.single()
        assertEquals("snowflakeIdGenerator", control.entityIdGenerator)
    }

    @Test
    fun `assembler skips aggregate id generator control for blank generator value`() {
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
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            entityIdGenerator = "   ",
                        )
                    )
                )
            )
        )

        assertTrue(result.model.aggregateIdGeneratorControls.isEmpty())
    }

    @Test
    fun `assembler does not derive aggregate id generator control for value object`() {
        val result = DefaultCanonicalAssembler().assemble(
            aggregateProjectConfig(),
            listOf(
                DbSchemaSnapshot(
                    tables = listOf(
                        DbTableSnapshot(
                            tableName = "video_post",
                            comment = "@AggregateRoot=false;",
                            columns = listOf(
                                DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            ),
                            primaryKey = listOf("id"),
                            uniqueConstraints = emptyList(),
                            aggregateRoot = false,
                            valueObject = true,
                            entityIdGenerator = "snowflakeIdGenerator",
                        )
                    )
                )
            )
        )

        assertTrue(result.model.aggregateIdGeneratorControls.isEmpty())
    }

    @Test
    fun `assembler fails fast when soft delete column does not exist on the table`() {
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
                                    DbColumnSnapshot("title", "VARCHAR", "String", false),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                                softDeleteColumn = "deletd",
                            )
                        )
                    )
                )
            )
        }

        assertEquals("softDeleteColumn deletd does not exist on table video_post", error.message)
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
    fun `assembler preserves both parent and child relations for parent child table metadata`() {
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
            listOf(
                "VideoPost|items|VideoPostItem|ONE_TO_MANY",
                "VideoPostItem|videoPost|VideoPost|MANY_TO_ONE",
            ).sorted(),
            result.model.aggregateRelations
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
        assertEquals(true, oneToMany.cascadeAll)
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
    fun `assembler suppresses inverse read only relation when explicit owner side relation already exists`() {
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

        assertTrue(result.model.aggregateInverseRelations.isEmpty())
        assertEquals(
            1,
            result.model.aggregateRelations.count {
                it.ownerEntityName == "VideoPostItem" &&
                    it.targetEntityName == "VideoPost" &&
                    it.relationType == AggregateRelationType.MANY_TO_ONE
            },
        )
    }

    @Test
    fun `assembler suppresses inverse read only relation when default owner side reference already exists`() {
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

        assertTrue(result.model.aggregateInverseRelations.isEmpty())
        assertEquals(
            1,
            result.model.aggregateRelations.count {
                it.ownerEntityName == "VideoPostItem" &&
                    it.targetEntityName == "VideoPost" &&
                    it.relationType == AggregateRelationType.MANY_TO_ONE
            },
        )
    }

    @Test
    fun `inverse inference suppresses owner side relation with case insensitive join column matching`() {
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
                    cascadeAll = true,
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

        assertTrue(inverseRelations.isEmpty())
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
            "aggregate inverse relation field collides with scalar field: com.acme.demo.domain.aggregates.video_post_item.VideoPostItem.videoPost",
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
            "aggregate inverse relation field collides with owner relation field: com.acme.demo.domain.aggregates.video_post_item.VideoPostItem.videoPost",
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
                        cascadeAll = true,
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
                        cascadeAll = true,
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
    fun `assembler rejects relation field names that collide with reference columns`() {
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
        }

        assertEquals("aggregate relation field collides with entity field: VideoPost.author -> UserProfile [MANY_TO_ONE]", error.message)
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
        assertEquals(false, manyToOne.cascadeAll)
        assertEquals(false, manyToOne.orphanRemoval)
        assertEquals(false, manyToOne.joinColumnNullable)

        val oneToOne = result.model.aggregateRelations.first { it.relationType == AggregateRelationType.ONE_TO_ONE }
        assertEquals(false, oneToOne.cascadeAll)
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
                            uniqueConstraints = listOf(listOf("title")),
                        )
                    )
                )
            ),
        ).model

        assertEquals("SVideoPost", model.schemas.single().name)
        assertEquals("com.acme.demo.domain._share.meta.video_post", model.schemas.single().packageName)
        assertEquals("VideoPost", model.entities.single().name)
        assertEquals("com.acme.demo.domain.aggregates.video_post", model.entities.single().packageName)
        assertEquals(listOf(listOf("title")), model.entities.single().uniqueConstraints)
        assertEquals("VideoPostRepository", model.repositories.single().name)
        assertEquals("com.acme.demo.adapter.domain.repositories", model.repositories.single().packageName)
        assertEquals("Long", model.repositories.single().idType)
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
                            uniqueConstraints = listOf(listOf("title")),
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
                            uniqueConstraints = listOf(listOf("title")),
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
                            uniqueConstraints = listOf(listOf("client_name")),
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
                        enabled = true,
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
                        enabled = true,
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
                        enabled = true,
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

    private fun baseConfig(): ProjectConfig {
        return ProjectConfig(
            basePackage = "com.acme.demo",
            layout = MULTI_MODULE,
            modules = mapOf("application" to "demo-application"),
            sources = emptyMap(),
            generators = emptyMap(),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
    }

    private fun baseAggregateConfig(generators: Map<String, GeneratorConfig> = emptyMap()): ProjectConfig {
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
        )
    }

    private fun aggregateProjectConfig(): ProjectConfig = baseAggregateConfig()
}
