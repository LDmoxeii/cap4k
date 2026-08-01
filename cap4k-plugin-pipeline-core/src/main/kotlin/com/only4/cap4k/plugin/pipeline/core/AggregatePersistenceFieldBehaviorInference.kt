package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceFieldControl
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldRole
import com.only4.cap4k.plugin.pipeline.api.ManagedValueAuthority
import com.only4.cap4k.plugin.pipeline.api.ResolvedManagedEntityPolicy

internal object AggregatePersistenceFieldBehaviorInference {
    fun infer(resolvedPolicies: List<ResolvedManagedEntityPolicy>): List<AggregatePersistenceFieldControl> =
        resolvedPolicies.flatMap { entity ->
            entity.fields.mapNotNull { field ->
                val generatedValueStrategy = "IDENTITY".takeIf {
                    field.policyKey == "identifier.database-identity"
                }
                val generatedEvents = when (field.policyKey) {
                    "database.generated-on-insert" -> listOf("INSERT")
                    "database.generated-always" -> listOf("INSERT", "UPDATE")
                    else -> emptyList()
                }
                val version = (field.role == ManagedFieldRole.VERSION).takeIf { it }
                val insertable = field.persistence.insert.isJpaWritable()
                val updatable = field.persistence.update.isJpaWritable()
                AggregatePersistenceFieldControl(
                    entityName = entity.entityName,
                    entityPackageName = entity.entityPackageName,
                    fieldName = field.fieldName,
                    columnName = field.columnName,
                    generatedValueStrategy = generatedValueStrategy,
                    generatedEvents = generatedEvents,
                    version = version,
                    insertable = insertable,
                    updatable = updatable,
                )
            }
        }

    private fun ManagedValueAuthority.isJpaWritable(): Boolean = when (this) {
        ManagedValueAuthority.CALLER,
        ManagedValueAuthority.FRAMEWORK,
        ManagedValueAuthority.MANAGED_HANDLER,
        ManagedValueAuthority.PERSISTENCE_PROVIDER,
        -> true
        ManagedValueAuthority.DATABASE,
        ManagedValueAuthority.NONE,
        -> false
    }
}
