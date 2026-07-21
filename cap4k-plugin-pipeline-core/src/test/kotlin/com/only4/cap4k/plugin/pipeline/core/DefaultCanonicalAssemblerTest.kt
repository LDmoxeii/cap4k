package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateCascadeType
import com.only4.cap4k.plugin.pipeline.api.AggregateFetchType
import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyKind
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.AggregateSpecialFieldDefaultsConfig
import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.DesignSpecEntry
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbIdStrategy
import com.only4.cap4k.plugin.pipeline.api.DbManagedRole
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
import com.only4.cap4k.plugin.pipeline.api.PipelineDiagnosticsException
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout.MULTI_MODULE
import com.only4.cap4k.plugin.pipeline.api.PackageLayout
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
import com.only4.cap4k.plugin.pipeline.api.ValueObjectStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
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
                            artifacts = emptyList(),
                            fields = listOf(FieldModel(name = "orderId", type = "Long")),
                            eventName = "order.created",
                        ),
                    ),
                ),
            ),
        ).model

        assertEquals(emptyList<ArtifactSelectionModel>(), model.designBlocks.single().artifacts)
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
    fun `integration event artifact variant controls typed projection role`() {
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
                            fields = listOf(FieldModel(name = "orderId", type = "Long")),
                            eventName = "order.created",
                            artifacts = listOf(ArtifactSelectionModel("integration-event", "inbound")),
                        ),
                    ),
                ),
            ),
        ).model

        assertEquals(
            listOf(ArtifactSelectionModel("integration-event", "inbound")),
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
                                tag = "domain_service",
                                packageName = "order",
                                name = "OrderDomainService",
                                description = "order domain service",
                                aggregates = emptyList(),
                                resultFields = listOf(FieldModel(name = "accepted", type = "Boolean")),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals(
            "design entry OrderDomainService cannot declare resultFields on tag: domain_service",
            error.message,
        )
    }

    @Test
    fun `assembler maps canonical command query client entries into design blocks`() {
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
                            resultFields = listOf(FieldModel(name = "accepted", type = "Boolean")),
                        ),
                        DesignSpecEntry(
                            tag = "query",
                            packageName = "order",
                            name = "FindOrderList",
                            description = "list order",
                            aggregates = emptyList(),
                        ),
                        DesignSpecEntry(
                            tag = "client",
                            packageName = "remote",
                            name = "SyncStock",
                            description = "sync stock",
                            aggregates = emptyList(),
                        ),
                    )
                ),
            ),
        ).model

        assertEquals(listOf("CreateOrder"), model.designBlocks.filter { it.tag == "command" }.map { it.name })
        assertEquals(
            listOf(FieldModel(name = "accepted", type = "Boolean")),
            model.designBlocks.single { it.tag == "command" }.resultFields,
        )
        assertEquals(listOf("FindOrderList"), model.designBlocks.filter { it.tag == "query" }.map { it.name })
        assertEquals(listOf("SyncStock"), model.designBlocks.filter { it.tag == "client" }.map { it.name })
    }

    @Test
    fun `query names with list and page suffixes keep explicit design block artifacts only`() {
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
                        ),
                        DesignSpecEntry(
                            tag = "query",
                            packageName = "order.read",
                            name = "FindOrderPage",
                            description = "find order page",
                            aggregates = emptyList(),
                        ),
                    )
                ),
            ),
        )

        assertEquals(
            listOf(
                listOf(ArtifactSelectionModel("query"), ArtifactSelectionModel("query-handler")),
                listOf(ArtifactSelectionModel("query"), ArtifactSelectionModel("query-handler")),
            ),
            result.model.designBlocks.map { it.artifacts },
        )
    }

    @Test
    fun `assembler carries page artifacts on query and api payload design blocks`() {
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
                            artifacts = listOf(ArtifactSelectionModel("query", "page")),
                        ),
                        DesignSpecEntry(
                            tag = "api_payload",
                            packageName = "order.read",
                            name = "FindOrderPage",
                            description = "find order page payload",
                            aggregates = emptyList(),
                            artifacts = listOf(ArtifactSelectionModel("api-payload", "page")),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                ArtifactSelectionModel("query", "page"),
                ArtifactSelectionModel("api-payload", "page"),
            ),
            result.model.designBlocks.map { it.artifacts.single() },
        )
    }

    @Test
    fun `assembles domain services and value objects`() {
        val design = DesignSpecSnapshot(
            entries = listOf(
                DesignSpecEntry(
                    tag = "domain_service",
                    packageName = "content.domain",
                    name = "ContentPublicationPolicy",
                    description = "publication policy",
                    aggregates = listOf("Content"),
                ),
                DesignSpecEntry(
                    tag = "saga",
                    packageName = "content.workflow",
                    name = "PublishContentSaga",
                    description = "publish content",
                    aggregates = emptyList(),
                    fields = listOf(FieldModel(name = "contentId", type = "ContentId")),
                ),
            )
        )
        val valueObjects = ValueObjectManifestSnapshot(
            valueObjects = listOf(
                ValueObjectModel(
                    name = "Money",
                    packageName = "shared.values",
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
        val sagaBlock = model.designBlocks.single { it.tag == "saga" }
        assertEquals("PublishContentSaga", sagaBlock.name)
        assertEquals(listOf("contentId"), sagaBlock.fields.map { it.name })
        assertEquals(emptyList<FieldModel>(), sagaBlock.resultFields)
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
                    aggregates = listOf("Content"),
                ),
                ValueObjectModel(
                    name = "Snapshot",
                    packageName = "review.values",
                    aggregates = listOf("Review"),
                ),
            )
        )

        val model = assemble(valueObjects = valueObjects)

        assertEquals(listOf(listOf("Content"), listOf("Review")), model.valueObjects.map { it.aggregates })
    }

    @Test
    fun `same aggregate local enum and value object with same simple name fail duplicate validation`() {
        val valueObjects = ValueObjectManifestSnapshot(
            valueObjects = listOf(
                ValueObjectModel(
                    name = "Status",
                    packageName = "content.values",
                    aggregates = listOf("Content"),
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
    fun `assembler rejects enum manifest with more than one aggregate`() {
        val enumManifest = EnumManifestSnapshot(
            definitions = listOf(
                SharedEnumDefinition(
                    typeName = "OrderStatus",
                    packageName = "order.enums",
                    items = listOf(EnumItemModel(1, "PAID", "Paid")),
                    aggregates = listOf("Order", "Payment"),
                ),
            )
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DefaultCanonicalAssembler().assemble(
                config = baseAggregateConfig(),
                snapshots = listOf(enumManifest),
            )
        }

        assertEquals("enum OrderStatus may declare at most one aggregate", error.message)
    }

    @Test
    fun `assembler rejects value object manifest with more than one aggregate`() {
        val valueObjects = ValueObjectManifestSnapshot(
            valueObjects = listOf(
                ValueObjectModel(
                    name = "Money",
                    packageName = "shared.values",
                    aggregates = listOf("Order", "Payment"),
                ),
            )
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            assemble(valueObjects = valueObjects)
        }

        assertEquals("value object Money may declare at most one aggregate", error.message)
    }

    @Test
    fun `fails on duplicate simple type names across enum value object and registry`() {
        val valueObjects = ValueObjectManifestSnapshot(
            valueObjects = listOf(
                ValueObjectModel(
                    name = "Status",
                    packageName = "shared.values",
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
                                eventName = " ",
                                artifacts = listOf(ArtifactSelectionModel("integration-event", "inbound")),
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
                                eventName = "order.created",
                                artifacts = listOf(ArtifactSelectionModel("integration-event", "inbound")),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals("integration_event OrderCreated must declare at least one fields entry.", error.message)
    }

    @Test
    fun `client design rejects legacy aliases`() {
        val assembler = DefaultCanonicalAssembler()

        listOf("client_legacy_alias", "clients_legacy_alias").forEach { tag ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                assembler.assemble(
                    config = baseConfig(),
                    snapshots = listOf(
                        DesignSpecSnapshot(
                            entries = listOf(
                                DesignSpecEntry(
                                    tag = tag,
                                    packageName = "auth.client",
                                    name = "IssueToken",
                                    description = "issue token",
                                    aggregates = emptyList(),
                                ),
                            )
                        ),
                    ),
                )
            }

            assertEquals("Unsupported design tag: $tag", error.message)
        }
    }

    @Test
    fun `client entries keep command and query design block mappings unchanged`() {
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
                        ),
                        DesignSpecEntry(
                            tag = "client",
                            packageName = "order.remote",
                            name = "IssueToken",
                            description = "issue token",
                            aggregates = listOf("Order"),
                        ),
                        DesignSpecEntry(
                            tag = "query",
                            packageName = "order.read",
                            name = "FindOrder",
                            description = "find order",
                            aggregates = listOf("Order"),
                        ),
                    )
                ),
                aggregateSnapshot("order"),
            ),
        ).model

        assertEquals(listOf("SubmitOrder"), model.designBlocks.filter { it.tag == "command" }.map { it.name })
        assertEquals(listOf("IssueToken"), model.designBlocks.filter { it.tag == "client" }.map { it.name })
        assertEquals(listOf("FindOrder"), model.designBlocks.filter { it.tag == "query" }.map { it.name })
        assertEquals(listOf(listOf("Order"), listOf("Order"), listOf("Order")), model.designBlocks.map { it.aggregates })
    }

    @Test
    fun `api payload entries assemble into design blocks and keep command assembly unchanged`() {
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
                            fields = requestFields,
                            resultFields = responseFields,
                        ),
                        DesignSpecEntry(
                            tag = "command",
                            packageName = "order.submit",
                            name = "SubmitOrder",
                            description = "submit order",
                            aggregates = listOf("Order"),
                            fields = listOf(FieldModel(name = "orderId", type = "Long")),
                        ),
                    )
                ),
            ),
        ).model

        assertEquals(1, model.designBlocks.count { it.tag == "api_payload" })
        val payload = model.designBlocks.single { it.tag == "api_payload" }
        assertEquals("auth.payload", payload.packageName)
        assertEquals("batchSaveAccountList", payload.name)
        assertEquals("batch save account payload", payload.description)
        assertEquals(requestFields, payload.fields)
        assertEquals(responseFields, payload.resultFields)

        assertEquals(1, model.designBlocks.count { it.tag == "command" })
        val command = model.designBlocks.single { it.tag == "command" }
        assertEquals("order.submit", command.packageName)
        assertEquals("SubmitOrder", command.name)
        assertEquals("submit order", command.description)
        assertEquals(listOf("Order"), command.aggregates)
        assertEquals(listOf(FieldModel(name = "orderId", type = "Long")), command.fields)
        assertEquals(emptyList<FieldModel>(), command.resultFields)
    }

    @Test
    fun `api payload design block rejects legacy payload aliases`() {
        val assembler = DefaultCanonicalAssembler()

        listOf("payload_legacy_alias", "request_payload", "req_payload", "request", "req").forEach { tag ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                assembler.assemble(
                    config = baseConfig(),
                    snapshots = listOf(
                        DesignSpecSnapshot(
                            entries = listOf(
                                DesignSpecEntry(
                                    tag = tag,
                                    packageName = "auth.payload",
                                    name = "LegacyPayload",
                                    description = "legacy payload alias",
                                    aggregates = emptyList(),
                                ),
                            )
                        ),
                    ),
                )
            }

            assertEquals("Unsupported design tag: $tag", error.message)
        }
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
                            fields = listOf(
                                FieldModel(name = "reason", type = "String"),
                                FieldModel(name = "snapshot", type = "Snapshot", nullable = true),
                                FieldModel(name = "snapshot.traceId", type = "UUID"),
                            ),
                        ),
                        DesignSpecEntry(
                            tag = "domain_event",
                            packageName = "order",
                            name = "OrderCreatedEvt",
                            description = "evt naming keeps suffix",
                            aggregates = listOf("Order"),
                        ),
                        DesignSpecEntry(
                            tag = "domain_event",
                            packageName = "order",
                            name = "OrderCreatedEvent",
                            description = "event naming keeps suffix",
                            aggregates = listOf("Order"),
                        ),
                        DesignSpecEntry(
                            tag = "domain_event",
                            packageName = "order",
                            name = "order_created",
                            description = "snake case",
                            aggregates = listOf("Order"),
                        ),
                        DesignSpecEntry(
                            tag = "domain_event",
                            packageName = "order",
                            name = "order-created",
                            description = "kebab case",
                            aggregates = listOf("Order"),
                        ),
                        DesignSpecEntry(
                            tag = "domain_event",
                            packageName = "order",
                            name = "order created event",
                            description = "space separated words",
                            aggregates = listOf("Order"),
                        ),
                        DesignSpecEntry(
                            tag = "domain_event",
                            packageName = "order",
                            name = "orderCreated",
                            description = "lower camel",
                            aggregates = listOf("Order"),
                        ),
                    )
                ),
                aggregateSnapshot("order"),
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
                        ),
                    )
                ),
                aggregateSnapshot("user_message"),
            ),
        ).model

        val event = model.domainEvents.single()
        assertEquals("user_message", event.packageName)
        assertEquals("UserMessage", event.aggregateName)
        assertEquals("com.acme.demo.domain.aggregates.user_message", event.aggregatePackageName)
    }

    @Test
    fun `domain event resolves aggregate package from canonical aggregate entities`() {
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
            ),
        ).model

        assertEquals("Order", model.domainEvents.single().aggregateName)
        assertEquals("com.acme.demo.domain.aggregates.order", model.domainEvents.single().aggregatePackageName)
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
                        ),
                    )
                ),
                aggregateSnapshot("order"),
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
                            ),
                        )
                    ),
                    aggregateSnapshot("order"),
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
                            ),
                        )
                    ),
                    aggregateSnapshot("order"),
                ),
            )
        }

        assertEquals("domain_event UnknownAggregate references missing aggregate metadata: Unknown", error.message)
    }

    @Test
    fun `maps design entries and canonical aggregates into design blocks`() {
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
                            fields = listOf(FieldModel(name = "orderId", type = "Long")),
                        ),
                        DesignSpecEntry(
                            tag = "query",
                            packageName = "order.read",
                            name = "FindOrder",
                            description = "find order",
                            aggregates = listOf("Order"),
                            fields = listOf(FieldModel(name = "orderId", type = "Long")),
                            resultFields = listOf(FieldModel(name = "status", type = "String")),
                        ),
                    )
                ),
                aggregateSnapshot("order"),
            ),
        ).model

        assertEquals(2, model.designBlocks.size)
        val command = model.designBlocks.single { it.tag == "command" }
        val query = model.designBlocks.single { it.tag == "query" }
        assertEquals("SubmitOrder", command.name)
        assertEquals("order.submit", command.packageName)
        assertEquals("submit order", command.description)
        assertEquals(listOf("Order"), command.aggregates)
        assertEquals(listOf(FieldModel(name = "orderId", type = "Long")), command.fields)
        assertEquals(emptyList<FieldModel>(), command.resultFields)
        assertEquals("FindOrder", query.name)
        assertEquals("order.read", query.packageName)
        assertEquals("find order", query.description)
        assertEquals(listOf("Order"), query.aggregates)
        assertEquals(listOf(FieldModel(name = "orderId", type = "Long")), query.fields)
        assertEquals(listOf(FieldModel(name = "status", type = "String")), query.resultFields)
    }

    @Test
    fun `design spec assembly rejects unsupported tags`() {
        val assembler = DefaultCanonicalAssembler()

        val error = assertThrows(IllegalArgumentException::class.java) {
            assembler.assemble(
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
                            ),
                        )
                    ),
                ),
            )
        }

        assertEquals("Unsupported design tag: evt", error.message)
    }

    @Test
    fun `design spec assembly rejects non exact canonical tags and historical aliases`() {
        val assembler = DefaultCanonicalAssembler()

        listOf(
            "COMMAND",
            "Query",
            "Client",
            "API_PAYLOAD",
            "DOMAIN_EVENT",
            "cmd",
            "qry",
            "cli",
            "clients",
            "payload",
            "de",
            "domain-event",
        ).forEach { tag ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                assembler.assemble(
                    config = baseConfig(),
                    snapshots = listOf(
                        DesignSpecSnapshot(
                            entries = listOf(
                                DesignSpecEntry(
                                    tag = tag,
                                    packageName = "order.submit",
                                    name = "UnsupportedTagBlock",
                                    description = "unsupported tag",
                                    aggregates = emptyList(),
                                ),
                            )
                        ),
                    ),
                )
            }

            assertEquals("Unsupported design tag: $tag", error.message)
        }
    }

    @Test
    fun `returns empty model when design snapshot is missing`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = baseConfig(),
            snapshots = listOf(
                aggregateSnapshot("order"),
            ),
        ).model

        assertEquals(0, model.designBlocks.size)
    }

    @Test
    fun `leaves request aggregate ref null when canonical aggregate metadata has no match`() {
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
                        ),
                    )
                ),
                aggregateSnapshot("customer"),
            ),
        ).model

        assertEquals(1, model.designBlocks.count { it.tag == "query" })
        assertEquals(listOf("Order"), model.designBlocks.single { it.tag == "query" }.aggregates)
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
                            artifacts = listOf(ArtifactSelectionModel(family = "command")),
                            persist = true,
                            fields = listOf(supportedField),
                            resultFields = listOf(responseField),
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
                            artifacts = listOf(ArtifactSelectionModel("query", "page")),
                        ),
                        DesignElementSnapshot(
                            tag = "api_payload",
                            packageName = "order.payload",
                            name = "CreateOrderPayload",
                            description = "create order payload",
                            artifacts = listOf(ArtifactSelectionModel("api-payload", "page")),
                        ),
                        DesignElementSnapshot(
                            tag = "domain_event",
                            packageName = "order.events",
                            name = "OrderCreatedDomainEvent",
                            description = "order created",
                            aggregates = listOf("Order"),
                            persist = false,
                            fields = listOf(entityField, reasonField),
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
                artifacts = listOf(ArtifactSelectionModel(family = "command")),
                persist = true,
                fields = listOf(DrawingBoardFieldModel(name = "orderId", type = "Long", nullable = false, defaultValue = "0")),
                resultFields = listOf(DrawingBoardFieldModel(name = "accepted", type = "Boolean")),
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
        assertEquals(listOf(ArtifactSelectionModel("query", "page")), query.designJsonArtifacts)
        val apiPayload = drawingBoard.elementsByTag.getValue("api_payload").single()
        assertEquals("CreateOrderPayload", apiPayload.name)
        assertEquals(listOf(ArtifactSelectionModel("api-payload", "page")), apiPayload.designJsonArtifacts)

        val domainEvent = drawingBoard.elementsByTag.getValue("domain_event").single()
        assertEquals(listOf(DrawingBoardFieldModel(name = "reason", type = "String")), domainEvent.fields)
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
                            fields = listOf(
                                DesignFieldSnapshot(name = "orderId", type = "Long"),
                                DesignFieldSnapshot(name = "buyerId", type = "Long"),
                            ),
                            artifacts = listOf(ArtifactSelectionModel("integration-event", "inbound")),
                            eventName = "order.created",
                        ),
                    ),
                ),
            ),
        )

        val board = requireNotNull(result.model.drawingBoard)
        val integrationEvent = board.elements.single()
        assertEquals("integration_event", integrationEvent.tag)
        assertEquals(listOf(ArtifactSelectionModel("integration-event", "inbound")), integrationEvent.designJsonArtifacts)
        assertEquals("order.created", integrationEvent.eventName)
        assertEquals(listOf("orderId", "buyerId"), integrationEvent.fields.map { it.name })
        assertEquals(listOf("integration_event"), board.elementsByTag.keys.toList())
    }

    @Test
    fun `drawing board accepts domain service and saga recovered design blocks with default artifacts omitted`() {
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
                            tag = "domain_service",
                            packageName = "order.domain",
                            name = "OrderPolicyService",
                            description = "order policy service",
                            aggregates = listOf("Order"),
                            artifacts = listOf(ArtifactSelectionModel("domain-service")),
                        ),
                        DesignElementSnapshot(
                            tag = "saga",
                            packageName = "order.application",
                            name = "PublishOrderSaga",
                            description = "publish order saga",
                            aggregates = listOf("Order"),
                            artifacts = listOf(ArtifactSelectionModel("saga")),
                            fields = listOf(DesignFieldSnapshot(name = "orderId", type = "Long")),
                            resultFields = listOf(DesignFieldSnapshot(name = "accepted", type = "Boolean")),
                        ),
                    ),
                ),
            ),
        )

        val board = requireNotNull(result.model.drawingBoard)
        assertEquals(listOf("domain_service", "saga"), board.elementsByTag.keys.toList())

        val domainService = board.elementsByTag.getValue("domain_service").single()
        assertEquals("OrderPolicyService", domainService.name)
        assertEquals(listOf("Order"), domainService.aggregates)
        assertFalse(domainService.includeDesignJsonArtifacts)

        val saga = board.elementsByTag.getValue("saga").single()
        assertEquals("PublishOrderSaga", saga.name)
        assertEquals(listOf("orderId"), saga.fields.map { it.name })
        assertEquals(listOf("accepted"), saga.resultFields.map { it.name })
        assertFalse(saga.includeDesignJsonArtifacts)
    }

    @Test
    fun `recovered design elements populate drawing board without authoring design blocks`() {
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
                            aggregates = listOf("Order"),
                            artifacts = listOf(ArtifactSelectionModel("query", "page")),
                            fields = listOf(DesignFieldSnapshot(name = "orderId", type = "Long")),
                            resultFields = listOf(DesignFieldSnapshot(name = "status", type = "String")),
                        ),
                        DesignElementSnapshot(
                            tag = "domain_event",
                            packageName = "order.events",
                            name = "OrderCreated",
                            description = "order created",
                            aggregates = listOf("Order"),
                            persist = true,
                            fields = listOf(
                                DesignFieldSnapshot(name = "entity", type = "Order"),
                                DesignFieldSnapshot(name = "reason", type = "String"),
                            ),
                        ),
                        DesignElementSnapshot(
                            tag = "saga",
                            packageName = "order.application",
                            name = "PublishOrderSaga",
                            description = "publish order saga",
                            aggregates = listOf("Order"),
                            fields = listOf(DesignFieldSnapshot(name = "orderId", type = "Long")),
                            resultFields = listOf(DesignFieldSnapshot(name = "accepted", type = "Boolean")),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(emptyList<DesignBlockModel>(), result.model.designBlocks)
        val board = requireNotNull(result.model.drawingBoard)
        assertEquals(listOf("query", "domain_event", "saga"), board.elementsByTag.keys.toList())
        assertEquals(listOf(ArtifactSelectionModel("query", "page")), board.elementsByTag.getValue("query").single().designJsonArtifacts)
        assertEquals(listOf("reason"), board.elementsByTag.getValue("domain_event").single().fields.map { it.name })
        assertEquals(listOf("accepted"), board.elementsByTag.getValue("saga").single().resultFields.map { it.name })
    }

    @Test
    fun `recovered artifacts do not merge into matching design source block`() {
        val assembler = DefaultCanonicalAssembler()

        val result = assembler.assemble(
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
                            artifacts = listOf(ArtifactSelectionModel("query")),
                            fields = listOf(FieldModel(name = "orderId", type = "Long")),
                            resultFields = listOf(FieldModel(name = "status", type = "String")),
                        )
                    )
                ),
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
                            aggregates = listOf("Order"),
                            artifacts = listOf(ArtifactSelectionModel("query-handler")),
                            fields = listOf(DesignFieldSnapshot(name = "orderId", type = "Long")),
                            resultFields = listOf(DesignFieldSnapshot(name = "status", type = "String")),
                        )
                    ),
                ),
            ),
        )

        assertEquals(listOf(ArtifactSelectionModel("query")), result.model.designBlocks.single().artifacts)
        assertEquals(
            listOf(ArtifactSelectionModel("query-handler")),
            requireNotNull(result.model.drawingBoard).elements.single().designJsonArtifacts,
        )
    }

    @Test
    fun `drawing board merges duplicate recovered design elements`() {
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
                            artifacts = listOf(ArtifactSelectionModel("query")),
                            fields = listOf(DesignFieldSnapshot(name = "orderId", type = "Long")),
                        ),
                        DesignElementSnapshot(
                            tag = "query",
                            packageName = "order.read",
                            name = "FindOrder",
                            description = "find order",
                            artifacts = listOf(ArtifactSelectionModel("query-handler")),
                            fields = listOf(DesignFieldSnapshot(name = "orderId", type = "Long")),
                        ),
                    ),
                ),
            ),
        )

        val query = requireNotNull(result.model.drawingBoard).elements.single()
        assertEquals(
            listOf(ArtifactSelectionModel("query"), ArtifactSelectionModel("query-handler")),
            query.designJsonArtifacts,
        )
    }

    @Test
    fun `canonical design blocks reject recovered default variant alias`() {
        val assembler = DefaultCanonicalAssembler()

        val error = assertThrows(IllegalArgumentException::class.java) {
            assembler.assemble(
                config = baseConfig(),
                snapshots = listOf(
                    IrAnalysisSnapshot(
                        inputDirs = emptyList(),
                        nodes = emptyList(),
                        edges = emptyList(),
                        designElements = listOf(
                            DesignElementSnapshot(
                                tag = "command",
                                packageName = "order.submit",
                                name = "SubmitOrder",
                                description = "submit order",
                                artifacts = listOf(ArtifactSelectionModel("command", "default")),
                            )
                        ),
                    ),
                ),
            )
        }

        assertEquals("design entry SubmitOrder artifact command has unsupported variant: default", error.message)
    }

    @Test
    fun `canonical design blocks reject recovered unknown artifacts`() {
        val assembler = DefaultCanonicalAssembler()

        val error = assertThrows(IllegalArgumentException::class.java) {
            assembler.assemble(
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
                                artifacts = listOf(ArtifactSelectionModel("design-query")),
                            )
                        ),
                    ),
                ),
            )
        }

        assertEquals("unsupported design artifact family on FindOrder: design-query", error.message)
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
        val messageJpa = result.aggregateEntityJpa.single { it.entityName == "UserMessage" }

        assertEquals("com.acme.demo.domain.model.user_message", messageEntity.packageName)
        assertEquals("com.acme.demo.domain.meta.user_message", messageSchema.packageName)
        assertEquals("com.acme.demo.adapter.persistence.repositories", messageRepository.packageName)
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
                        ),
                        ValueObjectModel(
                            name = "PublishWindow",
                            packageName = "com.acme.demo.domain.aggregates.content.values",
                            aggregates = listOf("Content"),
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
                        ),
                    )
                ),
                ValueObjectManifestSnapshot(
                    valueObjects = listOf(
                        ValueObjectModel(
                            name = "PublishWindow",
                            packageName = "com.acme.demo.domain.aggregates.content.values",
                            aggregates = listOf("Content"),
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
                        ),
                        ValueObjectModel(
                            name = "PublishWindow",
                            packageName = "content.values.secondary",
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
                                aggregates = listOf("Content"),
                            ),
                            ValueObjectModel(
                                name = "PublishWindow",
                                packageName = "content.values.secondary",
                                aggregates = listOf("Content"),
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
                                    idStrategy = DbIdStrategy.DB_IDENTITY
                                ),
                                DbColumnSnapshot("version", "BIGINT", "Long", false, managedRole = DbManagedRole.VERSION),
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
        assertEquals("IDENTITY", controls.single { it.fieldName == "id" }.generatedValueStrategy)
        assertEquals(true, controls.single { it.fieldName == "version" }.version)
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
    fun `assembler ignores unmarked version persistence control`() {
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
    fun `assembler does not record managed role columns as persistence field controls`() {
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
                                DbColumnSnapshot("created_by", "VARCHAR", "String", false, managedRole = DbManagedRole.SYSTEM),
                                DbColumnSnapshot("display_name", "VARCHAR", "String", false, managedRole = DbManagedRole.SCOPE),
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
    fun `db inherited columns remain canonical fields with inherited flag`() {
        val snapshot = DbSchemaSnapshot(
            tables = listOf(
                DbTableSnapshot(
                    tableName = "content",
                    comment = "",
                    columns = listOf(
                        DbColumnSnapshot("id", "VARCHAR", "String", false, isPrimaryKey = true),
                        DbColumnSnapshot("created_at", "TIMESTAMP", "java.time.Instant", false, inherited = true, managedRole = DbManagedRole.SYSTEM),
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
                                    idStrategy = DbIdStrategy.DB_IDENTITY
                                ),
                                DbColumnSnapshot("version", "BIGINT", "Long", false, managedRole = DbManagedRole.VERSION),
                                DbColumnSnapshot("deleted", "INT", "Int", false, managedRole = DbManagedRole.DELETED),
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
        assertNull(control.softDeleteColumn)
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
                                DbColumnSnapshot("version", "BIGINT", "Long", false, managedRole = DbManagedRole.VERSION),
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
        assertNull(control.softDeleteColumn)
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
                                idStrategy = DbIdStrategy.DB_IDENTITY,
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
                            fields = listOf(FieldModel("authorId", "AuthorId")),
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

        val command = result.model.designBlocks.single { it.name == "CreateContent" }

        assertEquals("AuthorId", command.fields.single { it.name == "authorId" }.type)
        assertEquals("AuthorId", result.model.strongIds.single { it.typeName == "AuthorId" }.typeName)
        assertEquals(1, result.model.designBlocks.count { it.tag == "command" })
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
                            idStrategy = DbIdStrategy.DB_IDENTITY,
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
                        column("id", "BIGINT", "Long", false, primaryKey = true, idStrategy = DbIdStrategy.DB_IDENTITY),
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
        assertEquals("identity", policy.id.strategy)
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
                            idStrategy = DbIdStrategy.DB_IDENTITY,
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
    fun `managed role scope is carried into special field resolution`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "uuid7"),
            tables = listOf(
                table(
                    name = "video_post",
                    columns = listOf(
                        column("id", "BIGINT", "Long", false, primaryKey = true),
                        column("tenant_id", "BIGINT", "Long", false, managedRole = DbManagedRole.SCOPE),
                        column("deleted", "INT", "Int", false, managedRole = DbManagedRole.DELETED),
                    ),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        val policy = result.model.aggregateSpecialFieldResolvedPolicies.single()

        assertEquals("tenantId", policy.managedFields.single { it.columnName == "tenant_id" }.fieldName)
        assertEquals(DbManagedRole.SCOPE, policy.managedFields.single { it.columnName == "tenant_id" }.managedRole)
        assertEquals("deleted", policy.deleted.columnName)
    }

    @Test
    fun `inherited managed fields remain visible to canonical policy`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "uuid7"),
            tables = listOf(
                table(
                    name = "video_post",
                    columns = listOf(
                        column("id", "BIGINT", "Long", false, primaryKey = true),
                        column("created_at", "TIMESTAMP", "java.time.Instant", false, managedRole = DbManagedRole.SYSTEM, inherited = true),
                    ),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        val policy = result.model.aggregateSpecialFieldResolvedPolicies.single()

        assertTrue(policy.managedFields.any { it.columnName == "created_at" && it.managedRole == DbManagedRole.SYSTEM })
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
                            idStrategy = DbIdStrategy.DB_IDENTITY,
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
                            idStrategy = DbIdStrategy.DB_IDENTITY,
                        ),
                        column("created_by", "VARCHAR", "String", false, managedRole = DbManagedRole.SYSTEM),
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
    fun `db managed role overrides dsl managed default and remains read only`() {
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
                            idStrategy = DbIdStrategy.DB_IDENTITY,
                        ),
                        column("created_by", "VARCHAR", "String", false, managedRole = DbManagedRole.SCOPE),
                        column("title", "VARCHAR", "String", false),
                    ),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        val policy = result.model.aggregateSpecialFieldResolvedPolicies.single()

        assertEquals(listOf("id", "created_by"), policy.managedFields.map { it.columnName })
        assertEquals(listOf("title"), policy.writeSurface.createAllowedFields)
        assertEquals(listOf("title"), policy.writeSurface.updateAllowedFields)
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
                        column(name = "is_deleted", dbType = "INT", kotlinType = "Int", nullable = false, managedRole = DbManagedRole.DELETED),
                        column(name = "deleted", dbType = "INT", kotlinType = "Int", nullable = false),
                    ),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        val policy = result.model.aggregateSpecialFieldResolvedPolicies.single()

        assertEquals(true, policy.deleted.enabled)
        assertEquals("isDeleted", policy.deleted.fieldName)
        assertEquals("is_deleted", policy.deleted.columnName)
        assertEquals(SpecialFieldSource.DB_EXPLICIT, policy.deleted.source)
        assertTrue(result.model.aggregatePersistenceProviderControls.isEmpty())
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
                            idStrategy = DbIdStrategy.DB_IDENTITY,
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
                                        idStrategy = DbIdStrategy.DB_IDENTITY,
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
                                    DbColumnSnapshot("deleted", "INT", "Int", false, managedRole = DbManagedRole.DELETED),
                                    DbColumnSnapshot("is_deleted", "INT", "Int", false, managedRole = DbManagedRole.DELETED),
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
                                    DbColumnSnapshot("version", "BIGINT", "Long", false, managedRole = DbManagedRole.VERSION),
                                    DbColumnSnapshot("lock_version", "BIGINT", "Long", false, managedRole = DbManagedRole.VERSION),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
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
        assertEquals(null, root.parentEntityName)

        val child = result.model.entities.first { it.name == "VideoPostItem" }
        assertEquals(false, child.aggregateRoot)
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
    @Disabled("stale relation metadata contract removed by Task 4 redesign")
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
    fun `owned parent binding fails without parent ref`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            assembleAggregate(
                aggregateProjectConfig(),
                listOf(
                    table(
                        name = "video_post_item",
                        parentTable = "video_post",
                        columns = listOf(
                            column("id", "BIGINT", "Long", false, primaryKey = true),
                            column("video_post_id", "BIGINT", "Long", false),
                        ),
                        primaryKey = listOf("id"),
                        aggregateRoot = false,
                    ),
                ),
            )
        }

        assertEquals("missing parent reference column for table: video_post_item", error.message)
    }

    @Test
    fun `owned parent binding fails with more than one parent ref`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            assembleAggregate(
                aggregateProjectConfig(),
                listOf(
                    table(
                        name = "video_post_item",
                        parentTable = "video_post",
                        columns = listOf(
                            column("id", "BIGINT", "Long", false, primaryKey = true),
                            column("video_post_id", "BIGINT", "Long", false, parentRef = true),
                            column("source_video_post_id", "BIGINT", "Long", false, parentRef = true),
                        ),
                        primaryKey = listOf("id"),
                        aggregateRoot = false,
                    ),
                ),
            )
        }

        assertEquals(
            "ambiguous parent reference columns for table video_post_item: source_video_post_id, video_post_id",
            error.message,
        )
    }

    @Test
    fun `owned parent binding ignores weak reference metadata without parent ref`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            assembleAggregate(
                aggregateProjectConfig(),
                listOf(
                    table(
                        name = "video_post",
                        columns = listOf(
                            column("id", "BIGINT", "Long", false, primaryKey = true),
                        ),
                        primaryKey = listOf("id"),
                        aggregateRoot = true,
                    ),
                    table(
                        name = "video_post_item",
                        parentTable = "video_post",
                        columns = listOf(
                            column("id", "BIGINT", "Long", false, primaryKey = true),
                            column("video_post_id", "BIGINT", "Long", false, refAggregate = "VideoPost"),
                        ),
                        primaryKey = listOf("id"),
                        aggregateRoot = false,
                    ),
                ),
            )
        }

        assertEquals("missing parent reference column for table: video_post_item", error.message)
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
    @Disabled("stale relation metadata contract removed by Task 4 redesign")
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
                ),
            ),
        )

        assertEquals(
            listOf("VideoPostItem|videoPost|VideoPost|MANY_TO_ONE|video_post_id"),
            inverseRelations.map { "${it.ownerEntityName}|${it.fieldName}|${it.targetEntityName}|${it.relationType}|${it.joinColumn}" },
        )
    }

    @Test
    @Disabled("stale relation metadata contract removed by Task 4 redesign")
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
    @Disabled("stale relation metadata contract removed by Task 4 redesign")
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
    @Disabled("stale relation metadata contract removed by Task 4 redesign")
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
                            ),
                        )
                    )
                )
            )
        }

        assertEquals("ambiguous parent reference columns for table video_post_item -> video_post: source_video_post_id, video_post_id", error.message)
    }

    @Test
    @Disabled("stale relation metadata contract removed by Task 4 redesign")
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
    @Disabled("stale relation metadata contract removed by Task 4 redesign")
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
    @Disabled("stale relation metadata contract removed by Task 4 redesign")
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
    @Disabled("stale relation metadata contract removed by Task 4 redesign")
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
    @Disabled("stale relation metadata contract removed by Task 4 redesign")
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
    @Disabled("stale relation metadata contract removed by Task 4 redesign")
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
    @Disabled("stale relation metadata contract removed by Task 4 redesign")
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
    @Disabled("stale relation metadata contract removed by Task 4 redesign")
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
    @Disabled("stale relation metadata contract removed by Task 4 redesign")
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
    @Disabled("stale relation metadata contract removed by Task 4 redesign")
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
    @Disabled("stale relation metadata contract removed by Task 4 redesign")
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
    )

    private fun DbColumnSnapshot(
        name: String,
        dbType: String,
        kotlinType: String,
        nullable: Boolean,
        defaultValue: String? = null,
        comment: String = "",
        isPrimaryKey: Boolean = false,
        typeBinding: String? = null,
        enumItems: List<EnumItemModel> = emptyList(),
        parentRef: Boolean = false,
        refAggregate: String? = null,
        refId: String? = null,
        idStrategy: DbIdStrategy? = null,
        managedRole: DbManagedRole? = null,
        inherited: Boolean? = null,
        // TODO(Task 4 cleanup): these stale relation parameters only keep disabled legacy tests compiling.
        // Active relation-contract tests should use parentRef/refAggregate/refId directly.
        referenceTable: String? = null,
        explicitRelationType: String? = null,
        lazy: Boolean? = null,
    ): DbColumnSnapshot = com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot(
        name = name,
        dbType = dbType,
        kotlinType = kotlinType,
        nullable = nullable,
        defaultValue = defaultValue,
        comment = comment,
        isPrimaryKey = isPrimaryKey,
        typeBinding = typeBinding,
        enumItems = enumItems,
        parentRef = parentRef || !referenceTable.isNullOrBlank(),
        refAggregate = refAggregate,
        refId = refId,
        idStrategy = idStrategy,
        managedRole = managedRole,
        inherited = inherited,
    )


    private fun column(
        name: String,
        dbType: String,
        kotlinType: String,
        nullable: Boolean,
        primaryKey: Boolean = false,
        // TODO(Task 4 cleanup): this stale relation alias is compatibility-only for legacy tests.
        // Active relation-contract tests should use parentRef/refAggregate/refId directly.
        referenceTable: String? = null,
        generatedValueDeclared: Boolean = false,
        generatedValueStrategy: String? = null,
        deleted: Boolean? = null,
        version: Boolean? = null,
        managed: Boolean? = null,
        exposed: Boolean? = null,
        parentRef: Boolean = false,
        refAggregate: String? = null,
        refId: String? = null,
        idStrategy: DbIdStrategy? = null,
        managedRole: DbManagedRole? = null,
        inherited: Boolean? = null,
    ): DbColumnSnapshot = DbColumnSnapshot(
        name = name,
        dbType = dbType,
        kotlinType = kotlinType,
        nullable = nullable,
        isPrimaryKey = primaryKey,
        parentRef = parentRef || !referenceTable.isNullOrBlank(),
        refAggregate = refAggregate,
        refId = refId,
        idStrategy = idStrategy ?: if (generatedValueDeclared || generatedValueStrategy != null) DbIdStrategy.DB_IDENTITY else null,
        managedRole = managedRole ?: when {
            deleted == true -> DbManagedRole.DELETED
            version == true -> DbManagedRole.VERSION
            managed == true -> DbManagedRole.SYSTEM
            exposed == true -> DbManagedRole.SCOPE
            else -> null
        },
        inherited = inherited,
    )

    private fun aggregateSnapshot(
        aggregateName: String,
        packageName: String = "com.acme.demo.domain.aggregates.$aggregateName",
        className: String = aggregateName.replaceFirstChar { it.uppercase() },
    ): DbSchemaSnapshot = DbSchemaSnapshot(
        tables = listOf(
            DbTableSnapshot(
                tableName = aggregateName,
                comment = "",
                columns = listOf(
                    DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                ),
                primaryKey = listOf("id"),
                uniqueConstraints = emptyList(),
                aggregateRoot = true,
            ),
        ),
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
