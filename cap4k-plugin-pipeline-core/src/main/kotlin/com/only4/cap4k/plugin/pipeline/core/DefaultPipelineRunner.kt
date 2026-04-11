package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.PipelineResult
import com.only4.cap4k.plugin.pipeline.api.PipelineRunner
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import com.only4.cap4k.plugin.pipeline.renderer.api.ArtifactRenderer

class DefaultPipelineRunner(
    private val sources: List<SourceProvider>,
    private val generators: List<GeneratorProvider>,
    private val assembler: CanonicalAssembler,
    private val renderer: ArtifactRenderer,
    private val exporter: ArtifactExporter,
) : PipelineRunner {
    override fun run(config: ProjectConfig): PipelineResult {
        val snapshots = sources
            .filter { config.sources[it.id]?.enabled == true }
            .map { it.collect(config) }

        val assembly = assembler.assemble(config, snapshots)
        val model = assembly.model

        val planItems = generators
            .filter { config.generators[it.id]?.enabled == true }
            .flatMap { it.plan(config, model) }

        val renderedArtifacts = renderer.render(planItems, config)
        val writtenPaths = exporter.export(renderedArtifacts)

        return PipelineResult(
            planItems = planItems,
            renderedArtifacts = renderedArtifacts,
            writtenPaths = writtenPaths,
            warnings = emptyList(),
            diagnostics = assembly.diagnostics,
        )
    }
}
