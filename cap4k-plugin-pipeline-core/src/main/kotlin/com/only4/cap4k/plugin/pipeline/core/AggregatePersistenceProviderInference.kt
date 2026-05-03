package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceProviderControl
import com.only4.cap4k.plugin.pipeline.api.AggregateSpecialFieldResolvedPolicy
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import java.util.Locale

internal object AggregatePersistenceProviderInference {
    fun infer(
        tables: List<DbTableSnapshot>,
        resolvedPolicies: List<AggregateSpecialFieldResolvedPolicy>,
    ): List<AggregatePersistenceProviderControl> {
        val tableByName = tables.associateBy { it.tableName.lowercase(Locale.ROOT) }

        return resolvedPolicies.mapNotNull { policy ->
            val table = tableByName[policy.tableName.lowercase(Locale.ROOT)] ?: return@mapNotNull null
            val softDeleteColumn = if (policy.deleted.enabled) policy.deleted.columnName else null
            val versionFieldName = if (policy.version.enabled) policy.version.fieldName else null
            if (table.dynamicInsert == null && table.dynamicUpdate == null && softDeleteColumn == null && versionFieldName == null) {
                return@mapNotNull null
            }

            AggregatePersistenceProviderControl(
                entityName = policy.entityName,
                entityPackageName = policy.entityPackageName,
                tableName = policy.tableName,
                dynamicInsert = table.dynamicInsert,
                dynamicUpdate = table.dynamicUpdate,
                softDeleteColumn = softDeleteColumn,
                idFieldName = policy.id.fieldName,
                versionFieldName = versionFieldName,
            )
        }
    }
}
