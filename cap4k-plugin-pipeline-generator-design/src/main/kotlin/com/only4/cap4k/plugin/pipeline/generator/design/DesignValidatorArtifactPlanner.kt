package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignValidatorArtifactPlanner : GeneratorProvider {
    override val id: String = "design-validator"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val basePath = config.basePackage.replace(".", "/")

        return model.validators.map { validator ->
            val packagePath = validator.packageName.replace(".", "/")
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = "design/validator.kt.peb",
                outputPath = "$applicationRoot/src/main/kotlin/$basePath/application/validators/$packagePath/${validator.typeName}.kt",
                context = DesignValidatorRenderModelFactory.create(
                    basePackage = config.basePackage,
                    validator = validator,
                ).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
