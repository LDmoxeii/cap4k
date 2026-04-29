package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class SharedEnumArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val planning = AggregateEnumPlanning.from(model, artifactLayout, config.typeRegistry)
        return model.sharedEnums.map { shared ->
            val enumTypeFqn = planning.resolveFieldType(shared.typeName, emptyList())
            val packageName = enumTypeFqn.substringBeforeLast('.', missingDelimiterValue = "")
            val typeName = enumTypeFqn.substringAfterLast('.')

            generatedKotlinArtifact(
                config = config,
                artifactLayout = artifactLayout,
                moduleRole = "domain",
                templateId = "aggregate/enum.kt.peb",
                packageName = packageName,
                typeName = typeName,
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
            )
        }
    }
}
