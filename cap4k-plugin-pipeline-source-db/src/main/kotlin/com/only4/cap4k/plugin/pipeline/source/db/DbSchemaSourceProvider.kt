package com.only4.cap4k.plugin.pipeline.source.db

import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbIdStrategy
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import com.only4.cap4k.plugin.pipeline.api.UniqueConstraintModel
import java.sql.DatabaseMetaData
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Locale

class DbSchemaSourceProvider : SourceProvider {
    override val id: String = "db"
    private val tableAnnotationParser = DbTableAnnotationParser

    override fun collect(config: ProjectConfig): DbSchemaSnapshot {
        val source = requireNotNull(config.sources[id]) { "Missing db source config" }
        val url = source.options["url"] as? String ?: error("db.url is required")
        val username = source.options["username"] as? String ?: ""
        val password = source.options["password"] as? String ?: ""
        val schema = (source.options["schema"] as? String ?: "").ifBlank { null }
        val includeTableRequests = requestedTableNames(source.options["includeTables"])
        val excludeTableRequests = requestedTableNames(source.options["excludeTables"])

        ensureDriverAvailable(url)

        DriverManager.getConnection(url, username, password).use { connection ->
            val metadata = connection.metaData
            val scope = resolveJdbcMetadataScope(
                url = url,
                configuredSchema = schema,
                connectionCatalog = connection.catalog,
            )
            val discoveredTables = metadata.getTables(scope.catalog, scope.schemaPattern, "%", arrayOf("TABLE")).use { tableRows ->
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
            val selectedTables = when {
                includeTableRequests.isNotEmpty() && includeTables.isEmpty() -> emptyList()
                includeTableRequests.isEmpty() -> discoveredTables
                else -> discoveredTables.filter { it in includeTables }
            }
            val filteredTables = selectedTables.filterNot { it in excludeTables }
            val tableResults = filteredTables
                .asSequence()
                .map { readTable(metadata, scope, it) }
                .toList()
            val tables = tableResults
                .filterNot { it.ignored }
                .map { it.table }
            val includedTables = tables.map { it.tableName }

            return DbSchemaSnapshot(
                tables = tables.sortedBy { it.tableName },
                discoveredTables = discoveredTables.sorted(),
                includedTables = includedTables.sorted(),
                excludedTables = excludeTables.sorted(),
            )
        }
    }

    private fun readTable(
        metadata: DatabaseMetaData,
        scope: JdbcMetadataScope,
        tableName: String,
    ): ReadTableResult {
        val tableComment = metadata.getTables(scope.catalog, scope.schemaPattern, tableName, arrayOf("TABLE")).use { rows ->
            if (rows.next()) rows.getString("REMARKS") ?: "" else ""
        }
        val tableMetadata = tableAnnotationParser.parse(tableComment)
        val primaryKey = metadata.getPrimaryKeys(scope.catalog, scope.schemaPattern, tableName).use { rows ->
            buildList {
                while (rows.next()) {
                    add(rows.getString("COLUMN_NAME"))
                }
            }
        }
        val primaryKeySet = primaryKey.toSet()
        val columns = metadata.getColumns(scope.catalog, scope.schemaPattern, tableName, "%").use { rows ->
            buildList {
                while (rows.next()) {
                    val name = rows.getString("COLUMN_NAME")
                    val comment = rows.getString("REMARKS") ?: ""
                    val typeName = rows.getString("TYPE_NAME")
                    val columnMetadata = DbColumnAnnotationParser.parse(comment)
                    add(
                        DbColumnSnapshot(
                            name = name,
                            dbType = typeName,
                            kotlinType = JdbcTypeMapper.toKotlinType(rows.getInt("DATA_TYPE"), typeName),
                            nullable = rows.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                            defaultValue = rows.getString("COLUMN_DEF"),
                            comment = columnMetadata.cleanedComment,
                            isPrimaryKey = name in primaryKeySet,
                            typeBinding = columnMetadata.typeBinding,
                            enumItems = columnMetadata.enumItems,
                            parentRef = columnMetadata.parentRef,
                            refAggregate = columnMetadata.refAggregate,
                            refId = columnMetadata.refId,
                            idStrategy = columnMetadata.idStrategy,
                            managedRole = columnMetadata.managedRole,
                            inherited = columnMetadata.inherited,
                        )
                    )
                }
            }
        }
        val uniqueConstraints = metadata.getIndexInfo(scope.catalog, scope.schemaPattern, tableName, true, false).use { rows ->
            var metadataSequence = 0
            val indexRows = buildList {
                while (rows.next()) {
                    if (rows.getBoolean("NON_UNIQUE")) continue
                    val indexName = rows.getString("INDEX_NAME") ?: continue
                    add(
                        UniqueIndexMetadataRow(
                            indexName = indexName,
                            columnName = rows.getString("COLUMN_NAME"),
                            ordinalPosition = rows.getInt("ORDINAL_POSITION"),
                            metadataSequence = metadataSequence++,
                            filterCondition = rows.getString("FILTER_CONDITION"),
                        )
                    )
                }
            }
            uniqueConstraintsFromIndexRows(
                rows = indexRows,
                primaryKey = primaryKeySet,
                physicalColumns = columns.mapTo(linkedSetOf()) { it.name },
            )
        }

        val table = DbTableSnapshot(
            tableName = tableName,
            comment = tableMetadata.cleanedComment,
            columns = columns,
            primaryKey = primaryKey,
            uniqueConstraints = uniqueConstraints,
            parentTable = tableMetadata.parentTable,
            aggregateRoot = tableMetadata.parentTable == null,
        )
        validateTable(table)

        return ReadTableResult(
            table = table,
            ignored = tableMetadata.ignored,
        )
    }

    private fun validateTable(table: DbTableSnapshot) {
        require(table.columns.none { it.parentRef } || table.parentTable != null) {
            "@ParentRef is valid only on child tables with @Parent"
        }

        if (table.parentTable != null) {
            val parentRefCount = table.columns.count { it.parentRef }
            require(parentRefCount != 0) {
                "table ${table.tableName.uppercase(Locale.ROOT)} declares @Parent=${table.parentTable} but has no @ParentRef column."
            }
            require(parentRefCount == 1) {
                "table ${table.tableName.uppercase(Locale.ROOT)} declares @Parent=${table.parentTable} but must declare exactly one @ParentRef column."
            }
        }

        require(table.columns.filter { it.idStrategy == DbIdStrategy.DB_IDENTITY }.all { it.isPrimaryKey }) {
            "@IdStrategy=db_identity is valid only on a primary-key column"
        }
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

    private fun ensureDriverAvailable(url: String) {
        if (hasDriver(url)) {
            return
        }

        knownDriverClassNames(url).forEach { className ->
            runCatching {
                Class.forName(className, true, javaClass.classLoader)
            }
            if (hasDriver(url)) {
                return
            }
        }
    }

    private fun hasDriver(url: String): Boolean =
        try {
            DriverManager.getDriver(url)
            true
        } catch (_: SQLException) {
            false
        }

    private fun knownDriverClassNames(url: String): List<String> = when {
        url.startsWith("jdbc:h2:") -> listOf("org.h2.Driver")
        url.startsWith("jdbc:mysql:") -> listOf("com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver")
        url.startsWith("jdbc:mariadb:") -> listOf("org.mariadb.jdbc.Driver")
        url.startsWith("jdbc:postgresql:") -> listOf("org.postgresql.Driver")
        else -> emptyList()
    }
}

private data class ReadTableResult(
    val table: DbTableSnapshot,
    val ignored: Boolean,
)

internal data class JdbcMetadataScope(
    val catalog: String?,
    val schemaPattern: String?,
)

internal fun resolveJdbcMetadataScope(
    url: String,
    configuredSchema: String?,
    connectionCatalog: String?,
): JdbcMetadataScope {
    val schema = configuredSchema?.ifBlank { null }
    val catalog = connectionCatalog?.ifBlank { null }
    return when {
        url.startsWith("jdbc:mysql:", ignoreCase = true) ||
            url.startsWith("jdbc:mariadb:", ignoreCase = true) ->
            JdbcMetadataScope(
                catalog = schema ?: catalog,
                schemaPattern = null,
            )

        else -> JdbcMetadataScope(
            catalog = null,
            schemaPattern = schema,
        )
    }
}

internal data class UniqueIndexMetadataRow(
    val indexName: String,
    val columnName: String?,
    val ordinalPosition: Int,
    val metadataSequence: Int,
    val filterCondition: String?,
)

internal fun uniqueConstraintsFromIndexRows(
    rows: List<UniqueIndexMetadataRow>,
    primaryKey: Set<String>,
    physicalColumns: Set<String>,
): List<UniqueConstraintModel> = rows
    .groupByTo(linkedMapOf(), UniqueIndexMetadataRow::indexName)
    .map { (physicalName, indexRows) ->
        val physicalColumnsByKey = physicalColumns.associateBy { it.lowercase(Locale.ROOT) }
        val sortedRows = indexRows.sortedWith(
            compareBy<UniqueIndexMetadataRow> {
                if (it.ordinalPosition > 0) it.ordinalPosition else Int.MAX_VALUE
            }.thenBy { it.metadataSequence }
        )
        val resolvedColumns = sortedRows.map { row ->
            row.columnName?.let { columnName ->
                physicalColumnsByKey[columnName.lowercase(Locale.ROOT)]
            }
        }
        val filterCondition = indexRows
            .asSequence()
            .mapNotNull { row -> row.filterCondition?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()
        UniqueConstraintModel(
            physicalName = physicalName,
            columns = resolvedColumns.filterNotNull(),
            complete = resolvedColumns.all { it != null },
            filterCondition = filterCondition,
        )
    }
    .filterNot { constraint ->
        constraint.complete &&
            constraint.filterCondition.isNullOrBlank() &&
            constraint.columns.mapTo(linkedSetOf()) { it.lowercase(Locale.ROOT) } ==
            primaryKey.mapTo(linkedSetOf()) { it.lowercase(Locale.ROOT) }
    }
