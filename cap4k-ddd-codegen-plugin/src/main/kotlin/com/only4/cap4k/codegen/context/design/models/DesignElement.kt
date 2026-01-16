package com.only4.cap4k.codegen.context.design.models

data class DesignElement(
    val type: String,              // 设计类型: cmd/qry/saga/cli/ie/de/svc
    val `package`: String,         // 相对包路径 (如 category)
    val name: String,              // 类名 (如 CreateCategory)
    val aggregates: List<String>?,  // 多聚合支持
    val desc: String,              // 描述
    val metadata: Map<String, Any?> = emptyMap(),  // 扩展元数据
)
