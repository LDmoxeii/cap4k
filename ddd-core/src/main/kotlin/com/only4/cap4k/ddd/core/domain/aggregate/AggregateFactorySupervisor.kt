package com.only4.cap4k.ddd.core.domain.aggregate

import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.share.misc.ClassUtils

/**
 * 聚合工厂管理器
 *
 * @author binking338
 * @date 2024/9/3
 */
interface AggregateFactorySupervisor {
    /**
     * 创建新聚合实例
     *
     * @param entityPayload 聚合载荷对象
     * @return 创建的聚合实例，如果找不到对应的工厂则返回null
     * @param ENTITY 聚合实体类型
     * @param ENTITY_PAYLOAD 聚合载荷类型
     */
    fun <ENTITY, ENTITY_PAYLOAD : AggregatePayload<ENTITY>> create(entityPayload: ENTITY_PAYLOAD): ENTITY

    companion object {
        /**
         * 获取聚合工厂管理器实例
         *
         * @return 聚合工厂管理器实例
         */
        val instance: AggregateFactorySupervisor
            get() = AggregateFactorySupervisorSupport.instance
    }
}

/**
 * 默认聚合工厂管理器实现
 * 负责管理和使用聚合工厂创建聚合实例
 *
 * @author binking338
 * @date 2024/9/3
 */
open class DefaultAggregateFactorySupervisor(
    private val factories: List<AggregateFactory<*, *>>,
    private val unitOfWork: UnitOfWork
) : AggregateFactorySupervisor {

    /**
     * 聚合工厂映射
     * 使用lazy委托属性实现线程安全的延迟初始化
     */
    private val factoryMap by lazy {
        mutableMapOf<Class<*>, AggregateFactory<*, *>>().apply {
            factories.forEach { factory ->
                put(
                    ClassUtils.resolveGenericTypeClass(
                        factory, 0,
                        AggregateFactory::class.java
                    ),
                    factory
                )
            }
        }
    }

    override fun <ENTITY, ENTITY_PAYLOAD : AggregatePayload<ENTITY>> create(entityPayload: ENTITY_PAYLOAD): ENTITY {
        val factory = factoryMap[entityPayload.javaClass] ?: throw IllegalArgumentException(
            "No factory found for entity payload: ${entityPayload.javaClass.name}"
        )

        @Suppress("UNCHECKED_CAST")
        val instance = (factory as AggregateFactory<ENTITY, ENTITY_PAYLOAD>).create(entityPayload)
        unitOfWork.persist(instance!!)
        return instance
    }
}
