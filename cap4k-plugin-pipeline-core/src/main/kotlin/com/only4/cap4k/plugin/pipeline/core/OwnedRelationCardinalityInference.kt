package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.DbManagedRole
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationCardinality
import java.util.Locale

internal object OwnedRelationCardinalityInference {
    fun infer(binding: OwnedParentBinding): OwnedRelationCardinality {
        val child = binding.childTable
        val parentRefKey = columnKey(binding.parentRefColumn.name)

        if (child.primaryKey.map(::columnKey) == listOf(parentRefKey)) {
            return OwnedRelationCardinality.ONE
        }

        val columnsByKey = child.columns.associateBy { columnKey(it.name) }
        val scopeColumnKeys = child.columns
            .filter { it.managedRole == DbManagedRole.SCOPE }
            .mapTo(mutableSetOf()) { columnKey(it.name) }
        val deletedColumnKeys = child.columns
            .filter { it.managedRole == DbManagedRole.DELETED }
            .mapTo(mutableSetOf()) { columnKey(it.name) }
        val neutralColumnKeys = buildSet {
            add(parentRefKey)
            addAll(scopeColumnKeys)
            addAll(deletedColumnKeys)
        }

        val hasOneProvingUniqueConstraint = child.uniqueConstraints.any { constraint ->
            val constraintColumnKeys = constraint.columns.mapTo(linkedSetOf(), ::columnKey)
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
