package com.only4.cap4k.plugin.pipeline.source.db

import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import java.sql.DatabaseMetaData
import java.sql.DriverManager
import java.util.Locale

class DbSchemaSourceProvider : SourceProvider {
    override val id: String = "db"

    override fun collect(config: ProjectConfig): DbSchemaSnapshot {
        val source = requireNotNull(config.sources[id]) { "Missing db source config" }
        val url = source.options["url"] as? String ?: error("db.url is required")
        val username = source.options["username"] as? String ?: ""
        val password = source.options["password"] as? String ?: ""
        val schema = (source.options["schema"] as? String ?: "").ifBlank { null }
        val includeTableRequests = requestedTableNames(source.options["includeTables"])
        val excludeTableRequests = requestedTableNames(source.options["excludeTables"])

        DriverManager.getConnection(url, username, password).use { connection ->
            val metadata = connection.metaData
            val discoveredTables = metadata.getTables(null, schema, "%", arrayOf("TABLE")).use { tableRows ->
                buildList {
                    while (tableRows.next()) {
                        add(tableRows.getString("TABLE_NAME"))
                    }
                }
            }
            val includeTables = resolveRequestedTables(
                requestedTables = includeTableRequests,
                discoveredTables = discoveredTables,
            )
            val excludeTables = resolveRequestedTables(
                requestedTables = excludeTableRequests,
                discoveredTables = discoveredTables,
            )
            val tables = when {
                includeTableRequests.isNotEmpty() && includeTables.isEmpty() -> emptyList()
                includeTableRequests.isEmpty() -> discoveredTables
                else -> discoveredTables.filter { it in includeTables }
            }.asSequence()
                .filterNot { it in excludeTables }
                .map { readTable(metadata, schema, it) }
                .toList()

            return DbSchemaSnapshot(tables = tables.sortedBy { it.tableName })
        }
    }

    private fun readTable(
        metadata: DatabaseMetaData,
        schema: String?,
        tableName: String,
    ): DbTableSnapshot {
        val primaryKey = metadata.getPrimaryKeys(null, schema, tableName).use { rows ->
            buildList {
                while (rows.next()) {
                    add(rows.getString("COLUMN_NAME"))
                }
            }
        }
        val primaryKeySet = primaryKey.toSet()
        val columns = metadata.getColumns(null, schema, tableName, "%").use { rows ->
            buildList {
                while (rows.next()) {
                    val name = rows.getString("COLUMN_NAME")
                    add(
                        DbColumnSnapshot(
                            name = name,
                            dbType = rows.getString("TYPE_NAME"),
                            kotlinType = JdbcTypeMapper.toKotlinType(rows.getInt("DATA_TYPE")),
                            nullable = rows.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                            defaultValue = rows.getString("COLUMN_DEF"),
                            comment = rows.getString("REMARKS") ?: "",
                            isPrimaryKey = name in primaryKeySet,
                        )
                    )
                }
            }
        }
        val uniqueConstraints = metadata.getIndexInfo(null, schema, tableName, true, false).use { rows ->
            linkedMapOf<String, MutableList<String>>().apply {
                while (rows.next()) {
                    if (rows.getBoolean("NON_UNIQUE")) continue
                    val indexName = rows.getString("INDEX_NAME") ?: continue
                    val columnName = rows.getString("COLUMN_NAME") ?: continue
                    getOrPut(indexName) { mutableListOf() }.add(columnName)
                }
            }.values
                .map { it.toList() }
                .filter { it.toSet() != primaryKeySet }
        }

        return DbTableSnapshot(
            tableName = tableName,
            comment = "",
            columns = columns,
            primaryKey = primaryKey,
            uniqueConstraints = uniqueConstraints,
        )
    }

    private fun requestedTableNames(value: Any?): List<String> =
        (value as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()

    private fun resolveRequestedTables(requestedTables: List<String>, discoveredTables: List<String>): Set<String> {
        if (requestedTables.isEmpty()) return emptySet()

        val discoveredByNormalized = discoveredTables.groupBy { normalizeIdentifier(it) }
        val discoveredByRaw = discoveredTables.toSet()

        return requestedTables.flatMap { requestedTable ->
            when {
                requestedTable in discoveredByRaw -> listOf(requestedTable)
                else -> {
                    val normalizedMatches = discoveredByNormalized[normalizeIdentifier(requestedTable)].orEmpty()
                    if (normalizedMatches.size == 1) normalizedMatches else emptyList()
                }
            }
        }.toSet()
    }

    private fun normalizeIdentifier(identifier: String): String = identifier.lowercase(Locale.ROOT)
}
