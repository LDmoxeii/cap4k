package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.ManagedFieldRole
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationCardinality
import com.only4.cap4k.plugin.pipeline.api.ResolvedManagedEntityPolicy
import java.util.Locale

internal object OwnedRelationCardinalityInference {
    fun infer(
        binding: OwnedParentBinding,
        managedPolicy: ResolvedManagedEntityPolicy?,
    ): OwnedRelationCardinality {
        val child = binding.childTable
        val parentRefKey = columnKey(binding.parentRefColumn.name)

        val columnsByKey = child.columns.associateBy { columnKey(it.name) }
        val scopeColumnKeys = managedPolicy?.fields.orEmpty()
            .filter { it.role == ManagedFieldRole.SCOPE }
            .mapTo(mutableSetOf()) { columnKey(it.columnName) }
        val deletedColumnKeys = managedPolicy?.fields.orEmpty()
            .filter { it.role == ManagedFieldRole.SOFT_DELETE }
            .mapTo(mutableSetOf()) { columnKey(it.columnName) }
        val neutralColumnKeys = buildSet {
            add(parentRefKey)
            addAll(scopeColumnKeys)
            addAll(deletedColumnKeys)
        }

        val hasOneProvingUniqueConstraint = child.uniqueConstraints.any { constraint ->
            val constraintColumnKeys = constraint.columns.mapTo(linkedSetOf(), ::columnKey)
            constraint.complete &&
                constraint.filterCondition.isNullOrBlank() &&
                parentRefKey in constraintColumnKeys &&
                constraintColumnKeys.minus(neutralColumnKeys).isEmpty() &&
                constraintColumnKeys
                    .filter { it in scopeColumnKeys || it in deletedColumnKeys }
                    .all { columnKey -> columnsByKey[columnKey]?.nullable == false }
        }

        return if (hasOneProvingUniqueConstraint) {
            OwnedRelationCardinality.ONE
        } else {
            OwnedRelationCardinality.MANY
        }
    }

    private fun columnKey(columnName: String): String = columnName.lowercase(Locale.ROOT)
}
