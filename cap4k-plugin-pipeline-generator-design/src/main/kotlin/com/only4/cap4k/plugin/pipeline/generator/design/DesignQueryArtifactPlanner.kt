package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignQueryArtifactPlanner : GeneratorProvider {
    override val id: String = "design-query"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return model.queries.map { query ->
            val siblingTypeNames = model.designInteractionSiblingTypeNames(
                packageName = query.packageName,
                currentTypeName = query.typeName,
            )
            val packageName = artifactLayout.designQueryPackage(query.packageName)

            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = "design/query.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, query.typeName),
                context = DesignPayloadRenderModelFactory.create(
                    packageName = packageName,
                    interaction = query,
                    typeRegistry = config.typeRegistryFqns(),
                    siblingTypeNames = siblingTypeNames,
                ).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
