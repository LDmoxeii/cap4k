package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateFetchType
import com.only4.cap4k.plugin.pipeline.api.AggregateInverseRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationCardinality
import java.util.Locale

internal object AggregateInverseRelationInference {
    private data class EntityKey(
        val packageName: String,
        val entityName: String,
    )

    private data class DerivedFieldKey(
        val owner: EntityKey,
        val fieldName: String,
    )

    fun infer(
        entities: List<EntityModel>,
        relations: List<AggregateRelationModel>,
        tables: List<DbTableSnapshot>,
    ): List<AggregateInverseRelationModel> {
        val entityByKey = entities.associateBy { EntityKey(it.packageName, it.name) }
        val scalarFieldsByEntity = entities.associate { entity ->
            EntityKey(entity.packageName, entity.name) to entity.fields.map { it.name }.toSet()
        }
        val ownerRelationFieldNamesByEntity = relations
            .groupBy { EntityKey(it.ownerEntityPackageName, it.ownerEntityName) }
            .mapValues { (_, entityRelations) -> entityRelations.map { it.fieldName }.toSet() }
        val ownedOneSingleAccessorNamesByEntity = relations
            .asSequence()
            .filter { it.owned && it.ownedCardinality == OwnedRelationCardinality.ONE }
            .mapNotNull { relation ->
                relation.singleAccessorName?.let { accessorName ->
                    EntityKey(relation.ownerEntityPackageName, relation.ownerEntityName) to accessorName
                }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, accessors) -> accessors.toSet() }
        val derivedFieldKeys = mutableSetOf<DerivedFieldKey>()

        return relations
            .asSequence()
            .filter { it.relationType == AggregateRelationType.ONE_TO_MANY }
            .mapNotNull { relation ->
                val childKey = EntityKey(relation.targetEntityPackageName, relation.targetEntityName)
                val childEntity = entityByKey[childKey] ?: return@mapNotNull null

                val fieldName = lowerFirst(relation.ownerEntityName)
                require(fieldName !in ownerRelationFieldNamesByEntity[childKey].orEmpty()) {
                    "aggregate inverse relation field collides with owner relation field: ${childEntity.packageName}.${childEntity.name}.$fieldName"
                }
                require(fieldName !in scalarFieldsByEntity.getValue(childKey)) {
                    "aggregate inverse relation field collides with scalar field: ${childEntity.packageName}.${childEntity.name}.$fieldName"
                }
                require(fieldName !in ownedOneSingleAccessorNamesByEntity[childKey].orEmpty()) {
                    "aggregate inverse relation field collides with owned one single accessor: ${childEntity.packageName}.${childEntity.name}.$fieldName"
                }
                require(derivedFieldKeys.add(DerivedFieldKey(childKey, fieldName))) {
                    "aggregate inverse relation field collision: ${childEntity.packageName}.${childEntity.name}.$fieldName"
                }

                AggregateInverseRelationModel(
                    ownerEntityName = childEntity.name,
                    ownerEntityPackageName = childEntity.packageName,
                    fieldName = fieldName,
                    targetEntityName = relation.ownerEntityName,
                    targetEntityPackageName = relation.ownerEntityPackageName,
                    relationType = AggregateRelationType.MANY_TO_ONE,
                    joinColumn = relation.joinColumn,
                    fetchType = AggregateFetchType.LAZY,
                    nullable = false,
                    insertable = false,
                    updatable = false,
                )
            }
            .toList()
    }

    private fun lowerFirst(value: String): String =
        if (value.isEmpty()) value else value.substring(0, 1).lowercase(Locale.ROOT) + value.substring(1)
}
