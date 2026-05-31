package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignDomainServiceArtifactPlanner : GeneratorProvider {
    override val id: String = "domain-service"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val blocks = model.designBlocks.filter { block -> block.selects(id) }
        if (blocks.isEmpty()) {
            return emptyList()
        }

        val domainRoot = requireRelativeModuleRoot(config, "domain")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return blocks.map { block ->
            val packageName = artifactLayout.designDomainServicePackage(block.packageName)
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "domain",
                templateId = config.artifactLayout.designDomainService.id,
                outputPath = artifactLayout.kotlinSourcePath(domainRoot, packageName, block.name),
                context = DesignDomainServiceRenderModelFactory.create(
                    packageName = packageName,
                    block = block,
                ).toContextMap() + mapOf("buildingBlock" to block.buildingBlockContext(id)),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
