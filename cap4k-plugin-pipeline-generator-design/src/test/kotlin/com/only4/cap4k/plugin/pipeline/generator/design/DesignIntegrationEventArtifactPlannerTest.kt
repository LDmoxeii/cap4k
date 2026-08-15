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
    fun `plans inbound and outbound integration event contracts into contract paths`() {
        val planner = DesignIntegrationEventArtifactPlanner()
        assertEquals("integration-event", planner.id)

        val items = planner.plan(
            config = projectConfig(modules = mapOf("contract" to "demo-contract")),
            model = CanonicalModel(
                designBlocks = listOf(
                    integrationEvent(
                        variant = "inbound",
                        fields = listOf(FieldModel("orderId", "UUID", defaultValue = "UUID(0L, 0L)")),
                        includeNestedPayload = true,
                    ),
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
            "demo-contract/src/main/kotlin/com/acme/demo/contract/events/integration/inbound/order/OrderCreatedIntegrationEvent.kt",
            inbound.outputPath,
        )
        assertEquals("contract", inbound.moduleRole)
        assertEquals(ConflictPolicy.SKIP, inbound.conflictPolicy)
        assertEquals("com.acme.demo.contract.events.integration.inbound.order", inbound.context["packageName"])
        assertEquals("OrderCreatedIntegrationEvent", inbound.context["typeName"])
        assertEquals("order.created", inbound.context["eventName"])
        assertEquals("\"order.created\"", inbound.context["eventNameKotlinStringLiteral"])
        assertEquals("inbound", inbound.context["variant"])
        assertEquals(true, inbound.context["inbound"])
        assertEquals(false, inbound.context["outbound"])
        assertEquals("order * / \"created\" event", inbound.context["descriptionCommentText"])
        assertEquals(
            listOf(
                DesignRenderFieldModel(name = "orderId", renderedType = "UUID", defaultValue = "UUID(0L, 0L)"),
                DesignRenderFieldModel(name = "details", renderedType = "Details"),
            ),
            inbound.context["fields"],
        )
        assertEquals(
            listOf(
                DesignRenderNestedTypeModel(
                    name = "Details",
                    fields = listOf(
                        DesignRenderFieldModel(name = "source", renderedType = "String", defaultValue = "\"api\""),
                    ),
                ),
            ),
            inbound.context["nestedTypes"],
        )
        assertEquals(listOf("java.util.UUID"), inbound.context["imports"])

        val outbound = items[1]
        assertEquals(
            "demo-contract/src/main/kotlin/com/acme/demo/contract/events/integration/outbound/billing/InvoicePaidIntegrationEvent.kt",
            outbound.outputPath,
        )
        assertEquals("com.acme.demo.contract.events.integration.outbound.billing", outbound.context["packageName"])
        assertEquals("invoice.\$paid\\completed", outbound.context["eventName"])
        assertEquals("\"invoice.\\\$paid\\\\completed\"", outbound.context["eventNameKotlinStringLiteral"])
        assertEquals("outbound", outbound.context["variant"])
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
        includeNestedPayload: Boolean = false,
    ) = designBlock(
        tag = "integration_event",
        family = "integration-event",
        variant = variant,
        packageName = packageName,
        name = name,
        description = "order */ \"created\" event",
        eventName = eventName,
        requestDefinition = semanticDefinition(
            packageName = packageName,
            typeName = name,
            role = com.only4.cap4k.plugin.pipeline.api.SemanticValueRole.INTEGRATION_EVENT,
            fields = fields.map { field ->
                if (field.type == "UUID") field.copy(type = "java.util.UUID") else field
            },
        ).let { definition ->
            if (!includeNestedPayload) {
                definition
            } else {
                val nestedIdentity = com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity(
                    packageName = packageName,
                    typePath = listOf(name, "Details"),
                    kind = com.only4.cap4k.plugin.pipeline.api.CanonicalTypeKind.NESTED_VALUE,
                )
                definition.copy(
                    fields = definition.fields + com.only4.cap4k.plugin.pipeline.api.SemanticValueField(
                        name = "details",
                        type = com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef(nestedIdentity),
                    ),
                    nestedDefinitions = listOf(
                        com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition(
                            identity = nestedIdentity,
                            role = com.only4.cap4k.plugin.pipeline.api.SemanticValueRole.INTEGRATION_EVENT,
                            fields = listOf(
                                com.only4.cap4k.plugin.pipeline.api.SemanticValueField(
                                    name = "source",
                                    type = com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef(
                                        com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinType.STRING,
                                    ),
                                    defaultValue = com.only4.cap4k.plugin.pipeline.api.SemanticDefaultExpression(
                                        kotlinExpression = "\"api\"",
                                        sourceExpression = "api",
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            }
        },
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
