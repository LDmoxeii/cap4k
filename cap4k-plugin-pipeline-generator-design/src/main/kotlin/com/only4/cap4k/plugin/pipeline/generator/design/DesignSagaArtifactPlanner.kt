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

        return model.sagas.map { saga ->
            val packageName = artifactLayout.designSagaPackage(saga.packageName)
            val renderModel = DesignSagaRenderModelFactory.create(
                packageName = packageName,
                saga = saga,
                typeRegistry = config.designTypeRegistryFqns(model),
            )
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = config.artifactLayout.designSagaArtifact.id,
                outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, saga.name),
                context = renderModel.toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
