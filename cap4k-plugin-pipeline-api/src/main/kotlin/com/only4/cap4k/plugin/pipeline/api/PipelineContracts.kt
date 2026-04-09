package com.only4.cap4k.plugin.pipeline.api

interface SourceProvider {
    val id: String

    fun collect(config: ProjectConfig): SourceSnapshot
}

interface GeneratorProvider {
    val id: String

    fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem>
}

interface PipelineRunner {
    fun run(config: ProjectConfig): PipelineResult
}
