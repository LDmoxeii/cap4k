package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateMetadataRecord
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignSpecEntry
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.KspMetadataSnapshot
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
        )

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
        )

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
        )

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
        )

        assertEquals(1, model.requests.size)
        assertEquals("Order", model.requests.first().aggregateName)
        assertNull(model.requests.first().aggregatePackageName)
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
        )

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
        )

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
        )

        assertEquals("SVideoPost", model.schemas.single().name)
        assertEquals("VideoPost", model.entities.single().name)
        assertEquals("VideoPostRepository", model.repositories.single().name)
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

        assertEquals("db table audit_log must define a primary key", error.message)
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

        assertEquals("db table audit_log must define a single-column primary key", error.message)
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

    private fun baseAggregateConfig(): ProjectConfig {
        return ProjectConfig(
            basePackage = "com.acme.demo",
            layout = MULTI_MODULE,
            modules = mapOf(
                "domain" to "demo-domain",
                "adapter" to "demo-adapter",
            ),
            sources = emptyMap(),
            generators = emptyMap(),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
    }
}
