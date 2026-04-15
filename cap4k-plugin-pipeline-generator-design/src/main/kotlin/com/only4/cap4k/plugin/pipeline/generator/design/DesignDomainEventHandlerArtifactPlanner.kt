package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignDomainEventHandlerArtifactPlanner : GeneratorProvider {
    override val id: String = "design-domain-event-handler"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val basePath = config.basePackage.replace(".", "/")

        return model.domainEvents.map { event ->
            val packagePath = event.packageName.replace(".", "/")
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = "design/domain_event_handler.kt.peb",
                outputPath = "$applicationRoot/src/main/kotlin/$basePath/application/$packagePath/events/${event.typeName}Subscriber.kt",
                context = DesignDomainEventHandlerRenderModelFactory.create(
                    basePackage = config.basePackage,
                    event = event,
                ).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
