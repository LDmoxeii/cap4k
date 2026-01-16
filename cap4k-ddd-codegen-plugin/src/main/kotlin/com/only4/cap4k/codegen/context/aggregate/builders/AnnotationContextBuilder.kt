package com.only4.cap4k.codegen.context.aggregate.builders

import com.only4.cap4k.codegen.context.ContextBuilder
import com.only4.cap4k.codegen.context.aggregate.MutableAggregateContext
import com.only4.cap4k.codegen.misc.SqlSchemaUtils.getAnnotations
import com.only4.cap4k.codegen.misc.SqlSchemaUtils.getComment
import com.only4.cap4k.codegen.misc.SqlSchemaUtils.getTableName

class AnnotationContextBuilder: ContextBuilder<MutableAggregateContext> {
    override val order: Int = 20

    override fun build(context: MutableAggregateContext) {
        with(context) {
            tableMap.values.forEach { table ->
                val tableName = getTableName(table)
                val tableComment = getComment(table)

                annotationsMap[tableComment] = getAnnotations(table)

                columnsMap[tableName]!!.forEach { column ->
                    val columnComment = getComment(column)

                    annotationsMap[columnComment] = getAnnotations(column)
                }
            }
        }
    }
}
