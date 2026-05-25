package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignDomainServiceArtifactPlanner : GeneratorProvider {
    override val id: String = "design-domain-service"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        if (model.domainServices.isEmpty()) {
            return emptyList()
        }

        val domainRoot = requireRelativeModuleRoot(config, "domain")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return model.domainServices.map { service ->
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "domain",
                templateId = config.artifactLayout.designDomainService.id,
                outputPath = artifactLayout.kotlinSourcePath(domainRoot, service.packageName, service.name),
                context = DesignDomainServiceRenderModelFactory.create(service).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
