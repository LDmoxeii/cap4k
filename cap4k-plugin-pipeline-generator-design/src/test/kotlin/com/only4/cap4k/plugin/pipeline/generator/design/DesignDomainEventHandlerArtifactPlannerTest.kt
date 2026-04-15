package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DomainEventModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignDomainEventHandlerArtifactPlannerTest {

    @Test
    fun `plans domain event subscriber artifacts into application events path`() {
        val planner = DesignDomainEventHandlerArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                domainEvents = listOf(
                    DomainEventModel(
                        packageName = "order",
                        typeName = "OrderCreatedDomainEvent",
                        description = "order created event",
                        aggregateName = "Order",
                        aggregatePackageName = "com.acme.demo.domain.order",
                        persist = false,
                        fields = listOf(
                            FieldModel("reason", "String"),
                            FieldModel("snapshot", "Snapshot", nullable = true),
                            FieldModel("snapshot.traceId", "UUID"),
                        ),
                    ),
                ),
            ),
        )

        val handler = items.single()
        assertEquals("design-domain-event-handler", handler.generatorId)
        assertEquals("design/domain_event_handler.kt.peb", handler.templateId)
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/order/events/OrderCreatedDomainEventSubscriber.kt",
            handler.outputPath,
        )
        assertEquals("application", handler.moduleRole)
        assertEquals(ConflictPolicy.SKIP, handler.conflictPolicy)
        assertEquals("com.acme.demo.application.order.events", handler.context["packageName"])
        assertEquals("OrderCreatedDomainEventSubscriber", handler.context["typeName"])
        assertEquals("OrderCreatedDomainEvent", handler.context["domainEventTypeName"])
        assertEquals("com.acme.demo.domain.order.events.OrderCreatedDomainEvent", handler.context["domainEventType"])
        assertEquals("Order", handler.context["aggregateName"])
        assertEquals("order created event", handler.context["description"])
        assertEquals(
            listOf("com.acme.demo.domain.order.events.OrderCreatedDomainEvent"),
            handler.context["imports"],
        )
    }

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("design-domain-event-handler" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}
