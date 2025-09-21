package com.only4.cap4k.gradle.codegen.misc

import com.only4.cap4k.gradle.codegen.AbstractCodegenTask
import com.only4.cap4k.gradle.codegen.misc.SqlSchemaUtils.LEFT_QUOTES_4_ID_ALIAS
import com.only4.cap4k.gradle.codegen.misc.SqlSchemaUtils.LEFT_QUOTES_4_LITERAL_STRING
import com.only4.cap4k.gradle.codegen.misc.SqlSchemaUtils.RIGHT_QUOTES_4_ID_ALIAS
import com.only4.cap4k.gradle.codegen.misc.SqlSchemaUtils.RIGHT_QUOTES_4_LITERAL_STRING
import com.only4.cap4k.gradle.codegen.misc.SqlSchemaUtils.executeQuery
import com.only4.cap4k.gradle.codegen.misc.SqlSchemaUtils.task


object SqlSchemaUtils4Mysql {
    fun resolveTables(connectionString: String, user: String, pwd: String): List<Map<String, Any?>> {
        var tableSql = """
                select * from ${LEFT_QUOTES_4_ID_ALIAS}information_schema${RIGHT_QUOTES_4_ID_ALIAS}.${LEFT_QUOTES_4_ID_ALIAS}tables${RIGHT_QUOTES_4_ID_ALIAS}
                    where table_schema = ${LEFT_QUOTES_4_ID_ALIAS}${task!!.extension.get().database.schema.get()}${RIGHT_QUOTES_4_ID_ALIAS}"
            """.trimIndent()
        if (task!!.extension.get().database.tables.get().isNotBlank()) {
            val whereClause = task!!.extension.get().database.tables.get()
                .split(AbstractCodegenTask.PATTERN_SPLITTER)
                .joinToString(" or ") { "table_name like ${LEFT_QUOTES_4_LITERAL_STRING}${it}${RIGHT_QUOTES_4_LITERAL_STRING}" }
            tableSql += "and ( $whereClause )"
        }
        if (task!!.extension.get().database.ignoreTables.get().isNotBlank()) {
            val whereClause = task!!.extension.get().database.ignoreTables.get()
                .split(AbstractCodegenTask.PATTERN_SPLITTER)
                .joinToString(" or ") { "table_name not like ${LEFT_QUOTES_4_LITERAL_STRING}${it}${RIGHT_QUOTES_4_LITERAL_STRING}" }
            tableSql += "and not ( $whereClause )"
        }
        return executeQuery(tableSql, connectionString, user, pwd)
    }

    fun resolveColumns(connectionString: String, user: String, pwd: String): List<Map<String, Any?>> {
        var columnSql = """
                select * from ${LEFT_QUOTES_4_ID_ALIAS}information_schema${RIGHT_QUOTES_4_ID_ALIAS}.${LEFT_QUOTES_4_ID_ALIAS}columns${RIGHT_QUOTES_4_ID_ALIAS}
                    where table_schema = ${LEFT_QUOTES_4_ID_ALIAS}${task!!.extension.get().database.schema.get()}${RIGHT_QUOTES_4_ID_ALIAS}
            """.trimIndent()
        if (task!!.extension.get().database.tables.get().isNotBlank()) {
            val whereClause = task!!.extension.get().database.tables.get()
                .split(AbstractCodegenTask.PATTERN_SPLITTER)
                .joinToString(" or ") { "table_name like ${LEFT_QUOTES_4_LITERAL_STRING}${it}${RIGHT_QUOTES_4_LITERAL_STRING}" }
            columnSql += "and ( $whereClause )"
        }
        if (task!!.extension.get().database.ignoreTables.get().isNotBlank()) {
            val whereClause = task!!.extension.get().database.ignoreTables.get()
                .split(AbstractCodegenTask.PATTERN_SPLITTER)
                .joinToString(" or ") { "table_name not like ${LEFT_QUOTES_4_LITERAL_STRING}${it}${RIGHT_QUOTES_4_LITERAL_STRING}" }
            columnSql += "and not ( $whereClause )"
        }
        return executeQuery(columnSql, connectionString, user, pwd)
    }

    fun getColumnType(column: Map<String, Any?>): String {
        val dataType = (column["DATA_TYPE"] as String).lowercase()
        val columnType = (column["COLUMN_TYPE"] as String).lowercase()
        val comment = SqlSchemaUtils.getComment(column)
        val columnName = SqlSchemaUtils.getColumnName(column).lowercase()
        if (task!!.extension.get().generation.typeRemapping.get()
                .isNotEmpty() && task!!.extension.get().generation.typeRemapping.get().containsKey(dataType)
        ) {
            return task!!.extension.get().generation.typeRemapping.get()[dataType]!!
        }

        return when (dataType) {
            "char", "varchar", "text", "mediumtext", "longtext" -> "String"
            "datetime", "timestamp" -> {
                if ("java.time".equals(task!!.extension.get().generation.datePackage.get(), ignoreCase = true)) {
                    "java.time.LocalDateTime"
                } else {
                    "java.util.Date"
                }
            }

            "date" -> {
                if ("java.time".equals(task!!.extension.get().generation.datePackage.get(), ignoreCase = true)) {
                    "java.time.LocalDate"
                } else {
                    "java.util.Date"
                }
            }

            "time" -> {
                if ("java.time".equals(task!!.extension.get().generation.datePackage.get(), ignoreCase = true)) {
                    "java.time.LocalTime"
                } else {
                    "java.util.Date"
                }
            }

            "int" -> "Int"
            "bigint" -> "Long"
            "smallint" -> "Short"
            "bit" -> "Boolean"
            "tinyint" -> {
                if (".deleted.".contains(".${columnName}.")) {
                    "Boolean"
                }
                if (task!!.extension.get().generation.deletedField.get().equals(columnName, ignoreCase = true)) {
                    "Boolean"
                }
                if (columnType.equals("tinyint(1)", ignoreCase = true)) {
                    "Boolean"
                }
                if (comment.contains("是否")) {
                    "Boolean"
                }
                "Byte"
            }

            "float" -> "Float"
            "double" -> "Double"
            "decimal", "numeric" -> "java.math.BigDecimal"
            else -> throw IllegalArgumentException("Unsupported DATA_TYPE: ${column["DATA_TYPE"]}")
        }
    }

    fun getColumnDefaultLiteral(column: Map<String, Any?>): String {
        val columnDefault = column["COLUMN_DEFAULT"] as String? ?: return "null"
        return when (SqlSchemaUtils.getColumnType(column)) {
            "String" -> {
                if (columnDefault.isNotEmpty()) {
                    return "\"${columnDefault.replace("\"", "\\\"")}\""
                }
                return "\"\""
            }

            "Int", "Short", "Byte", "Float", "Double" -> {
                if (columnDefault.isNotEmpty()) {
                    return columnDefault
                }
                return "0"
            }

            "Long" -> {
                if (columnDefault.isNotEmpty()) {
                    return columnDefault + "L"
                }
                return "0L"
            }

            "Boolean" -> {
                if (columnDefault.isNotEmpty()) {
                    if (columnDefault.trim().equals("b'1'", ignoreCase = true)) {
                        return "true"
                    }
                    if (columnDefault.trim().equals("b'0'", ignoreCase = true)) {
                        return "false"
                    }
                    if (columnDefault.trim() == "1") {
                        return "true"
                    }
                    if (columnDefault.trim() == "0") {
                        return "false"
                    }
                }
                "false"
            }

            "java.math.BigDecimal" -> {
                if (columnDefault.isNotEmpty()) {
                    return "java.math.BigDecimal(\"${columnDefault}\")"
                }
                return "java.math.BigDecimal.ZERO"
            }

            else -> ""
        }
    }

    fun isAutoUpdateDateColumn(column: Map<String, Any?>): Boolean {
        val extra = column["EXTRA"] as String
        return extra.contains("on update CURRENT_TIMESTAMP")
    }

    fun isAutoInsertDateColumn(column: Map<String, Any?>): Boolean {
        val extra = column["COLUMN_DEFAULT"] as String
        return extra.contains("CURRENT_TIMESTAMP")
    }

    fun isColumnInTable(column: Map<String, Any?>, table: Map<String, Any?>): Boolean =
        (column["TABLE_NAME"] as String).equals(table["TABLE_NAME"] as String, ignoreCase = true)

    fun getOrdinalPosition(column: Map<String, Any?>): Int = column["ORDINAL_POSITION"] as Int

    fun hasColumn(columnName: String, columns: List<Map<String, Any?>>): Boolean =
        columns.any { it["COLUMN_NAME"].toString().equals(columnName, ignoreCase = true) }

    fun getName(tableOrColumn: Map<String, Any?>): String {
        return if (tableOrColumn.containsKey("COLUMN_NAME")) {
            getColumnName(tableOrColumn)
        } else getTableName(tableOrColumn)
    }

    fun getColumnName(column: Map<String, Any?>): String = column["COLUMN_NAME"] as String

    fun getTableName(table: Map<String, Any?>): String = table["TABLE_NAME"] as String

    fun getColumnDbType(column: Map<String, Any?>): String = column["COLUMN_TYPE"] as String

    fun getColumnDbDataType(column: Map<String, Any?>): String = column["DATA_TYPE"] as String

    fun isColumnNullable(column: Map<String, Any?>): Boolean =
        (column["IS_NULLABLE"] as String).equals("YES", ignoreCase = true)

    fun isColumnPrimaryKey(column: Map<String, Any?>): Boolean =
        (column["COLUMN_KEY"] as String).equals("PRI", ignoreCase = true)

    fun getComment(tableOrColumn: Map<String, Any?>, cleanAnnotations: Boolean): String {
        val comment = if (tableOrColumn.containsKey("TABLE_COMMENT")) {
            tableOrColumn["TABLE_COMMENT"] as String? ?: ""
        } else if (tableOrColumn.containsKey("COLUMN_COMMENT")) {
            tableOrColumn["COLUMN_COMMENT"] as String? ?: ""
        } else {
            ""
        }

        return if (cleanAnnotations) {
            SqlSchemaUtils.ANNOTATION_PATTERN.matcher(comment).replaceAll("")
        } else {
            comment.trim()
        }
    }
}
