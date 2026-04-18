package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.AggregateRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.EntityModel

internal data class AggregateRelationRenderPlan(
    val relationFields: List<Map<String, Any?>>,
    val imports: List<String>,
)

internal object AggregateRelationPlanning {
    private val supportedRelationTypes = setOf(
        AggregateRelationType.MANY_TO_ONE,
        AggregateRelationType.ONE_TO_ONE,
        AggregateRelationType.ONE_TO_MANY,
    )

    fun planFor(entity: EntityModel, relations: List<AggregateRelationModel>): AggregateRelationRenderPlan {
        val entityRelations = relations
            .filter { it.ownerEntityName == entity.name && it.ownerEntityPackageName == entity.packageName }
        require(entityRelations.all { it.relationType in supportedRelationTypes }) {
            "Unsupported aggregate relation type for ${entity.packageName}.${entity.name}"
        }
        val targetPackagesByType = entityRelations
            .groupBy { it.targetEntityName }
            .mapValues { (_, matchingRelations) -> matchingRelations.map { it.targetEntityPackageName }.distinct() }

        val relationFields = entityRelations.map { relation ->
            val targetTypeRef = when {
                relation.targetEntityPackageName == entity.packageName -> relation.targetEntityName
                targetPackagesByType.getValue(relation.targetEntityName).size == 1 -> relation.targetEntityName
                else -> "${relation.targetEntityPackageName}.${relation.targetEntityName}"
            }

            mapOf(
                "name" to relation.fieldName,
                "targetType" to relation.targetEntityName,
                "targetTypeRef" to targetTypeRef,
                "targetPackageName" to relation.targetEntityPackageName,
                "relationType" to relation.relationType.name,
                "fetchType" to relation.fetchType.name,
                "joinColumn" to relation.joinColumn,
            )
        }
        val imports = entityRelations
            .mapNotNull { relation ->
                val targetPackages = targetPackagesByType.getValue(relation.targetEntityName)
                if (relation.targetEntityPackageName != entity.packageName && targetPackages.size == 1) {
                    "${relation.targetEntityPackageName}.${relation.targetEntityName}"
                } else {
                    null
                }
            }
            .distinct()

        return AggregateRelationRenderPlan(
            relationFields = relationFields,
            imports = imports,
        )
    }
}
