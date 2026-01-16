package com.only4.cap4k.codegen.context.aggregate

import com.only4.cap4k.codegen.context.BaseContext

interface AggregateContext : BaseContext {

    // === 数据库信息 ===
    val dbType: String

    // === 表信息 ===
    val tableMap: Map<String, Map<String, Any?>>
    val columnsMap: Map<String, List<Map<String, Any?>>>
    val tablePackageMap: Map<String, String>
    val entityTypeMap: Map<String, String>
    val tableModuleMap: Map<String, String>
    val tableAggregateMap: Map<String, String>
    val annotationsMap: Map<String, Map<String, String>>
    val relationsMap: Map<String, Map<String, String>>

    // === 默认的实体类导入 ===
    val entityClassExtraImports: List<String>

    // === 枚举信息 ===
    val enumConfigMap: Map<String, Map<Int, Array<String>>>
    val enumPackageMap: Map<String, String>

    // === 唯一约束信息 ===
    // key: tableName -> value: list of constraints
    // constraint map keys:
    //   - constraintName: String
    //   - columns: List<Map<String, Any?>> where each item has { columnName: String, ordinal: Int }
    val uniqueConstraintsMap: Map<String, List<Map<String, Any?>>>

    fun resolveAggregateWithModule(tableName: String): String
}

