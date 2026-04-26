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
        AggregateWrapperArtifactPlanner(),
        UniqueQueryArtifactPlanner(),
        UniqueQueryHandlerArtifactPlanner(),
        UniqueValidatorArtifactPlanner(),
        SharedEnumArtifactPlanner(),
        LocalEnumArtifactPlanner(),
        EnumTranslationArtifactPlanner(),
    )

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val selection = AggregateArtifactSelection.from(config)
        return delegates.flatMap { delegate ->
            when (delegate) {
                is FactoryArtifactPlanner ->
                    if (selection.factoryEnabled) delegate.plan(config, model) else emptyList()
                is SpecificationArtifactPlanner ->
                    if (selection.specificationEnabled) delegate.plan(config, model) else emptyList()
                is AggregateWrapperArtifactPlanner ->
                    if (selection.wrapperEnabled) delegate.plan(config, model) else emptyList()
                is UniqueQueryArtifactPlanner,
                is UniqueQueryHandlerArtifactPlanner,
                is UniqueValidatorArtifactPlanner ->
                    if (selection.uniqueEnabled) delegate.plan(config, model) else emptyList()
                is EnumTranslationArtifactPlanner ->
                    if (selection.enumTranslationEnabled) delegate.plan(config, model) else emptyList()
                else -> delegate.plan(config, model)
            }
        }
    }
}

internal interface AggregateArtifactFamilyPlanner {
    fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem>
}
