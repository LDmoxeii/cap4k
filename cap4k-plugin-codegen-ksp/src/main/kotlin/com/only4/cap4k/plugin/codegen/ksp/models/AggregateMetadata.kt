package com.only4.cap4k.plugin.codegen.ksp.models

/**
 * 聚合元信息（精简版）
 *
 * 仅收集：
 * - 聚合根
 * - 聚合内实体列表
 * - 聚合内值对象列表
 * - 聚合内枚举列表
 */
data class AggregateMetadata(
    val aggregateName: String,
    val aggregateRoot: ElementMetadata,
    val entities: List<ElementMetadata> = emptyList(),
    val valueObjects: List<ElementMetadata> = emptyList(),
    val enums: List<ElementMetadata> = emptyList()
) {
    val identityType: String
        get() = aggregateRoot.identityType

    val packageName: String
        get() = aggregateRoot.packageName

    val qualifiedName: String
        get() = aggregateRoot.qualifiedName
}
