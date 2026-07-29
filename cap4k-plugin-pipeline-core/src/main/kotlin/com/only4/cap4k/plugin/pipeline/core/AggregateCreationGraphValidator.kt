package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateRelationModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel

internal object AggregateCreationGraphValidator {
    fun validate(
        entities: List<EntityModel>,
        relations: List<AggregateRelationModel>,
    ) {
        val entitiesByFqn = entities.associateBy { "${it.packageName}.${it.name}" }
        val ownedRelations = relations.filter { it.owned }
        val targetOwnerRelations = ownedRelations.groupBy { "${it.targetEntityPackageName}.${it.targetEntityName}" }
        targetOwnerRelations.entries.firstOrNull { (_, owners) ->
            owners.map { "${it.ownerEntityPackageName}.${it.ownerEntityName}" }.distinct().size > 1
        }?.let { (target, owners) ->
            throw IllegalArgumentException(
                "owned entity $target has conflicting owners: " +
                    owners.joinToString { "${it.ownerEntityPackageName}.${it.ownerEntityName}.${it.fieldName}" },
            )
        }
        ownedRelations.forEach { relation ->
            require(entitiesByFqn.containsKey("${relation.ownerEntityPackageName}.${relation.ownerEntityName}")) {
                "owned relation ${relation.ownerEntityName}.${relation.fieldName} has unresolved owner entity"
            }
            require(entitiesByFqn.containsKey("${relation.targetEntityPackageName}.${relation.targetEntityName}")) {
                "owned relation ${relation.ownerEntityName}.${relation.fieldName} has unresolved target entity " +
                    "${relation.targetEntityPackageName}.${relation.targetEntityName}"
            }
            require(relation.ownedCardinality != null) {
                "owned relation ${relation.ownerEntityName}.${relation.fieldName} has unresolved cardinality"
            }
        }

        val relationsByOwner = ownedRelations.groupBy { "${it.ownerEntityPackageName}.${it.ownerEntityName}" }
        val visiting = linkedSetOf<String>()
        val visited = mutableSetOf<String>()

        fun visit(entityFqn: String, path: List<String>) {
            if (!visiting.add(entityFqn)) {
                throw IllegalArgumentException(
                    "owned relation cycle detected: ${(path + entityFqn).joinToString(" -> ")}",
                )
            }
            relationsByOwner[entityFqn].orEmpty().forEach { relation ->
                val targetFqn = "${relation.targetEntityPackageName}.${relation.targetEntityName}"
                val relationPath = path + "${relation.ownerEntityName}.${relation.fieldName}"
                if (targetFqn in visiting) {
                    throw IllegalArgumentException(
                        "owned relation cycle detected: ${(relationPath + targetFqn).joinToString(" -> ")}",
                    )
                }
                if (targetFqn !in visited) visit(targetFqn, relationPath)
            }
            visiting.remove(entityFqn)
            visited += entityFqn
        }

        entitiesByFqn.keys.sorted().forEach { entityFqn ->
            if (entityFqn !in visited) visit(entityFqn, emptyList())
        }
    }
}
