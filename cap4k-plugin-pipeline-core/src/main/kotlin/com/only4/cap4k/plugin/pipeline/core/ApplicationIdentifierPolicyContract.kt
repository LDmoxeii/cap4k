package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.OwnedManagedFieldPolicyDefinition
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal object ApplicationIdentifierPolicyContract {
    fun validate(
        config: ProjectConfig,
        tables: List<DbTableSnapshot>,
        contributedDefinitions: List<OwnedManagedFieldPolicyDefinition> = emptyList(),
    ) {
        rejectRetired(config.managedFields.identifierDefaultPolicy, "managedFields.identifierDefaultPolicy")
        config.managedFields.columnPolicyDefaults.forEach { (column, policy) ->
            rejectRetired(policy, "managedFields.columnPolicyDefaults[$column]")
        }
        tables.forEach { table ->
            table.columns.forEach { column ->
                column.managedPolicyKey?.let { policy ->
                    rejectRetired(policy, "${table.tableName}.${column.name}#comment:@Managed")
                }
            }
        }
        contributedDefinitions.forEach { owned ->
            rejectRetired(owned.definition.key, "managed field policy definition")
        }
    }

    fun rejectRetired(value: String, location: String) {
        if (value.trim().equals("identifier.snowflake", ignoreCase = true) ||
            value.trim().equals("snowflake", ignoreCase = true)
        ) {
            throw IllegalArgumentException(
                "unsupported application-side Strong ID strategy: rejected value '$value' at $location; " +
                    "supported application-side strategy: uuid7",
            )
        }
    }
}
