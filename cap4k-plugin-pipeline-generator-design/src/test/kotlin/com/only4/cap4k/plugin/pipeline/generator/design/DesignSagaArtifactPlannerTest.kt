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
            strongIds = listOf(
                StrongIdModel(
                    typeName = "ContentId",
                    packageName = "com.acme.demo.domain.shared.ids",
                    kind = StrongIdKind.REFERENCE,
                ),
            ),
        )

        val items = DesignSagaArtifactPlanner().plan(configWithApplicationModule(), model)

        assertEquals(
            setOf("design/saga_param.kt.peb", "design/saga_result.kt.peb", "design/saga_handler.kt.peb"),
            items.map { it.templateId }.toSet(),
        )
        assertTrue(items.all { it.outputKind == ArtifactOutputKind.CHECKED_IN_SOURCE })
        assertTrue(items.all { it.generatorId == "design-saga" })
        assertTrue(items.all { it.moduleRole == "application" })
        assertTrue(items.all { it.conflictPolicy == ConflictPolicy.SKIP })
        assertTrue(
            items.all {
                it.outputPath.startsWith("demo-application/src/main/kotlin/com/acme/demo/application/sagas/content/workflow/")
            },
        )

        val param = items.single { it.templateId == "design/saga_param.kt.peb" }
        assertEquals("PublishContentSagaParam.kt", param.outputPath.substringAfterLast('/'))
        assertEquals("com.acme.demo.application.sagas.content.workflow", param.context["packageName"])
        assertEquals(listOf("com.acme.demo.domain.shared.ids.ContentId"), param.context["imports"])
        assertEquals(
            listOf(DesignRenderFieldModel(name = "contentId", renderedType = "ContentId")),
            param.context["requestFields"],
        )

        val result = items.single { it.templateId == "design/saga_result.kt.peb" }
        assertEquals("PublishContentSagaResult.kt", result.outputPath.substringAfterLast('/'))
        assertEquals(
            listOf(DesignRenderFieldModel(name = "accepted", renderedType = "Boolean")),
            result.context["responseFields"],
        )

        val handler = items.single { it.templateId == "design/saga_handler.kt.peb" }
        assertEquals("PublishContentSagaHandler.kt", handler.outputPath.substringAfterLast('/'))
        assertEquals("com.acme.demo.application.sagas.content.workflow", handler.context["packageName"])
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
