package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.RequestKind

class DesignClientHandlerArtifactPlanner : GeneratorProvider {
    override val id: String = "design-client-handler"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val adapterRoot = requireRelativeModuleRoot(config, "adapter")
        val basePath = config.basePackage.replace(".", "/")

        return model.requests
            .asSequence()
            .filter { it.kind == RequestKind.CLIENT }
            .map { request ->
                val packagePath = request.packageName.replace(".", "/")

                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "adapter",
                    templateId = "design/client_handler.kt.peb",
                    outputPath = "$adapterRoot/src/main/kotlin/$basePath/adapter/application/distributed/clients/$packagePath/${request.typeName}Handler.kt",
                    context = DesignClientHandlerRenderModelFactory.create(
                        basePackage = config.basePackage,
                        request = request,
                    ).toContextMap(),
                    conflictPolicy = config.templates.conflictPolicy,
                )
            }
            .toList()
    }
}
