package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class LocalEnumArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val planning = AggregateEnumPlanning.from(model, artifactLayout, config.typeRegistry)
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
                    "items" to local.field.enumItems.map { item ->
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

private data class LocalEnumCandidateKey(
    val ownerPackageName: String,
    val typeBinding: String,
)

private data class LocalEnumCandidate(
    val ownerPackageName: String,
    val field: FieldModel,
)
