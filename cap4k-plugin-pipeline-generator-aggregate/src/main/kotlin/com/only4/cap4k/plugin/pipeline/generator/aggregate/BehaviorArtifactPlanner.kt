package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class BehaviorArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        return model.entities
            .filter { it.aggregateRoot }
            .map { root ->
                checkedInKotlinArtifact(
                    config = config,
                    artifactLayout = artifactLayout,
                    moduleRole = "domain",
                    packageName = root.packageName,
                    typeName = "${root.name}Behavior",
                    templateId = "aggregate/behavior.kt.peb",
                    context = mapOf(
                        "packageName" to root.packageName,
                        "rootName" to root.name,
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            }
    }
}
