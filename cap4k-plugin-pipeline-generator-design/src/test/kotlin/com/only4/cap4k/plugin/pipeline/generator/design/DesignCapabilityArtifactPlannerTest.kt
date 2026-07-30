package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DesignCapabilityArtifactPlannerTest {

    @Test
    fun `plans capability artifacts into application capabilities path`() {
        val planner = DesignCapabilityArtifactPlanner()
        assertEquals("capability", planner.id)

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
                        tag = "capability",
                        family = "capability",
                        packageName = "authorize",
                        name = "IssueToken",
                        description = "issue token",
                    ),
                ),
            ),
        )

        val capability = items.single()
        assertEquals("capability", capability.generatorId)
        assertEquals("design/capability.kt.peb", capability.templateId)
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/capabilities/authorize/IssueToken.kt",
            capability.outputPath,
        )
        assertEquals(
            "com.acme.demo.application.capabilities.authorize",
            capability.context["packageName"],
        )
    }

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("capability" to GeneratorConfig()),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}
