package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.RequestKind

class DesignClientArtifactPlanner : GeneratorProvider {
    override val id: String = "design-client"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val basePath = config.basePackage.replace(".", "/")
        val plannedEntries = model.requests.withIndex()
            .filter { entry -> entry.value.kind == RequestKind.CLIENT }

        return plannedEntries.map { entry ->
            val request = entry.value
            val siblingRequestTypeNames = plannedEntries
                .asSequence()
                .filter { it.index != entry.index && it.value.packageName == request.packageName }
                .map { it.value.typeName }
                .toSet()
            val packagePath = request.packageName.replace(".", "/")

            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = "design/client.kt.peb",
                outputPath = "$applicationRoot/src/main/kotlin/$basePath/application/distributed/clients/$packagePath/${request.typeName}.kt",
                context = DesignRenderModelFactory.create(
                    packageName = "${config.basePackage}.application.distributed.clients.${request.packageName}",
                    request = request,
                    typeRegistry = config.typeRegistry,
                    siblingRequestTypeNames = siblingRequestTypeNames,
                ).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
