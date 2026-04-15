package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignApiPayloadArtifactPlanner : GeneratorProvider {
    override val id: String = "design-api-payload"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val adapterRoot = requireRelativeModuleRoot(config, "adapter")
        val basePath = config.basePackage.replace(".", "/")

        return model.apiPayloads.map { payload ->
            val packagePath = payload.packageName.replace(".", "/")
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "adapter",
                templateId = "design/api_payload.kt.peb",
                outputPath = "$adapterRoot/src/main/kotlin/$basePath/adapter/portal/api/payload/$packagePath/${payload.typeName}.kt",
                context = DesignRenderModelFactory.createForApiPayload(
                    packageName = "${config.basePackage}.adapter.portal.api.payload.${payload.packageName}",
                    payload = payload,
                    typeRegistry = config.typeRegistry,
                ).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
