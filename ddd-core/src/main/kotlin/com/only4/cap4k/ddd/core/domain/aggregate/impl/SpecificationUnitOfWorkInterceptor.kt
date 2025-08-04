package com.only4.cap4k.ddd.core.domain.aggregate.impl

import com.only4.cap4k.ddd.core.application.UnitOfWorkInterceptor
import com.only4.cap4k.ddd.core.domain.aggregate.SpecificationManager
import com.only4.cap4k.ddd.core.share.DomainException

/**
 * UOW拦截器，调用聚合的规约
 *
 * @author LD_moxeii
 * @date 2025/07/23
 */
class SpecificationUnitOfWorkInterceptor(
    private val specificationManager: SpecificationManager
) : UnitOfWorkInterceptor {

    override fun beforeTransaction(persistAggregates: Set<Any>, removeAggregates: Set<Any>) {
        persistAggregates.forEach { entity ->
            val result = specificationManager.specifyBeforeTransaction(entity)
            if (!result.passed) {
                throw DomainException(result.message)
            }
        }
    }


    override fun preInTransaction(persistAggregates: Set<Any>, removeAggregates: Set<Any>) {
        persistAggregates.forEach { entity ->
            val result = specificationManager.specifyInTransaction(entity)
            if (!result.passed) {
                throw DomainException(result.message)
            }
        }
    }

    override fun postEntitiesPersisted(entities: Set<Any>) {
        // Empty implementation
    }

    override fun postInTransaction(persistAggregates: Set<Any>, removeAggregates: Set<Any>) {
        // Empty implementation
    }

    override fun afterTransaction(persistAggregates: Set<Any>, removeAggregates: Set<Any>) {
        // Empty implementation
    }
}
