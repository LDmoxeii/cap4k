package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EndpointRpcTemplateTest {
    @Test
    fun `renders provider client and metadata templates`() {
        val operation = mapOf<String, Any?>(
            "ownerFqn" to "com.acme.CreateBookingEndpoint",
            "requestFqn" to "com.acme.CreateBookingEndpoint.Request",
            "responseFqn" to "com.acme.CreateBookingEndpoint.Response",
            "handlerTypeName" to "CreateBookingEndpointRemoteEndpointHandler",
            "beanMethodName" to "createBookingEndpointRemoteEndpointHandler",
        )
        val context = mapOf<String, Any?>(
            "packageName" to "com.acme.generated",
            "serviceIdKotlinStringLiteral" to "\"booking-service\"",
            "operations" to listOf(operation),
        )
        val renderer = PebbleArtifactRenderer(PresetTemplateResolver("ddd-default", emptyList()))
        val rendered = renderer.render(listOf(
            item("design/endpoint-rpc-provider-bindings.kt.peb", "Provider.kt", context),
            item("design/endpoint-rpc-remote-handler.kt.peb", "Handler.kt", context + operation),
            item("design/endpoint-rpc-client-auto-configuration.kt.peb", "AutoConfiguration.kt", context),
            item("design/endpoint-rpc-auto-configuration.imports.peb", "imports", context + ("autoConfigurationFqn" to "com.acme.generated.EndpointRpcClientAutoConfiguration")),
        ), ProjectConfig())
        assertTrue(rendered[0].content.contains("CreateBookingEndpoint.OPERATION_NAME"))
        assertTrue(rendered[1].content.contains("RemoteEndpointHandler"))
        assertTrue(rendered[2].content.contains("@org.springframework.boot.autoconfigure.AutoConfiguration"))
        assertTrue(rendered[3].content.trim() == "com.acme.generated.EndpointRpcClientAutoConfiguration")
    }

    private fun item(template: String, path: String, context: Map<String, Any?>) = ArtifactPlanItem(
        generatorId = "test",
        moduleRole = "endpoint-client",
        templateId = template,
        outputPath = path,
        context = context,
        conflictPolicy = ConflictPolicy.SKIP,
    )
}
