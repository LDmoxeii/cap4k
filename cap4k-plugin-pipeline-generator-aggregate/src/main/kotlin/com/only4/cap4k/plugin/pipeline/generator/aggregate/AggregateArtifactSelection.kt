package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal data class AggregateArtifactSelection(
    val factoryEnabled: Boolean,
    val specificationEnabled: Boolean,
    val wrapperEnabled: Boolean,
    val uniqueEnabled: Boolean,
    val enumTranslationEnabled: Boolean,
) {
    companion object {
        fun from(config: ProjectConfig): AggregateArtifactSelection {
            val options = config.generators["aggregate"]?.options.orEmpty()
            return AggregateArtifactSelection(
                factoryEnabled = options["artifact.factory"] as? Boolean ?: false,
                specificationEnabled = options["artifact.specification"] as? Boolean ?: false,
                wrapperEnabled = options["artifact.wrapper"] as? Boolean ?: false,
                uniqueEnabled = options["artifact.unique"] as? Boolean ?: false,
                enumTranslationEnabled = options["artifact.enumTranslation"] as? Boolean ?: false,
            )
        }
    }
}
