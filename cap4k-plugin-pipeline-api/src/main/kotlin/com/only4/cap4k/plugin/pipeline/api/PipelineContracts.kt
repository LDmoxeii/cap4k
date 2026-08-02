package com.only4.cap4k.plugin.pipeline.api

interface SourceProvider {
    val id: String

    val descriptor: PipelineCapabilityDescriptor
        get() = PipelineCapabilityDescriptor.identityOnly(id, PipelineCapabilityKind.SOURCE)

    fun localInputPaths(config: ProjectConfig): List<String> = emptyList()

    fun collect(config: ProjectConfig): SourceSnapshot
}

interface GeneratorProvider {
    val id: String

    val descriptor: PipelineCapabilityDescriptor
        get() = PipelineCapabilityDescriptor.identityOnly(
            id,
            PipelineCapabilityKind.GENERATOR,
            PipelineCapabilityActivation.INPUT_DRIVEN,
        )

    fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem>
}

const val PIPELINE_EXTENSION_SPI_VERSION: Int = 1

/**
 * Pipeline Extension discovery is used by read-only project inspection as well as
 * planning. Implementations must keep construction plus [descriptor] and
 * [contributions] access deterministic and side-effect free: no network, database,
 * process, filesystem mutation, or other external live-source I/O is permitted.
 * A contribution may perform work only when its explicit pipeline operation runs.
 */
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

    val descriptor: PipelineCapabilityDescriptor
        get() = PipelineCapabilityDescriptor.identityOnly(
            id,
            PipelineCapabilityKind.ARTIFACT_ADDON,
            PipelineCapabilityActivation.INSTALLED,
        )

    fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem>
}

interface PipelineRunner {
    fun run(config: ProjectConfig): PipelineResult
}
