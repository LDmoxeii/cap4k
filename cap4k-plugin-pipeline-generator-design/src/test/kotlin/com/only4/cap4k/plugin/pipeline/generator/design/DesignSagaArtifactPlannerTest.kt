package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind
import com.only4.cap4k.plugin.pipeline.api.StrongIdModel
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignSagaArtifactPlannerTest {

    @Test
    fun `plans one saga skeleton file`() {
        val planner = DesignSagaArtifactPlanner()
        assertEquals("saga", planner.id)
        val model = CanonicalModel(
            designBlocks = listOf(
                designBlock(
                    tag = "saga",
                    family = "saga",
                    packageName = "content.workflow",
                    name = "PublishContentSaga",
                    requestDefinition = semanticDefinition(
                        packageName = "com.acme.demo.application.sagas.content.workflow",
                        typeName = "PublishContentSaga.Request",
                        role = com.only4.cap4k.plugin.pipeline.api.SemanticValueRole.SAGA_REQUEST,
                        fields = listOf(
                            FieldModel(
                                name = "contentId",
                                type = "com.acme.demo.domain.shared.ids.ContentId",
                            ),
                        ),
                    ),
                )
            ),
            strongIds = listOf(
                StrongIdModel(
                    typeName = "ContentId",
                    packageName = "com.acme.demo.domain.shared.ids",
                    kind = StrongIdKind.REFERENCE,
                ),
            ),
        )

        val items = planner.plan(configWithApplicationModule(), model)

        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("design/saga.kt.peb", item.templateId)
        assertEquals(ArtifactOutputKind.CHECKED_IN_SOURCE, item.outputKind)
        assertEquals("saga", item.generatorId)
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
            item.context["fields"],
        )
        assertEquals(emptyList<DesignRenderFieldModel>(), item.context["resultFields"])
        @Suppress("UNCHECKED_CAST")
        val buildingBlock = item.context["buildingBlock"] as Map<String, Any?>
        assertEquals("saga", buildingBlock["family"])
        assertEquals("content.workflow", buildingBlock["packageName"])
    }

    @Test
    fun `empty saga slice does not require application module`() {
        val items = DesignSagaArtifactPlanner().plan(configWithoutApplicationModule(), CanonicalModel(designBlocks = emptyList()))

        assertTrue(items.isEmpty())
    }

    @Test
    fun `saga renderer rejects an unapproved response contract`() {
        val block = designBlock(
            tag = "saga",
            family = "saga",
            name = "PublishContentSaga",
        ).copy(
            response = semanticDefinition(
                packageName = "content.workflow",
                typeName = "PublishContentSaga.Response",
                role = com.only4.cap4k.plugin.pipeline.api.SemanticValueRole.SAGA_RESPONSE,
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            DesignSagaArtifactPlanner().plan(
                configWithApplicationModule(),
                CanonicalModel(designBlocks = listOf(block)),
            )
        }
    }

    private fun configWithApplicationModule() = projectConfig(modules = mapOf("application" to "demo-application"))

    private fun configWithoutApplicationModule() = projectConfig(modules = emptyMap())

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("saga" to GeneratorConfig()),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}
