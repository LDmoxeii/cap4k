package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignValidatorArtifactPlanner : GeneratorProvider {
    override val id: String = "design-validator"

    /**
     * Temporary inert compatibility shim.
     *
     * The canonical validator slice was removed before the full Task 9 validator cleanup.
     * Keep this provider non-generating so older service-loader entries compile without
     * silently producing validator artifacts or reintroducing validator support.
     */
    override fun plan(
        config: ProjectConfig,
        model: CanonicalModel,
    ): List<com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem> = emptyList()
}
