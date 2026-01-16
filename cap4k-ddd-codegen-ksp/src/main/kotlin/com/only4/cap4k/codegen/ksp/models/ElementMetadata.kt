package com.only4.cap4k.codegen.ksp.models

/**
 * 聚合元素元数据（统一模型）
 *
 * 用于表示聚合内的各种元素：
 * - 聚合根（Entity, root=true）
 * - 实体（Entity, root=false）
 * - 值对象（Value Object）
 * - 枚举（Enum）
 * - 仓储（Repository）
 * - 工厂（Factory）
 * - 工厂负载（Factory Payload）
 * - 规约（Specification）
 * - 领域事件（Domain Event）
 */
data class ElementMetadata(
    /**
     * 类名（简单名称）
     */
    val className: String,

    /**
     * 类的全限定名
     */
    val qualifiedName: String,

    /**
     * 包名
     */
    val packageName: String,

    /**
     * 元素类型
     * @see ElementType
     */
    val type: String,

    /**
     * 是否为聚合根
     * 只有 type=entity 且 root=true 时才是聚合根
     */
    val isAggregateRoot: Boolean,

    /**
     * 标识类型（ID 字段的类型）
     * 对于非实体/聚合根，可能为 null 或继承自聚合根的 ID 类型
     */
    val identityType: String,

    /**
     * 字段列表
     */
    val fields: List<FieldMetadata>
) {
    companion object {
        /**
         * 元素类型常量（与 @Aggregate 注解的 type 属性对应）
         */
        object ElementType {
            const val ENTITY = "entity"
            const val VALUE_OBJECT = "value-object"
            const val ENUM = "enum"
            const val REPOSITORY = "repository"
            const val DOMAIN_EVENT = "domain-event"
            const val FACTORY = "factory"
            const val FACTORY_PAYLOAD = "factory-payload"
            const val SPECIFICATION = "specification"
        }
    }
}
