package com.only4.cap4k.plugin.codegen.context.design.models

/**
 * 统一设计接口 (所有设计类的基类)
 */
interface BaseDesign {
    val type: String               // cmd/qry/saga/cli/ie/de/svc
    val `package`: String         // 相对包路径 (如 category)
    val name: String               // 设计类名 (如 CreateCategoryCmd)
    val desc: String               // 描述
    val aggregate: String?         // 主聚合 (第一个聚合,可能为 null)
    val aggregates: List<String>   // 所有关联聚合
    val primaryAggregateMetadata: AggregateInfo?      // 主聚合元信息
    val aggregateMetadataList: List<AggregateInfo>    // 所有聚合元信息

    /**
     * 设计元素对应的类名（按照各自约定补全后缀并转成 UpperCamelCase）
     */
    fun className(): String
}
