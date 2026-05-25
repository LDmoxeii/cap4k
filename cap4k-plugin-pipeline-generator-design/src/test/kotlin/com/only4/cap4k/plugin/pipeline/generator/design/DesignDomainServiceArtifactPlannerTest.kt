package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DomainServiceModel
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
        val model = canonicalModel(
            domainServices = listOf(
                DomainServiceModel(
                    name = "ContentPublicationPolicy",
                    packageName = "content.domain",
                    description = "publication policy",
                    aggregates = listOf("Content"),
                ),
            ),
        )

        val items = DesignDomainServiceArtifactPlanner().plan(configWithDomainModule(), model)

        val item = items.single()
        assertEquals("design-domain-service", item.generatorId)
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
    }

    @Test
    fun `empty domain service slice does not require domain module`() {
        val items = DesignDomainServiceArtifactPlanner().plan(
            configWithoutDomainModule(),
            canonicalModel(domainServices = emptyList()),
        )

        assertTrue(items.isEmpty())
    }

    private fun canonicalModel(domainServices: List<DomainServiceModel>) = CanonicalModel(
        domainServices = domainServices,
    )

    private fun configWithDomainModule() = projectConfig(modules = mapOf("domain" to "demo-domain"))

    private fun configWithoutDomainModule() = projectConfig(modules = emptyMap())

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("design-domain-service" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}
