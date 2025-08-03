package com.only4.cap4k.ddd.core.domain.aggregate

import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import java.util.*

/**
 * 聚合管理器
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
interface AggregateSupervisor {

    companion object {
        val instance: AggregateSupervisor = AggregateSupervisorSupport.instance
    }

    fun <AGGREGATE : Aggregate<out ENTITY>, ENTITY_PAYLOAD : AggregatePayload<out ENTITY>, ENTITY : Any> create(
        clazz: Class<AGGREGATE>,
        payload: ENTITY_PAYLOAD
    ): AGGREGATE

    /**
     * 根据id获取聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> getById(
        id: Id<AGGREGATE, *>,
        persist: Boolean = true
    ): AGGREGATE? =
        getByIds(listOf(id), persist).firstOrNull()

    /**
     * 根据id获取聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> getByIds(vararg ids: Id<AGGREGATE, *>): List<AGGREGATE> =
        getByIds(ids.toList())

    /**
     * 根据id获取聚合
     */
    fun <AGGREGATE : Aggregate<out ENTITY>, ENTITY : Any> getByIds(
        ids: Iterable<Id<AGGREGATE, *>>,
        persist: Boolean = true
    ): List<AGGREGATE>

    /**
     * 根据条件获取聚合列表
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> find(
        predicate: AggregatePredicate<AGGREGATE, ENTITY>,
        vararg orders: OrderInfo
    ): List<AGGREGATE> =
        find(predicate, orders.toList())

    /**
     * 根据条件获取聚合列表
     */
    fun <AGGREGATE : Aggregate<out Any>> find(
        predicate: AggregatePredicate<AGGREGATE, out Any>,
        orders: Collection<OrderInfo> = emptyList(),
        persist: Boolean = true
    ): List<AGGREGATE>

    /**
     * 根据条件获取聚合列表
     */
    fun <AGGREGATE : Aggregate<out Any>> find(
        predicate: AggregatePredicate<AGGREGATE, out Any>,
        pageParam: PageParam,
        persist: Boolean = true
    ): List<AGGREGATE>

    /**
     * 根据条件获取单个实体
     */
    fun <AGGREGATE : Aggregate<out Any>> findOne(
        predicate: AggregatePredicate<AGGREGATE, out Any>,
        persist: Boolean = true
    ): Optional<AGGREGATE>

    /**
     * 根据条件获取实体
     */
    fun <AGGREGATE : Aggregate<out Any>> findFirst(
        predicate: AggregatePredicate<AGGREGATE, out Any>,
        orders: Collection<OrderInfo> = emptyList(),
        persist: Boolean = true
    ): Optional<AGGREGATE>

    /**
     * 根据条件获取实体
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> findFirst(
        predicate: AggregatePredicate<AGGREGATE, ENTITY>,
        vararg orders: OrderInfo
    ): Optional<AGGREGATE> =
        findFirst(predicate, orders.toList())

    /**
     * 根据条件获取实体分页列表
     */
    fun <AGGREGATE : Aggregate<out Any>> findPage(
        predicate: AggregatePredicate<AGGREGATE, out Any>,
        pageParam: PageParam,
        persist: Boolean = true
    ): PageData<AGGREGATE>

    /**
     * 根据id删除聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> removeById(id: Id<AGGREGATE, *>): AGGREGATE? =
        removeByIds(listOf(id)).firstOrNull()

    /**
     * 根据id删除聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> removeByIds(vararg ids: Id<AGGREGATE, *>): List<AGGREGATE> =
        removeByIds(ids.toList())

    /**
     * 根据id删除聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> removeByIds(ids: Iterable<Id<AGGREGATE, *>>): List<AGGREGATE>

    /**
     * 根据条件删除实体
     */
    fun <AGGREGATE : Aggregate<out Any>> remove(predicate: AggregatePredicate<AGGREGATE, out Any>): List<AGGREGATE>

    /**
     * 根据条件删除实体
     */
    fun <AGGREGATE : Aggregate<out Any>> remove(
        predicate: AggregatePredicate<AGGREGATE, out Any>,
        limit: Int
    ): List<AGGREGATE>

    /**
     * 根据条件获取实体计数
     */
    fun <AGGREGATE : Aggregate<out Any>> count(predicate: AggregatePredicate<AGGREGATE, out Any>): Long

    /**
     * 根据条件判断实体是否存在
     */
    fun <AGGREGATE : Aggregate<out Any>> exists(predicate: AggregatePredicate<AGGREGATE, out Any>): Boolean
}
