package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DesignEndpointRpcArtifactPlannerTest {
    @Test
    fun `plans explicit provider and client projections`() {
        val items = DesignEndpointRpcArtifactPlanner().plan(config(), CanonicalModel(endpoints = listOf(endpoint())))

        assertEquals(4, items.size)
        assertEquals(setOf("adapter", "endpoint-client"), items.map { it.moduleRole }.toSet())
        assertEquals(3, items.count { it.outputKind == ArtifactOutputKind.GENERATED_SOURCE })
        assertEquals(1, items.count { it.outputKind == ArtifactOutputKind.GENERATED_RESOURCE })
        assertEquals(
            "demo-client/build/generated/cap4k/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
            items.single { it.outputKind == ArtifactOutputKind.GENERATED_RESOURCE }.outputPath,
        )
        val provider = items.single { it.templateId.endsWith("provider-bindings.kt.peb") }
        @Suppress("UNCHECKED_CAST")
        val operations = provider.context.getValue("operations") as List<Map<String, Any?>>
        assertEquals("com.acme.contract.CreateBookingEndpoint.Request", operations.single()["requestFqn"])
    }

    @Test
    fun `rejects unknown explicit operation before rendering`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignEndpointRpcArtifactPlanner().plan(
                config(operationNames = listOf("booking.missing")),
                CanonicalModel(endpoints = listOf(endpoint())),
            )
        }
        assertEquals(
            "generators.endpointRpc.operationNames contains unknown Endpoint operations: booking.missing",
            error.message,
        )
    }

    private fun config(operationNames: List<String> = listOf("booking.create")) = ProjectConfig(
        basePackage = "com.acme",
        modules = mapOf(
            "contract" to "demo-contract",
            "adapter" to "demo-adapter",
            "endpoint-client" to "demo-client",
        ),
        generators = mapOf(
            "endpoint-rpc" to GeneratorConfig(
                mapOf("serviceId" to "booking-service", "operationNames" to operationNames),
            ),
        ),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )

    private fun endpoint(): EndpointModel {
        val request = SemanticValueDefinition(
            CanonicalTypeIdentity("com.acme.contract", listOf("CreateBookingEndpoint", "Request"), CanonicalTypeKind.NESTED_VALUE),
            SemanticValueRole.ENDPOINT_REQUEST,
            emptyList(),
        )
        val response = SemanticValueDefinition(
            CanonicalTypeIdentity("com.acme.contract", listOf("CreateBookingEndpoint", "Response"), CanonicalTypeKind.NESTED_VALUE),
            SemanticValueRole.ENDPOINT_RESPONSE,
            emptyList(),
        )
        return EndpointModel("booking.create", "com.acme.contract", "CreateBookingEndpoint", "create", emptyList(), request, response)
    }
}
