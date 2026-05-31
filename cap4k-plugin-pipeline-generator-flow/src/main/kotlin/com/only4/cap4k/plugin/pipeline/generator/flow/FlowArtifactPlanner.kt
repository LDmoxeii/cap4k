package com.only4.cap4k.plugin.pipeline.generator.flow

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class FlowArtifactPlanner : GeneratorProvider {
    override val id: String = "flow"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val graph = model.analysisGraph ?: return emptyList()
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val outputRoot = artifactLayout.flowOutputRoot()
        val plannedFlows = buildPlannedFlows(graph)

        val entryArtifacts = plannedFlows.entries.flatMap { flow ->
            listOf(
                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "project",
                    templateId = "flow/entry.json.peb",
                    outputPath = artifactLayout.projectResourcePath(outputRoot, "${flow.slug}.json"),
                    context = mapOf("jsonContent" to flow.jsonContent),
                    conflictPolicy = ConflictPolicy.OVERWRITE,
                ),
                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "project",
                    templateId = "flow/entry.mmd.peb",
                    outputPath = artifactLayout.projectResourcePath(outputRoot, "${flow.slug}.mmd"),
                    context = mapOf("mermaidText" to flow.mermaidText),
                    conflictPolicy = ConflictPolicy.OVERWRITE,
                ),
            )
        }

        return entryArtifacts + ArtifactPlanItem(
            generatorId = id,
            moduleRole = "project",
            templateId = "flow/index.json.peb",
            outputPath = artifactLayout.projectResourcePath(outputRoot, "index.json"),
            context = mapOf("jsonContent" to plannedFlows.indexJsonContent),
            conflictPolicy = ConflictPolicy.OVERWRITE,
        )
    }
}
