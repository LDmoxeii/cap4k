package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignValidatorArtifactPlanner : GeneratorProvider {
    override val id: String = "design-validator"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return model.validators.map { validator ->
            val packageName = artifactLayout.designValidatorPackage(validator.packageName)
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = "design/validator.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, validator.typeName),
                context = DesignValidatorRenderModelFactory.create(
                    packageName = packageName,
                    validator = validator,
                ).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
