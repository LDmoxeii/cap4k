package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class StrongIdArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        return model.strongIds.map { strongId ->
            val validationKind = when (val strategy = strongId.idStrategy) {
                null -> {
                    require(strongId.valueType in setOf("String", "UUID")) {
                        "unsupported Strong ID backing ${strongId.valueType} for UUID7 ${strongId.packageName}.${strongId.typeName}"
                    }
                    "UUID7"
                }
                else -> {
                    ApplicationIdentifierStrategyContract.requireUuid7(
                        strategy,
                        "${strongId.packageName}.${strongId.typeName}",
                    )
                    require(strongId.valueType in setOf("String", "UUID")) {
                        "unsupported Strong ID backing ${strongId.valueType} for UUID7 ${strongId.packageName}.${strongId.typeName}"
                    }
                    "UUID7"
                }
            }
            generatedKotlinArtifact(
                config = config,
                artifactLayout = artifactLayout,
                moduleRole = "domain",
                templateId = "aggregate/strong_id.kt.peb",
                packageName = strongId.packageName,
                typeName = strongId.typeName,
                context = mapOf(
                    "packageName" to strongId.packageName,
                    "typeName" to strongId.typeName,
                    "aggregateElement" to strongIdAggregateElementContext(strongId),
                    "kind" to strongId.kind.name,
                    "valueType" to strongId.valueType,
                    "validationKind" to validationKind,
                    "stringBacked" to (strongId.valueType == "String"),
                    "uuidBacked" to (strongId.valueType == "UUID"),
                ),
            )
        }
    }
}
