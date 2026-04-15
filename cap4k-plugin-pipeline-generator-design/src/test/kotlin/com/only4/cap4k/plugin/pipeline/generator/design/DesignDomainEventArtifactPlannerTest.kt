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

class DesignDomainEventArtifactPlannerTest {

    @Test
    fun `plans domain event artifacts into domain events path with one-level nested type contract`() {
        val planner = DesignDomainEventArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("domain" to "demo-domain")),
            model = CanonicalModel(domainEvents = listOf(domainEvent())),
        )

        val event = items.single()
        assertEquals("design-domain-event", event.generatorId)
        assertEquals("design/domain_event.kt.peb", event.templateId)
        assertTrue(event.outputPath.endsWith("domain/order/events/OrderCreatedDomainEvent.kt"))
        assertEquals("com.acme.demo.domain.order.events", event.context["packageName"])
        assertEquals(false, event.context["persist"])
        assertEquals(
            listOf(
                DesignRenderFieldModel(name = "reason", renderedType = "String"),
                DesignRenderFieldModel(name = "snapshot", renderedType = "Snapshot?", nullable = true),
            ),
            event.context["requestFields"],
        )
        assertEquals(
            listOf(
                DesignRenderNestedTypeModel(
                    name = "Snapshot",
                    fields = listOf(DesignRenderFieldModel(name = "traceId", renderedType = "UUID")),
                ),
            ),
            event.context["requestNestedTypes"],
        )
        val requestFields = event.context["requestFields"] as List<*>
        assertTrue(requestFields.none { (it as? DesignRenderFieldModel)?.name == "entity" })
    }

    private fun domainEvent() = DomainEventModel(
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
    )

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        typeRegistry = mapOf("UUID" to "java.util.UUID"),
        generators = mapOf("design-domain-event" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}
