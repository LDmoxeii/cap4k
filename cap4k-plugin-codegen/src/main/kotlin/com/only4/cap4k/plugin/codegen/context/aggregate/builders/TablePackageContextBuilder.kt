package com.only4.cap4k.plugin.codegen.context.aggregate.builders

import com.only4.cap4k.plugin.codegen.context.ContextBuilder
import com.only4.cap4k.plugin.codegen.context.aggregate.MutableAggregateContext
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.refPackage

class TablePackageContextBuilder: ContextBuilder<MutableAggregateContext> {
    override val order: Int = 40

    override fun build(context: MutableAggregateContext) {
        context.tableMap.values.forEach { table ->
            with(context) {
                val tableName = SqlSchemaUtils.getTableName(table)
                val aggregate = resolveAggregateWithModule(tableName)

                val basePackage = getString("basePackage")
                val templatePackage = refPackage(templatePackage["entity"]!!)
                val `package` = refPackage(aggregate)
                tablePackageMap[tableName] = "$basePackage$templatePackage$`package`"
            }
        }
    }
}
