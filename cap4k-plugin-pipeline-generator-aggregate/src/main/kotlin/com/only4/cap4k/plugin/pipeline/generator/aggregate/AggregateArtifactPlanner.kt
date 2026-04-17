package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class AggregateArtifactPlanner : GeneratorProvider {
    override val id: String = "aggregate"

    private val delegates: List<AggregateArtifactFamilyPlanner> = listOf(
        SchemaArtifactPlanner(),
        EntityArtifactPlanner(),
        RepositoryArtifactPlanner(),
        FactoryArtifactPlanner(),
        SpecificationArtifactPlanner(),
    )

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> =
        delegates.flatMap { it.plan(config, model) }
}

internal interface AggregateArtifactFamilyPlanner {
    fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem>
}
