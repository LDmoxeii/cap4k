package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityDescriptor
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignEndpointArtifactPlanner : GeneratorProvider {
    override val id: String = "endpoint"
    override val descriptor: PipelineCapabilityDescriptor = designDescriptor(
        providerId = id,
        displayName = "Endpoint Generator",
        tacticalCarrier = "Endpoint",
        boundaries = runtimeDesignBoundaries(providerOwned = true),
    )

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val blocks = model.designBlocks.filter { block -> block.selects(id) }
        if (blocks.isEmpty()) {
            return emptyList()
        }

        val contractRoot = requireRelativeModuleRoot(config, "contract")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return blocks.map { block ->
            val typeName = block.endpointTypeName()
            val packageName = artifactLayout.designEndpointPackage(block.packageName)
            val renderModel = DesignPayloadRenderModelFactory.createForEndpointBlock(
                packageName = packageName,
                block = block,
            )

            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "contract",
                templateId = "design/endpoint.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(contractRoot, packageName, typeName),
                context = renderModel.toContextMap() + mapOf(
                    "buildingBlock" to block.buildingBlockContext(id),
                    "operationName" to block.operationName,
                    "operationNameKotlinStringLiteral" to block.operationName.toKotlinStringLiteral(),
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
