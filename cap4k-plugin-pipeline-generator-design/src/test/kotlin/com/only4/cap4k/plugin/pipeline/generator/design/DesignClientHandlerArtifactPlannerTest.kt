package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.PackageLayout
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DesignClientHandlerArtifactPlannerTest {

    @Test
    fun `plans client handler artifacts into adapter distributed clients path`() {
        val planner = DesignClientHandlerArtifactPlanner()
        assertEquals("client-handler", planner.id)

        val items = planner.plan(
            config = projectConfig(
                modules = mapOf(
                    "application" to "demo-application",
                    "adapter" to "demo-adapter",
                )
            ),
            model = CanonicalModel(
                designBlocks = listOf(
                    clientBlock(),
                ),
            ),
        )

        val handler = items.single()
        assertEquals("client-handler", handler.generatorId)
        assertEquals("design/client_handler.kt.peb", handler.templateId)
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/distributed/clients/authorize/IssueTokenCliHandler.kt",
            handler.outputPath,
        )
        assertEquals(
            "com.acme.demo.adapter.application.distributed.clients.authorize",
            handler.context["packageName"],
        )
        assertEquals("IssueTokenCliHandler", handler.context["typeName"])
        assertEquals("IssueTokenCli", handler.context["clientTypeName"])
        assertEquals(
            listOf("com.acme.demo.application.distributed.clients.authorize.IssueTokenCli"),
            handler.context["imports"],
        )
    }

    @Test
    fun `client block without client handler selection does not emit handler`() {
        val planner = DesignClientHandlerArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(
                modules = mapOf(
                    "application" to "demo-application",
                    "adapter" to "demo-adapter",
                )
            ),
            model = CanonicalModel(
                designBlocks = listOf(
                    designBlock(
                        tag = "client",
                        family = "client",
                        packageName = "authorize",
                        name = "IssueToken",
                        description = "issue token",
                    ),
                ),
            ),
        )

        assertEquals(emptyList<Any>(), items)
    }

    @Test
    fun `custom client handler layout imports client from custom client layout`() {
        val planner = DesignClientHandlerArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(
                modules = mapOf(
                    "application" to "demo-application",
                    "adapter" to "demo-adapter",
                ),
                artifactLayout = ArtifactLayoutConfig(
                    designClient = PackageLayout("application.remote.clients"),
                    designClientHandler = PackageLayout("adapter.remote.clients"),
                ),
            ),
            model = CanonicalModel(
                designBlocks = listOf(clientBlock()),
            ),
        )

        val handler = items.single()
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/remote/clients/authorize/IssueTokenCliHandler.kt",
            handler.outputPath,
        )
        assertEquals("com.acme.demo.adapter.remote.clients.authorize", handler.context["packageName"])
        assertEquals(
            listOf("com.acme.demo.application.remote.clients.authorize.IssueTokenCli"),
            handler.context["imports"],
        )
    }

    private fun projectConfig(
        modules: Map<String, String>,
        artifactLayout: ArtifactLayoutConfig = ArtifactLayoutConfig(),
    ) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("client-handler" to GeneratorConfig()),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        artifactLayout = artifactLayout,
    )

    private fun clientBlock() = designBlock(
        tag = "client",
        family = "client-handler",
        packageName = "authorize",
        name = "IssueToken",
        description = "issue token",
    )
}
