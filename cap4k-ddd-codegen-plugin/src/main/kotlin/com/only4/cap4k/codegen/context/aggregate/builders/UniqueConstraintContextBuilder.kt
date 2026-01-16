package com.only4.cap4k.codegen.context.aggregate.builders

import com.only4.cap4k.codegen.context.ContextBuilder
import com.only4.cap4k.codegen.context.aggregate.MutableAggregateContext
import com.only4.cap4k.codegen.misc.SqlSchemaUtils

class UniqueConstraintContextBuilder : ContextBuilder<MutableAggregateContext> {
    override val order: Int = 20

    override fun build(context: MutableAggregateContext) {
        val dbUrl = context.getString("dbUrl")
        val username = context.getString("dbUsername")
        val password = context.getString("dbPassword")

        val rows = SqlSchemaUtils.resolveUniqueConstraints(dbUrl, username, password)

        val byTable = rows.groupBy { it["TABLE_NAME"].toString() }
        byTable.forEach { (tableName, items) ->
            // 仅处理已纳入生成范围的表
            if (!context.tableMap.containsKey(tableName)) return@forEach

            val byConstraint = items.groupBy { it["CONSTRAINT_NAME"].toString() }
            val constraints = byConstraint.map { (cName, cols) ->
                val columnList = cols.sortedBy { (it["ORDINAL_POSITION"] as Number).toInt() }
                    .map { col ->
                        mapOf(
                            "columnName" to col["COLUMN_NAME"].toString(),
                            "ordinal" to (col["ORDINAL_POSITION"] as Number).toInt()
                        )
                    }
                mapOf(
                    "constraintName" to cName,
                    "columns" to columnList
                )
            }

            context.uniqueConstraintsMap[tableName] = constraints
        }
    }
}

