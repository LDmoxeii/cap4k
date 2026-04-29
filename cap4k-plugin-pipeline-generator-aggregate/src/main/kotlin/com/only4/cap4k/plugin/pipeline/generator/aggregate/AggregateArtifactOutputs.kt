package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal fun generatedKotlinArtifact(
    config: ProjectConfig,
    artifactLayout: ArtifactLayoutResolver,
    moduleRole: String,
    packageName: String,
    typeName: String,
    templateId: String,
    context: Map<String, Any?>,
): ArtifactPlanItem {
    val moduleRoot = requireRelativeModule(config, moduleRole)
    return ArtifactPlanItem(
        generatorId = "aggregate",
        moduleRole = moduleRole,
        templateId = templateId,
        outputPath = artifactLayout.generatedKotlinSourcePath(moduleRoot, packageName, typeName),
        context = context,
        conflictPolicy = ConflictPolicy.OVERWRITE,
        outputKind = ArtifactOutputKind.GENERATED_SOURCE,
        resolvedOutputRoot = artifactLayout.generatedKotlinSourceRoot(moduleRoot),
    )
}

internal fun checkedInKotlinArtifact(
    config: ProjectConfig,
    artifactLayout: ArtifactLayoutResolver,
    moduleRole: String,
    packageName: String,
    typeName: String,
    templateId: String,
    context: Map<String, Any?>,
    conflictPolicy: ConflictPolicy = config.templates.conflictPolicy,
): ArtifactPlanItem {
    val moduleRoot = requireRelativeModule(config, moduleRole)
    return ArtifactPlanItem(
        generatorId = "aggregate",
        moduleRole = moduleRole,
        templateId = templateId,
        outputPath = artifactLayout.kotlinSourcePath(moduleRoot, packageName, typeName),
        context = context,
        conflictPolicy = conflictPolicy,
        outputKind = ArtifactOutputKind.CHECKED_IN_SOURCE,
        resolvedOutputRoot = artifactLayout.kotlinSourceRoot(moduleRoot),
    )
}
