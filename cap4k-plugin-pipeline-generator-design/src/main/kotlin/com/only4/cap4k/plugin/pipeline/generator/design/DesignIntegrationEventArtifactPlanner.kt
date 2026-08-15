package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityDescriptor

class DesignIntegrationEventArtifactPlanner : GeneratorProvider {
    override val id: String = "integration-event"
    override val descriptor: PipelineCapabilityDescriptor = designDescriptor(
        providerId = id,
        displayName = "Integration Event Generator",
        tacticalCarrier = "Integration Event",
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
            val variant = block.integrationEventVariant()
            val typeName = block.integrationEventTypeName()
            val packageName = artifactLayout.designIntegrationEventPackage(variant = variant, designPackage = block.packageName)
            val renderModel = DesignPayloadRenderModelFactory.createForIntegrationEventBlock(
                packageName = packageName,
                block = block,
            )
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "contract",
                templateId = "design/integration_event.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(contractRoot, packageName, typeName),
                context = renderModel.toIntegrationEventContextMap(block, variant),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
