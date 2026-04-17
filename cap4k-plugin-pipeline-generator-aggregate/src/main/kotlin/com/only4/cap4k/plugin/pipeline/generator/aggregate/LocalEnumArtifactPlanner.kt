package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class LocalEnumArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val planning = AggregateEnumPlanning.from(model, config.typeRegistry)
        val domainRoot = requireRelativeModule(config, "domain")
        val candidates = linkedMapOf<LocalEnumCandidateKey, LocalEnumCandidate>()
        model.entities.forEach { entity ->
            entity.fields.forEach { field ->
                val typeBinding = field.typeBinding?.takeIf { it.isNotBlank() } ?: return@forEach
                if (field.enumItems.isEmpty()) return@forEach
                val key = LocalEnumCandidateKey(entity.packageName, typeBinding)
                candidates.putIfAbsent(key, LocalEnumCandidate(entity.packageName, field))
            }
        }

        return candidates.values.map { local ->
            val enumTypeFqn = planning.resolveFieldType(local.ownerPackageName, local.field)
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
                    "items" to local.field.enumItems.map { item ->
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

private data class LocalEnumCandidateKey(
    val ownerPackageName: String,
    val typeBinding: String,
)

private data class LocalEnumCandidate(
    val ownerPackageName: String,
    val field: FieldModel,
)
