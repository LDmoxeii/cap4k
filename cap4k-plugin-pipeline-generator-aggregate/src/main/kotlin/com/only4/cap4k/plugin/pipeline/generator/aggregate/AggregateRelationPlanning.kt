package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.AggregateInverseRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.EntityModel

internal data class AggregateRelationRenderPlan(
    val relationFields: List<Map<String, Any?>>,
    val imports: List<String>,
    val jpaImports: List<String>,
)

internal object AggregateRelationPlanning {
    private val supportedRelationTypes = setOf(
        AggregateRelationType.MANY_TO_ONE,
        AggregateRelationType.ONE_TO_ONE,
        AggregateRelationType.ONE_TO_MANY,
    )

    fun planFor(
        entity: EntityModel,
        relations: List<AggregateRelationModel>,
        inverseRelations: List<AggregateInverseRelationModel>,
    ): AggregateRelationRenderPlan {
        val entityRelations = relations
            .filter { it.ownerEntityName == entity.name && it.ownerEntityPackageName == entity.packageName }
        val entityInverseRelations = inverseRelations
            .filter { it.ownerEntityName == entity.name && it.ownerEntityPackageName == entity.packageName }
        require(entityRelations.all { it.relationType in supportedRelationTypes }) {
            "Unsupported aggregate relation type for ${entity.packageName}.${entity.name}"
        }
        require(entityInverseRelations.all { it.relationType in supportedRelationTypes }) {
            "Unsupported aggregate relation type for ${entity.packageName}.${entity.name}"
        }
        val allEntityRelations = entityRelations.map { it.targetEntityName to it.targetEntityPackageName } +
            entityInverseRelations.map { it.targetEntityName to it.targetEntityPackageName }
        val targetPackagesByType = allEntityRelations
            .groupBy { it.first }
            .mapValues { (_, matchingRelations) -> matchingRelations.map { it.second }.distinct() }

        val ownerRelationFields = entityRelations.map { relation ->
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
                "nullable" to relation.nullable,
                "cascadeTypes" to relation.cascadeTypes.map { it.name },
                "orphanRemoval" to relation.orphanRemoval,
                "joinColumnNullable" to relation.joinColumnNullable,
            )
        }
        val inverseRelationFields = entityInverseRelations.map { relation ->
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
                "nullable" to relation.nullable,
                "readOnly" to true,
                "insertable" to relation.insertable,
                "updatable" to relation.updatable,
                "cascadeTypes" to emptyList<String>(),
                "orphanRemoval" to false,
                "joinColumnNullable" to null,
            )
        }
        val relationFields = ownerRelationFields + inverseRelationFields
        val imports = (entityRelations.map {
            it.targetEntityName to it.targetEntityPackageName
        } + entityInverseRelations.map {
            it.targetEntityName to it.targetEntityPackageName
        })
            .mapNotNull { relation ->
                val targetEntityName = relation.first
                val targetEntityPackageName = relation.second
                val targetPackages = targetPackagesByType.getValue(targetEntityName)
                if (targetEntityPackageName != entity.packageName && targetPackages.size == 1) {
                    "$targetEntityPackageName.$targetEntityName"
                } else {
                    null
                }
            }
            .distinct()
        val relationTypes = (entityRelations.map { it.relationType } + entityInverseRelations.map { it.relationType }).toSet()
        val hasCascadeTypes = entityRelations.any { it.cascadeTypes.isNotEmpty() }
        val jpaImports = buildList {
            if (relationTypes.isNotEmpty()) {
                add("jakarta.persistence.FetchType")
                add("jakarta.persistence.JoinColumn")
                if (hasCascadeTypes) {
                    add("jakarta.persistence.CascadeType")
                }
            }
            if (AggregateRelationType.MANY_TO_ONE in relationTypes) {
                add("jakarta.persistence.ManyToOne")
            }
            if (AggregateRelationType.ONE_TO_ONE in relationTypes) {
                add("jakarta.persistence.OneToOne")
            }
            if (AggregateRelationType.ONE_TO_MANY in relationTypes) {
                add("jakarta.persistence.OneToMany")
            }
        }

        return AggregateRelationRenderPlan(
            relationFields = relationFields,
            imports = imports,
            jpaImports = jpaImports,
        )
    }
}
