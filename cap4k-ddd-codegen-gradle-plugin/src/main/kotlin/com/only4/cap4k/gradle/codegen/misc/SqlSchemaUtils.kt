package com.only4.cap4k.gradle.codegen.misc

import org.gradle.api.logging.Logger
import java.sql.*
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

    private var logger: Logger? = null
    private val ANNOTATION_PATTERN = Pattern.compile("@([A-Za-z]+)(\\=[^;]+)?;?")

    fun setLogger(logger: Logger) {
        this.logger = logger
    }

    private fun log(message: String) {
        logger?.info(message)
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            logger?.error(message, throwable)
        } else {
            logger?.error(message)
        }
    }

    /**
     * 识别数据库类型
     */
    fun recognizeDbType(connectionString: String): String {
        return try {
            connectionString.split(":")[1]
        } catch (ex: Exception) {
            logError("数据库连接串异常 $connectionString", ex)
            DB_TYPE_MYSQL
        }
    }

    /**
     * 处理数据库方言语法配置
     */
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

    /**
     * 执行SQL查询
     */
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

    /**
     * 获取表的信息
     */
    fun resolveTables(connectionString: String, user: String, pwd: String): List<Map<String, Any?>> {
        return when (recognizeDbType(connectionString)) {
            DB_TYPE_MYSQL -> resolveMysqlTables(connectionString, user, pwd)
            DB_TYPE_POSTGRESQL -> resolvePostgresqlTables(connectionString, user, pwd)
            else -> resolveMysqlTables(connectionString, user, pwd)
        }
    }

    /**
     * 获取列的信息
     */
    fun resolveColumns(connectionString: String, user: String, pwd: String): List<Map<String, Any?>> {
        return when (recognizeDbType(connectionString)) {
            DB_TYPE_MYSQL -> resolveMysqlColumns(connectionString, user, pwd)
            DB_TYPE_POSTGRESQL -> resolvePostgresqlColumns(connectionString, user, pwd)
            else -> resolveMysqlColumns(connectionString, user, pwd)
        }
    }

    // MySQL 实现
    private fun resolveMysqlTables(connectionString: String, user: String, pwd: String): List<Map<String, Any?>> {
        val sql = """
            SELECT
                TABLE_NAME,
                TABLE_COMMENT,
                TABLE_SCHEMA
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
            ORDER BY TABLE_NAME
        """.trimIndent()
        return executeQuery(sql, connectionString, user, pwd)
    }

    private fun resolveMysqlColumns(connectionString: String, user: String, pwd: String): List<Map<String, Any?>> {
        val sql = """
            SELECT
                COLUMN_NAME,
                DATA_TYPE,
                COLUMN_TYPE,
                IS_NULLABLE,
                COLUMN_DEFAULT,
                COLUMN_COMMENT,
                TABLE_NAME,
                TABLE_SCHEMA,
                ORDINAL_POSITION,
                COLUMN_KEY,
                EXTRA
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            ORDER BY TABLE_NAME, ORDINAL_POSITION
        """.trimIndent()
        return executeQuery(sql, connectionString, user, pwd)
    }

    // PostgreSQL 实现
    private fun resolvePostgresqlTables(connectionString: String, user: String, pwd: String): List<Map<String, Any?>> {
        val sql = """
            SELECT
                t.table_name,
                obj_description(pgc.oid) as table_comment,
                t.table_schema
            FROM information_schema.tables t
            LEFT JOIN pg_class pgc ON pgc.relname = t.table_name
            WHERE t.table_schema = 'public'
            ORDER BY t.table_name
        """.trimIndent()
        return executeQuery(sql, connectionString, user, pwd)
    }

    private fun resolvePostgresqlColumns(connectionString: String, user: String, pwd: String): List<Map<String, Any?>> {
        val sql = """
            SELECT
                c.column_name,
                c.data_type,
                c.udt_name as column_type,
                c.is_nullable,
                c.column_default,
                col_description(pgc.oid, c.ordinal_position) as column_comment,
                c.table_name,
                c.table_schema,
                c.ordinal_position,
                CASE WHEN pk.column_name IS NOT NULL THEN 'PRI' ELSE '' END as column_key,
                '' as extra
            FROM information_schema.columns c
            LEFT JOIN pg_class pgc ON pgc.relname = c.table_name
            LEFT JOIN (
                SELECT ku.table_name, ku.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage ku ON tc.constraint_name = ku.constraint_name
                WHERE tc.constraint_type = 'PRIMARY KEY'
            ) pk ON pk.table_name = c.table_name AND pk.column_name = c.column_name
            WHERE c.table_schema = 'public'
            ORDER BY c.table_name, c.ordinal_position
        """.trimIndent()
        return executeQuery(sql, connectionString, user, pwd)
    }

    /**
     * 获取表名
     */
    fun getTableName(table: Map<String, Any?>): String {
        return table["TABLE_NAME"]?.toString() ?: table["table_name"]?.toString() ?: ""
    }

    /**
     * 获取表注释
     */
    fun getTableComment(table: Map<String, Any?>): String {
        return table["TABLE_COMMENT"]?.toString() ?: table["table_comment"]?.toString() ?: ""
    }

    /**
     * 获取列名
     */
    fun getColumnName(column: Map<String, Any?>): String {
        return column["COLUMN_NAME"]?.toString() ?: column["column_name"]?.toString() ?: ""
    }

    /**
     * 获取列注释
     */
    fun getColumnComment(column: Map<String, Any?>): String {
        return column["COLUMN_COMMENT"]?.toString() ?: column["column_comment"]?.toString() ?: ""
    }

    /**
     * 获取列的Java类型
     */
    fun getColumnJavaType(column: Map<String, Any?>): String {
        val dataType = (column["DATA_TYPE"]?.toString() ?: column["data_type"]?.toString() ?: "").lowercase()
        val columnType = (column["COLUMN_TYPE"]?.toString() ?: column["column_type"]?.toString() ?: "").lowercase()

        return when {
            dataType.contains("int") && !dataType.contains("bigint") -> "Int"
            dataType.contains("bigint") -> "Long"
            dataType.contains("decimal") || dataType.contains("numeric") -> "java.math.BigDecimal"
            dataType.contains("float") -> "Float"
            dataType.contains("double") -> "Double"
            dataType.contains("varchar") || dataType.contains("text") || dataType.contains("char") -> "String"
            dataType.contains("date") && !dataType.contains("datetime") -> "java.time.LocalDate"
            dataType.contains("datetime") || dataType.contains("timestamp") -> "java.time.LocalDateTime"
            dataType.contains("time") -> "java.time.LocalTime"
            dataType.contains("boolean") || dataType.contains("tinyint(1)") -> "Boolean"
            dataType.contains("blob") || dataType.contains("bytea") -> "ByteArray"
            else -> "String"
        }
    }

    /**
     * 获取列的数据库数据类型
     */
    fun getColumnDbDataType(column: Map<String, Any?>): String {
        return column["COLUMN_TYPE"]?.toString() ?: column["column_type"]?.toString() ?: ""
    }

    /**
     * 是否是主键列
     */
    fun isColumnPrimaryKey(column: Map<String, Any?>): Boolean {
        val columnKey = column["COLUMN_KEY"]?.toString() ?: column["column_key"]?.toString() ?: ""
        return columnKey.equals("PRI", ignoreCase = true)
    }

    /**
     * 是否是可空列
     */
    fun isColumnNullable(column: Map<String, Any?>): Boolean {
        val isNullable = column["IS_NULLABLE"]?.toString() ?: column["is_nullable"]?.toString() ?: ""
        return isNullable.equals("YES", ignoreCase = true)
    }

    /**
     * 获取列的默认值
     */
    fun getColumnDefault(column: Map<String, Any?>): String? {
        return column["COLUMN_DEFAULT"]?.toString() ?: column["column_default"]?.toString()
    }

    /**
     * 获取列的序号位置
     */
    fun getOrdinalPosition(column: Map<String, Any?>): Int {
        val position = column["ORDINAL_POSITION"] ?: column["ordinal_position"]
        return when (position) {
            is Number -> position.toInt()
            is String -> position.toIntOrNull() ?: 1
            else -> 1
        }
    }

    /**
     * 判断列是否在指定表中
     */
    fun isColumnInTable(column: Map<String, Any?>, table: Map<String, Any?>): Boolean {
        val columnTableName = column["TABLE_NAME"]?.toString() ?: column["table_name"]?.toString() ?: ""
        val tableName = getTableName(table)
        return columnTableName.equals(tableName, ignoreCase = true)
    }

    /**
     * 检查表是否有指定列
     */
    fun hasColumn(columnName: String, columns: List<Map<String, Any?>>): Boolean {
        return columns.any { getColumnName(it).equals(columnName, ignoreCase = true) }
    }

    /**
     * 解析注解信息
     */
    fun parseAnnotations(comment: String): Map<String, String> {
        val annotations = mutableMapOf<String, String>()
        val matcher = ANNOTATION_PATTERN.matcher(comment)
        while (matcher.find()) {
            val name = matcher.group(1)
            val value = matcher.group(2)?.removePrefix("=") ?: ""
            annotations[name] = value
        }
        return annotations
    }

    /**
     * 是否是聚合根
     */
    fun isAggregateRoot(table: Map<String, Any?>): Boolean {
        val comment = getTableComment(table)
        return parseAnnotations(comment).containsKey("AggregateRoot") ||
                parseAnnotations(comment).containsKey("Root")
    }

    /**
     * 是否是值对象
     */
    fun isValueObject(table: Map<String, Any?>): Boolean {
        val comment = getTableComment(table)
        return parseAnnotations(comment).containsKey("ValueObject")
    }

    /**
     * 获取模块名称
     */
    fun getModule(table: Map<String, Any?>): String {
        val comment = getTableComment(table)
        return parseAnnotations(comment)["Module"] ?: ""
    }

    /**
     * 获取聚合名称
     */
    fun getAggregate(table: Map<String, Any?>): String {
        val comment = getTableComment(table)
        return parseAnnotations(comment)["Aggregate"] ?: ""
    }

    /**
     * 获取父表名称
     */
    fun getParent(table: Map<String, Any?>): String {
        val comment = getTableComment(table)
        return parseAnnotations(comment)["Parent"] ?: ""
    }

    /**
     * 获取实体类型
     */
    fun getType(table: Map<String, Any?>): String {
        val comment = getTableComment(table)
        return parseAnnotations(comment)["Type"] ?: ""
    }

    /**
     * 是否包含枚举
     */
    fun hasEnum(column: Map<String, Any?>): Boolean {
        val comment = getColumnComment(column)
        return parseAnnotations(comment).containsKey("Enum")
    }

    /**
     * 获取枚举配置
     */
    fun getEnum(column: Map<String, Any?>): Map<Int, Array<String>> {
        val comment = getColumnComment(column)
        val enumValues = parseAnnotations(comment)["Enum"] ?: return emptyMap()

        val result = mutableMapOf<Int, Array<String>>()
        // 解析枚举值格式: "1:Active:激活,2:Inactive:未激活"
        enumValues.split(",").forEach { item ->
            val parts = item.split(":")
            if (parts.size >= 3) {
                val value = parts[0].toIntOrNull()
                if (value != null) {
                    result[value] = arrayOf(parts[1], parts[2])
                }
            }
        }
        return result
    }
}
