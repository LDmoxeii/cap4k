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

class DesignClientArtifactPlannerTest {

    @Test
    fun `plans client artifacts into application distributed clients path`() {
        val planner = DesignClientArtifactPlanner()

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

        val client = items.single()
        assertEquals("design-client", client.generatorId)
        assertEquals("design/client.kt.peb", client.templateId)
        assertTrue(
            client.outputPath.endsWith("application/distributed/clients/authorize/IssueTokenCli.kt"),
        )
        assertEquals(
            "com.acme.demo.application.distributed.clients.authorize",
            client.context["packageName"],
        )
    }

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("design-client" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}
