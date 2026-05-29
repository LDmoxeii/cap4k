package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
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

class DesignDomainEventArtifactPlannerTest {

    @Test
    fun `plans domain event artifacts into domain events path with one-level nested type contract`() {
        val planner = DesignDomainEventArtifactPlanner()
        assertEquals("domain-event", planner.id)

        val items = planner.plan(
            config = projectConfig(modules = mapOf("domain" to "demo-domain")),
            model = CanonicalModel(
                designBlocks = listOf(domainEventBlock()),
                entities = listOf(entityModel()),
            ),
        )

        val event = items.single()
        assertEquals("domain-event", event.generatorId)
        assertEquals("design/domain_event.kt.peb", event.templateId)
        assertEquals(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/events/OrderCreatedDomainEvent.kt",
            event.outputPath,
        )
        assertEquals("domain", event.moduleRole)
        assertEquals(ConflictPolicy.SKIP, event.conflictPolicy)
        assertEquals("com.acme.demo.domain.aggregates.order.events", event.context["packageName"])
        assertEquals("OrderCreatedDomainEvent", event.context["typeName"])
        assertEquals("order */ \"created\" \\event ${'$'}status", event.context["description"])
        assertEquals("order */ \"created\" \\event ${'$'}status", event.context["descriptionText"])
        assertEquals("order * / \"created\" \\event ${'$'}status", event.context["descriptionCommentText"])
        assertEquals("\"order */ \\\"created\\\" \\\\event \\${'$'}status\"", event.context["descriptionKotlinStringLiteral"])
        assertEquals("Order", event.context["aggregateName"])
        assertEquals("com.acme.demo.domain.order.Order", event.context["aggregateType"])
        assertEquals(false, event.context["persist"])
        @Suppress("UNCHECKED_CAST")
        val buildingBlock = event.context["buildingBlock"] as? Map<String, Any?>
        assertEquals("domain_event", buildingBlock?.get("tag"))
        assertEquals("OrderCreated", buildingBlock?.get("name"))
        assertEquals("order", buildingBlock?.get("packageName"))
        assertEquals("order */ \"created\" \\event ${'$'}status", buildingBlock?.get("description"))
        assertEquals("\"order */ \\\"created\\\" \\\\event \\${'$'}status\"", buildingBlock?.get("descriptionKotlinStringLiteral"))
        assertEquals(listOf("Order"), buildingBlock?.get("aggregates"))
        assertEquals("", buildingBlock?.get("eventName"))
        assertEquals("domain-event", buildingBlock?.get("family"))
        assertEquals("", buildingBlock?.get("variant"))
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

    @Test
    fun `routes domain event artifacts by aggregate package group`() {
        val planner = DesignDomainEventArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("domain" to "demo-domain")),
            model = CanonicalModel(
                designBlocks = listOf(
                    domainEventBlock(
                        packageName = "user_message",
                        name = "UserMessageCreated",
                        aggregates = listOf("UserMessage"),
                    ),
                ),
                entities = listOf(
                    entityModel(
                        name = "UserMessage",
                        packageName = "com.acme.demo.domain.aggregates.user_message",
                    ),
                ),
            ),
        )

        val event = items.single()
        assertEquals(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/user_message/events/UserMessageCreatedDomainEvent.kt",
            event.outputPath,
        )
        assertEquals("com.acme.demo.domain.aggregates.user_message.events", event.context["packageName"])
        assertEquals("com.acme.demo.domain.aggregates.user_message.UserMessage", event.context["aggregateType"])
    }

    private fun domainEventBlock(
        packageName: String = "order",
        name: String = "OrderCreated",
        aggregates: List<String> = listOf("Order"),
    ) = DesignBlockModel(
        tag = "domain_event",
        packageName = packageName,
        name = name,
        description = "order */ \"created\" \\event ${'$'}status",
        aggregates = aggregates,
        persist = false,
        artifacts = listOf(ArtifactSelectionModel("domain-event")),
        fields = listOf(
            FieldModel("reason", "String"),
            FieldModel("snapshot", "Snapshot", nullable = true),
            FieldModel("snapshot.traceId", "UUID"),
        ),
    )

    private fun entityModel(
        name: String = "Order",
        packageName: String = "com.acme.demo.domain.order",
    ) = EntityModel(
        name = name,
        packageName = packageName,
        tableName = name.lowercase(),
        comment = "",
        fields = listOf(FieldModel("id", "Long")),
        idField = FieldModel("id", "Long"),
        aggregateRoot = true,
    )

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        typeRegistry = TypeRegistryConfig(entries = mapOf("UUID" to TypeRegistryEntry("java.util.UUID"))),
        generators = mapOf("domain-event" to GeneratorConfig()),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}
