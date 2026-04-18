package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.AggregateRelationModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel

internal object AggregateRelationPlanning {
    fun relationFieldsFor(entity: EntityModel, relations: List<AggregateRelationModel>): List<Map<String, Any?>> =
        relations
            .filter { it.ownerEntityName == entity.name && it.ownerEntityPackageName == entity.packageName }
            .map { relation ->
                mapOf(
                    "name" to relation.fieldName,
                    "targetType" to relation.targetEntityName,
                    "targetPackageName" to relation.targetEntityPackageName,
                    "relationType" to relation.relationType.name,
                    "fetchType" to relation.fetchType.name,
                    "joinColumn" to relation.joinColumn,
                )
            }
}
