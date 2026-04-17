package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class EnumTranslationArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val planning = AggregateEnumPlanning.from(model, config.typeRegistry)
        val adapterRoot = requireRelativeModule(config, "adapter")
        val sharedTranslations = model.sharedEnums
            .filter { it.generateTranslation }
            .map { shared ->
                val enumTypeFqn = planning.resolveFieldType(shared.typeName, emptyList())
                val enumTypeName = enumTypeFqn.substringAfterLast('.')
                val enumPackageName = enumTypeFqn.substringBeforeLast('.', missingDelimiterValue = "")
                EnumTranslationCandidate(
                    packageName = sharedTranslationPackage(enumPackageName),
                    enumTypeName = enumTypeName,
                    enumTypeFqn = enumTypeFqn,
                    ownerScope = sharedOwnerScope(enumPackageName),
                )
            }

        val localCandidates = linkedMapOf<LocalTranslationKey, LocalTranslationCandidate>()
        model.entities.forEach { entity ->
            entity.fields.forEach { field ->
                val typeBinding = field.typeBinding?.takeIf { it.isNotBlank() } ?: return@forEach
                if (field.enumItems.isEmpty()) return@forEach
                val key = LocalTranslationKey(entity.packageName, typeBinding)
                localCandidates.putIfAbsent(key, LocalTranslationCandidate(entity.packageName, field))
            }
        }

        val localTranslations = localCandidates.values.map { candidate ->
            val enumTypeFqn = planning.resolveFieldType(candidate.ownerPackageName, candidate.field)
            EnumTranslationCandidate(
                packageName = localTranslationPackage(candidate.ownerPackageName),
                enumTypeName = enumTypeFqn.substringAfterLast('.'),
                enumTypeFqn = enumTypeFqn,
                ownerScope = localOwnerScope(candidate.ownerPackageName),
            )
        }

        return (sharedTranslations + localTranslations).map { translation ->
            val typeKey = translationTypeKey(translation.ownerScope, translation.enumTypeName)
            val translationTypeConst = "${typeKey.uppercase()}_CODE_TO_DESC"
            val translationTypeValue = "${typeKey}_code_to_desc"
            val outputDir = if (translation.packageName.isBlank()) {
                "$adapterRoot/src/main/kotlin"
            } else {
                "$adapterRoot/src/main/kotlin/${translation.packageName.replace(".", "/")}"
            }
            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "adapter",
                templateId = "aggregate/enum_translation.kt.peb",
                outputPath = "$outputDir/${translation.enumTypeName}Translation.kt",
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
    val field: FieldModel,
)

private fun translationTypeKey(ownerScope: String, typeName: String): String {
    return "${ownerScope}_$typeName"
        .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
        .replace("-", "_")
        .replace(".", "_")
        .lowercase()
}

private fun sharedOwnerScope(packageName: String): String {
    if (packageName.contains(".shared.enums")) {
        return "shared"
    }
    return packageName.substringAfterLast('.', missingDelimiterValue = "shared")
        .ifBlank { "shared" }
}

private fun sharedTranslationPackage(packageName: String): String {
    val marker = ".domain.shared.enums"
    val markerIndex = packageName.indexOf(marker)
    if (markerIndex >= 0) {
        return packageName.substring(0, markerIndex) + ".domain.translation.shared"
    }
    if (packageName.endsWith(".shared.enums")) {
        return packageName.removeSuffix(".shared.enums") + ".translation.shared"
    }
    return if (packageName.isBlank()) "translation.shared" else "$packageName.translation"
}

private fun localOwnerScope(ownerPackageName: String): String {
    val marker = ".domain.aggregates."
    val markerIndex = ownerPackageName.indexOf(marker)
    if (markerIndex >= 0) {
        return ownerPackageName.substring(markerIndex + marker.length).substringBefore('.')
    }
    return ownerPackageName.substringAfterLast('.', missingDelimiterValue = ownerPackageName)
}

private fun localTranslationPackage(ownerPackageName: String): String {
    val marker = ".domain.aggregates."
    val markerIndex = ownerPackageName.indexOf(marker)
    if (markerIndex >= 0) {
        val basePackage = ownerPackageName.substring(0, markerIndex)
        val aggregateSegment = ownerPackageName.substring(markerIndex + marker.length).substringBefore('.')
        return "$basePackage.domain.translation.$aggregateSegment"
    }
    return if (ownerPackageName.isBlank()) "translation" else "$ownerPackageName.translation"
}
