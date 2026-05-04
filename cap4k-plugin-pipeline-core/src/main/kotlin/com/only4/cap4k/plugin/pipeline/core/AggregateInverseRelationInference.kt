package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateFetchType
import com.only4.cap4k.plugin.pipeline.api.AggregateInverseRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import java.util.Locale

internal object AggregateInverseRelationInference {
    private data class EntityKey(
        val packageName: String,
        val entityName: String,
    )

    private data class RelationKey(
        val owner: EntityKey,
        val target: EntityKey,
        val joinColumnKey: String,
        val relationType: AggregateRelationType,
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
        val explicitOwnerRelations = explicitOwnerRelationKeys(
            entities = entities,
            tables = tables,
        )
        val derivedFieldKeys = mutableSetOf<DerivedFieldKey>()

        return relations
            .asSequence()
            .filter { it.relationType == AggregateRelationType.ONE_TO_MANY }
            .mapNotNull { relation ->
                val childKey = EntityKey(relation.targetEntityPackageName, relation.targetEntityName)
                val childEntity = entityByKey[childKey] ?: return@mapNotNull null
                val parentKey = EntityKey(relation.ownerEntityPackageName, relation.ownerEntityName)

                if (explicitOwnerRelations.contains(
                        RelationKey(
                            owner = childKey,
                            target = parentKey,
                            joinColumnKey = normalizedJoinColumnKey(relation.joinColumn),
                            relationType = AggregateRelationType.MANY_TO_ONE,
                        )
                    ) || explicitOwnerRelations.contains(
                        RelationKey(
                            owner = childKey,
                            target = parentKey,
                            joinColumnKey = normalizedJoinColumnKey(relation.joinColumn),
                            relationType = AggregateRelationType.ONE_TO_ONE,
                        )
                    )
                ) {
                    return@mapNotNull null
                }

                val fieldName = lowerFirst(relation.ownerEntityName)
                require(fieldName !in ownerRelationFieldNamesByEntity[childKey].orEmpty()) {
                    "aggregate inverse relation field collides with owner relation field: ${childEntity.packageName}.${childEntity.name}.$fieldName"
                }
                require(fieldName !in scalarFieldsByEntity.getValue(childKey)) {
                    "aggregate inverse relation field collides with scalar field: ${childEntity.packageName}.${childEntity.name}.$fieldName"
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

    private fun explicitOwnerRelationKeys(
        entities: List<EntityModel>,
        tables: List<DbTableSnapshot>,
    ): Set<RelationKey> {
        val entityByTableName = entities.associateBy { tableKey(it.tableName) }
        return tables.flatMap { ownerTable ->
            val ownerEntity = entityByTableName[tableKey(ownerTable.tableName)] ?: return@flatMap emptyList()
            ownerTable.columns.mapNotNull { column ->
                val referenceTable = column.referenceTable ?: return@mapNotNull null
                if (isOwnedDirectParentReference(ownerTable, referenceTable)) {
                    return@mapNotNull null
                }
                val relationType = resolveExplicitOwnerRelationType(column.explicitRelationType) ?: AggregateRelationType.MANY_TO_ONE
                val targetEntity = entityByTableName[tableKey(referenceTable)] ?: return@mapNotNull null
                RelationKey(
                    owner = EntityKey(ownerEntity.packageName, ownerEntity.name),
                    target = EntityKey(targetEntity.packageName, targetEntity.name),
                    joinColumnKey = normalizedJoinColumnKey(column.name),
                    relationType = relationType,
                )
            }
        }.toSet()
    }

    private fun resolveExplicitOwnerRelationType(explicitRelationType: String?): AggregateRelationType? {
        return when (explicitRelationType?.uppercase(Locale.ROOT)) {
            "MANY_TO_ONE" -> AggregateRelationType.MANY_TO_ONE
            "ONE_TO_ONE" -> AggregateRelationType.ONE_TO_ONE
            else -> null
        }
    }

    private fun isOwnedDirectParentReference(
        ownerTable: DbTableSnapshot,
        referenceTable: String,
    ): Boolean {
        val parentTable = ownerTable.parentTable ?: return false
        return referenceTable.equals(parentTable, ignoreCase = true)
    }

    private fun tableKey(tableName: String): String = tableName.lowercase(Locale.ROOT)

    private fun normalizedJoinColumnKey(joinColumn: String): String = joinColumn.lowercase(Locale.ROOT)

    private fun lowerFirst(value: String): String =
        if (value.isEmpty()) value else value.substring(0, 1).lowercase(Locale.ROOT) + value.substring(1)
}
