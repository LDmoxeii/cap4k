package com.only4.cap4k.plugin.pipeline.generator.drawingboard

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardElementModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DrawingBoardArtifactPlanner : GeneratorProvider {
    override val id: String = "drawing-board"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val elementsByTag = when {
            model.designBlocks.isNotEmpty() -> model.designBlocks
                .filter { block -> block.tag in supportedTags }
                .groupBy { block -> block.tag }
            else -> model.drawingBoard?.elementsByTag ?: return emptyList()
        }

        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val outputRoot = artifactLayout.drawingBoardOutputRoot()

        return supportedTags.flatMap { tag ->
            val elements = elementsByTag[tag].orEmpty()
            if (elements.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    ArtifactPlanItem(
                        generatorId = id,
                        moduleRole = "project",
                        templateId = "drawing-board/document.json.peb",
                        outputPath = artifactLayout.projectResourcePath(outputRoot, "drawing_board_$tag.json"),
                        context = mapOf(
                            "drawingBoardTag" to tag,
                            "elements" to elements.stablyOrdered(),
                        ),
                        conflictPolicy = ConflictPolicy.OVERWRITE,
                    ),
                )
            }
        }
    }

    private companion object {
        val supportedTags = listOf(
            "command",
            "query",
            "client",
            "api_payload",
            "domain_event",
            "integration_event",
            "domain_service",
            "saga",
        )
    }
}

private fun List<Any>.stablyOrdered(): List<Any> =
    when {
        all { element -> element is DesignBlockModel } -> filterIsInstance<DesignBlockModel>()
            .map { block ->
                block.copy(
                    artifacts = block.artifacts.sortedWith(ArtifactComparator),
                    fields = block.fields.sortedWith(FieldComparator),
                    resultFields = block.resultFields.sortedWith(FieldComparator),
                )
            }
        all { element -> element is DrawingBoardElementModel } -> filterIsInstance<DrawingBoardElementModel>()
            .map { element ->
                element.copy(
                    artifacts = element.artifacts.sortedWith(ArtifactComparator),
                    requestFields = element.requestFields.sortedWith(DrawingBoardFieldComparator),
                    responseFields = element.responseFields.sortedWith(DrawingBoardFieldComparator),
                )
            }
        else -> this
    }

private val ArtifactComparator =
    compareBy<com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel> { it.family }
        .thenBy { it.variant }

private val FieldComparator =
    compareBy<com.only4.cap4k.plugin.pipeline.api.FieldModel> { it.name }
        .thenBy { it.type }
        .thenBy { it.nullable }
        .thenBy { it.defaultValue.orEmpty() }

private val DrawingBoardFieldComparator =
    compareBy<com.only4.cap4k.plugin.pipeline.api.DrawingBoardFieldModel> { it.name }
        .thenBy { it.type }
        .thenBy { it.nullable }
        .thenBy { it.defaultValue.orEmpty() }
