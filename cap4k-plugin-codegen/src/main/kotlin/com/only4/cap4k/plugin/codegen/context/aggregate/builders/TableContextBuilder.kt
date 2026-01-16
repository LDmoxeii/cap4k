package com.only4.cap4k.plugin.codegen.context.aggregate.builders

import com.only4.cap4k.plugin.codegen.context.ContextBuilder
import com.only4.cap4k.plugin.codegen.context.aggregate.MutableAggregateContext
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils

/**
 * 表和列信息构建器
 */
class TableContextBuilder : ContextBuilder<MutableAggregateContext> {
    override val order = 10

    override fun build(context: MutableAggregateContext) {
        val tables = SqlSchemaUtils.resolveTables(
            context.getString("dbUrl"),
            context.getString("dbUsername"),
            context.getString("dbPassword"),
        )

        val allColumns = SqlSchemaUtils.resolveColumns(
            context.getString("dbUrl"),
            context.getString("dbUsername"),
            context.getString("dbPassword"),
        )

        tables.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)
            val columns = allColumns
                .filter { SqlSchemaUtils.isColumnInTable(it, table) }
                .sortedBy { SqlSchemaUtils.getOrdinalPosition(it) }

            context.tableMap[tableName] = table
            context.columnsMap[tableName] = columns
        }
    }
}
