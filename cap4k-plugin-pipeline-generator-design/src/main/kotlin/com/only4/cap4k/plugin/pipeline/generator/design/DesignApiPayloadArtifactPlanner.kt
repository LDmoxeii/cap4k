package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignApiPayloadArtifactPlanner : GeneratorProvider {
    override val id: String = "api-payload"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val blocks = model.designBlocks.filter { block -> block.selects(id) }
        if (blocks.isEmpty()) {
            return emptyList()
        }

        val adapterRoot = requireRelativeModuleRoot(config, "adapter")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return blocks.map { block ->
            val typeName = block.apiPayloadTypeName()
            val packageName = artifactLayout.designApiPayloadPackage(block.packageName)
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "adapter",
                templateId = "design/api_payload.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(adapterRoot, packageName, typeName),
                context = DesignPayloadRenderModelFactory.createForApiPayloadBlock(
                    packageName = packageName,
                    block = block,
                    pageRequest = block.pageVariantSelected(id),
                    symbolRegistry = config.designTypeSymbolRegistry(model),
                ).toContextMap() + mapOf("buildingBlock" to block.buildingBlockContext(id)),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
