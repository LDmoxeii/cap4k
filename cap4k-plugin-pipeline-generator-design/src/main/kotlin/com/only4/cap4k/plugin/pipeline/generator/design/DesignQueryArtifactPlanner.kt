package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignQueryArtifactPlanner : GeneratorProvider {
    override val id: String = "design-query"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val basePath = config.basePackage.replace(".", "/")

        return model.queries.mapIndexed { index, query ->
            val siblingTypeNames = model.queries
                .asSequence()
                .filterIndexed { siblingIndex, sibling ->
                    siblingIndex != index && sibling.packageName == query.packageName
                }
                .map { it.typeName }
                .toSet()
            val packagePath = query.packageName.replace(".", "/")

            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = query.variant.requestTemplateId,
                outputPath = "$applicationRoot/src/main/kotlin/$basePath/application/queries/$packagePath/${query.typeName}.kt",
                context = DesignPayloadRenderModelFactory.create(
                    packageName = "${config.basePackage}.application.queries.${query.packageName}",
                    interaction = query,
                    typeRegistry = config.typeRegistry,
                    siblingTypeNames = siblingTypeNames,
                ).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
