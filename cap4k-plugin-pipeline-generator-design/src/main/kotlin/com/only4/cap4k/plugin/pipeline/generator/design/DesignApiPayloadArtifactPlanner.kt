package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignApiPayloadArtifactPlanner : GeneratorProvider {
    override val id: String = "design-api-payload"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val adapterRoot = requireRelativeModuleRoot(config, "adapter")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return model.apiPayloads.map { payload ->
            val packageName = artifactLayout.designApiPayloadPackage(payload.packageName)
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "adapter",
                templateId = "design/api_payload.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(adapterRoot, packageName, payload.typeName),
                context = DesignPayloadRenderModelFactory.createForApiPayload(
                    packageName = packageName,
                    payload = payload,
                    typeRegistry = config.typeRegistry,
                ).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
