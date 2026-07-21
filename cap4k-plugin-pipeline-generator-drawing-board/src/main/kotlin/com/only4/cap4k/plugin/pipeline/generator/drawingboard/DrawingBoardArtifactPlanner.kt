package com.only4.cap4k.plugin.pipeline.generator.drawingboard

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardElementModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DrawingBoardArtifactPlanner : GeneratorProvider {
    override val id: String = "drawing-board"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val elementsByTag = model.drawingBoard?.elementsByTag ?: return emptyList()

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
        all { element -> element is DrawingBoardElementModel } -> filterIsInstance<DrawingBoardElementModel>()
            .map { element ->
                element.copy(
                    artifacts = element.artifacts.sortedWith(ArtifactComparator),
                    fields = element.fields.sortedWith(DrawingBoardFieldComparator),
                    resultFields = element.resultFields.sortedWith(DrawingBoardFieldComparator),
                )
            }
        else -> this
    }

private val ArtifactComparator =
    compareBy<com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel> { it.family }
        .thenBy { it.variant }

private val DrawingBoardFieldComparator =
    compareBy<com.only4.cap4k.plugin.pipeline.api.DrawingBoardFieldModel> { it.name }
        .thenBy { it.type }
        .thenBy { it.nullable }
        .thenBy { it.defaultValue.orEmpty() }
