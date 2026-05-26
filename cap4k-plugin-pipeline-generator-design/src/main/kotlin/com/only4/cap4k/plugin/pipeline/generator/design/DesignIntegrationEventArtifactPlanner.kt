package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignIntegrationEventArtifactPlanner : GeneratorProvider {
    override val id: String = "design-integration-event"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        if (model.integrationEvents.isEmpty()) {
            return emptyList()
        }

        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return model.integrationEvents.map { event ->
            val packageName = artifactLayout.designIntegrationEventPackage(event.role, event.packageName)
            val renderModel = DesignPayloadRenderModelFactory.createForIntegrationEvent(
                packageName = packageName,
                event = event,
                typeRegistry = config.designTypeRegistryFqns(model),
            )
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = "design/integration_event.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, event.typeName),
                context = renderModel.toIntegrationEventContextMap(event),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
