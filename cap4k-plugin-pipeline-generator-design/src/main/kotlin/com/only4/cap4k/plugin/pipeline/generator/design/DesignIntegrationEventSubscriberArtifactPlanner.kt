package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.IntegrationEventRole
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignIntegrationEventSubscriberArtifactPlanner : GeneratorProvider {
    override val id: String = "design-integration-event-subscriber"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return model.integrationEvents
            .filter { event -> event.role == IntegrationEventRole.INBOUND }
            .map { event ->
                val packageName = artifactLayout.designIntegrationEventSubscriberPackage(event.role, event.packageName)
                val eventType = "${artifactLayout.designIntegrationEventPackage(event.role, event.packageName)}.${event.typeName}"
                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "application",
                    templateId = "design/integration_event_subscriber.kt.peb",
                    outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, "${event.typeName}Subscriber"),
                    context = DesignIntegrationEventSubscriberRenderModelFactory.create(
                        subscriberPackageName = packageName,
                        eventType = eventType,
                        event = event,
                    ).toContextMap(),
                    conflictPolicy = config.templates.conflictPolicy,
                )
            }
    }
}
