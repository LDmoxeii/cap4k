package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import java.util.Locale

internal data class OwnedParentBinding(
    val childTable: DbTableSnapshot,
    val parentTable: String,
    val parentRefColumn: DbColumnSnapshot,
)

internal object OwnedParentBindingResolver {
    fun resolve(
        tables: List<DbTableSnapshot>,
        skippedTableNames: Set<String> = emptySet(),
        outOfScopeTableNames: Set<String> = emptySet(),
    ): List<OwnedParentBinding> {
        val ignoredTableNames = (skippedTableNames + outOfScopeTableNames).map(::tableKey).toSet()

        return tables
            .mapNotNull { table ->
                val parentTable = table.parentTable ?: return@mapNotNull null
                val parentRefColumns = table.columns
                    .filter { it.parentRef }
                    .sortedBy { it.name }

                val parentRefColumn = when (parentRefColumns.size) {
                    0 -> throw IllegalArgumentException("missing parent reference column for table: ${table.tableName}")
                    1 -> parentRefColumns.single()
                    else -> throw IllegalArgumentException(
                        "ambiguous parent reference columns for table ${table.tableName}: ${parentRefColumns.joinToString(", ") { it.name }}"
                    )
                }

                if (tableKey(table.tableName) in ignoredTableNames || tableKey(parentTable) in ignoredTableNames) {
                    return@mapNotNull null
                }

                OwnedParentBinding(
                    childTable = table,
                    parentTable = parentTable,
                    parentRefColumn = parentRefColumn,
                )
            }
    }

    private fun tableKey(tableName: String): String = tableName.lowercase(Locale.ROOT)
}
