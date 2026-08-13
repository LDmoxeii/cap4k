package com.only4.cap4k.plugin.pipeline.generator.flow

import com.only4.cap4k.plugin.pipeline.api.*

class FlowArtifactPlanner : GeneratorProvider {
    override val id: String = "flow"
    override val descriptor: PipelineCapabilityDescriptor = PipelineCapabilityDescriptor.builtIn(
        providerId = id,
        displayName = "Flow Generator",
        kind = PipelineCapabilityKind.GENERATOR,
        module = "cap4k-plugin-pipeline-generator-flow",
        activation = PipelineCapabilityActivation.EXPLICIT_CONFIGURATION,
        tacticalCarriers = listOf(
            "Input: Raw Analysis Graph Evidence",
            "Entry-centered Causal Flow Evidence",
            "Visible: Entry, Command, Domain Event, Integration Event",
            "Hidden: Command Handler, Domain Event Handler, Integration Event Handler, Entity Method",
            "Projection: Hidden Path Contraction, Root After Projection, Cycle Preservation",
        ),
        executionLanes = listOf(PipelineExecutionLane.ANALYSIS),
        tasks = listOf(PipelinePublicTasks.ANALYSIS_PLAN, PipelinePublicTasks.ANALYSIS_GENERATE),
        inputRequirements = listOf(
            PipelineInputRequirement(
                id = "flow-analysis",
                capabilityIds = listOf("pipeline.source.ir-analysis"),
            ),
        ),
        outputKinds = listOf(ArtifactOutputKind.OUTPUT_ARTIFACT),
        boundaries = listOf(
            PipelineCapabilityBoundary(PipelineBoundaryKind.GENERATION, PipelineBoundaryAuthorities.PIPELINE_GENERATOR),
            PipelineCapabilityBoundary(PipelineBoundaryKind.ANALYZER, PipelineBoundaryAuthorities.ANALYZER_OBSERVATION),
        ),
    )

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        requireFlowAnalysisMetadata(model)
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
                    outputKind = ArtifactOutputKind.OUTPUT_ARTIFACT,
                    resolvedOutputRoot = outputRoot,
                ),
                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "project",
                    templateId = "flow/entry.mmd.peb",
                    outputPath = artifactLayout.projectResourcePath(outputRoot, "${flow.slug}.mmd"),
                    context = mapOf("mermaidText" to flow.mermaidText),
                    conflictPolicy = ConflictPolicy.OVERWRITE,
                    outputKind = ArtifactOutputKind.OUTPUT_ARTIFACT,
                    resolvedOutputRoot = outputRoot,
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
            outputKind = ArtifactOutputKind.OUTPUT_ARTIFACT,
            resolvedOutputRoot = outputRoot,
        )
    }
}

private fun requireFlowAnalysisMetadata(model: CanonicalModel) {
    val missing = model.analysisGraph?.nodes.orEmpty()
        .filter { node -> AGGREGATE_ELEMENT_METADATA_FQ in node.missingMetadata }
        .groupBy { node -> node.metadataOwner ?: node.fullName }
    if (missing.isEmpty()) {
        return
    }
    val details = missing.keys.sorted().joinToString(separator = System.lineSeparator()) { symbol ->
        "- symbol: $symbol; missing metadata: $AGGREGATE_ELEMENT_METADATA_FQ; affected capability: Flow Analysis"
    }
    throw IllegalArgumentException(
        buildString {
            appendLine("Cap4k analysis metadata contract violation.")
            appendLine(details)
            append(
                "Recovery: restore the default ddd-default generator template for each symbol, or add the listed " +
                    "metadata annotation and keep io.github.ldmoxeii:cap4k-analysis-metadata on the owning business " +
                    "module compileOnly classpath. Custom templates that omit analysis metadata explicitly opt out " +
                    "of Flow Analysis; Cap4k will not emit an apparently complete partial result."
            )
        }
    )
}

private const val AGGREGATE_ELEMENT_METADATA_FQ =
    "com.only4.cap4k.analysis.metadata.AggregateElementMetadata"
