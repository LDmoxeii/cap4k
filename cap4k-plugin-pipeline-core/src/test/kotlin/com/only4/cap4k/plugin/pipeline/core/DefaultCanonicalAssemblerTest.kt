package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateMetadataRecord
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignSpecEntry
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.DesignElementSnapshot
import com.only4.cap4k.plugin.pipeline.api.DesignFieldSnapshot
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
import com.only4.cap4k.plugin.pipeline.api.RequestKind
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DefaultCanonicalAssemblerTest {

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

        assertEquals(1, model.validators.size)
        val validator = model.validators.single()
        assertEquals("auth.validator", validator.packageName)
        assertEquals("IssueToken", validator.typeName)
        assertEquals("issue token validator", validator.description)
        assertEquals("Long", validator.valueType)
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
}
