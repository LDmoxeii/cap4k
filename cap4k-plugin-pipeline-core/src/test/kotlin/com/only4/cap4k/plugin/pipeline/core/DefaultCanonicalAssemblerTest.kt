package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateMetadataRecord
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignSpecEntry
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.KspMetadataSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout.MULTI_MODULE
import com.only4.cap4k.plugin.pipeline.api.RequestKind
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DefaultCanonicalAssemblerTest {

    @Test
    fun `maps design entries and ksp aggregates into canonical requests`() {
        val assembler = DefaultCanonicalAssembler()

        val model = assembler.assemble(
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = MULTI_MODULE,
                modules = mapOf("application" to ":demo-application"),
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

    private fun baseConfig(): ProjectConfig {
        return ProjectConfig(
            basePackage = "com.acme.demo",
            layout = MULTI_MODULE,
            modules = mapOf("application" to ":demo-application"),
            sources = emptyMap(),
            generators = emptyMap(),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
    }
}
