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
            val table = tableByName[policy.tableName.lowercase(Locale.ROOT)]
                ?: return@mapNotNull null
            val versionFieldName = if (policy.version.enabled) policy.version.fieldName else null
            val softDelete = AggregateSoftDeletePolicyResolver.resolve(
                table = table,
                resolvedPolicy = policy,
            )
            if (versionFieldName == null && softDelete == null) {
                return@mapNotNull null
            }

            AggregatePersistenceProviderControl(
                entityName = policy.entityName,
                entityPackageName = policy.entityPackageName,
                tableName = policy.tableName,
                softDelete = softDelete,
                idFieldName = policy.id.fieldName,
                versionFieldName = versionFieldName,
            )
        }
    }
}
