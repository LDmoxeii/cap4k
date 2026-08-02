package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.*

class DesignApiPayloadArtifactPlanner : GeneratorProvider {
    override val id: String = "api-payload"
    override val descriptor: PipelineCapabilityDescriptor = designDescriptor(
        providerId = id,
        displayName = "API Payload Generator",
        tacticalCarrier = "API Payload",
        boundaries = standardDesignBoundaries(),
    )

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val blocks = model.designBlocks.filter { block -> block.selects(id) }
        if (blocks.isEmpty()) {
            return emptyList()
        }

        val adapterRoot = requireRelativeModuleRoot(config, "adapter")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return blocks.map { block ->
            val typeName = block.apiPayloadTypeName()
            val packageName = artifactLayout.designApiPayloadPackage(block.packageName)
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "adapter",
                templateId = "design/api_payload.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(adapterRoot, packageName, typeName),
                context = DesignPayloadRenderModelFactory.createForApiPayloadBlock(
                    packageName = packageName,
                    block = block,
                    pageRequest = block.pageVariantSelected(id),
                ).toContextMap() + mapOf("buildingBlock" to block.buildingBlockContext(id)),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}

internal fun designDescriptor(
    providerId: String,
    displayName: String,
    tacticalCarrier: String,
    boundaries: List<PipelineCapabilityBoundary>,
): PipelineCapabilityDescriptor = PipelineCapabilityDescriptor.builtIn(
    providerId = providerId,
    displayName = displayName,
    kind = PipelineCapabilityKind.GENERATOR,
    module = "cap4k-plugin-pipeline-generator-design",
    activation = PipelineCapabilityActivation.INPUT_DRIVEN,
    tacticalCarriers = listOf(tacticalCarrier),
    executionLanes = listOf(PipelineExecutionLane.AUTHORING),
    tasks = listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.GENERATE),
    inputRequirements = listOf(
        PipelineInputRequirement(
            id = "design-json-input",
            capabilityIds = listOf("pipeline.source.design-json"),
        ),
    ),
    outputKinds = listOf(ArtifactOutputKind.CHECKED_IN_SOURCE),
    boundaries = boundaries,
)

internal fun standardDesignBoundaries(): List<PipelineCapabilityBoundary> = listOf(
    PipelineCapabilityBoundary(PipelineBoundaryKind.GENERATION, PipelineBoundaryAuthorities.PIPELINE_GENERATOR),
    PipelineCapabilityBoundary(PipelineBoundaryKind.HANDWRITTEN, PipelineBoundaryAuthorities.PROJECT_HANDWRITTEN),
)

internal fun runtimeDesignBoundaries(providerOwned: Boolean = false): List<PipelineCapabilityBoundary> =
    buildList {
        addAll(standardDesignBoundaries())
        add(PipelineCapabilityBoundary(PipelineBoundaryKind.RUNTIME, PipelineBoundaryAuthorities.CAP4K_RUNTIME))
        if (providerOwned) {
            add(PipelineCapabilityBoundary(PipelineBoundaryKind.PROVIDER, PipelineBoundaryAuthorities.RUNTIME_PROVIDER))
        }
    }
