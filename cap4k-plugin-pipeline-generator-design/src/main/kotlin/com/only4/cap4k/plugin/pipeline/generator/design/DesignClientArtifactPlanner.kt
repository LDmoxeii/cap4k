package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignClientArtifactPlanner : GeneratorProvider {
    override val id: String = "design-client"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return model.clients.map { client ->
            val siblingTypeNames = model.designInteractionSiblingTypeNames(
                packageName = client.packageName,
                currentTypeName = client.typeName,
            )
            val packageName = artifactLayout.designClientPackage(client.packageName)

            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = "design/client.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, client.typeName),
                context = DesignPayloadRenderModelFactory.create(
                    packageName = packageName,
                    interaction = client,
                    typeRegistry = config.typeRegistryFqns(),
                    siblingTypeNames = siblingTypeNames,
                ).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
