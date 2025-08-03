package com.only4.cap4k.ddd.core.domain.aggregate.impl

import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass

/**
 * 默认聚合工厂管理器
 *
 * @author LD_moxeii
 * @date 2025/07/23
 */
class DefaultAggregateFactorySupervisor(
    private val factories: List<AggregateFactory<*, *>>,
    private val unitOfWork: UnitOfWork
) : AggregateFactorySupervisor {

    private val factoryMap: Map<Class<*>, AggregateFactory<*, *>> by lazy {
        factories.associateBy { factory ->
            resolveGenericTypeClass(factory, 0, AggregateFactory::class.java)
        }
    }

    fun init() {
        factoryMap
    }

    override fun <ENTITY_PAYLOAD : AggregatePayload<out ENTITY>, ENTITY : Any> create(entityPayload: ENTITY_PAYLOAD): ENTITY {
        val factory = factoryMap[entityPayload::class.java]
            ?: throw DomainException("No factory found for payload: ${entityPayload::class.java.name}")

        @Suppress("UNCHECKED_CAST")
        val instance = (factory as AggregateFactory<ENTITY_PAYLOAD, ENTITY>).create(entityPayload)

        unitOfWork.persist(instance)
        return instance
    }
}
