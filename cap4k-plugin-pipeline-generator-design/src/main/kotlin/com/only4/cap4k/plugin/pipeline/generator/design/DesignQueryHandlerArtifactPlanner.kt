package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignQueryHandlerArtifactPlanner : GeneratorProvider {
    override val id: String = "design-query-handler"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val adapterRoot = requireRelativeModuleRoot(config, "adapter")
        val basePath = config.basePackage.replace(".", "/")

        return model.queries
            .asSequence()
            .map { query ->
                val packagePath = query.packageName.replace(".", "/")

                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "adapter",
                    templateId = query.variant.handlerTemplateId,
                    outputPath = "$adapterRoot/src/main/kotlin/$basePath/adapter/queries/$packagePath/${query.typeName}Handler.kt",
                    context = DesignQueryHandlerRenderModelFactory.create(
                        basePackage = config.basePackage,
                        query = query,
                    ).toContextMap(),
                    conflictPolicy = config.templates.conflictPolicy,
                )
            }
            .toList()
    }
}
