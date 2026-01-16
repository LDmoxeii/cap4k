package com.only4.cap4k.codegen.context.aggregate.builders

import com.only4.cap4k.codegen.context.ContextBuilder
import com.only4.cap4k.codegen.context.aggregate.MutableAggregateContext
import com.only4.cap4k.codegen.misc.SqlSchemaUtils

/**
 * 表关系构建器
 */
class RelationContextBuilder : ContextBuilder<MutableAggregateContext> {
    override val order = 20

    override fun build(context: MutableAggregateContext) {
        context.tableMap.values.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)
            val columns = context.columnsMap[tableName]!!

            // 1. 解析表关系
            val relationTable = resolveRelationTable(table, columns, context)

            // 2. 合并到全局关系 Map
            relationTable.forEach { (key, value) ->
                context.relationsMap.merge(key, value) { existing, new ->
                    existing.toMutableMap().apply { putAll(new) }
                }
            }
        }
    }

    private fun resolveRelationTable(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        context: MutableAggregateContext
    ): Map<String, Map<String, String>> {
        val result = mutableMapOf<String, MutableMap<String, String>>()
        val tableName = SqlSchemaUtils.getTableName(table)

        if (SqlSchemaUtils.isIgnore(table)) return result

        // 聚合内部关系 OneToMany
        if (!SqlSchemaUtils.isAggregateRoot(table)) {
            processInternalRelations(table, columns, result, tableName, context)
        }

        // 聚合之间关系 ManyToMany
        if (SqlSchemaUtils.hasRelation(table)) {
            processExternalRelations(columns, result, tableName, context)
        }

        // 处理显式关系配置
        processExplicitRelations(columns, result, tableName, context)

        return result
    }

    private fun processInternalRelations(
        table: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        result: MutableMap<String, MutableMap<String, String>>,
        tableName: String,
        context: MutableAggregateContext
    ) {
        val parent = SqlSchemaUtils.getParent(table)
        result.putIfAbsent(parent, mutableMapOf())

        var rewrited = false
        columns.forEach { column ->
            if (SqlSchemaUtils.hasReference(column)) {
                if (parent.equals(SqlSchemaUtils.getReference(column), ignoreCase = true)) {
                    val lazy = SqlSchemaUtils.isLazy(
                        column,
                        "LAZY".equals(context.getString("fetchType"), ignoreCase = true)
                    )
                    val columnName = SqlSchemaUtils.getColumnName(column)

                    result[parent]!!.putIfAbsent(
                        tableName,
                        "OneToMany;$columnName${if (lazy) ";LAZY" else ""}"
                    )

                    result.putIfAbsent(tableName, mutableMapOf())
                    if (context.getBoolean("generateParent")) {
                        val parentRelation = "*ManyToOne;$columnName${if (lazy) ";LAZY" else ""}"
                        result[tableName]!!.putIfAbsent(parent, parentRelation)
                    }

                    rewrited = true
                }
            }
        }

        if (!rewrited) {
            val column = columns.firstOrNull {
                SqlSchemaUtils.getColumnName(it).equals("${parent}_id", ignoreCase = true)
            }
            if (column != null) {
                val lazy = SqlSchemaUtils.isLazy(
                    column,
                    "LAZY".equals(context.getString("fetchType"), ignoreCase = true)
                )
                val columnName = SqlSchemaUtils.getColumnName(column)

                result[parent]!!.putIfAbsent(
                    tableName,
                    "OneToMany;$columnName${if (lazy) ";LAZY" else ""}"
                )

                result.putIfAbsent(tableName, mutableMapOf())
                if (context.getBoolean("generateParent")) {
                    val parentRelation = "*ManyToOne;$columnName${if (lazy) ";LAZY" else ""}"
                    result[tableName]!!.putIfAbsent(parent, parentRelation)
                }
            }
        }
    }

    private fun processExternalRelations(
        columns: List<Map<String, Any?>>,
        result: MutableMap<String, MutableMap<String, String>>,
        tableName: String,
        context: MutableAggregateContext
    ) {
        var owner = ""
        var beowned = ""
        var joinCol = ""
        var inverseJoinColumn = ""
        var ownerLazy = false

        columns.forEach { column ->
            if (SqlSchemaUtils.hasReference(column)) {
                val refTableName = SqlSchemaUtils.getReference(column)
                result.putIfAbsent(refTableName, mutableMapOf())
                val lazy = SqlSchemaUtils.isLazy(
                    column,
                    "LAZY".equals(context.getString("fetchType"), ignoreCase = true)
                )
                if (owner.isEmpty()) {
                    ownerLazy = lazy
                    owner = refTableName
                    joinCol = SqlSchemaUtils.getColumnName(column)
                } else {
                    beowned = refTableName
                    inverseJoinColumn = SqlSchemaUtils.getColumnName(column)
                    result[beowned]!!.putIfAbsent(
                        owner,
                        "*ManyToMany;$inverseJoinColumn${if (lazy) ";LAZY" else ""}"
                    )
                }
            }
        }

        if (owner.isNotEmpty() && beowned.isNotEmpty()) {
            result[owner]!!.putIfAbsent(
                beowned,
                "ManyToMany;$joinCol;$inverseJoinColumn;$tableName${if (ownerLazy) ";LAZY" else ""}"
            )
        }
    }

    private fun processExplicitRelations(
        columns: List<Map<String, Any?>>,
        result: MutableMap<String, MutableMap<String, String>>,
        tableName: String,
        context: MutableAggregateContext
    ) {
        val tableMeta = context.tableMap[tableName]!!
        val isChild = !SqlSchemaUtils.isAggregateRoot(tableMeta)
        val parent = if (isChild) SqlSchemaUtils.getParent(tableMeta) else ""

        columns.forEach { column ->
            val colRel = SqlSchemaUtils.getRelation(column)
            val colName = SqlSchemaUtils.getColumnName(column)
            val lazy = SqlSchemaUtils.isLazy(
                column,
                "LAZY".equals(context.getString("fetchType"), ignoreCase = true)
            )

            if (colRel.isNotBlank() || SqlSchemaUtils.hasReference(column)) {
                val refTableName = SqlSchemaUtils.getReference(column)

                // 若为子表，且不生成父侧导航，则避免在此处回填子侧 ManyToOne/OneToOne
                if (isChild && !context.getBoolean("generateParent") &&
                    parent.equals(refTableName, ignoreCase = true)
                ) {
                    return@forEach
                }
                when (colRel) {
                    "OneToOne", "1:1" -> {
                        result.putIfAbsent(tableName, mutableMapOf())
                        result[tableName]!!.putIfAbsent(
                            refTableName,
                            "OneToOne;$colName${if (lazy) ";LAZY" else ""}"
                        )
                    }

                    "ManyToOne", "*:1" -> {
                        result.putIfAbsent(tableName, mutableMapOf())
                        result[tableName]!!.putIfAbsent(
                            refTableName,
                            "ManyToOne;$colName${if (lazy) ";LAZY" else ""}"
                        )
                    }

                    else -> {
                        if (SqlSchemaUtils.hasReference(column)) {
                            result.putIfAbsent(tableName, mutableMapOf())
                            result[tableName]!!.putIfAbsent(
                                refTableName,
                                "ManyToOne;$colName${if (lazy) ";LAZY" else ""}"
                            )
                        }
                    }
                }
            }
        }
    }
}
