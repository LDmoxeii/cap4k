package com.only4.cap4k.ddd.core.application.event.impl

import com.only4.cap4k.ddd.core.application.UnitOfWorkInterceptor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager

/**
 * UOW拦截器，调用集成事件
 *
 * @author LD_moxeii
 * @date 2025/07/26
 */
class IntegrationEventUnitOfWorkInterceptor(
    private val integrationEventManager: IntegrationEventManager
) : UnitOfWorkInterceptor {

    override fun beforeTransaction(persistAggregates: Set<Any>, removeAggregates: Set<Any>) {
        // 空实现
    }

    override fun preInTransaction(persistAggregates: Set<Any>, removeAggregates: Set<Any>) {
        // 空实现
    }

    override fun postInTransaction(persistAggregates: Set<Any>, removeAggregates: Set<Any>) {
        integrationEventManager.release()
    }

    override fun afterTransaction(persistAggregates: Set<Any>, removeAggregates: Set<Any>) {
        // 空实现
    }

    override fun postEntitiesPersisted(entities: Set<Any>) {
        // 空实现
    }
}