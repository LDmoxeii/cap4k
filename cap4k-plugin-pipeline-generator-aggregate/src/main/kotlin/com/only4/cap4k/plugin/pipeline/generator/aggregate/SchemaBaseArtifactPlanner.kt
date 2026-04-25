package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class SchemaBaseArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        if (model.schemas.isEmpty()) {
            return emptyList()
        }

        val domainRoot = requireRelativeModule(config, "domain")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val packageName = artifactLayout.aggregateSchemaBasePackage()

        return listOf(
            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/schema_base.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(domainRoot, packageName, "Schema"),
                context = mapOf(
                    "packageName" to packageName,
                    "typeName" to "Schema",
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        )
    }
}
