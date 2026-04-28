package com.only4.cap4k.plugin.pipeline.generator.drawingboard

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DrawingBoardArtifactPlanner : GeneratorProvider {
    override val id: String = "drawing-board"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val drawingBoard = model.drawingBoard
            ?: throw IllegalArgumentException(
                "drawing-board generator requires at least one parsed design-elements.json input.",
            )

        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val outputRoot = artifactLayout.drawingBoardOutputRoot()

        return supportedTags.flatMap { tag ->
            val elements = drawingBoard.elementsByTag[tag].orEmpty()
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
                            "elements" to elements,
                        ),
                        conflictPolicy = config.templates.conflictPolicy,
                    ),
                )
            }
        }
    }

    private companion object {
        val supportedTags = listOf("command", "query", "client", "api_payload", "domain_event", "validator")
    }
}
