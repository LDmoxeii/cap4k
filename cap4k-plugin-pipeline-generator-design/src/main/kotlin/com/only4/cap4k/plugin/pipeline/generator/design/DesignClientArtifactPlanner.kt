package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignClientArtifactPlanner : GeneratorProvider {
    override val id: String = "design-client"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val basePath = config.basePackage.replace(".", "/")

        return model.clients.map { client ->
            val siblingTypeNames = model.designInteractionSiblingTypeNames(
                packageName = client.packageName,
                currentTypeName = client.typeName,
            )
            val packagePath = client.packageName.replace(".", "/")

            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = "design/client.kt.peb",
                outputPath = "$applicationRoot/src/main/kotlin/$basePath/application/distributed/clients/$packagePath/${client.typeName}.kt",
                context = DesignPayloadRenderModelFactory.create(
                    packageName = "${config.basePackage}.application.distributed.clients.${client.packageName}",
                    interaction = client,
                    typeRegistry = config.typeRegistry,
                    siblingTypeNames = siblingTypeNames,
                ).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
