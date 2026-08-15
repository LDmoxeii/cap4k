package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeKind
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinType
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition
import com.only4.cap4k.plugin.pipeline.api.SemanticValueField
import com.only4.cap4k.plugin.pipeline.api.SemanticValueRole
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DesignEndpointArtifactPlannerTest {
    @Test
    fun `plans endpoint published contract into contract module`() {
        val requestDetails = CanonicalTypeIdentity(
            packageName = "com.acme.demo.contract.endpoints.booking",
            typePath = listOf("CreateBookingEndpoint", "Request", "Details"),
            kind = CanonicalTypeKind.NESTED_VALUE,
        )
        val responseReceipt = CanonicalTypeIdentity(
            packageName = "com.acme.demo.contract.endpoints.booking",
            typePath = listOf("CreateBookingEndpoint", "Response", "Receipt"),
            kind = CanonicalTypeKind.NESTED_VALUE,
        )
        val block = designBlock(
            tag = "endpoint",
            family = "endpoint",
            packageName = "booking",
            name = "CreateBookingEndpoint",
            description = "create booking",
            operationName = "booking.create",
            requestDefinition = SemanticValueDefinition(
                identity = CanonicalTypeIdentity(
                    "com.acme.demo.contract.endpoints.booking",
                    listOf("CreateBookingEndpoint", "Request"),
                    CanonicalTypeKind.NESTED_VALUE,
                ),
                role = SemanticValueRole.ENDPOINT_REQUEST,
                fields = listOf(
                    SemanticValueField("customerId", SemanticBuiltinTypeRef(SemanticBuiltinType.STRING)),
                    SemanticValueField("details", SemanticNamedTypeRef(requestDetails)),
                ),
                nestedDefinitions = listOf(
                    SemanticValueDefinition(
                        identity = requestDetails,
                        role = SemanticValueRole.ENDPOINT_REQUEST,
                        fields = listOf(
                            SemanticValueField("note", SemanticBuiltinTypeRef(SemanticBuiltinType.STRING, nullable = true)),
                        ),
                    ),
                ),
            ),
            responseDefinition = SemanticValueDefinition(
                identity = CanonicalTypeIdentity(
                    "com.acme.demo.contract.endpoints.booking",
                    listOf("CreateBookingEndpoint", "Response"),
                    CanonicalTypeKind.NESTED_VALUE,
                ),
                role = SemanticValueRole.ENDPOINT_RESPONSE,
                fields = listOf(
                    SemanticValueField("bookingId", SemanticBuiltinTypeRef(SemanticBuiltinType.STRING)),
                    SemanticValueField("receipt", SemanticNamedTypeRef(responseReceipt)),
                ),
                nestedDefinitions = listOf(
                    SemanticValueDefinition(
                        identity = responseReceipt,
                        role = SemanticValueRole.ENDPOINT_RESPONSE,
                        fields = listOf(
                            SemanticValueField("code", SemanticBuiltinTypeRef(SemanticBuiltinType.STRING)),
                        ),
                    ),
                ),
            ),
        )

        val item = DesignEndpointArtifactPlanner().plan(
            config = config(),
            model = CanonicalModel(designBlocks = listOf(block)),
        ).single()

        assertEquals("endpoint", item.generatorId)
        assertEquals("contract", item.moduleRole)
        assertEquals("design/endpoint.kt.peb", item.templateId)
        assertEquals(
            "demo-contract/src/main/kotlin/com/acme/demo/contract/endpoints/booking/CreateBookingEndpoint.kt",
            item.outputPath,
        )
        assertEquals("booking.create", item.context["operationName"])
        assertEquals("\"booking.create\"", item.context["operationNameKotlinStringLiteral"])
        assertEquals(
            listOf(
                DesignRenderFieldModel("customerId", "String"),
                DesignRenderFieldModel("details", "Details"),
            ),
            item.context["fields"],
        )
        assertEquals(
            listOf(DesignRenderNestedTypeModel("Details", listOf(DesignRenderFieldModel("note", "String?", nullable = true)))),
            item.context["nestedTypes"],
        )
        assertEquals(
            listOf(
                DesignRenderFieldModel("bookingId", "String"),
                DesignRenderFieldModel("receipt", "Receipt"),
            ),
            item.context["resultFields"],
        )
        assertEquals(
            listOf(DesignRenderNestedTypeModel("Receipt", listOf(DesignRenderFieldModel("code", "String")))),
            item.context["resultNestedTypes"],
        )
        @Suppress("UNCHECKED_CAST")
        val buildingBlock = item.context["buildingBlock"] as Map<String, Any?>
        assertEquals("endpoint", buildingBlock["tag"])
        assertEquals("endpoint", buildingBlock["family"])
        assertEquals(ConflictPolicy.SKIP, item.conflictPolicy)
    }

    @Test
    fun `fails with stable contract module diagnostic`() {
        val error = assertThrows(IllegalStateException::class.java) {
            DesignEndpointArtifactPlanner().plan(
                config = config(modules = emptyMap()),
                model = CanonicalModel(
                    designBlocks = listOf(
                        designBlock(
                            tag = "endpoint",
                            family = "endpoint",
                            name = "CreateBookingEndpoint",
                            operationName = "booking.create",
                            fields = listOf(FieldModel("customerId", "String")),
                            resultFields = listOf(FieldModel("bookingId", "String")),
                        ),
                    ),
                ),
            )
        }

        assertEquals("contract module is required", error.message)
    }

    private fun config(
        modules: Map<String, String> = mapOf("contract" to "demo-contract"),
    ) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        generators = mapOf("endpoint" to GeneratorConfig()),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}
