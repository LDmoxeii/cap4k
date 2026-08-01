package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceProviderControl
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldRole
import com.only4.cap4k.plugin.pipeline.api.ResolvedManagedEntityPolicy
import java.util.Locale

internal object AggregatePersistenceProviderInference {
    fun infer(
        tables: List<DbTableSnapshot>,
        resolvedPolicies: List<ResolvedManagedEntityPolicy>,
    ): List<AggregatePersistenceProviderControl> {
        val tableByName = tables.associateBy { it.tableName.lowercase(Locale.ROOT) }

        return resolvedPolicies.mapNotNull { policy ->
            val table = tableByName[policy.tableName.lowercase(Locale.ROOT)]
                ?: return@mapNotNull null
            val versionFieldName = policy.fieldByRole(ManagedFieldRole.VERSION)?.fieldName
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
                idFieldName = policy.requireIdentifier().fieldName,
                versionFieldName = versionFieldName,
            )
        }
    }
}
