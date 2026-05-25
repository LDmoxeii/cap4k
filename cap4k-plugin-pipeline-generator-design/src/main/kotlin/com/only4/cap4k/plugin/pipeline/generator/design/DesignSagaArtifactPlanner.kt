package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignSagaArtifactPlanner : GeneratorProvider {
    override val id: String = "design-saga"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        if (model.sagas.isEmpty()) {
            return emptyList()
        }

        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return model.sagas.flatMap { saga ->
            val renderModel = DesignSagaRenderModelFactory.create(saga)
            listOf(
                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "application",
                    templateId = config.artifactLayout.designSagaParam.id,
                    outputPath = artifactLayout.kotlinSourcePath(applicationRoot, saga.packageName, "${saga.name}Param"),
                    context = renderModel.toContextMap(),
                    conflictPolicy = config.templates.conflictPolicy,
                ),
                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "application",
                    templateId = config.artifactLayout.designSagaResult.id,
                    outputPath = artifactLayout.kotlinSourcePath(applicationRoot, saga.packageName, "${saga.name}Result"),
                    context = renderModel.toContextMap(),
                    conflictPolicy = config.templates.conflictPolicy,
                ),
                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "application",
                    templateId = config.artifactLayout.designSagaHandler.id,
                    outputPath = artifactLayout.kotlinSourcePath(applicationRoot, saga.packageName, "${saga.name}Handler"),
                    context = renderModel.toContextMap(),
                    conflictPolicy = config.templates.conflictPolicy,
                ),
            )
        }
    }
}
