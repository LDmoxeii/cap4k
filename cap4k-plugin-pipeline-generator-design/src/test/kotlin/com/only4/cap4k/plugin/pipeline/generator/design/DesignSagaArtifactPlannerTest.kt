package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SagaModel
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignSagaArtifactPlannerTest {

    @Test
    fun `plans saga param result and handler skeletons`() {
        val model = canonicalModel(
            sagas = listOf(
                SagaModel(
                    name = "PublishContentSaga",
                    packageName = "content.workflow",
                    requestFields = listOf(FieldModel(name = "contentId", type = "ContentId")),
                    responseFields = listOf(FieldModel(name = "accepted", type = "Boolean")),
                ),
            ),
        )

        val items = DesignSagaArtifactPlanner().plan(configWithApplicationModule(), model)

        assertEquals(
            setOf("design/saga_param.kt.peb", "design/saga_result.kt.peb", "design/saga_handler.kt.peb"),
            items.map { it.templateId }.toSet(),
        )
        assertTrue(items.all { it.outputKind == ArtifactOutputKind.CHECKED_IN_SOURCE })
    }

    @Test
    fun `empty saga slice does not require application module`() {
        val items = DesignSagaArtifactPlanner().plan(configWithoutApplicationModule(), canonicalModel(sagas = emptyList()))

        assertTrue(items.isEmpty())
    }

    private fun canonicalModel(sagas: List<SagaModel>) = CanonicalModel(
        sagas = sagas,
    )

    private fun configWithApplicationModule() = projectConfig(modules = mapOf("application" to "demo-application"))

    private fun configWithoutApplicationModule() = projectConfig(modules = emptyMap())

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("design-saga" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}
