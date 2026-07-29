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
import com.only4.cap4k.plugin.pipeline.api.SemanticListTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition
import com.only4.cap4k.plugin.pipeline.api.SemanticValueEnvelope
import com.only4.cap4k.plugin.pipeline.api.SemanticValueField
import com.only4.cap4k.plugin.pipeline.api.SemanticValueRole
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignApiPayloadArtifactPlannerTest {
    @Test
    fun `renders canonical nested definitions without parsing field paths`() {
        val requestIdentity = identity("com.acme.demo.adapter.portal.api.payload.video", "SyncVideo.Request")
        val fileItem = definition(
            identity = identity(requestIdentity.packageName, "SyncVideo.Request.FileItem"),
            role = SemanticValueRole.API_PAYLOAD_REQUEST,
            fields = listOf(field("index", SemanticBuiltinTypeRef(SemanticBuiltinType.INT))),
        )
        val request = definition(
            identity = requestIdentity,
            role = SemanticValueRole.API_PAYLOAD_REQUEST,
            fields = listOf(field("files", SemanticListTypeRef(SemanticNamedTypeRef(fileItem.identity)))),
            nestedDefinitions = listOf(fileItem),
        )
        val block = designBlock(
            tag = "api_payload",
            family = "api-payload",
            packageName = "video",
            name = "SyncVideo",
            requestDefinition = request,
            responseDefinition = definition(
                identity(requestIdentity.packageName, "SyncVideo.Response"),
                SemanticValueRole.API_PAYLOAD_RESPONSE,
            ),
        )

        val payload = DesignApiPayloadArtifactPlanner().plan(config(), CanonicalModel(designBlocks = listOf(block))).single()

        assertEquals(
            listOf(DesignRenderFieldModel("files", "List<FileItem>")),
            payload.context["fields"],
        )
        assertEquals(
            listOf(DesignRenderNestedTypeModel("FileItem", listOf(DesignRenderFieldModel("index", "Int")))),
            payload.context["nestedTypes"],
        )
    }

    @Test
    fun `renders Page envelope and item definition while keeping PageData outside type algebra`() {
        val packageName = "com.acme.demo.adapter.portal.api.payload.order"
        val item = definition(
            identity(packageName, "FindOrders.Response.Item"),
            SemanticValueRole.API_PAYLOAD_RESPONSE,
            fields = listOf(field("id", SemanticBuiltinTypeRef(SemanticBuiltinType.LONG))),
        )
        val response = definition(
            identity(packageName, "FindOrders.Response"),
            SemanticValueRole.API_PAYLOAD_RESPONSE,
            envelope = SemanticValueEnvelope.Page(item),
        )
        val block = designBlock(
            tag = "api_payload",
            family = "api-payload",
            packageName = "order",
            name = "FindOrders",
            responseDefinition = response,
        )

        val payload = DesignApiPayloadArtifactPlanner().plan(config(), CanonicalModel(designBlocks = listOf(block))).single()

        assertEquals(listOf(DesignRenderFieldModel("page", "PageData<Item>")), payload.context["resultFields"])
        assertEquals(listOf("com.only4.cap4k.ddd.core.share.PageData"), payload.context["imports"])
        assertEquals(
            listOf(DesignRenderNestedTypeModel("Item", listOf(DesignRenderFieldModel("id", "Long")))),
            payload.context["resultNestedTypes"],
        )
    }

    @Test
    fun `uses explicit FQN when an imported symbol collides with a local nested type`() {
        val packageName = "com.acme.demo.adapter.portal.api.payload.order"
        val localItem = definition(identity(packageName, "Payload.Request.Item"), SemanticValueRole.API_PAYLOAD_REQUEST)
        val request = definition(
            identity(packageName, "Payload.Request"),
            SemanticValueRole.API_PAYLOAD_REQUEST,
            fields = listOf(
                field("local", SemanticNamedTypeRef(localItem.identity)),
                field(
                    "external",
                    SemanticNamedTypeRef(
                        CanonicalTypeIdentity("com.acme.shared", listOf("Item"), CanonicalTypeKind.EXTERNAL),
                    ),
                ),
            ),
            nestedDefinitions = listOf(localItem),
        )
        val block = designBlock(
            tag = "api_payload",
            family = "api-payload",
            name = "Payload",
            requestDefinition = request,
        )

        val payload = DesignApiPayloadArtifactPlanner().plan(config(), CanonicalModel(designBlocks = listOf(block))).single()
        @Suppress("UNCHECKED_CAST")
        val fields = payload.context["fields"] as List<DesignRenderFieldModel>

        assertEquals("Item", fields[0].renderedType)
        assertEquals("com.acme.shared.Item", fields[1].renderedType)
        assertEquals(emptyList<String>(), payload.context["imports"])
    }

    @Test
    fun `fails instead of silently dropping colliding flattened nested definitions`() {
        val packageName = "com.acme.demo.adapter.portal.api.payload.order"
        val first = definition(identity(packageName, "Payload.Request.Item"), SemanticValueRole.API_PAYLOAD_REQUEST)
        val second = definition(identity(packageName, "Payload.Request.Group.Item"), SemanticValueRole.API_PAYLOAD_REQUEST)
        val request = definition(
            identity(packageName, "Payload.Request"),
            SemanticValueRole.API_PAYLOAD_REQUEST,
            nestedDefinitions = listOf(first, second),
        )
        val block = designBlock(
            tag = "api_payload",
            family = "api-payload",
            name = "Payload",
            requestDefinition = request,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignApiPayloadArtifactPlanner().plan(config(), CanonicalModel(designBlocks = listOf(block)))
        }

        assertTrue(error.message.orEmpty().contains("colliding flattened nested type Item"))
    }

    @Test
    fun `page artifact variant still controls PageRequest independently from response envelope`() {
        val block = designBlock(
            tag = "api_payload",
            family = "api-payload",
            variant = "page",
            name = "FindOrders",
        )

        val payload = DesignApiPayloadArtifactPlanner().plan(config(), CanonicalModel(designBlocks = listOf(block))).single()

        assertEquals(true, payload.context["pageRequest"])
    }

    private fun config() = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = mapOf("adapter" to "demo-adapter"),
        generators = mapOf("api-payload" to GeneratorConfig()),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )

    private fun identity(packageName: String, path: String) =
        CanonicalTypeIdentity(packageName, path.split('.'), CanonicalTypeKind.NESTED_VALUE)

    private fun field(name: String, type: com.only4.cap4k.plugin.pipeline.api.SemanticTypeRef) =
        SemanticValueField(name, type)

    private fun definition(
        identity: CanonicalTypeIdentity,
        role: SemanticValueRole,
        fields: List<SemanticValueField> = emptyList(),
        nestedDefinitions: List<SemanticValueDefinition> = emptyList(),
        envelope: SemanticValueEnvelope? = null,
    ) = SemanticValueDefinition(identity, role, fields, nestedDefinitions, envelope)
}
