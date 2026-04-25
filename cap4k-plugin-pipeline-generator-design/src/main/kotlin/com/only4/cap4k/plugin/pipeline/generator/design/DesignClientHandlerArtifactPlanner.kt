package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignClientHandlerArtifactPlanner : GeneratorProvider {
    override val id: String = "design-client-handler"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val adapterRoot = requireRelativeModuleRoot(config, "adapter")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return model.clients
            .asSequence()
            .map { client ->
                val packageName = artifactLayout.designClientHandlerPackage(client.packageName)
                val clientType = "${artifactLayout.designClientPackage(client.packageName)}.${client.typeName}"

                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "adapter",
                    templateId = "design/client_handler.kt.peb",
                    outputPath = artifactLayout.kotlinSourcePath(adapterRoot, packageName, "${client.typeName}Handler"),
                    context = DesignClientHandlerRenderModelFactory.create(
                        packageName = packageName,
                        clientType = clientType,
                        client = client,
                    ).toContextMap() + mapOf(
                        "responseFields" to client.responseFields
                            .asSequence()
                            .filterNot { it.name.contains('.') }
                            .map { field -> mapOf("name" to field.name) }
                            .toList()
                    ),
                    conflictPolicy = config.templates.conflictPolicy,
                )
            }
            .toList()
    }
}
