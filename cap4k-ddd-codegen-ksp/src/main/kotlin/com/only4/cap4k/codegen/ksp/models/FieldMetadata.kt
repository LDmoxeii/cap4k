package com.only4.cap4k.codegen.ksp.models

data class FieldMetadata(
    /**
     * 字段名称
     */
    val name: String,

    /**
     * 字段类型（全限定名）
     */
    val type: String,

    /**
     * 是否为 ID 字段
     */
    val isId: Boolean,

    /**
     * 是否可空
     */
    val isNullable: Boolean,

    /**
     * 字段上的注解列表
     */
    val annotations: List<String>
)
