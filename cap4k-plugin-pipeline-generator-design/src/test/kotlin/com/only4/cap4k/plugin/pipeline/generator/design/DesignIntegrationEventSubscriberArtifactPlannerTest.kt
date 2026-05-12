package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.IntegrationEventModel
import com.only4.cap4k.plugin.pipeline.api.IntegrationEventRole
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DesignIntegrationEventSubscriberArtifactPlannerTest {

    @Test
    fun `plans subscribers only for inbound integration events`() {
        val planner = DesignIntegrationEventSubscriberArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                integrationEvents = listOf(
                    integrationEvent(role = IntegrationEventRole.INBOUND),
                    integrationEvent(
                        role = IntegrationEventRole.OUTBOUND,
                        packageName = "billing",
                        typeName = "InvoicePaidIntegrationEvent",
                        eventName = "invoice.paid",
                    ),
                ),
            ),
        )

        val subscriber = items.single()
        assertEquals("design-integration-event-subscriber", subscriber.generatorId)
        assertEquals("design/integration_event_subscriber.kt.peb", subscriber.templateId)
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/integration/inbound/order/OrderCreatedIntegrationEventSubscriber.kt",
            subscriber.outputPath,
        )
        assertEquals("application", subscriber.moduleRole)
        assertEquals(ConflictPolicy.SKIP, subscriber.conflictPolicy)
        assertEquals("com.acme.demo.application.subscribers.integration.inbound.order", subscriber.context["packageName"])
        assertEquals("OrderCreatedIntegrationEventSubscriber", subscriber.context["typeName"])
        assertEquals("OrderCreatedIntegrationEvent", subscriber.context["eventTypeName"])
        assertEquals(
            "com.acme.demo.application.subscribers.integration.inbound.order.OrderCreatedIntegrationEvent",
            subscriber.context["eventType"],
        )
        assertEquals("order.created", subscriber.context["eventName"])
        assertEquals("inbound", subscriber.context["role"])
        assertEquals(true, subscriber.context["inbound"])
        assertEquals(false, subscriber.context["outbound"])
        assertEquals("order * / created event", subscriber.context["descriptionCommentText"])
        assertEquals(
            listOf("com.acme.demo.application.subscribers.integration.inbound.order.OrderCreatedIntegrationEvent"),
            subscriber.context["imports"],
        )
    }

    private fun integrationEvent(
        role: IntegrationEventRole,
        packageName: String = "order",
        typeName: String = "OrderCreatedIntegrationEvent",
        eventName: String = "order.created",
    ) = IntegrationEventModel(
        packageName = packageName,
        typeName = typeName,
        description = "order */ created event",
        role = role,
        eventName = eventName,
    )

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("design-integration-event-subscriber" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}
