package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.*

class DesignCapabilityHandlerArtifactPlanner : GeneratorProvider {
    override val id: String = "capability-handler"
    override val descriptor: PipelineCapabilityDescriptor = designDescriptor(
        providerId = id,
        displayName = "Capability Handler Generator",
        tacticalCarrier = "Capability Handler",
        boundaries = runtimeDesignBoundaries(providerOwned = true),
    )

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
                val capabilityTypeName = block.capabilityTypeName()
                val packageName = artifactLayout.designCapabilityHandlerPackage(block.packageName)
                val capabilityType = "${artifactLayout.designCapabilityPackage(block.packageName)}.$capabilityTypeName"

                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "adapter",
                    templateId = "design/capability_handler.kt.peb",
                    outputPath = artifactLayout.kotlinSourcePath(adapterRoot, packageName, "${capabilityTypeName}Handler"),
                    context = DesignCapabilityHandlerRenderModelFactory.create(
                        packageName = packageName,
                        capabilityType = capabilityType,
                        block = block,
                    ).toContextMap() + mapOf(
                        "resultFields" to block.topLevelResponseFieldNames()
                            .map { fieldName -> mapOf("name" to fieldName) },
                        "buildingBlock" to block.buildingBlockContext(id),
                    ),
                    conflictPolicy = config.templates.conflictPolicy,
                )
            }
            .toList()
    }
}
