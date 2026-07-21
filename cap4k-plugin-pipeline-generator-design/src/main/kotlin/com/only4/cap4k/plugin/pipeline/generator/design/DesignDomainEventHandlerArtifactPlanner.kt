package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignDomainEventHandlerArtifactPlanner : GeneratorProvider {
    override val id: String = "domain-subscriber"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val blocks = model.designBlocks.filter { block -> block.selects(id) }
        if (blocks.isEmpty()) {
            return emptyList()
        }

        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return blocks.map { block ->
            val packageKey = block.domainEventPackageKey(config, model)
            val eventTypeName = block.domainEventTypeName()
            val packageName = artifactLayout.designDomainEventHandlerPackage(packageKey)
            val domainEventType = "${artifactLayout.designDomainEventPackage(packageKey)}.$eventTypeName"
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = "design/domain_event_handler.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, "${eventTypeName}Subscriber"),
                context = DesignDomainEventHandlerRenderModelFactory.create(
                    eventHandlerPackageName = packageName,
                    domainEventType = domainEventType,
                    block = block,
                ).toContextMap() + mapOf("buildingBlock" to block.buildingBlockContext(id)),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
