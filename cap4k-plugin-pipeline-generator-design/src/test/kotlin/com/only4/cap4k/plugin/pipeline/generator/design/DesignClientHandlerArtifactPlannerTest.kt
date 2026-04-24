package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ClientModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignClientHandlerArtifactPlannerTest {

    @Test
    fun `plans client handler artifacts into adapter distributed clients path`() {
        val planner = DesignClientHandlerArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(
                modules = mapOf(
                    "application" to "demo-application",
                    "adapter" to "demo-adapter",
                )
            ),
            model = CanonicalModel(
                clients = listOf(
                    ClientModel(
                        packageName = "authorize",
                        typeName = "IssueTokenCli",
                        description = "issue token",
                    ),
                ),
            ),
        )

        val handler = items.single()
        assertEquals("design-client-handler", handler.generatorId)
        assertEquals("design/client_handler.kt.peb", handler.templateId)
        assertTrue(
            handler.outputPath.endsWith("adapter/application/distributed/clients/authorize/IssueTokenCliHandler.kt"),
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

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("design-client-handler" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}
