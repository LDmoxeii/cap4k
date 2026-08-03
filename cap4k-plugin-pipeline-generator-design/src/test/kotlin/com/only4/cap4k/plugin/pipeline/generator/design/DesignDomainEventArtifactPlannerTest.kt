package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeKind
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SemanticArrayTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinType
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticDefaultExpression
import com.only4.cap4k.plugin.pipeline.api.SemanticListTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticMapTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticSetTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition
import com.only4.cap4k.plugin.pipeline.api.SemanticValueField
import com.only4.cap4k.plugin.pipeline.api.SemanticValueRole
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryEntry
import com.only4.cap4k.plugin.pipeline.api.ValueObjectModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
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
                designBlocks = listOf(domainEventBlock(eventName = "order.created")),
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
        assertEquals("order.created", event.context["eventName"])
        assertEquals("\"order.created\"", event.context["eventNameKotlinStringLiteral"])
        assertFalse(event.context.containsKey("aggregateName"))
        assertFalse(event.context.containsKey("aggregateType"))
        assertEquals(false, event.context["persist"])
        @Suppress("UNCHECKED_CAST")
        val buildingBlock = event.context["buildingBlock"] as? Map<String, Any?>
        assertEquals("domain_event", buildingBlock?.get("tag"))
        assertEquals("OrderCreated", buildingBlock?.get("name"))
        assertEquals("order", buildingBlock?.get("packageName"))
        assertEquals("order */ \"created\" \\event ${'$'}status", buildingBlock?.get("description"))
        assertEquals("\"order */ \\\"created\\\" \\\\event \\${'$'}status\"", buildingBlock?.get("descriptionKotlinStringLiteral"))
        assertEquals(listOf("Order"), buildingBlock?.get("aggregates"))
        assertEquals("order.created", buildingBlock?.get("eventName"))
        assertEquals("domain-event", buildingBlock?.get("family"))
        assertEquals("", buildingBlock?.get("variant"))
        assertTrue(event.context.containsKey("fields"))
        assertTrue(event.context.containsKey("nestedTypes"))
        assertEquals(
            listOf(
                DesignRenderFieldModel(name = "reason", renderedType = "String", defaultValue = "\"manual\""),
                DesignRenderFieldModel(name = "snapshot", renderedType = "Snapshot?", nullable = true, defaultValue = "null"),
            ),
            event.context["fields"],
        )
        assertEquals(
            listOf(
                DesignRenderNestedTypeModel(
                    name = "Snapshot",
                    fields = listOf(
                        DesignRenderFieldModel(
                            name = "traceId",
                            renderedType = "UUID",
                            defaultValue = "UUID(0L, 0L)",
                        ),
                    ),
                ),
            ),
            event.context["nestedTypes"],
        )
        assertTrue(!event.context.containsKey("resultFields"))
        assertTrue(!event.context.containsKey("resultNestedTypes"))
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
        assertFalse(event.context.containsKey("aggregateName"))
        assertFalse(event.context.containsKey("aggregateType"))
    }

    @Test
    fun `rejects direct entity payload by resolved type for transient and reliable events`() {
        listOf(false, true).forEach { persist ->
            val block = domainEventBlock().copy(
                persist = persist,
                request = eventDefinition(
                    fields = listOf(
                        SemanticValueField(
                            name = "entity",
                            type = SemanticNamedTypeRef(entityIdentity()),
                            sourcePath = "fields.entity",
                        ),
                    ),
                ),
            )

            val error = assertThrows(IllegalArgumentException::class.java) {
                DesignDomainEventArtifactPlanner().plan(
                    projectConfig(modules = mapOf("domain" to "demo-domain")),
                    CanonicalModel(designBlocks = listOf(block), entities = listOf(entityModel())),
                )
            }

            assertEquals(
                "domain_event OrderCreated field fields.entity references persistent Entity/Aggregate type " +
                    "com.acme.demo.domain.order.Order.",
                error.message,
            )
        }
    }

    @Test
    fun `rejects entity payload even when only the domain subscriber artifact is selected`() {
        val block = domainEventBlock().copy(
            artifacts = listOf(ArtifactSelectionModel("domain-subscriber")),
            request = eventDefinition(
                fields = listOf(
                    SemanticValueField(
                        name = "order",
                        type = SemanticNamedTypeRef(entityIdentity()),
                        sourcePath = "fields.order",
                    ),
                ),
            ),
        )
        val model = CanonicalModel(designBlocks = listOf(block), entities = listOf(entityModel()))
        val expectedMessage =
            "domain_event OrderCreated field fields.order references persistent Entity/Aggregate type " +
                "com.acme.demo.domain.order.Order."

        listOf(
            DesignDomainEventArtifactPlanner() to projectConfig(modules = emptyMap()),
            DesignDomainEventHandlerArtifactPlanner() to projectConfig(modules = mapOf("application" to "demo-application")),
        ).forEach { (planner, config) ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                planner.plan(config, model)
            }
            assertEquals(expectedMessage, error.message)
        }
    }

    @Test
    fun `rejects entity payload through nested value definition`() {
        val snapshotIdentity = CanonicalTypeIdentity(
            packageName = "order",
            typePath = listOf("OrderCreated", "Snapshot"),
            kind = CanonicalTypeKind.NESTED_VALUE,
        )
        val block = domainEventBlock().copy(
            request = eventDefinition(
                fields = listOf(
                    SemanticValueField(
                        name = "snapshot",
                        type = SemanticNamedTypeRef(snapshotIdentity),
                        sourcePath = "fields.snapshot",
                    ),
                ),
                nestedDefinitions = listOf(
                    SemanticValueDefinition(
                        identity = snapshotIdentity,
                        role = SemanticValueRole.DOMAIN_EVENT,
                        fields = listOf(
                            SemanticValueField("order", SemanticNamedTypeRef(entityIdentity())),
                        ),
                    ),
                ),
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignDomainEventArtifactPlanner().plan(
                projectConfig(modules = mapOf("domain" to "demo-domain")),
                CanonicalModel(designBlocks = listOf(block), entities = listOf(entityModel())),
            )
        }

        assertEquals(
            "domain_event OrderCreated field fields.snapshot.order references persistent Entity/Aggregate type " +
                "com.acme.demo.domain.order.Order.",
            error.message,
        )
    }

    @Test
    fun `rejects entity payload through a known value object definition`() {
        val snapshot = SemanticValueDefinition(
            identity = CanonicalTypeIdentity(
                packageName = "com.acme.demo.domain.order.values",
                typePath = listOf("OrderSnapshot"),
                kind = CanonicalTypeKind.VALUE_OBJECT,
                ownerAggregateName = "Order",
            ),
            role = SemanticValueRole.VALUE_OBJECT,
            fields = listOf(
                SemanticValueField("order", SemanticNamedTypeRef(entityIdentity())),
            ),
        )
        val block = domainEventBlock().copy(
            request = eventDefinition(
                fields = listOf(
                    SemanticValueField(
                        name = "snapshot",
                        type = SemanticNamedTypeRef(snapshot.identity),
                        sourcePath = "fields.snapshot",
                    ),
                ),
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignDomainEventArtifactPlanner().plan(
                projectConfig(modules = mapOf("domain" to "demo-domain")),
                CanonicalModel(
                    designBlocks = listOf(block),
                    entities = listOf(entityModel()),
                    valueObjects = listOf(ValueObjectModel(snapshot, aggregates = listOf("Order"))),
                ),
            )
        }

        assertEquals(
            "domain_event OrderCreated field fields.snapshot.order references persistent Entity/Aggregate type " +
                "com.acme.demo.domain.order.Order.",
            error.message,
        )
    }

    @Test
    fun `rejects entity payload through every supported container edge`() {
        val entityType = SemanticNamedTypeRef(entityIdentity())
        val cases = listOf(
            SemanticListTypeRef(entityType) to "fields.payload[]",
            SemanticSetTypeRef(entityType) to "fields.payload[]",
            SemanticArrayTypeRef(entityType) to "fields.payload[]",
            SemanticMapTypeRef(entityType, SemanticBuiltinTypeRef(SemanticBuiltinType.STRING)) to
                "fields.payload{key}",
            SemanticMapTypeRef(SemanticBuiltinTypeRef(SemanticBuiltinType.STRING), entityType) to
                "fields.payload{value}",
        )

        cases.forEach { (payloadType, expectedPath) ->
            val block = domainEventBlock().copy(
                request = eventDefinition(
                    fields = listOf(SemanticValueField("payload", payloadType, sourcePath = "fields.payload")),
                ),
            )
            val error = assertThrows(IllegalArgumentException::class.java) {
                DesignDomainEventArtifactPlanner().plan(
                    projectConfig(modules = mapOf("domain" to "demo-domain")),
                    CanonicalModel(designBlocks = listOf(block), entities = listOf(entityModel())),
                )
            }

            assertEquals(
                "domain_event OrderCreated field $expectedPath references persistent Entity/Aggregate type " +
                    "com.acme.demo.domain.order.Order.",
                error.message,
            )
        }
    }

    @Test
    fun `allows scalar field named entity strong id arrays and value objects`() {
        val orderId = CanonicalTypeIdentity(
            packageName = "com.acme.demo.domain.order.ids",
            typePath = listOf("OrderId"),
            kind = CanonicalTypeKind.STRONG_ID,
            ownerAggregateName = "Order",
        )
        val snapshot = SemanticValueDefinition(
            identity = CanonicalTypeIdentity(
                packageName = "com.acme.demo.domain.order.values",
                typePath = listOf("OrderSnapshot"),
                kind = CanonicalTypeKind.VALUE_OBJECT,
                ownerAggregateName = "Order",
            ),
            role = SemanticValueRole.VALUE_OBJECT,
            fields = listOf(SemanticValueField("status", SemanticBuiltinTypeRef(SemanticBuiltinType.STRING))),
        )
        val block = domainEventBlock().copy(
            request = eventDefinition(
                fields = listOf(
                    SemanticValueField(
                        name = "entity",
                        type = SemanticBuiltinTypeRef(SemanticBuiltinType.STRING),
                        sourcePath = "fields.entity",
                    ),
                    SemanticValueField(
                        name = "orderIds",
                        type = SemanticArrayTypeRef(SemanticNamedTypeRef(orderId)),
                        sourcePath = "fields.orderIds",
                    ),
                    SemanticValueField(
                        name = "snapshot",
                        type = SemanticNamedTypeRef(snapshot.identity),
                        sourcePath = "fields.snapshot",
                    ),
                ),
            ),
        )

        val item = DesignDomainEventArtifactPlanner().plan(
            projectConfig(modules = mapOf("domain" to "demo-domain")),
            CanonicalModel(
                designBlocks = listOf(block),
                entities = listOf(entityModel()),
                valueObjects = listOf(ValueObjectModel(snapshot, aggregates = listOf("Order"))),
            ),
        ).single()

        assertEquals(
            listOf(
                DesignRenderFieldModel("entity", "String"),
                DesignRenderFieldModel("orderIds", "Array<OrderId>"),
                DesignRenderFieldModel("snapshot", "OrderSnapshot"),
            ),
            item.context["fields"],
        )
        assertEquals(
            listOf(
                "com.acme.demo.domain.order.ids.OrderId",
                "com.acme.demo.domain.order.values.OrderSnapshot",
            ),
            item.context["imports"],
        )
    }

    private fun domainEventBlock(
        packageName: String = "order",
        name: String = "OrderCreated",
        aggregates: List<String> = listOf("Order"),
        eventName: String = "",
    ) = designBlock(
        tag = "domain_event",
        family = "domain-event",
        packageName = packageName,
        name = name,
        description = "order */ \"created\" \\event ${'$'}status",
        aggregates = aggregates,
        eventName = eventName,
        persist = false,
        requestDefinition = com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition(
            identity = com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity(
                packageName,
                listOf(name),
                com.only4.cap4k.plugin.pipeline.api.CanonicalTypeKind.NESTED_VALUE,
            ),
            role = com.only4.cap4k.plugin.pipeline.api.SemanticValueRole.DOMAIN_EVENT,
            fields = listOf(
                com.only4.cap4k.plugin.pipeline.api.SemanticValueField(
                    "reason",
                    com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef(
                        com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinType.STRING,
                    ),
                    defaultValue = SemanticDefaultExpression("\"manual\"", "manual"),
                ),
                com.only4.cap4k.plugin.pipeline.api.SemanticValueField(
                    "snapshot",
                    com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef(
                        com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity(
                            packageName,
                            listOf(name, "Snapshot"),
                            com.only4.cap4k.plugin.pipeline.api.CanonicalTypeKind.NESTED_VALUE,
                        ),
                        nullable = true,
                    ),
                    defaultValue = SemanticDefaultExpression("null", "null"),
                ),
            ),
            nestedDefinitions = listOf(
                com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition(
                    identity = com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity(
                        packageName,
                        listOf(name, "Snapshot"),
                        com.only4.cap4k.plugin.pipeline.api.CanonicalTypeKind.NESTED_VALUE,
                    ),
                    role = com.only4.cap4k.plugin.pipeline.api.SemanticValueRole.DOMAIN_EVENT,
                    fields = listOf(
                        com.only4.cap4k.plugin.pipeline.api.SemanticValueField(
                            "traceId",
                            com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef(
                                com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity(
                                    "java.util",
                                    listOf("UUID"),
                                    com.only4.cap4k.plugin.pipeline.api.CanonicalTypeKind.EXTERNAL,
                                ),
                            ),
                            defaultValue = SemanticDefaultExpression("UUID(0L, 0L)", "UUID(0L, 0L)"),
                        ),
                    ),
                ),
            ),
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

    private fun entityIdentity() = CanonicalTypeIdentity(
        packageName = "com.acme.demo.domain.order",
        typePath = listOf("Order"),
        kind = CanonicalTypeKind.ENTITY,
        ownerAggregateName = "Order",
    )

    private fun eventDefinition(
        fields: List<SemanticValueField>,
        nestedDefinitions: List<SemanticValueDefinition> = emptyList(),
    ) = SemanticValueDefinition(
        identity = CanonicalTypeIdentity(
            packageName = "order",
            typePath = listOf("OrderCreated"),
            kind = CanonicalTypeKind.NESTED_VALUE,
        ),
        role = SemanticValueRole.DOMAIN_EVENT,
        fields = fields,
        nestedDefinitions = nestedDefinitions,
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
