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
        assertEquals(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/events/OrderCreatedDomainEvent.kt",
            event.outputPath,
        )
        assertEquals("domain", event.moduleRole)
        assertEquals(ConflictPolicy.SKIP, event.conflictPolicy)
        assertEquals("com.acme.demo.domain.aggregates.order.events", event.context["packageName"])
        assertEquals("OrderCreatedDomainEvent", event.context["typeName"])
        assertEquals("order */ \"created\" event", event.context["description"])
        assertEquals("order */ \"created\" event", event.context["descriptionText"])
        assertEquals("order * / \"created\" event", event.context["descriptionCommentText"])
        assertEquals("\"order */ \\\"created\\\" event\"", event.context["descriptionKotlinStringLiteral"])
        assertEquals("Order", event.context["aggregateName"])
        assertEquals("com.acme.demo.domain.order.Order", event.context["aggregateType"])
        assertEquals(false, event.context["persist"])
        assertTrue(event.context.containsKey("fields"))
        assertTrue(event.context.containsKey("nestedTypes"))
        assertEquals(
            listOf(
                DesignRenderFieldModel(name = "reason", renderedType = "String"),
                DesignRenderFieldModel(name = "snapshot", renderedType = "Snapshot?", nullable = true),
            ),
            event.context["fields"],
        )
        assertEquals(
            listOf(
                DesignRenderNestedTypeModel(
                    name = "Snapshot",
                    fields = listOf(DesignRenderFieldModel(name = "traceId", renderedType = "UUID")),
                ),
            ),
            event.context["nestedTypes"],
        )
        assertTrue(!event.context.containsKey("responseFields"))
        assertTrue(!event.context.containsKey("responseNestedTypes"))
        val fields = event.context["fields"] as List<*>
        assertTrue(fields.none { (it as? DesignRenderFieldModel)?.name == "entity" })
    }

    private fun domainEvent() = DomainEventModel(
        packageName = "order",
        typeName = "OrderCreatedDomainEvent",
        description = "order */ \"created\" event",
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
