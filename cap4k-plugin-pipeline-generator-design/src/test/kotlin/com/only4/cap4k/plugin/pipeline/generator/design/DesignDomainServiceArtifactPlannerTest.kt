package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignDomainServiceArtifactPlannerTest {

    @Test
    fun `plans checked in domain service skeleton`() {
        val planner = DesignDomainServiceArtifactPlanner()
        assertEquals("domain-service", planner.id)
        val model = CanonicalModel(
            designBlocks = listOf(domainServiceBlock()),
        )

        val items = planner.plan(configWithDomainModule(), model)

        val item = items.single()
        assertEquals("domain-service", item.generatorId)
        assertEquals("domain", item.moduleRole)
        assertEquals("design/domain_service.kt.peb", item.templateId)
        assertEquals(ArtifactOutputKind.CHECKED_IN_SOURCE, item.outputKind)
        assertEquals(ConflictPolicy.SKIP, item.conflictPolicy)
        assertEquals(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/services/content/domain/ContentPublicationPolicy.kt",
            item.outputPath,
        )
        assertEquals("com.acme.demo.domain.services.content.domain", item.context["packageName"])
        assertEquals("ContentPublicationPolicy", item.context["name"])
        assertEquals(
            mapOf(
                "tag" to "domain_service",
                "name" to "ContentPublicationPolicy",
                "packageName" to "content.domain",
                "description" to "publication policy",
                "descriptionKotlinStringLiteral" to "\"publication policy\"",
                "aggregates" to listOf("Content"),
                "eventName" to "",
                "family" to "domain-service",
                "variant" to "",
            ),
            item.context["buildingBlock"],
        )
    }

    @Test
    fun `empty domain service slice does not require domain module`() {
        val items = DesignDomainServiceArtifactPlanner().plan(
            configWithoutDomainModule(),
            CanonicalModel(designBlocks = emptyList()),
        )

        assertTrue(items.isEmpty())
    }

    private fun configWithDomainModule() = projectConfig(modules = mapOf("domain" to "demo-domain"))

    private fun configWithoutDomainModule() = projectConfig(modules = emptyMap())

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("domain-service" to GeneratorConfig()),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )

    private fun domainServiceBlock() = designBlock(
        tag = "domain_service",
        family = "domain-service",
        packageName = "content.domain",
        name = "ContentPublicationPolicy",
        description = "publication policy",
        aggregates = listOf("Content"),
    )
}
