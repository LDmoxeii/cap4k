package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignCommandArtifactPlanner : GeneratorProvider {
    override val id: String = "design-command"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val basePath = config.basePackage.replace(".", "/")

        return model.commands.mapIndexed { index, command ->
            val siblingTypeNames = model.commands
                .asSequence()
                .filterIndexed { siblingIndex, sibling ->
                    siblingIndex != index && sibling.packageName == command.packageName
                }
                .map { it.typeName }
                .toSet()
            val packagePath = command.packageName.replace(".", "/")

            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = "design/command.kt.peb",
                outputPath = "$applicationRoot/src/main/kotlin/$basePath/application/commands/$packagePath/${command.typeName}.kt",
                context = DesignPayloadRenderModelFactory.create(
                    packageName = "${config.basePackage}.application.commands.${command.packageName}",
                    interaction = command,
                    typeRegistry = config.typeRegistry,
                    siblingTypeNames = siblingTypeNames,
                ).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
