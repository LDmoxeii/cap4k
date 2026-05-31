package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind

internal class StrongIdArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        return model.strongIds.map { strongId ->
            generatedKotlinArtifact(
                config = config,
                artifactLayout = artifactLayout,
                moduleRole = "domain",
                templateId = "aggregate/strong_id.kt.peb",
                packageName = strongId.packageName,
                typeName = strongId.typeName,
                context = mapOf(
                    "packageName" to strongId.packageName,
                    "typeName" to strongId.typeName,
                    "aggregateElement" to strongIdAggregateElementContext(strongId),
                    "kind" to strongId.kind.name,
                    "canGenerateNew" to (strongId.kind == StrongIdKind.AGGREGATE_ROOT),
                ),
            )
        }
    }
}
