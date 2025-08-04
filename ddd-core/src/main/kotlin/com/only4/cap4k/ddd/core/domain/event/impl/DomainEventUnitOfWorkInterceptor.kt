package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.application.UnitOfWorkInterceptor
import com.only4.cap4k.ddd.core.domain.event.DomainEventManager

/**
 * UOW拦截器，调用领域事件
 *
 * @author LD_moxeii
 * @date 2025/07/24
 */
class DomainEventUnitOfWorkInterceptor(
    private val domainEventManager: DomainEventManager
) : UnitOfWorkInterceptor {

    override fun beforeTransaction(persistAggregates: Set<Any>, removeAggregates: Set<Any>) {
        // 在事务开始前不需要特殊处理
    }

    override fun preInTransaction(persistAggregates: Set<Any>, removeAggregates: Set<Any>) {
        // 在事务执行最初不需要特殊处理
    }

    override fun postEntitiesPersisted(entities: Set<Any>) {
        // 实体持久化之后，发布领域事件
        domainEventManager.release(entities)
    }

    override fun postInTransaction(persistAggregates: Set<Any>, removeAggregates: Set<Any>) {
        // 在事务执行之后不需要特殊处理
    }

    override fun afterTransaction(persistAggregates: Set<Any>, removeAggregates: Set<Any>) {
        // 在事务结束后不需要特殊处理
    }
}
