package com.only4.cap4k.ddd.core.application

/**
 * UOW工作单元拦截器
 *
 * @author LD_moxeii
 * @date 2025/07/20
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
     * 实体持久化之后（仅聚合根实体）
     * @param entities 聚合根实体集
     */
    fun postEntitiesPersisted(entities: Set<Any>)

    /**
     * 事务执行最后
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
}

