package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignCommandArtifactPlanner : GeneratorProvider {
    override val id: String = "design-command"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return model.commands.map { command ->
            val siblingTypeNames = model.designInteractionSiblingTypeNames(
                packageName = command.packageName,
                currentTypeName = command.typeName,
            )
            val packageName = artifactLayout.designCommandPackage(command.packageName)

            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = "design/command.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, command.typeName),
                context = DesignPayloadRenderModelFactory.create(
                    packageName = packageName,
                    interaction = command,
                    typeRegistry = config.typeRegistryFqns(),
                    siblingTypeNames = siblingTypeNames,
                ).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
