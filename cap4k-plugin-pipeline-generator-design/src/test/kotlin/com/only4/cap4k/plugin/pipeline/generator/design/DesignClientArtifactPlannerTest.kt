package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DesignClientArtifactPlannerTest {

    @Test
    fun `plans client artifacts into application distributed clients path`() {
        val planner = DesignClientArtifactPlanner()
        assertEquals("client", planner.id)

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

        val client = items.single()
        assertEquals("client", client.generatorId)
        assertEquals("design/client.kt.peb", client.templateId)
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/distributed/clients/authorize/IssueTokenCli.kt",
            client.outputPath,
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
        generators = mapOf("client" to GeneratorConfig()),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}
