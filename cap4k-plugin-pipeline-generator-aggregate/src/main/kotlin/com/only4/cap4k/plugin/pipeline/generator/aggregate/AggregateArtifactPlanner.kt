package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.PipelineBoundaryAuthorities
import com.only4.cap4k.plugin.pipeline.api.PipelineBoundaryKind
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityBoundary
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityDescriptor
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityKind
import com.only4.cap4k.plugin.pipeline.api.PipelineExecutionLane
import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirement
import com.only4.cap4k.plugin.pipeline.api.PipelineInputSafety
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class AggregateArtifactPlanner : GeneratorProvider {
    override val id: String = "aggregate"
    override val descriptor: PipelineCapabilityDescriptor = PipelineCapabilityDescriptor.builtIn(
        providerId = id,
        displayName = "Aggregate Generator",
        kind = PipelineCapabilityKind.GENERATOR,
        module = "cap4k-plugin-pipeline-generator-aggregate",
        tacticalCarriers = listOf("Aggregate", "Entity", "Strong ID", "Factory", "Repository"),
        executionLanes = listOf(PipelineExecutionLane.AUTHORING, PipelineExecutionLane.GENERATED_SOURCE),
        tasks = listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.GENERATE, PipelinePublicTasks.GENERATE_SOURCES),
        inputRequirements = listOf(
            PipelineInputRequirement(
                id = "aggregate-schema",
                capabilityIds = listOf("pipeline.source.db"),
                safety = PipelineInputSafety.LIVE_EXTERNAL,
            ),
        ),
        outputKinds = listOf(ArtifactOutputKind.CHECKED_IN_SOURCE, ArtifactOutputKind.GENERATED_SOURCE),
        boundaries = listOf(
            PipelineCapabilityBoundary(PipelineBoundaryKind.GENERATION, PipelineBoundaryAuthorities.PIPELINE_GENERATOR),
            PipelineCapabilityBoundary(PipelineBoundaryKind.HANDWRITTEN, PipelineBoundaryAuthorities.PROJECT_HANDWRITTEN),
            PipelineCapabilityBoundary(PipelineBoundaryKind.RUNTIME, PipelineBoundaryAuthorities.CAP4K_RUNTIME),
        ),
    )

    private val delegates: List<AggregateArtifactFamilyPlanner> = listOf(
        SchemaArtifactPlanner(),
        EntityArtifactPlanner(),
        BehaviorArtifactPlanner(),
        RepositoryArtifactPlanner(),
        CreationValueArtifactPlanner(),
        FactoryArtifactPlanner(),
        StrongIdArtifactPlanner(),
        ManagedFieldCatalogArtifactPlanner(),
        LocalEnumArtifactPlanner(),
    )

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> =
        delegates.flatMap { delegate -> delegate.plan(config, model) }
}

internal interface AggregateArtifactFamilyPlanner {
    fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem>
}
