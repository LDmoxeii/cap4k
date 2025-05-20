package com.only4.core.application

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
     * @param persistAggregates 待持久化聚合根（新建、更新）
     * @param removeAggregates  待删除聚合根
     */
    fun beforeTransaction(persistAggregates: Set<Any>, removeAggregates: Set<Any>)

    /**
     * 事务执行最初
     *
     * @param persistAggregates 待持久化聚合根（新建、更新）
     * @param removeAggregates  待删除聚合根
     */
    fun preInTransaction(persistAggregates: Set<Any>, removeAggregates: Set<Any>)

    /**
     * 事务执行之后
     *
     * @param persistAggregates 待持久化聚合根（新建、更新）
     * @param removeAggregates  待删除聚合根
     */
    fun postInTransaction(persistAggregates: Set<Any>, removeAggregates: Set<Any>)

    /**
     * 事务结束后
     *
     * @param persistAggregates 待持久化聚合根（新建、更新）
     * @param removeAggregates  待删除聚合根
     */
    fun afterTransaction(persistAggregates: Set<Any>, removeAggregates: Set<Any>)

    /**
     * 实体持久化之后
     * @param entities 实体
     */
    fun postEntitiesPersisted(entities: Set<Any>)
}
