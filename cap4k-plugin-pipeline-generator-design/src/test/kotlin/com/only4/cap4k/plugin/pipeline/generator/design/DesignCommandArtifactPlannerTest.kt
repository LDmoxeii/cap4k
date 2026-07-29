package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeKind
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinType
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticDefaultExpression
import com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition
import com.only4.cap4k.plugin.pipeline.api.SemanticValueField
import com.only4.cap4k.plugin.pipeline.api.SemanticValueRole
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DesignCommandArtifactPlannerTest {
    @Test
    fun `plans command from resolved semantic definitions`() {
        val request = definition(
            path = "SubmitOrderCmd.Request",
            role = SemanticValueRole.COMMAND_REQUEST,
            fields = listOf(
                SemanticValueField(
                    name = "orderId",
                    type = SemanticNamedTypeRef(
                        CanonicalTypeIdentity("com.acme.order", listOf("OrderId"), CanonicalTypeKind.STRONG_ID),
                    ),
                ),
                SemanticValueField(
                    name = "note",
                    type = SemanticBuiltinTypeRef(SemanticBuiltinType.STRING, nullable = true),
                    defaultValue = SemanticDefaultExpression("null", "null"),
                ),
            ),
        )
        val response = definition(
            path = "SubmitOrderCmd.Response",
            role = SemanticValueRole.COMMAND_RESPONSE,
            fields = listOf(SemanticValueField("accepted", SemanticBuiltinTypeRef(SemanticBuiltinType.BOOLEAN))),
        )
        val block = designBlock(
            tag = "command",
            family = "command",
            packageName = "order.submit",
            name = "SubmitOrder",
            description = "submit order",
            aggregates = listOf("Order"),
            requestDefinition = request,
            responseDefinition = response,
        )

        val item = DesignCommandArtifactPlanner().plan(config(), CanonicalModel(designBlocks = listOf(block))).single()

        assertEquals("design/command.kt.peb", item.templateId)
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt",
            item.outputPath,
        )
        assertEquals(
            listOf(
                DesignRenderFieldModel("orderId", "OrderId"),
                DesignRenderFieldModel("note", "String?", nullable = true, defaultValue = "null"),
            ),
            item.context["fields"],
        )
        assertEquals(listOf("com.acme.order.OrderId"), item.context["imports"])
        assertEquals(
            listOf(DesignRenderFieldModel("accepted", "Boolean")),
            item.context["resultFields"],
        )
    }

    @Test
    fun `uses checked-in conflict policy without generator-side type registry resolution`() {
        val item = DesignCommandArtifactPlanner().plan(
            config(),
            CanonicalModel(designBlocks = listOf(designBlock("command", "command", name = "SubmitOrder"))),
        ).single()

        assertEquals(ConflictPolicy.SKIP, item.conflictPolicy)
    }

    @Test
    fun `renders an external symbol with the outer declaration name as an explicit fqn`() {
        val request = definition(
            path = "FooCmd.Request",
            role = SemanticValueRole.COMMAND_REQUEST,
            fields = listOf(
                SemanticValueField(
                    name = "upstream",
                    type = SemanticNamedTypeRef(
                        CanonicalTypeIdentity(
                            packageName = "com.vendor.commands",
                            typePath = listOf("FooCmd"),
                            kind = CanonicalTypeKind.EXTERNAL,
                        ),
                    ),
                )
            ),
        )
        val block = designBlock(
            tag = "command",
            family = "command",
            packageName = "order.submit",
            name = "Foo",
            requestDefinition = request,
        )

        val item = DesignCommandArtifactPlanner().plan(config(), CanonicalModel(designBlocks = listOf(block))).single()

        assertEquals(
            listOf(DesignRenderFieldModel("upstream", "com.vendor.commands.FooCmd")),
            item.context["fields"],
        )
        assertEquals(emptyList<String>(), item.context["imports"])
    }

    @Test
    fun `fails when application module is missing`() {
        val error = assertThrows(IllegalStateException::class.java) {
            DesignCommandArtifactPlanner().plan(
                config(modules = emptyMap()),
                CanonicalModel(designBlocks = listOf(designBlock("command", "command", name = "SubmitOrder"))),
            )
        }

        assertEquals("application module is required", error.message)
    }

    private fun definition(
        path: String,
        role: SemanticValueRole,
        fields: List<SemanticValueField> = emptyList(),
    ) = SemanticValueDefinition(
        identity = CanonicalTypeIdentity(
            packageName = "com.acme.demo.application.commands.order.submit",
            typePath = path.split('.'),
            kind = CanonicalTypeKind.NESTED_VALUE,
        ),
        role = role,
        fields = fields,
    )

    private fun config(
        modules: Map<String, String> = mapOf("application" to "demo-application"),
    ) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        generators = mapOf("command" to GeneratorConfig()),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}
