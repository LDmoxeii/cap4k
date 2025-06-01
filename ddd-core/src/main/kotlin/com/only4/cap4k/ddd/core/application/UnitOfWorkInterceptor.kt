package com.only4.cap4k.ddd.core.application

import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.domain.aggregate.SpecificationManager
import com.only4.cap4k.ddd.core.domain.event.DomainEventManager
import com.only4.cap4k.ddd.core.share.DomainException

/**
 * 聚合根操作集合
 */
data class AggregateOperation(
    val persistAggregates: Set<Any> = emptySet(),
    val removeAggregates: Set<Any> = emptySet()
)

/**
 * UOW工作单元拦截器
 *
 * @author binking338
 * @date 2024/12/29
 */
interface UnitOfWorkInterceptor {
    /**
     * 事务开始前
     *
     * @param operation 聚合根操作集合
     */
    fun beforeTransaction(operation: AggregateOperation) = Unit

    /**
     * 事务执行最初
     *
     * @param operation 聚合根操作集合
     */
    fun preInTransaction(operation: AggregateOperation) = Unit

    /**
     * 事务执行之后
     *
     * @param operation 聚合根操作集合
     */
    fun postInTransaction(operation: AggregateOperation) = Unit

    /**
     * 事务结束后
     *
     * @param operation 聚合根操作集合
     */
    fun afterTransaction(operation: AggregateOperation) = Unit

    /**
     * 实体持久化之后
     * @param entities 实体集合
     */
    fun postEntitiesPersisted(entities: Set<Any>) = Unit
}

/**
 * 集成事件工作单元拦截器
 */
class IntegrationEventUnitOfWorkInterceptor(
    private val integrationEventManager: IntegrationEventManager
) : UnitOfWorkInterceptor {
    override fun postInTransaction(operation: AggregateOperation) {
        integrationEventManager.release()
    }
}

/**
 * 规约工作单元拦截器
 * 在事务执行过程中调用聚合根的规约进行验证
 *
 * @author binking338
 * @date 2024/12/29
 */
class SpecificationUnitOfWorkInterceptor(
    private val specificationManager: SpecificationManager
) : UnitOfWorkInterceptor {

    override fun beforeTransaction(operation: AggregateOperation) {
        operation.persistAggregates.forEach { entity ->
            val result = specificationManager.specifyBeforeTransaction(entity)
            if (!result.passed) {
                throw DomainException(result.message)
            }
        }
    }

    override fun preInTransaction(operation: AggregateOperation) {
        operation.persistAggregates.forEach { entity ->
            val result = specificationManager.specifyInTransaction(entity)
            if (!result.passed) {
                throw DomainException(result.message)
            }
        }
    }
}

/**
 * 领域事件工作单元拦截器
 * 在实体持久化后释放领域事件
 *
 * @author binking338
 * @date 2024/12/29
 */
class DomainEventUnitOfWorkInterceptor(
    private val domainEventManager: DomainEventManager
) : UnitOfWorkInterceptor {
    override fun postEntitiesPersisted(entities: Set<Any>) {
        domainEventManager.release(entities)
    }
}
