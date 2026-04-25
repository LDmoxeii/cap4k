package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignDomainEventHandlerArtifactPlanner : GeneratorProvider {
    override val id: String = "design-domain-event-handler"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return model.domainEvents.map { event ->
            val packageName = artifactLayout.designDomainEventHandlerPackage(event.packageName)
            val domainEventType = "${artifactLayout.designDomainEventPackage(event.packageName)}.${event.typeName}"
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = "design/domain_event_handler.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, "${event.typeName}Subscriber"),
                context = DesignDomainEventHandlerRenderModelFactory.create(
                    eventHandlerPackageName = packageName,
                    domainEventType = domainEventType,
                    event = event,
                ).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
