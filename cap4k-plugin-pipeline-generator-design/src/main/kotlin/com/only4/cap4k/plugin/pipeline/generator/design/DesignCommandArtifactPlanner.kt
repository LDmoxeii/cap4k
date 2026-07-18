package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignCommandArtifactPlanner : GeneratorProvider {
    override val id: String = "command"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val blocks = model.designBlocks.filter { block -> block.selects(id) }
        if (blocks.isEmpty()) {
            return emptyList()
        }

        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return blocks.map { block ->
            val typeName = block.commandTypeName()
            val siblingTypeNames = model.designInteractionSiblingTypeNames(
                packageName = block.packageName,
                currentTypeName = typeName,
            )
            val packageName = artifactLayout.designCommandPackage(block.packageName)

            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = "design/command.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, typeName),
                context = DesignPayloadRenderModelFactory.createForCommandBlock(
                    packageName = packageName,
                    block = block,
                    symbolRegistry = config.designSymbolRegistry(model),
                    siblingTypeNames = siblingTypeNames,
                ).toContextMap() + mapOf("buildingBlock" to block.buildingBlockContext(id)),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
