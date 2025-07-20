@file:JvmName("Aggregate")

package com.only4.cap4k.ddd.core.domain.aggregate.annotation

const val TYPE_ENTITY: String = "entity"
const val TYPE_VALUE_OBJECT: String = "value-object"
const val TYPE_ENUM: String = "enum"
const val TYPE_REPOSITORY: String = "repository"
const val TYPE_DOMAIN_EVENT: String = "domain-event"
const val TYPE_FACTORY: String = "factory"
const val TYPE_FACTORY_PAYLOAD: String = "factory-payload"
const val TYPE_SPECIFICATION: String = "specification"

/**
 * 聚合信息
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Aggregate(
    /**
     * 所属聚合
     *
     * @return
     */
    val aggregate: String = "",
    /**
     * 元素名称
     *
     * @return
     */
    val name: String = "",
    /**
     * 是否聚合根
     * @return
     */
    val root: Boolean = false,
    /**
     * 元素类型
     * entity、value-object、repository、factory、factory-payload、domain-event、specification、enum
     *
     * @return
     */
    val type: String = "",
    /**
     * 实体描述
     *
     * @return
     */
    val description: String = "",
    /**
     * 关联元素名称
     *
     * @return
     */
    vararg val relevant: String = []
)
