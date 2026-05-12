package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.IntegrationEventModel
import com.only4.cap4k.plugin.pipeline.api.IntegrationEventRole
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignIntegrationEventArtifactPlannerTest {

    @Test
    fun `plans inbound and outbound integration event contracts into application integration event paths`() {
        val planner = DesignIntegrationEventArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                integrationEvents = listOf(
                    integrationEvent(role = IntegrationEventRole.INBOUND),
                    integrationEvent(
                        role = IntegrationEventRole.OUTBOUND,
                        packageName = "billing",
                        typeName = "InvoicePaidIntegrationEvent",
                        eventName = "invoice.\$paid\\completed",
                        fields = listOf(FieldModel("invoiceId", "java.util.UUID")),
                    ),
                ),
            ),
        )

        assertEquals(2, items.size)

        val inbound = items[0]
        assertEquals("design-integration-event", inbound.generatorId)
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
    }

    private fun integrationEvent(
        role: IntegrationEventRole,
        packageName: String = "order",
        typeName: String = "OrderCreatedIntegrationEvent",
        eventName: String = "order.created",
        fields: List<FieldModel> = listOf(FieldModel("orderId", "UUID")),
    ) = IntegrationEventModel(
        packageName = packageName,
        typeName = typeName,
        description = "order */ \"created\" event",
        role = role,
        eventName = eventName,
        fields = fields,
    )

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        typeRegistry = mapOf("UUID" to TypeRegistryEntry("java.util.UUID")),
        generators = mapOf("design-integration-event" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}
