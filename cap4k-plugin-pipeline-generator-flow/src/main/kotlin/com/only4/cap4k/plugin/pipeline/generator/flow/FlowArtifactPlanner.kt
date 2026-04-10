package com.only4.cap4k.plugin.pipeline.generator.flow

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import java.nio.file.InvalidPathException
import java.nio.file.Path

class FlowArtifactPlanner : GeneratorProvider {
    override val id: String = "flow"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val graph = model.analysisGraph ?: return emptyList()
        val outputDir = requireRelativeOutputDir(config)
        val plannedFlows = buildPlannedFlows(graph)

        val entryArtifacts = plannedFlows.entries.flatMap { flow ->
            listOf(
                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "project",
                    templateId = "flow/entry.json.peb",
                    outputPath = "$outputDir/${flow.slug}.json",
                    context = mapOf("jsonContent" to flow.jsonContent),
                    conflictPolicy = config.templates.conflictPolicy,
                ),
                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "project",
                    templateId = "flow/entry.mmd.peb",
                    outputPath = "$outputDir/${flow.slug}.mmd",
                    context = mapOf("mermaidText" to flow.mermaidText),
                    conflictPolicy = config.templates.conflictPolicy,
                ),
            )
        }

        return entryArtifacts + ArtifactPlanItem(
            generatorId = id,
            moduleRole = "project",
            templateId = "flow/index.json.peb",
            outputPath = "$outputDir/index.json",
            context = mapOf("jsonContent" to plannedFlows.indexJsonContent),
            conflictPolicy = config.templates.conflictPolicy,
        )
    }

    private fun requireRelativeOutputDir(config: ProjectConfig): String {
        val rawValue = config.generators[id]
            ?.options
            ?.get("outputDir")
            ?.toString()
            ?.trim()
            .orEmpty()
            .ifBlank { "flows" }

        val path = try {
            Path.of(rawValue)
        } catch (ex: InvalidPathException) {
            throw invalidOutputDir(rawValue, ex)
        }

        if (path.isAbsolute || path.root != null || path.any { it.toString() == ".." }) {
            throw invalidOutputDir(rawValue)
        }

        return path.normalize()
            .toString()
            .replace('\\', '/')
            .trimEnd('/')
            .ifBlank { "flows" }
    }

    private fun invalidOutputDir(value: String, cause: Throwable? = null): IllegalArgumentException =
        IllegalArgumentException("flow outputDir must be a valid relative filesystem path: $value", cause)
}
