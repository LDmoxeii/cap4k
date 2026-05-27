package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SagaModel
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind
import com.only4.cap4k.plugin.pipeline.api.StrongIdModel
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignSagaArtifactPlannerTest {

    @Test
    fun `plans one saga skeleton file`() {
        val model = canonicalModel(
            sagas = listOf(
                SagaModel(
                    name = "PublishContentSaga",
                    packageName = "content.workflow",
                    requestFields = listOf(FieldModel(name = "contentId", type = "ContentId")),
                    responseFields = listOf(FieldModel(name = "accepted", type = "Boolean")),
                ),
            ),
            strongIds = listOf(
                StrongIdModel(
                    typeName = "ContentId",
                    packageName = "com.acme.demo.domain.shared.ids",
                    kind = StrongIdKind.REFERENCE,
                ),
            ),
        )

        val items = DesignSagaArtifactPlanner().plan(configWithApplicationModule(), model)

        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("design/saga.kt.peb", item.templateId)
        assertEquals(ArtifactOutputKind.CHECKED_IN_SOURCE, item.outputKind)
        assertEquals("design-saga", item.generatorId)
        assertEquals("application", item.moduleRole)
        assertEquals(ConflictPolicy.SKIP, item.conflictPolicy)
        assertTrue(
            item.outputPath.startsWith("demo-application/src/main/kotlin/com/acme/demo/application/sagas/content/workflow/"),
        )

        assertEquals("PublishContentSaga.kt", item.outputPath.substringAfterLast('/'))
        assertEquals("com.acme.demo.application.sagas.content.workflow", item.context["packageName"])
        assertEquals(listOf("com.acme.demo.domain.shared.ids.ContentId"), item.context["imports"])
        assertEquals(
            listOf(DesignRenderFieldModel(name = "contentId", renderedType = "ContentId")),
            item.context["requestFields"],
        )
        assertEquals(
            listOf(DesignRenderFieldModel(name = "accepted", renderedType = "Boolean")),
            item.context["responseFields"],
        )
    }

    @Test
    fun `empty saga slice does not require application module`() {
        val items = DesignSagaArtifactPlanner().plan(configWithoutApplicationModule(), canonicalModel(sagas = emptyList()))

        assertTrue(items.isEmpty())
    }

    private fun canonicalModel(
        sagas: List<SagaModel>,
        strongIds: List<StrongIdModel> = emptyList(),
    ) = CanonicalModel(
        sagas = sagas,
        strongIds = strongIds,
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
