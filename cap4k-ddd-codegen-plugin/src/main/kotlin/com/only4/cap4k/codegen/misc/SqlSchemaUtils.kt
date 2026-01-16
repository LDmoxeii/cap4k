package com.only4.cap4k.codegen.misc

import com.only4.cap4k.codegen.context.aggregate.AggregateContext
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

    val ANNOTATION_PATTERN: Pattern = Pattern.compile("@([A-Za-z]+)(=[^;]+)?;?")

    lateinit var context: AggregateContext

    // 新增: 驱动映射（可按需扩展）
    private val DRIVER_BY_DB = mapOf(
        DB_TYPE_MYSQL to "com.mysql.cj.jdbc.Driver",
        DB_TYPE_POSTGRESQL to "org.postgresql.Driver"
    )

    private val ANY_INT_REGEX = Regex("^[-+]?[0-9]+$")


    fun recognizeDbType(connectionString: String): String =
        connectionString.substringAfter("jdbc:").substringBefore(":")

    fun processSqlDialect(dbType: String) = when (dbType) {
        DB_TYPE_MYSQL -> {
            LEFT_QUOTES_4_ID_ALIAS = "`"; RIGHT_QUOTES_4_ID_ALIAS = "`"
            LEFT_QUOTES_4_LITERAL_STRING = "'"; RIGHT_QUOTES_4_LITERAL_STRING = "'"
        }

        DB_TYPE_POSTGRESQL, DB_TYPE_ORACLE -> {
            LEFT_QUOTES_4_ID_ALIAS = "\""; RIGHT_QUOTES_4_ID_ALIAS = "\""
            LEFT_QUOTES_4_LITERAL_STRING = "'"; RIGHT_QUOTES_4_LITERAL_STRING = "'"
        }

        DB_TYPE_SQLSERVER -> {
            LEFT_QUOTES_4_ID_ALIAS = "["; RIGHT_QUOTES_4_ID_ALIAS = "]"
            LEFT_QUOTES_4_LITERAL_STRING = "'"; RIGHT_QUOTES_4_LITERAL_STRING = "'"
        }

        else -> {}
    }

    interface SqlSchemaDialect {
        fun resolveTables(connectionString: String, user: String, pwd: String): List<Map<String, Any?>>
        fun resolveColumns(connectionString: String, user: String, pwd: String): List<Map<String, Any?>>
        fun resolveUniqueConstraints(connectionString: String, user: String, pwd: String): List<Map<String, Any?>>
        fun getColumnType(column: Map<String, Any?>): String
        fun getColumnDefaultLiteral(column: Map<String, Any?>): String
        fun isAutoUpdateDateColumn(column: Map<String, Any?>): Boolean
        fun isAutoInsertDateColumn(column: Map<String, Any?>): Boolean
        fun isColumnInTable(column: Map<String, Any?>, table: Map<String, Any?>): Boolean
        fun getOrdinalPosition(column: Map<String, Any?>): Int
        fun hasColumn(columnName: String, columns: List<Map<String, Any?>>): Boolean
        fun getName(tableOrColumn: Map<String, Any?>): String
        fun getColumnName(column: Map<String, Any?>): String
        fun getTableName(table: Map<String, Any?>): String
        fun getColumnDbType(column: Map<String, Any?>): String
        fun getColumnDbDataType(column: Map<String, Any?>): String
        fun isColumnNullable(column: Map<String, Any?>): Boolean
        fun isColumnPrimaryKey(column: Map<String, Any?>): Boolean
        fun getComment(tableOrColumn: Map<String, Any?>, cleanAnnotations: Boolean): String
    }

    private fun pickByConnectionString(connectionString: String): SqlSchemaDialect =
        when (recognizeDbType(connectionString)) {
            DB_TYPE_POSTGRESQL -> SqlSchemaUtils4Postgresql
            else -> SqlSchemaUtils4Mysql
        }

    private fun pickByTask(): SqlSchemaDialect =
        when (context.dbType) {
            DB_TYPE_POSTGRESQL -> SqlSchemaUtils4Postgresql
            else -> SqlSchemaUtils4Mysql
        }

    fun executeQuery(sql: String, connectionString: String, user: String, pwd: String): List<Map<String, Any?>> {
        val result = mutableListOf<Map<String, Any?>>()
        val dbType = recognizeDbType(connectionString)
        DRIVER_BY_DB[dbType]?.let { runCatching { Class.forName(it) } } // 驱动加载失败不抛
        DriverManager.getConnection(connectionString, user, pwd).use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(sql).use { rs ->
                    val meta = rs.metaData
                    while (rs.next()) {
                        val row = HashMap<String, Any?>(meta.columnCount)
                        for (i in 1..meta.columnCount) {
                            val value = rs.getObject(i)
                            row[meta.getColumnName(i)] =
                                if (value is ByteArray) String(value) else value
                        }
                        result += row
                    }
                }
            }
        }
        return result
    }

    // 连接串驱动的结构解析
    fun resolveTables(connectionString: String, user: String, pwd: String) =
        pickByConnectionString(connectionString).resolveTables(connectionString, user, pwd)

    fun resolveColumns(connectionString: String, user: String, pwd: String) =
        pickByConnectionString(connectionString).resolveColumns(connectionString, user, pwd)

    fun resolveUniqueConstraints(connectionString: String, user: String, pwd: String) =
        pickByConnectionString(connectionString).resolveUniqueConstraints(connectionString, user, pwd)

    // 方言驱动的列/表属性访问
    fun getColumnType(column: Map<String, Any?>): String =
        if (hasType(column)) {
             getType(column)
        } else pickByTask().getColumnType(column)

    fun getColumnDefaultLiteral(column: Map<String, Any?>) =
        pickByTask().getColumnDefaultLiteral(column)

    fun isAutoUpdateDateColumn(column: Map<String, Any?>) =
        pickByTask().isAutoUpdateDateColumn(column)

    fun isAutoInsertDateColumn(column: Map<String, Any?>) =
        pickByTask().isAutoInsertDateColumn(column)

    fun isColumnInTable(column: Map<String, Any?>, table: Map<String, Any?>) =
        pickByTask().isColumnInTable(column, table)

    // 新正确拼写
    fun getOrdinalPosition(column: Map<String, Any?>): Int =
        pickByTask().getOrdinalPosition(column)

    fun hasColumn(columnName: String, columns: List<Map<String, Any?>>) =
        pickByTask().hasColumn(columnName, columns)

    fun getName(tableOrColumn: Map<String, Any?>) =
        pickByTask().getName(tableOrColumn)

    fun getColumnName(column: Map<String, Any?>) =
        pickByTask().getColumnName(column)

    fun getTableName(table: Map<String, Any?>) =
        pickByTask().getTableName(table)

    fun getColumnDbType(column: Map<String, Any?>) =
        pickByTask().getColumnDbType(column)

    fun getColumnDbDataType(column: Map<String, Any?>) =
        pickByTask().getColumnDbDataType(column)

    fun isColumnNullable(column: Map<String, Any?>) =
        pickByTask().isColumnNullable(column)

    fun isColumnPrimaryKey(column: Map<String, Any?>) =
        pickByTask().isColumnPrimaryKey(column)

    fun getComment(tableOrColumn: Map<String, Any?>, cleanAnnotations: Boolean = true) =
        pickByTask().getComment(tableOrColumn, cleanAnnotations)

    fun getAnnotations(columnOrTable: Map<String, Any?>): Map<String, String> {
        val comment = getComment(columnOrTable, false)
        return parseAnnotations(comment)
    }

    private fun parseAnnotations(comment: String): Map<String, String> {
        val matcher = ANNOTATION_PATTERN.matcher(comment)
        val map = mutableMapOf<String, String>()
        while (matcher.find()) {
            if (matcher.groupCount() > 1 && matcher.group(1).isNotBlank()) {
                val name = matcher.group(1)
                val value = matcher.group(2)?.removePrefix("=")?.trim().orEmpty()
                map[name] = value
            }
        }
        return map
    }

    fun hasAnnotation(columnOrTable: Map<String, Any?>, annotation: String) =
        getAnnotations(columnOrTable).containsKey(annotation)

    fun hasAnyAnnotation(columnOrTable: Map<String, Any?>, annotations: List<String>): Boolean =
        annotations.any { hasAnnotation(columnOrTable, it) }

    fun getAnyAnnotation(tableOrColumn: Map<String, Any?>, annotations: List<String>): String =
        annotations.firstOrNull { hasAnnotation(tableOrColumn, it) }
            ?.let { getAnnotations(tableOrColumn)[it]!! }.orEmpty()

    // ---------------- 语义工具（保持原语义） ----------------
    fun hasLazy(table: Map<String, Any?>) = hasAnyAnnotation(table, listOf("Lazy", "L"))

    fun isLazy(table: Map<String, Any?>, defaultLazy: Boolean = false): Boolean {
        val value = getAnyAnnotation(table, listOf("Lazy", "L"))
        return when {
            value.equals("true", true) || value.equals("0", true) -> true
            value.equals("false", true) || value.equals("1", true) -> false
            else -> defaultLazy
        }
    }

    fun countIsOne(table: Map<String, Any?>): Boolean {
        val value = getAnyAnnotation(table, listOf("Count", "C"))
        return value.equals("one", true) || value.equals("1", true)
    }

    fun isIgnore(tableOrColumn: Map<String, Any?>) =
        hasAnyAnnotation(tableOrColumn, listOf("Ignore", "I"))

    fun isAggregateRoot(table: Map<String, Any?>) =
        !hasParent(table) || hasAnyAnnotation(table, listOf("AggregateRoot", "Root", "R"))

    fun isValueObject(table: Map<String, Any?>) =
        hasAnyAnnotation(table, listOf("ValueObject", "VO"))

    fun hasParent(table: Map<String, Any?>) =
        hasAnyAnnotation(table, listOf("Parent", "P"))

    fun getParent(table: Map<String, Any?>) =
        getAnyAnnotation(table, listOf("Parent", "P"))

    fun getModule(table: Map<String, Any?>): String =
        getAnyAnnotation(table, listOf("Module", "M"))

    fun getAggregate(table: Map<String, Any?>): String =
        getAnyAnnotation(table, listOf("Aggregate", "A"))

    fun hasIgnoreInsert(column: Map<String, Any?>) =
        hasAnyAnnotation(column, listOf("IgnoreInsert", "II"))

    fun hasIgnoreUpdate(column: Map<String, Any?>) =
        hasAnyAnnotation(column, listOf("IgnoreUpdate", "IU"))

    fun hasReadOnly(column: Map<String, Any?>) =
        hasAnyAnnotation(column, listOf("ReadOnly", "RO"))

    fun hasRelation(column: Map<String, Any?>) =
        hasAnyAnnotation(column, listOf("Relation", "Rel"))

    fun getRelation(column: Map<String, Any?>): String =
        getAnyAnnotation(column, listOf("Relation", "Rel"))

    fun hasReference(column: Map<String, Any?>) =
        hasAnyAnnotation(column, listOf("Reference", "Ref"))

    fun getReference(column: Map<String, Any?>): String {
        val columnName = getColumnName(column).lowercase()
        var ref = getAnyAnnotation(column, listOf("Reference", "Ref"))
        if (ref.isBlank()) {
            ref = when {
                columnName.endsWith("_id") -> columnName.removeSuffix("_id")
                columnName.endsWith("id") -> columnName.removeSuffix("id")
                else -> columnName
            }
        }
        return ref
    }

    fun hasIdGenerator(column: Map<String, Any?>) =
        hasAnyAnnotation(column, listOf("IdGenerator", "IG"))

    fun getIdGenerator(column: Map<String, Any?>): String =
        getAnyAnnotation(column, listOf("IdGenerator", "IG"))

    fun hasType(columnOrTable: Map<String, Any?>) =
        hasAnyAnnotation(columnOrTable, listOf("Type", "T"))

    fun getType(columnOrTable: Map<String, Any?>): String =
        getAnyAnnotation(columnOrTable, listOf("Type", "T"))

    fun hasEnum(columnOrTable: Map<String, Any?>) =
        hasAnyAnnotation(columnOrTable, listOf("Enum", "E"))

    fun getEnum(columnOrTable: Map<String, Any?>): Map<Int, Array<String>> {
        val config = getAnyAnnotation(columnOrTable, listOf("Enum", "E"))
        if (config.isBlank()) return emptyMap()
        val result = mutableMapOf<Int, Array<String>>()
        val items = config.splitWithTrim("\\|")
        items.forEachIndexed { idx, token ->
            val parts = token.split(":").map {
                it.trim().replace("\n", "").replace("\r", "").replace("\t", "")
            }
            if (parts.isEmpty()) return@forEachIndexed
            fun putValue(k: Int, name: String, value: String = name) {
                result[k] = arrayOf(name, value)
            }
            when (parts.size) {
                1 -> {
                    if (!ANY_INT_REGEX.matches(parts[0])) putValue(idx, parts[0])
                }

                2 -> {
                    if (ANY_INT_REGEX.matches(parts[0])) {
                        putValue(parts[0].toInt(), parts[1])
                    } else {
                        putValue(idx, parts[0], parts[1])
                    }
                }

                else -> {
                    if (ANY_INT_REGEX.matches(parts[0])) {
                        putValue(parts[0].toInt(), parts[1], parts[2])
                    } else {
                        putValue(idx, parts[0], parts[1])
                    }
                }
            }
        }
        return result
    }

    fun hasFactory(table: Map<String, Any?>) =
        hasAnyAnnotation(table, listOf("Factory", "Fac"))

    fun hasSpecification(table: Map<String, Any?>) =
        hasAnyAnnotation(table, listOf("Specification", "Spec", "Spe"))

    fun hasDomainEvent(table: Map<String, Any?>) =
        hasAnyAnnotation(table, listOf("DomainEvent", "DE", "Event", "Evt"))

    fun getDomainEvents(table: Map<String, Any?>): List<String> {
        if (!isAggregateRoot(table)) return emptyList()
        return getAnyAnnotation(table, listOf("DomainEvent", "DE", "Event", "Evt"))
            .splitWithTrim("\\|")
            .filter { it.isNotBlank() }
    }
}
