package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignQueryHandlerArtifactPlanner : GeneratorProvider {
    override val id: String = "query-handler"

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
                val queryTypeName = block.queryTypeName()
                val packageName = artifactLayout.designQueryHandlerPackage(block.packageName)
                val queryType = "${artifactLayout.designQueryPackage(block.packageName)}.$queryTypeName"

                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "adapter",
                    templateId = "design/query_handler.kt.peb",
                    outputPath = artifactLayout.kotlinSourcePath(adapterRoot, packageName, "${queryTypeName}Handler"),
                    context = DesignQueryHandlerRenderModelFactory.create(
                        packageName = packageName,
                        queryType = queryType,
                        block = block,
                    ).toContextMap() + mapOf("buildingBlock" to block.buildingBlockContext(id)),
                    conflictPolicy = config.templates.conflictPolicy,
                )
            }
            .toList()
    }
}
