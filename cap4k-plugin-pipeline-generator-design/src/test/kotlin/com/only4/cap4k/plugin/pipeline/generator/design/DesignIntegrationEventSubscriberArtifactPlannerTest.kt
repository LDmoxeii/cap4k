package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DesignIntegrationEventSubscriberArtifactPlannerTest {

    @Test
    fun `plans subscribers only for selected inbound integration event blocks`() {
        val planner = DesignIntegrationEventSubscriberArtifactPlanner()
        assertEquals("integration-subscriber", planner.id)

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                designBlocks = listOf(
                    integrationEvent(subscriber = true),
                    integrationEvent(
                        subscriber = false,
                        packageName = "billing",
                        name = "InvoicePaid",
                        eventName = "invoice.paid",
                    ),
                ),
            ),
        )

        val subscriber = items.single()
        assertEquals("integration-subscriber", subscriber.generatorId)
        assertEquals("design/integration_event_subscriber.kt.peb", subscriber.templateId)
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/integration/OrderCreatedIntegrationEventSubscriber.kt",
            subscriber.outputPath,
        )
        assertEquals("application", subscriber.moduleRole)
        assertEquals(ConflictPolicy.SKIP, subscriber.conflictPolicy)
        assertEquals("com.acme.demo.application.subscribers.integration", subscriber.context["packageName"])
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
        subscriber: Boolean,
        packageName: String = "order",
        name: String = "OrderCreated",
        eventName: String = "order.created",
    ) = DesignBlockModel(
        tag = "integration_event",
        packageName = packageName,
        name = name,
        description = "order */ created event",
        eventName = eventName,
        artifacts = listOf(ArtifactSelectionModel("integration-event", "inbound")) +
            if (subscriber) listOf(ArtifactSelectionModel("integration-subscriber")) else emptyList(),
    )

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("integration-subscriber" to GeneratorConfig()),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}
