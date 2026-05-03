package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DomainEventModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.PackageLayout
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignDomainEventHandlerArtifactPlannerTest {

    @Test
    fun `plans domain event subscriber artifacts into application subscribers domain path`() {
        val planner = DesignDomainEventHandlerArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                domainEvents = listOf(
                    DomainEventModel(
                        packageName = "order",
                        typeName = "OrderCreatedDomainEvent",
                        description = "order */ created event",
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
            "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/domain/order/OrderCreatedDomainEventSubscriber.kt",
            handler.outputPath,
        )
        assertEquals("application", handler.moduleRole)
        assertEquals(ConflictPolicy.SKIP, handler.conflictPolicy)
        assertEquals("com.acme.demo.application.subscribers.domain.order", handler.context["packageName"])
        assertEquals("OrderCreatedDomainEventSubscriber", handler.context["typeName"])
        assertEquals("OrderCreatedDomainEvent", handler.context["domainEventTypeName"])
        assertEquals("com.acme.demo.domain.aggregates.order.events.OrderCreatedDomainEvent", handler.context["domainEventType"])
        assertEquals("Order", handler.context["aggregateName"])
        assertEquals("order */ created event", handler.context["description"])
        assertEquals("order * / created event", handler.context["descriptionCommentText"])
        assertEquals(
            listOf("com.acme.demo.domain.aggregates.order.events.OrderCreatedDomainEvent"),
            handler.context["imports"],
        )
    }

    @Test
    fun `routes domain event subscriber artifacts by aggregate package group`() {
        val planner = DesignDomainEventHandlerArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                domainEvents = listOf(
                    DomainEventModel(
                        packageName = "user_message",
                        typeName = "UserMessageCreatedDomainEvent",
                        description = "user message created",
                        aggregateName = "UserMessage",
                        aggregatePackageName = "com.acme.demo.domain.aggregates.user_message",
                        persist = false,
                    ),
                ),
            ),
        )

        val handler = items.single()
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/domain/user_message/UserMessageCreatedDomainEventSubscriber.kt",
            handler.outputPath,
        )
        assertEquals("com.acme.demo.application.subscribers.domain.user_message", handler.context["packageName"])
        assertEquals(
            "com.acme.demo.domain.aggregates.user_message.events.UserMessageCreatedDomainEvent",
            handler.context["domainEventType"],
        )
        assertEquals(
            listOf("com.acme.demo.domain.aggregates.user_message.events.UserMessageCreatedDomainEvent"),
            handler.context["imports"],
        )
    }

    @Test
    fun `custom domain event handler layout imports event from custom event layout`() {
        val planner = DesignDomainEventHandlerArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(
                modules = mapOf("application" to "demo-application"),
                artifactLayout = ArtifactLayoutConfig(
                    designDomainEvent = PackageLayout(
                        packageRoot = "domain.model",
                        packageSuffix = "events",
                    ),
                    designDomainEventHandler = PackageLayout(
                        packageRoot = "application.eventing",
                        packageSuffix = "subscribers",
                    ),
                ),
            ),
            model = CanonicalModel(
                domainEvents = listOf(
                    DomainEventModel(
                        packageName = "order",
                        typeName = "OrderCreatedDomainEvent",
                        description = "order created event",
                        aggregateName = "Order",
                        aggregatePackageName = "com.acme.demo.domain.order",
                        persist = false,
                    ),
                ),
            ),
        )

        val handler = items.single()
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/eventing/order/subscribers/OrderCreatedDomainEventSubscriber.kt",
            handler.outputPath,
        )
        assertEquals("com.acme.demo.application.eventing.order.subscribers", handler.context["packageName"])
        assertEquals(
            "com.acme.demo.domain.model.order.events.OrderCreatedDomainEvent",
            handler.context["domainEventType"],
        )
        assertEquals(
            listOf("com.acme.demo.domain.model.order.events.OrderCreatedDomainEvent"),
            handler.context["imports"],
        )
    }

    private fun projectConfig(
        modules: Map<String, String>,
        artifactLayout: ArtifactLayoutConfig = ArtifactLayoutConfig(),
    ) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("design-domain-event-handler" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        artifactLayout = artifactLayout,
    )
}
