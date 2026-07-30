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

class DesignCapabilityHandlerArtifactPlannerTest {

    @Test
    fun `plans capability handler artifacts into adapter capabilities path`() {
        val planner = DesignCapabilityHandlerArtifactPlanner()
        assertEquals("capability-handler", planner.id)

        val items = planner.plan(
            config = projectConfig(
                modules = mapOf(
                    "application" to "demo-application",
                    "adapter" to "demo-adapter",
                )
            ),
            model = CanonicalModel(
                designBlocks = listOf(
                    capabilityBlock(),
                ),
            ),
        )

        val handler = items.single()
        assertEquals("capability-handler", handler.generatorId)
        assertEquals("design/capability_handler.kt.peb", handler.templateId)
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/capabilities/authorize/IssueTokenHandler.kt",
            handler.outputPath,
        )
        assertEquals(
            "com.acme.demo.adapter.application.capabilities.authorize",
            handler.context["packageName"],
        )
        assertEquals("IssueTokenHandler", handler.context["typeName"])
        assertEquals("IssueToken", handler.context["capabilityTypeName"])
        assertEquals(
            listOf("com.acme.demo.application.capabilities.authorize.IssueToken"),
            handler.context["imports"],
        )
    }

    @Test
    fun `capability block without capability handler selection does not emit handler`() {
        val planner = DesignCapabilityHandlerArtifactPlanner()

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

        assertEquals(emptyList<Any>(), items)
    }

    @Test
    fun `custom capability handler layout imports capability from custom capability layout`() {
        val planner = DesignCapabilityHandlerArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(
                modules = mapOf(
                    "application" to "demo-application",
                    "adapter" to "demo-adapter",
                ),
                artifactLayout = ArtifactLayoutConfig(
                    designCapability = PackageLayout("application.remote.capabilities"),
                    designCapabilityHandler = PackageLayout("adapter.remote.capabilities"),
                ),
            ),
            model = CanonicalModel(
                designBlocks = listOf(capabilityBlock()),
            ),
        )

        val handler = items.single()
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/remote/capabilities/authorize/IssueTokenHandler.kt",
            handler.outputPath,
        )
        assertEquals("com.acme.demo.adapter.remote.capabilities.authorize", handler.context["packageName"])
        assertEquals(
            listOf("com.acme.demo.application.remote.capabilities.authorize.IssueToken"),
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
        generators = mapOf("capability-handler" to GeneratorConfig()),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        artifactLayout = artifactLayout,
    )

    private fun capabilityBlock() = designBlock(
        tag = "capability",
        family = "capability-handler",
        packageName = "authorize",
        name = "IssueToken",
        description = "issue token",
    )
}
