package com.only4.core.application

import com.only4.core.application.event.IntegrationEventManager

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
