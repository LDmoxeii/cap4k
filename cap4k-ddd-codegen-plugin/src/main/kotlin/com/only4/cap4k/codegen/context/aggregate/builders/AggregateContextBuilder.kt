package com.only4.cap4k.codegen.context.aggregate.builders

import com.only4.cap4k.codegen.context.ContextBuilder
import com.only4.cap4k.codegen.context.aggregate.MutableAggregateContext
import com.only4.cap4k.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.codegen.misc.toSnakeCase

class AggregateContextBuilder : ContextBuilder<MutableAggregateContext> {
    override val order: Int = 30

    override fun build(context: MutableAggregateContext) {
        context.tableMap.values.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)
            var aggregateRootTableName = tableName

            var result = ""

            generateSequence(table) { currentTable ->
                val parent = SqlSchemaUtils.getParent(currentTable)
                if (parent.isBlank()) null else {
                    context.tableMap[parent]?.also {
                        aggregateRootTableName = SqlSchemaUtils.getTableName(it)
                    }
                }
            }.forEach { table ->
                val aggregate = SqlSchemaUtils.getAggregate(table)

                if (SqlSchemaUtils.isAggregateRoot(table) || aggregate.isNotBlank()) {
                    result = aggregate.takeIf { it.isNotBlank() }
                        ?: (toSnakeCase(context.entityTypeMap[aggregateRootTableName]) ?: "")
                    return@forEach
                }
            }

            if (result.isBlank()) {
                result = toSnakeCase(context.entityTypeMap[aggregateRootTableName]) ?: ""
            }

            context.tableAggregateMap[tableName] = result
        }
    }
}
