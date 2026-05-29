package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignIntegrationEventArtifactPlannerTest {

    @Test
    fun `plans inbound and outbound integration event contracts into application integration event paths`() {
        val planner = DesignIntegrationEventArtifactPlanner()
        assertEquals("integration-event", planner.id)

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                designBlocks = listOf(
                    integrationEvent(variant = "inbound"),
                    integrationEvent(
                        variant = "outbound",
                        packageName = "billing",
                        name = "InvoicePaid",
                        eventName = "invoice.\$paid\\completed",
                        fields = listOf(FieldModel("invoiceId", "java.util.UUID")),
                    ),
                ),
            ),
        )

        assertEquals(2, items.size)

        val inbound = items[0]
        assertEquals("integration-event", inbound.generatorId)
        assertEquals("design/integration_event.kt.peb", inbound.templateId)
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/integration/inbound/order/OrderCreatedIntegrationEvent.kt",
            inbound.outputPath,
        )
        assertEquals("application", inbound.moduleRole)
        assertEquals(ConflictPolicy.SKIP, inbound.conflictPolicy)
        assertEquals("com.acme.demo.application.subscribers.integration.inbound.order", inbound.context["packageName"])
        assertEquals("OrderCreatedIntegrationEvent", inbound.context["typeName"])
        assertEquals("order.created", inbound.context["eventName"])
        assertEquals("\"order.created\"", inbound.context["eventNameKotlinStringLiteral"])
        assertEquals("inbound", inbound.context["role"])
        assertEquals(true, inbound.context["inbound"])
        assertEquals(false, inbound.context["outbound"])
        assertEquals("order * / \"created\" event", inbound.context["descriptionCommentText"])
        assertEquals(
            listOf(DesignRenderFieldModel(name = "orderId", renderedType = "UUID")),
            inbound.context["fields"],
        )
        assertEquals(emptyList<DesignRenderNestedTypeModel>(), inbound.context["nestedTypes"])
        assertEquals(listOf("java.util.UUID"), inbound.context["imports"])

        val outbound = items[1]
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/integration/outbound/billing/InvoicePaidIntegrationEvent.kt",
            outbound.outputPath,
        )
        assertEquals("com.acme.demo.application.subscribers.integration.outbound.billing", outbound.context["packageName"])
        assertEquals("invoice.\$paid\\completed", outbound.context["eventName"])
        assertEquals("\"invoice.\\\$paid\\\\completed\"", outbound.context["eventNameKotlinStringLiteral"])
        assertEquals("outbound", outbound.context["role"])
        assertEquals(false, outbound.context["inbound"])
        assertEquals(true, outbound.context["outbound"])
        @Suppress("UNCHECKED_CAST")
        val buildingBlock = outbound.context["buildingBlock"] as Map<String, Any?>
        assertEquals("integration-event", buildingBlock["family"])
        assertEquals("outbound", buildingBlock["variant"])
    }

    private fun integrationEvent(
        variant: String,
        packageName: String = "order",
        name: String = "OrderCreated",
        eventName: String = "order.created",
        fields: List<FieldModel> = listOf(FieldModel("orderId", "UUID")),
    ) = DesignBlockModel(
        tag = "integration_event",
        packageName = packageName,
        name = name,
        description = "order */ \"created\" event",
        eventName = eventName,
        artifacts = listOf(ArtifactSelectionModel("integration-event", variant)),
        fields = fields,
    )

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        typeRegistry = TypeRegistryConfig(entries = mapOf("UUID" to TypeRegistryEntry("java.util.UUID"))),
        generators = mapOf("integration-event" to GeneratorConfig()),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}
