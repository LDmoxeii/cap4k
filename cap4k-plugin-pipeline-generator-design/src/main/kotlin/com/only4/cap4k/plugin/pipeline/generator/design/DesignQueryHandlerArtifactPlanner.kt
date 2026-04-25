package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignQueryHandlerArtifactPlanner : GeneratorProvider {
    override val id: String = "design-query-handler"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val adapterRoot = requireRelativeModuleRoot(config, "adapter")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return model.queries
            .asSequence()
            .map { query ->
                val packageName = artifactLayout.designQueryHandlerPackage(query.packageName)
                val queryType = "${artifactLayout.designQueryPackage(query.packageName)}.${query.typeName}"

                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "adapter",
                    templateId = query.variant.handlerTemplateId,
                    outputPath = artifactLayout.kotlinSourcePath(adapterRoot, packageName, "${query.typeName}Handler"),
                    context = DesignQueryHandlerRenderModelFactory.create(
                        packageName = packageName,
                        queryType = queryType,
                        query = query,
                    ).toContextMap(),
                    conflictPolicy = config.templates.conflictPolicy,
                )
            }
            .toList()
    }
}
