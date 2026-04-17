package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class SharedEnumArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val planning = AggregateEnumPlanning.from(model, config.typeRegistry)
        val domainRoot = requireRelativeModule(config, "domain")
        return model.sharedEnums.map { shared ->
            val enumTypeFqn = planning.resolveFieldType(shared.typeName, emptyList())
            val packageName = enumTypeFqn.substringBeforeLast('.', missingDelimiterValue = "")
            val typeName = enumTypeFqn.substringAfterLast('.')
            val outputDir = if (packageName.isBlank()) {
                "$domainRoot/src/main/kotlin"
            } else {
                "$domainRoot/src/main/kotlin/${packageName.replace(".", "/")}"
            }

            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/enum.kt.peb",
                outputPath = "$outputDir/$typeName.kt",
                context = mapOf(
                    "packageName" to packageName,
                    "typeName" to typeName,
                    "items" to shared.items.map { item ->
                        mapOf(
                            "value" to item.value,
                            "name" to item.name,
                            "description" to item.description,
                        )
                    },
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
