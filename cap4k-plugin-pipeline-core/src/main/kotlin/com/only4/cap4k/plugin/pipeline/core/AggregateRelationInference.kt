package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateFetchType
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot

internal object AggregateRelationInference {
    private data class Endpoint(
        val entityName: String,
        val packageName: String,
    )

    fun fromTables(basePackage: String, tables: List<DbTableSnapshot>): List<AggregateRelationModel> {
        val entityLookup = tables.associateBy(
            keySelector = { it.tableName.lowercase() },
            valueTransform = { table ->
                Endpoint(
                    entityName = AggregateNaming.entityName(table.tableName),
                    packageName = "${basePackage}.domain.aggregates.${AggregateNaming.tableSegment(table.tableName)}",
                )
            }
        )

        val parentChildRelations = tables
            .filter { it.parentTable != null }
            .map { child ->
                val parentTable = requireNotNull(child.parentTable)
                val parent = requireNotNull(entityLookup[parentTable.lowercase()]) {
                    "unknown parent table: ${child.parentTable}"
                }
                val target = requireNotNull(entityLookup[child.tableName.lowercase()]) {
                    "unknown child table: ${child.tableName}"
                }
                val joinColumn = requireNotNull(
                    child.columns.firstOrNull { it.referenceTable?.equals(parentTable, ignoreCase = true) == true }?.name
                ) {
                    "missing parent reference column for table: ${child.tableName}"
                }
                AggregateRelationModel(
                    ownerEntityName = parent.entityName,
                    ownerEntityPackageName = parent.packageName,
                    fieldName = parentChildFieldName(parent.entityName, target.entityName),
                    targetEntityName = target.entityName,
                    targetEntityPackageName = target.packageName,
                    relationType = AggregateRelationType.ONE_TO_MANY,
                    joinColumn = joinColumn,
                    fetchType = AggregateFetchType.LAZY,
                )
            }

        val explicitRelations = tables.flatMap { table ->
            val owner = requireNotNull(entityLookup[table.tableName.lowercase()]) {
                "unknown owner table: ${table.tableName}"
            }
            table.columns
                .filter { column ->
                    val referenceTable = column.referenceTable
                    if (referenceTable == null) {
                        return@filter false
                    }
                    !(table.parentTable != null && referenceTable.equals(table.parentTable, ignoreCase = true))
                }
                .map { column ->
                    val relationType = resolveRelationType(column.explicitRelationType)
                    val referenceTable = requireNotNull(column.referenceTable)
                    val target = requireNotNull(entityLookup[referenceTable.lowercase()]) {
                        "unknown reference table: ${column.referenceTable}"
                    }
                    AggregateRelationModel(
                        ownerEntityName = owner.entityName,
                        ownerEntityPackageName = owner.packageName,
                        fieldName = relationFieldName(column.name, target.entityName),
                        targetEntityName = target.entityName,
                        targetEntityPackageName = target.packageName,
                        relationType = relationType,
                        joinColumn = column.name,
                        fetchType = if (column.lazy == true) AggregateFetchType.LAZY else AggregateFetchType.EAGER,
                    )
                }
        }

        return (parentChildRelations + explicitRelations)
            .distinctBy { listOf(it.ownerEntityName, it.fieldName, it.targetEntityName, it.relationType) }
    }

    private fun resolveRelationType(explicitRelationType: String?): AggregateRelationType {
        return when (explicitRelationType) {
            "ONE_TO_ONE" -> AggregateRelationType.ONE_TO_ONE
            null, "MANY_TO_ONE" -> AggregateRelationType.MANY_TO_ONE
            else -> throw IllegalArgumentException("unsupported aggregate relation type in first slice: $explicitRelationType")
        }
    }

    private fun parentChildFieldName(parentEntityName: String, childEntityName: String): String {
        val stem = childEntityName.removePrefix(parentEntityName).ifBlank { childEntityName }
        return pluralize(stem)
    }

    private fun relationFieldName(columnName: String, targetEntityName: String): String {
        val stem = columnName.removeSuffix("_id").removeSuffix("_ID")
        return if (stem.isNotBlank()) {
            stem.split("_")
                .joinToString("") { part ->
                    part.lowercase().replaceFirstChar { it.titlecase() }
                }
                .replaceFirstChar { it.lowercase() }
        } else {
            targetEntityName.replaceFirstChar { it.lowercase() }
        }
    }

    private fun pluralize(typeName: String): String = typeName.replaceFirstChar { it.lowercase() } + "s"
}
