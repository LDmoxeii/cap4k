package com.only4.cap4k.plugin.pipeline.generator.drawingboard

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import java.nio.file.InvalidPathException
import java.nio.file.Path

class DrawingBoardArtifactPlanner : GeneratorProvider {
    override val id: String = "drawing-board"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val drawingBoard = model.drawingBoard
            ?: throw IllegalArgumentException(
                "drawing-board generator requires at least one parsed design-elements.json input.",
            )

        val outputDir = requireRelativeOutputDir(config)

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
                        outputPath = "$outputDir/drawing_board_$tag.json",
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

    private fun requireRelativeOutputDir(config: ProjectConfig): String {
        val rawValue = config.generators[id]
            ?.options
            ?.get("outputDir")
            ?.toString()
            ?.trim()
            .orEmpty()

        if (rawValue.isBlank()) {
            return "design"
        }

        val path = try {
            Path.of(rawValue)
        } catch (ex: InvalidPathException) {
            throw invalidOutputDir(rawValue, ex)
        }

        if (path.isAbsolute || path.root != null || path.any { it.toString() == ".." }) {
            throw invalidOutputDir(rawValue)
        }

        val normalized = path.normalize()
            .toString()
            .replace('\\', '/')
            .trimEnd('/')

        if (normalized.isBlank()) {
            throw invalidOutputDir(rawValue)
        }

        return normalized
    }

    private fun invalidOutputDir(value: String, cause: Throwable? = null): IllegalArgumentException =
        IllegalArgumentException("drawing-board outputDir must be a valid relative filesystem path: $value", cause)

    private companion object {
        val supportedTags = listOf("cli", "cmd", "qry", "payload", "de")
    }
}
