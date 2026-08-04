package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.only4.cap4k.plugin.pipeline.api.AggregateElementModel
import com.only4.cap4k.plugin.pipeline.json.PipelineJson
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DrawingBoardAggregateElementsTemplateTest {
    @Test
    fun `renders aggregate structure without a design tag`() {
        val repository = AggregateElementModel(
            carrierQualifiedName = "com.acme.demo.adapter.domain.repositories.OrderJpaRepositoryAdapter",
            aggregate = "Order",
            name = "OrderRepository",
            packageName = "com.acme.demo.adapter.domain.repositories",
            description = "Order \"provider\" carrier",
            type = "repository",
            root = false,
        )
        val rendered = PebbleArtifactRenderer(
            PresetTemplateResolver(preset = "ddd-default", overrideDirs = emptyList()),
        ).render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "drawing-board",
                    moduleRole = "project",
                    templateId = "drawing-board/aggregate-elements.json.peb",
                    outputPath = "design/drawing_board_aggregate_elements.json",
                    context = mapOf("aggregateElements" to listOf(repository)),
                    conflictPolicy = ConflictPolicy.OVERWRITE,
                    outputKind = ArtifactOutputKind.OUTPUT_ARTIFACT,
                    resolvedOutputRoot = "design",
                ),
            ),
            config = ProjectConfig(),
        ).single()

        val element = PipelineJson.newMapper().readTree(rendered.content).single()
        assertEquals(repository.carrierQualifiedName, element.get("carrierQualifiedName").asText())
        assertEquals(repository.aggregate, element.get("aggregate").asText())
        assertEquals(repository.name, element.get("name").asText())
        assertEquals(repository.packageName, element.get("packageName").asText())
        assertEquals(repository.description, element.get("description").asText())
        assertEquals(repository.type, element.get("type").asText())
        assertEquals(repository.root, element.get("root").asBoolean())
        assertFalse(element.has("tag"))
    }
}
