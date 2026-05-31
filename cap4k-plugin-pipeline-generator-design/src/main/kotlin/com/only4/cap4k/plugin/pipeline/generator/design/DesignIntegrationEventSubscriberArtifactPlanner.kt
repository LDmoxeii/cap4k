package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignIntegrationEventSubscriberArtifactPlanner : GeneratorProvider {
    override val id: String = "integration-subscriber"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val blocks = model.designBlocks.filter { block -> block.selects(id) }
        if (blocks.isEmpty()) {
            return emptyList()
        }

        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return blocks
            .map { block ->
                val variant = block.integrationEventVariant()
                require(variant == "inbound") {
                    "integration_event ${block.name} integration-subscriber requires integration-event:inbound."
                }
                val eventTypeName = block.integrationEventTypeName()
                val packageName = artifactLayout.designIntegrationEventSubscriberPackage()
                val eventType = "${artifactLayout.designIntegrationEventPackage(variant = variant, designPackage = block.packageName)}.$eventTypeName"
                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "application",
                    templateId = "design/integration_event_subscriber.kt.peb",
                    outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, "${eventTypeName}Subscriber"),
                    context = DesignIntegrationEventSubscriberRenderModelFactory.create(
                        subscriberPackageName = packageName,
                        eventType = eventType,
                        block = block,
                        variant = variant,
                    ).toContextMap() + mapOf("buildingBlock" to block.buildingBlockContext(id)),
                    conflictPolicy = config.templates.conflictPolicy,
                )
            }
    }
}
