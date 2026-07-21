package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignClientHandlerArtifactPlanner : GeneratorProvider {
    override val id: String = "client-handler"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val blocks = model.designBlocks.filter { block -> block.selects(id) }
        if (blocks.isEmpty()) {
            return emptyList()
        }

        val adapterRoot = requireRelativeModuleRoot(config, "adapter")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return blocks
            .asSequence()
            .map { block ->
                val clientTypeName = block.clientTypeName()
                val packageName = artifactLayout.designClientHandlerPackage(block.packageName)
                val clientType = "${artifactLayout.designClientPackage(block.packageName)}.$clientTypeName"

                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "adapter",
                    templateId = "design/client_handler.kt.peb",
                    outputPath = artifactLayout.kotlinSourcePath(adapterRoot, packageName, "${clientTypeName}Handler"),
                    context = DesignClientHandlerRenderModelFactory.create(
                        packageName = packageName,
                        clientType = clientType,
                        block = block,
                    ).toContextMap() + mapOf(
                        "resultFields" to block.resultFields
                            .asSequence()
                            .filterNot { it.name.contains('.') }
                            .map { field -> mapOf("name" to field.name) }
                            .toList(),
                        "buildingBlock" to block.buildingBlockContext(id),
                    ),
                    conflictPolicy = config.templates.conflictPolicy,
                )
            }
            .toList()
    }
}
