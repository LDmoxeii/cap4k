package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignSagaArtifactPlanner : GeneratorProvider {
    override val id: String = "saga"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val blocks = model.designBlocks.filter { block -> block.selects(id) }
        if (blocks.isEmpty()) {
            return emptyList()
        }

        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return blocks.map { block ->
            val packageName = artifactLayout.designSagaPackage(block.packageName)
            val renderModel = DesignSagaRenderModelFactory.create(
                packageName = packageName,
                block = block,
                symbolRegistry = config.designTypeSymbolRegistry(model),
            )
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = config.artifactLayout.designSagaArtifact.id,
                outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, block.name),
                context = renderModel.toContextMap() + mapOf("buildingBlock" to block.buildingBlockContext(id)),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
