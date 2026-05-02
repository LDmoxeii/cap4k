package com.only4.cap4k.plugin.pipeline.source.db

import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import com.only4.cap4k.plugin.pipeline.api.UniqueConstraintModel
import java.sql.DatabaseMetaData
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Locale

private val H2_UNIQUE_INDEX_SUFFIX = Regex("""_INDEX_[A-Z0-9]+$""")

class DbSchemaSourceProvider : SourceProvider {
    override val id: String = "db"
    private val relationAnnotationParser = DbRelationAnnotationParser()
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
                    val annotationMetadata = DbColumnAnnotationParser.parse(comment)
                    val relationMetadata = relationAnnotationParser.parseColumn(comment)
                    add(
                        DbColumnSnapshot(
                            name = name,
                            dbType = typeName,
                            kotlinType = JdbcTypeMapper.toKotlinType(rows.getInt("DATA_TYPE"), typeName),
                            nullable = rows.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                            defaultValue = rows.getString("COLUMN_DEF"),
                            comment = relationMetadata.cleanedComment,
                            isPrimaryKey = name in primaryKeySet,
                            typeBinding = annotationMetadata.typeBinding,
                            enumItems = annotationMetadata.enumItems,
                            referenceTable = relationMetadata.referenceTable,
                            explicitRelationType = relationMetadata.explicitRelationType,
                            lazy = relationMetadata.lazy,
                            countHint = relationMetadata.countHint,
                            generatedValueStrategy = annotationMetadata.generatedValueStrategy,
                            version = annotationMetadata.version,
                            insertable = annotationMetadata.insertable,
                            updatable = annotationMetadata.updatable,
                        )
                    )
                }
            }
        }
        val uniqueConstraints = metadata.getIndexInfo(scope.catalog, scope.schemaPattern, tableName, true, false).use { rows ->
            data class IndexedConstraintColumn(
                val name: String,
                val ordinalPosition: Int,
                val metadataSequence: Int,
            )

            var metadataSequence = 0
            linkedMapOf<String, MutableList<IndexedConstraintColumn>>().apply {
                while (rows.next()) {
                    if (rows.getBoolean("NON_UNIQUE")) continue
                    val indexName = rows.getString("INDEX_NAME") ?: continue
                    val columnName = rows.getString("COLUMN_NAME") ?: continue
                    val ordinalPosition = rows.getInt("ORDINAL_POSITION")
                    val physicalName = normalizeUniquePhysicalName(metadata, indexName)
                    getOrPut(physicalName) { mutableListOf() }.add(
                        IndexedConstraintColumn(
                            name = columnName,
                            ordinalPosition = ordinalPosition,
                            metadataSequence = metadataSequence++,
                        )
                    )
                }
            }.map { (physicalName, columns) ->
                UniqueConstraintModel(
                    physicalName = physicalName,
                    columns = columns
                        .sortedWith(
                            compareBy<IndexedConstraintColumn> {
                                if (it.ordinalPosition > 0) it.ordinalPosition else Int.MAX_VALUE
                            }.thenBy { it.metadataSequence }
                        )
                        .map { it.name },
                )
            }
                .filter { it.columns.toSet() != primaryKeySet }
        }

        return ReadTableResult(
            table = DbTableSnapshot(
                tableName = tableName,
                comment = tableMetadata.cleanedComment,
                columns = columns,
                primaryKey = primaryKey,
                uniqueConstraints = uniqueConstraints,
                parentTable = tableMetadata.parentTable,
                aggregateRoot = tableMetadata.aggregateRoot,
                valueObject = tableMetadata.valueObject,
                dynamicInsert = tableMetadata.dynamicInsert,
                dynamicUpdate = tableMetadata.dynamicUpdate,
                softDeleteColumn = tableMetadata.softDeleteColumn,
            ),
            ignored = tableMetadata.ignored,
        )
    }

    private fun requestedTableNames(value: Any?): List<String> =
        (value as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()

    private fun normalizeUniquePhysicalName(metadata: DatabaseMetaData, indexName: String): String {
        if (!metadata.databaseProductName.equals("H2", ignoreCase = true)) return indexName
        return indexName.replace(H2_UNIQUE_INDEX_SUFFIX, "")
    }

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
