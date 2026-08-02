package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.*

class DesignCapabilityArtifactPlanner : GeneratorProvider {
    override val id: String = "capability"
    override val descriptor: PipelineCapabilityDescriptor = designDescriptor(
        providerId = id,
        displayName = "Capability Generator",
        tacticalCarrier = "Capability",
        boundaries = runtimeDesignBoundaries(providerOwned = true),
    )

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val blocks = model.designBlocks.filter { block -> block.selects(id) }
        if (blocks.isEmpty()) {
            return emptyList()
        }

        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return blocks.map { block ->
            val typeName = block.capabilityTypeName()
            val packageName = artifactLayout.designCapabilityPackage(block.packageName)

            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = "design/capability.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, typeName),
                context = DesignPayloadRenderModelFactory.createForCapabilityBlock(
                    packageName = packageName,
                    block = block,
                ).toContextMap() + mapOf("buildingBlock" to block.buildingBlockContext(id)),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
