package com.only4.cap4k.gradle.codegen.misc

import com.only4.cap4k.gradle.codegen.GenEntityTask
import org.gradle.api.logging.Logger
import java.sql.DriverManager
import java.util.regex.Pattern

/**
 * SQL Schema 工具类
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
object SqlSchemaUtils {
    const val DB_TYPE_MYSQL = "mysql"
    const val DB_TYPE_POSTGRESQL = "postgresql"
    const val DB_TYPE_SQLSERVER = "sqlserver"
    const val DB_TYPE_ORACLE = "oracle"

    var LEFT_QUOTES_4_ID_ALIAS = "`"
    var RIGHT_QUOTES_4_ID_ALIAS = "`"
    var LEFT_QUOTES_4_LITERAL_STRING = "'"
    var RIGHT_QUOTES_4_LITERAL_STRING = "'"

    var logger: Logger? = null
    val ANNOTATION_PATTERN: Pattern = Pattern.compile("@([A-Za-z]+)(\\=[^;]+)?;?")

    var task: GenEntityTask? = null

    fun setLogger(logger: Logger) {
        this.logger = logger
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            logger?.error(message, throwable)
        } else {
            logger?.error(message)
        }
    }

    fun recognizeDbType(connectionString: String): String {
        return try {
            connectionString.split(":")[1]
        } catch (ex: Exception) {
            logError("数据库连接串异常 $connectionString", ex)
            DB_TYPE_MYSQL
        }
    }

    fun processSqlDialect(dbType: String) {
        when (dbType) {
            DB_TYPE_MYSQL -> {
                LEFT_QUOTES_4_ID_ALIAS = "`"
                RIGHT_QUOTES_4_ID_ALIAS = "`"
                LEFT_QUOTES_4_LITERAL_STRING = "'"
                RIGHT_QUOTES_4_LITERAL_STRING = "'"
            }

            DB_TYPE_POSTGRESQL, DB_TYPE_ORACLE -> {
                LEFT_QUOTES_4_ID_ALIAS = "\""
                RIGHT_QUOTES_4_ID_ALIAS = "\""
                LEFT_QUOTES_4_LITERAL_STRING = "'"
                RIGHT_QUOTES_4_LITERAL_STRING = "'"
            }

            DB_TYPE_SQLSERVER -> {
                LEFT_QUOTES_4_ID_ALIAS = "["
                RIGHT_QUOTES_4_ID_ALIAS = "]"
                LEFT_QUOTES_4_LITERAL_STRING = "'"
                RIGHT_QUOTES_4_LITERAL_STRING = "'"
            }
        }
    }

    fun executeQuery(sql: String, connectionString: String, user: String, pwd: String): List<Map<String, Any?>> {
        val result = mutableListOf<Map<String, Any?>>()
        try {
            // 加载驱动程序
            when (recognizeDbType(connectionString)) {
                DB_TYPE_MYSQL -> Class.forName("com.mysql.cj.jdbc.Driver")
                DB_TYPE_POSTGRESQL -> Class.forName("org.postgresql.Driver")
            }

            // 获得数据库连接
            DriverManager.getConnection(connectionString, user, pwd).use { conn ->
                conn.createStatement().use { st ->
                    st.executeQuery(sql).use { rs ->
                        while (rs.next()) {
                            val map = mutableMapOf<String, Any?>()
                            val metaData = rs.metaData
                            for (i in 1..metaData.columnCount) {
                                val value = rs.getObject(i)
                                map[metaData.getColumnName(i)] = if (value is ByteArray) {
                                    String(value)
                                } else {
                                    value
                                }
                            }
                            result.add(map)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            logError("SQL查询执行失败: $sql", e)
        }

        return result
    }

    fun resolveTables(connectionString: String, user: String, pwd: String): List<Map<String, Any?>> {
        return when (recognizeDbType(connectionString)) {
            DB_TYPE_MYSQL -> SqlSchemaUtils4Mysql.resolveTables(connectionString, user, pwd)
            DB_TYPE_POSTGRESQL -> throw NotImplementedError("PostgreSQL 列信息获取未实现")
            else -> SqlSchemaUtils4Mysql.resolveTables(connectionString, user, pwd)
        }
    }

    fun resolveColumns(connectionString: String, user: String, pwd: String): List<Map<String, Any?>> {
        return when (recognizeDbType(connectionString)) {
            DB_TYPE_MYSQL -> SqlSchemaUtils4Mysql.resolveColumns(connectionString, user, pwd)
            DB_TYPE_POSTGRESQL -> throw NotImplementedError("PostgreSQL 列信息获取未实现")
            else -> SqlSchemaUtils4Mysql.resolveColumns(connectionString, user, pwd)
        }
    }

    fun getColumnType(column: Map<String, Any?>): String {
        if (hasType(column)) {
            val customerType = getType(column)
            if (hasEnum(column) && task!!.enumPackageMap.containsKey(customerType)) {
                return "${task!!.enumPackageMap[customerType]!!}.$customerType"
            }
            return customerType
        }

        return when (task!!.dbType) {
            DB_TYPE_MYSQL -> SqlSchemaUtils4Mysql.getColumnType(column)
            DB_TYPE_POSTGRESQL -> throw NotImplementedError("PostgreSQL 列类型获取未实现")
            else -> SqlSchemaUtils4Mysql.getColumnType(column)
        }
    }

    fun getColumnDefaultLiteral(column: Map<String, Any?>): String {
        return when (task!!.dbType) {
            DB_TYPE_MYSQL -> SqlSchemaUtils4Mysql.getColumnDefaultLiteral(column)
            DB_TYPE_POSTGRESQL -> throw NotImplementedError("PostgreSQL 列类型获取未实现")
            else -> SqlSchemaUtils4Mysql.getColumnDefaultLiteral(column)
        }
    }

    fun isAutoUpdateDateColumn(column: Map<String, Any?>): Boolean {
        return when (task!!.dbType) {
            DB_TYPE_MYSQL -> SqlSchemaUtils4Mysql.isAutoUpdateDateColumn(column)
            DB_TYPE_POSTGRESQL -> throw NotImplementedError("PostgreSQL 列类型获取未实现")
            else -> SqlSchemaUtils4Mysql.isAutoUpdateDateColumn(column)
        }
    }

    fun isAutoInsertDateColumn(column: Map<String, Any?>): Boolean {
        return when (task!!.dbType) {
            DB_TYPE_MYSQL -> SqlSchemaUtils4Mysql.isAutoInsertDateColumn(column)
            DB_TYPE_POSTGRESQL -> throw NotImplementedError("PostgreSQL 列类型获取未实现")
            else -> SqlSchemaUtils4Mysql.isAutoInsertDateColumn(column)
        }
    }

    fun isColumnInTable(column: Map<String, Any?>, table: Map<String, Any?>): Boolean {
        return when (task!!.dbType) {
            DB_TYPE_MYSQL -> SqlSchemaUtils4Mysql.isColumnInTable(column, table)
            DB_TYPE_POSTGRESQL -> throw NotImplementedError("PostgreSQL 列类型获取未实现")
            else -> SqlSchemaUtils4Mysql.isColumnInTable(column, table)
        }
    }

    fun getOridinalPosition(column: Map<String, Any?>): Int {
        return when (task!!.dbType) {
            DB_TYPE_MYSQL -> SqlSchemaUtils4Mysql.getOrdinalPosition(column)
            DB_TYPE_POSTGRESQL -> throw NotImplementedError("PostgreSQL 列类型获取未实现")
            else -> SqlSchemaUtils4Mysql.getOrdinalPosition(column)
        }
    }

    fun hasColumn(columnName: String, columns: List<Map<String, Any?>>): Boolean {
        return when (task!!.dbType) {
            DB_TYPE_MYSQL -> SqlSchemaUtils4Mysql.hasColumn(columnName, columns)
            DB_TYPE_POSTGRESQL -> throw NotImplementedError("PostgreSQL 列类型获取未实现")
            else -> SqlSchemaUtils4Mysql.hasColumn(columnName, columns)
        }
    }

    fun getName(tableOrColumn: Map<String, Any?>): String {
        return when (task!!.dbType) {
            DB_TYPE_MYSQL -> SqlSchemaUtils4Mysql.getName(tableOrColumn)
            DB_TYPE_POSTGRESQL -> throw NotImplementedError("PostgreSQL 列类型获取未实现")
            else -> SqlSchemaUtils4Mysql.getName(tableOrColumn)
        }
    }

    fun getColumnName(column: Map<String, Any?>): String {
        return when (task!!.dbType) {
            DB_TYPE_MYSQL -> SqlSchemaUtils4Mysql.getColumnName(column)
            DB_TYPE_POSTGRESQL -> throw NotImplementedError("PostgreSQL 列类型获取未实现")
            else -> SqlSchemaUtils4Mysql.getColumnName(column)
        }
    }

    fun getTableName(table: Map<String, Any?>): String {
        return when (task!!.dbType) {
            DB_TYPE_MYSQL -> SqlSchemaUtils4Mysql.getTableName(table)
            DB_TYPE_POSTGRESQL -> throw NotImplementedError("PostgreSQL 列类型获取未实现")
            else -> SqlSchemaUtils4Mysql.getTableName(table)
        }
    }

    fun getColumnDbType(column: Map<String, Any?>): String {
        return when (task!!.dbType) {
            DB_TYPE_MYSQL -> SqlSchemaUtils4Mysql.getColumnDbType(column)
            DB_TYPE_POSTGRESQL -> throw NotImplementedError("PostgreSQL 列类型获取未实现")
            else -> SqlSchemaUtils4Mysql.getColumnDbType(column)
        }
    }

    fun getColumnDbDataType(column: Map<String, Any?>): String {
        return when (task!!.dbType) {
            DB_TYPE_MYSQL -> SqlSchemaUtils4Mysql.getColumnDbDataType(column)
            DB_TYPE_POSTGRESQL -> throw NotImplementedError("PostgreSQL 列类型获取未实现")
            else -> SqlSchemaUtils4Mysql.getColumnDbDataType(column)
        }
    }

    fun isColumnNullable(column: Map<String, Any?>): Boolean {
        return when (task!!.dbType) {
            DB_TYPE_MYSQL -> SqlSchemaUtils4Mysql.isColumnNullable(column)
            DB_TYPE_POSTGRESQL -> throw NotImplementedError("PostgreSQL 列类型获取未实现")
            else -> SqlSchemaUtils4Mysql.isColumnNullable(column)
        }
    }

    fun isColumnPrimaryKey(column: Map<String, Any?>): Boolean {
        return when (task!!.dbType) {
            DB_TYPE_MYSQL -> SqlSchemaUtils4Mysql.isColumnPrimaryKey(column)
            DB_TYPE_POSTGRESQL -> throw NotImplementedError("PostgreSQL 列类型获取未实现")
            else -> SqlSchemaUtils4Mysql.isColumnPrimaryKey(column)
        }
    }

    fun getComment(tableOrColumn: Map<String, Any?>, cleanAnnotations: Boolean = true): String {
        return when (task!!.dbType) {
            DB_TYPE_MYSQL -> SqlSchemaUtils4Mysql.getComment(tableOrColumn, cleanAnnotations)
            DB_TYPE_POSTGRESQL -> throw NotImplementedError("PostgreSQL 列类型获取未实现")
            else -> SqlSchemaUtils4Mysql.getComment(tableOrColumn, cleanAnnotations)
        }
    }

    fun getAnnotations(columnOrTable: Map<String, Any?>): Map<String, String> {
        val comment = getComment(columnOrTable, false)
        if (task!!.annotationsCache.containsKey(comment)) {
            return task!!.annotationsCache[comment]!!
        }
        val annotations = mutableMapOf<String, String>()
        val matcher = ANNOTATION_PATTERN.matcher(comment)
        while (matcher.find()) {
            if (matcher.groupCount() > 1 && matcher.group(1).isNotBlank()) {
                val name = matcher.group(1)
                val value = matcher.group(2)
                if (value.isNotBlank() && value.length > 1) {
                    annotations[name] = value.removePrefix("=")
                } else {
                    annotations[name] = ""
                }
            }
        }
        task!!.annotationsCache.putIfAbsent(comment, annotations)
        return annotations
    }

    fun hasAnnotation(columnOrTable: Map<String, Any?>, annotation: String): Boolean =
        getAnnotations(columnOrTable).containsKey(annotation)

    fun hasAnyAnnotation(
        columnOrTable: Map<String, Any?>,
        annotations: List<String>,
    ): Boolean = annotations.any { hasAnnotation(columnOrTable, it) }

    fun getAnyAnnotation(
        tableOrColumn: Map<String, Any?>,
        annotations: List<String>,
    ): String = annotations.find { hasAnnotation(tableOrColumn, it) }?.let {
        getAnnotations(tableOrColumn)[it]!!
    } ?: ""

    fun hasLazy(table: Map<String, Any?>): Boolean = hasAnyAnnotation(table, listOf("Lazy", "L"))

    fun isLazy(table: Map<String, Any?>, defaultLazy: Boolean = false): Boolean {
        val value = getAnyAnnotation(table, listOf("Lazy", "L"))
        return when {
            value.equals("true", ignoreCase = true) || value.equals("0", ignoreCase = true) -> true
            value.equals("false", ignoreCase = true) || value.equals("1", ignoreCase = true) -> false
            else -> defaultLazy
        }
    }

    fun countIsOne(table: Map<String, Any?>): Boolean {
        val value = getAnyAnnotation(table, listOf("Count", "C"))
        return "one".equals(value, ignoreCase = true) || "1".equals(value, ignoreCase = true)
    }

    fun isIgnore(tableOrColumn: Map<String, Any?>) = hasAnyAnnotation(tableOrColumn, listOf("Ignore", "I"))

    fun isAggregateRoot(table: Map<String, Any?>): Boolean =
        !hasParent(table) || hasAnyAnnotation(table, listOf("AggregateRoot", "Root", "R"))

    fun isValueObject(table: Map<String, Any?>): Boolean = hasAnyAnnotation(table, listOf("ValueObject", "VO"))

    fun hasParent(table: Map<String, Any?>): Boolean = hasAnyAnnotation(table, listOf("Parent", "P"))

    fun getParent(table: Map<String, Any?>) = getAnyAnnotation(table, listOf("Parent", "P"))

    fun getModule(table: Map<String, Any?>): Boolean = hasAnyAnnotation(table, listOf("Module", "M"))

    fun getAggregate(table: Map<String, Any?>): String = getAnyAnnotation(table, listOf("Aggregate", "A"))

    fun hasIgnoreInsert(column: Map<String, Any?>): Boolean = hasAnyAnnotation(column, listOf("IgnoreInsert", "II"))

    fun hasIgnoreUpdate(column: Map<String, Any?>): Boolean = hasAnyAnnotation(column, listOf("IgnoreUpdate", "IU"))

    fun hasReadOnly(column: Map<String, Any?>): Boolean = hasAnyAnnotation(column, listOf("ReadOnly", "RO"))

    fun hasRelation(column: Map<String, Any?>): Boolean = hasAnyAnnotation(column, listOf("Relation", "Rel"))

    fun getRelation(column: Map<String, Any?>): String = getAnyAnnotation(column, listOf("Relation", "Rel"))

    fun hasReference(column: Map<String, Any?>): Boolean = hasAnyAnnotation(column, listOf("Reference", "Ref"))

    fun getReference(column: Map<String, Any?>): String {
        var ref = getAnyAnnotation(column, listOf("Reference", "Ref"))
        val columnName = getColumnName(column).lowercase()
        if (ref.isBlank() && columnName.endsWith("_id")) {
            ref = columnName.removeSuffix("_id")
        } else if (ref.isBlank() && columnName.endsWith("id")) {
            ref = columnName.removeSuffix("id")
        }
        if (ref.isBlank()) {
            return columnName
        }
        return ref
    }

    fun hasIdGenerator(column: Map<String, Any?>): Boolean = hasAnyAnnotation(column, listOf("IdGenerator", "IG"))

    fun getIdGenerator(column: Map<String, Any?>): String = getAnyAnnotation(column, listOf("IdGenerator", "IG"))

    fun hasType(columnOrTable: Map<String, Any?>): Boolean = hasAnyAnnotation(columnOrTable, listOf("Type", "T"))

    fun getType(columnOrTable: Map<String, Any?>): String = getAnyAnnotation(columnOrTable, listOf("Type", "T"))

    fun hasEnum(columnOrTable: Map<String, Any?>) = hasAnyAnnotation(columnOrTable, listOf("Enum", "E"))

    fun getEnum(columnOrTable: Map<String, Any?>): Map<Int, Array<String>> {
        val enumsConfig = getAnyAnnotation(columnOrTable, listOf("Enum", "E"))
        val result = mutableMapOf<Int, Array<String>>()
        if (enumsConfig.isNotBlank()) {
            val enumsConfigs = enumsConfig.splitWithTrim("\\|")
            enumsConfigs.forEachIndexed { i, it ->
                val pair = it.split("\\:")
                    .map { c ->
                        c.trim()
                            .replace("\n", "")
                            .replace("\r", "")
                            .replace("\t", "")
                    }
                when (pair.size) {
                    0 -> return@forEachIndexed
                    1 -> {
                        if (pair[0].matches("^[-+]?[0-9]+$".toRegex())) {
                            return@forEachIndexed
                        } else {
                            result.put(i, arrayOf(pair[0], pair[0]))
                        }
                    }

                    2 -> {
                        if (pair[0].matches("^[-+]?[0-9]+$".toRegex())) {
                            result.put(pair[0].toInt(), arrayOf(pair[1], pair[1]))
                        } else {
                            result.put(i, arrayOf(pair[0], pair[1]))
                        }
                    }

                    else -> {
                        if (pair[0].matches("^[-+]?[0-9]+$".toRegex())) {
                            result.put(pair[0].toInt(), arrayOf(pair[1], pair[2]))
                        } else {
                            result.put(i, arrayOf(pair[0], pair[1]))
                        }
                    }
                }
            }
        }
        return result
    }

    fun hasFactory(table: Map<String, Any?>): Boolean = hasAnyAnnotation(table, listOf("Factory", "Fac"))

    fun hasSpecification(table: Map<String, Any?>): Boolean =
        hasAnyAnnotation(table, listOf("Specification", "Spec", "Spe"))

    fun hasDomainEvent(table: Map<String, Any?>): Boolean =
        hasAnyAnnotation(table, listOf("DomainEvent", "DE", "Event", "Evt"))

    fun getDomainEvents(table: Map<String, Any?>): List<String> {
        if (!isAggregateRoot(table)) {
            return emptyList()
        }
        return getAnyAnnotation(table, listOf("DomainEvent", "DE", "Event", "Evt")).splitWithTrim("\\|")
            .filter { it.isNotBlank() }
    }


}

private fun String.splitWithTrim(regexSplitter: String): List<String> {
    return this.split(regexSplitter).map { it.trim() }
}
