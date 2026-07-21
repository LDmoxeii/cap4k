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
            if (tableByName[policy.tableName.lowercase(Locale.ROOT)] == null) {
                return@mapNotNull null
            }
            val versionFieldName = if (policy.version.enabled) policy.version.fieldName else null
            if (versionFieldName == null) {
                return@mapNotNull null
            }

            AggregatePersistenceProviderControl(
                entityName = policy.entityName,
                entityPackageName = policy.entityPackageName,
                tableName = policy.tableName,
                softDeleteColumn = null,
                idFieldName = policy.id.fieldName,
                versionFieldName = versionFieldName,
            )
        }
    }
}
