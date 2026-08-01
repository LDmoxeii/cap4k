package com.only4.cap4k.plugin.pipeline.api

interface SourceProvider {
    val id: String

    fun collect(config: ProjectConfig): SourceSnapshot
}

interface GeneratorProvider {
    val id: String

    fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem>
}

const val PIPELINE_EXTENSION_SPI_VERSION: Int = 1

interface PipelineExtensionProvider {
    val descriptor: PipelineExtensionDescriptor
    val contributions: List<PipelineContribution>
}

data class PipelineExtensionDescriptor(
    val id: String,
    val spiVersion: Int,
    val displayName: String = id,
)

interface PipelineContribution

data class PipelineContributionBinding<out T : PipelineContribution>(
    val extensionId: String,
    val contribution: T,
)

interface ArtifactAddonProvider : PipelineContribution {
    val id: String

    fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem>
}

interface PipelineRunner {
    fun run(config: ProjectConfig): PipelineResult
}
