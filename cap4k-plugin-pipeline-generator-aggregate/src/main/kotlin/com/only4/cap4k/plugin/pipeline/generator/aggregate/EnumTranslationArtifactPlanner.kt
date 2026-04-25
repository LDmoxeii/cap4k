package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class EnumTranslationArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val planning = AggregateEnumPlanning.from(model, artifactLayout, config.typeRegistry)
        val adapterRoot = requireRelativeModule(config, "adapter")
        val sharedTranslations = model.sharedEnums
            .filter { it.generateTranslation }
            .map { shared ->
                val enumTypeFqn = planning.resolveFieldType(shared.typeName, emptyList())
                val enumTypeName = enumTypeFqn.substringAfterLast('.')
                EnumTranslationCandidate(
                    packageName = artifactLayout.aggregateEnumTranslationPackage("shared"),
                    enumTypeName = enumTypeName,
                    enumTypeFqn = enumTypeFqn,
                    ownerScope = "",
                )
            }

        val localCandidates = linkedMapOf<LocalTranslationKey, LocalTranslationCandidate>()
        model.entities.forEach { entity ->
            entity.fields.forEach { field ->
                val typeBinding = field.typeBinding?.takeIf { it.isNotBlank() } ?: return@forEach
                if (field.enumItems.isEmpty()) return@forEach
                val key = LocalTranslationKey(entity.packageName, typeBinding)
                localCandidates.putIfAbsent(
                    key,
                    LocalTranslationCandidate(entity.packageName, entity.tableName, field),
                )
            }
        }

        val localTranslations = localCandidates.values.map { candidate ->
            val enumTypeFqn = planning.resolveFieldType(candidate.ownerPackageName, candidate.field)
            val ownerScope = aggregateTableSegment(candidate.ownerTableName)
            EnumTranslationCandidate(
                packageName = artifactLayout.aggregateEnumTranslationPackage(ownerScope),
                enumTypeName = enumTypeFqn.substringAfterLast('.'),
                enumTypeFqn = enumTypeFqn,
                ownerScope = ownerScope,
            )
        }

        return (sharedTranslations + localTranslations).map { translation ->
            val typeKey = translationTypeKey(translation.ownerScope, translation.enumTypeName)
            val translationTypeConst = "${typeKey.uppercase()}_CODE_TO_DESC"
            val translationTypeValue = "${typeKey}_code_to_desc"
            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "adapter",
                templateId = "aggregate/enum_translation.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(
                    adapterRoot,
                    translation.packageName,
                    "${translation.enumTypeName}Translation",
                ),
                context = mapOf(
                    "packageName" to translation.packageName,
                    "typeName" to "${translation.enumTypeName}Translation",
                    "enumTypeName" to translation.enumTypeName,
                    "enumTypeFqn" to translation.enumTypeFqn,
                    "translationTypeConst" to translationTypeConst,
                    "translationTypeValue" to translationTypeValue,
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}

private data class EnumTranslationCandidate(
    val packageName: String,
    val enumTypeName: String,
    val enumTypeFqn: String,
    val ownerScope: String,
)

private data class LocalTranslationKey(
    val ownerPackageName: String,
    val typeBinding: String,
)

private data class LocalTranslationCandidate(
    val ownerPackageName: String,
    val ownerTableName: String,
    val field: FieldModel,
)

private fun translationTypeKey(ownerScope: String, typeName: String): String {
    val scopedTypeName = if (ownerScope.isBlank()) typeName else "${ownerScope}_$typeName"
    return scopedTypeName
        .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
        .replace("-", "_")
        .replace(".", "_")
        .lowercase()
}
