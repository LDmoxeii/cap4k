package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignValidatorArtifactPlanner : GeneratorProvider {
    override val id: String = "design-validator"

    override fun plan(config: ProjectConfig, model: CanonicalModel) = emptyList<com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem>()
}
